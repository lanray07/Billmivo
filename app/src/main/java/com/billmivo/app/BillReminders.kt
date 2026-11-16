package com.billmivo.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime

data class ReminderPreferences(
    val enabled: Boolean = false, val hours: ReminderHours = ReminderHours(), val previews: Boolean = false
)

class BillReminders(private val app: BillmivoApplication) {
    private val storage = app.getSharedPreferences("bill-reminders", Context.MODE_PRIVATE)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun preferences() = ReminderPreferences(storage.getBoolean("enabled", false),
        ReminderHours(storage.getInt("hour", 9), storage.getInt("quiet_start", 22), storage.getInt("quiet_end", 7)),
        storage.getBoolean("previews", false))

    fun save(value: ReminderPreferences) {
        storePreferences(value)
        refresh()
    }

    internal fun storePreferences(value: ReminderPreferences) {
        storage.edit().putBoolean("enabled", value.enabled).putInt("hour", value.hours.hour)
            .putInt("quiet_start", value.hours.quietStart).putInt("quiet_end", value.hours.quietEnd)
            .putBoolean("previews", value.previews).apply()
    }

    fun canNotify(): Boolean = notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun refresh(snooze: Boolean = false, done: () -> Unit = {}) {
        scope.launch {
            try {
                reconcile(snooze)
            } catch (failure: Exception) {
                Log.w("BillReminders", "Could not refresh bill reminders", failure)
            } finally {
                done()
            }
        }
    }

    private fun broadcast(action: String, code: Int) = PendingIntent.getBroadcast(app, code,
        Intent(app, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun scheduleAt(time: Instant) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toEpochMilli(), broadcast(ALARM, 1))
    }

    internal suspend fun reconcile(snooze: Boolean = false, now: ZonedDateTime = ZonedDateTime.now()) = mutex.withLock {
        val options = preferences()
        val records = if (options.enabled && canNotify()) app.repository.records.first() else null
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)
        val recurring = records?.reminders?.any { it.active } == true
        val scheduledDays = records?.oneOffReminders.orEmpty().map { it.notifyOn }.filter { it >= yesterday }.toSet()
        if (records == null || (!recurring && scheduledDays.isEmpty())) {
            alarms.cancel(broadcast(ALARM, 1))
            notifications.cancel(NOTIFICATION)
            storage.edit().remove("visible").remove("snoozed").remove("snooze_time").apply()
            return@withLock
        }
        val hours = options.hours
        val openAt = hours.firstAllowed(now)
        val allowed = !openAt.isAfter(now)
        val candidates = listOf(yesterday, today).associateWith { records.reminderEvents(it) }
        val open = candidates.values.flatten().associateBy { it.id }
        val ready = candidates.entries.filter { (day, _) ->
            val delivery = hours.delivery(day, now.zone)
            !delivery.isAfter(now) && delivery.toLocalDate() >= today
        }.flatMap { it.value }.map { it.id }.toSet()
        val seen = storage.getStringSet("seen", emptySet()).orEmpty().intersect(open.keys)
        var visible = storage.getStringSet("visible", emptySet()).orEmpty().intersect(open.keys)
        var deferred = storage.getStringSet("snoozed", emptySet()).orEmpty().intersect(open.keys)
        var snoozeTime = Instant.ofEpochMilli(storage.getLong("snooze_time", 0L))
        if (snooze && visible.isNotEmpty()) {
            deferred = deferred + visible
            snoozeTime = hours.firstAllowed(now.plusHours(1)).toInstant()
            visible = emptySet()
            notifications.cancel(NOTIFICATION)
        }

        val dueSnooze = allowed && now.toInstant() >= snoozeTime
        val revived = if (dueSnooze) deferred else emptySet()
        val added = if (allowed) (ready - seen - deferred) + revived else emptySet()
        if (dueSnooze) deferred = emptySet()
        visible = visible + added
        if (visible.isEmpty()) {
            notifications.cancel(NOTIFICATION)
        } else if (added.isNotEmpty() || visible != storage.getStringSet("visible", emptySet()).orEmpty() ||
            storage.getBoolean("last_preview", false) != options.previews) {
            post(visible.mapNotNull(open::get), options.previews, added.isEmpty())
        }
        storage.edit().putStringSet("seen", seen + added).putStringSet("visible", visible)
            .putStringSet("snoozed", deferred).putLong("snooze_time", snoozeTime.toEpochMilli())
            .putBoolean("last_preview", options.previews).apply()

        val days = if (recurring) listOf(yesterday, today, today.plusDays(1)) else scheduledDays
        val next = days.map { hours.delivery(it, now.zone).toInstant() }
            .filter { it.isAfter(now.toInstant()) }.toMutableList()
        if (!allowed && ready.any { it !in seen }) next.add(openAt.toInstant())
        if (deferred.isNotEmpty()) next.add(maxOf(snoozeTime, openAt.toInstant()))
        val checkAt = next.minOrNull()
        if (checkAt != null) scheduleAt(checkAt) else alarms.cancel(broadcast(ALARM, 1))
    }

    private fun post(events: List<ReminderEvent>, previews: Boolean, silent: Boolean) {
        val localized = ContextCompat.getContextForLanguage(app)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            localized.getString(R.string.reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val landing = PendingIntent.getActivity(app, 3,
            Intent(app, MainActivity::class.java).setAction(OPEN_REMINDERS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val general = localized.getString(R.string.reminder_message)
        val details = events.distinctBy { it.bill.key }.take(3).joinToString("\n") { event ->
            localized.getString(R.string.reminder_notification_line, event.bill.schedule.name,
                dateLabel(event.bill.date, localized.resources.configuration.locales[0]))
        }
        val body = if (previews && details.isNotBlank()) details else general
        fun base() = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle(localized.getString(R.string.reminders))
        val notification = base().setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(landing).setAutoCancel(true).setOnlyAlertOnce(silent)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(base().setContentText(general).build())
            .addAction(Notification.Action.Builder(null, localized.getString(R.string.reminder_snooze),
                broadcast(SNOOZE, 2)).build()).build()
        notifications.notify(NOTIFICATION, notification)
    }

    companion object {
        internal const val NOTIFICATION = 4201
        internal const val CHANNEL = "bill-reminders"
        internal const val ALARM = "com.billmivo.app.REMINDER_CHECK"
        internal const val SNOOZE = "com.billmivo.app.REMINDER_SNOOZE"
        internal const val OPEN_REMINDERS = "com.billmivo.app.OPEN_REMINDERS"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as BillmivoApplication
        app.reminders.refresh(intent.action == BillReminders.SNOOZE) { pending.finish() }
    }
}
