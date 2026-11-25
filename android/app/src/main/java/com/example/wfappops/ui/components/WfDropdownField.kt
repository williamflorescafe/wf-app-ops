package com.example.wfappops.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/** Selector accesible de locales o ambientes, con el mismo borde y superficie que los campos de OPS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WfDropdownField(
    label: String, selectedLabel: String, options: List<Pair<String, String>>,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (options.isNotEmpty()) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel, onValueChange = {}, readOnly = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            label = { Text(label) }, singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainerLowest,
                unfocusedContainerColor = scheme.surfaceContainerLowest,
                focusedBorderColor = scheme.primary, unfocusedBorderColor = scheme.outlineVariant
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                    onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}
