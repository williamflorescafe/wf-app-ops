package com.example.wfappops.auth.data

import android.content.Context
import android.net.ConnectivityManager
import android.util.Patterns
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.schedule.ScheduleCache
import com.example.wfappops.schedule.ScheduleScope
import com.example.wfappops.schedule.ScheduleSnapshot
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal class AccountRepository(context: Context) {
    private val app = context.applicationContext
    private val store = SessionStore(app)
    private val api = AuthApi(app)
    private val scheduleCache = ScheduleCache(app)

    val isStaging = api.isStaging
    val appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()

    suspend fun demoAccount(): DemoAccount? = withContext(Dispatchers.IO) {
        if (!isStaging) return@withContext null
        try {
            app.assets.open("auth/team-context.json").bufferedReader().use { reader ->
                val fixture = JSONObject(reader.readText())
                val credentials = fixture.getJSONObject("credentials")
                val email = credentials.getString("email")
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() ||
                    credentials.getString("password").isBlank()
                ) throw AuthApiException()
                api.parseLoginToken(fixture.getJSONObject("login").toString())
                DemoAccount(email, api.parseBusinessContext(fixture.getJSONObject("context").toString()))
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: JSONException) {
            throw AuthApiException()
        }
    }

    suspend fun restoreSession() = withContext(Dispatchers.IO) {
        store.read().also { if (it == null) scheduleCache.invalidate() }
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

    suspend fun clearSession() = withContext(Dispatchers.IO) {
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

    suspend fun schedule(active: StoredSession, context: TeamBusinessContext, month: YearMonth) =
        withContext(Dispatchers.IO) { api.schedule(active.token, context, month) }

    fun scheduleLease() = scheduleCache.lease()

    suspend fun readSchedule(scope: ScheduleScope, month: YearMonth, lease: Long) =
        withContext(Dispatchers.IO) { scheduleCache.read(scope, month, lease) }

    suspend fun saveSchedule(scope: ScheduleScope, month: YearMonth, snapshot: ScheduleSnapshot, lease: Long) =
        withContext(Dispatchers.IO) { scheduleCache.save(scope, month, snapshot, lease) }

    suspend fun invalidateSchedule() = withContext(Dispatchers.IO + NonCancellable) {
        scheduleCache.invalidate()
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

internal data class DemoAccount(val email: String, val context: TeamBusinessContext)
