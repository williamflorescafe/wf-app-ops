package com.example.wfappops.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.wfappops.R
import com.example.wfappops.tables.tableZone
import com.example.wfappops.ui.components.*
import com.example.wfappops.ui.theme.ColorTokens
import com.example.wfappops.ui.theme.WfSpacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Historial local de avisos recibidos: no depende del estado actual ni consulta al API. */
@Composable
internal fun NotificationsScreen(history: Result<List<LocalNotification>>?, onBack: () -> Unit,
    onRetry: () -> Unit = {}) {
    var now by remember { mutableStateOf(Instant.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = Instant.now(); delay(1_000) }
        }
    }
    OpsScaffold(topBar = {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.auth_back), Modifier.rotate(180f))
                }
                Text(stringResource(R.string.home_notifications), style = MaterialTheme.typography.titleLarge)
            }
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            NotificationRequirementsBanner()
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(WfSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
                val entries = history?.getOrNull()
                when {
                    history == null -> item { CircularProgressIndicator() }
                    entries == null -> item { ErrorState(stringResource(R.string.notification_history_error), onRetry) }
                    entries.isEmpty() -> item { EmptyState(stringResource(R.string.home_notifications),
                        stringResource(R.string.home_notifications_empty)) }
                    else -> entries.sortedByDescending { it.receivedAt }.groupBy {
                        it.receivedAt.atZone(tableZone).toLocalDate()
                    }.forEach { (day, group) ->
                        item(key = "day-$day") {
                            val today = now.atZone(tableZone).toLocalDate()
                            Text(when (day) {
                                today -> stringResource(R.string.home_orders_today)
                                today.minusDays(1) -> stringResource(R.string.orders_yesterday)
                                else -> notificationDay.format(day)
                            }, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = WfSpacing.xs))
                        }
                        items(group, key = { it.id }) { entry -> NotificationHistoryRow(entry, now) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryRow(entry: LocalNotification, now: Instant) {
    val reminder = entry.kind == LocalNotificationKind.SERVE_REMINDER
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = WfSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
        Box(Modifier.size(48.dp).background(ColorTokens.NavigationIndicator, CircleShape), contentAlignment = Alignment.Center) {
            Text(entry.tableLabel, style = MaterialTheme.typography.titleMedium,
                color = ColorTokens.NavigationPrimary, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
            Text(stringResource(if (reminder) R.string.notification_serve_reminder_title else R.string.notification_ready_title),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val product = if (entry.quantity == 1) entry.product else "${entry.quantity} × ${entry.product}"
            Text(stringResource(if (reminder) R.string.notification_serve_reminder_body else R.string.notification_ready_body, product),
                style = MaterialTheme.typography.bodyMedium)
            val order = entry.orderNumber?.let { stringResource(R.string.notification_order_label,
                stringResource(R.string.order_number, it)) }
            val details = listOfNotNull(order, entry.locationName).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            val seconds = notificationElapsedSeconds(entry.receivedAt, now)
            val age = when {
                seconds < 60 -> stringResource(R.string.notification_seconds_ago, seconds)
                seconds < 3_600 -> stringResource(R.string.notification_minutes_ago, seconds / 60)
                seconds < 86_400 -> stringResource(R.string.notification_hours_ago, seconds / 3_600)
                else -> stringResource(R.string.notification_days_ago, seconds / 86_400)
            }
            Text(age, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

private val notificationDay = DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", Locale.forLanguageTag("es-PE"))
