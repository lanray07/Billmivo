package com.billmivo.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HouseholdScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun configuresEqualSharingAndShowsTheRoundedShares() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BillDatabase::class.java).build()
        try {
            val today = LocalDate.now()
            val repo = BillRepository(database)
            val state = runBlocking {
                repo.saveMember(Member("alex", "Alex"))
                repo.saveMember(Member("jamie", "Jamie"))
                repo.save(Schedule(series = "rent", name = "Household test rent", category = "housing", currency = "GBP",
                    amount = BigDecimal("36.01"), estimated = false, first = today, cadence = Cadence.MONTHLY), null, today)
                mutableStateOf(repo.records.first())
            }
            ui.setContent {
                BillmivoTheme(false) {
                    HouseholdPage(state.value, today, save = { action, success, _ ->
                        runBlocking { repo.action(); state.value = repo.records.first() }
                        success()
                    }, back = {})
                }
            }
            ui.onNodeWithText("Household test rent").performScrollTo().performClick()
            ui.onNodeWithText("Responsible member · Unassigned").performClick()
            ui.onAllNodesWithText("Jamie").onLast().performClick()
            ui.onNodeWithText("Split method · No split").performClick()
            ui.onNodeWithText("Equal shares").performClick()
            ui.onAllNodesWithText("Alex").onLast().performScrollTo().performClick()
            ui.onAllNodesWithText("Jamie").onLast().performScrollTo().performClick()
            ui.onNodeWithText("Save").performClick()
            ui.onNodeWithText("Alex: £18.01").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Jamie: £18.00").performScrollTo().assertIsDisplayed()
            val saved = runBlocking { repo.records.first().sharing.single() }
            assertEquals("jamie", saved.responsibleId)
            assertEquals(SplitMode.EQUAL, saved.mode)
        } finally {
            database.close()
        }
    }
}
