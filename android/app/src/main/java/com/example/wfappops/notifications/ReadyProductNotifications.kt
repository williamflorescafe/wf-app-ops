package com.example.wfappops.notifications

import android.Manifest
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
        OrderRepository(context, recipient.storageId)
            .observe { it.readyNoticesFor(recipient.employeeId) }
            .catch { error -> Log.w("ReadyProductNotifications", "Could not read ready products", error) }
            .collect { notices -> notifier.sync(recipient, notices) }
    }
}

@Composable
internal fun ReadyProductSoundSetting() {
    val context = LocalContext.current.applicationContext
    val notifier = remember(context) { ReadyProductNotifier(context) }
    var soundIssue by remember(notifier) { mutableStateOf(notifier.soundIssue()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { soundIssue = notifier.soundIssue() }

    WfButton(
        text = stringResource(R.string.ready_notification_sound),
        onClick = notifier::openSoundSettings,
        style = WfButtonStyle.BACK,
        fullWidth = true
    )
    soundIssue?.let { message ->
        Text(stringResource(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
