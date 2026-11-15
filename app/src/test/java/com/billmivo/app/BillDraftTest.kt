package com.billmivo.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class BillDraftTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test fun parsesDraftAmountDateAndExplicitCurrencyByLocale() {
        val english = parseBillDraft("Broadband costs 29.95 GBP due 2027-01-15", Locale.UK, today)
        val french = parseBillDraft("Internet coûte 29,95 EUR pour le 2027-01-15", Locale.FRANCE, today)
        assertEquals(BillDraft("Broadband", BigDecimal("29.95"), "GBP", LocalDate.of(2027, 1, 15)), english)
        assertEquals(BillDraft("Internet", BigDecimal("29.95"), "EUR", LocalDate.of(2027, 1, 15)), french)
    }

    @Test fun rejectsInvalidMoneyAndCalendarDates() {
        assertNull(parseBillDraft("Broadband costs 2.003 GBP due 2027-02-28", Locale.UK, today))
        assertNull(parseBillDraft("Broadband costs 2.00 GBP due 2027-02-29", Locale.UK, today))
        assertNull(parseBillDraft("Broadband costs 2.00 ZZZ due 2027-02-28", Locale.UK, today))
    }
}
