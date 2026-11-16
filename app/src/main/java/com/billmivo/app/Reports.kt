package com.billmivo.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

enum class ReportKind(val title: Int) {
    Month(R.string.report_month), Upcoming(R.string.upcoming), Forecast(R.string.forecast),
    Payments(R.string.payment_history), Overdue(R.string.overdue), Vendors(R.string.report_provider),
    Sharing(R.string.shares_30), Changes(R.string.bill_changes)
}

data class ReportRow(val item: String, val date: LocalDate?, val currency: String, val amount: BigDecimal, val status: String)
data class BillReport(val title: String, val headers: List<String>, val rows: List<ReportRow>, val asOf: LocalDate)

fun Records.report(kind: ReportKind, today: LocalDate, context: Context): BillReport {
    fun label(id: Int) = context.getString(id)
    val schedulesById = schedules.associateBy { it.id }
    val currencies = schedules.map { it.currency }.distinct().sorted()
    val horizon = today.plusDays(29)
    fun openForecast() = forecast(today, today, horizon)
    fun Occurrence.entry(outstanding: Boolean) = ReportRow(schedule.name, date, schedule.currency,
        if (outstanding) remaining else amount, label(when {
            cancelled -> R.string.cancelled_occurrence
            skipped -> R.string.skipped
            estimated -> R.string.estimated
            !open -> R.string.paid
            paid.signum() > 0 -> R.string.part_paid
            else -> R.string.confirmed
        }))
    val rows = when (kind) {
        ReportKind.Month -> YearMonth.from(today).let { month ->
            occurrences(month.atDay(1), month.atEndOfMonth()).map { it.entry(false) }
        }
        ReportKind.Upcoming -> openForecast().upcoming.map { it.entry(true) }
        ReportKind.Overdue -> openForecast().overdue.map { it.entry(true) }
        ReportKind.Forecast -> {
            val forecast = openForecast()
            currencies.flatMap { currency ->
                val total = forecast.totals(currency)
                listOf(ReportRow(label(R.string.confirmed), horizon, currency, total.confirmed, label(R.string.remaining)),
                    ReportRow(label(R.string.estimated), horizon, currency, total.estimated, label(R.string.remaining)))
            }
        }
        ReportKind.Payments -> payments.sortedByDescending { it.date }.map { payment ->
            val bill = schedulesById.getValue(payment.key.scheduleId)
            ReportRow(bill.name, payment.date, bill.currency, payment.amount, label(R.string.paid))
        }
        ReportKind.Vendors -> {
            val providersById = providers.associateBy { it.id }
            payments.filter { it.date in today.minusYears(1).plusDays(1)..today }.groupBy { payment ->
                val bill = schedulesById.getValue(payment.key.scheduleId)
                bill.providerId to bill.currency
            }.map { (key, values) ->
                val (provider, currency) = key
                ReportRow(providersById[provider]?.name ?: label(R.string.unassigned), null, currency,
                    values.sumOf { it.amount }, label(R.string.report_last_year))
            }.sortedWith(compareBy({ it.item }, { it.currency }))
        }
        ReportKind.Sharing -> {
            val names = members.associate { it.id to it.name }
            openForecast().upcoming.flatMap { bill ->
                val rule = sharingFor(bill.schedule.series, bill.date) ?: return@flatMap emptyList()
                val split = rule.allocate(bill.amount, bill.schedule.currency)
                if (split == null) listOf(ReportRow(bill.schedule.name, bill.date, bill.schedule.currency,
                    bill.amount, context.getString(R.string.split_review, bill.schedule.name)))
                else split.map { (id, amount) ->
                    ReportRow(context.getString(R.string.chart_value, names[id].orEmpty(), bill.schedule.name),
                        bill.date, bill.schedule.currency, amount, label(if (bill.estimated) R.string.estimated else R.string.shares_bill))
                }
            }
        }
        ReportKind.Changes -> currencies.flatMap { currency ->
            billChanges(today, currency).map { change ->
                ReportRow(change.bill.name, change.current.date, currency, change.change, label(R.string.confirmed))
            }
        }.sortedByDescending { it.date }
    }
    return BillReport(label(kind.title), listOf(label(R.string.name), label(R.string.report_date), label(R.string.currency),
        label(R.string.amount), label(R.string.report_status)), rows, today)
}

fun csvCell(value: String, numeric: Boolean = false): String {
    val significant = value.trimStart { it.isWhitespace() || it == '\uFEFF' }
    val formula = significant.firstOrNull() in listOf('=', '+', '-', '@')
    val text = if (!numeric && formula) "'$value" else value
    return "\"${text.replace("\"", "\"\"")}\""
}

fun BillReport.writeCsv(output: OutputStream) {
    val writer = output.bufferedWriter(Charsets.UTF_8)
    writer.append(headers.joinToString(",") { csvCell(it) }).append("\r\n")
    rows.forEach { row ->
        val fields = listOf(row.item, row.date?.toString().orEmpty(), row.currency, row.amount.toPlainString(), row.status)
        writer.append(fields.mapIndexed { index, text -> csvCell(text, numeric = index == 3) }.joinToString(","))
            .append("\r\n")
    }
    writer.flush()
}

fun ReportRow.human(locale: Locale) = listOfNotNull(item, date?.let { dateLabel(it, locale) },
    money(amount, currency, locale), status).joinToString(" · ")

fun BillReport.writePdf(output: OutputStream, locale: Locale) {
    val pdf = PdfDocument()
    try {
        val titlePaint = TextPaint().apply { color = Color.rgb(20, 60, 52); textSize = 23f; typeface = Typeface.SERIF }
        val bodyPaint = TextPaint().apply { color = Color.rgb(30, 45, 39); textSize = 13f }
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var y = 42f
        fun layout(text: String, paint: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, paint, 511)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setIncludePad(false).build()
        fun draw(text: StaticLayout) {
            page.canvas.save()
            page.canvas.translate(42f, y)
            text.draw(page.canvas)
            page.canvas.restore()
            y += text.height + 10
        }
        fun heading() {
            draw(layout("$title · ${dateLabel(asOf, locale)}", titlePaint))
        }
        heading()
        rows.forEach { row ->
            val text = layout(row.human(locale), bodyPaint)
            if (y + text.height > 800) {
                pdf.finishPage(page)
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                y = 42f
                heading()
            }
            draw(text)
        }
        pdf.finishPage(page)
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}
