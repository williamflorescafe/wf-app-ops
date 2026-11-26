package com.example.wfappops.auth.data

import com.example.wfappops.auth.model.TeamBusinessContext
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MenuAccessSnapshotTest {
    private val context = TeamBusinessContext("business", "Negocio", "Colaborador", "employee", "1",
        membershipId = "member", permissions = setOf("pos.tables.view"))
    private val active = StoredSession("test-session", "business", VerifiedBusiness(context, Instant.EPOCH))

    @Test fun verificationTimestampDoesNotInvalidateUnchangedAccess() {
        val newerCheck = active.copy(verifiedBusiness = VerifiedBusiness(context, Instant.EPOCH.plusSeconds(1)))
        assertTrue(sameOpsAccess(active, newerCheck))
    }

    @Test fun logoutTokenReplacementAndBusinessSwitchRejectAnOldRefresh() {
        assertFalse(sameOpsAccess(active, null))
        assertFalse(sameOpsAccess(active, active.copy(token = "another-session")))
        assertFalse(sameOpsAccess(active, active.copy(businessId = "another-business")))
    }

    @Test fun concurrentlyUpdatedPermissionsMembershipOrEmployeeCannotBeOverwritten() {
        listOf(context.copy(accessVersion = "2", permissions = setOf("pos.menus.view")),
            context.copy(membershipId = "another-member"), context.copy(employeeId = "another-employee")).forEach { changed ->
            assertFalse(sameOpsAccess(active, active.copy(verifiedBusiness = VerifiedBusiness(changed, Instant.EPOCH))))
        }
    }
}
