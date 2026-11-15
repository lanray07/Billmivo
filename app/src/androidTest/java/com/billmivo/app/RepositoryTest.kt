package com.billmivo.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun editsAndConcurrentPaymentsKeepRecordedHistoryConsistent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BillDatabase::class.java).build()
        try {
            val repo = BillRepository(db)
            val today = LocalDate.of(2026, 11, 15)
            val first = today.minusMonths(1)
            repo.save(Schedule(series = "broadband", name = "Broadband", category = "internet", currency = "GBP",
                amount = "100.00".toBigDecimal(), estimated = true, first = first, cadence = Cadence.MONTHLY), null, today)
            val old = repo.records.first().schedules.single()
            val past = OccurrenceKey(old.id, first)
            repo.confirm(past, "85.00".toBigDecimal())
            repo.pay(past, "85.00".toBigDecimal(), first, today)
            val current = OccurrenceKey(old.id, today)
            repo.confirm(current, "100.00".toBigDecimal())
            val results = List(2) { async { runCatching { repo.pay(current, "75.00".toBigDecimal(), today, today) } } }
            assertEquals(1, results.count { it.await().isSuccess })
            repo.save(old.copy(id = 0, amount = "140.00".toBigDecimal()), old, today)
            val records = repo.records.first()
            val forecast = records.forecast(today, today, today.plusMonths(1))
            assertEquals("25.00".toBigDecimal(), forecast.totals("GBP").confirmed)
            assertEquals("140.00".toBigDecimal(), forecast.totals("GBP").estimated)
            assertTrue(forecast.overdue.isEmpty())
            assertEquals(2, records.payments.size)
            assertEquals("85.00".toBigDecimal(), records.occurrences(first, first).single().paid)
        } finally {
            db.close()
        }
    }
}
