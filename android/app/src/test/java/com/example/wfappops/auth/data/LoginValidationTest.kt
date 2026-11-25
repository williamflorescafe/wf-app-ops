package com.example.wfappops.auth.data

import com.example.wfappops.auth.model.LoginFieldIssue
import com.example.wfappops.auth.model.validateLoginFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginValidationTest {
    @Test
    fun emptyCredentialsCannotContinue() {
        val result = validateLoginFields("", "")
        assertEquals(LoginFieldIssue.REQUIRED, result.email)
        assertEquals(LoginFieldIssue.REQUIRED, result.password)
    }

    @Test
    fun invalidEmailCannotContinue() {
        listOf("correo", "a@", "@example.test", "a b@example.test").forEach {
            assertEquals(LoginFieldIssue.INVALID_EMAIL, validateLoginFields(it, "secret").email)
        }
    }

    @Test
    fun validFormStillRequiresServerAuthentication() {
        val result = validateLoginFields("colaborador@example.test", "secret")
        assertNull(result.email)
        assertNull(result.password)
    }

    @Test
    fun missingPasswordCannotContinueWithValidEmail() {
        assertEquals(LoginFieldIssue.REQUIRED, validateLoginFields("colaborador@example.test", "").password)
    }

    @Test
    fun cleanupTargetsOnlyLegacyDemoStorage() {
        org.junit.Assert.assertTrue(isLegacyDemoFile("ops-mesas-demo-business.json"))
        org.junit.Assert.assertTrue(isLegacyDemoFile("ops-carrito-demo-business.json.bak"))
        org.junit.Assert.assertFalse(isLegacyDemoFile("team-auth-session"))
        org.junit.Assert.assertFalse(isLegacyDemoFile("ops-mesas-10.json"))
    }
}
