package com.example.wfappops.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.ui.components.InlineMessage
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.StatusChip
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.components.WfTextField
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfRadius
import com.example.wfappops.ui.theme.WfSpacing

@Composable
internal fun AccountAccessScreen(account: AccountViewModel) {
    when {
        account.restoring -> OpsScaffold {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        account.signedIn -> OpsScaffold { BusinessScreen(account) }
        else -> LoginScreen(account)
    }
}

@Composable
private fun LoginScreen(account: AccountViewModel) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        account.login()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.surfaceContainerLowest)
            .verticalScroll(rememberScrollState()).imePadding()
    ) {
        Box(Modifier.fillMaxWidth().background(scheme.primary).statusBarsPadding()) {
            Image(
                painter = painterResource(R.drawable.brand_logo_portrait_light),
                contentDescription = stringResource(R.string.auth_brand),
                modifier = Modifier.align(Alignment.Center).padding(vertical = WfSpacing.xl).height(152.dp)
            )
            if (account.isStaging) {
                StatusChip(
                    stringResource(R.string.auth_staging), StatusTone.WARNING,
                    modifier = Modifier.align(Alignment.TopEnd).padding(WfSpacing.lg)
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = WfRadius.xl, topEnd = WfRadius.xl))
                .background(scheme.surfaceContainerLowest)
                .padding(horizontal = WfSpacing.xl, vertical = WfSpacing.xxl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Notice(account.notice)
            WfTextField(
                value = account.email,
                onValueChange = account::updateEmail,
                label = stringResource(R.string.auth_email),
                placeholder = stringResource(R.string.auth_email_placeholder),
                leadingIcon = R.drawable.ic_mail,
                errorMessage = account.emailError,
                enabled = !account.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.semantics { contentType = ContentType.EmailAddress + ContentType.Username }
            )
            WfTextField(
                value = account.password,
                onValueChange = account::updatePassword,
                label = stringResource(R.string.auth_password),
                placeholder = stringResource(R.string.auth_password_placeholder),
                leadingIcon = R.drawable.ic_lock,
                isPassword = true,
                errorMessage = account.passwordError,
                enabled = !account.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.semantics { contentType = ContentType.Password }
            )
            Spacer(Modifier.height(WfSpacing.xs))
            WfButton(
                text = stringResource(if (account.demoLogin) R.string.auth_continue else R.string.auth_login),
                onClick = submit,
                isLoading = account.loading,
                fullWidth = true
            )
            Text(
                text = stringResource(R.string.auth_version, account.appVersion),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = WfSpacing.sm)
            )
        }
    }
}

@Composable
private fun BusinessScreen(account: AccountViewModel) {
    val scheme = MaterialTheme.colorScheme
    val businesses = account.businesses
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(WfSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.lg)
    ) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            WfButton(
                text = stringResource(R.string.auth_logout),
                onClick = account::logout,
                style = WfButtonStyle.LOGOUT_SOFT,
                leadingIcon = R.drawable.ic_logout,
                enabled = !account.loading
            )
        }
        Text(stringResource(R.string.auth_choose_business), style = MaterialTheme.typography.headlineMedium)
        Notice(account.notice)
        when {
            account.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            businesses == null -> WfButton(text = stringResource(R.string.auth_retry), onClick = account::retry)

            businesses.isEmpty() -> Text(stringResource(R.string.auth_no_businesses))

            else -> {
                Text(stringResource(R.string.auth_choose_business_hint), color = scheme.onSurfaceVariant)
                businesses.forEach { business -> BusinessCard(business) { account.selectBusiness(business) } }
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
