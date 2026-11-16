package com.billmivo.app

import android.app.Application
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import org.json.JSONArray

@Entity(tableName = "schedules")
data class ScheduleRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val series: String, val name: String, val category: String, val currency: String, val amount: String,
    val estimated: Boolean, val first: Long, val cadence: String, val interval: Long, val starts: Long, val ends: Long?,
    val providerId: String? = null
) {
    fun schedule() = Schedule(id, series, name, category, currency, amount.toBigDecimal(), estimated,
        LocalDate.ofEpochDay(first), Cadence.valueOf(cadence), interval, LocalDate.ofEpochDay(starts),
        ends?.let(LocalDate::ofEpochDay), providerId)
}

private fun Schedule.row() = ScheduleRow(id, series, name, category, currency, amount.toPlainString(), estimated,
    first.toEpochDay(), cadence.name, interval, from.toEpochDay(), until?.toEpochDay(), providerId)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(entity = ScheduleRow::class, parentColumns = ["id"], childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["scheduleId", "due"])]
)
data class PaymentRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long, val due: Long, val date: Long, val amount: String, val payerId: String? = null
) {
    fun payment() = Payment(OccurrenceKey(scheduleId, LocalDate.ofEpochDay(due)),
        LocalDate.ofEpochDay(date), amount.toBigDecimal(), payerId)
}

@Entity(
    tableName = "adjustments", primaryKeys = ["scheduleId", "due"],
    foreignKeys = [ForeignKey(entity = ScheduleRow::class, parentColumns = ["id"], childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE)]
)
data class AdjustmentRow(val scheduleId: Long, val due: Long, val confirmed: String?, val skipped: Boolean) {
    fun adjustment() = Adjustment(OccurrenceKey(scheduleId, LocalDate.ofEpochDay(due)), confirmed?.toBigDecimal(), skipped)
}

@Entity(tableName = "members")
data class MemberRow(
    @PrimaryKey val id: String, val name: String, val relationship: String, val notes: String, val archived: Boolean
) {
    fun member() = Member(id, name, relationship, notes, archived)
}

@Entity(tableName = "sharing", primaryKeys = ["series", "starts"])
data class SharingRow(val series: String, val starts: Long, val mode: String?, val responsibleId: String?, val parts: String) {
    fun rule(): SharingRule {
        val entries = JSONArray(parts)
        return SharingRule(series, LocalDate.ofEpochDay(starts), mode?.let(SplitMode::valueOf), responsibleId,
            List(entries.length()) { index ->
                val entry = entries.getJSONArray(index)
                SplitPart(entry.getString(0), entry.getString(1).toBigDecimal())
            })
    }
}

@Entity(tableName = "reminders")
data class ReminderRow(
    @PrimaryKey val series: String, val leadDays: Int?, val due: Boolean, val overdue: Boolean, val increase: Boolean
) {
    fun rule() = ReminderRule(series, leadDays, due, overdue, increase)
}

@Entity(tableName = "providers")
data class ProviderRow(@PrimaryKey val id: String, val name: String, val category: String, val phone: String,
                       val email: String, val website: String, val reference: String, val notes: String) {
    fun provider() = Provider(id, name, category, phone, email, website, reference, notes)
}

@Entity(tableName = "provider_contacts",
    foreignKeys = [ForeignKey(entity = ProviderRow::class, parentColumns = ["id"], childColumns = ["providerId"],
        onDelete = ForeignKey.CASCADE)], indices = [Index("providerId")])
data class ContactRow(@PrimaryKey val id: String, val providerId: String, val date: Long, val note: String) {
    fun contact() = ProviderContact(id, providerId, LocalDate.ofEpochDay(date), note)
}

@Entity(tableName = "documents",
    foreignKeys = [ForeignKey(entity = ScheduleRow::class, parentColumns = ["id"], childColumns = ["billId"],
        onDelete = ForeignKey.CASCADE)], indices = [Index("billId")])
data class DocumentRow(@PrimaryKey val id: String, val billId: Long, val name: String, val mimeType: String,
                       val size: Long, val added: Long) {
    fun document() = BillDocument(id, billId, name, mimeType, size, LocalDate.ofEpochDay(added))
}

@Dao
interface BillDao {
    @Query("SELECT * FROM schedules") suspend fun schedules(): List<ScheduleRow>
    @Query("SELECT * FROM payments") suspend fun payments(): List<PaymentRow>
    @Query("SELECT * FROM adjustments") suspend fun adjustments(): List<AdjustmentRow>
    @Query("SELECT * FROM members ORDER BY name COLLATE NOCASE, id") suspend fun members(): List<MemberRow>
    @Query("SELECT * FROM sharing") suspend fun sharing(): List<SharingRow>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderRow>
    @Upsert suspend fun put(member: MemberRow)
    @Upsert suspend fun put(rule: SharingRow)
    @Upsert suspend fun put(rule: ReminderRow)
    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE") suspend fun providers(): List<ProviderRow>
    @Query("SELECT * FROM provider_contacts ORDER BY date DESC") suspend fun contacts(): List<ContactRow>
    @Query("SELECT * FROM documents ORDER BY added DESC, name COLLATE NOCASE") suspend fun documents(): List<DocumentRow>
    @Upsert suspend fun put(provider: ProviderRow)
    @Insert suspend fun put(contact: ContactRow)
    @Insert suspend fun put(document: DocumentRow)
    @Query("UPDATE schedules SET providerId = NULL WHERE providerId = :id") suspend fun unlink(id: String)
    @Query("DELETE FROM providers WHERE id = :id") suspend fun deleteProvider(id: String)
    @Query("DELETE FROM provider_contacts WHERE id = :id") suspend fun deleteContact(id: String)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: String)
    @Query("DELETE FROM sharing WHERE series = :series") suspend fun deleteSharing(series: String)
    @Query("DELETE FROM reminders WHERE series = :series") suspend fun deleteReminder(series: String)
    @Insert suspend fun add(schedule: ScheduleRow): Long
    @Insert suspend fun pay(payment: PaymentRow)
    @Upsert suspend fun adjust(adjustment: AdjustmentRow)
    @Query("UPDATE schedules SET ends = :cutoff WHERE id = :id AND ends IS NULL") suspend fun close(id: Long, cutoff: Long): Int
    @Query("DELETE FROM schedules WHERE series = :series") suspend fun delete(series: String)
}

@Database(entities = [ScheduleRow::class, PaymentRow::class, AdjustmentRow::class,
    MemberRow::class, SharingRow::class, ReminderRow::class, ProviderRow::class, ContactRow::class,
    DocumentRow::class], version = 3)
abstract class BillDatabase : RoomDatabase() {
    abstract fun bills(): BillDao
}

val migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payments ADD COLUMN payerId TEXT")
        db.execSQL("CREATE TABLE members (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, relationship TEXT NOT NULL, notes TEXT NOT NULL, archived INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sharing (series TEXT NOT NULL, starts INTEGER NOT NULL, mode TEXT, responsibleId TEXT, parts TEXT NOT NULL, PRIMARY KEY(series, starts))")
        db.execSQL("CREATE TABLE reminders (series TEXT NOT NULL PRIMARY KEY, leadDays INTEGER, due INTEGER NOT NULL, overdue INTEGER NOT NULL, increase INTEGER NOT NULL)")
    }
}

val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedules ADD COLUMN providerId TEXT")
        db.execSQL("CREATE TABLE providers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, website TEXT NOT NULL, reference TEXT NOT NULL, notes TEXT NOT NULL)")
        db.execSQL("CREATE TABLE provider_contacts (id TEXT NOT NULL PRIMARY KEY, providerId TEXT NOT NULL, date INTEGER NOT NULL, note TEXT NOT NULL, FOREIGN KEY(providerId) REFERENCES providers(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_provider_contacts_providerId ON provider_contacts(providerId)")
        db.execSQL("CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY, billId INTEGER NOT NULL, name TEXT NOT NULL, mimeType TEXT NOT NULL, size INTEGER NOT NULL, added INTEGER NOT NULL, FOREIGN KEY(billId) REFERENCES schedules(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_documents_billId ON documents(billId)")
    }
}

class BillmivoApplication : Application() {
    val premium by lazy { installStore(this) }
    val repository by lazy {
        BillRepository(Room.databaseBuilder(this, BillDatabase::class.java, "billmivo.db")
            .addMigrations(migration1To2, migration2To3).build()) { !premium.available || premium.state.value.entitled }
    }
    val reminders by lazy { BillReminders(this) }
    val documents by lazy { DocumentDisk(this) }
}

class BillRepository(private val db: BillDatabase, private val unlimitedBills: () -> Boolean = { true }) {
    internal suspend fun <T> archiveTransaction(block: suspend (SupportSQLiteDatabase) -> T): T =
        db.withTransaction { block(db.openHelper.writableDatabase) }

    internal suspend fun validateRestoredRecords() {
        val records = read()
        val earliest = LocalDate.of(2000, 1, 1)
        val schedules = records.schedules.associateBy { it.id }
        val providers = records.providers.map { it.id }.toSet()
        require(schedules.values.all { schedule ->
            val currency = Currency.getInstance(schedule.currency)
            schedule.id > 0 && schedule.name.isNotBlank() && schedule.amount.signum() > 0 &&
                schedule.first >= earliest && schedule.from >= schedule.first &&
                schedule.interval > 0 && currency.defaultFractionDigits >= 0 &&
                (schedule.providerId == null || schedule.providerId in providers)
        })
        require(records.payments.all { it.key.scheduleId in schedules && it.amount.signum() > 0 } &&
            records.adjustments.all { it.key.scheduleId in schedules && (it.confirmed?.signum() ?: 1) > 0 })
    }

    private val dao get() = db.bills()

    private suspend fun read() = Records(
        dao.schedules().map { it.schedule() }, dao.payments().map { it.payment() },
        dao.adjustments().map { it.adjustment() }, dao.members().map { it.member() },
        dao.sharing().map { it.rule() }, dao.reminders().map { it.rule() }, dao.providers().map { it.provider() },
        dao.contacts().map { it.contact() }, dao.documents().map { it.document() }
    )

    val records: Flow<Records> = db.invalidationTracker.createFlow("schedules", "payments", "adjustments", "members",
        "sharing", "reminders", "providers", "provider_contacts", "documents")
        .map { db.withTransaction { read() } }

    suspend fun saveProvider(provider: Provider) {
        require(provider.name.isNotBlank())
        dao.put(ProviderRow(provider.id, provider.name.trim(), provider.category, provider.phone.trim(),
            provider.email.trim(), provider.website.trim(), provider.reference.trim(), provider.notes.trim()))
    }

    suspend fun deleteProvider(id: String) = db.withTransaction {
        dao.unlink(id)
        dao.deleteProvider(id)
    }

    suspend fun recordContact(contact: ProviderContact) {
        require(contact.note.isNotBlank())
        dao.put(ContactRow(contact.id, contact.providerId, contact.date.toEpochDay(), contact.note.trim()))
    }
    suspend fun deleteContact(id: String) = dao.deleteContact(id)

    suspend fun saveDocument(doc: BillDocument) =
        dao.put(DocumentRow(doc.id, doc.billId, doc.name, doc.mimeType, doc.size, doc.added.toEpochDay()))

    suspend fun removeDocument(id: String) = dao.deleteDocument(id)

    suspend fun saveMember(member: Member) {
        require(member.name.isNotBlank() && member.name.length <= 60)
        dao.put(MemberRow(member.id, member.name.trim(), member.relationship.trim(), member.notes.trim(), member.archived))
    }

    suspend fun saveSharing(rule: SharingRule, today: LocalDate) = db.withTransaction {
        require(rule.from == today)
        val records = read()
        val active = records.schedules.single { it.series == rule.series && it.until == null }
        val ids = records.members.map { it.id }.toSet()
        require(rule.responsibleId == null || rule.responsibleId in ids)
        require(rule.parts.all { it.memberId in ids })
        require(rule.mode != null || rule.parts.isEmpty())
        require(rule.allocate(active.amount, active.currency) != null)
        val parts = JSONArray()
        rule.parts.forEach { part -> parts.put(JSONArray().put(part.memberId).put(part.value.toPlainString())) }
        dao.put(SharingRow(rule.series, rule.from.toEpochDay(), rule.mode?.name, rule.responsibleId, parts.toString()))
    }

    suspend fun saveReminder(rule: ReminderRule) = db.withTransaction {
        require(rule.leadDays == null || rule.leadDays in 1..30)
        require(dao.schedules().any { it.series == rule.series && it.ends == null })
        if (rule.active) dao.put(ReminderRow(rule.series, rule.leadDays, rule.due, rule.overdue, rule.increase))
        else dao.deleteReminder(rule.series)
    }

    suspend fun save(schedule: Schedule, previous: Schedule?, today: LocalDate) = db.withTransaction {
        require(schedule.providerId == null || dao.providers().any { it.id == schedule.providerId })
        if (!unlimitedBills() && schedule.cadence != Cadence.ONCE &&
            (previous == null || previous.cadence == Cadence.ONCE) &&
            dao.schedules().count { it.ends == null && it.cadence != Cadence.ONCE.name } >= 10) {
            throw RecurringBillLimit()
        }
        if (previous == null) {
            dao.add(schedule.row())
        } else {
            val records = read()
            val active = records.schedules.single { it.id == previous.id && it.until == null }
            val latestTouched = (records.payments.map { it.key } + records.adjustments.map { it.key })
                .filter { it.scheduleId == active.id }.maxOfOrNull { it.due }
            val previousCutoff = records.schedules.filter { it.series == active.series }.mapNotNull { it.until }.maxOrNull()
            val cutoff = maxOf(today, latestTouched?.plusDays(1) ?: today, previousCutoff ?: today)
            require(schedule.cadence != Cadence.ONCE || schedule.first >= cutoff)
            check(dao.close(active.id, cutoff.toEpochDay()) == 1)
            dao.add(schedule.copy(from = maxOf(schedule.first, cutoff)).row())
        }
    }

    suspend fun archive(schedule: Schedule, today: LocalDate) = db.withTransaction {
        val records = read()
        val active = records.schedules.single { it.id == schedule.id && it.until == null }
        val seriesIds = records.schedules.filter { it.series == active.series }.map { it.id }.toSet()
        val lastPaidDue = records.payments.filter { it.key.scheduleId == schedule.id }.maxOfOrNull { it.key.due }
        // Preserve bills with early payment records without leaving their future schedule active.
        val cutoff = maxOf(today, lastPaidDue?.plusDays(1) ?: today)
        check(dao.close(schedule.id, cutoff.toEpochDay()) == 1)
        dao.deleteReminder(schedule.series)
        val priorEnd = records.schedules.filter { it.id in seriesIds }.mapNotNull { it.until }.maxOrNull() ?: today
        records.occurrences(today, maxOf(cutoff, priorEnd).minusDays(1))
            .filter { it.schedule.id in seriesIds && it.open && it.paid.signum() == 0 }
            .forEach { dao.adjust(AdjustmentRow(it.schedule.id, it.date.toEpochDay(),
                it.amount.takeUnless { _ -> it.estimated }?.toPlainString(), true)) }
    }

    suspend fun delete(series: String) = db.withTransaction {
        dao.deleteSharing(series)
        dao.deleteReminder(series)
        dao.delete(series)
    }

    suspend fun pay(key: OccurrenceKey, amount: BigDecimal, paymentDate: LocalDate, today: LocalDate = LocalDate.now(),
                    payerId: String? = null) = db.withTransaction {
        val records = read()
        val occurrence = records.occurrences(key.due, key.due).single { it.key == key }
        require(occurrence.open && !occurrence.estimated && amount.signum() > 0 && amount <= occurrence.remaining &&
            paymentDate <= today && (payerId == null || records.members.any { it.id == payerId }))
        dao.pay(PaymentRow(scheduleId = key.scheduleId, due = key.due.toEpochDay(),
            date = paymentDate.toEpochDay(), amount = amount.toPlainString(), payerId = payerId))
    }

    suspend fun confirm(key: OccurrenceKey, amount: BigDecimal) = db.withTransaction {
        val occurrence = read().occurrences(key.due, key.due).single { it.key == key }
        require(occurrence.open && occurrence.estimated && amount.signum() > 0)
        dao.adjust(AdjustmentRow(key.scheduleId, key.due.toEpochDay(), amount.toPlainString(), false))
    }

    suspend fun skip(key: OccurrenceKey) = db.withTransaction {
        val occurrence = read().occurrences(key.due, key.due).single { it.key == key }
        require(occurrence.open && occurrence.paid.signum() == 0)
        dao.adjust(AdjustmentRow(key.scheduleId, key.due.toEpochDay(),
            occurrence.amount.takeUnless { occurrence.estimated }?.toPlainString(), true))
    }
}
