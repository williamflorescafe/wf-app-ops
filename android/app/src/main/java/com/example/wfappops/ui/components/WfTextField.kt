package com.example.wfappops.ui.components


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.WfRadius
import com.example.wfappops.ui.theme.WfSpacing

/**
 * Campo de texto con la etiqueta encima (más legible que la etiqueta flotante de Material).
 *
 * Si [isPassword] es verdadero, oculta el texto e incluye el botón para mostrarlo. [errorMessage]
 * pinta el borde de error y muestra el mensaje debajo del campo.
 */
@Composable
internal fun WfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: Int? = null,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    labelInside: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme

    Column {
        if (!labelInside) Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            modifier = Modifier.padding(bottom = WfSpacing.sm),
        )
        if (isPassword) {
            WfPasswordField(value, onValueChange, label, modifier, placeholder, leadingIcon,
                errorMessage, enabled, keyboardOptions, keyboardActions, labelInside)
            return@Column
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().semantics { if (labelInside) contentDescription = label },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = (if (labelInside) label else placeholder)?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            leadingIcon = leadingIcon?.let {
                { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp)) }
            },
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(WfRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainerLowest,
                unfocusedContainerColor = scheme.surfaceContainerLowest,
                disabledContainerColor = scheme.surfaceContainerLow,
                errorContainerColor = scheme.surfaceContainerLowest,
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = if (labelInside) scheme.outline else scheme.outlineVariant,
                focusedLeadingIconColor = scheme.primary,
                unfocusedLeadingIconColor = scheme.onSurfaceVariant,
                unfocusedTrailingIconColor = scheme.onSurfaceVariant,
                focusedTrailingIconColor = scheme.onSurfaceVariant,
                unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                focusedPlaceholderColor = scheme.onSurfaceVariant,
            ),
        )
    }
}
