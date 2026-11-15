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
import java.text.NumberFormat

@Composable
private fun PageLayout(content: @Composable ColumnScope.() -> Unit) {
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
                            Text(stringResource(if (bill.estimated) R.string.estimated else R.string.confirmed), color = palette.muted)
                        }
                        Text(money(bill.amount, bill.currency, displayLocale()))
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarPage(records: Records, today: LocalDate, onSelect: (Occurrence) -> Unit) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val locale = displayLocale()
    val items = remember(month, records) { records.occurrences(month.atDay(1), month.atEndOfMonth()) }
    PageLayout {
        SectionTitle(stringResource(R.string.calendar))
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ offset-- }, enabled = offset > -120) {
                    Icon(painterResource(R.drawable.ic_previous), stringResource(R.string.previous_month))
                }
                Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                IconButton({ offset++ }, enabled = offset < 120) {
                    Icon(painterResource(R.drawable.ic_next), stringResource(R.string.next_month))
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
fun SettingsPage(appearance: String, spoken: Boolean, onTheme: (String) -> Unit, onSpoken: (Boolean) -> Unit) {
    val languages = linkedMapOf("" to R.string.system, "en" to R.string.language_en,
        "fr" to R.string.language_fr, "es" to R.string.language_es, "ar" to R.string.language_ar)
    val themes = linkedMapOf("system" to R.string.system, "light" to R.string.light, "dark" to R.string.dark)
    PageLayout {
        SectionTitle(stringResource(R.string.settings))
        Panel(Modifier.fillMaxWidth()) {
            MenuField(stringResource(R.string.language), AppCompatDelegate.getApplicationLocales().toLanguageTags(),
                languages.keys.toList(), { stringResource(languages[it] ?: R.string.system) },
                { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) })
            MenuField(stringResource(R.string.appearance), appearance, themes.keys.toList(),
                { stringResource(themes.getValue(it)) }, onTheme)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(spoken, onSpoken)
                Text(stringResource(R.string.voice_enable))
            }
        }
    }
}
