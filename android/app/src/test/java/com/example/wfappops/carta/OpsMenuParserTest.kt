package com.example.wfappops.carta

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpsMenuParserTest {
    private fun payload(): JSONObject {
        val product = JSONObject("""{"id":"product","name":"Cappuccino API","short_description":null,"available":true,
            "variants":[{"id":"variant","name":"Grande","price_minor":1450,"currency_code":"PEN","available":true}],
            "modifier_groups":[{"group_id":"milk","variant_id":null,"min_select":0,"max_select":2}]}""")
        return JSONObject().put("data", JSONObject()
            .put("location", JSONObject("""{"id":"10","code":"LOC","name":"Centro"}"""))
            .put("menu", JSONObject("""{"id":"menu","name":"Carta"}"""))
            .put("generated_at", "2026-11-25T17:40:19.955Z")
            .put("categories", JSONArray().put(JSONObject().put("id", "category").put("name", "Bebidas")
                .put("products", JSONArray().put(product))
                .put("subcategories", JSONArray().put(JSONObject().put("id", "hot").put("name", "Calientes")
                    .put("products", JSONArray().put(JSONObject(product.toString())))))))
            .put("modifier_groups", JSONArray().put(JSONObject("""{"id":"milk","name":"Leche","allows_repeat":true,
                "options":[{"id":"almond","name":"Almendras","price_delta_minor":200,"is_default":true,"available":true}]}"""))))
    }

    @Test fun fullResponsePreservesReferencesAndAvoidsRepeatingTheProductFromItsCategory() {
        val menu = parseOpsMenu(payload().toString(), "10")
        val section = menu.collections.single().sections.single()
        assertEquals("Calientes", section.name)
        val product = section.products.single()
        assertEquals("", product.description)
        assertEquals(1450L, product.priceInCents)
        val line = product.orderLine("variant", mapOf("milk" to listOf("almond", "almond")))
        assertEquals(1850L, line.unitPriceInCents)
        assertEquals("menu", line.menuId)
        assertEquals("10", line.locationId)
        assertEquals(2, line.modifierSelections.single().quantity)
    }

    @Test fun noMenuDoesNotFallBackToLocalProducts() {
        val response = payload().apply { getJSONObject("data").put("menu", JSONObject.NULL) }
        val menu = parseOpsMenu(response.toString(), "10")
        assertNull(menu.id)
        assertTrue(menu.collections.isEmpty())
    }

    @Test fun crossLocalAndDanglingReferencesAreRejected() {
        assertTrue(runCatching { parseOpsMenu(payload().toString(), "11") }.isFailure)
        val response = payload().apply { getJSONObject("data").put("modifier_groups", JSONArray()) }
        assertTrue(runCatching { parseOpsMenu(response.toString(), "10") }.isFailure)
    }

    @Test fun minorUnitsCannotSilentlyLoseFractionalData() {
        val response = payload()
        response.getJSONObject("data").getJSONArray("categories").getJSONObject(0)
            .getJSONArray("products").getJSONObject(0).getJSONArray("variants").getJSONObject(0).put("price_minor", "1450.5")
        assertTrue(runCatching { parseOpsMenu(response.toString(), "10") }.isFailure)
    }

    @Test fun inconsistentRepeatedProductDefinitionsAreRejected() {
        val response = payload()
        response.getJSONObject("data").getJSONArray("categories").getJSONObject(0)
            .getJSONArray("products").getJSONObject(0).put("name", "Nombre contradictorio")
        assertTrue(runCatching { parseOpsMenu(response.toString(), "10") }.isFailure)
    }
}
