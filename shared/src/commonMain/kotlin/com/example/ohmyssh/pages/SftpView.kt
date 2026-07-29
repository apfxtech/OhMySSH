package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ohmyssh.components.GroupedCardList
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.platform.FilePick
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.platform.isDesktop
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.SftpChannel
import com.example.ohmyssh.ssh.SftpEntry
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.promptForText
import kotlinx.coroutines.launch

private class Transfer(
    val name: String,
    val total: Long,
    val done: Long,
    val isDownload: Boolean,
) {
    val ratio: Float? get() = if (total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)
}

@Composable
fun SftpView(session: HostSession) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    var sftp by remember(session.id) { mutableStateOf<SftpChannel?>(null) }
    var path by remember(session.id) { mutableStateOf(".") }
    var folders by remember(session.id) { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var files by remember(session.id) { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var transfer by remember(session.id) { mutableStateOf<Transfer?>(null) }

    val selected = remember(session.id) { mutableStateListOf<String>() }
    var selectionMode by remember(session.id) { mutableStateOf(false) }

    suspend fun listDir(target: String) {
        val channel = sftp ?: return
        loading = true
        error = null
        selected.clear()
        selectionMode = false
        try {
            val names = channel.list(target).filter { it.name != "." && it.name != ".." }
            folders = names.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            files = names.filterNot { it.isDirectory }.sortedBy { it.name.lowercase() }
            path = target
            loading = false
        } catch (failure: Exception) {
            Log.error("sftp", "listdir $target failed: $failure", failure)
            loading = false
            error = "$failure"
        }
    }

    LaunchedEffect(session.id) {
        try {
            val channel = session.sftp()
            sftp = channel
            listDir(channel.absolute("."))
        } catch (failure: Exception) {
            Log.error("sftp", "could not open SFTP channel: $failure", failure)
            loading = false
            error = "$failure"
        }
    }

    fun join(base: String, name: String): String =
        if (base.endsWith("/")) "$base$name" else "$base/$name"

    fun selectedEntries(): List<SftpEntry> =
        (folders + files).filter { selected.contains(it.name) }

    suspend fun downloadOne(entry: SftpEntry, directory: String?) {
        val channel = sftp ?: return
        transfer = Transfer(entry.name, entry.size ?: 0, 0, true)
        val bytes = channel.readBytes(join(path, entry.name)) { done ->
            transfer = Transfer(entry.name, entry.size ?: 0, done, true)
        }
        FilePick.saveFile(
            name = entry.name.substringBeforeLast('.', entry.name),
            extension = entry.name.substringAfterLast('.', ""),
            bytes = bytes,
        )
    }

    suspend fun upload() {
        val channel = sftp ?: return
        val picked = FilePick.pickFiles("Upload to $path")
        if (picked.isEmpty()) return

        for (file in picked) {
            transfer = Transfer(file.name, file.bytes.size.toLong(), 0, false)
            try {
                channel.writeBytes(join(path, file.name), file.bytes) { done ->
                    transfer = Transfer(file.name, file.bytes.size.toLong(), done, false)
                }
            } catch (failure: Exception) {
                Log.error("sftp", "Upload failed: $failure", failure)
                AppToasts.show("Upload failed: $failure")
                break
            }
        }
        transfer = null
        listDir(path)
    }

    suspend fun delete(entries: List<SftpEntry>) {
        val channel = sftp ?: return
        for (entry in entries) {
            try {
                val target = join(path, entry.name)
                if (entry.isDirectory) channel.rmdir(target) else channel.remove(target)
            } catch (failure: Exception) {
                Log.error("sftp", "${entry.name}: $failure", failure)
                AppToasts.show("${entry.name}: $failure")
                break
            }
        }
        listDir(path)
    }

    fun openEntry(entry: SftpEntry) {
        if (selectionMode) {
            if (!selected.remove(entry.name)) selected.add(entry.name)
            selectionMode = selected.isNotEmpty()
            return
        }
        if (entry.isDirectory) {
            scope.launch { listDir(join(path, entry.name)) }
            return
        }
        val channel = sftp ?: return
        if (isProbablyText(entry)) {
            navigator.push {
                RemoteFileEditor(
                    sftp = channel,
                    path = join(path, entry.name),
                    name = entry.name,
                )
            }
        } else {
            scope.launch {
                try {
                    downloadOne(entry, null)
                    AppToasts.show("Downloaded ${entry.name}")
                } catch (failure: Exception) {
                    Log.error("sftp", "Download failed: $failure", failure)
                    AppToasts.show("Download failed: $failure")
                } finally {
                    transfer = null
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selectionMode) {
                SelectionBar(
                    count = selected.size,
                    onCancel = {
                        selectionMode = false
                        selected.clear()
                    },
                    onSelectAll = {
                        val all = (folders + files).map { it.name }
                        if (selected.size == all.size) {
                            selected.clear()
                            selectionMode = false
                        } else {
                            selected.clear()
                            selected.addAll(all)
                            selectionMode = true
                        }
                    },
                    onDownload = {
                        scope.launch {
                            val entries = selectedEntries().filterNot { it.isDirectory }
                            val skipped = selected.size - entries.size
                            if (entries.isEmpty()) {
                                AppToasts.show("Select at least one file")
                                return@launch
                            }
                            val directory = if (appPlatform.isDesktop) {
                                FilePick.pickDirectory(
                                    "Download ${entries.size} file" +
                                        "${if (entries.size == 1) "" else "s"} to…",
                                )
                            } else {
                                null
                            }
                            var done = 0
                            for (entry in entries) {
                                try {
                                    downloadOne(entry, directory)
                                    done++
                                } catch (failure: Exception) {
                                    Log.error("sftp", "${entry.name}: $failure", failure)
                                    AppToasts.show("${entry.name}: $failure")
                                    break
                                }
                            }
                            transfer = null
                            selectionMode = false
                            selected.clear()
                            AppToasts.show(
                                "Downloaded $done file${if (done == 1) "" else "s"}" +
                                    if (skipped > 0) {
                                        " · $skipped folder${if (skipped == 1) "" else "s"} skipped"
                                    } else {
                                        ""
                                    },
                            )
                        }
                    },
                    onDelete = {
                        scope.launch {
                            val entries = selectedEntries()
                            if (entries.isEmpty()) return@launch
                            val confirmed = confirmDestructive(
                                title = "Delete ${entries.size} item" +
                                    "${if (entries.size == 1) "" else "s"}?",
                                message = "They will be removed on the remote system. " +
                                    "This cannot be undone.",
                            )
                            if (confirmed) delete(entries)
                        }
                    },
                )
            } else {
                Breadcrumbs(path) { target -> scope.launch { listDir(target) } }
            }

            transfer?.let { TransferBar(it) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                    error != null -> QEmptyView(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Could not read this directory",
                        message = error!!,
                    )
                    folders.isEmpty() && files.isEmpty() -> QEmptyView(
                        icon = Icons.Filled.FolderOpen,
                        title = "Empty directory",
                        message = "Nothing here yet.",
                    )
                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 10.dp, bottom = 170.dp),
                    ) {
                        if (folders.isNotEmpty()) {
                            EntrySection(
                                title = "Folders (${folders.size})",
                                entries = folders,
                                selected = selected,
                                selectionMode = selectionMode,
                                onOpen = ::openEntry,
                                onEnterSelection = { entry ->
                                    selectionMode = true
                                    selected.clear()
                                    selected.add(entry.name)
                                },
                                onAction = { action, entry ->
                                    runEntryAction(
                                        action, entry, scope, sftp, path, navigator,
                                        ::join, ::downloadOne, ::listDir,
                                        onSelect = {
                                            selectionMode = true
                                            selected.clear()
                                            selected.add(entry.name)
                                        },
                                        onTransferDone = { transfer = null },
                                    )
                                },
                            )
                        }
                        if (folders.isNotEmpty() && files.isNotEmpty()) Spacer(Modifier.height(14.dp))
                        if (files.isNotEmpty()) {
                            EntrySection(
                                title = "Files (${files.size})",
                                entries = files,
                                selected = selected,
                                selectionMode = selectionMode,
                                onOpen = ::openEntry,
                                onEnterSelection = { entry ->
                                    selectionMode = true
                                    selected.clear()
                                    selected.add(entry.name)
                                },
                                onAction = { action, entry ->
                                    runEntryAction(
                                        action, entry, scope, sftp, path, navigator,
                                        ::join, ::downloadOne, ::listDir,
                                        onSelect = {
                                            selectionMode = true
                                            selected.clear()
                                            selected.add(entry.name)
                                        },
                                        onTransferDone = { transfer = null },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!selectionMode) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FloatingAction(
                    icon = Icons.Outlined.CreateNewFolder,
                    background = colors.card,
                    foreground = colors.textSecondary,
                    enabled = sftp != null,
                ) {
                    scope.launch {
                        val channel = sftp ?: return@launch
                        val name = promptForText(
                            title = "New directory",
                            label = "Name",
                            actionLabel = "Create",
                        )
                        if (name.isNullOrEmpty()) return@launch
                        try {
                            channel.mkdir(join(path, name))
                            listDir(path)
                        } catch (failure: Exception) {
                            AppToasts.show("Could not create: $failure")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FloatingAction(
                    icon = Icons.Filled.Refresh,
                    background = colors.card,
                    foreground = colors.textSecondary,
                    enabled = true,
                ) { scope.launch { listDir(path) } }
                Spacer(Modifier.height(10.dp))
                FloatingAction(
                    icon = Icons.Filled.Upload,
                    background = colors.accent,
                    foreground = colors.onAccent,
                    enabled = sftp != null,
                ) { scope.launch { upload() } }
            }
        }
    }
}

private enum class EntryAction { EDIT, DOWNLOAD, RENAME, DELETE, SELECT }

private fun runEntryAction(
    action: EntryAction,
    entry: SftpEntry,
    scope: kotlinx.coroutines.CoroutineScope,
    sftp: SftpChannel?,
    path: String,
    navigator: com.example.ohmyssh.navigation.Navigator,
    join: (String, String) -> String,
    downloadOne: suspend (SftpEntry, String?) -> Unit,
    listDir: suspend (String) -> Unit,
    onSelect: () -> Unit,
    onTransferDone: () -> Unit,
) {
    val channel = sftp ?: return
    when (action) {
        EntryAction.EDIT -> navigator.push {
            RemoteFileEditor(sftp = channel, path = join(path, entry.name), name = entry.name)
        }
        EntryAction.DOWNLOAD -> scope.launch {
            try {
                downloadOne(entry, null)
                AppToasts.show("Downloaded ${entry.name}")
            } catch (failure: Exception) {
                AppToasts.show("Download failed: $failure")
            } finally {
                onTransferDone()
            }
        }
        EntryAction.RENAME -> scope.launch {
            val name = promptForText(
                title = "Rename",
                label = "New name",
                initial = entry.name,
                actionLabel = "Rename",
            )
            if (name.isNullOrEmpty() || name == entry.name) return@launch
            try {
                channel.rename(join(path, entry.name), join(path, name))
                listDir(path)
            } catch (failure: Exception) {
                AppToasts.show("Rename failed: $failure")
            }
        }
        EntryAction.DELETE -> scope.launch {
            val confirmed = confirmDestructive(
                title = if (entry.isDirectory) "Delete directory?" else "Delete file?",
                message = "${join(path, entry.name)} will be removed on the remote system. " +
                    "This cannot be undone.",
            )
            if (!confirmed) return@launch
            try {
                val target = join(path, entry.name)
                if (entry.isDirectory) channel.rmdir(target) else channel.remove(target)
                listDir(path)
            } catch (failure: Exception) {
                AppToasts.show("Delete failed: $failure")
            }
        }
        EntryAction.SELECT -> onSelect()
    }
}

@Composable
private fun EntrySection(
    title: String,
    entries: List<SftpEntry>,
    selected: List<String>,
    selectionMode: Boolean,
    onOpen: (SftpEntry) -> Unit,
    onEnterSelection: (SftpEntry) -> Unit,
    onAction: (EntryAction, SftpEntry) -> Unit,
) {
    GroupedCardList(
        title = title,
        items = entries,
        onTap = { entry -> { onOpen(entry) } },
        itemBuilder = { entry ->
            EntryRow(
                entry = entry,
                selected = selected.contains(entry.name),
                selectionMode = selectionMode,
                onAction = { action -> onAction(action, entry) },
                onLongPress = { onEnterSelection(entry) },
            )
        },
    )
}

internal fun isProbablyText(entry: SftpEntry): Boolean {
    val size = entry.size ?: 0
    if (size > kMaxEditableBytes) return false
    val name = entry.name
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return true
    return name.substring(dot + 1).lowercase() in setOf(
        "txt", "md", "log", "conf", "cfg", "ini", "yaml", "yml", "toml", "json", "xml",
        "sh", "bash", "zsh", "fish", "py", "pl", "rb", "lua", "js", "ts", "c", "h",
        "cpp", "hpp", "go", "rs", "java", "kt", "php", "sql", "env", "service", "socket",
        "timer", "rules", "list", "repo", "properties", "gitignore", "dockerignore",
        "csv", "tsv",
    )
}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val colors = appColors
    val segments = path.split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()

    LaunchedEffect(path) { scroll.animateScrollTo(scroll.maxValue) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = segments.isNotEmpty()) { onNavigate("/") }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "Root",
                tint = if (segments.isEmpty()) colors.accent else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        var cumulative = ""
        for ((index, segment) in segments.withIndex()) {
            cumulative += "/$segment"
            val isLast = index == segments.size - 1
            val target = cumulative
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isLast) { onNavigate(target) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    segment,
                    style = TextStyle(
                        color = if (isLast) colors.accent else colors.textSecondary,
                        fontWeight = if (isLast) FontWeight.W700 else FontWeight.W500,
                        fontSize = 13.5.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.accent.copy(alpha = 0.14f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            "$count selected",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W700,
            ),
        )
        IconButton(onClick = onSelectAll) {
            Icon(
                Icons.Filled.SelectAll,
                contentDescription = "Select all",
                tint = colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Download",
                tint = colors.accent,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = colors.danger,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun FloatingAction(
    icon: ImageVector,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onPressed: () -> Unit,
) {
    FloatingActionButton(
        onClick = { if (enabled) onPressed() },
        containerColor = if (enabled) background else background.copy(alpha = 0.5f),
        contentColor = if (enabled) foreground else foreground.copy(alpha = 0.4f),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TransferBar(transfer: Transfer) {
    val colors = appColors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${if (transfer.isDownload) "Downloading" else "Uploading"} " +
                    "${transfer.name}  ${formatBytes(transfer.done)}" +
                    if (transfer.total > 0) " / ${formatBytes(transfer.total)}" else "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textSecondary, fontSize = 12.5.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        val ratio = transfer.ratio
        if (ratio == null) {
            LinearProgressIndicator(
                color = colors.accent,
                trackColor = colors.divider,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            LinearProgressIndicator(
                progress = { ratio },
                color = colors.accent,
                trackColor = colors.divider,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: SftpEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onAction: (EntryAction) -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = appColors
    var menuOpen by remember { mutableStateOf(false) }

    val icon = when {
        entry.isDirectory -> Icons.Filled.Folder
        entry.isSymlink -> Icons.Filled.Link
        else -> iconForName(entry.name)
    }

    val parts = buildList {
        if (!entry.isDirectory) entry.size?.let { add(formatBytes(it)) }
        entry.modifyTimeSeconds?.let { add(formatTime(it)) }
        entry.mode?.let { add(formatMode(it)) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(entry.name) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textMuted,
                modifier = Modifier.padding(end = 6.dp).size(22.dp),
            )
        }
        QIconBadge(
            icon = icon,
            color = if (entry.isDirectory) colors.info else colors.textMuted,
            size = 32.dp,
            iconSize = 20.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (entry.isDirectory) FontWeight.W600 else FontWeight.W400,
                ),
            )
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    parts.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                    ),
                )
            }
        }
        if (entry.isDirectory && !selectionMode) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Actions",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.dialogBackground,
                ) {
                    if (!entry.isDirectory && isProbablyText(entry)) {
                        MenuRow(EntryAction.EDIT, Icons.Filled.EditNote, "Edit", colors.accent) {
                            menuOpen = false
                            onAction(it)
                        }
                    }
                    if (!entry.isDirectory) {
                        MenuRow(
                            EntryAction.DOWNLOAD,
                            Icons.Filled.Download,
                            "Download",
                            colors.accent,
                        ) {
                            menuOpen = false
                            onAction(it)
                        }
                    }
                    MenuRow(
                        EntryAction.SELECT,
                        Icons.Outlined.Checklist,
                        "Select",
                        colors.textSecondary,
                    ) {
                        menuOpen = false
                        onAction(it)
                    }
                    MenuRow(
                        EntryAction.RENAME,
                        Icons.Outlined.DriveFileRenameOutline,
                        "Rename",
                        colors.info,
                    ) {
                        menuOpen = false
                        onAction(it)
                    }
                    MenuRow(
                        EntryAction.DELETE,
                        Icons.Outlined.Delete,
                        "Delete",
                        colors.danger,
                        textColor = colors.danger,
                    ) {
                        menuOpen = false
                        onAction(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    action: EntryAction,
    icon: ImageVector,
    label: String,
    iconColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color? = null,
    onSelected: (EntryAction) -> Unit,
) {
    val colors = appColors
    DropdownMenuItem(
        text = {
            Text(label, color = textColor ?: colors.dialogText, fontSize = 14.sp)
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        },
        onClick = { onSelected(action) },
    )
}

private fun iconForName(name: String): ImageVector {
    val dot = name.lastIndexOf('.')
    val ext = if (dot < 0) "" else name.substring(dot + 1).lowercase()
    return when (ext) {
        "zip", "gz", "tar", "xz", "bz2", "7z" -> Icons.Filled.FolderZip
        "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Outlined.Image
        "sh", "bash", "zsh", "py", "pl", "rb" -> Icons.Filled.Code
        "conf", "cfg", "ini", "yaml", "yml", "toml", "json" -> Icons.Filled.Tune
        "log", "txt", "md" -> Icons.Outlined.Description
        "bin", "so", "o", "exe", "dll" -> Icons.Filled.DataObject
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun formatTime(epochSeconds: Long): String {
    var days = epochSeconds / 86400
    val secondsOfDay = epochSeconds % 86400
    var year = 1970
    while (true) {
        val length = if (isLeap(year)) 366 else 365
        if (days < length) break
        days -= length
        year++
    }
    val monthLengths = intArrayOf(
        31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
    )
    var month = 0
    while (days >= monthLengths[month]) {
        days -= monthLengths[month]
        month++
    }
    fun two(value: Long): String = value.toString().padStart(2, '0')
    return "$year-${two((month + 1).toLong())}-${two(days + 1)} " +
        "${two(secondsOfDay / 3600)}:${two((secondsOfDay % 3600) / 60)}"
}

private fun isLeap(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun formatMode(mode: Int): String {
    fun bit(on: Boolean, ch: Char): Char = if (on) ch else '-'
    return buildString {
        append(bit(mode and 0x100 != 0, 'r'))
        append(bit(mode and 0x080 != 0, 'w'))
        append(bit(mode and 0x040 != 0, 'x'))
        append(bit(mode and 0x020 != 0, 'r'))
        append(bit(mode and 0x010 != 0, 'w'))
        append(bit(mode and 0x008 != 0, 'x'))
        append(bit(mode and 0x004 != 0, 'r'))
        append(bit(mode and 0x002 != 0, 'w'))
        append(bit(mode and 0x001 != 0, 'x'))
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    val rounded = if (value >= 10) {
        value.toLong().toString()
    } else {
        val scaled = (value * 10).toLong()
        "${scaled / 10}.${scaled % 10}"
    }
    return "$rounded ${units[unit]}"
}
