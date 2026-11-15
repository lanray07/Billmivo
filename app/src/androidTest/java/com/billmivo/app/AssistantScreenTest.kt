package com.billmivo.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AssistantScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun preparesLocalDraftOnlyAfterReviewIsRequested() {
        val today = LocalDate.of(2026, 11, 15)
        var draft: BillDraft? = null
        ui.setContent {
            BillmivoTheme(false) {
                AssistantPage(Records(emptyList(), emptyList(), emptyList()), today, { draft = it }, {})
            }
        }
        ui.onNodeWithText("Choose a task · What’s due in the next 30 days?").performClick()
        ui.onNodeWithText("Draft a bill").performClick()
        ui.onNodeWithText("Draft a bill").performTextInput("Gas costs 32.15 GBP due 2026-12-01")
        ui.runOnIdle { assertNull(draft) }
        ui.onNodeWithText("Review bill draft").performClick()
        ui.runOnIdle {
            assertEquals(BillDraft("Gas", BigDecimal("32.15"), "GBP", LocalDate.of(2026, 12, 1)), draft)
        }
    }
}
