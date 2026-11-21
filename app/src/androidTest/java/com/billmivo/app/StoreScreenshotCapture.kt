package com.billmivo.app

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class StoreScreenshotCapture {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun capturesTranslatedFireScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("demo") == "true") { "Run this fixture explicitly with demo=true." }
        val localeTag = requireNotNull(arguments.getString("previewLanguage"))
        val original = AppCompatDelegate.getApplicationLocales()
        StorePreviewFixture().seedFictionalHousehold()
        val context = instrumentation.targetContext
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(localeTag))
        }
        val translated = context.createConfigurationContext(configuration)
        if (localeTag == "pl-PL") {
            mapOf(1 to "1 nieopłacony rachunek", 2 to "2 nieopłacone rachunki",
                5 to "5 nieopłaconych rachunków", 12 to "12 nieopłaconych rachunków",
                22 to "22 nieopłacone rachunki").forEach { (count, expected) ->
                org.junit.Assert.assertEquals(expected, translated.resources.getQuantityString(R.plurals.open_count, count, count))
            }
            mapOf(1 to "1 dzień wcześniej", 2 to "2 dni wcześniej", 5 to "5 dni wcześniej",
                12 to "12 dni wcześniej", 22 to "22 dni wcześniej").forEach { (count, expected) ->
                org.junit.Assert.assertEquals(expected, translated.resources.getQuantityString(R.plurals.reminder_days, count, count))
            }
        }
        if (localeTag == "tr-TR") {
            listOf(0, 1, 2, 5, 22).forEach { count ->
                org.junit.Assert.assertEquals("$count ödenmemiş fatura",
                    translated.resources.getQuantityString(R.plurals.open_count, count, count))
                org.junit.Assert.assertEquals("$count gün önce",
                    translated.resources.getQuantityString(R.plurals.reminder_days, count, count))
            }
        }
        fun text(id: Int) = translated.getString(id)
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val output = File(context.getExternalFilesDir(null), "store-captures/$localeTag").apply { mkdirs() }
            fun capture(name: String, visibleLabel: Int) {
                ui.waitUntil(timeoutMillis = 15_000) {
                    ui.onAllNodesWithText(text(visibleLabel)).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onAllNodesWithText(text(visibleLabel)).onFirst().assertIsDisplayed()
                ui.waitForIdle()
                // Android's dialog window animation runs outside Compose's idle clock.
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(500)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    check(bitmap.width == 1920 && bitmap.height == 1200) { "Use the Fire HD 10 in landscape." }
                    File(output, "$name.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            fun open(id: Int) {
                val node = ui.onNodeWithText(text(id))
                if (!node.isDisplayed()) node.performScrollTo()
                node.performClick()
            }
            if (arguments.getString("changesOnly") == "true") {
                open(R.string.insights)
                ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 420f) }
                capture("changes", R.string.bill_changes)
                return
            }
            capture("forecast", R.string.forecast)
            open(R.string.days_7)
            capture("week", R.string.forecast)
            open(R.string.more)
            open(R.string.reminders)
            capture("reminders", R.string.reminders)
            open(R.string.settings)
            open(R.string.household)
            ui.onNodeWithText(text(R.string.shares_30)).performScrollTo()
            ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 700f) }
            capture("sharing", R.string.shares_30)
            open(R.string.insights)
            capture("spending", R.string.spending)
            ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 420f) }
            capture("changes", R.string.bill_changes)
            ui.onNodeWithContentDescription(text(R.string.voice)).performClick()
            capture("voice", R.string.voice_command_title)
            if (localeTag == "pl-PL") {
                val repository = (context.applicationContext as BillmivoApplication).repository
                val before = runBlocking { repository.records.first() }
                if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                    ui.onNodeWithText(text(R.string.voice_unavailable)).assertIsDisplayed()
                }
                ui.onNodeWithText(text(R.string.voice_command_input))
                    .performTextInput("Oznacz Rachunek za wodę jako opłacony")
                open(R.string.voice_open_command)
                ui.onNodeWithText(text(R.string.record_payment)).assertIsDisplayed()
                ui.onNodeWithText("Rachunek za wodę").assertIsDisplayed()
                open(R.string.cancel)
                ui.onNodeWithContentDescription(text(R.string.voice)).performClick()
                ui.onNodeWithText(text(R.string.voice_command_input))
                    .performTextInput("Przypomnij mi zapłacić Prąd w piątek")
                open(R.string.voice_open_command)
                ui.onNodeWithText(text(R.string.one_off_reminder)).assertIsDisplayed()
                val friday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                ui.onNodeWithText(translated.getString(R.string.chart_value, text(R.string.one_off_reminder_date),
                    dateLabel(friday, Locale.forLanguageTag(localeTag)))).assertIsDisplayed()
                open(R.string.cancel)
                org.junit.Assert.assertEquals(before, runBlocking { repository.records.first() })
            } else if (localeTag == "tr-TR") {
                val repository = (context.applicationContext as BillmivoApplication).repository
                val before = runBlocking { repository.records.first() }
                if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                    ui.onNodeWithText(text(R.string.voice_unavailable)).assertIsDisplayed()
                }
                ui.onNodeWithText(text(R.string.voice_command_input))
                    .performTextInput("EV İNTERNETİ İÇİN ÖDEMEYİ İNCELE")
                open(R.string.voice_open_command)
                ui.onNodeWithText(text(R.string.record_payment)).assertIsDisplayed()
                ui.onNodeWithText("Ev interneti").assertIsDisplayed()
                open(R.string.cancel)
                ui.onNodeWithContentDescription(text(R.string.voice)).performClick()
                ui.onNodeWithText(text(R.string.voice_command_input))
                    .performTextInput("Elektrik ödemesini cuma günü hatırlat")
                open(R.string.voice_open_command)
                ui.onNodeWithText(text(R.string.one_off_reminder)).assertIsDisplayed()
                val friday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                ui.onNodeWithText(translated.getString(R.string.chart_value, text(R.string.one_off_reminder_date),
                    dateLabel(friday, Locale.forLanguageTag(localeTag)))).assertIsDisplayed()
                open(R.string.cancel)
                org.junit.Assert.assertEquals(before, runBlocking { repository.records.first() })
            } else open(R.string.close)
            open(R.string.calendar)
            capture("calendar", R.string.calendar)
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(original) }
            scenario.close()
        }
    }
}
