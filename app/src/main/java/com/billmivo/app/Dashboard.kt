package com.billmivo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Dashboard(
    records: Records, forecast: Forecast, spending: Spending, currency: String, currencies: List<String>,
    period: Int, start: LocalDate, end: LocalDate, today: LocalDate, onCurrency: (String) -> Unit,
    onRange: (Int, LocalDate, LocalDate) -> Unit, onAdd: () -> Unit, onSelect: (Occurrence) -> Unit,
    remindersEnabled: Boolean = false
) {
    val locale = displayLocale()
    var customRange by remember { mutableStateOf(false) }
    val overdue = forecast.overdue.filter { it.schedule.currency == currency }
    val upcoming = forecast.upcoming.filter { it.schedule.currency == currency }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 720.dp
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 1300.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = if (twoColumns) 30.dp else 20.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.forecast), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.forecast_sub), color = palette.muted,
                    style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(7 to R.string.days_7, 30 to R.string.days_30, 90 to R.string.days_90, 0 to R.string.custom)
                    .forEach { (days, label) ->
                        FilterChip(selected = period == days,
                            onClick = { if (days == 0) customRange = true else onRange(days, today, today.plusDays(days.toLong() - 1)) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                if (currencies.size > 1) MenuField(stringResource(R.string.currency), currency, currencies, { it }, onCurrency)
            }
            val balance: @Composable () -> Unit = {
                ForecastCard(forecast.totals(currency), upcoming.size, currency, locale, start, end)
            }
            val gettingStarted: @Composable () -> Unit = {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.first_title), stringResource(R.string.first_body))
                    Button(onAdd, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.first_action)) }
                }
            }
            val upcomingPanel: @Composable () -> Unit = {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.upcoming))
                    OccurrenceList(upcoming, today, onSelect, R.string.no_upcoming)
                }
            }
            val overduePanel: @Composable () -> Unit = {
                if (overdue.isNotEmpty()) OverduePanel(overdue, currency, locale, today, onSelect)
            }
            val changesPanel: @Composable () -> Unit = {
                val changes = remember(records, today, currency) { records.billChanges(today, currency) }
                if (changes.isNotEmpty()) BillChangesPanel(changes.take(3), currency, locale)
            }
            val recentPanel: @Composable () -> Unit = {
                RecentPayments(records, currency, 4, showEmpty = false)
            }
            val planning: @Composable () -> Unit = {
                ForecastPlanning(records, upcoming, currency, today, remindersEnabled, onSelect)
            }
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1.22f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        balance()
                        if (records.schedules.isNotEmpty()) {
                            upcomingPanel()
                            recentPanel()
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (records.schedules.isEmpty()) {
                            gettingStarted()
                        } else {
                            SpendingPanel(spending, currency, locale)
                            overduePanel()
                            planning()
                            changesPanel()
                        }
                    }
                }
            } else {
                balance()
                if (records.schedules.isEmpty()) {
                    gettingStarted()
                } else {
                    overduePanel()
                    upcomingPanel()
                    SpendingPanel(spending, currency, locale)
                    planning()
                    changesPanel()
                    recentPanel()
                }
            }
        }
    }
    if (customRange) RangeDialog(start, end, today, { customRange = false }) { from, through ->
        onRange(0, from, through)
        customRange = false
    }
}

@Composable
private fun ForecastPlanning(records: Records, upcoming: List<Occurrence>, currency: String, today: LocalDate,
                             remindersEnabled: Boolean, select: (Occurrence) -> Unit) {
    val locale = displayLocale()
    val plannedAlerts = remember(records.reminders, upcoming, today, remindersEnabled) {
        if (!remindersEnabled) emptyList() else {
            val rules = records.reminders.associateBy { it.series }
            upcoming.flatMap { bill ->
                val rule = rules[bill.schedule.series]
                listOfNotNull(rule?.leadDays?.let { bill.date.minusDays(it.toLong()) }, bill.date.takeIf { rule?.due == true })
                    .filter { it >= today }.distinct().map { it to bill }
            }.sortedBy { it.first }.take(4)
        }
    }
    if (plannedAlerts.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.forecast_reminders))
        plannedAlerts.forEach { (day, bill) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { select(bill) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bill.schedule.name, Modifier.weight(1f))
                Text(dateLabel(day, locale), color = palette.muted)
            }
        }
    }
    val totals = remember(records.sharing, upcoming, currency) {
        buildMap<String, BigDecimal> {
            upcoming.forEach { bill ->
                records.sharingFor(bill.schedule.series, bill.date)?.allocate(bill.amount, currency)?.forEach { (member, amount) ->
                    put(member, (get(member) ?: BigDecimal.ZERO) + amount)
                }
            }
        }
    }
    if (totals.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.forecast_shares), stringResource(R.string.forecast_shares_detail))
        records.members.filter { it.id in totals }.forEach { member ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(member.name, Modifier.weight(1f))
                Text(money(totals.getValue(member.id), currency, locale))
            }
        }
    }
}

@Composable
private fun ForecastCard(
    total: CurrencyTotal, count: Int, currency: String, locale: Locale, start: LocalDate, end: LocalDate
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().background(forest, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.plan), Modifier.weight(1f), color = pale,
                style = MaterialTheme.typography.titleMedium)
            Text(currency, color = lime, style = MaterialTheme.typography.labelMedium)
            Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(22.dp), tint = lime)
        }
        val dates = dateRange(start, end, locale)
        Text(stringResource(R.string.date_span, dates.first, dates.second), color = Color(0xFFCFDFD6),
            style = MaterialTheme.typography.bodySmall)
        Text(money(total.combined, currency, locale), color = pale,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp, lineHeight = 49.sp))
        Text(pluralStringResource(R.plurals.open_count, count, count), color = Color(0xFFCFDFD6),
            style = MaterialTheme.typography.bodyMedium)
        val confirmedFraction = if (total.combined.signum() == 0) 0f
            else total.confirmed.divide(total.combined, MathContext.DECIMAL64).toFloat()
        val strip = RoundedCornerShape(8.dp)
        Row(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF46675C), strip)) {
            if (confirmedFraction > 0f) Box(Modifier.weight(confirmedFraction).fillMaxHeight().background(lime, strip))
            if (confirmedFraction < 1f && total.combined.signum() > 0) {
                Box(Modifier.weight(1f - confirmedFraction).fillMaxHeight().background(Color(0xFFF4CD84), strip))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceLabel(R.string.confirmed, money(total.confirmed, currency, locale), lime, Modifier.weight(1f))
            BalanceLabel(R.string.estimated, money(total.estimated, currency, locale), Color(0xFFF4CD84), Modifier.weight(1f))
        }
        if (total.estimated.signum() > 0) {
            Text(stringResource(R.string.estimate_note), color = Color(0xFFCFDFD6),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BalanceLabel(label: Int, value: String, accent: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), color = accent, style = MaterialTheme.typography.labelLarge)
        Text(value, color = pale, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OverduePanel(bills: List<Occurrence>, currency: String, locale: Locale, today: LocalDate,
                         onSelect: (Occurrence) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_error), null, tint = palette.red)
            Text(stringResource(R.string.overdue), Modifier.weight(1f), color = palette.red,
                style = MaterialTheme.typography.headlineSmall)
        }
        val byCertainty = bills.partition { !it.estimated }
        Text(stringResource(R.string.chart_value, stringResource(R.string.confirmed),
            money(byCertainty.first.sumOf { it.remaining }, currency, locale)), color = palette.muted)
        if (byCertainty.second.isNotEmpty()) Text(stringResource(R.string.chart_value, stringResource(R.string.estimated),
            money(byCertainty.second.sumOf { it.remaining }, currency, locale)), color = palette.amberText)
        OccurrenceList(bills, today, onSelect, R.string.no_overdue)
    }
}

@Composable
fun OccurrenceList(items: List<Occurrence>, today: LocalDate, onSelect: (Occurrence) -> Unit, empty: Int) {
    var shown by remember(items) { mutableIntStateOf(6) }
    if (items.isEmpty()) Text(stringResource(empty), color = palette.muted)
    items.take(shown).forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(color = palette.line)
        OccurrenceRow(item, today, onSelect)
    }
    if (items.size > 6) TextButton({ shown = if (shown < items.size) shown + 12 else 6 }) {
        Text(stringResource(if (shown < items.size) R.string.show_more else R.string.show_less))
    }
}

@Composable
fun OccurrenceRow(item: Occurrence, today: LocalDate, onSelect: (Occurrence) -> Unit) {
    val locale = displayLocale()
    val status = when {
        item.skipped -> R.string.skipped
        !item.open -> R.string.paid
        item.paid.signum() > 0 -> R.string.part_paid
        item.date < today -> R.string.overdue
        item.date == today -> R.string.today
        else -> null
    }
    val statusColor = if (item.open && item.date < today) palette.red else palette.muted
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { onSelect(item) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(37.dp).background(if (item.estimated) palette.gold else palette.mint, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(if (item.estimated) R.drawable.ic_time else if (!item.open && !item.skipped)
                R.drawable.ic_check else R.drawable.ic_bills), null,
                Modifier.size(19.dp), tint = if (item.estimated) palette.amberText else palette.green)
        }
        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.schedule.name, style = MaterialTheme.typography.titleMedium)
            Text(dateLabel(item.date, locale), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            status?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = statusColor) }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(money(if (item.open) item.remaining else item.amount, item.schedule.currency, locale),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (item.estimated) R.string.estimated else R.string.confirmed),
                style = MaterialTheme.typography.bodySmall, color = if (item.estimated) palette.amberText else palette.green)
        }
        Icon(painterResource(R.drawable.ic_forward), null, Modifier.size(15.dp), tint = palette.muted)
    }
}

@Composable
fun SpendingPanel(spend: Spending, currency: String, locale: Locale) {
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.spending), stringResource(R.string.spending_sub))
        if (!spend.hasPayments) {
            Text(stringResource(R.string.trend_empty), color = palette.muted)
            return@Panel
        }
        val monthName = DateTimeFormatter.ofPattern("LLLL", locale)
        val shortMonth = DateTimeFormatter.ofPattern("LLL", locale)
        Text(stringResource(R.string.month_to_date, spend.months.last().month.format(monthName)), color = palette.muted)
        Text(money(spend.months.last().paid, currency, locale), style = MaterialTheme.typography.headlineSmall)
        val changeLabel = when (spend.change.signum()) {
            1 -> stringResource(R.string.change_up, money(spend.change, currency, locale))
            -1 -> stringResource(R.string.change_down, money(spend.change.abs(), currency, locale))
            else -> stringResource(R.string.change_same)
        }
        Text(changeLabel, style = MaterialTheme.typography.bodySmall, color = palette.muted)
        if (spend.comparableDay < spend.asOfDay) {
            Text(stringResource(R.string.comparison_day, NumberFormat.getIntegerInstance(locale).format(spend.comparableDay)),
                style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
        val maximum = spend.months.maxOf { it.paid }.max(BigDecimal.ONE)
        var selectedIndex by remember(spend) { mutableIntStateOf(spend.months.lastIndex) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            spend.months.forEachIndexed { index, point ->
                val fraction = point.paid.divide(maximum, MathContext.DECIMAL64).toFloat()
                val value = stringResource(R.string.chart_value, point.month.format(monthName), money(point.paid, currency, locale))
                Column(Modifier.weight(1f).clickable { selectedIndex = index }.semantics(mergeDescendants = true) {
                    contentDescription = value
                    selected = selectedIndex == index
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.height(91.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth(0.72f).height(if (point.paid.signum() == 0) 2.dp else maxOf(3.dp, 91.dp * fraction))
                            .background(if (selectedIndex == index) palette.green else palette.chartHistory,
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                    }
                    Text(point.month.format(shortMonth), fontSize = 11.sp, color = palette.muted)
                }
            }
        }
        if (selectedIndex != spend.months.lastIndex) {
            val point = spend.months[selectedIndex]
            Text(stringResource(R.string.chart_value, point.month.format(monthName), money(point.paid, currency, locale)),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RecentPayments(records: Records, currency: String, limit: Int, showEmpty: Boolean = true) {
    val locale = displayLocale()
    val schedules = records.schedules.associateBy { it.id }
    val recent = records.payments.filter { schedules[it.key.scheduleId]?.currency == currency }
        .sortedByDescending { it.date }.take(limit)
    if (recent.isEmpty() && !showEmpty) return
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.history))
        if (recent.isEmpty()) Text(stringResource(R.string.no_history), color = palette.muted)
        recent.forEach { payment ->
            val schedule = schedules.getValue(payment.key.scheduleId)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.paid_on, dateLabel(payment.date, locale)), color = palette.muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(money(payment.amount, currency, locale), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
