package com.billmivo.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HouseholdReminderTest {
    private val date = LocalDate.of(2026, 11, 15)
    private fun rule(mode: SplitMode, vararg values: String) = SharingRule("bill", date, mode, null,
        values.mapIndexed { index, value -> SplitPart("member-$index", BigDecimal(value)) })

    @Test fun equalSharesAssignIndivisibleUnitsDeterministically() {
        val shares = rule(SplitMode.EQUAL, "1", "1", "1").allocate(BigDecimal("10"), "GBP")!!
        assertEquals(listOf("3.34", "3.33", "3.33"), shares.values.map { it.toPlainString() })
        assertEquals(listOf("1", "1", "0"),
            rule(SplitMode.EQUAL, "1", "1", "1").allocate(BigDecimal("2"), "JPY")!!.values.map { it.toString() })
    }

    @Test fun percentagesAndWeightsPreserveTheMinorUnitTotal() {
        assertEquals(listOf("3.333", "6.668"), rule(SplitMode.PERCENT, "33.33", "66.67")
            .allocate(BigDecimal("10.001"), "KWD")!!.values.map { it.toPlainString() })
        assertEquals(listOf("6.67", "3.33"), rule(SplitMode.WEIGHTS, "2", "1")
            .allocate(BigDecimal("10"), "GBP")!!.values.map { it.toPlainString() })
        assertNull(rule(SplitMode.PERCENT, "60", "38").allocate(BigDecimal("10"), "GBP"))
    }

    @Test fun fixedSharesRequireAnExactReconciliation() {
        val plan = rule(SplitMode.FIXED, "4", "6")
        assertEquals(BigDecimal("10.00"), plan.allocate(BigDecimal("10"), "GBP")!!.values.sumOf { it })
        assertNull(plan.allocate(BigDecimal("11"), "GBP"))
    }

    @Test fun remindersSuppressSettledAndSkippedOccurrencesAndRepeat() {
        val bill = Schedule(1, "bill", "Rent", "housing", "GBP", BigDecimal("10"), false, date, Cadence.WEEKLY)
        val records = Records(listOf(bill), emptyList(), emptyList(),
            reminders = listOf(ReminderRule("bill", 3, due = true, overdue = true)))
        assertEquals(ReminderKind.UPCOMING, records.reminderEvents(date.minusDays(3)).single().kind)
        assertEquals(ReminderKind.DUE, records.reminderEvents(date.plusWeeks(1)).single().kind)
        assertEquals(ReminderKind.OVERDUE, records.reminderEvents(date.plusDays(1)).single().kind)
        val key = OccurrenceKey(bill.id, date)
        assertTrue(records.copy(payments = listOf(Payment(key, date, bill.amount))).reminderEvents(date).isEmpty())
        assertTrue(records.copy(adjustments = listOf(Adjustment(key, skipped = true))).reminderEvents(date).isEmpty())
    }

    @Test fun quietHoursHandleOvernightAndDaytimeIntervals() {
        val zone = ZoneId.of("Europe/London")
        val evening = date.atTime(23, 0).atZone(zone)
        assertEquals(date.plusDays(1).atTime(7, 0).atZone(zone), ReminderHours().firstAllowed(evening))
        assertEquals(date.atTime(14, 0).atZone(zone),
            ReminderHours(9, 8, 14).firstAllowed(date.atTime(9, 30).atZone(zone)))
        val spring = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, zone)
        assertEquals(7, ReminderHours().firstAllowed(spring).hour)
    }

    @Test fun forecastPlansByReminderDateRatherThanBillDueDate() {
        val overdue = Schedule(1, "water", "Water", "water", "GBP", BigDecimal.TEN, false, date.minusDays(4), Cadence.ONCE)
        val future = overdue.copy(id = 2, series = "internet", name = "Internet", first = date.plusDays(8),
            from = date.plusDays(8), cadence = Cadence.MONTHLY)
        val euros = overdue.copy(id = 3, series = "rent", currency = "EUR")
        val overdueKey = OccurrenceKey(overdue.id, overdue.first)
        val futureKey = OccurrenceKey(future.id, future.first)
        val records = Records(listOf(overdue, future, euros), emptyList(), emptyList(),
            reminders = listOf(ReminderRule(future.series, leadDays = 3, due = true)), oneOffReminders = listOf(
                OneOffReminder(overdueKey, date.plusDays(2)), OneOffReminder(futureKey, date.plusDays(5)),
                OneOffReminder(OccurrenceKey(euros.id, euros.first), date.plusDays(2))))
        val window = records.plannedReminders(date, date.plusDays(6), "GBP")
        assertEquals(listOf(date.plusDays(2) to overdueKey, date.plusDays(5) to futureKey),
            window.map { (day, bill) -> day to bill.key })
        val paid = records.copy(payments = listOf(Payment(overdueKey, date, overdue.amount)))
        assertEquals(listOf(futureKey), paid.plannedReminders(date, date.plusDays(6), "GBP").map { it.second.key })
    }
}
