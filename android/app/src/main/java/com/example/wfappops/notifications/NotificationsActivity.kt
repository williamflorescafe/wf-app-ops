package com.example.wfappops.notifications

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wfappops.MainActivity
import com.example.wfappops.auth.data.SessionStore
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.ui.theme.WfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) { SessionStore(applicationContext).read()?.verifiedBusiness?.context }
            if (account == null || !hasOpsAccess(account.businessId)) { returnToAccess(); return@launch }
            val recipient = ReadyNotificationRecipient(account.businessId, account.employeeId, account.employeeName)
            val store = NotificationHistoryStore(applicationContext, recipient)
            setContent {
                WfTheme {
                    var retry by remember { mutableIntStateOf(0) }
                    val updates = remember(store, retry) { store.observe().map { result -> result.map { history ->
                        history.entries.filter { entry -> entry.locationId == null || account.locations.any { it.id == entry.locationId } }
                    } } }
                    val entries by updates.collectAsStateWithLifecycle(initialValue = null)
                    LaunchedEffect(account) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            while (true) {
                                val current = withContext(Dispatchers.IO) { SessionStore(applicationContext).read()?.verifiedBusiness?.context }
                                if (current?.businessId != account.businessId || current.employeeId != account.employeeId ||
                                    current.locations != account.locations || !hasOpsAccess(account.businessId)) {
                                    returnToAccess(); break
                                }
                                delay(15_000)
                            }
                        }
                    }
                    ReadyProductNotifications(recipient)
                    NotificationsScreen(entries, onBack = { finish() }, onRetry = { retry++ })
                }
            }
        }
    }

    private fun returnToAccess() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    internal companion object {
        // El historial pertenece a la cuenta y reúne los avisos de todos sus locales autorizados.
        fun intent(context: Context, @Suppress("UNUSED_PARAMETER") locationId: String?) = Intent(context, NotificationsActivity::class.java)
    }
}
