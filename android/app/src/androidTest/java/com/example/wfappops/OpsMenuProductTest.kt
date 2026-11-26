package com.example.wfappops

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.carta.ProductDetail
import com.example.wfappops.carta.parseOpsMenu
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.ui.theme.WfTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OpsMenuProductTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingAVariantAndRepeatingAnOptionProducesTheApiIdsAndFinalPrice() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val payload = opsMenuFixture()
        val category = payload.getJSONObject("data").getJSONArray("categories").getJSONObject(0)
        listOf(category.getJSONArray("products").getJSONObject(0),
            category.getJSONArray("subcategories").getJSONObject(0).getJSONArray("products").getJSONObject(0)).forEach {
            it.getJSONArray("variants").put(JSONObject("""{"id":"small","name":"Pequeño","price_minor":1000,"currency_code":"PEN","available":true}"""))
        }
        val product = parseOpsMenu(payload.toString(), "10").collections.single().sections.single().products.single()
        var added: OrderLine? = null
        compose.setContent { WfTheme { ProductDetail(product, "Bebidas", false, { added = it }, forOrder = true) } }
        compose.onNodeWithText(app.getString(R.string.order_add_product)).assertIsNotEnabled()
        compose.onNodeWithText("Grande").performClick()
        compose.onNodeWithText("S/ 16.50").assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.menu_add_option, "Leche de almendras")).performClick()
        compose.onNodeWithText("S/ 18.50").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.order_add_product)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("coffee", added?.productId)
            assertEquals("big", added?.variantId)
            assertEquals(1850L, added?.unitPriceInCents)
            assertEquals(2, added?.modifierSelections?.single()?.quantity)
        }
    }
}
