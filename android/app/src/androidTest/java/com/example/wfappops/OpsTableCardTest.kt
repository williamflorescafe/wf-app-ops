package com.example.wfappops

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.tables.AvailableTable
import com.example.wfappops.tables.OpsTableCard
import com.example.wfappops.tables.TableAttention
import com.example.wfappops.tables.TableAttendant
import com.example.wfappops.ui.theme.WfTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OpsTableCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun occupiedAndFreeCardsUseRealCapacityAndKeepTheirActionsDistinct() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var additions = 0
        compose.setContent {
            WfTheme {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OpsTableCard(AvailableTable("10", "M01", "1", 6), Modifier.weight(1f),
                        attention = TableAttention.Occupied(TableAttendant("Álvaro", "Gallegos"), Instant.parse("2026-11-24T21:30:00Z")))
                    OpsTableCard(AvailableTable("11", "M02", "2", 4), Modifier.weight(1f),
                        attention = TableAttention.Free, onAdd = { additions++ })
                }
            }
        }
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("6 personas").assertIsDisplayed()
        compose.onNodeWithText("Álvaro G.").assertIsDisplayed()
        compose.onNodeWithText("16:30").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_occupied_status)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.tables_free)).assertIsDisplayed()
        File(app.cacheDir, "ops-table-card-states.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription(app.getString(R.string.tables_open_action)).performClick()
        compose.runOnIdle { assertEquals(1, additions) }
    }

    @Test
    fun tableWithoutLocalAttentionShowsFreeState() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { WfTheme { OpsTableCard(AvailableTable("10", "M01", "1", 6)) } }
        compose.onNodeWithText(app.getString(R.string.tables_attention_unknown)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.tables_free)).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.tables_open_action)).assertIsDisplayed()
    }
}
