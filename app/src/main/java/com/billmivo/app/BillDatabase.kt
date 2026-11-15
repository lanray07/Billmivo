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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

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
    val scheduleId: Long, val due: Long, val date: Long, val amount: String
) {
    fun payment() = Payment(OccurrenceKey(scheduleId, LocalDate.ofEpochDay(due)),
        LocalDate.ofEpochDay(date), amount.toBigDecimal())
}

@Entity(
    tableName = "adjustments", primaryKeys = ["scheduleId", "due"],
    foreignKeys = [ForeignKey(entity = ScheduleRow::class, parentColumns = ["id"], childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE)]
)
data class AdjustmentRow(val scheduleId: Long, val due: Long, val confirmed: String?, val skipped: Boolean) {
    fun adjustment() = Adjustment(OccurrenceKey(scheduleId, LocalDate.ofEpochDay(due)), confirmed?.toBigDecimal(), skipped)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM schedules") suspend fun schedules(): List<ScheduleRow>
    @Query("SELECT * FROM payments") suspend fun payments(): List<PaymentRow>
    @Query("SELECT * FROM adjustments") suspend fun adjustments(): List<AdjustmentRow>
    @Insert suspend fun add(schedule: ScheduleRow): Long
    @Insert suspend fun pay(payment: PaymentRow)
    @Upsert suspend fun adjust(adjustment: AdjustmentRow)
    @Query("UPDATE schedules SET ends = :cutoff WHERE id = :id AND ends IS NULL") suspend fun close(id: Long, cutoff: Long): Int
    @Query("DELETE FROM schedules WHERE series = :series") suspend fun delete(series: String)
}

@Database(entities = [ScheduleRow::class, PaymentRow::class, AdjustmentRow::class], version = 1)
abstract class BillDatabase : RoomDatabase() {
    abstract fun bills(): BillDao
}

class BillmivoApplication : Application() {
    val repository by lazy {
        BillRepository(Room.databaseBuilder(this, BillDatabase::class.java, "billmivo.db").build())
    }
}

class BillRepository(private val db: BillDatabase) {
    private val dao get() = db.bills()

    private suspend fun read() = Records(
        dao.schedules().map { it.schedule() }, dao.payments().map { it.payment() },
        dao.adjustments().map { it.adjustment() }
    )

    val records: Flow<Records> = db.invalidationTracker.createFlow("schedules", "payments", "adjustments")
        .map { db.withTransaction { read() } }

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
        val priorEnd = records.schedules.filter { it.id in seriesIds }.mapNotNull { it.until }.maxOrNull() ?: today
        records.occurrences(today, maxOf(cutoff, priorEnd).minusDays(1))
            .filter { it.schedule.id in seriesIds && it.open && it.paid.signum() == 0 }
            .forEach { dao.adjust(AdjustmentRow(it.schedule.id, it.date.toEpochDay(),
                it.amount.takeUnless { _ -> it.estimated }?.toPlainString(), true)) }
    }

    suspend fun delete(series: String) = dao.delete(series)

    suspend fun pay(key: OccurrenceKey, amount: BigDecimal, paymentDate: LocalDate, today: LocalDate = LocalDate.now()) = db.withTransaction {
        val occurrence = read().occurrences(key.due, key.due).single { it.key == key }
        require(occurrence.open && !occurrence.estimated && amount.signum() > 0 && amount <= occurrence.remaining &&
            paymentDate <= today)
        dao.pay(PaymentRow(scheduleId = key.scheduleId, due = key.due.toEpochDay(),
            date = paymentDate.toEpochDay(), amount = amount.toPlainString()))
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
