package com.billmivo.app

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.util.Currency

data class Member(val id: String, val name: String, val relationship: String = "", val notes: String = "", val archived: Boolean = false)
enum class SplitMode { EQUAL, PERCENT, FIXED, WEIGHTS }
data class SplitPart(val memberId: String, val value: BigDecimal)
data class SharingRule(
    val series: String, val from: LocalDate, val mode: SplitMode?,
    val responsibleId: String?, val parts: List<SplitPart>
)

fun Records.sharingFor(series: String, date: LocalDate): SharingRule? =
    sharing.filter { it.series == series && it.from <= date }.maxByOrNull { it.from }

/** Return null when the configured shares cannot reconcile with this occurrence. */
fun SharingRule.allocate(amount: BigDecimal, currency: String): Map<String, BigDecimal>? {
    val type = mode ?: return emptyMap()
    if (parts.isEmpty() || parts.map { it.memberId }.distinct().size != parts.size) return null
    val digits = Currency.getInstance(currency).defaultFractionDigits
    val units = amount.movePointRight(digits).toBigIntegerExact()
    if (type == SplitMode.FIXED) {
        if (parts.any { it.value.signum() <= 0 || it.value.stripTrailingZeros().scale() > digits } ||
            parts.sumOf { it.value }.compareTo(amount) != 0) return null
        return parts.associate { it.memberId to it.value.setScale(digits) }
    }
    val values = parts.map { if (type == SplitMode.EQUAL) BigDecimal.ONE else it.value }
    if (values.any { it.signum() <= 0 || it.stripTrailingZeros().scale() > 4 }) return null
    if (type == SplitMode.PERCENT && values.sumOf { it }.compareTo(BigDecimal("100")) != 0) return null
    val weights = values.map { it.movePointRight(4).toBigIntegerExact() }
    val total = weights.reduce(BigInteger::add)
    val division = weights.map { (units * it).divideAndRemainder(total) }
    val allocated = division.map { it[0] }.toMutableList()
    val leftover = units - allocated.reduce(BigInteger::add)
    check(leftover.signum() >= 0 && leftover < BigInteger.valueOf(parts.size.toLong()))
    division.indices.sortedWith(compareByDescending<Int> { division[it][1] }.thenBy { parts[it].memberId })
        .take(leftover.toInt()).forEach { allocated[it] += BigInteger.ONE }
    return parts.mapIndexed { index, part -> part.memberId to BigDecimal(allocated[index], digits) }.toMap()
}
