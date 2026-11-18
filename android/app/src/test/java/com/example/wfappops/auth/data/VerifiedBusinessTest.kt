package com.example.wfappops.auth.data

import com.example.wfappops.auth.model.TeamBusinessContext
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedBusinessTest {
    private val verifiedAt = Instant.parse("2026-11-17T15:00:00Z")
    private val verified = VerifiedBusiness(
        TeamBusinessContext("business", "Café", "Ana", "employee", "3"),
        verifiedAt
    )

    @Test
    fun requiresRefreshAfterFifteenMinutesAndExpiresOfflineAfterEightHours() {
        val refreshAt = verifiedAt.plus(Duration.ofMinutes(15))
        val expiresAt = verifiedAt.plus(Duration.ofHours(8))

        assertTrue(verified.isFresh(refreshAt.minusMillis(1)))
        assertFalse(verified.isFresh(refreshAt))
        assertTrue(verified.canRestoreOffline(expiresAt.minusMillis(1)))
        assertFalse(verified.canRestoreOffline(expiresAt))
    }

    @Test
    fun rejectsVerificationFromTheFuture() {
        val now = verifiedAt.minusSeconds(1)

        assertFalse(verified.isFresh(now))
        assertFalse(verified.canRestoreOffline(now))
    }
}
