package com.billmivo.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BillJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun enteredBillAppearsInForecastAndPaymentUpdatesTheBalance() {
        val repo = (ui.activity.application as BillmivoApplication).repository
        val name = "Forecast test ${System.nanoTime()}"
        val today = LocalDate.now()
        try {
            ui.waitUntil(12_000) { ui.onAllNodesWithText("Add bill").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Add bill").performClick()
            ui.onNodeWithText("Bill name").performTextInput(name)
            ui.onNodeWithText("Amount").performTextInput("12.50")
            ui.onNodeWithText("Save").performClick()
            ui.waitUntil(12_000) { ui.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(name).performScrollTo().assertIsDisplayed().performClick()
            ui.onNodeWithText("Record payment").performClick()
            ui.waitUntil(12_000) {
                runBlocking {
                    val records = repo.records.first()
                    val id = records.schedules.singleOrNull { it.name == name }?.id
                    records.payments.any { it.key.scheduleId == id && it.date == today }
                }
            }
            val records = runBlocking { repo.records.first() }
            val bill = records.schedules.single { it.name == name }
            val occurrence = records.occurrences(today, today).single { it.schedule.id == bill.id }
            assertFalse(occurrence.open)
            assertEquals(0, "12.50".toBigDecimal().compareTo(occurrence.paid))
        } finally {
            runBlocking {
                repo.records.first().schedules.singleOrNull { it.name == name }?.let { repo.delete(it.series) }
            }
        }
    }
}
