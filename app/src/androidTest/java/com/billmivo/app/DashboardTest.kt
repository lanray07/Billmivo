package com.billmivo.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DashboardTest {
    @get:Rule val ui = createComposeRule()

    @Test fun showsCertaintyAndChangesForecastRange() {
        val today = LocalDate.of(2026, 11, 15)
        val bills = listOf(
            Schedule(1, "rent", "Rent", "housing", "GBP", "720.00".toBigDecimal(), false, today, Cadence.MONTHLY),
            Schedule(2, "power", "Electricity", "utilities", "GBP", "84.50".toBigDecimal(), true, today.plusDays(9), Cadence.MONTHLY)
        )
        val records = Records(bills, emptyList(), emptyList())
        var selected = -1
        var opened: OccurrenceKey? = null
        ui.setContent {
            BillmivoTheme(false) {
                Dashboard(records, records.forecast(today, today, today.plusDays(29)),
                    records.spending(today, "GBP"), "GBP", listOf("GBP"), 30, today, today.plusDays(29), today,
                    onCurrency = {}, onRange = { days, _, _ -> selected = days }, onAdd = {}, onSelect = { opened = it.key })
            }
        }
        ui.onAllNodesWithText("Confirmed").onFirst().assertIsDisplayed()
        ui.onAllNodesWithText("Estimated").onFirst().assertIsDisplayed()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ui.onNode(hasText("Upcoming payments") and heading).assertExists()
        ui.onNode(hasText("Rent") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction().performClick()
        ui.runOnIdle { assertEquals(OccurrenceKey(1, today), opened) }
        ui.onNodeWithText("7 days").performClick()
        ui.runOnIdle { assertEquals(7, selected) }
    }
}
