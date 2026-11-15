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
fun VoiceDialog(records: Records, currency: String, today: LocalDate, spoken: Boolean,
                onClose: () -> Unit, onRange: (Int) -> Unit) {
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
                    val selected = parseForecastRange(transcript, locale)
                    if (selected == null) status = R.string.voice_error else range = selected
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
    val total = remember(records, range, today, currency) {
        records.forecast(today, today, today.plusDays(range.toLong() - 1)).totals(currency)
    }
    val summary = stringResource(R.string.voice_summary, stringResource(voiceLabels.getValue(range)),
        money(total.confirmed, currency, locale), money(total.estimated, currency, locale))
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.voice_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.voice_intro), color = palette.muted)
            Text(stringResource(R.string.voice_try, voiceRanges.joinToString(", ") { java.text.NumberFormat.getIntegerInstance(locale).format(it) }))
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
                transcript = it.take(100)
                parseForecastRange(transcript, locale)?.let { parsed -> range = parsed; status = null }
            }, label = { Text(stringResource(R.string.voice_type)) }, singleLine = true, enabled = !listening)
            status?.let { Text(stringResource(it), color = palette.red) }
            MenuField(stringResource(R.string.period), range, voiceRanges,
                { stringResource(voiceLabels.getValue(it)) }, { range = it })
            Text(summary)
            if (spoken) OutlinedButton({
                val engine = speech
                if (engine == null || !ttsReady || engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE ||
                    engine.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "forecast") == TextToSpeech.ERROR) {
                    status = R.string.voice_speech_unavailable
                }
            }) { Text(stringResource(R.string.voice_read)) }
        }
    }, confirmButton = { Button({ onRange(range) }) { Text(stringResource(R.string.voice_use)) } },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.close)) } })
}
