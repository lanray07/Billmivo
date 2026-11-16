package com.billmivo.app

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val legacyArchiveTables = listOf("providers", "members", "schedules", "payments", "adjustments",
    "sharing", "reminders", "provider_contacts", "documents")
private val archiveTables = listOf("custom_categories") + legacyArchiveTables + "recurrence_pauses"
private val legacyMissingColumns = mapOf("schedules" to setOf("notes", "paymentMethod"), "payments" to setOf("method", "notes"))
private const val metadataLimit = 4 * 1024 * 1024
private const val payloadLimit = 24 * 1024 * 1024

// The format identifier also fixes the password derivation and payload format for this version.
private val backupMagic = "BILLMIVO-BACKUP-1\n".encodeToByteArray()
internal const val backupPasswordMinimum = 12

internal class BackupEnvelope(private val password: CharArray) {
    fun write(output: OutputStream, payload: (DataOutputStream) -> Unit) {
        val header = backupMagic + ByteArray(28).also(SecureRandom()::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, header)
        output.write(header)
        DataOutputStream(CipherOutputStream(output, cipher)).use(payload)
    }

    fun read(input: InputStream): ByteArray {
        val reader = DataInputStream(input)
        val header = ByteArray(backupMagic.size + 28).also(reader::readFully)
        require(header.copyOf(backupMagic.size).contentEquals(backupMagic))
        val cipher = cipher(Cipher.DECRYPT_MODE, header)
        val ciphertext = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = reader.read(buffer)
            if (count == -1) break
            require(ciphertext.size() + count <= payloadLimit + 16)
            ciphertext.write(buffer, 0, count)
        }
        return cipher.doFinal(ciphertext.toByteArray())
    }

    private fun cipher(mode: Int, header: ByteArray): Cipher {
        val spec = PBEKeySpec(password, header.copyOfRange(backupMagic.size, backupMagic.size + 16), 600_000, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.takeLast(12).toByteArray()))
                updateAAD(header)
            }
        } finally {
            key.fill(0)
        }
    }
}

class EncryptedBackup internal constructor(private val resolver: ContentResolver, private val repository: BillRepository,
                                           private val documents: DocumentDisk, private val refreshReminders: () -> Unit) {
    constructor(app: BillmivoApplication) : this(app.contentResolver, app.repository, app.documents, { app.reminders.refresh() })

    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" && password.size in backupPasswordMinimum..128)
        documents.withFiles {
            val data = repository.archiveTransaction(::snapshot)
            val metadata = data.toString().encodeToByteArray()
            val files = attachmentList(data)
            require(metadata.size <= metadataLimit && 4L + metadata.size + files.sumOf { it.second } <= payloadLimit)
            files.forEach { (id, size) -> check(documents.file(id).let { it.isFile && it.length() == size }) }
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { output ->
                BackupEnvelope(password).write(output) { target ->
                    target.writeInt(metadata.size)
                    target.write(metadata)
                    files.forEach { (id, size) ->
                        check(documents.file(id).inputStream().use { it.copyTo(target) } == size)
                    }
                }
            }
        }
    }

    suspend fun restore(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" && password.size in backupPasswordMinimum..128)
        val payload = requireNotNull(resolver.openInputStream(uri)).use(BackupEnvelope(password)::read)
        try {
            val source = DataInputStream(payload.inputStream())
            val size = source.readInt()
            require(size in 1..metadataLimit && size <= source.available())
            val metadata = ByteArray(size).also(source::readFully)
            val data = JSONObject(metadata.decodeToString(throwOnInvalidSequence = true))
            metadata.fill(0)
            val tables = when (data.optInt("schema", 3)) {
                3 -> legacyArchiveTables
                4 -> archiveTables
                else -> error("Unsupported backup schema")
            }
            val files = attachmentList(data)
            require(files.sumOf { it.second } == source.available().toLong())
            documents.withFiles {
                val installed = mutableListOf<File>()
                var committed = false
                try {
                    val rows = data.getJSONArray("documents")
                    val buffer = ByteArray(64 * 1024)
                    files.forEachIndexed { index, (_, sizeInBytes) ->
                        val id = UUID.randomUUID().toString()
                        val target = documents.file(id)
                        check(target.parentFile!!.let { it.isDirectory || it.mkdirs() })
                        check(target.createNewFile())
                        installed += target
                        target.outputStream().use { output ->
                            var remaining = sizeInBytes
                            while (remaining > 0) {
                                val count = minOf(buffer.size.toLong(), remaining).toInt()
                                source.readFully(buffer, 0, count)
                                output.write(buffer, 0, count)
                                remaining -= count
                            }
                            output.fd.sync()
                        }
                        rows.getJSONObject(index).put("id", id)
                    }
                    buffer.fill(0)
                    withContext(NonCancellable) {
                        repository.archiveTransaction { db ->
                            clearRows(db)
                            tables.forEach { table ->
                                val missing = if (tables == legacyArchiveTables) legacyMissingColumns[table].orEmpty() else emptySet()
                                insertRows(db, table, data.getJSONArray(table), missing)
                            }
                            repository.validateRestoredRecords()
                        }
                        committed = true
                    }
                } finally {
                    if (!committed) installed.forEach(File::delete)
                }
            }
            documents.prune()
            refreshReminders()
        } finally {
            payload.fill(0)
        }
    }

    suspend fun erase() = withContext(Dispatchers.IO) {
        documents.withFiles { repository.archiveTransaction(::clearRows) }
        documents.prune()
        refreshReminders()
    }

    private fun attachmentList(data: JSONObject): List<Pair<String, Long>> {
        val rows = data.getJSONArray("documents")
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val id = row.getString("id")
            val size = row.getLong("size")
            require(UUID.fromString(id).toString() == id && size in 1..DocumentDisk.limit &&
                row.getString("mimeType") in DocumentDisk.types)
            id to size
        }.also { require(it.map { file -> file.first }.toSet().size == it.size) }
    }

    private fun snapshot(db: SupportSQLiteDatabase) = JSONObject().apply {
        put("schema", 4)
        archiveTables.forEach { table ->
            val rows = JSONArray()
            db.query("SELECT * FROM $table").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    cursor.columnNames.forEachIndexed { index, column ->
                        row.put(column, when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                            else -> error("Unsupported backup column")
                        })
                    }
                    rows.put(row)
                }
            }
            put(table, rows)
        }
    }

    private fun insertRows(db: SupportSQLiteDatabase, table: String, rows: JSONArray, absentColumns: Set<String>) {
        val columns = db.query("PRAGMA table_info($table)").use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(1), cursor.getString(2)) }
        } - absentColumns
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            require(row.keys().asSequence().toSet() == columns.keys)
            val values = ContentValues(columns.size)
            columns.forEach { (column, type) ->
                when (val value = row.get(column)) {
                    JSONObject.NULL -> values.putNull(column)
                    is Int -> { require(type == "INTEGER"); values.put(column, value) }
                    is Long -> { require(type == "INTEGER"); values.put(column, value) }
                    is String -> { require(type == "TEXT"); values.put(column, value) }
                    else -> error("Invalid backup column")
                }
            }
            db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }

    private fun clearRows(db: SupportSQLiteDatabase) {
        archiveTables.asReversed().forEach { db.delete(it, null, null) }
    }
}
