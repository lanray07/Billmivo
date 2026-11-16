package com.billmivo.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private enum class AssistantTask(val label: Int) {
    Due(R.string.assistant_due), Checklist(R.string.assistant_checklist), Changes(R.string.assistant_changes),
    Categories(R.string.assistant_categories), Planning(R.string.assistant_planning), Duplicates(R.string.assistant_duplicates),
    Terms(R.string.assistant_terms), Provider(R.string.assistant_provider), Draft(R.string.assistant_draft)
}

@Composable
fun AssistantPage(records: Records, today: LocalDate, onDraft: (BillDraft) -> Unit, back: () -> Unit) {
    val locale = displayLocale()
    val context = LocalContext.current
    var task by rememberSaveable { mutableStateOf(AssistantTask.Due) }
    var input by rememberSaveable { mutableStateOf("") }
    var reviewed by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    val forecast = remember(records, today) { records.forecast(today, today, today.plusDays(29)) }
    val currencies = remember(records) { records.schedules.map { it.currency }.distinct().sorted() }
    val draft = remember(input, locale, today) { parseBillDraft(input, locale, today) }

    @Composable
    fun billSummary(bill: Occurrence): String {
        val value = money(bill.remaining, bill.schedule.currency, locale)
        val description = stringResource(R.string.chart_value, bill.schedule.name, value)
        return stringResource(R.string.assistant_summary_line, description, dateLabel(bill.date, locale),
            stringResource(if (bill.estimated) R.string.estimated else R.string.confirmed))
    }

    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.assistant), stringResource(R.string.assistant_offline))
        Panel(Modifier.fillMaxWidth()) {
            MenuField(stringResource(R.string.assistant_task), task, AssistantTask.entries,
                { stringResource(it.label) }, { task = it })
            when (task) {
                AssistantTask.Due -> {
                    currencies.forEach { code ->
                        val totals = forecast.totals(code)
                        Text(stringResource(R.string.assistant_balance, code, money(totals.confirmed, code, locale),
                            money(totals.estimated, code, locale)))
                    }
                    if (forecast.upcoming.isEmpty()) Text(stringResource(R.string.no_upcoming))
                    forecast.upcoming.take(10).forEach { Text(billSummary(it)) }
                    if (forecast.upcoming.size > 10) Text(stringResource(R.string.assistant_first_ten), color = palette.muted)
                }
                AssistantTask.Checklist -> {
                    Text(stringResource(R.string.assistant_check_note), color = palette.muted)
                    val bills = (forecast.overdue + forecast.upcoming).take(10)
                    if (bills.isEmpty()) Text(stringResource(R.string.no_upcoming))
                    bills.forEach { bill ->
                        val id = "${bill.schedule.id}/${bill.date}"
                        SettingSwitch(billSummary(bill), id in reviewed) { checked ->
                            reviewed = ArrayList(if (checked) reviewed + id else reviewed - id)
                        }
                    }
                    if (forecast.overdue.size + forecast.upcoming.size > 10) Text(stringResource(R.string.assistant_first_ten), color = palette.muted)
                }
                AssistantTask.Changes -> {
                    val rows = remember(records, today, context) { records.report(ReportKind.Changes, today, context).rows }
                    if (rows.isEmpty()) Text(stringResource(R.string.changes_empty))
                    rows.take(10).forEach { Text(it.human(locale)) }
                }
                AssistantTask.Categories -> {
                    if (forecast.upcoming.isEmpty()) Text(stringResource(R.string.no_upcoming))
                    forecast.upcoming.groupBy { it.schedule.category to it.schedule.currency }.forEach { (key, bills) ->
                        val title = categoryName(key.first, records)
                        val total = money(bills.sumOf { it.remaining }, key.second, locale)
                        Text(stringResource(R.string.chart_value, title, total))
                    }
                    Text(stringResource(R.string.estimate_note), color = palette.muted)
                }
                AssistantTask.Planning -> {
                    val clusters = forecast.upcoming.groupBy { it.date }.filterValues { it.size > 1 }
                    if (clusters.isEmpty()) Text(stringResource(R.string.assistant_no_clusters))
                    clusters.entries.take(10).forEach { (day, bills) ->
                        Text(dateLabel(day, locale), style = MaterialTheme.typography.titleMedium)
                        bills.forEach { Text(billSummary(it)) }
                    }
                    Text(stringResource(R.string.assistant_planning_note), color = palette.muted)
                }
                AssistantTask.Duplicates -> {
                    val groups = records.schedules.filter { it.until == null }.groupBy {
                        listOf(it.name.lowercase(locale).trim(), it.currency, it.amount.stripTrailingZeros().toPlainString(), it.first.toString())
                    }.values.filter { it.size > 1 }
                    if (groups.isEmpty()) Text(stringResource(R.string.assistant_no_duplicates))
                    groups.forEach { Text(it.first().name) }
                    Text(stringResource(R.string.assistant_duplicate_note), color = palette.muted)
                }
                AssistantTask.Terms -> {
                    Text(stringResource(R.string.assistant_confirmed_term))
                    Text(stringResource(R.string.assistant_estimated_term))
                    Text(stringResource(R.string.assistant_balance_term))
                }
                AssistantTask.Provider -> Text(stringResource(R.string.assistant_questions))
                AssistantTask.Draft -> {
                    Text(stringResource(R.string.assistant_draft_format), color = palette.muted)
                    OutlinedTextField(input, { input = it.take(250) }, label = { Text(stringResource(R.string.assistant_draft)) },
                        modifier = Modifier.fillMaxWidth())
                    if (input.isNotBlank() && draft == null) Text(stringResource(R.string.assistant_draft_error), color = palette.red)
                    draft?.let {
                        Text(stringResource(R.string.assistant_summary_line,
                            it.name, dateLabel(it.due, locale), money(it.amount, it.currency, locale)))
                        Button({ onDraft(it) }) { Text(stringResource(R.string.assistant_review_draft)) }
                    }
                }
            }
        }
    }
}
