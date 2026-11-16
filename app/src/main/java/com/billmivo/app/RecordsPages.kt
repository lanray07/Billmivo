package com.billmivo.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun ProvidersPage(records: Records, today: LocalDate, save: SaveBillAction, back: () -> Unit) {
    val locale = displayLocale()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var addContact by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var removeContact by rememberSaveable { mutableStateOf<String?>(null) }
    val provider = records.providers.find { it.id == selected }
    PageLayout {
        TextButton({ if (provider == null) back() else selected = null }) {
            Text(stringResource(if (provider == null) R.string.settings else R.string.providers))
        }
        if (provider == null) {
            SectionTitle(stringResource(R.string.providers), stringResource(R.string.provider_intro))
            Panel(Modifier.fillMaxWidth()) {
                if (records.providers.isEmpty()) Text(stringResource(R.string.providers_empty), color = palette.muted)
                records.providers.forEach { item ->
                    OutlinedButton({ selected = item.id }, modifier = Modifier.heightIn(min = 48.dp)) { Text(item.name) }
                }
                Button({ edit = true }) { Text(stringResource(R.string.provider_add)) }
            }
        } else {
            SectionTitle(provider.name, categoryName(provider.category, records))
            Panel(Modifier.fillMaxWidth()) {
                listOf(R.string.provider_phone to provider.phone, R.string.provider_email to provider.email,
                    R.string.provider_website to provider.website, R.string.provider_reference to provider.reference,
                    R.string.member_notes to provider.notes).filter { it.second.isNotBlank() }.forEach { (key, value) ->
                    Text(stringResource(R.string.chart_value, stringResource(key), value))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton({ edit = true }) { Text(stringResource(R.string.provider_edit)) }
                    TextButton({ delete = true }) { Text(stringResource(R.string.provider_delete), color = palette.red) }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.provider_bills))
                val bills = records.schedules.filter { it.providerId == provider.id }.distinctBy { it.series }
                if (bills.isEmpty()) Text(stringResource(R.string.provider_bills_empty), color = palette.muted)
                bills.forEach { Text(it.name) }
            }
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.provider_history))
                records.providerContacts.filter { it.providerId == provider.id }.forEach {
                    Text(dateLabel(it.date, locale), color = palette.muted)
                    Text(it.note)
                    TextButton({ removeContact = it.id }) { Text(stringResource(R.string.provider_remove_contact)) }
                }
                Button({ addContact = true }) { Text(stringResource(R.string.provider_log_contact)) }
            }
        }
    }
    if (edit) ProviderDialog(provider, records, { edit = false }, save)
    if (addContact && provider != null) {
        var note by rememberSaveable { mutableStateOf("") }
        var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
        AlertDialog({ addContact = false }, title = { Text(stringResource(R.string.provider_log_contact)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DateField(R.string.provider_contact_date, LocalDate.ofEpochDay(day), today) { day = it.toEpochDay() }
                OutlinedTextField(note, { note = it.take(500) }, label = { Text(stringResource(R.string.provider_contact_note)) })
            }
        }, dismissButton = { TextButton({ addContact = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { Button({
                val record = ProviderContact(UUID.randomUUID().toString(), provider.id, LocalDate.ofEpochDay(day), note)
                addContact = false
                save({ recordContact(record) }, {}, {})
            }, enabled = note.isNotBlank()) { Text(stringResource(R.string.save)) } })
    }
    if (delete && provider != null) ConfirmRecordAction(R.string.provider_delete, R.string.provider_delete_note,
        { delete = false }) {
        delete = false
        save({ deleteProvider(provider.id) }, { selected = null }, {})
    }
    removeContact?.let { id ->
        ConfirmRecordAction(R.string.provider_remove_contact, R.string.provider_remove_contact_note, { removeContact = null }) {
            removeContact = null
            save({ deleteContact(id) }, {}, {})
        }
    }
}

@Composable
private fun ProviderDialog(provider: Provider?, records: Records, close: () -> Unit, save: SaveBillAction) {
    val id = rememberSaveable { provider?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(provider?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(provider?.category ?: "other") }
    var phone by rememberSaveable { mutableStateOf(provider?.phone.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(provider?.email.orEmpty()) }
    var website by rememberSaveable { mutableStateOf(provider?.website.orEmpty()) }
    var reference by rememberSaveable { mutableStateOf(provider?.reference.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(provider?.notes.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog({ if (!busy) close() }, title = { Text(stringResource(if (provider == null) R.string.provider_add else R.string.provider_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.provider_name)) }, singleLine = true)
                CategoryMenu(category, records, { category = it })
                OutlinedTextField(phone, { phone = it.take(50) }, label = { Text(stringResource(R.string.provider_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(email, { email = it.take(200) }, label = { Text(stringResource(R.string.provider_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                OutlinedTextField(website, { website = it.take(240) }, label = { Text(stringResource(R.string.provider_website)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                OutlinedTextField(reference, { reference = it.take(75) }, label = { Text(stringResource(R.string.provider_reference)) },
                    singleLine = true)
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.member_notes)) })
            }
        }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button({
            val result = Provider(id, name, category, phone, email, website, reference, notes)
            busy = true
            save({ saveProvider(result) }, close) { busy = false }
        }, enabled = name.isNotBlank() && !busy) { Text(stringResource(R.string.save)) } })
}

@Composable
fun DocumentsPage(records: Records, storage: DocumentDisk, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = displayLocale()
    val bills = records.schedules.groupBy { it.series }.values.map { it.maxBy { bill -> bill.id } }.sortedBy { it.name }
    val names = records.schedules.associate { it.id to it.name }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var pending by rememberSaveable { mutableStateOf<Long?>(null) }
    var remove by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pending
        pending = null
        if (uri != null && id != null) scope.launch {
            busy = true
            message = null
            try {
                storage.import(uri, id)
                message = R.string.document_added
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = R.string.document_import_error
            } finally {
                busy = false
            }
        }
    }
    val billId = selected?.takeIf { id -> bills.any { it.id == id } } ?: bills.firstOrNull()?.id
    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.documents), stringResource(R.string.documents_intro))
        Panel(Modifier.fillMaxWidth()) {
            if (billId == null) Text(stringResource(R.string.empty_bills), color = palette.muted) else {
                MenuField(stringResource(R.string.documents_bill), billId, bills.map { it.id }, { names.getValue(it) },
                    { selected = it })
                Button({
                    pending = billId
                    openDocument.launch(DocumentDisk.types.toTypedArray())
                }, enabled = !busy) { Text(stringResource(R.string.document_attach)) }
            }
            if (busy) CircularProgressIndicator()
            message?.let { Text(stringResource(it), color = palette.muted) }
        }
        Panel(Modifier.fillMaxWidth()) {
            if (records.documents.isEmpty()) Text(stringResource(R.string.documents_empty), color = palette.muted)
            records.documents.forEach { doc ->
                Text(doc.name, style = MaterialTheme.typography.titleMedium)
                names[doc.billId]?.let { Text(it) }
                Text(dateLabel(doc.added, locale), color = palette.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton({
                        runCatching { context.startActivity(storage.viewer(doc)) }.onFailure { message = R.string.document_open_error }
                    }) { Text(stringResource(R.string.document_open)) }
                    TextButton({ remove = doc.id }) { Text(stringResource(R.string.document_remove), color = palette.red) }
                }
                HorizontalDivider(color = palette.line)
            }
        }
    }
    records.documents.find { it.id == remove }?.let { doc ->
        ConfirmRecordAction(R.string.document_remove, R.string.document_remove_note, { remove = null }) {
            remove = null
            scope.launch {
                try {
                    storage.remove(doc)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = R.string.action_error
                }
            }
        }
    }
}

@Composable
fun ConfirmRecordAction(title: Int, detail: Int, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(close, title = { Text(stringResource(title)) }, text = { Text(stringResource(detail)) },
        dismissButton = { TextButton(close) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button(confirm) { Text(stringResource(title)) } })
}
