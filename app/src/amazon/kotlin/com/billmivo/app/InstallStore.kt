package com.billmivo.app

import android.content.Context
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.Receipt
import com.amazon.device.iap.model.RequestId
import com.amazon.device.iap.model.UserDataResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

fun installStore(context: Context): PremiumAccess = AmazonPremium(context.applicationContext)

private const val SKU = "billmivo_premium"
private const val OFFLINE_VALIDITY_MILLIS = 3 * 24 * 60 * 60 * 1000L

private class AmazonPremium(private val context: Context) : PremiumAccess, PurchasingListener {
    private val storage = context.getSharedPreferences("amazon-premium", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val endpoint = BuildConfig.AMAZON_VERIFIER
    private val mode = if (BuildConfig.AMAZON_SANDBOX) "sandbox" else "production"
    override val available = endpoint.isNotBlank() && runCatching {
        context.assets.open("AppstoreAuthenticationKey.pem").use { it.read() != -1 }
    }.getOrDefault(false)

    private val cache = MutableStateFlow(PremiumState(entitled = cached()))
    override val state = cache.asStateFlow()
    private var initialized = false
    private var products: RequestId? = null
    private var purchase: RequestId? = null
    private var restore: RequestId? = null
    private var userRequest: RequestId? = null
    private var pendingCheck: Job? = null
    private val receipts = linkedMapOf<String, Receipt>()
    private var restoreUser: String? = null
    private var pages = 0

    override fun initialize() {
        if (!available || initialized) return
        runCatching {
            PurchasingService.registerListener(context, this)
            PurchasingService.enablePendingPurchases()
        }.onSuccess { initialized = true }
            .onFailure { finish(R.string.premium_error) }
    }

    override fun refresh() {
        initialize()
        if (!initialized || state.value.busy) return
        cache.update { it.copy(entitled = cached()) }
        runCatching {
            userRequest = PurchasingService.getUserData()
            products = PurchasingService.getProductData(setOf(SKU))
        }.onFailure { finish(R.string.premium_error) }
        restore()
    }

    override fun buy() {
        if (!initialized || state.value.busy || state.value.entitled || state.value.price == null) return
        startOperation()
        runCatching { purchase = PurchasingService.purchase(SKU) }.onFailure { finish(R.string.premium_error) }
    }

    override fun restore() {
        if (!initialized || state.value.busy) return
        receipts.clear()
        restoreUser = null
        pages = 0
        startOperation()
        runCatching { restore = PurchasingService.getPurchaseUpdates(true) }.onFailure { finish(R.string.premium_error) }
    }

    override fun onUserDataResponse(response: UserDataResponse) {
        scope.launch {
            if (response.requestId != userRequest) return@launch
            val user = response.userData?.userId
            if (response.requestStatus == UserDataResponse.RequestStatus.SUCCESSFUL && !user.isNullOrBlank()) {
                invalidateOtherAccount(user)
            }
        }
    }

    override fun onProductDataResponse(response: ProductDataResponse) {
        scope.launch {
            if (response.requestId != products) return@launch
            val price = if (response.requestStatus == ProductDataResponse.RequestStatus.SUCCESSFUL)
                response.productData[SKU]?.price?.takeIf(String::isNotBlank) else null
            cache.update { it.copy(price = price) }
        }
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        scope.launch {
            if (response.requestId != purchase) return@launch
            when (response.requestStatus.name) {
                "SUCCESSFUL" -> {
                    val user = response.userData?.userId
                    val receipt = response.receipt
                    if (user.isNullOrBlank() || receipt == null || !receipt.matches()) {
                        finish(R.string.premium_error)
                        return@launch
                    }
                    invalidateOtherAccount(user)
                    when (verify(user, receipt.receiptId)) {
                        true -> if (purchase == response.requestId) {
                            finishVerified(user)
                            runCatching { PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED) }
                        }
                        false -> if (purchase == response.requestId) finish(R.string.premium_unverified)
                        null -> if (purchase == response.requestId) finish(R.string.premium_retry)
                    }
                }
                "ALREADY_PURCHASED" -> { finish(); restore() }
                "PENDING" -> finish(R.string.premium_pending)
                else -> finish(R.string.premium_incomplete)
            }
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        scope.launch {
            if (response.requestId != restore) return@launch
            val user = response.userData?.userId
            if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL || user.isNullOrBlank() ||
                (restoreUser != null && restoreUser != user) || ++pages > 50) {
                finish(R.string.premium_retry)
                return@launch
            }
            restoreUser = user
            invalidateOtherAccount(user)
            response.receipts.filter { it.sku == SKU && it.productType == ProductType.ENTITLED }
                .forEach { receipts[it.receiptId] = it }
            if (response.hasMore()) {
                runCatching { restore = PurchasingService.getPurchaseUpdates(false) }
                    .onFailure { finish(R.string.premium_retry) }
                return@launch
            }
            var unavailable = false
            for (receipt in receipts.values.filter { it.matches() }) {
                val valid = verify(user, receipt.receiptId)
                if (restore != response.requestId) return@launch
                if (valid == true) {
                    finishVerified(user)
                    runCatching { PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED) }
                    return@launch
                }
                if (valid == null) unavailable = true
            }
            if (!unavailable) storage.edit().clear().apply()
            finish(if (unavailable) R.string.premium_retry else R.string.premium_none)
        }
    }

    private fun Receipt.matches() = sku == SKU && productType == ProductType.ENTITLED && !isCanceled

    private fun cached() = available && storage.getString("mode", "") == mode &&
        storage.getLong("until", 0) > System.currentTimeMillis()

    private fun invalidateOtherAccount(user: String) {
        if (storage.getString("account", null)?.let { it != accountKey(user) } == true) {
            storage.edit().clear().apply()
            cache.update { it.copy(entitled = false) }
        }
    }

    private fun accountKey(user: String) = MessageDigest.getInstance("SHA-256").digest(user.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun startOperation() {
        cache.update { it.copy(busy = true, message = null) }
        pendingCheck?.cancel()
        pendingCheck = scope.launch {
            delay(45_000)
            finish(R.string.premium_retry)
        }
    }

    private fun finishVerified(user: String) {
        storage.edit().putString("account", accountKey(user)).putString("mode", mode)
            .putLong("until", System.currentTimeMillis() + OFFLINE_VALIDITY_MILLIS).apply()
        finish(R.string.premium_owned)
    }

    private fun finish(message: Int? = null) {
        purchase = null
        restore = null
        pendingCheck?.cancel()
        pendingCheck = null
        cache.update { it.copy(entitled = cached(), busy = false, message = message) }
    }

    private suspend fun verify(userId: String, receiptId: String): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(endpoint).openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 9000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val body = JSONObject().put("userId", userId).put("receiptId", receiptId).toString().toByteArray()
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                if (connection.responseCode != 200) return@runCatching null
                val json = connection.inputStream.use { JSONObject(it.bufferedReader().readText()) }
                if (json.optString("mode") != mode) null else json.opt("entitled") as? Boolean
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
