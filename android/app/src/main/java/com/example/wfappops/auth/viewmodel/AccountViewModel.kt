package com.example.wfappops.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.R
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.auth.OpsAccess
import com.example.wfappops.auth.data.AccountRepository
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.auth.data.StoredSession
import com.example.wfappops.auth.model.TeamBusiness
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.model.LoginFieldIssue
import com.example.wfappops.auth.model.validateLoginFields
import com.example.wfappops.tables.LocationTables
import java.time.Duration
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import javax.net.ssl.SSLException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AccountUiState(
    val isStaging: Boolean,
    val appVersion: String,
    val restoring: Boolean = true,
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val notice: AccountNotice? = null,
    val businesses: List<TeamBusiness>? = null,
    val businessContext: TeamBusinessContext? = null,
    val profileEmail: Result<String>? = null,
    val selectedLocationId: String? = null,
    val tables: Result<LocationTables>? = null,
    val tablesLoading: Boolean = false,
    val tablesFetchedAt: Instant? = null
)

internal class AccountViewModel(
    private val repository: AccountRepository,
    private val text: (Int) -> String
) : ViewModel() {
    private val state = MutableStateFlow(AccountUiState(repository.isStaging, repository.appVersion))
    val uiState = state.asStateFlow()

    private var session: StoredSession? = null
    private var profileJob: Job? = null
    private var tablesJob: Job? = null

    init {
        val startupStartedAt = android.os.SystemClock.elapsedRealtime()
        runWork {
            try {
                val restored = repository.restoreSession()
                session = restored?.session
                state.update { it.copy(signedIn = session != null, profileEmail = restored?.let { Result.success(it.email) }) }
                loadBusinessAccess()
            } finally {
                state.update { it.copy(restoring = false) }
                if (repository.isStaging) android.util.Log.d("OpsStartupTiming",
                    "Session restoration completed in ${android.os.SystemClock.elapsedRealtime() - startupStartedAt} ms; businessReady=${state.value.businessContext != null}")
            }
        }
    }

    fun updateEmail(value: String) = state.update { it.copy(email = value, emailError = null, notice = null) }

    fun updatePassword(value: String) = state.update {
        it.copy(password = value, passwordError = null, notice = null)
    }

    fun login() {
        val current = state.value
        if (current.loading || current.signedIn) return
        val address = current.email.trim()
        val secret = current.password
        val validation = validateLoginFields(address, secret)
        val emailError = when (validation.email) {
            LoginFieldIssue.REQUIRED -> text(R.string.auth_required)
            LoginFieldIssue.INVALID_EMAIL -> text(R.string.auth_invalid_email)
            null -> null
        }
        val passwordError = if (validation.password != null) text(R.string.auth_required) else null
        state.update { it.copy(emailError = emailError, passwordError = passwordError, notice = null) }
        if (emailError != null || passwordError != null) return

        runWork {
            val (active, saved) = repository.login(address, secret)
            session = active
            state.update { it.copy(signedIn = true, password = "") }
            if (!saved) notify(R.string.auth_session_not_saved, isError = false)
            loadBusinessAccess()
        }
    }

    fun retry() = runWork { loadBusinessAccess() }

    fun selectBusiness(business: TeamBusiness) {
        val active = session ?: return
        if (!business.available) return
        runWork {
            val selected = active.copy(businessId = business.id, verifiedBusiness = null)
            val (verified, saved) = repository.validateBusiness(selected, switchingBusiness = true)
            activateBusiness(verified, saved)
        }
    }

    fun expireSession() {
        viewModelScope.launch { clearSession(); state.update { it.copy(loading = false) } }
    }

    fun logout() {
        val active = session ?: return
        runWork {
            try {
                repository.logout(active)
            } finally {
                clearSession()
            }
        }
    }

    /** Caché en memoria de cinco minutos, ligada a la sesión y al contexto de acceso. */
    fun loadTables(locationId: String, refresh: Boolean = false) {
        val active = session ?: return
        val context = state.value.businessContext ?: return
        if ("pos.tables.view" !in context.permissions ||
            context.locations.none { it.id == locationId } || !Regex("[1-9][0-9]*").matches(locationId)
        ) return
        val current = state.value
        if (current.selectedLocationId == locationId) {
            if (tablesJob?.isActive == true) return
            val age = current.tablesFetchedAt?.let { Duration.between(it, Instant.now()) }
            if (!refresh && current.tables?.isSuccess == true && age != null &&
                !age.isNegative && age < Duration.ofMinutes(5)
            ) return
        }
        tablesJob?.cancel()
        state.update {
            it.copy(
                selectedLocationId = locationId,
                tables = current.tables.takeIf { refresh && current.selectedLocationId == locationId && it?.isSuccess == true },
                tablesLoading = true,
                tablesFetchedAt = null
            )
        }
        fun stillCurrent() = session === active && state.value.businessContext == context &&
            state.value.selectedLocationId == locationId
        tablesJob = viewModelScope.launch {
            val result = try {
                Result.success(repository.tables(active, context.businessId, locationId, refresh))
            } catch (error: IOException) {
                if (!stillCurrent()) return@launch
                if (error is AuthApiException && error.status == 401) {
                    clearSession()
                    notify(R.string.auth_session_expired, isError = false)
                    return@launch
                }
                Result.failure(error)
            }
            if (stillCurrent()) state.update {
                it.copy(tables = result, tablesLoading = false, tablesFetchedAt = if (result.isSuccess) Instant.now() else null)
            }
        }
    }

    fun loadProfile() {
        if (profileJob?.isActive == true || state.value.profileEmail?.isSuccess == true) return
        val active = session ?: return
        state.update { it.copy(profileEmail = null) }
        profileJob = viewModelScope.launch {
            val result = try {
                Result.success(repository.profileEmail(active))
            } catch (error: IOException) {
                if (error is AuthApiException && error.status == 401 && session === active) {
                    runWork {
                        clearSession()
                        notify(R.string.auth_session_expired, isError = false)
                    }
                }
                Result.failure(error)
            }
            if (session === active) state.update { it.copy(profileEmail = result) }
        }
    }

    private suspend fun loadBusinessAccess() {
        val active = session ?: return
        if (active.businessId != null) {
            val cached = active.verifiedBusiness?.takeIf { it.context.businessId == active.businessId }
            val now = Instant.now()
            if (cached != null && (cached.isFresh(now) ||
                    cached.canRestoreOffline(now) && repository.hasNoNetwork())
            ) {
                confirmBusiness(cached.context)
                return
            }
            try {
                val (verified, saved) = repository.validateBusiness(active)
                activateBusiness(verified, saved)
                return
            } catch (error: AuthApiException) {
                if (error.status in 500..599 && cached?.canRestoreOffline(Instant.now()) == true) {
                    confirmBusiness(cached.context)
                    return
                }
                if (error.status != 403) throw error
                forgetBusiness(active)
                notify(R.string.auth_business_access_changed, isError = false)
            } catch (error: IOException) {
                if (error is SSLException || cached?.canRestoreOffline(Instant.now()) != true) throw error
                confirmBusiness(cached.context)
                return
            }
        }
        val businesses = repository.businesses(active)
        state.update { it.copy(businesses = businesses) }
    }

    private suspend fun forgetBusiness(active: StoredSession) {
        val unselected = active.copy(businessId = null, verifiedBusiness = null)
        session = unselected
        repository.forgetBusiness(unselected)
    }

    private fun activateBusiness(active: StoredSession, saved: Boolean) {
        session = active
        confirmBusiness(checkNotNull(active.verifiedBusiness).context)
        state.update { it.copy(businesses = null) }
        if (!saved) notify(R.string.auth_session_not_saved, isError = false)
    }

    private fun runWork(block: suspend () -> Unit) {
        if (state.value.loading) return
        state.update { it.copy(loading = true, notice = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: AuthApiException) {
                if (state.value.signedIn && error.status == 401) {
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
                state.update { it.copy(loading = false) }
            }
        }
    }

    private fun showError(error: AuthApiException) {
        if (!state.value.signedIn && error.status == 422) {
            val emailError = error.fieldErrors["email"]
            val passwordError = error.fieldErrors["password"]
            state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            if (emailError != null || passwordError != null) return
        }
        val message = when {
            error.status == 401 -> text(R.string.auth_session_expired)
            error.status in 400..599 && error.serverMessage != null -> error.serverMessage
            error.status == 403 -> text(R.string.auth_forbidden)
            error.status == 422 -> text(R.string.auth_validation_error)
            error.status == 429 -> text(R.string.auth_rate_limit)
            error.status in 500..599 -> text(R.string.auth_server_error)
            else -> text(R.string.auth_request_error)
        }
        state.update { it.copy(notice = AccountNotice(message, isError = true)) }
    }

    private suspend fun clearSession() {
        session = null
        profileJob?.cancel()
        tablesJob?.cancel()
        ActiveOpsAccess.current = null
        state.update {
            AccountUiState(it.isStaging, it.appVersion, restoring = false, loading = it.loading)
        }
        repository.clearSession()
    }

    private fun confirmBusiness(context: TeamBusinessContext?) {
        val previous = state.value.businessContext
        val identityChanged = previous?.businessId != context?.businessId || previous?.employeeId != context?.employeeId
        if (identityChanged) profileJob?.cancel()
        val contextChanged = previous != context
        if (contextChanged) tablesJob?.cancel()
        ActiveOpsAccess.current = context?.let { OpsAccess(it.businessId) }
        state.update {
            it.copy(
                businessContext = context, profileEmail = it.profileEmail.takeUnless { identityChanged },
                selectedLocationId = it.selectedLocationId.takeUnless { contextChanged },
                tables = it.tables.takeUnless { contextChanged },
                tablesFetchedAt = it.tablesFetchedAt.takeUnless { contextChanged },
                tablesLoading = it.tablesLoading && !contextChanged
            )
        }
    }

    override fun onCleared() {
        val current = state.value
        val context = current.businessContext ?: return
        if (ActiveOpsAccess.current == OpsAccess(context.businessId)) ActiveOpsAccess.current = null
    }

    private fun notify(message: Int, isError: Boolean = true) {
        state.update { it.copy(notice = AccountNotice(text(message), isError)) }
    }
}

internal data class AccountNotice(val text: String, val isError: Boolean)
