package com.example.wfappops.carta

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.ui.theme.WfThemeExtras

@Composable
internal fun ProductCustomizer(
    groups: List<CartaModifierGroup>,
    selected: Map<String, List<CartaModifierOption>>,
    activeStep: Int,
    onStep: (Int) -> Unit,
    onSelect: (CartaModifierOption) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val group = groups[activeStep]
    val title = stringResource(R.string.carta_customize)
    val suffix = stringResource(R.string.carta_customize_suffix)
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                    append(' ')
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(suffix) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary
            )
            CustomizerSteps(groups, selected, activeStep, onStep)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(if (group.multiple) R.string.carta_choose_multiple else R.string.carta_choose_one),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    stringResource(if (group.minSelections > 0) R.string.carta_modifier_required else R.string.carta_modifier_optional),
                    modifier = Modifier.background(scheme.primary, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                    color = scheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            val columns = if (group.options.size <= 3) 3 else 2
            val choices = selected.getValue(group.id)
            Column(
                modifier = if (group.multiple) Modifier else Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.options.chunked(columns).forEach { options ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            val checked = option in choices
                            val enabled = option.available && (!group.multiple || checked || choices.size < group.maxSelections)
                            ModifierOptionTile(option, group.multiple, checked, enabled, Modifier.weight(1f)) {
                                onSelect(option)
                            }
                        }
                        repeat(columns - options.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizerSteps(
    groups: List<CartaModifierGroup>,
    selected: Map<String, List<CartaModifierOption>>,
    activeStep: Int,
    onStep: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val firstPending = groups.indexOfFirst { selected.getValue(it.id).size < it.minSelections }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stepWidth = maxOf(64.dp, maxWidth / groups.size)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).selectableGroup().drawBehind {
                val center = stepWidth.toPx() / 2
                val y = 22.dp.toPx()
                val end = center + stepWidth.toPx() * (groups.lastIndex)
                drawLine(scheme.surfaceVariant, Offset(center, y), Offset(end, y), 3.dp.toPx())
                val progress = (center + stepWidth.toPx() * (activeStep + 0.5f)).coerceAtMost(end)
                drawLine(scheme.primary, Offset(center, y), Offset(progress, y), 3.dp.toPx())
            }
        ) {
            groups.forEachIndexed { index, group ->
                val active = activeStep == index
                val enabled = firstPending < 0 || index <= firstPending
                val complete = selected.getValue(group.id).isNotEmpty()
                Column(
                    modifier = Modifier.width(stepWidth).selectable(active, enabled = enabled, role = Role.Tab) {
                        onStep(index)
                    }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp)
                            .shadow(if (active) 4.dp else 0.dp, CircleShape)
                            .background(if (complete) scheme.primary else scheme.primaryContainer, CircleShape)
                            .border(1.dp, if (enabled) scheme.primary else scheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (group.id) {
                            "tamano" -> R.drawable.ic_carta_cup
                            "leche" -> R.drawable.ic_carta_milk
                            else -> R.drawable.ic_carta_add
                        }
                        Icon(
                            painterResource(icon), null, Modifier.size(18.dp),
                            tint = if (complete) scheme.onPrimary else scheme.primary
                        )
                    }
                    Text(
                        group.label,
                        color = if (active) scheme.primary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ModifierOptionTile(
    option: CartaModifierOption,
    multiple: Boolean,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val selection = if (multiple) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() })
    } else {
        Modifier.selectable(checked, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }
    Box(
        modifier = modifier.clip(shape).background(if (checked) WfThemeExtras.selectedOption else scheme.surface)
            .border(1.dp, if (checked) WfThemeExtras.onSelectedOption else scheme.outlineVariant, shape).then(selection)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (option.image == null) 88.dp else 124.dp)
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            option.image?.let { CartaPhoto(it, null, Modifier.size(48.dp), contentScale = ContentScale.Fit) }
            Text(option.label.substringBefore('(').trim(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            val capacity = option.label.substringAfter('(', "").substringBefore(')')
            if (capacity.isNotEmpty()) {
                Text(capacity.replace("ml", " ml"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            val price = formatCartaPrice(option.priceDeltaInCents)
            Text(
                if (option.priceDeltaInCents > 0) stringResource(R.string.carta_modifier_surcharge, price) else price,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) WfThemeExtras.onSelectedOption else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check_circle), null,
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp), tint = scheme.primary
            )
        }
    }
}
