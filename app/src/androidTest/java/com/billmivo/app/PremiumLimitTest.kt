package com.billmivo.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PremiumLimitTest {
    @Test fun enforcesRecurringLimitWithoutRestrictingExistingBillsOrOneTimeBills() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BillDatabase::class.java).build()
        try {
            var premium = false
            val repo = BillRepository(db) { premium }
            val today = LocalDate.now()
            fun bill(index: Int, cadence: Cadence = Cadence.MONTHLY) = Schedule(
                series = "series-$index", name = "Bill $index", category = "other", currency = "GBP",
                amount = BigDecimal("5.00"), estimated = false, first = today, cadence = cadence
            )
            repeat(10) { repo.save(bill(it), null, today) }
            assertTrue(runCatching { repo.save(bill(10), null, today) }.exceptionOrNull() is RecurringBillLimit)
            repo.save(bill(11, Cadence.ONCE), null, today)
            val existing = repo.records.first().schedules.first { it.series == "series-0" }
            repo.save(existing.copy(id = 0, amount = BigDecimal("6.00")), existing, today)
            premium = true
            repo.save(bill(10), null, today)
            premium = false
            val records = repo.records.first()
            assertEquals(12, records.schedules.count { it.until == null })
            assertEquals(12, records.forecast(today, today, today).upcoming.size)
        } finally {
            db.close()
        }
    }
}
