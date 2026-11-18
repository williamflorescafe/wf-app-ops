package com.example.wfappops.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.home.viewmodel.OrderHistoryUiState
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.WfSpacing

private const val RecentOrderLimit = 20

@Composable
internal fun HomeScreen(
    worker: TableWorker,
    canOpenOrder: Boolean,
    onCreateOrder: () -> Unit,
    history: OrderHistoryUiState,
    onRetry: () -> Unit,
    onOpenOrder: (RegisteredOrder) -> Unit,
    onSeeMore: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(scheme.background),
        contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
    ) {
        item {
            Text(
                stringResource(R.string.home_greeting, worker.name),
                modifier = Modifier.padding(bottom = WfSpacing.sm),
                style = MaterialTheme.typography.titleLarge
            )
        }
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(WfSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
                ) {
                    Box(
                        Modifier.size(56.dp).background(WfThemeExtras.warmSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_order_bag), contentDescription = null,
                            modifier = Modifier.size(26.dp), tint = scheme.primary
                        )
                    }
                    Button(
                        onClick = onCreateOrder,
                        enabled = canOpenOrder,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary, contentColor = WfThemeExtras.onAdvanceAction,
                            disabledContainerColor = scheme.outlineVariant,
                            disabledContentColor = scheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.home_create_order))
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.home_my_orders),
                modifier = Modifier.padding(top = WfSpacing.lg, bottom = WfSpacing.xs),
                style = MaterialTheme.typography.titleLarge
            )
        }
        orderHistoryItems(history, onRetry, canOpenOrder, onOpenOrder, limit = RecentOrderLimit)
        if ((history.orders?.size ?: 0) > RecentOrderLimit) item(key = "see-more-orders") {
            WfButton(
                text = stringResource(R.string.home_orders_more),
                onClick = onSeeMore,
                style = WfButtonStyle.BACK,
                fullWidth = true
            )
        }
    }
}
