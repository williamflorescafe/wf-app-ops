package com.example.wfappops.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.wfappops.ui.model.OperationalStateColor

/** Único vínculo entre categorías operativas y colores de la marca. */
internal val OperationalStateColor.color: Color get() = when (this) {
    OperationalStateColor.ORANGE -> ColorTokens.OperationsPending
    OperationalStateColor.GREEN -> ColorTokens.ActiveIndicator
    OperationalStateColor.GRAY -> ColorTokens.OperationsDraft
    OperationalStateColor.RED -> ColorTokens.OperationsClosed
}
