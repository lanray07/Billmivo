package com.billmivo.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SharingMigrationTest {
    @Test fun migratesVersionOneDataAndKeepsAllocationsSeparateFromPayments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val schema = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.billmivo.app.BillDatabase/1.json").bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database").getJSONArray("entities")
        val today = LocalDate.of(2026, 11, 15)
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            for (i in 0 until schema.length()) {
                val entry = schema.getJSONObject(i)
                database.execSQL(entry.getString("createSql").replace("\${TABLE_NAME}", entry.getString("tableName")))
                val indices = entry.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    database.execSQL(indices.getJSONObject(j).getString("createSql")
                        .replace("\${TABLE_NAME}", entry.getString("tableName")))
                }
            }
            database.execSQL("INSERT INTO schedules VALUES (1, 'rent', 'Rent', 'housing', 'GBP', '36.01', 0, ?, 'MONTHLY', 1, ?, NULL)",
                arrayOf(today.toEpochDay(), today.toEpochDay()))
            database.execSQL("INSERT INTO payments VALUES (1, 1, ?, ?, '2.34')",
                arrayOf(today.toEpochDay(), today.toEpochDay()))
            database.version = 1
        }
        val db = Room.databaseBuilder(context, BillDatabase::class.java, name).addMigrations(migration1To2, migration2To3).build()
        try {
            val repo = BillRepository(db)
            val initial = repo.records.first()
            assertEquals("Rent", initial.schedules.single().name)
            assertEquals(BigDecimal("2.34"), initial.payments.single().amount)
            assertNull(initial.payments.single().payerId)
            assertTrue(initial.members.isEmpty())
            val sam = Member("sam", "Sam")
            val robin = Member("robin", "Robin")
            repo.saveMember(sam)
            repo.saveMember(robin)
            repo.saveSharing(SharingRule("rent", today, SplitMode.EQUAL, robin.id,
                listOf(SplitPart(sam.id, BigDecimal.ONE), SplitPart(robin.id, BigDecimal.ONE))), today)
            val key = OccurrenceKey(1, today)
            repo.pay(key, BigDecimal("4.50"), today, today, sam.id)
            val current = repo.records.first()
            assertNull(current.sharingFor("rent", today.minusDays(1)))
            val rule = current.sharingFor("rent", today)!!
            assertEquals(robin.id, rule.responsibleId)
            assertEquals(BigDecimal("36.01"), rule.allocate(BigDecimal("36.01"), "GBP")!!.values.sumOf { it })
            assertEquals(listOf(sam.id), current.payments.mapNotNull { it.payerId })
            assertEquals(BigDecimal("29.17"), current.occurrences(today, today).single().remaining)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
        Unit
    }
}
