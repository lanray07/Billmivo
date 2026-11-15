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
import org.json.JSONArray

@Entity(tableName = "schedules")
data class ScheduleRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val series: String, val name: String, val category: String, val currency: String, val amount: String,
    val estimated: Boolean, val first: Long, val cadence: String, val interval: Long, val starts: Long, val ends: Long?
) {
    fun schedule() = Schedule(id, series, name, category, currency, amount.toBigDecimal(), estimated,
        LocalDate.ofEpochDay(first), Cadence.valueOf(cadence), interval, LocalDate.ofEpochDay(starts),
        ends?.let(LocalDate::ofEpochDay))
}

private fun Schedule.row() = ScheduleRow(id, series, name, category, currency, amount.toPlainString(), estimated,
    first.toEpochDay(), cadence.name, interval, from.toEpochDay(), until?.toEpochDay())

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
    @Query("DELETE FROM sharing WHERE series = :series") suspend fun deleteSharing(series: String)
    @Query("DELETE FROM reminders WHERE series = :series") suspend fun deleteReminder(series: String)
    @Insert suspend fun add(schedule: ScheduleRow): Long
    @Insert suspend fun pay(payment: PaymentRow)
    @Upsert suspend fun adjust(adjustment: AdjustmentRow)
    @Query("UPDATE schedules SET ends = :cutoff WHERE id = :id AND ends IS NULL") suspend fun close(id: Long, cutoff: Long): Int
    @Query("DELETE FROM schedules WHERE series = :series") suspend fun delete(series: String)
}

@Database(entities = [ScheduleRow::class, PaymentRow::class, AdjustmentRow::class,
    MemberRow::class, SharingRow::class, ReminderRow::class], version = 2)
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

class BillmivoApplication : Application() {
    val repository by lazy {
        BillRepository(Room.databaseBuilder(this, BillDatabase::class.java, "billmivo.db")
            .addMigrations(migration1To2).build())
    }
    val reminders by lazy { BillReminders(this) }
}

class BillRepository(private val db: BillDatabase) {
    private val dao get() = db.bills()

    private suspend fun read() = Records(
        dao.schedules().map { it.schedule() }, dao.payments().map { it.payment() },
        dao.adjustments().map { it.adjustment() }, dao.members().map { it.member() },
        dao.sharing().map { it.rule() }, dao.reminders().map { it.rule() }
    )

    val records: Flow<Records> = db.invalidationTracker.createFlow("schedules", "payments", "adjustments", "members", "sharing", "reminders")
        .map { db.withTransaction { read() } }

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
