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
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
            open(R.string.close)
            open(R.string.calendar)
            capture("calendar", R.string.calendar)
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(original) }
            scenario.close()
        }
    }
}
