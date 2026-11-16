package com.billmivo.app

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class ReminderRule(
    val series: String, val leadDays: Int? = null, val due: Boolean = false,
    val overdue: Boolean = false, val increase: Boolean = false
) {
    val active get() = leadDays != null || due || overdue || increase
}

data class OneOffReminder(val key: OccurrenceKey, val notifyOn: LocalDate)

enum class ReminderKind { UPCOMING, DUE, OVERDUE, INCREASE, ONE_OFF }

internal fun Records.plannedReminders(start: LocalDate, end: LocalDate, currency: String): List<Pair<LocalDate, Occurrence>> {
    val rules = reminders.filter { it.leadDays != null || it.due }.associateBy { it.series }
    fun Occurrence.isOpenInCurrency() = open && schedule.currency == currency
    val recurring = if (rules.isEmpty()) emptyList() else {
        val lead = rules.values.mapNotNull { it.leadDays }.maxOrNull()?.toLong() ?: 0L
        occurrences(start, end.plusDays(lead)).filter { it.isOpenInCurrency() }.flatMap { bill ->
            val rule = rules[bill.schedule.series] ?: return@flatMap emptyList()
            listOfNotNull(rule.leadDays?.let { bill.date.minusDays(it.toLong()) }, bill.date.takeIf { rule.due })
                .filter { it in start..end }.map { it to bill }
        }
    }
    val explicit = oneOffReminders.filter { it.notifyOn in start..end }.groupBy { it.key.due }
        .flatMap { (due, reminders) ->
            val byKey = reminders.associateBy { it.key }
            occurrences(due, due).filter { it.isOpenInCurrency() && it.key in byKey }
                .map { byKey.getValue(it.key).notifyOn to it }
        }
    return (recurring + explicit).distinctBy { (day, bill) -> day to bill.key }
        .sortedWith(compareBy({ it.first }, { it.second.date }, { it.second.schedule.name }))
}

data class ReminderEvent(val bill: Occurrence, val kind: ReminderKind, val notifyOn: LocalDate? = null) {
    val id get() = if (notifyOn == null) "${bill.schedule.id}/${bill.date.toEpochDay()}/${kind.name}"
        else "${bill.schedule.series}/${bill.date.toEpochDay()}/${kind.name}/${notifyOn.toEpochDay()}"
}

fun Records.reminderEvents(date: LocalDate): List<ReminderEvent> {
    val bySeries = reminders.filter { it.active }.associateBy { it.series }
    val oneOff = oneOffReminders.filter { it.notifyOn == date }.groupBy { it.key.due }
        .flatMap { (due, reminders) ->
            val keys = reminders.map { it.key }.toSet()
            occurrences(due, due).filter { it.open && it.key in keys }
                .map { ReminderEvent(it, ReminderKind.ONE_OFF, date) }
        }
    if (bySeries.isEmpty()) return oneOff
    val latestIncrease = if (bySeries.values.any { it.increase }) {
        schedules.map { it.currency }.distinct().flatMap { billChanges(date, it) }
            .filter { it.current.date == date && it.change.signum() > 0 }.map { it.current.key }.toSet()
    } else emptySet()
    return oneOff + occurrences(date.minusDays(1), date.plusDays((bySeries.values.mapNotNull { it.leadDays }.maxOrNull() ?: 0).toLong()))
        .filter { it.open && it.schedule.series in bySeries }
        .flatMap { bill ->
            val rule = bySeries.getValue(bill.schedule.series)
            buildList {
                if (rule.leadDays?.let { bill.date.minusDays(it.toLong()) == date } == true) add(ReminderKind.UPCOMING)
                if (rule.due && bill.date == date) add(ReminderKind.DUE)
                if (rule.overdue && bill.date.plusDays(1) == date) add(ReminderKind.OVERDUE)
                if (rule.increase && bill.key in latestIncrease) add(ReminderKind.INCREASE)
            }.map { ReminderEvent(bill, it) }
        }
}

data class ReminderHours(val hour: Int = 9, val quietStart: Int = 22, val quietEnd: Int = 7) {
    fun firstAllowed(time: ZonedDateTime): ZonedDateTime {
        val local = time.toLocalTime()
        val start = LocalTime.of(quietStart, 0)
        val end = LocalTime.of(quietEnd, 0)
        if (start == end) return time
        val quiet = if (start < end) local >= start && local < end else local >= start || local < end
        if (!quiet) return time
        val day = if (start > end && local >= start) time.toLocalDate().plusDays(1) else time.toLocalDate()
        return day.atTime(end).atZone(time.zone)
    }

    fun delivery(day: LocalDate, zone: java.time.ZoneId) = firstAllowed(day.atTime(hour, 0).atZone(zone))
}
