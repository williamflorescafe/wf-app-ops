package com.example.wfappops

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.wfappops.orders.view.CreateOrderActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountStartupTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun grantNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @Test
    fun demoShowsLoginAndContinuesWithEmptyCredentials() {
        val mesa = compose.activity.getString(R.string.tables_number, 1)
        val login = compose.activity.getString(R.string.auth_login)
        val profile = compose.activity.getString(R.string.auth_account)
        val profileTab = compose.activity.getString(R.string.ops_profile)
        val homeTab = compose.activity.getString(R.string.ops_home)
        val tablesTab = compose.activity.getString(R.string.tables_title)
        val cartaTab = compose.activity.getString(R.string.ops_carta)
        val scheduleTab = compose.activity.getString(R.string.ops_schedule)
        val continueLabel = compose.activity.getString(R.string.auth_continue)
        val create = compose.activity.getString(R.string.home_create_order)

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(continueLabel).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText(mesa).assertDoesNotExist()
        compose.onNodeWithText(profileTab).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_email)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_password)).assertExists()
        compose.onNodeWithText(continueLabel).performScrollTo().performClick()

        compose.onNodeWithText(homeTab).assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.home_my_orders)).assertIsDisplayed()
        compose.onNodeWithText(create).performClick()
        val order = compose.activity.getString(R.string.order_table_number, 1)
        val tableSearch = hasContentDescription(compose.activity.getString(R.string.tables_search)) and isFocused()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(tableSearch).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithText(order).fetchSemanticsNodes().size == 1
        }
        compose.runOnIdle {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            assertTrue(resumed.any { it is CreateOrderActivity })
        }
        compose.onNodeWithText(order).performClick()
        val search = compose.activity.getString(R.string.order_search)
        val newDispatch = hasText(compose.activity.getString(R.string.order_new_dispatch)) and isEnabled()
        val pending = compose.activity.getString(R.string.order_draft_status)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(search).fetchSemanticsNodes().size == 1
        }
        pressBack()
        compose.onNodeWithText(pending).assertDoesNotExist()
        compose.onNode(newDispatch).assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp).performClick()
        compose.onNodeWithContentDescription(search).assertIsDisplayed()
        val addEspresso = compose.activity.getString(R.string.order_add_named, "ESPRESSO")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(addEspresso).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription(addEspresso)
            .assertIsDisplayed().assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        compose.onNode(hasText("ESPRESSO") and hasClickAction()).performClick()
        val noteLabel = compose.activity.getString(R.string.order_optional_note)
        val note = "Sin azúcar"
        compose.onNode(hasText(noteLabel) and hasSetTextAction()).performScrollTo().performTextInput(note)
        compose.onNodeWithText(compose.activity.getString(R.string.carta_customize)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.order_add_product)).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.tables_choose_table)).assertDoesNotExist()
        val continueProducts = hasText(compose.activity.getString(R.string.order_continue_products)) and isEnabled()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(continueProducts).fetchSemanticsNodes().size == 1
        }
        compose.onNode(continueProducts)
            .assertIsDisplayed().assertHeightIsAtLeast(44.dp).performClick()
        compose.onNodeWithText(order).assertIsDisplayed()
        compose.onNodeWithText(pending).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.order_note_text, note)).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(homeTab).assertIsSelected()
        compose.onNodeWithText(create).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(order).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText(order).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(pending).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription(search).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.order_note_text, note)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.order_send)).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(newDispatch).fetchSemanticsNodes().size == 1
        }
        pressBack()

        compose.onNodeWithText(create).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(order).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText(order).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("#1").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription(search).assertDoesNotExist()
        compose.onNode(newDispatch).assertIsDisplayed()
        pressBack()

        val orderCard = hasText(mesa) and hasClickAction()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(orderCard).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(orderCard)[0].performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(newDispatch).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText(order).assertIsDisplayed()
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.tables_choose_table)).assertDoesNotExist()
        pressBack()
        compose.onNodeWithText(tablesTab).assertDoesNotExist()
        compose.onNodeWithText(scheduleTab).assertDoesNotExist()
        compose.onNodeWithText(login).assertDoesNotExist()
        compose.onNodeWithText(continueLabel).assertDoesNotExist()

        compose.onNodeWithText(cartaTab).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.cart_title)).assertDoesNotExist()
        compose.onNodeWithText(profileTab).performClick()
        compose.onNodeWithText(profileTab).assertIsSelected()
        compose.onNodeWithText(profile).assertIsDisplayed()
        compose.onNodeWithText("Ana · Pruebas").assertIsDisplayed()
        compose.onNodeWithText(login).assertDoesNotExist()
    }
}
