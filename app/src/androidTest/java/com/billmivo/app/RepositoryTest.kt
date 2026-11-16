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
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun keepsPaymentDetailsAndScheduleHistoryThroughPauseAndCategoryChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BillDatabase::class.java).build()
        try {
            val repo = BillRepository(db)
            val first = LocalDate.of(2026, 11, 16)
            val category = CustomCategory("custom:utilities", "Utilities at home")
            repo.saveCategory(category)
            val provider = Provider("water", "Water provider", category.id)
            repo.saveProvider(provider)
            repo.save(Schedule(series = "water", name = "Water", category = category.id, currency = "GBP",
                amount = BigDecimal("40.00"), estimated = false, first = first, cadence = Cadence.DAILY,
                providerId = provider.id, notes = "Metered account"), null, first)
            val bill = repo.records.first().schedules.single()
            repo.pay(OccurrenceKey(bill.id, first.plusDays(2)), BigDecimal("10.00"), first, first,
                method = "Bank transfer", notes = "First instalment")
            repo.setPaused(bill.series, first.plusDays(1), true)
            val paused = repo.records.first()
            assertTrue(paused.isPaused(bill.series))
            assertEquals(listOf(first, first.plusDays(2)), paused.occurrences(first, first.plusDays(4)).map { it.date })
            assertEquals(BigDecimal("30.00"), paused.occurrences(first, first.plusDays(4)).last().remaining)

            repo.setPaused(bill.series, first.plusDays(4), false)
            repo.saveCategory(category.copy(name = "Water and utilities"))
            val resumed = repo.records.first()
            assertEquals(listOf(first, first.plusDays(2), first.plusDays(4), first.plusDays(5)),
                resumed.occurrences(first, first.plusDays(5)).map { it.date })
            assertEquals("Water and utilities", resumed.customCategories.single().name)
            assertEquals("Metered account", resumed.schedules.single().notes)
            assertEquals("Bank transfer", resumed.payments.single().method)
            assertEquals("First instalment", resumed.payments.single().notes)

            repo.deleteCategory(category.id)
            val uncategorized = repo.records.first()
            assertEquals("other", uncategorized.schedules.single().category)
            assertEquals("other", uncategorized.providers.single().category)
            assertEquals(resumed.payments, uncategorized.payments)
        } finally {
            db.close()
        }
    }
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
