package com.billmivo.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class ForecastTest {
    private val today = LocalDate.parse("2026-11-15")
    private fun schedule(
        id: Long, due: LocalDate, amount: String = "10.00", currency: String = "GBP",
        cadence: Cadence = Cadence.ONCE, estimated: Boolean = false
    ) = Schedule(id, "series-$id", "Bill $id", "other", currency, amount.toBigDecimal(), estimated, due, cadence)

    @Test fun monthEndClampsAndRecoversOriginalAnchor() {
        val first = LocalDate.parse("2024-01-31")
        val schedule = schedule(1, first, cadence = Cadence.MONTHLY)
        assertEquals(listOf("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30"),
            schedule.dates(first, first.plusMonths(3)).map { it.toString() }.toList())
    }

    @Test fun yearlyLeapDayRecoversInNextLeapYear() {
        val first = LocalDate.parse("2024-02-29")
        assertEquals(listOf("2026-02-28", "2027-02-28", "2028-02-29"),
            schedule(1, first, cadence = Cadence.YEARLY).dates(first.plusYears(2), first.plusYears(4))
                .map { it.toString() }.toList())
    }

    @Test fun intervalUsesCalendarDaysAcrossDaylightSaving() {
        val first = LocalDate.parse("2026-10-18")
        val through = first.plusDays(60)
        assertEquals(listOf(first, first.plusDays(14), first.plusDays(28), first.plusDays(42), first.plusDays(56)),
            schedule(1, first, cadence = Cadence.FORTNIGHTLY).dates(first, through).toList())
        assertEquals(listOf(first.plusDays(5), first.plusDays(10), first.plusDays(15)),
            schedule(1, first, cadence = Cadence.CUSTOM).copy(interval = 5)
                .dates(first.plusDays(2), first.plusDays(16)).toList())
    }

    @Test fun windowsIncludeTodayAndExactLastDayAndKeepOverdueSeparate() {
        val offsets = listOf(-1L, 0L, 6L, 7L, 29L, 30L, 89L, 90L)
        val data = Records(offsets.mapIndexed { i, d -> schedule(i.toLong(), today.plusDays(d)) }, emptyList(), emptyList())
        listOf(7L to 2, 30L to 4, 90L to 6).forEach { (days, count) ->
            val forecast = data.forecast(today, today, today.plusDays(days - 1))
            assertEquals(count, forecast.upcoming.size)
            assertEquals(today.minusDays(1), forecast.overdue.single().date)
        }
        assertEquals(listOf(today.plusDays(29)),
            data.forecast(today, today.plusDays(28), today.plusDays(29)).upcoming.map { it.date })
    }

    @Test fun partialConfirmedEstimatedSkippedAndCurrenciesStayIndependent() {
        val bills = listOf(schedule(1, today, "104.20"), schedule(2, today, "40.50", estimated = true),
            schedule(3, today, "250.00", "EUR"), schedule(4, today, "15.01", estimated = true), schedule(5, today))
        val data = Records(bills, listOf(Payment(OccurrenceKey(1, today), today, "10.30".toBigDecimal())), listOf(
            Adjustment(OccurrenceKey(4, today), "21.05".toBigDecimal()),
            Adjustment(OccurrenceKey(5, today), skipped = true)))
        val forecast = data.forecast(today, today, today)
        assertEquals(CurrencyTotal("114.95".toBigDecimal(), "40.50".toBigDecimal()), forecast.totals("GBP"))
        assertEquals("250.00".toBigDecimal(), forecast.totals("EUR").confirmed)
        assertEquals(4, forecast.upcoming.size)
    }

    @Test fun scheduleRevisionPreservesPriorAmountAndReportsLatestMaterialChange() {
        val first = LocalDate.parse("2026-08-10")
        val cutoff = first.plusMonths(1)
        val old = schedule(1, first, "85.00", cadence = Cadence.MONTHLY).copy(until = cutoff)
        val revised = old.copy(id = 2, amount = "102.00".toBigDecimal(), from = cutoff, until = null)
        val data = Records(listOf(old, revised), emptyList(), emptyList())
        val items = data.occurrences(first, today)
        assertEquals(4, items.size)
        assertEquals(listOf(1L, 2L, 2L, 2L), items.map { it.schedule.id })
        val change = data.billChanges(today, "GBP").single()
        assertEquals("17.00".toBigDecimal(), change.change)
        assertEquals("0.2000".toBigDecimal(), change.rate)
    }

    @Test fun spendingUsesPaymentDateAndLikeForLikeCutoff() {
        val bill = schedule(1, today, "100.00")
        val key = OccurrenceKey(1, today)
        val data = Records(listOf(bill), listOf(
            Payment(key, today.minusMonths(1), "20.00".toBigDecimal()),
            Payment(key, today.minusMonths(1).plusDays(1), "30.00".toBigDecimal()),
            Payment(key, today, "40.00".toBigDecimal()),
            Payment(key, today.plusDays(1), "70.00".toBigDecimal())
        ), emptyList())
        val spend = data.spending(today, "GBP")
        assertEquals(6, spend.months.size)
        assertEquals(MonthSpend(YearMonth.from(today), "40.00".toBigDecimal()), spend.months.last())
        assertEquals("50.00".toBigDecimal(), spend.months[4].paid)
        assertEquals("20.00".toBigDecimal(), spend.priorToComparableDay)
        assertEquals("20.00".toBigDecimal(), spend.change)
    }

    @Test fun localizedAmountAndVoiceParsingRejectAmbiguity() {
        assertEquals("12.34".toBigDecimal(), parseAmount("12,34", "EUR", Locale.FRANCE))
        assertNull(parseAmount("12,345", "EUR", Locale.FRANCE))
        assertNull(parseAmount("12.5", "JPY", Locale.US))
        assertEquals(30, parseForecastRange("٣٠ يومًا", Locale.forLanguageTag("ar")))
        assertEquals(90, parseForecastRange("quatre-vingt-dix jours", Locale.FRANCE))
        assertNull(parseForecastRange("7 or 90 days", Locale.UK))
        assertNull(parseForecastRange("130 days", Locale.UK))
    }

    @Test fun comparisonsUseIdenticalDaysWhenPreviousMonthIsShorter() {
        val monthEnd = LocalDate.parse("2026-03-31")
        val bill = schedule(1, monthEnd)
        val key = OccurrenceKey(1, monthEnd)
        val data = Records(listOf(bill), listOf(
            Payment(key, LocalDate.parse("2026-02-28"), "5.00".toBigDecimal()),
            Payment(key, LocalDate.parse("2026-03-28"), "5.00".toBigDecimal()),
            Payment(key, monthEnd, "20.00".toBigDecimal())
        ), emptyList())
        val spending = data.spending(monthEnd, "GBP")
        assertEquals(28, spending.comparableDay)
        assertEquals(0, spending.change.signum())
        assertEquals("25.00".toBigDecimal(), spending.months.last().paid)
    }
}
