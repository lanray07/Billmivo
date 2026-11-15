package com.billmivo.app

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentsReportsTest {
    @Test fun linksProviderAndDocumentAndProducesReadablePdfAndSafeCsv() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<BillmivoApplication>()
        val repo = app.repository
        val today = LocalDate.now()
        val series = UUID.randomUUID().toString()
        val provider = Provider(UUID.randomUUID().toString(), "Test provider", notes = "Fictional")
        val input = File(File(app.filesDir, "bill-documents"), "test-${UUID.randomUUID()}.txt")
        val pdf = File.createTempFile("billmivo-test-", ".pdf", app.cacheDir)
        try {
            repo.saveProvider(provider)
            repo.recordContact(ProviderContact(UUID.randomUUID().toString(), provider.id, today, "Asked about a bill"))
            repo.save(Schedule(series = series, name = "=Report fixture", category = "utilities", currency = "GBP",
                amount = BigDecimal("19.05"), estimated = false, first = today, cadence = Cadence.ONCE,
                providerId = provider.id), null, today)
            val bill = repo.records.first().schedules.single { it.series == series }
            repo.pay(OccurrenceKey(bill.id, today), BigDecimal("9.05"), today, today)
            input.parentFile?.mkdirs()
            input.writeText("Receipt fixture")
            val source = FileProvider.getUriForFile(app, "${app.packageName}.documents", input)
            val document = app.documents.import(source, bill.id)
            assertEquals("Receipt fixture", app.documents.file(document.id).readText())
            assertEquals("android.intent.action.CHOOSER", app.documents.viewer(document).action)
            val records = repo.records.first()
            assertEquals(provider.id, records.schedules.single { it.series == series }.providerId)
            assertEquals("Asked about a bill", records.providerContacts.single { it.providerId == provider.id }.note)
            assertEquals(document.id, records.documents.single { it.billId == bill.id }.id)
            val providers = records.report(ReportKind.Vendors, today, app)
            assertEquals(BigDecimal("9.05"), providers.rows.single { it.item == provider.name }.amount)
            val report = records.report(ReportKind.Payments, today, app)
                .let { it.copy(rows = it.rows.filter { row -> row.item == "=Report fixture" }) }
            val csv = ByteArrayOutputStream().also { report.writeCsv(it) }.toString("UTF-8")
            assertTrue(csv.contains("\"'=Report fixture\""))
            assertTrue(csv.contains("\"9.05\""))
            pdf.outputStream().use { report.writePdf(it, Locale.UK) }
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).let { descriptor ->
                PdfRenderer(descriptor).use { renderer -> assertEquals(1, renderer.pageCount) }
            }
            repo.deleteProvider(provider.id)
            val updated = repo.records.first()
            assertNull(updated.schedules.single { it.series == series }.providerId)
            assertEquals(BigDecimal("9.05"), updated.payments.single { it.key.scheduleId == bill.id }.amount)
            assertFalse(updated.providerContacts.any { it.providerId == provider.id })
            repo.delete(series)
            app.documents.prune()
            assertFalse(app.documents.file(document.id).exists())
        } finally {
            repo.delete(series)
            repo.deleteProvider(provider.id)
            app.documents.prune()
            input.delete()
            pdf.delete()
        }
        Unit
    }
}
