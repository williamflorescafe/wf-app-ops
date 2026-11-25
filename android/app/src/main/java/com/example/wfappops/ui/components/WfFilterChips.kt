package com.example.wfappops.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfSize
import androidx.compose.ui.text.font.FontWeight
import com.example.wfappops.ui.theme.WfThemeExtras

/** Filtros de selección única: verde de marca al seleccionar y superficie neutra al descansar. */
@Composable
internal fun WfFilterChips(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex, onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = WfSize.minTouchTarget),
                label = { Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = scheme.surfaceContainerLowest,
                    labelColor = scheme.onSurfaceVariant,
                    selectedContainerColor = scheme.primary,
                    selectedLabelColor = WfThemeExtras.onAdvanceAction
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = index == selectedIndex,
                    borderColor = scheme.outlineVariant, selectedBorderColor = scheme.primary
                )
            )
        }
    }
}
