package com.example.ohmyssh.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.fs.FileBrowsers

enum class PaneKind { SHELL, FILES }

fun paneKey(groupId: String, kind: PaneKind): String =
    "$groupId:${if (kind == PaneKind.SHELL) "shell" else "files"}"

class LocalPane(val id: String)

object Workspace {
    const val MAX_SLOTS = 2

    val localPanes = mutableStateListOf<LocalPane>()

    val slots = mutableStateListOf<String>()

    var focused: Int by mutableStateOf(0)
        private set

    val isSplit: Boolean get() = slots.size >= 2

    val focusedKey: String? get() = slots.getOrNull(focused)

    fun focus(index: Int) {
        if (index in slots.indices) focused = index
    }

    fun show(key: String) {
        val existing = slots.indexOf(key)
        if (existing >= 0) {
            focused = existing
            return
        }
        if (slots.isEmpty()) {
            slots.add(key)
            focused = 0
            return
        }
        slots[focused.coerceIn(0, slots.size - 1)] = key
    }

    fun showBeside(key: String) {
        val existing = slots.indexOf(key)
        if (existing >= 0) {
            focused = existing
            return
        }
        if (slots.size < MAX_SLOTS) {
            slots.add(key)
            focused = slots.size - 1
            return
        }
        val other = if (focused == 0) 1 else 0
        slots[other] = key
        focused = other
    }

    fun unsplit() {
        if (slots.size < 2) return
        val keep = slots[focused.coerceIn(0, slots.size - 1)]
        slots.clear()
        slots.add(keep)
        focused = 0
    }

    fun hide(key: String) {
        val index = slots.indexOf(key)
        if (index < 0) return
        slots.removeAt(index)
        if (focused >= slots.size) focused = (slots.size - 1).coerceAtLeast(0)
    }

    fun openLocal(): LocalPane {
        val pane = LocalPane("local-${newId()}")
        localPanes.add(pane)
        return pane
    }

    fun requestLocal(): LocalPane =
        localPanes.firstOrNull { !slots.contains(paneKey(it.id, PaneKind.FILES)) } ?: openLocal()

    fun closeLocal(id: String) {
        hide(paneKey(id, PaneKind.FILES))
        FileBrowsers.forgetGroup("$id:")
        localPanes.removeAll { it.id == id }
    }

    fun reconcile(available: List<String>) {
        val dead = slots.filterNot { available.contains(it) }
        for (key in dead) slots.remove(key)
        if (slots.isEmpty() && available.isNotEmpty()) slots.add(available.first())
        if (focused >= slots.size) focused = (slots.size - 1).coerceAtLeast(0)
    }

    fun reset() {
        slots.clear()
        focused = 0
        for (pane in localPanes.toList()) FileBrowsers.forgetGroup("${pane.id}:")
        localPanes.clear()
    }
}
