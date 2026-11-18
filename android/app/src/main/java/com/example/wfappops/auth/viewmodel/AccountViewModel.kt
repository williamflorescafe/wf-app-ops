package com.example.wfappops.auth.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.R
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.auth.OpsAccess
import com.example.wfappops.auth.data.AccountRepository
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.auth.data.DemoAccount
import com.example.wfappops.auth.data.StoredSession
import com.example.wfappops.auth.model.TeamBusiness
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.schedule.ScheduleIssue
import com.example.wfappops.schedule.ScheduleProtocolException
import com.example.wfappops.schedule.ScheduleScope
import com.example.wfappops.schedule.ScheduleSnapshot
import com.example.wfappops.schedule.ScheduleState
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import java.time.YearMonth
import javax.net.ssl.SSLException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AccountUiState(
    val isStaging: Boolean,
    val appVersion: String,
    val restoring: Boolean = true,
    val signedIn: Boolean = false,
    val isSimulated: Boolean = false,
    val demoLogin: Boolean = false,
    val loading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val notice: AccountNotice? = null,
    val businesses: List<TeamBusiness>? = null,
    val businessContext: TeamBusinessContext? = null,
    val profileEmail: Result<String>? = null
)

internal class AccountViewModel(
    private val repository: AccountRepository,
    private val text: (Int) -> String
) : ViewModel() {
    private val state = MutableStateFlow(AccountUiState(repository.isStaging, repository.appVersion))
    val uiState = state.asStateFlow()

    private var session: StoredSession? = null
    private var demoAccount: DemoAccount? = null
    private var profileJob: Job? = null

    init {
        runWork {
            try {
                demoAccount = repository.demoAccount()
                if (demoAccount != null) {
                    state.update { it.copy(demoLogin = true) }
                } else {
                    session = repository.restoreSession()
                    state.update { it.copy(signedIn = session != null) }
                    loadBusinessAccess()
                }
            } finally {
                state.update { it.copy(restoring = false) }
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
        demoAccount?.let { demo ->
            ActiveOpsAccess.current = OpsAccess(demo.context.businessId, simulated = true)
            state.update {
                it.copy(email = "", password = "", notice = null, signedIn = true,
                    isSimulated = true, businessContext = demo.context)
            }
            return
        }

        val address = current.email.trim()
        val secret = current.password
        val emailError = when {
            address.isEmpty() -> text(R.string.auth_required)
            !Patterns.EMAIL_ADDRESS.matcher(address).matches() -> text(R.string.auth_invalid_email)
            else -> null
        }
        val passwordError = if (secret.isEmpty()) text(R.string.auth_required) else null
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

    fun logout() {
        val active = session ?: return
        runWork {
            clearSession()
            repository.logout(active)
        }
    }

    fun loadProfile() {
        if (profileJob?.isActive == true || state.value.profileEmail?.isSuccess == true) return
        if (state.value.isSimulated) {
            state.update { it.copy(profileEmail = demoAccount?.let { demo -> Result.success(demo.email) }) }
            return
        }
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

    fun schedule(month: YearMonth): Flow<ScheduleState> = flow {
        if (state.value.isSimulated) {
            emit(ScheduleState(issue = ScheduleIssue.DEMO))
            return@flow
        }
        val active = session ?: return@flow
        var context = state.value.businessContext ?: return@flow
        fun stillCurrent() = session === active && state.value.businessContext == context
        val environment = if (state.value.isStaging) "stg" else "prod"
        val scope = ScheduleScope("$environment:${context.businessId}", context.employeeId)
        var lease = repository.scheduleLease()
        var cached = repository.readSchedule(scope, month, lease)
        if (!stillCurrent()) return@flow
        emit(ScheduleState(cached, refreshing = true))

        try {
            val shifts = try {
                repository.schedule(active, context, month)
            } catch (error: AuthApiException) {
                if (error.code != "iam.access_changed" || !stillCurrent()) throw error
                cached = null
                repository.invalidateSchedule()
                if (!stillCurrent()) return@flow
                emit(ScheduleState(refreshing = true))
                context = refreshScheduleContext(active, context) ?: return@flow
                lease = repository.scheduleLease()
                repository.schedule(active, context, month)
            }
            if (!stillCurrent()) return@flow
            val snapshot = ScheduleSnapshot(shifts, Instant.now())
            val saved = repository.saveSchedule(scope, month, snapshot, lease)
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
                    repository.invalidateSchedule()
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
            repository.businessContext(active, previous.businessId)
        } catch (error: AuthApiException) {
            if (error.status != 403) throw error
            null
        }
        if (session !== active || state.value.businessContext != previous) return null
        if (refreshed == null || refreshed.employeeId != previous.employeeId) {
            runWork {
                if (session === active && state.value.businessContext == previous) {
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
        if (!state.value.signedIn && error.code == "validation_failed") {
            val emailError = error.fieldErrors["email"]
            val passwordError = error.fieldErrors["password"]
            state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            if (emailError != null || passwordError != null) return
        }
        val message = when {
            error.code == "iam.two_factor_unsupported" -> text(R.string.auth_two_factor_unsupported)
            error.status == 429 -> text(R.string.auth_rate_limit)
            error.status in 400..499 && error.serverMessage != null -> error.serverMessage
            else -> text(R.string.auth_request_error)
        }
        state.update { it.copy(notice = AccountNotice(message, isError = true)) }
    }

    private suspend fun clearSession() {
        session = null
        profileJob?.cancel()
        ActiveOpsAccess.current = null
        state.update { it.copy(signedIn = false, businessContext = null, businesses = null, profileEmail = null) }
        repository.clearSession()
    }

    private fun confirmBusiness(context: TeamBusinessContext?) {
        val previous = state.value.businessContext
        val identityChanged = previous?.businessId != context?.businessId || previous?.employeeId != context?.employeeId
        if (identityChanged) profileJob?.cancel()
        ActiveOpsAccess.current = context?.let { OpsAccess(it.businessId, state.value.isSimulated) }
        state.update { it.copy(businessContext = context, profileEmail = it.profileEmail.takeUnless { identityChanged }) }
    }

    override fun onCleared() {
        val current = state.value
        val context = current.businessContext ?: return
        if (ActiveOpsAccess.current == OpsAccess(context.businessId, current.isSimulated)) ActiveOpsAccess.current = null
    }

    private fun notify(message: Int, isError: Boolean = true) {
        state.update { it.copy(notice = AccountNotice(text(message), isError)) }
    }
}

internal data class AccountNotice(val text: String, val isError: Boolean)
