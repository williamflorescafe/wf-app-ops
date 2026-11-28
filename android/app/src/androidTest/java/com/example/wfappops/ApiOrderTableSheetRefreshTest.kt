package com.example.wfappops

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.orders.view.ApiOrderTableSheet
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.tables.AvailableTable
import com.example.wfappops.tables.LocationTables
import com.example.wfappops.tables.TableArea
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ApiOrderTableSheetRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun swipeRefreshesTheSheetWithoutReloadButtonOrDismissal() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val location = TeamLocation("Local", "LOC-01", "10")
        val state = mutableStateOf(OrderUiState(table = null, locations = listOf(location), selectedLocationId = "10",
            apiTables = Result.success(LocationTables(location, listOf(
                TableArea("20", "Salón 1", listOf(AvailableTable("900", "M01", "Mesa salón", 4)))
            )))))
        var refreshes = 0
        var dismissals = 0
        compose.setContent {
            WfTheme {
                ApiOrderTableSheet(state.value, onLoad = { locationId, refresh ->
                    assertEquals("10", locationId)
                    if (refresh) { refreshes++; state.value = state.value.copy(tablesLoading = true, tablesUserRefreshing = true) }
                }, onDismiss = { dismissals++ }, onSelect = {})
            }
        }
        compose.onNodeWithText(app.getString(R.string.state_reload)).assertDoesNotExist()
        val list = hasScrollAction() and hasAnyDescendant(hasText("Mesa salón"))
        compose.onNode(list).performTouchInput { swipeDown() }
        compose.onNodeWithText("Mesa salón").assertIsDisplayed().assertIsNotEnabled()
        compose.onNode(list).performTouchInput { swipeDown() }
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(0, dismissals)
        }
    }

    @Test fun cachedTablesStayVisibleWithoutSpinnerDuringAutomaticRefresh() {
        val location = TeamLocation("Local", "LOC-01", "10")
        val state = mutableStateOf(OrderUiState(table = null, locations = listOf(location), selectedLocationId = "10",
            apiTables = Result.success(LocationTables(location, listOf(TableArea("20", "Salón 1",
                listOf(AvailableTable("900", "M01", "Mesa salón", 4))))))))
        compose.setContent { WfTheme { ApiOrderTableSheet(state.value, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("Mesa salón").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(tablesLoading = true, tablesUserRefreshing = false) }
        compose.onNodeWithText("Mesa salón").assertIsDisplayed()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(tablesLoading = false) }
        compose.onNodeWithText("Mesa salón").assertIsDisplayed()
    }
}
