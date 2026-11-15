package com.billmivo.app

import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @Test fun postsPrivateReminderThroughAlarmWhileActivityIsClosedAndCancelsWhenPaid() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<BillmivoApplication>()
        val service = app.reminders
        val repo = app.repository
        val manager = app.getSystemService(NotificationManager::class.java)
        val previous = service.preferences()
        val now = ZonedDateTime.now()
        val date = now.toLocalDate()
        val series = UUID.randomUUID().toString()
        fun current() = manager.activeNotifications.firstOrNull { it.id == BillReminders.NOTIFICATION }?.notification
        try {
            service.storePreferences(ReminderPreferences(enabled = true, hours = ReminderHours(now.hour, 0, 0)))
            service.reconcile()
            assertTrue("Notifications must be enabled for this hardware test", service.canNotify())
            repo.save(Schedule(series = series, name = "Private test bill", category = "other", currency = "GBP",
                amount = BigDecimal("17.30"), estimated = false, first = date, cadence = Cadence.ONCE), null, date)
            val bill = repo.records.first().schedules.single { it.series == series }
            repo.saveReminder(ReminderRule(series, due = true))
            service.scheduleAt(Instant.now().plusSeconds(6))
            assertNull(current())
            val posted = withTimeout(50_000L) {
                var result = current()
                while (result == null) {
                    delay(250)
                    result = current()
                }
                result
            }
            assertEquals(app.getString(R.string.reminder_message),
                posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
            posted.actions.single().actionIntent.send()
            withTimeout(8_000L) { while (current() != null) delay(100) }
            service.reconcile(now = ZonedDateTime.now().plusMinutes(61))
            assertNotNull("The snoozed reminder should return after its delay", current())
            repo.pay(OccurrenceKey(bill.id, date), bill.amount, date, date)
            service.reconcile()
            assertNull(current())
        } finally {
            repo.delete(series)
            service.storePreferences(previous)
            service.reconcile()
        }
    }
}
