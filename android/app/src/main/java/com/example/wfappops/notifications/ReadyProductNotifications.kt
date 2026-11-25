package com.example.wfappops.notifications

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.wfappops.R
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.readyNoticesFor
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import kotlinx.coroutines.flow.catch

@Composable
internal fun ReadyProductNotifications(recipient: ReadyNotificationRecipient?) {
    val context = LocalContext.current.applicationContext
    val notifier = remember(context) { ReadyProductNotifier(context) }
    var enabled by remember(notifier) { mutableStateOf(notifier.enabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = notifier.enabled()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = notifier.enabled() }
    LaunchedEffect(notifier, recipient) {
        notifier.selectRecipient(recipient)
        if (recipient != null && notifier.shouldRequestPermission()) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(notifier, recipient, enabled) {
        if (recipient == null || !enabled) return@LaunchedEffect
        readyProductNotices(context, recipient)
            .catch { error -> Log.w("ReadyProductNotifications", "Could not read ready products", error) }
            .collect { notices -> notifier.sync(recipient, notices) }
    }
}

/** Observa el mismo almacenamiento personal que usa el flujo manual de comandas. */
internal fun readyProductNotices(context: Context, recipient: ReadyNotificationRecipient) =
    OrderRepository(context, recipient.storageId, localDraftEmployeeId = recipient.employeeId)
        .observe { it.readyNoticesFor(recipient.employeeId) }

@Composable
internal fun ReadyProductSoundSetting() {
    val context = LocalContext.current.applicationContext
    val notifier = remember(context) { ReadyProductNotifier(context) }

    WfButton(
        text = stringResource(R.string.notification_configure),
        onClick = {
            val issue = notifier.requirementsIssue()
            if (issue == null) notifier.openSoundSettings() else notifier.openRequiredSettings(issue)
        },
        style = WfButtonStyle.BACK,
        fullWidth = true
    )
    Text(
        stringResource(R.string.notification_configuration_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
