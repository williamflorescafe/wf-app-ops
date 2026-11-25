package com.example.wfappops.notifications

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.wfappops.R
import com.example.wfappops.ui.components.InlineMessage
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Aviso persistente: no permite ocultar un requisito pendiente ni modifica los ajustes del usuario. */
@Composable
internal fun NotificationRequirementsBanner() {
    val context = LocalContext.current.applicationContext
    val notifier = remember(context) { ReadyProductNotifier(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var issue by remember(notifier) { mutableStateOf(notifier.requirementsIssue()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        issue = notifier.requirementsIssue()
    }
    LaunchedEffect(notifier, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // También detecta cambios con las teclas de volumen mientras OPS está en primer plano.
            while (isActive) {
                issue = notifier.requirementsIssue()
                delay(2_000)
            }
        }
    }
    issue?.let { current ->
        NotificationRequirementNotice(current) {
            if (current == NotificationIssue.PERMISSION && notifier.shouldRequestPermission()) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else notifier.openRequiredSettings(current)
        }
    }
}

/** Una sola acción para corregir el requisito actual; desaparece al quedar configurado. */
@Composable
internal fun NotificationRequirementNotice(issue: NotificationIssue, onConfigure: () -> Unit) {
    val colors = WfThemeExtras.statusColors.of(StatusTone.WARNING)
    val message = when (issue) {
        NotificationIssue.PERMISSION -> R.string.notification_require_permission
        NotificationIssue.CHANNEL -> R.string.notification_require_channel
        NotificationIssue.DEVICE_SOUND -> R.string.notification_require_volume
        NotificationIssue.DO_NOT_DISTURB -> R.string.notification_require_dnd
    }
    InlineMessage(
        text = stringResource(message), tone = StatusTone.WARNING,
        modifier = Modifier.padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm)
    ) {
        TextButton(onClick = onConfigure) {
            Text(stringResource(R.string.notification_configure), color = colors.content)
        }
    }
}
