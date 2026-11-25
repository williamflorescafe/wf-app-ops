package com.example.wfappops.auth

import android.content.Context
internal data class OpsAccess(val businessId: String)

internal object ActiveOpsAccess {
    @Volatile var current: OpsAccess? = null
}

internal fun Context.hasOpsAccess(storageId: String): Boolean {
    val access = ActiveOpsAccess.current ?: return false
    return storageId == access.businessId
}
