package com.example.wfappops.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras

@Composable
internal fun WfSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(WfSpacing.xxs)) {
            labels.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                Box(
                    Modifier.weight(1f)
                        .heightIn(min = WfSize.compactControl - WfSpacing.xxs * 2)
                        .clip(CircleShape)
                        .background(if (selected) scheme.primary else Color.Transparent)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = WfSpacing.sm, vertical = WfSpacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) WfThemeExtras.onAdvanceAction else scheme.onSurface
                    )
                }
            }
        }
    }
}
