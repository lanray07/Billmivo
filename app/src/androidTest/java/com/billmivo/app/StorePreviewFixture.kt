package com.billmivo.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StorePreviewFixture {
    @Test fun seedFictionalHousehold() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("demo") != "true") return@runBlocking
        check(instrumentation.targetContext.packageName.endsWith(".debug"))
        val app = instrumentation.targetContext.applicationContext as BillmivoApplication
        val repo = app.repository
        val language = InstrumentationRegistry.getArguments().getString("previewLanguage") ?: "en"
        val labels = mapOf(
            "en" to listOf("Household rent", "Electricity", "Water bill", "Home broadband", "Home insurance", "Gas", "Council tax"),
            "fr" to listOf("Loyer", "Électricité", "Facture d’eau", "Internet à domicile", "Assurance habitation", "Gaz", "Taxes locales"),
            "es" to listOf("Alquiler", "Electricidad", "Factura del agua", "Internet en casa", "Seguro del hogar", "Gas", "Impuestos locales"),
            "de" to listOf("Miete", "Stromrechnung", "Wasserrechnung", "Internet zu Hause", "Hausratversicherung", "Gas", "Kommunale Abgaben")
        )
        val keys = listOf("rent", "energy", "water", "fiber", "insurance", "gas", "tax")
        val names = keys.zip(labels.getValue(language)).toMap()
        val prior = repo.records.first()
        check(prior.schedules.groupingBy { it.series }.eachCount().values.all { it == 1 } &&
            prior.schedules.all { bill -> labels.values.any { keys.zip(it).toMap()[bill.series] == bill.name } } &&
            prior.members.all { it.id in setOf("preview-alex", "preview-jamie") }) {
            "The preview fixture requires either an empty debug household or existing preview records."
        }
        prior.schedules.forEach { repo.delete(it.series) }
        app.reminders.storePreferences(ReminderPreferences())
        app.reminders.reconcile()

        val today = LocalDate.now()
        val currency = if (language == "en") "GBP" else "EUR"
        val alex = Member("preview-alex", if (language == "es") "Álex" else "Alex")
        val jamie = Member("preview-jamie", when (language) { "fr" -> "Camille"; "es" -> "Dani"; else -> "Jamie" })
        repo.saveMember(alex)
        repo.saveMember(jamie)
        suspend fun bill(key: String, category: String, amount: String, date: LocalDate,
                         cadence: Cadence, estimated: Boolean = false): Schedule {
            repo.save(Schedule(series = key, name = names.getValue(key), category = category, currency = currency,
                amount = amount.toBigDecimal(), estimated = estimated, first = date, cadence = cadence), null, today)
            return repo.records.first().schedules.single { it.series == key }
        }

        val rentStart = today.minusMonths(5).withDayOfMonth(1)
        val rent = bill("rent", "housing", "780.00", rentStart, Cadence.MONTHLY)
        rent.dates(rentStart, today).forEach { due ->
            repo.pay(OccurrenceKey(rent.id, due), rent.amount, due, today, alex.id)
        }
        val electricityStart = rentStart.withDayOfMonth(5)
        val electricity = bill("energy", "utilities", "101.50", electricityStart, Cadence.MONTHLY, estimated = true)
        val actualAmounts = listOf("82.50", "88.20", "85.00", "92.70", "86.50", "103.80")
        electricity.dates(electricityStart, today).forEachIndexed { index, due ->
            val actual = actualAmounts[index].toBigDecimal()
            val key = OccurrenceKey(electricity.id, due)
            repo.confirm(key, actual)
            repo.pay(key, actual, due, today, jamie.id)
        }

        bill("water", "utilities", "37.50", today.minusDays(2), Cadence.ONCE)
        bill("fiber", "internet", "29.95", today.plusDays(2), Cadence.MONTHLY)
        bill("insurance", "insurance", "64.00", today.plusDays(5), Cadence.MONTHLY)
        bill("gas", "utilities", "96.50", today.plusDays(9), Cadence.MONTHLY, estimated = true)
        bill("tax", "tax", "138.00", today.plusDays(13), Cadence.MONTHLY)
        repo.saveReminder(ReminderRule("fiber", leadDays = 2, due = true, overdue = true))
        repo.saveReminder(ReminderRule("energy", leadDays = 3, due = true, increase = true))
        repo.saveSharing(SharingRule("rent", today, SplitMode.EQUAL, alex.id,
            listOf(SplitPart(alex.id, BigDecimal.ONE), SplitPart(jamie.id, BigDecimal.ONE))), today)
        repo.saveSharing(SharingRule("fiber", today, SplitMode.PERCENT, jamie.id,
            listOf(SplitPart(alex.id, BigDecimal("60")), SplitPart(jamie.id, BigDecimal("40")))), today)
    }
}
