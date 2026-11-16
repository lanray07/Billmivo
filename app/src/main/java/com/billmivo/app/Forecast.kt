package com.billmivo.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class Cadence(val days: Long = 0, val months: Long = 0) {
    ONCE, DAILY(days = 1), WEEKLY(days = 7), FORTNIGHTLY(days = 14),
    MONTHLY(months = 1), QUARTERLY(months = 3), YEARLY(months = 12), CUSTOM
}

data class Schedule(
    val id: Long = 0,
    val series: String,
    val name: String,
    val category: String,
    val currency: String,
    val amount: BigDecimal,
    val estimated: Boolean,
    val first: LocalDate,
    val cadence: Cadence,
    val interval: Long = 1,
    val from: LocalDate = first,
    val until: LocalDate? = null,
    val providerId: String? = null,
    val notes: String? = null,
    val paymentMethod: String? = null
) {
    fun dates(start: LocalDate, end: LocalDate): Sequence<LocalDate> {
        val lower = maxOf(start, first, from)
        val upper = minOf(end, until?.minusDays(1) ?: end)
        if (lower > upper) return emptySequence()
        if (cadence == Cadence.ONCE) return if (first in lower..upper) sequenceOf(first) else emptySequence()
        val monthStep = cadence.months
        val dayStep = if (cadence == Cadence.CUSTOM) interval else cadence.days
        if (monthStep == 0L) {
            val offset = ChronoUnit.DAYS.between(first, lower)
            val firstMatch = first.plusDays(((offset + dayStep - 1) / dayStep) * dayStep)
            return generateSequence(firstMatch) { it.plusDays(dayStep) }.takeWhile { it <= upper }
        }
        val offset = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(lower)) / monthStep
        return generateSequence(offset) { it + 1 }.map { first.plusMonths(it * monthStep) }
            .dropWhile { it < lower }.takeWhile { it <= upper }
    }
}

data class OccurrenceKey(val scheduleId: Long, val due: LocalDate)
data class Adjustment(val key: OccurrenceKey, val confirmed: BigDecimal? = null, val skipped: Boolean = false,
                      val cancelled: Boolean = false)
data class Payment(val key: OccurrenceKey, val date: LocalDate, val amount: BigDecimal, val payerId: String? = null,
                   val method: String? = null, val notes: String? = null)
data class RecurrencePause(val series: String, val from: LocalDate, val until: LocalDate? = null) {
    fun covers(date: LocalDate) = date >= from && (until == null || date < until)
}
data class CustomCategory(val id: String, val name: String)
data class Occurrence(
    val schedule: Schedule,
    val date: LocalDate,
    val amount: BigDecimal,
    val estimated: Boolean,
    val paid: BigDecimal,
    val skipped: Boolean,
    val cancelled: Boolean = false
) {
    val key get() = OccurrenceKey(schedule.id, date)
    val dismissed get() = skipped || cancelled
    val remaining: BigDecimal get() = if (cancelled) BigDecimal.ZERO else (amount - paid).max(BigDecimal.ZERO)
    val open get() = !dismissed && remaining.signum() > 0
}

data class Records(
    val schedules: List<Schedule>,
    val payments: List<Payment>,
    val adjustments: List<Adjustment>,
    val members: List<Member> = emptyList(),
    val sharing: List<SharingRule> = emptyList(),
    val reminders: List<ReminderRule> = emptyList(),
    val providers: List<Provider> = emptyList(),
    val providerContacts: List<ProviderContact> = emptyList(),
    val documents: List<BillDocument> = emptyList(),
    val pauses: List<RecurrencePause> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList()
) {
    fun isPaused(series: String) = pauses.any { it.series == series && it.until == null }

    fun occurrences(start: LocalDate, end: LocalDate): List<Occurrence> {
        val paid = payments.groupingBy { it.key }.fold(BigDecimal.ZERO) { sum, payment -> sum + payment.amount }
        val modified = adjustments.associateBy { it.key }
        val pausesBySeries = pauses.groupBy { it.series }
        return schedules.flatMap { schedule ->
            val pauses = pausesBySeries[schedule.series].orEmpty()
            schedule.dates(start, end).mapNotNull { due ->
                val key = OccurrenceKey(schedule.id, due)
                val change = modified[key]
                if (key !in paid && change?.cancelled != true && pauses.any { it.covers(due) }) return@mapNotNull null
                Occurrence(
                    schedule, due, change?.confirmed ?: schedule.amount,
                    schedule.estimated && change?.confirmed == null, paid[key] ?: BigDecimal.ZERO,
                    change?.skipped == true, change?.cancelled == true
                )
            }.toList()
        }.sortedWith(compareBy({ it.date }, { it.schedule.name }, { it.schedule.id }))
    }
}

data class CurrencyTotal(val confirmed: BigDecimal, val estimated: BigDecimal) {
    val combined get() = confirmed + estimated
}

data class Forecast(val upcoming: List<Occurrence>, val overdue: List<Occurrence>) {
    fun totals(currency: String): CurrencyTotal {
        val bills = upcoming.filter { it.schedule.currency == currency }
        return CurrencyTotal(
            bills.filterNot { it.estimated }.sumOf { it.remaining },
            bills.filter { it.estimated }.sumOf { it.remaining }
        )
    }
}

fun Records.forecast(today: LocalDate, start: LocalDate, end: LocalDate): Forecast {
    require(start >= today && end >= start)
    val earliest = schedules.minOfOrNull { it.from } ?: today
    val open = occurrences(minOf(earliest, today), end).filter { it.open }
    return Forecast(open.filter { it.date >= start }, open.filter { it.date < today })
}

data class MonthSpend(val month: YearMonth, val paid: BigDecimal)
data class Spending(
    val months: List<MonthSpend>, val asOfDay: Int, val comparableDay: Int, val currentToComparableDay: BigDecimal,
    val priorToComparableDay: BigDecimal, val hasPayments: Boolean
) {
    val change get() = currentToComparableDay - priorToComparableDay
}

fun Records.spending(today: LocalDate, currency: String): Spending {
    val scheduleIds = schedules.filter { it.currency == currency }.map { it.id }.toSet()
    val current = YearMonth.from(today)
    val prior = current.minusMonths(1)
    val comparableDay = minOf(today.dayOfMonth, prior.lengthOfMonth())
    val window = current.minusMonths(5).atDay(1)..today
    val actual = payments.filter { it.key.scheduleId in scheduleIds && it.date in window }
    val monthly = actual.groupingBy { YearMonth.from(it.date) }.fold(BigDecimal.ZERO) { sum, payment ->
        sum + payment.amount
    }
    return Spending(
        (5L downTo 0L).map { current.minusMonths(it) }.map { MonthSpend(it, monthly[it] ?: BigDecimal.ZERO) },
        today.dayOfMonth, comparableDay,
        actual.filter { it.date in current.atDay(1)..current.atDay(comparableDay) }.sumOf { it.amount },
        actual.filter { it.date in prior.atDay(1)..prior.atDay(comparableDay) }.sumOf { it.amount },
        actual.isNotEmpty()
    )
}

data class BillChange(val bill: Schedule, val previous: Occurrence, val current: Occurrence) {
    val change get() = current.amount - previous.amount
    val rate: BigDecimal? get() = previous.amount.takeUnless { it.signum() == 0 }
        ?.let { change.divide(it, 4, RoundingMode.HALF_UP) }
}

fun Records.billChanges(today: LocalDate, currency: String): List<BillChange> =
    occurrences(today.minusYears(2), today)
        .filter { !it.estimated && !it.dismissed && it.schedule.currency == currency && it.schedule.cadence != Cadence.ONCE }
        .groupBy { it.schedule.series }.values.mapNotNull { history ->
            history.zipWithNext().asReversed().firstNotNullOfOrNull { (previous, current) ->
                val comparable = previous.schedule.cadence == current.schedule.cadence &&
                    previous.schedule.interval == current.schedule.interval
                if (comparable && previous.amount.compareTo(current.amount) != 0) {
                    BillChange(current.schedule, previous, current)
                } else null
            }
        }.sortedByDescending { it.current.date }
