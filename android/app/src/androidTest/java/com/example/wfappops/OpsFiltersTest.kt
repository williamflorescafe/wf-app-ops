package com.example.wfappops

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.home.view.OrdersUnavailableScreen
import com.example.wfappops.tables.AvailableTable
import com.example.wfappops.tables.LocationTables
import com.example.wfappops.tables.OpsTablesScreen
import com.example.wfappops.tables.TableArea
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Rule
import org.junit.Test

class OpsFiltersTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun ordersShowFixedTwoDayWindowAndIndependentFilters() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { WfTheme { OrdersUnavailableScreen() } }
        capture("ops-comandas-filters.png")
        compose.onNodeWithText(app.getString(R.string.orders_last_two_days)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.home_my_orders)).assertIsSelected()
        compose.onAllNodesWithText(app.getString(R.string.ops_filter_all))[0].performClick().assertIsSelected()
        compose.onNodeWithText(app.getString(R.string.order_ready)).performClick().assertIsSelected()
        compose.onAllNodesWithText(app.getString(R.string.ops_filter_all))[0].assertIsSelected()
    }

    @Test
    fun tablesSearchRealCodesSelectAreasAndFilterFreeTables() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val location = TeamLocation("Local", "LOC-01", "10")
        val state = AccountUiState(
            isStaging = true, appVersion = "test", restoring = false, signedIn = true,
            businessContext = TeamBusinessContext("1", "Negocio", "Colaborador", "2", "3",
                locations = listOf(location), permissions = setOf("pos.tables.view")),
            selectedLocationId = "10", tables = Result.success(LocationTables(location, listOf(
                TableArea("20", "Salón 1", listOf(AvailableTable("900", "S01", "Mesa salón", 4))),
                TableArea("30", "Terraza", listOf(AvailableTable("400", "T03", "Mesa terraza", 2)))
            )))
        )
        compose.setContent { WfTheme { OpsTablesScreen(state) { _, _ -> } } }
        capture("ops-mesas-filters.png")
        compose.onNodeWithText("Mesa salón").assertIsDisplayed()
        compose.onNodeWithText("Salón 1").performClick()
        compose.onNodeWithText("Terraza").performClick()
        compose.onNodeWithText("Mesa terraza").assertIsDisplayed()
        compose.onNodeWithText("Mesa salón").assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.tables_search_name_code)).performTextInput("T03")
        compose.onNodeWithText("Mesa terraza").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_filter_free)).performClick()
        compose.onNodeWithText(app.getString(R.string.tables_service_status_unavailable)).assertDoesNotExist()
        compose.onNodeWithText("Mesa terraza").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_free)).assertIsDisplayed()
    }

    private fun capture(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        File(app.cacheDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
