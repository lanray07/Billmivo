package com.billmivo.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal data class PaidBreakdown(
    val total: BigDecimal,
    val categories: Map<String, BigDecimal>,
    val providers: Map<String?, BigDecimal>,
    val members: Map<String?, BigDecimal>
)

internal fun Records.paidBreakdown(start: LocalDate, end: LocalDate, currency: String): PaidBreakdown {
    val bills = schedules.filter { it.currency == currency }.associateBy { it.id }
    val recorded = payments.filter { it.date in start..end && it.key.scheduleId in bills }
    fun <Key> totals(key: (Payment) -> Key) = recorded.groupingBy(key).fold(BigDecimal.ZERO) { sum, payment ->
        sum + payment.amount
    }
    return PaidBreakdown(recorded.sumOf { it.amount }, totals { bills.getValue(it.key.scheduleId).category },
        totals { bills.getValue(it.key.scheduleId).providerId }, totals { it.payerId })
}

@Composable
internal fun HouseholdInsights(records: Records, today: LocalDate, currency: String) {
    val locale = displayLocale()
    val start = today.minusYears(1).plusDays(1)
    val paid = remember(records, today, currency) { records.paidBreakdown(start, today, currency) }
    val upcoming = remember(records, today, currency) {
        records.forecast(today, today, today.plusDays(29)).totals(currency)
    }
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.insights_year_paid), stringResource(R.string.date_span,
            dateLabel(start, locale), dateLabel(today, locale)))
        Text(money(paid.total, currency, locale))
    }
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.insights_due_30))
        Text(stringResource(R.string.chart_value, stringResource(R.string.confirmed), money(upcoming.confirmed, currency, locale)))
        Text(stringResource(R.string.chart_value, stringResource(R.string.estimated), money(upcoming.estimated, currency, locale)))
    }
    if (paid.total.signum() > 0) {
        val providerNames = records.providers.associate { it.id to it.name }
        val memberNames = records.members.associate { it.id to it.name }
        BreakdownPanel(R.string.insights_by_category, paid.categories, paid.total, currency) { categoryName(it, records) }
        BreakdownPanel(R.string.insights_by_provider, paid.providers, paid.total, currency) { id ->
            id?.let(providerNames::get) ?: stringResource(R.string.unassigned)
        }
        BreakdownPanel(R.string.insights_by_member, paid.members, paid.total, currency) { id ->
            id?.let(memberNames::get) ?: stringResource(R.string.unassigned)
        }
    }
}

@Composable
private fun <Key> BreakdownPanel(title: Int, items: Map<Key, BigDecimal>, total: BigDecimal,
                                 currency: String, name: @Composable (Key) -> String) {
    val locale = displayLocale()
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(title), stringResource(R.string.insights_year_paid))
        items.entries.sortedByDescending { it.value }.forEach { (key, amount) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name(key), Modifier.weight(1f))
                    Text(money(amount, currency, locale))
                }
                val fraction = amount.divide(total, 6, RoundingMode.HALF_UP).toFloat()
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
