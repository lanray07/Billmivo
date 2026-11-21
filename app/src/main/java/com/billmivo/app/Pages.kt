package com.billmivo.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.text.NumberFormat

@Composable
fun PageLayout(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 880.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun BillsPage(records: Records, onEdit: (Schedule) -> Unit, onAdd: () -> Unit) {
    val latest = records.schedules.groupBy { it.series }.values.map { versions -> versions.maxBy { it.id } }
        .sortedBy { it.name }
    PageLayout {
        SectionTitle(stringResource(R.string.bills))
        if (latest.isEmpty()) Panel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.empty_bills), color = palette.muted)
            Button(onAdd) { Text(stringResource(R.string.first_action)) }
        }
        listOf(false, true).forEach { archived ->
            val bills = latest.filter { (it.until != null) == archived }
            if (bills.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
                if (archived) SectionTitle(stringResource(R.string.archived_bills))
                bills.forEachIndexed { index, bill ->
                    if (index > 0) HorizontalDivider(color = palette.line)
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { onEdit(bill) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when {
                                bill.until == null && records.isPaused(bill.series) -> R.string.paused
                                bill.estimated -> R.string.estimated
                                else -> R.string.confirmed
                            }), color = palette.muted)
                        }
                        Text(money(bill.amount, bill.currency, displayLocale()))
                    }
                }
            }
        }
    }
}

internal enum class CalendarMode(val label: Int) {
    Month(R.string.calendar_month), Week(R.string.calendar_week), Upcoming(R.string.upcoming),
    Overdue(R.string.overdue), Paid(R.string.paid), Cancelled(R.string.cancelled_occurrence)
}

@Composable
internal fun CalendarPage(records: Records, today: LocalDate, mode: CalendarMode,
                          onMode: (CalendarMode) -> Unit, onSelect: (Occurrence) -> Unit) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var providerFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var memberFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val locale = displayLocale()
    val firstWeekday = WeekFields.of(locale).firstDayOfWeek
    val week = today.minusDays(((today.dayOfWeek.value - firstWeekday.value + 7) % 7).toLong()).plusWeeks(offset.toLong())
    val categoryChoices = records.schedules.map { it.category }.distinct().sorted()
    val provider = providerFilter?.takeIf { id -> records.providers.any { it.id == id } }
    val member = memberFilter?.takeIf { id -> records.members.any { it.id == id } }
    val options = remember(mode, month, week, records, today) {
        when (mode) {
            CalendarMode.Month -> records.occurrences(month.atDay(1), month.atEndOfMonth())
            CalendarMode.Week -> records.occurrences(week, week.plusDays(6))
            CalendarMode.Upcoming -> records.occurrences(today, today.plusDays(29)).filter { it.open }
            CalendarMode.Overdue -> records.forecast(today, today, today).overdue
            CalendarMode.Cancelled -> records.occurrences(month.atDay(1), month.atEndOfMonth()).filter { it.cancelled }
            CalendarMode.Paid -> {
                val keys = records.payments.filter { YearMonth.from(it.date) == month }.map { it.key }.toSet()
                if (keys.isEmpty()) emptyList() else records.occurrences(keys.minOf { it.due }, keys.maxOf { it.due })
                    .filter { it.key in keys }
            }
        }
    }
    val items = remember(options, categoryFilter, provider, member, records.sharing, records.payments) {
        val category = categoryFilter?.takeIf { it in categoryChoices }
        options.filter { occurrence ->
            (category == null || occurrence.schedule.category == category) &&
                (provider == null || occurrence.schedule.providerId == provider) &&
                (member == null || records.sharingFor(occurrence.schedule.series, occurrence.date)
                    ?.let { rule -> rule.responsibleId == member || rule.parts.any { it.memberId == member } } == true ||
                    records.payments.any { it.key == occurrence.key && it.payerId == member })
        }
    }
    PageLayout {
        SectionTitle(stringResource(R.string.calendar))
        Panel(Modifier.fillMaxWidth()) {
            MenuField(stringResource(R.string.calendar_view), mode, CalendarMode.entries,
                { stringResource(it.label) }, { offset = 0; onMode(it) })
            if (categoryChoices.isNotEmpty()) MenuField(stringResource(R.string.category), categoryFilter,
                listOf(null) + categoryChoices, { key ->
                    if (key == null) stringResource(R.string.calendar_all) else categoryName(key, records)
                },
                { categoryFilter = it })
            if (records.providers.isNotEmpty()) MenuField(stringResource(R.string.providers), provider,
                listOf(null) + records.providers.map { it.id },
                { id -> records.providers.find { it.id == id }?.name ?: stringResource(R.string.calendar_all) },
                { providerFilter = it })
            if (records.members.isNotEmpty()) MenuField(stringResource(R.string.household), member,
                listOf(null) + records.members.map { it.id },
                { id -> records.members.find { it.id == id }?.name ?: stringResource(R.string.calendar_all) },
                { memberFilter = it })
            if (mode in setOf(CalendarMode.Month, CalendarMode.Week, CalendarMode.Paid, CalendarMode.Cancelled)) Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ offset-- }, enabled = offset > -120) {
                    Icon(painterResource(R.drawable.ic_previous), stringResource(
                        if (mode == CalendarMode.Week) R.string.calendar_previous_week else R.string.previous_month))
                }
                val label = if (mode == CalendarMode.Week) dateRange(week, week.plusDays(6), locale).let { (a, b) ->
                    stringResource(R.string.date_span, a, b)
                } else month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ offset++ }, enabled = offset < 120) {
                    Icon(painterResource(R.drawable.ic_next), stringResource(
                        if (mode == CalendarMode.Week) R.string.calendar_next_week else R.string.next_month))
                }
            }
            OccurrenceList(items, today, onSelect, R.string.calendar_empty)
        }
    }
}

@Composable
fun InsightsPage(records: Records, spending: Spending, currency: String, currencies: List<String>,
                 today: LocalDate, onCurrency: (String) -> Unit) {
    val locale = displayLocale()
    val changes = remember(today, records, currency) { records.billChanges(today, currency) }
    PageLayout {
        SectionTitle(stringResource(R.string.insights))
        if (currencies.size > 1) MenuField(stringResource(R.string.currency), currency, currencies, { it }, onCurrency)
        SpendingPanel(spending, currency, locale)
        BillChangesPanel(changes, currency, locale)
        HouseholdInsights(records, today, currency)
        RecentPayments(records, currency, Int.MAX_VALUE)
    }
}

@Composable
fun BillChangesPanel(changes: List<BillChange>, currency: String, locale: java.util.Locale) {
    Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.bill_changes))
            if (changes.isEmpty()) Text(stringResource(R.string.changes_empty), color = palette.muted)
            changes.forEach { change ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(change.bill.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.change_detail, money(change.previous.amount, currency, locale),
                        money(change.current.amount, currency, locale)))
                    val difference = money(change.change.abs(), currency, locale)
                    val sign = if (change.change.signum() > 0) "+" else "−"
                    val rate = change.rate?.let { NumberFormat.getPercentInstance(locale).apply {
                        maximumFractionDigits = 1
                    }.format(it.abs()) }
                    Text(if (rate == null) "$sign$difference" else "$sign$difference ($sign$rate)", color = palette.muted)
                    Text(stringResource(R.string.date_span, dateLabel(change.previous.date, locale),
                        dateLabel(change.current.date, locale)), style = MaterialTheme.typography.labelMedium, color = palette.muted)
                }
            }
    }
}

@Composable
fun SettingsPage(appearance: String, spoken: Boolean, onTheme: (String) -> Unit, onSpoken: (Boolean) -> Unit,
                 onReminders: () -> Unit, onHousehold: () -> Unit, onProviders: () -> Unit,
                 onDocuments: () -> Unit, onReports: () -> Unit, onAssistant: () -> Unit,
                 onPrivacy: () -> Unit, onSetup: () -> Unit, onCategories: () -> Unit, onPremium: (() -> Unit)? = null) {
    val themes = linkedMapOf("system" to R.string.appearance_system, "light" to R.string.light, "dark" to R.string.dark)
    PageLayout {
        SectionTitle(stringResource(R.string.settings))
        Panel(Modifier.fillMaxWidth()) {
            OutlinedButton(onReminders, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reminders)) }
            OutlinedButton(onHousehold, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.household)) }
            OutlinedButton(onProviders, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.providers)) }
            OutlinedButton(onDocuments, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.documents)) }
            OutlinedButton(onReports, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reports)) }
            OutlinedButton(onAssistant, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.assistant)) }
            if (onPremium != null) OutlinedButton(onPremium, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.premium_title))
            }
            OutlinedButton(onPrivacy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_title)) }
            OutlinedButton(onCategories, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.custom_categories)) }
        }
        Panel(Modifier.fillMaxWidth()) {
            LanguageMenu()
            OutlinedButton(onSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.setup_title)) }
            MenuField(stringResource(R.string.appearance), appearance, themes.keys.toList(),
                { stringResource(themes.getValue(it)) }, onTheme)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(spoken, onSpoken)
                Text(stringResource(R.string.voice_enable))
            }
        }
    }
}

@Composable
internal fun LanguageMenu() {
    val languages = linkedMapOf("" to R.string.system, "en" to R.string.language_en,
        "fr" to R.string.language_fr, "es" to R.string.language_es, "ar" to R.string.language_ar,
        "de" to R.string.language_de, "it" to R.string.language_it,
        "pt-BR" to R.string.language_pt, "nl" to R.string.language_nl, "sv" to R.string.language_sv)
    MenuField(stringResource(R.string.language), AppCompatDelegate.getApplicationLocales().toLanguageTags(),
        languages.keys.toList(), { stringResource(languages[it] ?: R.string.system) },
        { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) })
}
