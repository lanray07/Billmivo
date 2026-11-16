package com.billmivo.app

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

typealias SaveBillAction = (suspend BillRepository.() -> Unit, () -> Unit, () -> Unit) -> Unit

@Composable
fun DateField(label: Int, date: LocalDate, latest: LocalDate = LocalDate.now().plusYears(10),
              onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val locale = displayLocale()
    OutlinedButton({
        DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day)) },
            date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.minDate = LocalDate.of(2000, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate = latest.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }, Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.chart_value, stringResource(label), dateLabel(date, locale)))
    }
}

@Composable
fun RangeDialog(from: LocalDate, through: LocalDate, today: LocalDate, close: () -> Unit,
                apply: (LocalDate, LocalDate) -> Unit) {
    var first by rememberSaveable { mutableLongStateOf(from.toEpochDay()) }
    var last by rememberSaveable { mutableLongStateOf(through.toEpochDay()) }
    val start = LocalDate.ofEpochDay(first)
    val end = LocalDate.ofEpochDay(last)
    val valid = start >= today && end >= start
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.period)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(R.string.start_date, start) {
                first = it.toEpochDay()
                if (last < first) last = first
            }
            DateField(R.string.end_date, end) { last = it.toEpochDay() }
            if (!valid) Text(stringResource(R.string.range_error), color = palette.red)
        }
    }, confirmButton = { Button({ apply(start, end) }, enabled = valid) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(close) { Text(stringResource(R.string.cancel)) } })
}

val categories = mapOf(
    "rent" to R.string.category_rent, "mortgage" to R.string.category_mortgage,
    "council_tax" to R.string.category_council_tax, "electricity" to R.string.category_electricity,
    "gas" to R.string.category_gas, "water" to R.string.category_water,
    "broadband" to R.string.category_broadband, "mobile_phone" to R.string.category_mobile,
    "television" to R.string.category_tv, "school_fees" to R.string.category_school,
    "transport" to R.string.category_transport, "household_services" to R.string.category_household_services,
    "housing" to R.string.housing, "utilities" to R.string.utilities, "internet" to R.string.internet,
    "insurance" to R.string.insurance, "tax" to R.string.tax, "services" to R.string.services,
    "subscriptions" to R.string.subscriptions, "other" to R.string.other
)
private val cadences = mapOf(
    Cadence.ONCE to R.string.once, Cadence.DAILY to R.string.daily, Cadence.WEEKLY to R.string.weekly,
    Cadence.FORTNIGHTLY to R.string.fortnightly, Cadence.MONTHLY to R.string.monthly,
    Cadence.QUARTERLY to R.string.quarterly, Cadence.YEARLY to R.string.yearly, Cadence.CUSTOM to R.string.custom_days
)
private val marketCurrencies = Currency.getAvailableCurrencies().filter { it.defaultFractionDigits >= 0 }
    .map { it.currencyCode }.sorted()

@Composable
fun BillEditor(previous: Schedule?, today: LocalDate, defaultCurrency: String, onClose: () -> Unit,
               save: SaveBillAction, records: Records, draft: BillDraft? = null) {
    val locale = displayLocale()
    var name by rememberSaveable(previous?.id) { mutableStateOf(previous?.name ?: draft?.name.orEmpty()) }
    var amount by rememberSaveable(previous?.id) {
        mutableStateOf((previous?.amount ?: draft?.amount)?.let { inputAmount(it, locale) }.orEmpty())
    }
    var first by rememberSaveable(previous?.id) { mutableLongStateOf((previous?.first ?: draft?.due ?: today).toEpochDay()) }
    var currency by rememberSaveable(previous?.id) { mutableStateOf(previous?.currency ?: draft?.currency ?: defaultCurrency) }
    var category by rememberSaveable(previous?.id) { mutableStateOf(previous?.category ?: "other") }
    var notes by rememberSaveable(previous?.id) { mutableStateOf(previous?.notes.orEmpty()) }
    var paymentMethod by rememberSaveable(previous?.id) { mutableStateOf(previous?.paymentMethod.orEmpty()) }
    var provider by rememberSaveable(previous?.id) { mutableStateOf(previous?.providerId.orEmpty()) }
    var cadence by rememberSaveable(previous?.id) { mutableStateOf(previous?.cadence ?: if (draft != null) Cadence.ONCE else Cadence.MONTHLY) }
    var interval by rememberSaveable(previous?.id) { mutableStateOf(previous?.interval?.toString() ?: "30") }
    var estimate by rememberSaveable(previous?.id) { mutableStateOf(previous?.estimated ?: false) }
    var busy by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<Int?>(null) }
    var validation by remember { mutableStateOf<Int?>(null) }
    val date = LocalDate.ofEpochDay(first)
    val archived = previous?.until != null
    val paused = previous?.let { records.isPaused(it.series) } == true
    val providers = records.providers

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(if (previous == null) R.string.add_bill else R.string.edit_bill)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (previous != null && !archived) Text(stringResource(R.string.edit_note), color = palette.muted)
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !archived)
                OutlinedTextField(amount, { amount = it.take(30) }, label = { Text(stringResource(R.string.amount)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !archived,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (previous == null) MenuField(stringResource(R.string.currency), currency, marketCurrencies,
                    { it }, { currency = it })
                else Text(stringResource(R.string.chart_value, stringResource(R.string.currency), currency))
                if (!archived) {
                    DateField(R.string.due_date, date) { first = it.toEpochDay() }
                    MenuField(stringResource(R.string.frequency), cadence, Cadence.entries,
                        { stringResource(cadences.getValue(it)) }, { cadence = it })
                    if (cadence == Cadence.CUSTOM) OutlinedTextField(interval, { interval = it.take(4) },
                        label = { Text(stringResource(R.string.day_interval)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    CategoryMenu(category, records, { category = it })
                    if (providers.isNotEmpty()) MenuField(stringResource(R.string.provider), provider,
                        listOf("") + providers.map { it.id },
                        { id -> providers.find { it.id == id }?.name ?: stringResource(R.string.unassigned) },
                        { provider = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(estimate, { estimate = it })
                        Text(stringResource(R.string.expected))
                    }
                    if (estimate) Text(stringResource(R.string.estimate_help), color = palette.muted)
                } else Text(stringResource(R.string.archived))
                if (!archived || paymentMethod.isNotEmpty()) OutlinedTextField(paymentMethod, { paymentMethod = it.take(60) },
                    label = { Text(stringResource(R.string.payment_method)) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, readOnly = archived)
                if (!archived || notes.isNotEmpty()) OutlinedTextField(notes, { notes = it.take(1000) },
                    label = { Text(stringResource(R.string.bill_notes)) }, modifier = Modifier.fillMaxWidth(),
                    maxLines = 4, readOnly = archived)
                validation?.let { Text(stringResource(it), color = palette.red) }
                if (previous != null) {
                    if (!archived && previous.cadence != Cadence.ONCE) {
                        val pauseAction = if (paused) R.string.resume_recurrence else R.string.pause_recurrence
                        if (paused) Text(stringResource(R.string.paused), color = palette.muted)
                        TextButton(onClick = { confirm = pauseAction }, enabled = !busy) { Text(stringResource(pauseAction)) }
                    }
                    if (!archived) TextButton(onClick = { confirm = R.string.archive }, enabled = !busy) {
                        Text(stringResource(R.string.archive))
                    }
                    TextButton(onClick = { confirm = R.string.delete }, enabled = !busy) {
                        Text(stringResource(R.string.delete), color = palette.red)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            if (!archived) Button(onClick = {
                val value = parseAmount(amount, currency, locale)
                val repeat = if (cadence == Cadence.CUSTOM) interval.toLongOrNull() else 1L
                if (name.isBlank() || repeat == null || repeat !in 1..3660) {
                    validation = R.string.check_fields
                } else if (value == null) {
                    validation = R.string.invalid_amount
                } else if (previous != null && cadence == Cadence.ONCE && date < today) {
                    validation = R.string.invalid_date_order
                } else {
                    val schedule = Schedule(series = previous?.series ?: UUID.randomUUID().toString(), name = name.trim(),
                        category = category, currency = currency, amount = value, estimated = estimate,
                        first = date, cadence = cadence, interval = repeat, providerId = provider.takeIf(String::isNotBlank),
                        notes = notes.trim().takeIf(String::isNotEmpty), paymentMethod = paymentMethod.trim().takeIf(String::isNotEmpty))
                    busy = true
                    save({ save(schedule, previous, today) }, onClose) { busy = false; validation = R.string.action_error }
                }
            }, enabled = !busy) { Text(stringResource(R.string.save)) }
        }
    )
    val action = confirm
    if (action != null && previous != null) AlertDialog(onDismissRequest = { confirm = null },
        title = { Text(stringResource(action)) },
        text = { Text(stringResource(when (action) {
            R.string.archive -> R.string.archive_note
            R.string.pause_recurrence -> R.string.pause_recurrence_note
            R.string.resume_recurrence -> R.string.resume_recurrence_note
            else -> R.string.delete_note
        })) },
        confirmButton = {
            Button({
                confirm = null
                busy = true
                save({
                    when (action) {
                        R.string.archive -> archive(previous, today)
                        R.string.pause_recurrence, R.string.resume_recurrence -> setPaused(previous.series, today, action == R.string.pause_recurrence)
                        else -> delete(previous.series)
                    }
                }, onClose) { busy = false; validation = R.string.action_error }
            }) { Text(stringResource(action)) }
        }, dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun OccurrenceDialog(item: Occurrence, today: LocalDate, close: () -> Unit, save: SaveBillAction, records: Records) {
    val locale = displayLocale()
    val currency = item.schedule.currency
    val action = if (item.estimated) R.string.confirm_amount else R.string.record_payment
    var raw by rememberSaveable(item.schedule.id, item.date.toEpochDay(), action, item.remaining.toPlainString()) {
        mutableStateOf(inputAmount(item.remaining, locale))
    }
    var validation by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dismissal by rememberSaveable { mutableStateOf<Int?>(null) }
    var paidOn by rememberSaveable(item.schedule.id, item.date.toEpochDay()) { mutableLongStateOf(today.toEpochDay()) }
    var payer by rememberSaveable(item.schedule.id, item.date.toEpochDay()) { mutableStateOf("") }
    var method by rememberSaveable(item.schedule.id, item.date.toEpochDay()) { mutableStateOf(item.schedule.paymentMethod.orEmpty()) }
    var notes by rememberSaveable(item.schedule.id, item.date.toEpochDay()) { mutableStateOf("") }
    val members = records.members.filterNot { it.archived }
    val payments = records.payments.filter { it.key == item.key }.sortedByDescending { it.date }
    AlertDialog(onDismissRequest = { if (!busy) close() },
        title = { Text(item.schedule.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stringResource(R.string.chart_value, stringResource(R.string.due_date), dateLabel(item.date, locale)))
                records.providers.find { it.id == item.schedule.providerId }?.let {
                    Text(stringResource(R.string.chart_value, stringResource(R.string.provider), it.name))
                }
                item.schedule.notes?.let { Text(stringResource(R.string.chart_value, stringResource(R.string.bill_notes), it)) }
                Text(stringResource(R.string.chart_value, stringResource(if (item.estimated) R.string.estimated else R.string.confirmed),
                    money(item.amount, currency, locale)))
                if (item.paid.signum() > 0) Text(stringResource(R.string.chart_value, stringResource(R.string.paid),
                    money(item.paid, currency, locale)))
                BillAllocation(item, records.sharingFor(item.schedule.series, item.date), records.members,
                    payments)
                if (payments.isNotEmpty()) {
                    Text(stringResource(R.string.payment_history), style = MaterialTheme.typography.titleSmall)
                    payments.forEach { payment ->
                        Text(stringResource(R.string.chart_value, dateLabel(payment.date, locale), money(payment.amount, currency, locale)))
                        payment.method?.let { Text(stringResource(R.string.chart_value, stringResource(R.string.payment_method), it)) }
                        payment.notes?.let { Text(it, color = palette.muted) }
                    }
                }
                if (item.open) {
                    if (item.paid.signum() > 0) Text(stringResource(R.string.chart_value, stringResource(R.string.remaining),
                        money(item.remaining, currency, locale)))
                    Text(stringResource(if (item.estimated) R.string.confirm_note else R.string.payment_note), color = palette.muted)
                    OutlinedTextField(raw, { raw = it.take(30) }, label = { Text(stringResource(R.string.amount)) },
                        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (!item.estimated) DateField(R.string.payment_date, LocalDate.ofEpochDay(paidOn), latest = today) {
                        paidOn = it.toEpochDay()
                    }
                    if (!item.estimated && members.isNotEmpty()) {
                        MenuField(stringResource(R.string.paid_by), payer, listOf("") + members.map { it.id },
                            { id -> members.find { it.id == id }?.name ?: stringResource(R.string.unassigned) }, { payer = it })
                    }
                    if (!item.estimated) {
                        OutlinedTextField(method, { method = it.take(60) }, label = { Text(stringResource(R.string.payment_method)) },
                            singleLine = true, enabled = !busy)
                        OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.payment_notes)) },
                            maxLines = 4, enabled = !busy)
                    }
                    if (item.paid.signum() == 0) {
                        TextButton({ dismissal = R.string.skip_bill }, enabled = !busy) {
                            Text(stringResource(R.string.skip_bill))
                        }
                        TextButton({ dismissal = R.string.cancel_occurrence }, enabled = !busy) {
                            Text(stringResource(R.string.cancel_occurrence))
                        }
                    }
                } else Text(stringResource(when {
                    item.cancelled -> R.string.cancelled_occurrence
                    item.skipped -> R.string.skipped
                    else -> R.string.paid
                }))
                validation?.let { Text(stringResource(it), color = palette.red) }
            }
        },
        confirmButton = {
            if (item.open) Button({
                val amount = parseAmount(raw, currency, locale)
                validation = when {
                    amount == null -> R.string.invalid_amount
                    !item.estimated && amount > item.remaining -> R.string.invalid_payment
                    else -> null
                }
                if (amount != null && validation == null) {
                    busy = true
                    val paymentDate = LocalDate.ofEpochDay(paidOn)
                    save({ if (item.estimated) confirm(item.key, amount) else
                        pay(item.key, amount, paymentDate, today, payer.takeIf(String::isNotBlank), method, notes) },
                        if (item.estimated) ({ busy = false }) else close
                    ) { busy = false; validation = R.string.action_error }
                }
            }, enabled = !busy) { Text(stringResource(action)) }
            else TextButton(close) { Text(stringResource(R.string.close)) }
        },
        dismissButton = { if (item.open) TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    dismissal?.let { selected ->
        val cancelling = selected == R.string.cancel_occurrence
        AlertDialog(onDismissRequest = { dismissal = null }, title = { Text(stringResource(selected)) },
            text = { Text(stringResource(if (cancelling) R.string.cancel_occurrence_prompt else R.string.skip_prompt)) },
            confirmButton = { Button({
                dismissal = null
                busy = true
                save({ if (cancelling) cancelOccurrence(item.key) else skip(item.key) }, close) {
                    busy = false; validation = R.string.action_error
                }
            }) { Text(stringResource(selected)) } },
            dismissButton = { TextButton({ dismissal = null }) {
                Text(stringResource(if (cancelling) R.string.keep_occurrence else R.string.cancel))
            } })
    }
}
