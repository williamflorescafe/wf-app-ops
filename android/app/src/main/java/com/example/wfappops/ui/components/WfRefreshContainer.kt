package com.example.wfappops.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.example.wfappops.R

/** Actualiza al deslizar; incluye una acción equivalente para lectores de pantalla. */
@Composable
internal fun WfRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (enabled) {
        val label = stringResource(R.string.state_reload)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { if (!isRefreshing) onRefresh() },
            modifier = modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(label) {
                    if (isRefreshing) false else { onRefresh(); true }
                })
            },
            content = content
        )
    } else {
        Box(modifier, content = content)
    }
}
