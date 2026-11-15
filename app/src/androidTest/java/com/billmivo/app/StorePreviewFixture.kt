package com.billmivo.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StorePreviewFixture {
    @Test fun seedFictionalHousehold() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("demo") != "true") return@runBlocking
        check(instrumentation.targetContext.packageName.endsWith(".debug"))
        val repo = (instrumentation.targetContext.applicationContext as BillmivoApplication).repository
        check(repo.records.first().schedules.isEmpty()) { "Use a fresh debug install for previews." }

        val today = LocalDate.now()
        suspend fun bill(key: String, name: String, category: String, amount: String, date: LocalDate,
                         cadence: Cadence, estimated: Boolean = false): Schedule {
            repo.save(Schedule(series = key, name = name, category = category, currency = "GBP",
                amount = amount.toBigDecimal(), estimated = estimated, first = date, cadence = cadence), null, today)
            return repo.records.first().schedules.single { it.series == key }
        }

        val rentStart = today.minusMonths(5).withDayOfMonth(1)
        val rent = bill("rent", "Household rent", "housing", "780.00", rentStart, Cadence.MONTHLY)
        rent.dates(rentStart, today).forEach { due ->
            repo.pay(OccurrenceKey(rent.id, due), rent.amount, due, today)
        }
        val electricityStart = rentStart.withDayOfMonth(5)
        val electricity = bill("energy", "Electricity", "utilities", "101.50", electricityStart, Cadence.MONTHLY, estimated = true)
        val actualAmounts = listOf("82.50", "88.20", "85.00", "92.70", "86.50", "103.80")
        electricity.dates(electricityStart, today).forEachIndexed { index, due ->
            val actual = actualAmounts[index].toBigDecimal()
            val key = OccurrenceKey(electricity.id, due)
            repo.confirm(key, actual)
            repo.pay(key, actual, due, today)
        }

        bill("water", "Water bill", "utilities", "37.50", today.minusDays(2), Cadence.ONCE)
        bill("fiber", "Home broadband", "internet", "29.95", today.plusDays(2), Cadence.MONTHLY)
        bill("insurance", "Home insurance", "insurance", "64.00", today.plusDays(5), Cadence.MONTHLY)
        bill("gas", "Gas", "utilities", "96.50", today.plusDays(9), Cadence.MONTHLY, estimated = true)
        bill("tax", "Council tax", "tax", "138.00", today.plusDays(13), Cadence.MONTHLY)
    }
}
