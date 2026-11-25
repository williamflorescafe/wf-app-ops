package com.example.wfappops

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.view.CreateOrderScreen
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InitialTableTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun alreadySelectedTableDoesNotShowTablePickerBeforeOpeningProducts() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val state = mutableStateOf(OrderUiState(table = null, openingSelectedTable = true, tablesLoading = true))
        var catalogLoads = 0
        compose.setContent {
            WfTheme {
                CreateOrderScreen(
                    state.value, Result.success(emptyList()), onLoadCatalog = { catalogLoads++ },
                    onReload = {}, onOpenTable = {}, onAddProduct = {}, onChangeQuantity = { _, _ -> },
                    onSaveNote = { _, _ -> }, onSaveComment = {}, onSendToCashier = {}, onSendOrder = {},
                    onAdvanceLine = { _, _, _ -> }, onCancelLines = { _, _ -> },
                    onDismissKitchenUnavailable = {}, onCancel = {}
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.tables_choose_table)).assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(table = 1, visit = TableVisit("visit", 1, remoteTableId = "api-table"),
                openingSelectedTable = false, tablesLoading = false)
        }
        compose.onNodeWithText(app.getString(R.string.tables_choose_table)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.order_search)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(addedProducts = 1) }
        compose.runOnIdle { assertEquals(1, catalogLoads) }
    }
}
