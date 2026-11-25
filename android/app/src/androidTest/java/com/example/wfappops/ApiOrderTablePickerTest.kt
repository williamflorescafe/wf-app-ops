package com.example.wfappops

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.orders.view.ApiOrderTableSheet
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.tables.*
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ApiOrderTablePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectorUsesApiNamesAndReturnsTheUnmodifiedRemoteId() {
        val location = TeamLocation("San Borja", "SB", "10")
        val table = AvailableTable("api-table-999999999999", "T-99", "Mesa terraza", 6)
        var selected: String? = null
        compose.setContent {
            WfTheme {
                ApiOrderTableSheet(
                    OrderUiState(table = null, locations = listOf(location), selectedLocationId = location.id,
                        apiTables = Result.success(LocationTables(location,
                            listOf(TableArea("area-1", "Terraza", listOf(table)))))),
                    onLoad = { _, _ -> }, onDismiss = {}, onSelect = { selected = it }
                )
            }
        }
        compose.onNodeWithText("Mesa terraza").assertIsDisplayed()
        compose.onNodeWithText("T-99").assertIsDisplayed()
        compose.onNodeWithText("Mesa 15").assertDoesNotExist()
        compose.onNodeWithText("Mesa terraza").performClick()
        compose.runOnIdle { assertEquals(table.id, selected) }
    }
}
