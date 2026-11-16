package com.billmivo.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LockedPage(available: Boolean, unlock: () -> Unit) {
    Surface(color = palette.canvas) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Panel(Modifier.sizeIn(maxWidth = 460.dp)) {
                SectionTitle(stringResource(R.string.lock_title), stringResource(R.string.lock_prompt))
                if (available) Button(unlock, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.lock_unlock))
                } else Text(stringResource(R.string.lock_unavailable))
            }
        }
    }
}

private data class BackupRequest(val uri: Uri, val restore: Boolean)

@Composable
fun PrivacyPage(lockEnabled: Boolean, credentialAvailable: Boolean, onLockChange: (Boolean) -> Unit,
                upgrade: (() -> Unit)?, back: () -> Unit) {
    val app = LocalContext.current.applicationContext as BillmivoApplication
    val backup = remember(app) { EncryptedBackup(app) }
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<BackupRequest?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) request = BackupRequest(uri, false)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) request = BackupRequest(uri, true)
    }
    val runOperation: (Int, Int, suspend () -> Unit) -> Unit = { success, failure, operation ->
        busy = true
        status = null
        scope.launch {
            try { operation(); status = success }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = failure }
            finally { busy = false }
        }
    }

    PageLayout {
        TextButton(back, enabled = !busy) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.privacy_title))
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.lock_setting), stringResource(R.string.lock_detail))
            if (!credentialAvailable) Text(stringResource(R.string.lock_unavailable))
            OutlinedButton({ onLockChange(!lockEnabled) }, enabled = credentialAvailable && !busy,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (lockEnabled) R.string.lock_disable else R.string.lock_enable))
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.backup_title), stringResource(R.string.backup_detail))
            OutlinedButton({ if (upgrade == null) save.launch("Billmivo.billmivo") else upgrade() }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.backup_create))
            }
            OutlinedButton({ open.launch(arrayOf("*/*")) }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.backup_restore))
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.erase_title), stringResource(R.string.erase_detail))
            OutlinedButton({ confirmErase = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.erase_action), color = palette.red)
            }
        }
        if (busy) CircularProgressIndicator()
        status?.let { Text(stringResource(it)) }
    }

    request?.let { pending ->
        var password by remember(pending) { mutableStateOf("") }
        var repeated by remember(pending) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { request = null }, title = {
            Text(stringResource(if (pending.restore) R.string.backup_restore else R.string.backup_create))
        }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (pending.restore) R.string.backup_replace else R.string.backup_passphrase_help))
                PasswordField(password, { password = it }, R.string.backup_passphrase)
                if (!pending.restore) PasswordField(repeated, { repeated = it }, R.string.backup_passphrase_repeat)
            }
        }, confirmButton = {
            Button({
                val chars = password.toCharArray()
                request = null
                runOperation(if (pending.restore) R.string.backup_restored else R.string.backup_created,
                    R.string.backup_error) {
                    try {
                        if (pending.restore) backup.restore(pending.uri, chars) else backup.export(pending.uri, chars)
                    } finally { chars.fill('\u0000') }
                }
            }, enabled = password.length >= backupPasswordMinimum && (pending.restore || password == repeated)) {
                Text(stringResource(if (pending.restore) R.string.backup_replace_action else R.string.backup_create))
            }
        }, dismissButton = { TextButton({ request = null }) { Text(stringResource(R.string.cancel)) } })
    }

    if (confirmErase) AlertDialog(onDismissRequest = { confirmErase = false }, title = {
        Text(stringResource(R.string.erase_action))
    }, text = { Text(stringResource(R.string.erase_confirm)) }, confirmButton = {
        Button({
            confirmErase = false
            runOperation(R.string.erase_done, R.string.action_error) { backup.erase() }
        }) { Text(stringResource(R.string.erase_action)) }
    }, dismissButton = { TextButton({ confirmErase = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: Int) {
    OutlinedTextField(value, { change(it.take(128)) }, label = { Text(stringResource(label)) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
