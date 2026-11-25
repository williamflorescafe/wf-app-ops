package com.example.wfappops.auth.data

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsTablesContractTest {
    private val api = AuthApi(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun preservesServerOrderAndIdsWithoutDerivingTableNumbers() {
        val layout = api.parseTables(response().toString(), "10")

        assertEquals("10", layout.location.id)
        assertEquals(listOf("Terraza", "Salón"), layout.areas.map { it.name })
        assertEquals(listOf("900", "400"), layout.areas.first().tables.map { it.id })
        assertEquals("T03", layout.areas.first().tables.first().code)
        assertEquals("Terraza 3", layout.areas.first().tables.first().name)
        assertEquals(4, layout.areas.first().tables.first().seats)
    }

    @Test
    fun acceptsLocationWithNoTables() {
        val body = response()
        body.getJSONObject("data").put("areas", JSONArray())
        assertTrue(api.parseTables(body.toString(), "10").areas.isEmpty())
    }

    @Test
    fun rejectsTablesFromAnotherLocation() {
        assertThrows(AuthApiException::class.java) { api.parseTables(response().toString(), "99") }
    }

    @Test
    fun rejectsDuplicateTableIdsAcrossAreas() {
        val body = response()
        val areas = body.getJSONObject("data").getJSONArray("areas")
        areas.getJSONObject(1).getJSONArray("tables").getJSONObject(0).put("id", "900")
        assertThrows(AuthApiException::class.java) { api.parseTables(body.toString(), "10") }
    }

    private fun response() = JSONObject("""
        {"data": {
          "location": {"id":"10","code":"LOC-01","name":"Local Centro"},
          "areas": [
            {"id":"20","name":"Terraza","tables":[
              {"id":"900","code":"T03","name":"Terraza 3","seats":4},
              {"id":"400","code":"T01","name":"Terraza 1","seats":2}
            ]},
            {"id":"30","name":"Salón","tables":[
              {"id":"700","code":"S01","name":"Salón 1","seats":6}
            ]}
          ]
        }}
    """.trimIndent())
}
