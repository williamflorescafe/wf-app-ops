package com.example.wfappops

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.carta.*
import com.example.wfappops.carta.data.OpsMenuCache
import com.example.wfappops.orders.data.TableStore
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableWorker
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal fun opsMenuFixture(): JSONObject {
    val product = JSONObject("""{"id":"coffee","name":"Café API","short_description":null,"available":true,
        "variants":[{"id":"big","name":"Grande","price_minor":1450,"currency_code":"PEN","available":true}],
        "modifier_groups":[{"group_id":"milk","variant_id":null,"min_select":0,"max_select":3}]}""")
    return JSONObject().put("data", JSONObject()
        .put("location", JSONObject("""{"id":"10","code":"LOC","name":"Centro"}"""))
        .put("menu", JSONObject("""{"id":"menu","name":"Carta API"}"""))
        .put("generated_at", "2026-11-25T17:40:19.955Z")
        .put("categories", JSONArray().put(JSONObject().put("id", "category").put("name", "Bebidas")
            .put("products", JSONArray().put(product))
            .put("subcategories", JSONArray().put(JSONObject().put("id", "hot").put("name", "Calientes")
                .put("products", JSONArray().put(JSONObject(product.toString())))))))
        .put("modifier_groups", JSONArray().put(JSONObject("""{"id":"milk","name":"Tipo de leche","allows_repeat":true,
            "options":[{"id":"almond","name":"Leche de almendras","price_delta_minor":200,"is_default":true,"available":true}]}"""))))
}

class OpsMenuContractTest {
    @Test fun parsesTheCompleteContractWithoutDuplicatingProductIdentityOrUsingLocalModifiers() {
        val menu = parseOpsMenu(opsMenuFixture().toString(), "10")
        val product = menu.collections.single().sections.single().products.single()
        assertEquals("Calientes", menu.collections.single().sections.single().name)
        assertEquals("", product.description)
        assertEquals("coffee", product.id)
        val group = product.groupsFor("big").single()
        assertEquals("Tipo de leche", group.label)
        val line = product.orderLine("big", mapOf(group.id to group.defaults()))
        assertEquals(1650L, line.unitPriceInCents)
        assertEquals("almond", line.modifierSelections.single().optionId)
        assertEquals(Instant.parse("2026-11-25T17:40:19.955Z"), menu.generatedAt)
    }

    @Test fun nullMenuIsAnEmptyRemoteMenuAndInvalidReferencesOrWrongLocalAreRejected() {
        val none = opsMenuFixture().apply { getJSONObject("data").put("menu", JSONObject.NULL) }
        assertTrue(parseOpsMenu(none.toString(), "10").collections.isEmpty())
        assertNull(parseOpsMenu(none.toString(), "10").id)
        assertTrue(runCatching { parseOpsMenu(opsMenuFixture().toString(), "11") }.isFailure)
        val invalid = opsMenuFixture().apply { getJSONObject("data").put("modifier_groups", JSONArray()) }
        assertTrue(runCatching { parseOpsMenu(invalid.toString(), "10") }.isFailure)
        val conflicting = opsMenuFixture().apply { getJSONObject("data").getJSONArray("categories").getJSONObject(0)
            .getJSONArray("products").getJSONObject(0).put("name", "Otro producto") }
        assertTrue(runCatching { parseOpsMenu(conflicting.toString(), "10") }.isFailure)
    }

    @Test fun availabilityAndVariantSpecificLimitsAreHonored() {
        val payload = opsMenuFixture()
        val category = payload.getJSONObject("data").getJSONArray("categories").getJSONObject(0)
        val products = listOf(category.getJSONArray("products").getJSONObject(0),
            category.getJSONArray("subcategories").getJSONObject(0).getJSONArray("products").getJSONObject(0))
        products.forEach { product ->
            product.getJSONArray("variants").put(JSONObject("""{"id":"small","name":"Pequeño","price_minor":1000,"currency_code":"PEN","available":false}"""))
            product.getJSONArray("modifier_groups").put(JSONObject("""{"group_id":"milk","variant_id":"big","min_select":2,"max_select":2}"""))
        }
        val product = parseOpsMenu(payload.toString(), "10").collections.single().sections.single().products.single()
        assertEquals(2, product.groupsFor("big").single().minSelections)
        assertTrue(runCatching { product.orderLine("small", emptyMap()) }.isFailure)
        assertTrue(runCatching { product.orderLine("big", mapOf("milk" to listOf("almond"))) }.isFailure)
        assertEquals(1850L, product.orderLine("big", mapOf("milk" to listOf("almond", "almond"))).unitPriceInCents)
    }

    @Test fun persistedDraftKeepsAllApiReferencesAndRepeatedModifierQuantities() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "menu-persistence-${UUID.randomUUID()}"
        try {
            val product = parseOpsMenu(opsMenuFixture().toString(), "10").collections.single().sections.single().products.single()
            val line = product.orderLine("big", mapOf("milk" to listOf("almond", "almond")))
            val worker = TableWorker("worker", "Colaborador")
            val store = TableStore(app, id)
            store.update {
                val opened = TableService().createOrder(worker, Instant.EPOCH)
                opened.add(opened.visits.single().id, line, Instant.EPOCH, worker)
            }
            assertEquals(line, TableStore(app, id).read().visits.single().draft.single())
        } finally {
            app.noBackupFilesDir.listFiles().orEmpty().filter { it.name.contains(id) }.forEach { it.delete() }
        }
    }

    @Test fun cacheIsIsolatedReusedRefreshedAndNeverFallsBackAfterAccessOrNetworkFailure() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = "menu-contract-${UUID.randomUUID()}"
        val key = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
        var now = 1_000L
        val cache = OpsMenuCache(app) { now }
        var requests = 0
        val fetch = { requests++; opsMenuFixture().toString() }
        try {
            cache.get(scope, "10", fetch = fetch)
            cache.get(scope, "10", fetch = fetch)
            assertEquals(1, requests)
            assertTrue(runCatching { cache.get(scope, "10", stillAuthorized = { false }, fetch = fetch) }.isFailure)
            now += 60_001
            cache.get(scope, "10", fetch = fetch)
            assertEquals(2, requests)
            assertTrue(runCatching { cache.get(scope, "10", refresh = true) { throw AuthApiException(403) } }.isFailure)
            cache.get(scope, "10", fetch = fetch)
            assertEquals(3, requests)
            now += 60_001
            assertTrue(runCatching { cache.get(scope, "10") { throw IOException("Sin conexión") } }.isFailure)
            assertTrue(runCatching { cache.get(scope, "11", refresh = true, fetch = fetch) }.isFailure)
        } finally {
            listOf(".json", ".json.bak", ".json.new").forEach { File(app.noBackupFilesDir, "ops-menu-v1-$key$it").delete() }
        }
    }
}
