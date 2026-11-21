package com.billmivo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class VoiceCommandTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test fun supportsBrazilianPortugueseCommands() {
        val locale = Locale.forLanguageTag("pt-BR")
        assertEquals("BRL", defaultCurrency(Locale.forLanguageTag("pt")))
        assertEquals(VoiceCommand.Range(30), parseVoiceCommand("trinta dias", locale, today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("ler meus próximos pagamentos", locale, today))
        assertEquals(VoiceCommand.ReviewReminder("energia", today.plusDays(4)),
            parseVoiceCommand("lembre de pagar energia na sexta-feira", locale, today))
        assertEquals(VoiceCommand.ReviewPayment("aluguel"), parseVoiceCommand("marque aluguel como pago", locale, today))
        assertEquals(VoiceCommand.Draft(BillDraft("Internet", "29.95".toBigDecimal(), "BRL", today)),
            parseVoiceCommand("Internet custa 29,95 BRL vence em 2026-11-16", locale, today))
    }

    @Test fun supportsDutchCommands() {
        val locale = Locale.forLanguageTag("nl-NL")
        assertEquals("EUR", defaultCurrency(Locale.forLanguageTag("nl")))
        assertEquals(VoiceCommand.Range(90), parseVoiceCommand("negentig dagen", locale, today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("lees mijn komende betalingen", locale, today))
        assertEquals(VoiceCommand.ReviewReminder("elektriciteit", today.plusDays(4)),
            parseVoiceCommand("herinner me om elektriciteit te betalen op vrijdag", locale, today))
        assertEquals(VoiceCommand.ReviewPayment("huur"), parseVoiceCommand("markeer huur als betaald", locale, today))
        assertEquals(VoiceCommand.Draft(BillDraft("Internet", "29.95".toBigDecimal(), "EUR", today)),
            parseVoiceCommand("Internet kost 29,95 EUR vervalt op 2026-11-16", locale, today))
    }

    @Test fun supportsSwedishCommands() {
        val locale = Locale.forLanguageTag("sv-SE")
        assertEquals("SEK", defaultCurrency(Locale.forLanguageTag("sv")))
        assertEquals(VoiceCommand.Range(7), parseVoiceCommand("sju dagar", locale, today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("läs mina kommande betalningar", locale, today))
        assertEquals(VoiceCommand.ReviewReminder("el", today.plusDays(4)),
            parseVoiceCommand("påminn mig att betala el på fredag", locale, today))
        assertEquals(VoiceCommand.ReviewPayment("hyra"), parseVoiceCommand("markera hyra som betald", locale, today))
        assertEquals(VoiceCommand.Draft(BillDraft("Internet", "29.95".toBigDecimal(), "SEK", today)),
            parseVoiceCommand("Internet kostar 29,95 SEK förfaller 2026-11-16", locale, today))
    }

    @Test fun supportsItalianCommandsAndLocalAmounts() {
        val italian = Locale.ITALY
        assertEquals("EUR", defaultCurrency(Locale.ITALIAN))
        assertEquals("29.95".toBigDecimal(), parseAmount("29,95", "EUR", italian))
        assertEquals(VoiceCommand.Range(30), parseVoiceCommand("trenta giorni", italian, today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("Leggi i miei prossimi pagamenti", italian, today))
        assertEquals(VoiceCommand.Navigate.Household, parseVoiceCommand("Mostra le spese condivise", italian, today))
        assertEquals(VoiceCommand.ReviewReminder("elettricità", today.plusDays(4)),
            parseVoiceCommand("Ricordami di pagare elettricità venerdì", italian, today))
        assertEquals(VoiceCommand.ReviewReminder("internet casa", today.plusDays(1)),
            parseVoiceCommand("Ricordami di pagare Internet casa domani", italian, today))
        assertEquals(VoiceCommand.ReviewReminder("acqua", today.plusDays(4)),
            parseVoiceCommand("Ricordami di pagare acqua il 2026-11-20", italian, today))
        assertEquals(VoiceCommand.ReviewPayment("affitto"), parseVoiceCommand("Segna affitto come pagato", italian, today))
        assertEquals(VoiceCommand.Draft(BillDraft("Internet", "29.95".toBigDecimal(), "EUR", today)),
            parseVoiceCommand("Internet costa 29,95 EUR scade il 2026-11-16", italian, today))
        assertNull(parseVoiceCommand("Ricordami di pagare acqua il 2026-11-15", italian, today))
    }

    @Test fun supportsGermanCommandsAndEuroFormatting() {
        val german = Locale.GERMANY
        assertEquals("EUR", defaultCurrency(Locale.GERMAN))
        assertEquals("29.95".toBigDecimal(), parseAmount("29,95", "EUR", german))
        assertEquals(VoiceCommand.Range(30), parseVoiceCommand("dreißig", german, today))
        assertEquals(VoiceCommand.Navigate.Upcoming, parseVoiceCommand("Lies meine nächsten Zahlungen vor", german, today))
        assertEquals(VoiceCommand.ReviewReminder("strom", today.plusDays(4)),
            parseVoiceCommand("Erinnere mich an die Zahlung für Strom am Freitag", german, today))
        assertEquals(VoiceCommand.ReviewPayment("miete"), parseVoiceCommand("Markiere Miete als bezahlt", german, today))
        assertEquals(VoiceCommand.Draft(BillDraft("Internet", "29.95".toBigDecimal(), "EUR", today)),
            parseVoiceCommand("Internet kostet 29,95 EUR fällig am 2026-11-16", german, today))
    }

    @Test fun resolvesNamedReminderDatesInEachShippedLanguage() {
        assertEquals(VoiceCommand.ReviewReminder("council tax", today.plusDays(4)),
            parseVoiceCommand("Remind me to pay council tax on Friday", Locale.UK, today))
        assertEquals(VoiceCommand.ReviewReminder("electricity", today.plusDays(1)),
            parseVoiceCommand("Remind me to pay electricity tomorrow", Locale.UK, today))
        assertEquals(VoiceCommand.ReviewReminder("gaz", today.plusDays(1)),
            parseVoiceCommand("Rappelle-moi de payer gaz pour demain", Locale.FRANCE, today))
        assertEquals(VoiceCommand.ReviewReminder("agua", today),
            parseVoiceCommand("Recuérdame pagar agua para hoy", Locale.forLanguageTag("es-ES"), today))
        assertEquals(VoiceCommand.ReviewReminder("الماء", today.plusDays(4)),
            parseVoiceCommand("ذكرني بدفع الماء يوم ٢٠٢٦-١١-٢٠", Locale.forLanguageTag("ar"), today))
        assertNull(parseVoiceCommand("Remind me to pay water on 2026-11-15", Locale.UK, today))
        assertNull(parseVoiceCommand("Remind me to pay water on 2026-11-31", Locale.UK, today))
    }

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
