package com.example.wfappops.auth.data

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsContextContractTest {
    private val api = AuthApi(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun preservesOpsIdentityLocationsAndPermissions() {
        val context = api.parseBusinessContext(response("ops.example.action").toString(), "10")

        assertEquals("10", context.businessId)
        assertEquals("20", context.membershipId)
        assertEquals("30", context.employeeId)
        assertEquals("2", context.accessVersion)
        assertEquals("all", context.locationScope)
        assertEquals("40", context.locations.single().id)
        assertEquals("LOC-01", context.locations.single().code)
        assertEquals(setOf("ops.example.action"), context.permissions)
    }

    @Test
    fun acceptsContextWithNoAssignedActions() {
        val context = api.parseBusinessContext(response().toString(), "10")

        assertTrue(context.permissions.isEmpty())
        assertEquals("10", context.businessId)
    }

    @Test
    fun rejectsContextFromAnotherBusiness() {
        assertThrows(AuthApiException::class.java) {
            api.parseBusinessContext(response().toString(), "99")
        }
    }

    private fun response(vararg permissions: String) = JSONObject().put(
        "data",
        JSONObject()
            .put("business", JSONObject().put("id", "10").put("name", "Café de ejemplo"))
            .put("membership", JSONObject().put("id", "20"))
            .put("employee", JSONObject().put("id", "30").put("display_name", "Ana Ejemplo"))
            .put("location_scope", "all")
            .put("locations", JSONArray().put(
                JSONObject().put("id", "40").put("code", "LOC-01").put("name", "Local Centro")
            ))
            .put("permissions", JSONArray(permissions.toList()))
            .put("access_version", "2")
    )
}
