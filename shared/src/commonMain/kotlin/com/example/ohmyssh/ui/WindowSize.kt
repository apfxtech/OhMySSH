package com.example.ohmyssh.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidth {
    COMPACT,
    MEDIUM,
    EXPANDED;

    val isCompact: Boolean get() = this == COMPACT
}

fun windowWidthFor(width: Dp): WindowWidth = when {
    width < 600.dp -> WindowWidth.COMPACT
    width < 840.dp -> WindowWidth.MEDIUM
    else -> WindowWidth.EXPANDED
}
