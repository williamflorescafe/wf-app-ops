package com.example.wfappops.orders.view

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.wfappops.R
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing

@Composable
internal fun OrderSearchField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    inputTransformation: InputTransformation? = null
) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        state = state,
        modifier = modifier.height(WfSize.minTouchTarget)
            .semantics { contentDescription = label },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        placeholder = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
            focusedBorderColor = scheme.onPrimaryContainer,
            unfocusedBorderColor = scheme.outlineVariant
        ),
        contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.sm),
        inputTransformation = inputTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
        onKeyboardAction = { focus.clearFocus() }
    )
}
