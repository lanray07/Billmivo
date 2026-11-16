package com.billmivo.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PaidBreakdownTest {
    @Test fun keepsUnassignedPaymentsAndLimitsTotalsToTheSelectedCurrencyAndDates() {
        val start = LocalDate.of(2026, 11, 1)
        val water = Schedule(1, "water", "Water", "water", "GBP", "20.00".toBigDecimal(), false,
            start, Cadence.MONTHLY, providerId = "utility")
        val rent = water.copy(id = 2, series = "rent", name = "Rent", category = "rent", providerId = null)
        val internet = water.copy(id = 3, series = "internet", currency = "EUR")
        val payments = listOf(
            Payment(OccurrenceKey(1, start), start, "5.50".toBigDecimal(), "alex"),
            Payment(OccurrenceKey(2, start), start.plusDays(1), "13.25".toBigDecimal()),
            Payment(OccurrenceKey(1, start), start.minusDays(1), "14.50".toBigDecimal()),
            Payment(OccurrenceKey(3, start), start, "9.50".toBigDecimal())
        )
        val graph = Records(listOf(water, rent, internet), payments, emptyList()).paidBreakdown(start, start.plusDays(30), "GBP")
        assertEquals("18.75".toBigDecimal(), graph.total)
        assertEquals(mapOf("water" to "5.50".toBigDecimal(), "rent" to "13.25".toBigDecimal()), graph.categories)
        assertEquals("13.25".toBigDecimal(), graph.providers[null])
        assertEquals("5.50".toBigDecimal(), graph.members["alex"])
    }
}
