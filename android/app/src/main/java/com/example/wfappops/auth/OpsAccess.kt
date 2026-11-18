package com.example.wfappops.auth

import android.content.Context
import android.content.pm.ApplicationInfo

internal const val DEMO_STORAGE_PREFIX = "demo-"

internal data class OpsAccess(val businessId: String, val simulated: Boolean)

internal object ActiveOpsAccess {
    @Volatile var current: OpsAccess? = null
}

internal fun Context.hasOpsAccess(storageId: String): Boolean {
    val access = ActiveOpsAccess.current ?: return false
    val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val activeStorageId = if (access.simulated) DEMO_STORAGE_PREFIX + access.businessId else access.businessId
    return storageId == activeStorageId && (!access.simulated || debuggable)
}
