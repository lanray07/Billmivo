package com.billmivo.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsPage(records: Records, today: LocalDate, back: () -> Unit) {
    val context = LocalContext.current
    val locale = displayLocale()
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(ReportKind.Month) }
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf<ReportKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    val report by produceState<BillReport?>(null, records, kind, today, context) {
        value = null
        value = withContext(Dispatchers.Default) { records.report(kind, today, context) }
    }
    fun saveTo(uri: Uri?, pdf: Boolean) {
        val selected = pendingKind
        pendingKind = null
        if (uri == null || selected == null) return
        scope.launch {
            busy = true
            result = null
            try {
                withContext(Dispatchers.IO) {
                    val data = records.report(selected, today, context)
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                        if (pdf) data.writePdf(output, locale) else data.writeCsv(output)
                    }
                }
                result = R.string.export_complete
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                result = R.string.export_failed
            } finally {
                busy = false
            }
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { saveTo(it, false) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it, true) }
    val preview = report
    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.reports), stringResource(R.string.reports_intro))
        Panel(Modifier.fillMaxWidth()) {
            MenuField(stringResource(R.string.report_kind), kind, ReportKind.entries, { stringResource(it.title) }, { kind = it })
            if (preview == null || busy) CircularProgressIndicator()
            if (preview != null && preview.rows.isEmpty()) Text(stringResource(R.string.report_empty), color = palette.muted)
            if (preview != null && preview.rows.isNotEmpty()) {
                preview.rows.take(5).forEach { Text(it.human(locale)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({ format = "csv" }, enabled = !busy) { Text(stringResource(R.string.export_csv)) }
                    OutlinedButton({ format = "pdf" }, enabled = !busy) { Text(stringResource(R.string.export_pdf)) }
                    OutlinedButton({ format = "text" }, enabled = !busy) { Text(stringResource(R.string.export_text)) }
                }
            }
            result?.let { Text(stringResource(it), color = palette.muted) }
        }
    }
    val target = format
    if (target != null && preview != null) {
        val reportData = preview
        ConfirmRecordAction(when (target) { "csv" -> R.string.export_csv; "pdf" -> R.string.export_pdf; else -> R.string.export_text },
            if (target == "text" && reportData.rows.size > 100) R.string.export_text_confirm_limit else R.string.export_confirm,
            { format = null }) {
            format = null
            when (target) {
                "csv" -> { pendingKind = kind; csv.launch("Billmivo-$today.csv") }
                "pdf" -> { pendingKind = kind; pdf.launch("Billmivo-$today.pdf") }
                else -> {
                    val text = (listOf(reportData.title) + reportData.rows.take(100).map { it.human(locale) }).joinToString("\n")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(Intent.createChooser(send, null)) }.onFailure { result = R.string.export_failed }
                }
            }
        }
    }
}
