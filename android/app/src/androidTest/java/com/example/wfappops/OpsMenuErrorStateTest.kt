package com.example.wfappops

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.orders.view.OrderProductSheet
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OpsMenuErrorStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingPermissionShowsCartaAccessAndRevalidatesInsteadOfClaimingNoProducts() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var revalidated = 0
        compose.setContent { WfTheme {
            OrderProductSheet(emptyList(), false, false, 0,
                Result.failure(AuthApiException(403, code = "client.menu_permission_missing")),
                onRetry = {}, onDismiss = {}, onAdd = {}, onRefresh = { revalidated++ })
        } }
        compose.onNodeWithText(app.getString(R.string.menu_access_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.menu_permission_missing)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.state_error_title)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.menu_empty)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.menu_refresh_access)).performClick()
        compose.runOnIdle { assertEquals(1, revalidated) }
    }

    @Test fun successfulEmptyCatalogShowsEmptyStateRatherThanPermissionFailure() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { WfTheme {
            OrderProductSheet(emptyList(), false, false, 0, Result.success(emptyList()),
                onRetry = {}, onDismiss = {}, onAdd = {})
        } }
        compose.onNodeWithText(app.getString(R.string.menu_empty)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.menu_access_title)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.state_error_title)).assertDoesNotExist()
    }
}
