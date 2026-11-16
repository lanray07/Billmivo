package com.billmivo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class VoiceCommandTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test fun routesLocalizedCommandsAndKeepsForecastFallback() {
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("Read my upcoming payments", Locale.UK, today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("Lire mes prochains paiements", Locale.FRANCE, today))
        assertEquals(VoiceCommand.Navigate.Upcoming,
            parseVoiceCommand("Leer mis próximos pagos", Locale.forLanguageTag("es-ES"), today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("اقرأ دفعاتي القادمة", Locale.forLanguageTag("ar"), today))
        assertEquals(VoiceCommand.Navigate.Overdue, parseVoiceCommand("Show overdue bills!", Locale.UK, today))
        assertEquals(VoiceCommand.Navigate.Household, parseVoiceCommand("afficher le partage des dépenses", Locale.FRANCE, today))
        assertEquals(VoiceCommand.Navigate.Increases, parseVoiceCommand("mostrar aumentos de facturas", Locale.forLanguageTag("es-ES"), today))
        assertEquals(VoiceCommand.Navigate.Reminders, parseVoiceCommand("عرض تذكيرات الفواتير", Locale.forLanguageTag("ar"), today))
        assertEquals(VoiceCommand.Range(30), parseVoiceCommand("٣٠", Locale.forLanguageTag("ar"), today))
    }

    @Test fun billDraftTakesPrecedenceAndUnknownActionsDoNotCreateRecords() {
        assertTrue(parseVoiceCommand("Water costs 30 GBP due 2026-11-30", Locale.UK, today) is VoiceCommand.Draft)
        assertNull(parseVoiceCommand("Water costs 30 GBP due 2026-11-31", Locale.UK, today))
        assertEquals(VoiceCommand.ReviewPayment("broadband"), parseVoiceCommand("Mark my broadband as paid", Locale.UK, today))
        assertEquals(VoiceCommand.ReviewPayment("gaz"), parseVoiceCommand("Marquer le gaz comme payé", Locale.FRANCE, today))
        assertEquals(VoiceCommand.ReviewPayment("agua"), parseVoiceCommand("Marcar el agua como pagada", Locale.forLanguageTag("es-ES"), today))
        assertEquals(VoiceCommand.ReviewPayment("الماء"), parseVoiceCommand("تسجيل دفع الماء", Locale.forLanguageTag("ar"), today))
    }

    @Test fun paymentReviewRejectsAmbiguityAndUnconfirmedEstimates() {
        val original = Schedule(1, "water", "Water", "utilities", "GBP", "25.00".toBigDecimal(), false,
            today.minusDays(2), Cadence.MONTHLY)
        val records = Records(listOf(original), emptyList(), emptyList())
        assertEquals(today.minusDays(2), records.paymentForReview("WATER", today, Locale.UK)?.date)
        assertNull(records.copy(schedules = listOf(original, original.copy(id = 2, series = "other-water")))
            .paymentForReview("water", today, Locale.UK))
        assertNull(records.copy(schedules = listOf(original.copy(estimated = true))).paymentForReview("water", today, Locale.UK))
    }
}
