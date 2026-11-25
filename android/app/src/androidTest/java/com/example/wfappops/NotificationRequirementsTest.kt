package com.example.wfappops

import android.app.NotificationManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.notifications.NotificationIssue
import com.example.wfappops.notifications.NotificationRequirementNotice
import com.example.wfappops.notifications.ReadyProductNotifier
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationRequirementsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bannerHasOneSettingsActionAndCannotBeDismissed() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var configurations = 0
        compose.setContent { WfTheme { NotificationRequirementNotice(NotificationIssue.PERMISSION) { configurations++ } } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText(app.getString(R.string.notification_configure)).performClick()
        compose.onNodeWithText(app.getString(R.string.notification_require_permission)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, configurations) }
    }

    @Test
    fun checkerDoesNotOverwriteUserChannelSettings() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = app.getSystemService(NotificationManager::class.java)
        val notifier = ReadyProductNotifier(app)
        notifier.requirementsIssue()
        val before = manager.getNotificationChannel("kitchen-ready")
        notifier.requirementsIssue()
        val after = manager.getNotificationChannel("kitchen-ready")
        assertEquals(before.importance, after.importance)
        assertEquals(before.sound, after.sound)
        assertEquals(before.shouldVibrate(), after.shouldVibrate())
    }
}
