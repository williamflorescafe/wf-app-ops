package com.example.wfappops.home.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.wfappops.R
import com.example.wfappops.ui.components.StateMessage
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfCard
import com.example.wfappops.ui.components.WfScreenTitle
import com.example.wfappops.ui.components.WfSegmentedControl
import com.example.wfappops.ui.components.WfFilterChips
import com.example.wfappops.orders.view.OrderSearchField
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSpacing

/** Presentación de Comandas hasta disponer de su servicio, sin simular una lista vacía. */
@Composable
internal fun OrdersUnavailableScreen() {
    val search = rememberTextFieldState()
    var owner by rememberSaveable { mutableIntStateOf(0) }
    var status by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        WfScreenTitle(stringResource(R.string.ops_orders), stringResource(R.string.orders_last_two_days))
        OrderSearchField(search, stringResource(R.string.orders_search),
            Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm))
        WfSegmentedControl(
            labels = listOf(stringResource(R.string.home_my_orders), stringResource(R.string.ops_filter_all)),
            selectedIndex = owner, onSelect = { owner = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm)
        )
        WfFilterChips(
            labels = listOf(stringResource(R.string.ops_filter_all), stringResource(R.string.order_preparing), stringResource(R.string.order_ready)),
            selectedIndex = status, onSelect = { status = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg)
        )
        WfCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.md),
            contentPadding = PaddingValues(WfSpacing.xl)
        ) {
            StateMessage(
                title = stringResource(R.string.orders_overview_title),
                message = stringResource(R.string.orders_overview_description),
                tone = StatusTone.NEUTRAL,
                icon = R.drawable.ic_order_bag
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
                    WfButton(stringResource(R.string.home_create_order), onClick = {}, enabled = false, fullWidth = true)
                    Text(
                        stringResource(R.string.operations_unavailable),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
