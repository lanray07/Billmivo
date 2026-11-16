package com.billmivo.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal sealed interface VoiceCommand {
    data class Range(val days: Int) : VoiceCommand
    data class Draft(val bill: BillDraft) : VoiceCommand
    data class ReviewPayment(val name: String) : VoiceCommand
    data class ReviewReminder(val name: String, val notifyOn: LocalDate) : VoiceCommand
    enum class Navigate : VoiceCommand { Overdue, Household, Increases, Reminders, Upcoming }
}

private val shortcuts = mapOf(
    "en" to listOf("show overdue bills", "show household cost sharing", "show bill increases", "show bill reminders", "read my upcoming payments"),
    "fr" to listOf("afficher les factures en retard", "afficher le partage des dépenses", "afficher les hausses de factures", "afficher les rappels", "lire mes prochains paiements"),
    "es" to listOf("mostrar facturas vencidas", "mostrar gastos compartidos", "mostrar aumentos de facturas", "mostrar recordatorios", "leer mis próximos pagos"),
    "ar" to listOf("عرض الفواتير المتأخرة", "عرض تقاسم تكاليف الأسرة", "عرض زيادات الفواتير", "عرض تذكيرات الفواتير", "اقرأ دفعاتي القادمة"),
    "de" to listOf("zeige überfällige rechnungen", "zeige die kostenaufteilung", "zeige änderungen bei rechnungsbeträgen",
        "zeige rechnungserinnerungen", "lies meine nächsten zahlungen vor")
)

private val paymentRequests = mapOf(
    "en" to Regex("mark (?:my )?(.+?) (?:as )?paid"),
    "fr" to Regex("marquer (?:ma |mon |la |le )?(.+) comme pay[ée]e?"),
    "es" to Regex("marcar (?:mi |la |el )?(.+) como pagad[oa]"),
    "ar" to Regex("تسجيل دفع (.+)"),
    "de" to Regex("markiere (?:die rechnung )?(.+?) als bezahlt")
)

private val reminderRequests = mapOf(
    "en" to Regex("remind me to pay (.{1,100}?) (?:on |(?=today$|tomorrow$))(.+)"),
    "fr" to Regex("rappelle-moi de payer (.{1,100}) (?:le|pour) (.+)"),
    "es" to Regex("recuérdame pagar (.{1,100}) (?:el|para) (.+)"),
    "ar" to Regex("ذكرني بدفع (.{1,100}) (?:يوم|في) (.+)"),
    "de" to Regex("erinnere mich an die zahlung für (.{1,100}) (?:am |(?=heute$|morgen$))(.+)")
)

private val reminderDayWords = mapOf(
    "en" to listOf("today", "tomorrow"), "fr" to listOf("aujourd’hui", "demain"),
    "es" to listOf("hoy", "mañana"), "ar" to listOf("اليوم", "غدًا"), "de" to listOf("heute", "morgen")
)

private fun reminderDate(text: String, locale: Locale, today: LocalDate): LocalDate? {
    val relative = reminderDayWords[locale.language] ?: reminderDayWords.getValue("en")
    val day = text.trim().replace('\'', '’')
    val offset = relative.indexOf(day)
    if (offset >= 0) return today.plusDays(offset.toLong())
    DayOfWeek.entries.firstOrNull { it.getDisplayName(TextStyle.FULL, locale).lowercase(locale) == day }?.let {
        return today.with(TemporalAdjusters.nextOrSame(it))
    }
    val iso = day.map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
    return runCatching { LocalDate.parse(iso) }.getOrNull()?.takeIf { it in today..today.plusDays(90) }
}

internal fun parseVoiceCommand(input: String, locale: Locale, today: LocalDate): VoiceCommand? {
    parseBillDraft(input, locale, today)?.let { return VoiceCommand.Draft(it) }
    val phrase = input.trim().trimEnd('.', '!', '?', '؟').lowercase(locale).replace(Regex("\\s+"), " ")
    val reminder = (reminderRequests[locale.language] ?: reminderRequests.getValue("en")).matchEntire(phrase)
    if (reminder != null) {
        val (name, day) = reminder.destructured
        return reminderDate(day, locale, today)?.let { VoiceCommand.ReviewReminder(name, it) }
    }
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
    return namedOpenOccurrence(name, today, today.plusDays(29), locale, confirmedOnly = true)
}

internal fun Records.reminderForReview(command: VoiceCommand.ReviewReminder, today: LocalDate, locale: Locale): Occurrence? =
    namedOpenOccurrence(command.name, today, command.notifyOn.plusDays(29), locale, confirmedOnly = false)

private fun Records.namedOpenOccurrence(name: String, today: LocalDate, horizon: LocalDate, locale: Locale,
                                        confirmedOnly: Boolean): Occurrence? {
    val matchName = name.lowercase(locale).trim()
    val open = forecast(today, today, horizon)
    return (open.overdue + open.upcoming)
        .filter { (!confirmedOnly || !it.estimated) && it.schedule.name.lowercase(locale).trim() == matchName }
        .groupBy { it.schedule.series }.values
        .singleOrNull()?.firstOrNull()
}
