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

enum class ReminderKind { UPCOMING, DUE, OVERDUE, INCREASE }

data class ReminderEvent(val bill: Occurrence, val kind: ReminderKind) {
    val id get() = "${bill.schedule.id}/${bill.date.toEpochDay()}/${kind.name}"
}

fun Records.reminderEvents(date: LocalDate): List<ReminderEvent> {
    val bySeries = reminders.filter { it.active }.associateBy { it.series }
    if (bySeries.isEmpty()) return emptyList()
    val latestIncrease = if (bySeries.values.any { it.increase }) {
        schedules.map { it.currency }.distinct().flatMap { billChanges(date, it) }
            .filter { it.current.date == date && it.change.signum() > 0 }.map { it.current.key }.toSet()
    } else emptySet()
    return occurrences(date.minusDays(1), date.plusDays((bySeries.values.mapNotNull { it.leadDays }.maxOrNull() ?: 0).toLong()))
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
