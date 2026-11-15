package com.example.wfappops.carta

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.wfappops.R
import com.example.wfappops.cart.CartActivity
import com.example.wfappops.cart.CartStore
import com.example.wfappops.cart.cartShortcut
import com.example.wfappops.cart.hasCartAccess
import com.example.wfappops.tables.OrderLine
import com.example.wfappops.tables.addCartLine
import com.example.wfappops.ui.components.OpsBottomBar
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.OpsTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GeneralCarta(
    storageId: String,
    onExit: () -> Unit,
    onNavigate: (OpsTab) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val store = remember(storageId) { CartStore(context.applicationContext, storageId) }
    var lines by remember(storageId) { mutableStateOf(emptyList<OrderLine>()) }
    var busy by remember(storageId) { mutableStateOf(false) }

    fun update(change: ((List<OrderLine>) -> List<OrderLine>)? = null) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(context.hasCartAccess(storageId))
                    if (change == null) store.read() else store.update(change)
                }
            }
            busy = false
            result.onSuccess { lines = it }
                .onFailure { snackbar.showSnackbar(context.getString(R.string.cart_error)) }
        }
    }

    LaunchedEffect(store) { update() }
    val cartLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val destination = CartActivity.destinationTab(result.data)
        if (destination == null || destination == OpsTab.CARTA) {
            update()
        } else {
            onNavigate(destination)
        }
    }

    OpsScaffold(bottomBar = {
        OpsBottomBar(
            selected = OpsTab.CARTA,
            onSelect = { if (it != OpsTab.CARTA && !busy) onNavigate(it) },
            cart = cartShortcut(lines) {
                if (!busy) cartLauncher.launch(CartActivity.generalIntent(context, storageId))
            }
        )
    }) {
        Box(Modifier.fillMaxSize()) {
            CartaScreen(
                onExit = { if (!busy) onExit() },
                onAdd = { line -> if (!busy) update { it.addCartLine(line) } },
                adding = busy
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
