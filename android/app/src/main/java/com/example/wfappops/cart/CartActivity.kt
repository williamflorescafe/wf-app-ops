package com.example.wfappops.cart

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.carta.formatCartaPrice
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.adjustCartLine
import com.example.wfappops.ui.components.CartShortcut
import com.example.wfappops.ui.components.OpsBottomBar
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.OpsTab
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageId = intent.getStringExtra(GENERAL_STORAGE)
        if (storageId == null || !hasOpsAccess(storageId)) {
            finish()
            return
        }
        val store = CartStore(applicationContext, storageId)
        fun checkAccess() {
            check(hasOpsAccess(storageId))
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            WfTheme {
                CartRoute(
                    read = {
                        checkAccess()
                        store.read()
                    },
                    adjust = { line, delta ->
                        checkAccess()
                        store.update { it.adjustCartLine(line, delta) }
                    },
                    onExit = ::finishTo
                )
            }
        }
    }

    private fun finishTo(destination: String) {
        setResult(RESULT_OK, Intent().putExtra(DESTINATION, destination))
        finish()
    }

    internal companion object {
        private const val DESTINATION = "cart-destination"
        private const val GENERAL_STORAGE = "cart-general-storage"

        fun destinationTab(result: Intent?): OpsTab? =
            OpsTab.entries.firstOrNull { it.name == result?.getStringExtra(DESTINATION) }

        fun generalIntent(context: Context, storageId: String) = Intent(context, CartActivity::class.java)
            .putExtra(GENERAL_STORAGE, storageId)
    }
}

@Composable
private fun CartRoute(
    read: () -> List<OrderLine>,
    adjust: (OrderLine, Int) -> List<OrderLine>,
    onExit: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<OrderLine>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun update(operation: () -> List<OrderLine>) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(operation) }
            busy = false
            result.onSuccess { lines = it }.onFailure { failed = true }
        }
    }
    LaunchedEffect(Unit) { update(read) }
    OpsScaffold(bottomBar = {
        OpsBottomBar(selected = OpsTab.CARTA, onSelect = { if (!busy) onExit(it.name) })
    }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.headlineMedium)
            }
            if (failed) {
                Text(
                    stringResource(R.string.cart_error),
                    Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error
                )
            }
            val current = lines
            when {
                current == null && busy -> CircularProgressIndicator(Modifier.padding(16.dp))
                current == null -> TextButton(onClick = { update(read) }) { Text(stringResource(R.string.auth_retry)) }
                current.isEmpty() -> Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.cart_empty))
                    TextButton(onClick = { onExit(OpsTab.CARTA.name) }) { Text(stringResource(R.string.tables_view_carta)) }
                }
                else -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(current) { line -> CartLine(line, busy) { delta -> update({ adjust(line, delta) }) } }
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.cart_total, formatCartaPrice(current.sumOf { it.totalInCents })),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CartLine(line: OrderLine, busy: Boolean, onQuantity: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.product, style = MaterialTheme.typography.titleMedium)
            if (line.options.isNotEmpty()) Text(line.options.joinToString(), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val less = stringResource(R.string.carta_quantity_less)
                val more = stringResource(R.string.carta_quantity_more)
                IconButton(
                    onClick = { onQuantity(-1) }, enabled = !busy,
                    modifier = Modifier.size(WfSize.minTouchTarget)
                ) {
                    Text("−", Modifier.semantics { contentDescription = less })
                }
                Text(line.quantity.toString())
                IconButton(
                    onClick = { onQuantity(1) }, enabled = !busy && line.quantity < Int.MAX_VALUE,
                    modifier = Modifier.size(WfSize.minTouchTarget)
                ) {
                    Text("+", Modifier.semantics { contentDescription = more })
                }
                Text(formatCartaPrice(line.totalInCents), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(
                    onClick = { onQuantity(-line.quantity) }, enabled = !busy,
                    modifier = Modifier.sizeIn(minWidth = WfSize.minTouchTarget, minHeight = WfSize.minTouchTarget)
                ) {
                    Text(stringResource(R.string.tables_remove))
                }
            }
        }
    }
}

internal fun cartShortcut(lines: List<OrderLine>, onOpen: () -> Unit) = CartShortcut(
    count = lines.sumOf { it.quantity.toLong() },
    total = formatCartaPrice(lines.sumOf { it.totalInCents }),
    onOpen = onOpen
)
