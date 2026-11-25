package com.example.wfappops

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.orders.model.*
import com.example.wfappops.tables.*
import com.example.wfappops.ui.theme.WfTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DraftTableScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savingAndRemovingOnlyDraftProductUpdatesOccupancyWithoutRefresh() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TableWorker("7", "Álvaro Gallegos")
        val location = TeamLocation("Centro", "CTR", "10")
        val table = AvailableTable("900", "M01", "1", 6)
        val state = AccountUiState(isStaging = true, appVersion = "test", restoring = false, signedIn = true,
            businessContext = TeamBusinessContext("1", "Negocio", worker.name, worker.id, "3",
                locations = listOf(location), permissions = setOf("pos.tables.view")),
            selectedLocationId = location.id,
            tables = Result.success(LocationTables(location, listOf(TableArea("20", "Salón 1", listOf(table))))))
        val service = mutableStateOf(TableService().openApiTable(location, table, worker))
        val visitId = service.value.visits.single().id
        var refreshes = 0
        var selectedVisit: String? = null
        compose.setContent {
            WfTheme { OpsTablesScreen(state, attentions = service.value.localTableAttentions(),
                onSelectTable = { _, _, attention -> selectedVisit = (attention as TableAttention.Occupied).visitId },
                onLoad = { _, refresh -> if (refresh) refreshes++ }) }
        }
        compose.onNodeWithText(app.getString(R.string.tables_free)).assertIsDisplayed()
        compose.runOnIdle {
            service.value = service.value.add(visitId, OrderLine("Espresso", 1, 800),
                Instant.parse("2026-11-24T21:30:00Z"), worker)
        }
        compose.onNodeWithText("Álvaro G.").assertIsDisplayed()
        compose.onNodeWithText("16:30").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_occupied_status)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_attention_unknown)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.tables_filter_occupied)).performClick()
        compose.onNodeWithText("Álvaro G.").assertIsDisplayed()
        compose.onNodeWithText("1").performClick()
        compose.runOnIdle { assertEquals(visitId, selectedVisit); assertEquals(0, refreshes) }
        File(app.cacheDir, "ops-draft-table.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText(app.getString(R.string.ops_filter_all)).performClick()
        compose.runOnIdle {
            service.value = service.value.adjustDraft(visitId, OrderLine("Espresso", 1, 800), -1)
        }
        compose.onNodeWithText(app.getString(R.string.tables_free)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_occupied_status)).assertDoesNotExist()
        compose.onNodeWithText("Álvaro G.").assertDoesNotExist()
        compose.onNodeWithText("16:30").assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.tables_open_action)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, refreshes) }
    }
}
