package com.billmivo.app

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

data class BillDraft(val name: String, val amount: BigDecimal, val currency: String, val due: LocalDate)

private val draftPhrases = mapOf("en" to ("costs" to "due"), "fr" to ("coûte" to "pour le"),
    "es" to ("cuesta" to "vence el"), "ar" to ("تكلف" to "تستحق في"))

fun parseBillDraft(input: String, locale: Locale, today: LocalDate): BillDraft? {
    val (cost, due) = draftPhrases[locale.language] ?: draftPhrases.getValue("en")
    val grammar = Regex("^(.{1,100}?)\\s+${Regex.escape(cost)}\\s+(\\S+)\\s+([A-Z]{3})\\s+" +
        "${Regex.escape(due)}\\s+([\\p{Nd}]{4}-[\\p{Nd}]{2}-[\\p{Nd}]{2})$", RegexOption.IGNORE_CASE)
    val result = grammar.matchEntire(input.trim()) ?: return null
    val (name, amount, rawCode, rawDate) = result.destructured
    val code = rawCode.uppercase(Locale.ROOT)
    val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return null
    if (currency.defaultFractionDigits < 0 || name.isBlank()) return null
    val date = runCatching {
        LocalDate.parse(rawDate.map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString(""))
    }.getOrNull()?.takeIf { it in LocalDate.of(2000, 1, 1)..today.plusYears(10) } ?: return null
    val value = parseAmount(amount, code, locale) ?: return null
    return BillDraft(name.trim(), value, code, date)
}
