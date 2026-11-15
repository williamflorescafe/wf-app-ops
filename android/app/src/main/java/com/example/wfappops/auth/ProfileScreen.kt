package com.example.wfappops.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.ui.components.StatusChip
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSpacing


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreen(account: AccountViewModel, business: TeamBusinessContext, onOpenHistory: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var email by remember { mutableStateOf<Result<String?>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(account, retry) { email = account.profileEmail() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WfSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.xl)
    ) {
        Text(stringResource(R.string.auth_account), style = MaterialTheme.typography.headlineMedium)
        ProfileIdentity(business.employeeName, email?.getOrNull())
        if (email?.isFailure == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.profile_email_unavailable), Modifier.weight(1f),
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { email = null; retry++ }) { Text(stringResource(R.string.auth_retry)) }
            }
        }
        ProfileSection(R.string.profile_business) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).background(scheme.primaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_home), contentDescription = null, tint = scheme.primary)
                }
                Column {
                    Text(
                        stringResource(R.string.profile_business_current),
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant
                    )
                    Text(business.businessName, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        val scopeLabel = when (business.locationScope) {
            "all" -> R.string.profile_locations_all
            "selected" -> R.string.profile_locations_selected
            else -> null
        }
        ProfileSection(R.string.profile_locations, scopeLabel) {
            if (business.locations.isEmpty()) {
                Text(stringResource(R.string.profile_locations_empty), color = scheme.onSurfaceVariant)
            }
            business.locations.forEachIndexed { index, location ->
                if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = WfSpacing.sm)) {
                    Text(location.name, Modifier.weight(1f))
                    if (location.code.isNotEmpty()) Text(location.code, color = scheme.onSurfaceVariant)
                }
            }
        }
        ProfileSection(R.string.profile_app) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_version), Modifier.weight(1f))
                Text(account.appVersion, color = scheme.onSurfaceVariant)
            }
        }
        WfButton(
            text = stringResource(R.string.profile_order_history),
            onClick = onOpenHistory,
            style = WfButtonStyle.BACK,
            fullWidth = true
        )
        if (!account.isSimulated) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                WfButton(
                    text = stringResource(R.string.auth_logout), onClick = { confirmLogout = true },
                    style = WfButtonStyle.LOGOUT_SOFT, enabled = !account.loading,
                    leadingIcon = R.drawable.ic_logout, fullWidth = true
                )
                Text(
                    stringResource(R.string.profile_logout_hint), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
        }
    }

    if (confirmLogout) {
        ModalBottomSheet(
            onDismissRequest = { confirmLogout = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = scheme.surfaceContainerLowest
        ) {
            Column(Modifier.padding(WfSpacing.xl), verticalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
                Text(stringResource(R.string.profile_logout_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.profile_logout_hint), color = scheme.onSurfaceVariant)
                WfButton(stringResource(R.string.auth_logout), onClick = {
                    confirmLogout = false
                    account.logout()
                }, style = WfButtonStyle.LOGOUT, enabled = !account.loading, fullWidth = true)
                WfButton(
                    stringResource(R.string.tables_cancel), onClick = { confirmLogout = false },
                    style = WfButtonStyle.BACK, fullWidth = true
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentity(name: String, email: String?) {
    val scheme = MaterialTheme.colorScheme
    val initials = name.split(' ').filter { it.firstOrNull()?.isLetterOrDigit() == true }
        .take(2).joinToString("") { it.take(1).uppercase() }
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(WfThemeExtras.warmSurface)) {
        Box(Modifier.align(Alignment.TopEnd).offset(56.dp, (-56).dp).size(170.dp).background(WfThemeExtras.selectedOption, CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(24.dp, 56.dp).size(110.dp).background(WfThemeExtras.campaignSurface, CircleShape))
        Column(
            Modifier.fillMaxWidth().padding(WfSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            Box(Modifier.size(80.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                if (initials.isEmpty()) {
                    Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = WfThemeExtras.warmSurface)
                } else {
                    Text(initials, style = MaterialTheme.typography.headlineSmall, color = WfThemeExtras.warmSurface)
                }
            }
            Text(
                name, style = MaterialTheme.typography.headlineSmall,
                color = scheme.onPrimaryContainer, textAlign = TextAlign.Center
            )
            if (email != null) {
                Text(
                    email, style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSecondaryContainer, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(title: Int, chip: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (chip != null) StatusChip(stringResource(chip), StatusTone.NEUTRAL)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.fillMaxWidth().padding(WfSpacing.cardPadding), content = content)
        }
    }
}
