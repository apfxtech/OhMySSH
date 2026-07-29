package com.example.ohmyssh.fs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FileTransfer(
    val verb: String,
    val name: String,
    val total: Long,
    val done: Long,
) {
    val ratio: Float? get() = if (total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)
}

class CopyReport(val copied: Int, val skipped: Int)

class FileBrowserError(override val message: String) : Exception(message) {
    override fun toString(): String = message
}

class FileBrowserState(
    val source: FileSource,
    val scope: CoroutineScope,
) {
    var path: String by mutableStateOf("")
        private set

    var folders: List<FileEntry> by mutableStateOf(emptyList())
        private set

    var files: List<FileEntry> by mutableStateOf(emptyList())
        private set

    var loading: Boolean by mutableStateOf(true)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var transfer: FileTransfer? by mutableStateOf(null)

    var busy: Boolean by mutableStateOf(false)
        private set

    val selected = mutableStateListOf<String>()

    var selectionMode: Boolean by mutableStateOf(false)

    private var started = false

    val entries: List<FileEntry> get() = folders + files

    fun selectedEntries(): List<FileEntry> = entries.filter { selected.contains(it.name) }

    fun join(name: String): String = source.join(path, name)

    suspend fun start() {
        if (started) return
        started = true
        try {
            listDir(source.home())
        } catch (failure: Exception) {
            Log.error("files", "could not open ${source.label}: $failure", failure)
            loading = false
            error = "$failure"
        }
    }

    suspend fun listDir(target: String) {
        loading = true
        error = null
        selected.clear()
        selectionMode = false
        try {
            val listed = source.list(target).filter { it.name != "." && it.name != ".." }
            folders = listed.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            files = listed.filterNot { it.isDirectory }.sortedBy { it.name.lowercase() }
            path = target
            loading = false
        } catch (failure: Exception) {
            Log.error("files", "listdir $target failed: $failure", failure)
            loading = false
            error = "$failure"
        }
    }

    suspend fun refresh() = listDir(path)

    fun clearSelection() {
        selected.clear()
        selectionMode = false
    }

    fun select(entry: FileEntry) {
        selectionMode = true
        selected.clear()
        selected.add(entry.name)
    }

    fun toggle(entry: FileEntry) {
        if (!selected.remove(entry.name)) selected.add(entry.name)
        selectionMode = selected.isNotEmpty()
    }

    fun dragPayload(entry: FileEntry): List<FileEntry> =
        if (selectionMode && selected.contains(entry.name)) {
            selectedEntries().ifEmpty { listOf(entry) }
        } else {
            listOf(entry)
        }

    /// Copies [entries] from another pane into this one's current directory.
    /// Directories are walked; symlinked directories are skipped rather than
    /// followed, since one pointing at an ancestor never ends.
    suspend fun receive(from: FileBrowserState, entries: List<FileEntry>): CopyReport {
        if (entries.isEmpty()) return CopyReport(0, 0)
        val sameEndpoint = from.source.id == source.id
        if (sameEndpoint && from.path == path) {
            throw FileBrowserError("Those items are already here")
        }

        var copied = 0
        var skipped = 0
        busy = true
        try {
            for (entry in entries) {
                val origin = from.source.join(from.path, entry.name)
                if (sameEndpoint && entry.isDirectory && source.contains(origin, path)) {
                    throw FileBrowserError("Cannot copy ${entry.name} into itself")
                }
                val report = copyInto(from, origin, entry, path)
                copied += report.copied
                skipped += report.skipped
            }
        } finally {
            busy = false
            transfer = null
            refresh()
        }
        return CopyReport(copied, skipped)
    }

    private suspend fun copyInto(
        from: FileBrowserState,
        origin: String,
        entry: FileEntry,
        targetDir: String,
    ): CopyReport {
        if (entry.isDirectory) {
            if (entry.isSymlink) return CopyReport(0, 1)
            val target = source.join(targetDir, entry.name)
            // Already there is the common case when merging into an existing
            // folder; a real failure surfaces on the first child write.
            runCatching { source.mkdir(target) }

            var copied = 0
            var skipped = 0
            for (child in from.source.list(origin)) {
                if (child.name == "." || child.name == "..") continue
                val report = copyInto(from, from.source.join(origin, child.name), child, target)
                copied += report.copied
                skipped += report.skipped
            }
            return CopyReport(copied, skipped)
        }

        val total = entry.size ?: 0
        transfer = FileTransfer("Copying", entry.name, total, 0)
        val bytes = from.source.read(origin) { done ->
            transfer = FileTransfer("Copying", entry.name, total, done)
        }
        source.write(source.join(targetDir, entry.name), bytes) { done ->
            transfer = FileTransfer("Copying", entry.name, total, done)
        }
        return CopyReport(1, 0)
    }

    internal fun dispose() {
        scope.cancel()
    }
}

object FileBrowsers {
    private val states = mutableMapOf<String, FileBrowserState>()

    fun of(key: String, source: () -> FileSource): FileBrowserState = states.getOrPut(key) {
        FileBrowserState(source(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    fun byKey(key: String): FileBrowserState? = states[key]

    fun forget(key: String) {
        states.remove(key)?.dispose()
    }
}
