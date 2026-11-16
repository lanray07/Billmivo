package com.billmivo.app

import java.time.LocalDate
import java.util.Locale

internal sealed interface VoiceCommand {
    data class Range(val days: Int) : VoiceCommand
    data class Draft(val bill: BillDraft) : VoiceCommand
    data class ReviewPayment(val name: String) : VoiceCommand
    enum class Navigate : VoiceCommand { Overdue, Household, Increases, Reminders, Upcoming }
}

private val shortcuts = mapOf(
    "en" to listOf("show overdue bills", "show household cost sharing", "show bill increases", "show bill reminders", "read my upcoming payments"),
    "fr" to listOf("afficher les factures en retard", "afficher le partage des dépenses", "afficher les hausses de factures", "afficher les rappels", "lire mes prochains paiements"),
    "es" to listOf("mostrar facturas vencidas", "mostrar gastos compartidos", "mostrar aumentos de facturas", "mostrar recordatorios", "leer mis próximos pagos"),
    "ar" to listOf("عرض الفواتير المتأخرة", "عرض تقاسم تكاليف الأسرة", "عرض زيادات الفواتير", "عرض تذكيرات الفواتير", "اقرأ دفعاتي القادمة")
)

private val paymentRequests = mapOf(
    "en" to Regex("mark (?:my )?(.+?) (?:as )?paid"),
    "fr" to Regex("marquer (?:ma |mon |la |le )?(.+) comme pay[ée]e?"),
    "es" to Regex("marcar (?:mi |la |el )?(.+) como pagad[oa]"),
    "ar" to Regex("تسجيل دفع (.+)")
)

internal fun parseVoiceCommand(input: String, locale: Locale, today: LocalDate): VoiceCommand? {
    parseBillDraft(input, locale, today)?.let { return VoiceCommand.Draft(it) }
    val phrase = input.trim().trimEnd('.', '!', '?', '؟').lowercase(locale).replace(Regex("\\s+"), " ")
    (paymentRequests[locale.language] ?: paymentRequests.getValue("en")).matchEntire(phrase)?.groupValues?.get(1)
        ?.takeIf { it.isNotBlank() && it.length <= 100 }?.let { return VoiceCommand.ReviewPayment(it) }
    val phrases = shortcuts[locale.language] ?: shortcuts.getValue("en")
    val route = phrases.indexOf(phrase)
    if (route >= 0) return VoiceCommand.Navigate.entries[route]
    if (Regex("\\d{4}-\\d{1,2}-\\d{1,2}").containsMatchIn(phrase) ||
        phrase.any { it in "£€$" }) return null
    return parseForecastRange(phrase, locale)?.let { VoiceCommand.Range(it) }
}

internal fun Records.paymentForReview(name: String, today: LocalDate, locale: Locale): Occurrence? {
    val matchName = name.lowercase(locale).trim()
    val horizon = today.plusDays(29)
    val open = forecast(today, today, horizon)
    return (open.overdue + open.upcoming)
        .filter { !it.estimated && it.schedule.name.lowercase(locale).trim() == matchName }
        .groupBy { it.schedule.series }.values
        .singleOrNull()?.firstOrNull()
}
