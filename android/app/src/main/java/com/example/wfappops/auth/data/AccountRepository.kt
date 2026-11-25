package com.example.wfappops.auth.data

import android.content.Context
import android.net.ConnectivityManager
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.schedule.ScheduleCache
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class AccountRepository(context: Context) {
    private val app = context.applicationContext
    private val store = SessionStore(app)
    private val api = AuthApi(app)
    private val scheduleCache = ScheduleCache(app)
    private val tablesCache = com.example.wfappops.tables.TablesCache(app)

    val isStaging = api.isStaging
    val appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()

    suspend fun restoreSession() = withContext(Dispatchers.IO) {
        LegacyDemoCleanup(app).run()
        val active = store.read()
        if (active == null) {
            scheduleCache.invalidate()
            return@withContext null
        }
        // La caché de contexto nunca sustituye la validación del token al arrancar.
        try {
            RestoredAccount(active, api.profileEmail(active.token))
        } catch (error: AuthApiException) {
            if (error.status == 401) {
                store.clear()
                scheduleCache.invalidate()
            }
            throw error
        }
    }

    fun hasNoNetwork(): Boolean =
        app.getSystemService(ConnectivityManager::class.java)?.let { it.activeNetwork == null } == true

    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val active = StoredSession(api.login(email, password, store.deviceId()))
        scheduleCache.invalidate()
        active to save(active)
    }

    suspend fun validateBusiness(active: StoredSession, switchingBusiness: Boolean = false) = withContext(Dispatchers.IO) {
        val context = api.businessContext(active.token, checkNotNull(active.businessId))
        if (switchingBusiness || active.verifiedBusiness?.context?.accessVersion != context.accessVersion) {
            scheduleCache.invalidate()
        }
        val verified = active.copy(verifiedBusiness = VerifiedBusiness(context, Instant.now()))
        verified to save(verified)
    }

    suspend fun businesses(active: StoredSession) = withContext(Dispatchers.IO) { api.businesses(active.token) }

    suspend fun businessContext(active: StoredSession, businessId: String) =
        withContext(Dispatchers.IO) { api.businessContext(active.token, businessId) }

    suspend fun forgetBusiness(active: StoredSession) = withContext(Dispatchers.IO) {
        if (!save(active)) store.clear()
        scheduleCache.invalidate()
    }

    suspend fun clearSession() = withContext(Dispatchers.IO + NonCancellable) {
        store.clear()
        scheduleCache.invalidate()
    }

    suspend fun logout(active: StoredSession) = withContext(Dispatchers.IO) {
        try {
            api.logout(active.token)
        } catch (_: IOException) {
            // El cierre local también funciona sin conexión.
        }
    }

    suspend fun profileEmail(active: StoredSession) = withContext(Dispatchers.IO) { api.profileEmail(active.token) }

    suspend fun tables(active: StoredSession, businessId: String, locationId: String, refresh: Boolean = false) =
        withContext(Dispatchers.IO) {
            val verified = checkNotNull(active.verifiedBusiness).context
            check(active.businessId == businessId && verified.businessId == businessId)
            check("pos.tables.view" in verified.permissions && verified.locations.any { it.id == locationId })
            val scope = org.json.JSONArray(listOf(businessId, verified.employeeId, verified.membershipId,
                verified.accessVersion, locationId)).toString()
            tablesCache.get(scope, locationId, refresh) { api.tables(active.token, businessId, locationId) }
        }

    private fun save(active: StoredSession): Boolean = try {
        store.save(active)
        true
    } catch (_: IOException) {
        false
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: ProviderException) {
        false
    }
}

internal data class RestoredAccount(val session: StoredSession, val email: String)
