package com.example.wfappops.tables

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.wfappops.R
import com.example.wfappops.orders.view.OrderSearchField
import kotlinx.coroutines.flow.first

@Composable
internal fun TableSearchField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current

    LaunchedEffect(autoFocus, window) {
        if (!autoFocus) return@LaunchedEffect
        snapshotFlow { window.isWindowFocused }.first { it }
        focus.requestFocus()
        keyboard?.show()
    }

    OrderSearchField(
        state = state,
        label = stringResource(R.string.tables_search),
        modifier = modifier.focusRequester(focus),
        keyboardType = KeyboardType.Number,
        inputTransformation = {
            if (asCharSequence().any { it !in '0'..'9' }) revertAllChanges()
        }
    )
}

internal fun Int.matchesTableSearch(query: String): Boolean =
    toString().startsWith(query) || toString().padStart(2, '0').startsWith(query)
