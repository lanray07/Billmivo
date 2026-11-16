package com.billmivo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate

private val voiceLabels = mapOf(7 to R.string.days_7, 30 to R.string.days_30, 90 to R.string.days_90)

@Composable
internal fun VoiceDialog(records: Records, currency: String, today: LocalDate, spoken: Boolean,
                         onClose: () -> Unit, onCommand: (VoiceCommand) -> Unit) {
    val context = LocalContext.current
    val locale = displayLocale()
    var range by rememberSaveable { mutableIntStateOf(30) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(locale, spoken) {
        if (spoken) speech = TextToSpeech(context.applicationContext) { ttsReady = it == TextToSpeech.SUCCESS }
    }
    DisposableEffect(speech) {
        val engine = speech
        onDispose { engine?.stop(); engine?.shutdown() }
    }
    DisposableEffect(recognizer) {
        val engine = recognizer
        onDispose { engine?.destroy() }
    }
    val start = {
        runCatching {
            val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let { transcript = it }
                }
                override fun onError(error: Int) {
                    listening = false
                    status = R.string.voice_error
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val selected = parseVoiceCommand(transcript, locale, today)
                    if (selected == null) status = R.string.voice_command_error
                    if (selected is VoiceCommand.Range) range = selected.days
                }
            })
            transcript = ""
            status = null
            engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
            listening = true
        }.onFailure { listening = false; status = R.string.voice_error }
        Unit
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else status = R.string.voice_permission
    }
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val command = remember(transcript, locale, today) { parseVoiceCommand(transcript, locale, today) }
    val listUpcoming = command == VoiceCommand.Navigate.Upcoming
    val days = if (listUpcoming) 30 else range
    val forecast = remember(records, days, today) {
        records.forecast(today, today, today.plusDays(days.toLong() - 1))
    }
    val total = forecast.totals(currency)
    val summary = stringResource(R.string.voice_summary, stringResource(voiceLabels.getValue(days)),
        money(total.confirmed, currency, locale), money(total.estimated, currency, locale))
    val upcoming = if (listUpcoming) forecast.upcoming.filter { it.schedule.currency == currency } else emptyList()
    val lines = upcoming.take(10).map { item ->
        stringResource(R.string.assistant_summary_line,
            stringResource(R.string.chart_value, item.schedule.name, money(item.remaining, currency, locale)),
            dateLabel(item.date, locale), stringResource(if (item.estimated) R.string.estimated else R.string.confirmed))
    }
    val spokenLines = listOf(summary) + lines +
        listOfNotNull(if (upcoming.size > lines.size) stringResource(R.string.assistant_first_ten) else null)
    val payment = remember(command, records, today, locale) {
        (command as? VoiceCommand.ReviewPayment)?.let { records.paymentForReview(it.name, today, locale) }
    }
    val reminder = remember(command, records, today, locale) {
        (command as? VoiceCommand.ReviewReminder)?.let { records.reminderForReview(it, today, locale) }
    }
    val commandLabel = when (command) {
        is VoiceCommand.Draft -> stringResource(R.string.voice_review_draft, command.bill.name,
            money(command.bill.amount, command.bill.currency, locale), dateLabel(command.bill.due, locale))
        is VoiceCommand.ReviewPayment -> payment?.let {
            stringResource(R.string.voice_review_payment, it.schedule.name, dateLabel(it.date, locale))
        } ?: stringResource(R.string.voice_payment_no_match)
        is VoiceCommand.ReviewReminder -> reminder?.let {
            stringResource(R.string.one_off_review_voice, it.schedule.name, dateLabel(command.notifyOn, locale))
        } ?: stringResource(R.string.one_off_no_match)
        VoiceCommand.Navigate.Overdue -> stringResource(R.string.overdue)
        VoiceCommand.Navigate.Household -> stringResource(R.string.household)
        VoiceCommand.Navigate.Increases -> stringResource(R.string.bill_changes)
        VoiceCommand.Navigate.Reminders -> stringResource(R.string.reminders)
        VoiceCommand.Navigate.Upcoming -> stringResource(R.string.upcoming)
        else -> null
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.voice_command_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.voice_command_intro), color = palette.muted)
            Text(stringResource(R.string.voice_try, voiceRanges.joinToString(", ") { java.text.NumberFormat.getIntegerInstance(locale).format(it) }))
            Text(stringResource(R.string.voice_examples), color = palette.muted)
            Text(stringResource(R.string.one_off_voice_example), color = palette.muted)
            if (available) {
                OutlinedButton({
                    if (listening) {
                        recognizer?.cancel()
                        listening = false
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED) start()
                    else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(stringResource(if (listening) R.string.voice_stop else R.string.voice_listen)) }
            } else Text(stringResource(R.string.voice_unavailable), color = palette.muted)
            OutlinedTextField(transcript, {
                transcript = it.take(175)
                val parsed = parseVoiceCommand(transcript, locale, today)
                if (parsed is VoiceCommand.Range) range = parsed.days
                status = null
            }, label = { Text(stringResource(R.string.voice_command_input)) }, singleLine = true, enabled = !listening)
            val feedback = status ?: R.string.voice_command_error.takeIf { transcript.isNotBlank() && command == null && !listening }
            feedback?.let { Text(stringResource(it), color = palette.red) }
            if (commandLabel != null) Text(commandLabel) else {
                MenuField(stringResource(R.string.period), range, voiceRanges,
                    { stringResource(voiceLabels.getValue(it)) }, { range = it; transcript = "" })
                Text(summary)
            }
            if (listUpcoming) spokenLines.forEach { Text(it) }
            if (spoken && (commandLabel == null || listUpcoming)) OutlinedButton({
                val engine = speech
                if (engine == null || !ttsReady || engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE ||
                    engine.speak(spokenLines.joinToString("\n"), TextToSpeech.QUEUE_FLUSH, null, "forecast") == TextToSpeech.ERROR) {
                    status = R.string.voice_speech_unavailable
                }
            }) { Text(stringResource(R.string.voice_read)) }
        }
    }, confirmButton = {
        Button({ onCommand(command ?: VoiceCommand.Range(range)) }, enabled = !listening &&
            (transcript.isBlank() || command != null) && (command !is VoiceCommand.ReviewPayment || payment != null) &&
            (command !is VoiceCommand.ReviewReminder || reminder != null)) {
            Text(stringResource(if (commandLabel != null) R.string.voice_open_command else R.string.voice_use))
        }
    },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.close)) } })
}
