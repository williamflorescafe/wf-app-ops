package com.example.wfappops.auth.data

import com.example.wfappops.core.model.ApiException
import android.content.Context
import android.net.ConnectivityManager
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
    private val menuCache = com.example.wfappops.carta.data.OpsMenuCache(app)

    val isStaging = api.isStaging
    val appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()

    suspend fun restoreSession() = withContext(Dispatchers.IO) {
        LegacyDemoCleanup(app).run()
        val active = store.read()
        if (active == null) {
            menuCache.clear()
            scheduleCache.invalidate()
            return@withContext null
        }
        // La caché de contexto nunca sustituye la validación del token al arrancar.
        try {
            RestoredAccount(active, api.profileEmail(active.token))
        } catch (error: ApiException) {
            if (error.status == 401) {
                store.clear()
                menuCache.clear()
                scheduleCache.invalidate()
            }
            throw error
        }
    }

    fun hasNoNetwork(): Boolean =
        app.getSystemService(ConnectivityManager::class.java)?.let { it.activeNetwork == null } == true

    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val active = StoredSession(api.login(email, password, store.deviceId()))
        menuCache.clear()
        scheduleCache.invalidate()
        active to save(active)
    }

    suspend fun validateBusiness(active: StoredSession, switchingBusiness: Boolean = false) = withContext(Dispatchers.IO) {
        val context = api.businessContext(active.token, checkNotNull(active.businessId))
        if (switchingBusiness || active.verifiedBusiness?.context?.accessVersion != context.accessVersion) {
            menuCache.clear()
            scheduleCache.invalidate()
        }
        val verified = active.copy(verifiedBusiness = VerifiedBusiness(context, Instant.now()))
        verified to save(verified)
    }

    suspend fun businesses(active: StoredSession) = withContext(Dispatchers.IO) { api.businesses(active.token) }

    suspend fun forgetBusiness(active: StoredSession) = withContext(Dispatchers.IO) {
        menuCache.clear()
        if (!save(active)) store.clear()
        scheduleCache.invalidate()
    }

    suspend fun clearSession() = withContext(Dispatchers.IO + NonCancellable) {
        store.clear()
        menuCache.clear()
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

    suspend fun menu(active: StoredSession, businessId: String, locationId: String, refresh: Boolean = false) =
        withContext(Dispatchers.IO) {
            var authorized = active
            val cached = checkNotNull(active.verifiedBusiness).context
            check(active.businessId == businessId && cached.businessId == businessId)
            // Un permiso agregado en Teams no debe quedar negado por la instantánea del ingreso.
            if (refresh || "pos.menus.view" !in cached.permissions) {
                val updated = api.businessContext(active.token, businessId)
                check(updated.businessId == cached.businessId && updated.employeeId == cached.employeeId)
                authorized = active.copy(verifiedBusiness = VerifiedBusiness(updated, Instant.now()))
                check(store.saveIfCurrent(active, authorized))
                if (updated != cached) menuCache.clear()
            }
            val context = checkNotNull(authorized.verifiedBusiness).context
            check(active.businessId == businessId && context.businessId == businessId)
            if ("pos.menus.view" !in context.permissions) {
                if (isStaging) android.util.Log.w("OpsMenuAccess", "Context denies pos.menus.view after revalidation")
                throw ApiException(403, code = "client.menu_permission_missing")
            }
            if (context.locations.none { it.id == locationId }) throw ApiException(403, code = "client.menu_location_denied")
            val scope = org.json.JSONArray(listOf(businessId, context.employeeId, context.membershipId,
                context.accessVersion, locationId)).toString()
            menuCache.get(scope, locationId, refresh, stillAuthorized = {
                val current = store.read()
                current?.token == authorized.token && current.verifiedBusiness?.context == context
            }) { api.menu(authorized.token, businessId, locationId) }
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
