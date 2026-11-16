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
        assertEquals(VoiceCommand.Navigate.Overdue, parseVoiceCommand("Show overdue bills!", Locale.UK, today))
        assertEquals(VoiceCommand.Navigate.Household, parseVoiceCommand("afficher le partage des dépenses", Locale.FRANCE, today))
        assertEquals(VoiceCommand.Navigate.Increases, parseVoiceCommand("mostrar aumentos de facturas", Locale.forLanguageTag("es-ES"), today))
        assertEquals(VoiceCommand.Navigate.Reminders, parseVoiceCommand("عرض تذكيرات الفواتير", Locale.forLanguageTag("ar"), today))
        assertEquals(VoiceCommand.Range(30), parseVoiceCommand("٣٠", Locale.forLanguageTag("ar"), today))
    }

    @Test fun billDraftTakesPrecedenceAndUnknownActionsDoNotCreateRecords() {
        assertTrue(parseVoiceCommand("Water costs 30 GBP due 2026-11-30", Locale.UK, today) is VoiceCommand.Draft)
        assertNull(parseVoiceCommand("Water costs 30 GBP due 2026-11-31", Locale.UK, today))
        assertNull(parseVoiceCommand("Mark broadband as paid", Locale.UK, today))
    }
}
