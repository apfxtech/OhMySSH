package com.example.ohmyssh.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object AppToasts {
    val hostState = SnackbarHostState()
    internal var scope: CoroutineScope? = null

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val scope = scope ?: return
        scope.launch {
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}
