package com.example.wfappops.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.ColorTokens

/** Destino visual de la barra, independiente de la navegación de cada pantalla. */
internal data class WfBottomBarItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int = icon
)

/** Acción principal de la barra; se mantiene separada de los destinos de navegación. */
internal data class WfBottomBarAction(
    val label: String,
    @param:DrawableRes val icon: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

/** Barra de WF Team adaptada a los recursos de Android y a la navegación de OPS. */
@Composable
internal fun WfBottomBar(
    items: List<WfBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    centerAction: WfBottomBarAction? = null
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = ColorTokens.NavigationLine)
        NavigationBar(containerColor = ColorTokens.Surface, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                if (centerAction != null && index == items.size / 2) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = centerAction.onClick,
                            enabled = centerAction.enabled,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp).shadow(3.dp, CircleShape),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(painterResource(centerAction.icon), contentDescription = centerAction.label, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                NavigationBarItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            painterResource(if (index == selectedIndex) item.selectedIcon else item.icon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ColorTokens.Ink,
                        selectedTextColor = ColorTokens.NavigationPrimary,
                        indicatorColor = ColorTokens.NavigationIndicator,
                        unselectedIconColor = ColorTokens.NavigationMuted,
                        unselectedTextColor = ColorTokens.NavigationMuted
                    )
                )
            }
        }
    }
}
