package com.example.wfappops.auth.view

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.wfappops.R
import com.example.wfappops.auth.model.TeamBusiness
import com.example.wfappops.auth.viewmodel.AccountNotice
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.ui.components.InlineMessage
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.StatusChip
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.components.WfTextField
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSpacing

@Composable
internal fun AccountAccessScreen(
    state: AccountUiState,
    onUpdateEmail: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onLogin: () -> Unit,
    onSelectBusiness: (TeamBusiness) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    launching: Boolean
) {
    when {
        state.restoring -> OpsScaffold {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.signedIn -> OpsScaffold { BusinessScreen(state, onSelectBusiness, onRetry, onLogout) }
        else -> LoginScreen(state, onUpdateEmail, onUpdatePassword, onLogin, launching)
    }
}

@Composable
private fun LoginScreen(
    state: AccountUiState,
    onUpdateEmail: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onLogin: () -> Unit,
    launching: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onLogin()
    }
    LoginSheetLayout(launching) {
        Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineMedium)
                if (state.isStaging) StatusChip(stringResource(R.string.auth_staging), StatusTone.WARNING)
            }
            Text(
                stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
        Notice(state.notice)
        WfTextField(
            value = state.email,
            onValueChange = onUpdateEmail,
            label = stringResource(R.string.auth_email),
            labelInside = true,
            leadingIcon = R.drawable.ic_mail,
            errorMessage = state.emailError,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false
            ),
            modifier = Modifier.semantics { contentType = ContentType.EmailAddress + ContentType.Username }
        )
        WfTextField(
            value = state.password,
            onValueChange = onUpdatePassword,
            label = stringResource(R.string.auth_password),
            labelInside = true,
            leadingIcon = R.drawable.ic_lock,
            isPassword = true,
            errorMessage = state.passwordError,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.semantics { contentType = ContentType.Password }
        )
        Spacer(Modifier.height(WfSpacing.xs))
        WfButton(
            text = stringResource(R.string.auth_login),
            onClick = submit,
            isLoading = state.loading,
            fullWidth = true
        )
        Text(
            text = stringResource(R.string.auth_version, state.appVersion),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = WfSpacing.sm)
        )
    }
}

@Composable
private fun BusinessScreen(
    state: AccountUiState,
    onSelectBusiness: (TeamBusiness) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val businesses = state.businesses
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(WfSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.lg)
    ) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            WfButton(
                text = stringResource(R.string.auth_logout),
                onClick = onLogout,
                style = WfButtonStyle.LOGOUT_SOFT,
                leadingIcon = R.drawable.ic_logout,
                enabled = !state.loading
            )
        }
        Text(stringResource(R.string.auth_choose_business), style = MaterialTheme.typography.headlineMedium)
        Notice(state.notice)
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            businesses == null -> WfButton(text = stringResource(R.string.auth_retry), onClick = onRetry)

            businesses.isEmpty() -> Text(stringResource(R.string.auth_no_businesses))

            else -> {
                Text(stringResource(R.string.auth_choose_business_hint), color = scheme.onSurfaceVariant)
                businesses.forEach { business -> BusinessCard(business) { onSelectBusiness(business) } }
            }
        }
    }
}

@Composable
private fun BusinessCard(business: TeamBusiness, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = business.available,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier.padding(WfSpacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_home), contentDescription = null, tint = scheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                Text(business.name, style = MaterialTheme.typography.titleSmall)
                if (!business.available) {
                    val reason = when (business.blockedReason) {
                        "membership_suspended" -> R.string.auth_business_membership_suspended
                        "employee_inactive" -> R.string.auth_business_employee_inactive
                        "business_unavailable" -> R.string.auth_business_unavailable
                        else -> R.string.auth_business_blocked
                    }
                    StatusChip(stringResource(reason), StatusTone.WARNING)
                }
            }
            if (business.available) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
            }
        }
    }
}

@Composable
private fun Notice(notice: AccountNotice?) {
    notice?.let { InlineMessage(it.text, if (it.isError) StatusTone.CRITICAL else StatusTone.INFO) }
}
