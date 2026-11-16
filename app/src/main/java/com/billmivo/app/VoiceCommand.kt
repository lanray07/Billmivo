package com.billmivo.app

import java.time.LocalDate
import java.util.Locale

internal sealed interface VoiceCommand {
    data class Range(val days: Int) : VoiceCommand
    data class Draft(val bill: BillDraft) : VoiceCommand
    enum class Navigate : VoiceCommand { Overdue, Household, Increases, Reminders }
}

private val shortcuts = mapOf(
    "en" to listOf("show overdue bills", "show household cost sharing", "show bill increases", "show bill reminders"),
    "fr" to listOf("afficher les factures en retard", "afficher le partage des dépenses", "afficher les hausses de factures", "afficher les rappels"),
    "es" to listOf("mostrar facturas vencidas", "mostrar gastos compartidos", "mostrar aumentos de facturas", "mostrar recordatorios"),
    "ar" to listOf("عرض الفواتير المتأخرة", "عرض تقاسم تكاليف الأسرة", "عرض زيادات الفواتير", "عرض تذكيرات الفواتير")
)

internal fun parseVoiceCommand(input: String, locale: Locale, today: LocalDate): VoiceCommand? {
    parseBillDraft(input, locale, today)?.let { return VoiceCommand.Draft(it) }
    val phrase = input.trim().trimEnd('.', '!', '?', '؟').lowercase(locale).replace(Regex("\\s+"), " ")
    val phrases = shortcuts[locale.language] ?: shortcuts.getValue("en")
    val route = phrases.indexOf(phrase)
    if (route >= 0) return VoiceCommand.Navigate.entries[route]
    if (Regex("\\d{4}-\\d{1,2}-\\d{1,2}").containsMatchIn(phrase) ||
        phrase.any { it in "£€$" }) return null
    return parseForecastRange(phrase, locale)?.let { VoiceCommand.Range(it) }
}
