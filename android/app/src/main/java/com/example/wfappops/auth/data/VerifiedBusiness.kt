package com.example.wfappops.auth.data

import com.example.wfappops.auth.model.TeamBusinessContext
import java.time.Duration
import java.time.Instant

internal data class VerifiedBusiness(val context: TeamBusinessContext, val verifiedAt: Instant) {
    fun isFresh(now: Instant) = isWithin(now, Duration.ofMinutes(15))

    fun canRestoreOffline(now: Instant) = isWithin(now, Duration.ofHours(8))

    private fun isWithin(now: Instant, limit: Duration): Boolean {
        val age = Duration.between(verifiedAt, now)
        return !age.isNegative && age < limit
    }
}
