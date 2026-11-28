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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.wfappops.R
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.readyNoticesFor
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.retryWhen

@Composable
internal fun ReadyProductNotifications(recipient: ReadyNotificationRecipient?, allowPermissionPrompt: Boolean = true) {
    val context = LocalContext.current.applicationContext
    val notifier = remember(context) { ReadyProductNotifier(context) }
    val owner = LocalLifecycleOwner.current
    var enabled by remember(notifier) { mutableStateOf(notifier.enabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = notifier.enabled()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = notifier.enabled() }
    LaunchedEffect(notifier, recipient) {
        notifier.selectRecipient(recipient)
    }
    LaunchedEffect(notifier, recipient, allowPermissionPrompt) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            allowPermissionPrompt && recipient != null && notifier.shouldRequestPermission()) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(notifier, recipient, enabled, owner) {
        if (recipient == null) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                var current: List<com.example.wfappops.orders.model.ReadyProductNotice>? = null
                launch {
                    readyProductNotices(context, recipient).onEach { notices ->
                        withContext(Dispatchers.IO) { notifier.sync(recipient, notices) }
                        current = notices
                    }.retryWhen { _, _ ->
                        current = null
                        Log.w("ReadyProductNotifications", "Could not persist ready-product notifications")
                        delay(5_000); true
                    }.collect { }
                }
                launch {
                    while (true) {
                        delay(15_000)
                        current?.let { notices -> withContext(Dispatchers.IO) {
                            runCatching { notifier.sync(recipient, notices) }
                                .onFailure { Log.w("ReadyProductNotifications", "Could not persist notification reminder") }
                        } }
                    }
                }
            }
        }
    }
}

/** Observa la proyección persistida de la cuenta activa; no simula eventos del servidor. */
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
