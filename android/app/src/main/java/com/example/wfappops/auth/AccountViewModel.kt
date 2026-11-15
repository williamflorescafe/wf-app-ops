package com.example.wfappops.auth

import android.app.Application
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.R
import com.example.wfappops.schedule.ScheduleCache
import com.example.wfappops.schedule.ScheduleIssue
import com.example.wfappops.schedule.ScheduleProtocolException
import com.example.wfappops.schedule.ScheduleScope
import com.example.wfappops.schedule.ScheduleSnapshot
import com.example.wfappops.schedule.ScheduleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.FileNotFoundException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import java.time.YearMonth

internal class AccountViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val api = AuthApi(app)
    private val scheduleCache = ScheduleCache(app)
    private var session by mutableStateOf<StoredSession?>(null)
    private var demoAccount by mutableStateOf<DemoAccount?>(null)

    val isStaging = api.isStaging
    val appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    val signedIn get() = session != null || isSimulated
    val demoLogin get() = demoAccount != null
    var restoring by mutableStateOf(true)
        private set
    var isSimulated by mutableStateOf(false)
        private set
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var emailError by mutableStateOf<String?>(null)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<AccountNotice?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var businesses by mutableStateOf<List<TeamBusiness>?>(null)
        private set
    var businessContext by mutableStateOf<TeamBusinessContext?>(null)
        private set

    init {
        runWork {
            try {
                demoAccount = if (isStaging) withContext(Dispatchers.IO) { loadDemoAccount() } else null
                if (demoLogin) return@runWork
                session = withContext(Dispatchers.IO) {
                    store.read().also { if (it == null) scheduleCache.invalidate() }
                }
                loadBusinessAccess()
            } finally {
                restoring = false
            }
        }
    }

    private fun loadDemoAccount(): DemoAccount? = try {
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

    fun updateEmail(value: String) {
        email = value
        emailError = null
        notice = null
    }

    fun updatePassword(value: String) {
        password = value
        passwordError = null
        notice = null
    }

    fun login() {
        if (loading || signedIn) return
        demoAccount?.let { demo ->
            email = ""
            password = ""
            notice = null
            isSimulated = true
            confirmBusiness(demo.context)
            return
        }
        val address = email.trim()
        val secret = password
        emailError = when {
            address.isEmpty() -> app.getString(R.string.auth_required)
            !Patterns.EMAIL_ADDRESS.matcher(address).matches() -> app.getString(R.string.auth_invalid_email)
            else -> null
        }
        passwordError = if (secret.isEmpty()) app.getString(R.string.auth_required) else null
        notice = null
        if (emailError != null || passwordError != null) return

        runWork {
            val (active, saved) = withContext(Dispatchers.IO) {
                val active = StoredSession(api.login(address, secret, store.deviceId()))
                scheduleCache.invalidate()
                active to save(active)
            }
            session = active
            password = ""
            if (!saved) notify(R.string.auth_session_not_saved, isError = false)
            loadBusinessAccess()
        }
    }

    fun retry() = runWork { loadBusinessAccess() }

    fun selectBusiness(business: TeamBusiness) {
        val active = session ?: return
        if (!business.available) return
        runWork {
            val selected = active.copy(businessId = business.id)
            val (confirmed, saved) = withContext(Dispatchers.IO) {
                val confirmed = api.businessContext(active.token, business.id)
                scheduleCache.invalidate()
                confirmed to save(selected)
            }
            session = selected
            confirmBusiness(confirmed)
            businesses = null
            if (!saved) notify(R.string.auth_session_not_saved, isError = false)
        }
    }

    fun logout() {
        val active = session ?: return
        runWork {
            clearSession()
            withContext(Dispatchers.IO) {
                try {
                    api.logout(active.token)
                } catch (_: IOException) {
                    // El cierre local también funciona sin conexión.
                }
            }
        }
    }

    suspend fun profileEmail(): Result<String?> {
        if (isSimulated) return Result.success(demoAccount?.email)
        val active = session ?: return Result.success(null)
        return try {
            val email = withContext(Dispatchers.IO) { api.profileEmail(active.token) }
            Result.success(email.takeIf { session === active })
        } catch (error: IOException) {
            if (error is AuthApiException && error.status == 401 && session === active) {
                runWork {
                    clearSession()
                    notify(R.string.auth_session_expired, isError = false)
                }
            }
            Result.failure(error)
        }
    }

    fun schedule(month: YearMonth): Flow<ScheduleState> = flow {
        if (isSimulated) {
            emit(ScheduleState(issue = ScheduleIssue.DEMO))
            return@flow
        }
        val active = session ?: return@flow
        var context = businessContext ?: return@flow
        fun stillCurrent() = session === active && businessContext == context
        val environment = if (isStaging) "stg" else "prod"
        val scope = ScheduleScope("$environment:${context.businessId}", context.employeeId)
        var lease = scheduleCache.lease()
        var cached = withContext(Dispatchers.IO) { scheduleCache.read(scope, month, lease) }
        if (!stillCurrent()) return@flow
        emit(ScheduleState(cached, refreshing = true))

        try {
            val shifts = try {
                withContext(Dispatchers.IO) { api.schedule(active.token, context, month) }
            } catch (error: AuthApiException) {
                if (error.code != "iam.access_changed" || !stillCurrent()) throw error
                cached = null
                withContext(Dispatchers.IO + NonCancellable) { scheduleCache.invalidate() }
                if (!stillCurrent()) return@flow
                emit(ScheduleState(refreshing = true))
                context = refreshScheduleContext(active, context) ?: return@flow
                lease = scheduleCache.lease()
                withContext(Dispatchers.IO) { api.schedule(active.token, context, month) }
            }
            if (!stillCurrent()) return@flow
            val snapshot = ScheduleSnapshot(shifts, Instant.now())
            val saved = withContext(Dispatchers.IO) { scheduleCache.save(scope, month, snapshot, lease) }
            if (stillCurrent()) emit(ScheduleState(snapshot, issue = if (saved) null else ScheduleIssue.CACHE))
        } catch (error: AuthApiException) {
            if (!stillCurrent()) return@flow
            when {
                error.status == 401 -> runWork {
                    if (session === active) {
                        clearSession()
                        notify(R.string.auth_session_expired, isError = false)
                    }
                }
                error.status == 403 || error.code == "iam.access_changed" -> {
                    withContext(Dispatchers.IO + NonCancellable) { scheduleCache.invalidate() }
                    emit(ScheduleState(issue = ScheduleIssue.DENIED))
                }
                else -> emit(ScheduleState(cached, issue = ScheduleIssue.UNAVAILABLE))
            }
        } catch (_: ScheduleProtocolException) {
            if (stillCurrent()) emit(ScheduleState(cached, issue = ScheduleIssue.UNAVAILABLE))
        } catch (_: IOException) {
            if (stillCurrent()) emit(ScheduleState(cached, issue = ScheduleIssue.CONNECTION))
        }
    }

    private suspend fun refreshScheduleContext(active: StoredSession, previous: TeamBusinessContext): TeamBusinessContext? {
        val refreshed = try {
            withContext(Dispatchers.IO) { api.businessContext(active.token, previous.businessId) }
        } catch (error: AuthApiException) {
            if (error.status != 403) throw error
            null
        }
        if (session !== active || businessContext != previous) return null
        if (refreshed == null || refreshed.employeeId != previous.employeeId) {
            runWork {
                if (session === active && businessContext == previous) {
                    confirmBusiness(null)
                    forgetBusiness(active)
                    notify(R.string.auth_business_access_changed, isError = false)
                    loadBusinessAccess()
                }
            }
            return null
        }
        confirmBusiness(refreshed)
        return refreshed
    }

    private suspend fun loadBusinessAccess() {
        val active = session ?: return
        val selectedId = active.businessId
        if (selectedId != null) {
            try {
                confirmBusiness(withContext(Dispatchers.IO) { api.businessContext(active.token, selectedId) })
                return
            } catch (error: AuthApiException) {
                if (error.status != 403) throw error
                forgetBusiness(active)
                notify(R.string.auth_business_access_changed, isError = false)
            }
        }
        businesses = withContext(Dispatchers.IO) { api.businesses(active.token) }
    }

    private suspend fun forgetBusiness(active: StoredSession) {
        val unselected = active.copy(businessId = null)
        session = unselected
        withContext(Dispatchers.IO) {
            if (!save(unselected)) store.clear()
            scheduleCache.invalidate()
        }
    }

    private fun runWork(block: suspend () -> Unit) {
        if (loading) return
        loading = true
        notice = null
        viewModelScope.launch {
            try {
                block()
            } catch (error: AuthApiException) {
                if (signedIn && error.status == 401) {
                    clearSession()
                    notify(R.string.auth_session_expired, isError = false)
                } else {
                    showError(error)
                }
            } catch (_: IOException) {
                notify(R.string.auth_connection_error)
            } catch (_: GeneralSecurityException) {
                notify(R.string.auth_device_storage_error)
            } catch (_: ProviderException) {
                notify(R.string.auth_device_storage_error)
            } finally {
                loading = false
            }
        }
    }

    private fun showError(error: AuthApiException) {
        if (!signedIn && error.code == "validation_failed") {
            emailError = error.fieldErrors["email"]
            passwordError = error.fieldErrors["password"]
            if (emailError != null || passwordError != null) return
        }
        val text = when {
            error.code == "iam.two_factor_unsupported" -> app.getString(R.string.auth_two_factor_unsupported)
            error.status == 429 -> app.getString(R.string.auth_rate_limit)
            error.status in 400..499 && error.serverMessage != null -> error.serverMessage
            else -> app.getString(R.string.auth_request_error)
        }
        notice = AccountNotice(text, isError = true)
    }

    private suspend fun clearSession() {
        session = null
        confirmBusiness(null)
        businesses = null
        withContext(Dispatchers.IO) {
            store.clear()
            scheduleCache.invalidate()
        }
    }

    private fun confirmBusiness(context: TeamBusinessContext?) {
        ActiveOpsAccess.current = context?.let { OpsAccess(it.businessId, isSimulated) }
        businessContext = context
    }

    override fun onCleared() {
        val context = businessContext ?: return
        if (ActiveOpsAccess.current == OpsAccess(context.businessId, isSimulated)) ActiveOpsAccess.current = null
    }

    private fun notify(message: Int, isError: Boolean = true) {
        notice = AccountNotice(app.getString(message), isError)
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

internal data class AccountNotice(val text: String, val isError: Boolean)

private data class DemoAccount(val email: String, val context: TeamBusinessContext)

internal data class OpsAccess(val businessId: String, val simulated: Boolean)

/** Las actividades de comandas y carrito usan el acceso validado en este proceso. */
internal object ActiveOpsAccess {
    @Volatile var current: OpsAccess? = null
}
