package com.billmivo.app

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedBackupTest {
    @Test fun encryptsRestoresAndErasesAnIsolatedHousehold() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<BillmivoApplication>()
        val db = Room.inMemoryDatabaseBuilder(app, BillDatabase::class.java).build()
        val repo = BillRepository(db)
        val testFiles = File(app.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val disk = DocumentDisk(app, repo, testFiles)
        val resolverFile = File(File(app.filesDir, "bill-documents").apply { mkdirs() }, "backup-test-${UUID.randomUUID()}")
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.documents", resolverFile)
        val backup = EncryptedBackup(app.contentResolver, repo, disk) {}
        val password = "fixture passphrase 9575".toCharArray()
        val today = LocalDate.of(2026, 11, 16)
        try {
            repo.save(Schedule(series = "source", name = "Private water bill", category = "utilities", currency = "GBP",
                amount = BigDecimal("41.79"), estimated = false, first = today, cadence = Cadence.MONTHLY), null, today)
            val original = repo.records.first().schedules.single()
            repo.pay(OccurrenceKey(original.id, today), BigDecimal("11.40"), today, today)
            val id = UUID.randomUUID().toString()
            val contents = "Private receipt fixture"
            disk.file(id).writeText(contents)
            repo.saveDocument(BillDocument(id, original.id, "Receipt.txt", "text/plain", contents.length.toLong(), today))
            backup.export(uri, password)
            val bytes = resolverFile.readBytes()
            assertFalse(bytes.decodeToString().contains("Private water bill"))
            assertFalse(bytes.decodeToString().contains(contents))

            repo.delete(original.series)
            disk.prune()
            repo.save(original.copy(id = 0, series = "keep-until-authenticated", name = "Current household"), null, today)
            assertTrue(runCatching { backup.restore(uri, "incorrect passphrase".toCharArray()) }.isFailure)
            assertEquals("Current household", repo.records.first().schedules.single().name)
            resolverFile.writeBytes(bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            assertTrue(runCatching { backup.restore(uri, password) }.isFailure)
            assertEquals("Current household", repo.records.first().schedules.single().name)

            resolverFile.writeBytes(bytes)
            backup.restore(uri, password)
            val restored = repo.records.first()
            assertEquals(original, restored.schedules.single())
            assertEquals(BigDecimal("11.40"), restored.payments.single().amount)
            val document = restored.documents.single()
            assertFalse(id == document.id)
            assertEquals(contents, disk.file(document.id).readText())

            backup.erase()
            assertTrue(repo.records.first().schedules.isEmpty())
            assertTrue(repo.records.first().payments.isEmpty())
            assertFalse(disk.file(document.id).exists())
        } finally {
            password.fill('\u0000')
            db.close()
            resolverFile.delete()
            testFiles.deleteRecursively()
        }
        Unit
    }
}
