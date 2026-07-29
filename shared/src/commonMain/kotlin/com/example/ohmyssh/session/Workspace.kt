package com.example.ohmyssh.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.fs.FileBrowsers

sealed class PaneRef {
    data class Shell(val session: String) : PaneRef()

    data class Files(val session: String) : PaneRef()

    data object Local : PaneRef()

    data object Picker : PaneRef()
}

fun PaneRef.sessionId(): String? = when (this) {
    is PaneRef.Shell -> session
    is PaneRef.Files -> session
    else -> null
}

class PaneWindow(val id: String, ref: PaneRef) {
    var ref: PaneRef by mutableStateOf(ref)
}

class PaneGroup(val id: String) {
    val windows = mutableStateListOf<PaneWindow>()
}

object Workspace {
    const val MAX_WINDOWS = 2

    val groups = mutableStateListOf<PaneGroup>()

    var activeGroupId: String? by mutableStateOf(null)
        private set

    var focusedWindowId: String? by mutableStateOf(null)
        private set

    val activeGroup: PaneGroup? get() = groups.firstOrNull { it.id == activeGroupId }

    val windows: List<PaneWindow> get() = activeGroup?.windows.orEmpty()

    val focusedWindow: PaneWindow?
        get() = windows.firstOrNull { it.id == focusedWindowId } ?: windows.firstOrNull()

    val isSplit: Boolean get() = windows.size >= 2

    fun windowById(id: String): PaneWindow? =
        groups.firstNotNullOfOrNull { group -> group.windows.firstOrNull { it.id == id } }

    fun openGroup(ref: PaneRef): PaneGroup {
        val group = PaneGroup("g-${newId()}")
        val window = PaneWindow("w-${newId()}", ref)
        group.windows.add(window)
        groups.add(group)
        activeGroupId = group.id
        focusedWindowId = window.id
        return group
    }

    fun reveal(ref: PaneRef): PaneGroup {
        for (group in groups) {
            val window = group.windows.firstOrNull { it.ref == ref } ?: continue
            activeGroupId = group.id
            focusedWindowId = window.id
            return group
        }
        return openGroup(ref)
    }

    fun activate(groupId: String) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        activeGroupId = group.id
        if (group.windows.none { it.id == focusedWindowId }) {
            focusedWindowId = group.windows.firstOrNull()?.id
        }
    }

    fun focusWindow(windowId: String) {
        val group = groups.firstOrNull { g -> g.windows.any { it.id == windowId } } ?: return
        activeGroupId = group.id
        focusedWindowId = windowId
    }

    fun addWindow(ref: PaneRef): PaneWindow {
        val group = activeGroup ?: return openGroup(ref).windows.first()

        if (ref.sessionId() != null) {
            group.windows.firstOrNull { it.ref == ref }?.let {
                focusedWindowId = it.id
                return it
            }
        }

        if (group.windows.size >= MAX_WINDOWS) {
            val target = group.windows.firstOrNull { it.id == focusedWindowId }
                ?: group.windows.first()
            retire(target)
            target.ref = ref
            focusedWindowId = target.id
            return target
        }

        val window = PaneWindow("w-${newId()}", ref)
        group.windows.add(window)
        focusedWindowId = window.id
        return window
    }

    fun resolve(windowId: String, ref: PaneRef) {
        val window = windowById(windowId) ?: return
        retire(window)
        window.ref = ref
        focusedWindowId = window.id
    }

    fun closeWindow(windowId: String) {
        val group = groups.firstOrNull { g -> g.windows.any { it.id == windowId } } ?: return
        val window = group.windows.first { it.id == windowId }
        retire(window)
        group.windows.remove(window)

        if (group.windows.isNotEmpty()) {
            if (focusedWindowId == windowId) focusedWindowId = group.windows.first().id
            return
        }

        groups.remove(group)
        if (activeGroupId == group.id) {
            val next = groups.lastOrNull()
            activeGroupId = next?.id
            focusedWindowId = next?.windows?.firstOrNull()?.id
        }
    }

    fun dropSession(sessionId: String) {
        for (group in groups.toList()) {
            for (window in group.windows.toList()) {
                if (window.ref.sessionId() == sessionId) closeWindow(window.id)
            }
        }
    }

    fun reset() {
        for (group in groups) {
            for (window in group.windows) retire(window)
        }
        groups.clear()
        activeGroupId = null
        focusedWindowId = null
    }

    private fun retire(window: PaneWindow) {
        FileBrowsers.forget(window.id)
    }
}
