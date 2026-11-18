package com.example.wfappops.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.ui.theme.WfThemeExtras

internal enum class OpsTab(val label: Int, val icon: Int) {
    HOME(R.string.ops_home, R.drawable.ic_home),
    CARTA(R.string.ops_carta, R.drawable.ic_carta_cup),
    KITCHEN(R.string.ops_kitchen, R.drawable.ic_kitchen),
    PROFILE(R.string.ops_profile, R.drawable.ic_person)
}

internal class CartShortcut(val count: Long, val total: String, val onOpen: () -> Unit)

@Composable
internal fun OpsScaffold(
    bottomBar: @Composable () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

@Composable
internal fun OpsBottomBar(
    selected: OpsTab?,
    onSelect: (OpsTab) -> Unit,
    cart: CartShortcut? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = scheme.outlineVariant)
        if (cart != null) {
            Surface(color = scheme.surfaceContainerLowest) {
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    CartButton(cart)
                }
            }
        }
        NavigationBar(
            containerColor = scheme.surfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            OpsTab.entries.filterNot { it == OpsTab.CARTA }.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null, Modifier.size(24.dp)) },
                    label = {
                        Text(
                            stringResource(tab.label),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.primary,
                        selectedTextColor = scheme.primary,
                        indicatorColor = scheme.primaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun CartButton(cart: CartShortcut) {
    val description = stringResource(R.string.cart_button_description, cart.count, cart.total)
    Button(
        onClick = cart.onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(
            containerColor = WfThemeExtras.purchaseAction,
            contentColor = WfThemeExtras.onPurchaseAction
        )
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_order_bag), contentDescription = null, Modifier.size(20.dp))
            Text(stringResource(R.string.cart_title), Modifier.weight(1f))
            Text(stringResource(R.string.cart_shortcut_amount, cart.count, cart.total))
        }
    }
}
