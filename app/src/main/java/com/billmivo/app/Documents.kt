package com.billmivo.app

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class Provider(
    val id: String, val name: String, val category: String = "other", val phone: String = "",
    val email: String = "", val website: String = "", val reference: String = "", val notes: String = ""
)

data class ProviderContact(val id: String, val providerId: String, val date: LocalDate, val note: String)
data class BillDocument(
    val id: String, val billId: Long, val name: String, val mimeType: String, val size: Long, val added: LocalDate
)

class DocumentDisk(private val app: BillmivoApplication) {
    private val mutex = Mutex()
    private val folder = File(app.filesDir, "bill-documents")

    internal fun file(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(folder, id)
    }

    suspend fun import(uri: Uri, billId: Long): BillDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(uri.scheme == "content")
            val resolver = app.contentResolver
            val mime = requireNotNull(resolver.getType(uri))
            require(mime in types) { "Unsupported document media type" }
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.replace(Regex("[\\p{Cc}]"), " ")?.trim()?.take(150)?.takeIf(String::isNotBlank)
                ?: app.getString(R.string.document_default_name)
            val id = UUID.randomUUID().toString()
            val destination = file(id)
            check(folder.isDirectory || folder.mkdirs())
            var committed = false
            try {
                val size = requireNotNull(resolver.openInputStream(uri)).use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var written = 0L
                        while (true) {
                            val length = input.read(buffer)
                            if (length < 0) break
                            written += length
                            require(written <= limit) { "Document exceeds size limit" }
                            output.write(buffer, 0, length)
                        }
                        require(written > 0) { "Empty document" }
                        written
                    }
                }
                val document = BillDocument(id, billId, name, mime, size, LocalDate.now())
                withContext(NonCancellable) {
                    app.repository.saveDocument(document)
                    committed = true
                }
                document
            } finally {
                if (!committed) destination.delete()
            }
        }
    }

    suspend fun remove(document: BillDocument) = withContext(Dispatchers.IO) {
        mutex.withLock {
            app.repository.removeDocument(document.id)
            file(document.id).delete()
        }
    }

    suspend fun prune() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = folder.listFiles() ?: return@withLock
            val known = app.repository.records.first().documents.map { it.id }.toSet()
            files.filter { it.isFile && it.name !in known }.forEach { it.delete() }
        }
    }

    fun viewer(document: BillDocument): Intent {
        val target = file(document.id)
        check(target.isFile)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.documents", target)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(view, null)
    }

    companion object {
        private const val limit = 20L * 1024 * 1024
        val types = setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain")
    }
}
