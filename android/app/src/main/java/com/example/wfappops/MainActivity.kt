package com.example.wfappops

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wfappops.auth.data.AccountRepository
import com.example.wfappops.auth.view.AccountAccessScreen
import com.example.wfappops.auth.view.AccountScreen
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.auth.viewmodel.AccountViewModel
import com.example.wfappops.orders.view.CreateOrderActivity
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.home.view.HomeScreen
import com.example.wfappops.home.view.OrderHistoryScreen
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.viewmodel.OrderHistoryScope
import com.example.wfappops.home.viewmodel.OrderHistoryViewModel
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.tables.OpsTablesScreen
import com.example.wfappops.notifications.NotificationRequirementsBanner
import com.example.wfappops.notifications.ReadyProductNotifications
import com.example.wfappops.notifications.ReadyNotificationRecipient
import com.example.wfappops.ui.components.EmptyState
import com.example.wfappops.ui.components.OpsBottomBar
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.OpsTab
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfTheme

class MainActivity : ComponentActivity() {
    private val account by viewModels<AccountViewModel> {
        val app = application
        viewModelFactory { initializer { AccountViewModel(AccountRepository(app), app::getString) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            WfTheme {
                val state by account.uiState.collectAsStateWithLifecycle()
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                        state.restoring || state.signedIn
                }
                OpsRoot(state, account)
            }
        }
    }
}

@Composable
private fun OpsRoot(state: AccountUiState, account: AccountViewModel) {
    val business = state.businessContext
    ReadyProductNotifications(business?.takeUnless { state.restoring }?.let {
        ReadyNotificationRecipient(it.businessId, it.employeeId, it.employeeName)
    })
    if (business == null || state.restoring) {
        Surface(Modifier.fillMaxSize()) {
            AccountAccessScreen(state, account::updateEmail, account::updatePassword, account::login,
                account::selectBusiness, account::retry, account::logout)
        }
        return
    }
    key(business.businessId, business.employeeId) {
        val context = LocalContext.current
        val worker = TableWorker(business.employeeId, business.employeeName)
        val history = remember(business.businessId, worker.id) {
            ViewModelProvider(context as ComponentActivity, viewModelFactory {
                initializer { OrderHistoryViewModel(repository = { id -> OrderRepository(context, id, localDraftEmployeeId = worker.id) }) }
            }).get("orders-${business.businessId}-${worker.id}", OrderHistoryViewModel::class.java)
        }
        val orders by history.uiState.collectAsStateWithLifecycle()
        val scope = OrderHistoryScope(business.businessId, worker.id)
        LaunchedEffect(scope) { history.load(scope) }
        DisposableEffect(history) { onDispose { history.clear() } }
        var filter by rememberSaveable { mutableStateOf(HomeOrderFilter.ALL) }
        fun openOrder(table: Int? = null, visitId: String? = null) {
            context.startActivity(CreateOrderActivity.intent(context, business.businessId, worker, table, visitId))
        }
        var tab by rememberSaveable { mutableStateOf(OpsTab.HOME) }
        BackHandler(tab != OpsTab.HOME) { tab = OpsTab.HOME }
        OpsScaffold(bottomBar = {
            OpsBottomBar(
                tab, onSelect = { if (!state.loading) tab = it },
                onCreateOrder = { openOrder() }, createOrderEnabled = !state.loading
            )
        }) {
            Column(Modifier.fillMaxSize()) {
                NotificationRequirementsBanner()
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        OpsTab.HOME -> HomeScreen(
                            employeeName = business.employeeName, canOpenOrder = !state.loading,
                            onCreateOrder = { openOrder() }, openOrders = orders.homeOrders(filter),
                            filter = filter, onFilter = { filter = it },
                            onRetry = { history.load(scope, true) },
                            onOpenOrder = { openOrder(it.table, it.visitId) },
                            notificationOrders = orders.homeOrders(HomeOrderFilter.READY)
                        )
                        OpsTab.ORDERS -> OrderHistoryScreen(
                            orders = orders.orders(true), day = orders.day,
                            onRetry = { history.load(scope, true) },
                            canOpenOrder = !state.loading, onOpenOrder = { openOrder(it.table, it.visitId) }
                        )
                        OpsTab.TABLES -> OpsTablesScreen(state, attentions = orders.attentions,
                            onSelectTable = { locationId, tableId, attention ->
                                if (attention is com.example.wfappops.tables.TableAttention.Occupied) {
                                    openOrder(attention.internalTable, attention.visitId)
                                } else context.startActivity(CreateOrderActivity.intent(context, business.businessId, worker,
                                    null, null, locationId, tableId))
                            }, onLoad = account::loadTables)
                        OpsTab.PROFILE -> {
                            LaunchedEffect(business.businessId, business.employeeId) { account.loadProfile() }
                            AccountScreen(state, business, account::loadProfile, account::logout)
                        }
                        else -> Column(Modifier.fillMaxSize().padding(WfSpacing.lg)) {
                            EmptyState(stringResource(R.string.ops_orders), stringResource(R.string.operations_unavailable))
                        }
                    }
                }
            }
        }
    }
}
