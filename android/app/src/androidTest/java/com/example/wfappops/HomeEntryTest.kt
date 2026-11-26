package com.example.wfappops

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.view.HomeScreen
import com.example.wfappops.notifications.NotificationsScreen
import com.example.wfappops.notifications.LocalNotification
import com.example.wfappops.notifications.LocalNotificationKind
import com.example.wfappops.ui.components.*
import com.example.wfappops.ui.theme.WfTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun heroAndBellHaveIndependentActions() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var created = 0
        var notifications = 0
        compose.setContent { WfTheme {
            OpsScaffold(bottomBar = {
                OpsBottomBar(OpsTab.HOME, {}, onCreateOrder = { created++ })
            }) {
                HomeScreen("Álvaro Gallegos", true, { created++ }, Result.success(emptyList()),
                    HomeOrderFilter.ALL, {}, {}, {}, onNotifications = { notifications++ })
            }
        } }
        compose.onNodeWithText("Hola, Álvaro Gallegos").assertIsDisplayed()
        compose.onNodeWithTag("home-create-order-card").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.home_notifications)).performClick()
        compose.runOnIdle {
            assertEquals(1, created)
            assertEquals(1, notifications)
        }
        capture("ops-home-illustrated.png")
    }

    @Test fun emptyNotificationsScreenHasItsOwnBackAction() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var backs = 0
        compose.setContent { WfTheme {
            NotificationsScreen(Result.success(emptyList()), { backs++ })
        } }
        compose.onNodeWithText(app.getString(R.string.home_notifications_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.auth_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
        capture("ops-notifications-screen.png")
    }

    @Test fun notificationHistoryShowsTableOrderAndBothKindsOfNotice() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val now = Instant.now()
        val entries = listOf(
            LocalNotification("ready-test", LocalNotificationKind.READY, "Cappuccino", 2, "2", 12, "1", now.minusSeconds(20), "San Borja"),
            LocalNotification("reminder-test", LocalNotificationKind.SERVE_REMINDER, "Espresso", 1, "3", 13, "1", now.minusSeconds(180), "San Borja")
        )
        compose.setContent { WfTheme { NotificationsScreen(Result.success(entries), {}) } }
        compose.onNodeWithText(app.getString(R.string.notification_ready_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.notification_serve_reminder_title)).assertIsDisplayed()
        compose.onNodeWithText("Comanda #000012 · San Borja").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.home_orders_today)).assertIsDisplayed()
        capture("ops-notification-history.png")
    }

    private fun capture(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(app.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
