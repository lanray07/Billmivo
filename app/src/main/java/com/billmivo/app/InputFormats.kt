package com.billmivo.app

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

fun defaultCurrency(locale: Locale): String =
    runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        ?: when (locale.language) {
            "fr", "es" -> "EUR"
            else -> "GBP"
        }

fun money(value: BigDecimal, currency: String, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply {
        val unit = requireNotNull(Currency.getInstance(currency))
        this.currency = unit
        val digits = unit.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)

fun parseAmount(value: String, currency: String, locale: Locale): BigDecimal? {
    val digits = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    return parseDecimal(value, locale, digits)
}

fun parseDecimal(value: String, locale: Locale, digits: Int = 4): BigDecimal? {
    val clean = value.trim()
    val format = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        isParseBigDecimal = true
        isGroupingUsed = false
    }
    val position = ParsePosition(0)
    val decimal = format.parse(clean, position) as? BigDecimal ?: return null
    return decimal.takeIf { position.index == clean.length && it.signum() > 0 &&
        it.stripTrailingZeros().scale() <= digits && it < BigDecimal("1000000000000") }
}

fun inputAmount(amount: BigDecimal, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 12 }.format(amount)

fun dateLabel(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale)).format(date)

fun dateRange(start: LocalDate, end: LocalDate, locale: Locale): Pair<String, String> =
    dateLabel(start, locale) to dateLabel(end, locale)

private val numberWords = mapOf(
    "en" to listOf("seven", "thirty", "ninety"),
    "fr" to listOf("sept", "trente", "quatre-vingt-dix"),
    "es" to listOf("siete", "treinta", "noventa"),
    "ar" to listOf("سبعة", "ثلاثون", "تسعون")
)
val voiceRanges = listOf(7, 30, 90)

fun parseForecastRange(transcript: String, locale: Locale): Int? {
    val text = transcript.lowercase(locale).map { char ->
        char.digitToIntOrNull()?.digitToChar() ?: char
    }.joinToString("")
    val digits = Regex("(?<![\\p{L}\\d])(7|30|90)(?![\\p{L}\\d])").findAll(text).map { it.value.toInt() }.toSet()
    val words = (numberWords[locale.language] ?: numberWords.getValue("en")).mapIndexedNotNull { index, word ->
        if (Regex("(?<![\\p{L}\\d])${Regex.escape(word)}(?![\\p{L}\\d])").containsMatchIn(text)) voiceRanges[index] else null
    }
    return (digits + words).singleOrNull()
}
