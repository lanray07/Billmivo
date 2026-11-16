package com.billmivo.app

import android.app.Application
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private var reminderRequests by mutableIntStateOf(0)
    private val privacy by lazy { getSharedPreferences("privacy", 0) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private var lockEnabled by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var credentialAvailable by mutableStateOf(false)
    private var authenticationUnavailable by mutableStateOf(false)
    private var pendingLockChange: Boolean? = null
    private var stoppedAt: Long? = null
    private val authenticate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingLockChange?.let { enabled ->
                privacy.edit().putBoolean("lock", enabled).apply()
                lockEnabled = enabled
                updateSecureWindow()
            }
            locked = false
            authenticationUnavailable = false
        }
        pendingLockChange = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockEnabled = privacy.getBoolean("lock", false)
        locked = lockEnabled
        pendingLockChange = savedInstanceState?.takeIf { it.containsKey("pendingLockChange") }
            ?.getBoolean("pendingLockChange")
        updateSecureWindow()
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[BillsModel::class.java]
        if (savedInstanceState == null && intent.action == BillReminders.OPEN_REMINDERS) reminderRequests++
        val app = application as BillmivoApplication
        app.premium.initialize()
        lifecycleScope.launch {
            try { app.documents.prune() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { android.util.Log.w("Billmivo", "Deferred document cleanup.") }
        }
        setContent {
            if (locked) {
                BillmivoTheme(isSystemInDarkTheme()) {
                    LockedPage(credentialAvailable && !authenticationUnavailable) { requestDeviceCredential() }
                }
            } else BillmivoApp(model, app.reminders, app.premium, app.documents, reminderRequests,
                lockEnabled, credentialAvailable, ::requestDeviceCredential)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingLockChange?.let { outState.putBoolean("pendingLockChange", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        credentialAvailable = keyguard?.isDeviceSecure == true
        if (lockEnabled && stoppedAt?.let { SystemClock.elapsedRealtime() - it >= 120_000 } == true) locked = true
        stoppedAt = null
    }

    override fun onStop() {
        stoppedAt = SystemClock.elapsedRealtime()
        super.onStop()
    }

    private fun updateSecureWindow() {
        if (lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Suppress("DEPRECATION") // Fire OS 7 runs Android 9 and supplies its own system credential prompt.
    private fun requestDeviceCredential(change: Boolean? = null) {
        val intent = keyguard?.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.lock_prompt))
        if (intent == null) {
            credentialAvailable = false
            return
        }
        pendingLockChange = change
        try {
            authenticate.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            pendingLockChange = null
            credentialAvailable = false
            authenticationUnavailable = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == BillReminders.OPEN_REMINDERS) reminderRequests++
    }

    override fun onResume() {
        super.onResume()
        val app = application as BillmivoApplication
        app.reminders.refresh()
        app.premium.refresh()
    }
}

class BillsModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as BillmivoApplication).repository
    private val reminders = (application as BillmivoApplication).reminders
    private val documents = (application as BillmivoApplication).documents
    val records = repo.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun mutate(onError: (Int) -> Unit, onSuccess: () -> Unit, action: suspend BillRepository.() -> Unit) {
        viewModelScope.launch {
            try {
                repo.action()
                reminders.refresh()
                onSuccess()
                launch {
                    try {
                        documents.prune()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        android.util.Log.w("Billmivo", "Deferred cleanup of removed documents.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RecurringBillLimit) {
                onError(R.string.premium_limit)
            } catch (_: Exception) {
                onError(R.string.action_error)
            }
        }
    }
}

private enum class Page(val title: Int, val icon: Int) {
    Dashboard(R.string.dashboard, R.drawable.ic_home),
    Bills(R.string.bills, R.drawable.ic_bills),
    Calendar(R.string.calendar, R.drawable.ic_calendar),
    Insights(R.string.insights, R.drawable.ic_chart),
    More(R.string.more, R.drawable.ic_more)
}

private enum class SettingsScreen { General, Reminders, Household, Providers, Documents, Reports, Assistant, Premium, Privacy, Setup, Categories }

@Composable
private fun BillmivoApp(model: BillsModel, reminders: BillReminders, premium: PremiumAccess,
                       documents: DocumentDisk, reminderRequests: Int, lockEnabled: Boolean,
                       credentialAvailable: Boolean, onLockChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("display", 0) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("appearance", "system") ?: "system") }
    var spoken by rememberSaveable { mutableStateOf(preferences.getBoolean("spoken", true)) }
    val systemDark = isSystemInDarkTheme()
    var page by rememberSaveable { mutableStateOf(Page.Dashboard) }
    var settings by rememberSaveable { mutableStateOf(SettingsScreen.General) }
    var calendarMode by rememberSaveable { mutableStateOf(CalendarMode.Month) }
    var setupSeen by rememberSaveable { mutableStateOf(preferences.getBoolean("setupSeen", false)) }
    var defaultMoney by rememberSaveable { mutableStateOf(preferences.getString("defaultCurrency", null)) }
    val premiumState by premium.state.collectAsStateWithLifecycle()
    val unlocked = !premium.available || premiumState.entitled
    val showPremium = { page = Page.More; settings = SettingsScreen.Premium }
    LaunchedEffect(reminderRequests) {
        if (reminderRequests > 0) {
            page = Page.More
            settings = SettingsScreen.Reminders
        }
    }
    BackHandler(page == Page.More && settings != SettingsScreen.General) { settings = SettingsScreen.General }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(15_000)
        }
    }

    val dark = appearance == "dark" || appearance == "system" && systemDark
    val window = (context as? MainActivity)?.window
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    BillmivoTheme(dark) {
        val data by model.records.collectAsStateWithLifecycle()
        LaunchedEffect(data, setupSeen, reminderRequests) {
            val records = data ?: return@LaunchedEffect
            if (!setupSeen) {
                if (reminderRequests == 0 && records.schedules.isEmpty() && page == Page.Dashboard) {
                    page = Page.More
                    settings = SettingsScreen.Setup
                }
                setupSeen = true
                preferences.edit().putBoolean("setupSeen", true).apply()
            }
        }
        val snackbar = remember { SnackbarHostState() }
        var errorId by remember { mutableIntStateOf(R.string.action_error) }
        val error = stringResource(errorId)
        var failure by remember { mutableStateOf(0) }
        LaunchedEffect(failure) { if (failure > 0) snackbar.showSnackbar(error) }
        val save: (suspend BillRepository.() -> Unit, () -> Unit, () -> Unit) -> Unit =
            { action, success, failed -> model.mutate({ errorId = it; failed(); failure++ }, success, action) }

        var showEditor by rememberSaveable { mutableStateOf(false) }
        var draft by remember { mutableStateOf<BillDraft?>(null) }
        var editing by rememberSaveable { mutableStateOf<Long?>(null) }
        var selectedSchedule by rememberSaveable { mutableStateOf<Long?>(null) }
        var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
        var showVoice by rememberSaveable { mutableStateOf(false) }
        var period by rememberSaveable { mutableStateOf(30) }
        var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
        var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
        var requestedCurrency by rememberSaveable { mutableStateOf<String?>(null) }
        val locale = displayLocale()
        val currencies = data?.schedules?.map { it.currency }?.distinct()?.sorted().orEmpty()
        val currency = requestedCurrency?.takeIf { it in currencies } ?: defaultMoney?.takeIf { it in currencies }
            ?: currencies.firstOrNull() ?: defaultMoney ?: defaultCurrency(locale)
        val start = if (period == 0) maxOf(today, LocalDate.ofEpochDay(customStart ?: today.toEpochDay())) else today
        val end = if (period == 0) maxOf(start, LocalDate.ofEpochDay(customEnd ?: start.toEpochDay()))
            else start.plusDays(period.toLong() - 1)
        val forecast = remember(data, today, start, end) { data?.forecast(today, start, end) }
        val spend = remember(data, today, currency) { data?.spending(today, currency) }

        val add = { editing = null; draft = null; showEditor = true }
        val select: (Occurrence) -> Unit = { selectedSchedule = it.schedule.id; selectedDay = it.date.toEpochDay() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = palette.paper) {
                        Page.entries.forEach { item ->
                            NavigationBarItem(page == item, { page = item },
                                icon = { Icon(painterResource(item.icon), null) }, label = { Text(stringResource(item.title), maxLines = 1) })
                        }
                    }
                }
            ) { insets ->
                Row(Modifier.fillMaxSize().padding(insets)) {
                    if (wide) NavigationRail(containerColor = palette.paper) {
                        Page.entries.forEach { item ->
                            NavigationRailItem(page == item, { page = item },
                                icon = { Icon(painterResource(item.icon), null) }, label = { Text(stringResource(item.title)) })
                        }
                    }
                    val records = data
                    if (records == null || forecast == null || spend == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            BrandBar(onAdd = add, onVoice = { showVoice = true })
                            when (page) {
                                Page.Dashboard -> Dashboard(
                                    records, forecast, spend, currency, currencies, period, start, end, today,
                                    onCurrency = { requestedCurrency = it },
                                    onRange = { days, from, until ->
                                        if (!unlocked && days != 30) showPremium() else {
                                            period = days
                                            customStart = from.toEpochDay()
                                            customEnd = until.toEpochDay()
                                        }
                                    },
                                    onAdd = add, onSelect = select,
                                    remindersEnabled = reminders.preferences().enabled && reminders.canNotify()
                                )
                                Page.Bills -> BillsPage(records, onEdit = { editing = it.id; showEditor = true }, onAdd = add)
                                Page.Calendar -> CalendarPage(records, today, calendarMode, { calendarMode = it }, select)
                                Page.Insights -> InsightsPage(records, spend, currency, currencies, today) { requestedCurrency = it }
                                Page.More -> when (settings) {
                                    SettingsScreen.General -> SettingsPage(appearance, spoken,
                                        onTheme = { appearance = it; preferences.edit().putString("appearance", it).apply() },
                                        onSpoken = { spoken = it; preferences.edit().putBoolean("spoken", it).apply() },
                                        onReminders = { settings = SettingsScreen.Reminders },
                                        onHousehold = { settings = SettingsScreen.Household },
                                        onProviders = { settings = SettingsScreen.Providers },
                                        onDocuments = { settings = SettingsScreen.Documents },
                                        onReports = { settings = SettingsScreen.Reports },
                                        onAssistant = { settings = SettingsScreen.Assistant },
                                        onPrivacy = { settings = SettingsScreen.Privacy },
                                        onSetup = { settings = SettingsScreen.Setup },
                                        onCategories = { settings = SettingsScreen.Categories },
                                        onPremium = showPremium.takeIf { premium.available })
                                    SettingsScreen.Reminders -> RemindersPage(records, today, reminders, save) {
                                        settings = SettingsScreen.General
                                    }
                                    SettingsScreen.Household -> HouseholdPage(records, today, save,
                                        upgrade = showPremium.takeUnless { unlocked }) { settings = SettingsScreen.General }
                                    SettingsScreen.Providers -> ProvidersPage(records, today, save) { settings = SettingsScreen.General }
                                    SettingsScreen.Categories -> CategoriesPage(records, save) { settings = SettingsScreen.General }
                                    SettingsScreen.Documents -> DocumentsPage(records, documents) { settings = SettingsScreen.General }
                                    SettingsScreen.Reports -> ReportsPage(records, today) { settings = SettingsScreen.General }
                                    SettingsScreen.Privacy -> PrivacyPage(lockEnabled, credentialAvailable, onLockChange,
                                        upgrade = showPremium.takeUnless { unlocked }) { settings = SettingsScreen.General }
                                    SettingsScreen.Setup -> SetupPage(defaultMoney ?: defaultCurrency(locale), { selection ->
                                        defaultMoney = selection
                                        preferences.edit().putString("defaultCurrency", selection).apply()
                                    }, add = add,
                                        reminders = { settings = SettingsScreen.Reminders },
                                        household = { settings = SettingsScreen.Household },
                                        privacy = { settings = SettingsScreen.Privacy },
                                        finish = { settings = SettingsScreen.General; page = Page.Dashboard })
                                    SettingsScreen.Assistant -> AssistantPage(records, today, onDraft = {
                                        editing = null
                                        draft = it
                                        showEditor = true
                                    }) { settings = SettingsScreen.General }
                                    SettingsScreen.Premium -> PremiumPage(premium, premiumState) {
                                        settings = SettingsScreen.General
                                    }
                                }
                            }
                        }
                        if (showEditor) {
                            BillEditor(records.schedules.find { it.id == editing }, today, defaultMoney ?: currency,
                                onClose = { showEditor = false; draft = null }, save = save,
                                records = records, draft = draft)
                        }
                        val date = selectedDay?.let(LocalDate::ofEpochDay)
                        val occurrence = remember(records, date, selectedSchedule) {
                            date?.let { records.occurrences(it, it).find { candidate -> candidate.schedule.id == selectedSchedule } }
                        }
                        occurrence?.let {
                            OccurrenceDialog(it, today, { selectedSchedule = null; selectedDay = null }, save, records)
                        }
                        if (showVoice) {
                            VoiceDialog(records, currency, today, spoken, onClose = { showVoice = false }) { command ->
                                when (command) {
                                    is VoiceCommand.Range -> if (!unlocked && command.days != 30) showPremium() else {
                                        period = command.days
                                        page = Page.Dashboard
                                    }
                                    is VoiceCommand.Draft -> {
                                        editing = null
                                        draft = command.bill
                                        showEditor = true
                                    }
                                    is VoiceCommand.ReviewPayment -> records.paymentForReview(command.name, today, locale)?.let(select)
                                    VoiceCommand.Navigate.Overdue -> { calendarMode = CalendarMode.Overdue; page = Page.Calendar }
                                    VoiceCommand.Navigate.Household -> { settings = SettingsScreen.Household; page = Page.More }
                                    VoiceCommand.Navigate.Increases -> { page = Page.Insights }
                                    VoiceCommand.Navigate.Reminders -> { settings = SettingsScreen.Reminders; page = Page.More }
                                }
                                showVoice = false
                            }
                        }
                    }
                }
            }
        }
    }
}
