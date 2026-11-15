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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.example.wfappops.auth.AccountScreen
import com.example.wfappops.auth.AccountAccessScreen
import com.example.wfappops.auth.AccountViewModel
import com.example.wfappops.auth.TeamBusinessContext
import com.example.wfappops.carta.CreateOrderActivity
import com.example.wfappops.cart.DEMO_STORAGE_PREFIX
import com.example.wfappops.home.HomeScreen
import com.example.wfappops.home.OrderHistoryScreen
import com.example.wfappops.home.rememberOrderHistory
import com.example.wfappops.tables.RegisteredOrder
import com.example.wfappops.tables.TableWorker
import com.example.wfappops.ui.components.OpsBottomBar
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.OpsTab
import com.example.wfappops.ui.theme.WfTheme

class MainActivity : ComponentActivity() {
    private val account by viewModels<AccountViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            WfTheme {
                val lightBackground = account.restoring || account.signedIn
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                        lightBackground
                }
                OpsRoot(account)
            }
        }
    }
}

@Composable
private fun OpsRoot(account: AccountViewModel) {
    val business = account.businessContext
    if (business == null || account.restoring) {
        Surface(Modifier.fillMaxSize()) { AccountAccessScreen(account) }
        return
    }

    val storageId = if (account.isSimulated) DEMO_STORAGE_PREFIX + business.businessId else business.businessId
    key(storageId, business.employeeId) { AuthorizedOps(account, business, storageId) }
}

@Composable
private fun AuthorizedOps(account: AccountViewModel, business: TeamBusinessContext, storageId: String) {
    var savedTab by rememberSaveable { mutableStateOf(OpsTab.HOME) }
    val tab = if (savedTab == OpsTab.CARTA) OpsTab.HOME else savedTab
    var openingOrder by rememberSaveable { mutableStateOf(false) }
    var orderHistoryVersion by rememberSaveable { mutableIntStateOf(0) }
    var showOrderHistory by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val worker = TableWorker(business.employeeId, business.employeeName)
    val history = rememberOrderHistory(storageId, worker.id, orderHistoryVersion)

    fun navigate(destination: OpsTab) {
        if (account.loading) return
        showOrderHistory = false
        savedTab = destination
    }

    fun openHistory() {
        savedTab = OpsTab.PROFILE
        showOrderHistory = true
    }

    val orderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openingOrder = false
        orderHistoryVersion++
    }

    fun openOrder(order: RegisteredOrder? = null) {
        if (openingOrder || account.loading) return
        openingOrder = true
        orderLauncher.launch(CreateOrderActivity.intent(context, storageId, worker, order))
    }

    BackHandler(enabled = tab != OpsTab.HOME) {
        if (showOrderHistory) showOrderHistory = false else navigate(OpsTab.HOME)
    }
    OpsScaffold(bottomBar = {
        OpsBottomBar(selected = tab, onSelect = { if (it != tab) navigate(it) })
    }) {
        when (tab) {
            OpsTab.HOME -> {
                HomeScreen(
                    worker = worker,
                    history = history,
                    canOpenOrder = !openingOrder && !account.loading,
                    onCreateOrder = { openOrder() },
                    onOpenOrder = ::openOrder,
                    onSeeMore = ::openHistory
                )
            }
            OpsTab.CARTA -> Unit
            OpsTab.PROFILE -> if (showOrderHistory) {
                OrderHistoryScreen(history, !openingOrder && !account.loading, ::openOrder) {
                    showOrderHistory = false
                }
            } else {
                AccountScreen(account, business, onOpenHistory = ::openHistory)
            }
        }
    }
}
