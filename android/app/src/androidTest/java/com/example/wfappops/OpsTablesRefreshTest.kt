package com.example.wfappops

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.tables.AvailableTable
import com.example.wfappops.tables.LocationTables
import com.example.wfappops.tables.OpsTablesScreen
import com.example.wfappops.tables.TableArea
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OpsTablesRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun swipeRefreshesCurrentLocationAndKeepsTablesVisibleWithoutDuplicateRequests() {
        val location = TeamLocation("Local", "LOC-01", "10")
        val state = mutableStateOf(AccountUiState(
            isStaging = true, appVersion = "test", restoring = false, signedIn = true,
            businessContext = TeamBusinessContext("1", "Negocio", "Colaborador", "2", "3",
                locations = listOf(location), permissions = setOf("pos.tables.view")),
            selectedLocationId = "10",
            tables = Result.success(LocationTables(location, listOf(
                TableArea("20", "Terraza", listOf(AvailableTable("900", "T03", "Terraza 3", 4)))
            )))
        ))
        var refreshes = 0
        compose.setContent {
            WfTheme {
                OpsTablesScreen(state.value) { locationId, refresh ->
                    if (refresh) {
                        assertEquals("10", locationId)
                        refreshes++
                        state.value = state.value.copy(tablesLoading = true)
                    }
                }
            }
        }
        val list = hasScrollAction() and hasAnyDescendant(hasText("Terraza 3"))
        compose.onNode(list).performTouchInput { swipeDown() }
        compose.onNodeWithText("Terraza 3").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNode(list).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }
}
