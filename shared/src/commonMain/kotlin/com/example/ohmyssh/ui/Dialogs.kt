package com.example.ohmyssh.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CompletableDeferred

internal class DialogEntry(
    val content: @Composable (dismiss: (Any?) -> Unit) -> Unit,
) {
    val result = CompletableDeferred<Any?>()
}

object Dialogs {
    internal val stack = mutableStateListOf<DialogEntry>()

    suspend fun <T> show(content: @Composable (dismiss: (T?) -> Unit) -> Unit): T? {
        val entry = DialogEntry { dismiss ->
            content { value -> dismiss(value) }
        }
        stack.add(entry)
        try {
            @Suppress("UNCHECKED_CAST")
            return entry.result.await() as? T
        } finally {
            stack.remove(entry)
        }
    }
}

@Composable
fun DialogsHost() {
    for (entry in Dialogs.stack.toList()) {
        entry.content { value -> entry.result.complete(value) }
    }
}
