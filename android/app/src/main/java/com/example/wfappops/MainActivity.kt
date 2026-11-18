package com.example.wfappops

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity

import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wfappops.auth.DEMO_STORAGE_PREFIX
import com.example.wfappops.auth.data.AccountRepository
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.view.AccountAccessScreen
import com.example.wfappops.auth.view.AccountScreen
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.auth.viewmodel.AccountViewModel
import com.example.wfappops.home.view.HomeScreen
import com.example.wfappops.home.view.OrderHistoryScreen
import com.example.wfappops.home.viewmodel.OrderHistoryScope
import com.example.wfappops.home.viewmodel.OrderHistoryUiState
import com.example.wfappops.home.viewmodel.OrderHistoryViewModel
import com.example.wfappops.kitchen.view.KitchenRoute
import com.example.wfappops.kitchen.viewmodel.KitchenViewModel
import com.example.wfappops.notifications.ReadyNotificationRecipient
import com.example.wfappops.notifications.ReadyProductNotifications
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.orders.view.CreateOrderActivity
import com.example.wfappops.ui.components.OpsBottomBar
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.OpsTab
import com.example.wfappops.ui.theme.WfTheme
import com.example.wfappops.ui.theme.WfThemeExtras

class MainActivity : ComponentActivity() {
    private val account by viewModels<AccountViewModel> {
        val app = application
        viewModelFactory { initializer { AccountViewModel(AccountRepository(app), app::getString) } }
    }
    private val history by viewModels<OrderHistoryViewModel> {
        val app = application
        viewModelFactory { initializer { OrderHistoryViewModel { OrderRepository(app, it) } } }
    }
    private val kitchen by viewModels<KitchenViewModel> {
        val app = application
        viewModelFactory { initializer { KitchenViewModel { OrderRepository(app, it) } } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            WfTheme {
                val accountState by account.uiState.collectAsStateWithLifecycle()
                val lightBackground = accountState.restoring || accountState.signedIn
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                        lightBackground
                }
                OpsRoot(accountState, account, history, kitchen)
            }
        }
    }
}

@Composable
private fun OpsRoot(
    state: AccountUiState,
    account: AccountViewModel,
    history: OrderHistoryViewModel,
    kitchen: KitchenViewModel
) {
    val business = state.businessContext
    val storageId = business?.businessId?.let { if (state.isSimulated) DEMO_STORAGE_PREFIX + it else it }
    if (!state.restoring) {
        val recipient = if (state.isSimulated && business != null && storageId != null) {
            ReadyNotificationRecipient(storageId, business.employeeId, business.employeeName)
        } else null
        ReadyProductNotifications(recipient)
    }
    if (business == null || storageId == null || state.restoring) {
        LaunchedEffect(history, kitchen) {
            history.clear()
            kitchen.clear()
        }
        Surface(Modifier.fillMaxSize()) {
            AccountAccessScreen(
                state = state,
                onUpdateEmail = account::updateEmail,
                onUpdatePassword = account::updatePassword,
                onLogin = account::login,
                onSelectBusiness = account::selectBusiness,
                onRetry = account::retry,
                onLogout = account::logout
            )
        }
        return
    }

    key(storageId, business.employeeId) { AuthorizedOps(state, account, history, kitchen, business, storageId) }
}

@Composable
private fun AuthorizedOps(
    state: AccountUiState,
    account: AccountViewModel,
    history: OrderHistoryViewModel,
    kitchen: KitchenViewModel,
    business: TeamBusinessContext,
    storageId: String
) {
    var savedTab by rememberSaveable { mutableStateOf(OpsTab.HOME) }
    val tab = if (savedTab == OpsTab.CARTA) OpsTab.HOME else savedTab
    var openingOrder by rememberSaveable { mutableStateOf(false) }
    var showOrderHistory by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val worker = TableWorker(business.employeeId, business.employeeName)
    val historyScope = OrderHistoryScope(storageId, worker.id)
    val historyState by history.uiState.collectAsStateWithLifecycle()
    val scopedHistory = historyState.takeIf { it.scope == historyScope } ?: OrderHistoryUiState()
    val reloadHistory = { history.load(historyScope, refresh = true) }
    LaunchedEffect(historyScope) { history.load(historyScope) }

    fun navigate(destination: OpsTab) {
        if (state.loading) return
        showOrderHistory = false
        savedTab = destination
    }

    fun openHistory() {
        savedTab = OpsTab.PROFILE
        showOrderHistory = true
    }

    val orderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openingOrder = false
    }

    fun openOrder(order: RegisteredOrder? = null) {
        if (openingOrder || state.loading) return
        openingOrder = true
        orderLauncher.launch(CreateOrderActivity.intent(context, storageId, worker, order))
    }

    BackHandler(enabled = tab != OpsTab.HOME) {
        if (showOrderHistory) showOrderHistory = false else navigate(OpsTab.HOME)
    }
    val background = if (tab == OpsTab.KITCHEN) WfThemeExtras.operationsBackground else MaterialTheme.colorScheme.background
    OpsScaffold(
        containerColor = background,
        bottomBar = { OpsBottomBar(selected = tab, onSelect = { if (it != tab) navigate(it) }) }
    ) {
        when (tab) {
            OpsTab.HOME -> {
                HomeScreen(
                    worker = worker,
                    history = scopedHistory,
                    onRetry = reloadHistory,
                    canOpenOrder = !openingOrder && !state.loading,
                    onCreateOrder = { openOrder() },
                    onOpenOrder = ::openOrder,
                    onSeeMore = ::openHistory
                )
            }
            OpsTab.CARTA -> Unit
            OpsTab.KITCHEN -> KitchenRoute(kitchen, storageId, worker.id, simulated = state.isSimulated)
            OpsTab.PROFILE -> if (showOrderHistory) {
                OrderHistoryScreen(scopedHistory, reloadHistory, !openingOrder && !state.loading, ::openOrder) {
                    showOrderHistory = false
                }
            } else {
                LaunchedEffect(account, historyScope) { account.loadProfile() }
                AccountScreen(state, business, account::loadProfile, account::logout, ::openHistory)
            }
        }
    }
}
