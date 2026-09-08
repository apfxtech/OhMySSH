package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.BrowseHeader
import com.example.ohmyssh.components.GridSectionTitle
import com.example.ohmyssh.components.HeaderAction
import com.example.ohmyssh.components.ItemCard
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.components.gridSection
import com.example.ohmyssh.components.withNetwork
import com.example.ohmyssh.data.ConnectionKind
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.ConnectionRecord
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.net.NetworkWatcher
import com.example.ohmyssh.platform.formatRelative
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.serial.serialPortName
import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.ui.WindowWidth
import com.example.ohmyssh.ui.windowWidthFor
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.SegmentOption
import com.example.ohmyssh.widgets.SegmentedChoice
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

private val kListPaneWidth = 360.dp

enum class SessionsSource(val label: String, val icon: ImageVector) {
    RECENT("Recent", Icons.Outlined.History),
    ARCHIVE("Archive", Icons.Outlined.Inventory2),
}

private sealed class SessionEntry(val key: String) {
    class Live(val session: TerminalSession, val record: ConnectionRecord?) : SessionEntry("s:${session.id}")

    class Past(val record: ConnectionRecord) : SessionEntry("r:${record.id}")

    fun matches(needle: String): Boolean {
        if (needle.isEmpty()) return true
        val words = when (this) {
            is Live -> listOf(session.title, session.subtitle, record?.target, record?.username)
            is Past -> listOf(record.label, record.target, record.username)
        }
        return words.any { it?.lowercase()?.contains(needle) == true }
    }
}

private class Section(val title: String, val entries: List<SessionEntry>)

/**
 * Open sessions and the history behind them as a list beside a detail pane on
 * a wide window, and a list that opens each item on a narrow one. The archive
 * is the same page over the records put aside for good.
 */
@Composable
fun SessionsListPage() {
    val navigator = LocalNavigator.current
    var source by rememberSaveable { mutableStateOf(SessionsSource.RECENT) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) { NetworkWatcher.refresh() }
    LaunchedEffect(source) {
        picking = false
        picked = emptySet()
    }

    val needle = query.trim().lowercase()
    fun past(records: List<ConnectionRecord>) =
        records.map { SessionEntry.Past(it) }.filter { it.matches(needle) }

    val sections = when (source) {
        SessionsSource.RECENT -> listOf(
            Section(
                "Open",
                SessionManager.sessions.map { SessionEntry.Live(it, HistoryStore.forSession(it.id)) }
                    .filter { it.matches(needle) },
            ),
            Section("History", past(HistoryStore.clientPast)),
            Section("Agent", past(HistoryStore.agentPast)),
        )
        SessionsSource.ARCHIVE -> listOf(
            Section("Archived", past(HistoryStore.archived.filter { !it.agent })),
            Section("Agent", past(HistoryStore.archived.filter { it.agent })),
        )
    }
    val entries = sections.flatMap { it.entries }
    val nothingAtAll = when (source) {
        SessionsSource.RECENT -> SessionManager.sessions.isEmpty() && HistoryStore.past.isEmpty()
        SessionsSource.ARCHIVE -> HistoryStore.archived.isEmpty()
    }
    val pickable = entries.filterIsInstance<SessionEntry.Past>().map { it.record }

    QScaffold {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = windowWidthFor(maxWidth)
            val twoPane = width == WindowWidth.EXPANDED
            val current = if (twoPane) entries.firstOrNull { it.key == selectedKey } ?: entries.firstOrNull() else null

            fun openEntry(entry: SessionEntry) {
                if (picking) {
                    if (entry is SessionEntry.Past) {
                        val id = entry.record.id
                        picked = if (id in picked) picked - id else picked + id
                    }
                    return
                }
                if (twoPane) {
                    selectedKey = entry.key
                    return
                }
                when (entry) {
                    is SessionEntry.Live -> {
                        val group = Workspace.reveal(PaneRef.Shell(entry.session.id))
                        navigator.push { SessionPage(group.id) }
                    }
                    is SessionEntry.Past -> navigator.push { CommandHistoryPage(entry.record.id) }
                }
            }

            Row(Modifier.fillMaxSize()) {
                Column((if (twoPane) Modifier.width(kListPaneWidth) else Modifier.weight(1f)).fillMaxHeight()) {
                    if (picking) {
                        PickBar(
                            source = source,
                            picked = pickable.filter { it.id in picked },
                            total = pickable.size,
                            onSelectAll = { picked = pickable.mapTo(HashSet()) { it.id } },
                            onDone = {
                                picking = false
                                picked = emptySet()
                            },
                        )
                    } else {
                        ListHeader(
                            source = source,
                            onSourceChange = { source = it },
                            query = query,
                            onQueryChange = { query = it },
                            canPick = pickable.isNotEmpty(),
                            onPick = { picking = true },
                        )
                    }
                    ListBody(
                        sections = sections,
                        source = source,
                        query = query,
                        empty = nothingAtAll,
                        grid = width == WindowWidth.MEDIUM,
                        selectedKey = current?.key,
                        picking = picking,
                        picked = picked,
                        onOpen = ::openEntry,
                    )
                }
                if (twoPane) {
                    VerticalDivider(color = appColors.divider)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (current) {
                            null -> if (!nothingAtAll) {
                                QEmptyView(
                                    icon = Icons.Outlined.Terminal,
                                    title = "Nothing selected",
                                    message = "Pick a session to see what ran over it.",
                                )
                            }
                            is SessionEntry.Live -> ConnectionDetail(current.record, current.session, header = true)
                            is SessionEntry.Past -> ConnectionDetail(current.record, null, header = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHeader(
    source: SessionsSource,
    onSourceChange: (SessionsSource) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    canPick: Boolean,
    onPick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BrowseHeader(
        title = null,
        wide = false,
        query = query,
        onQueryChange = onQueryChange,
        filter = {
            SegmentedChoice(
                options = SessionsSource.entries.map { SegmentOption(it, it.label, it.icon) },
                selected = source,
                onSelect = onSourceChange,
            )
        },
        actions = {
            if (canPick) {
                HeaderAction(label = "Select", icon = Icons.Outlined.Checklist, wide = false, onClick = onPick)
            }
            if (source == SessionsSource.RECENT && SessionManager.sessions.isNotEmpty()) {
                HeaderAction(label = "Close all", icon = Icons.Filled.LinkOff, wide = false, onClick = {
                    scope.launch {
                        val confirmed = confirmDestructive(
                            title = "Close all sessions?",
                            message = "Every open connection will be dropped.",
                            actionLabel = "Close all",
                        )
                        if (confirmed) SessionManager.closeAll()
                    }
                })
            }
            if (source == SessionsSource.RECENT && HistoryStore.past.isNotEmpty()) {
                HeaderAction(label = "Clear history", icon = Icons.Outlined.DeleteSweep, wide = false, onClick = {
                    scope.launch {
                        val confirmed = confirmDestructive(
                            title = "Clear connection history?",
                            message = "Every recent connection and the commands recorded over it will be " +
                                "forgotten. The archive stays.",
                            actionLabel = "Clear",
                        )
                        if (confirmed) HistoryStore.clearAll()
                    }
                })
            }
        },
    )
}

@Composable
private fun PickBar(
    source: SessionsSource,
    picked: List<ConnectionRecord>,
    total: Int,
    onSelectAll: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = appColors
    val scope = rememberCoroutineScope()
    val count = picked.size
    val toArchive = source == SessionsSource.RECENT

    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 6.dp).height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (count == 0) "Select connections" else "$count of $total",
            modifier = Modifier.weight(1f),
            style = TextStyle(color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.W600),
        )
        if (count < total) {
            HeaderAction(label = "Select all", icon = Icons.Outlined.SelectAll, wide = false, onClick = onSelectAll)
        }
        if (count > 0) {
            HeaderAction(
                label = if (toArchive) "Archive" else "Unarchive",
                icon = if (toArchive) Icons.Outlined.Archive else Icons.Outlined.Unarchive,
                wide = false,
                onClick = {
                    HistoryStore.archive(picked, archived = toArchive)
                    AppToasts.show(
                        if (toArchive) "$count archived" else "$count back in the recent list",
                    )
                    onDone()
                },
            )
            HeaderAction(label = "Forget", icon = Icons.Outlined.DeleteOutline, wide = false, onClick = {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = if (count == 1) "Forget this connection?" else "Forget $count connections?",
                        message = "The commands recorded over them go too.",
                        actionLabel = "Forget",
                    )
                    if (!confirmed) return@launch
                    HistoryStore.delete(picked)
                    onDone()
                }
            })
        }
        HeaderAction(label = "Done", icon = Icons.Filled.Close, wide = false, onClick = onDone)
    }
}

@Composable
private fun ListBody(
    sections: List<Section>,
    source: SessionsSource,
    query: String,
    empty: Boolean,
    grid: Boolean,
    selectedKey: String?,
    picking: Boolean,
    picked: Set<String>,
    onOpen: (SessionEntry) -> Unit,
) {
    if (empty) {
        when (source) {
            SessionsSource.RECENT -> QEmptyView(
                icon = Icons.Outlined.Terminal,
                title = "Nothing open",
                message = "Connect to a system to start a session.",
            )
            SessionsSource.ARCHIVE -> QEmptyView(
                icon = Icons.Outlined.Inventory2,
                title = "Nothing archived",
                message = "Archived connections stay for good and are never cleared.",
            )
        }
        return
    }

    if (sections.all { it.entries.isEmpty() }) {
        QEmptyView(
            icon = Icons.Filled.Search,
            title = "Nothing matches",
            message = "No session matches “${query.trim()}”.",
        )
        return
    }

    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(264.dp) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 8.dp else 6.dp),
    ) {
        for (section in sections) {
            section(section.title, section.entries, selectedKey, picking, picked, onOpen)
        }
    }
}

private fun LazyGridScope.section(
    title: String,
    entries: List<SessionEntry>,
    selectedKey: String?,
    picking: Boolean,
    picked: Set<String>,
    onOpen: (SessionEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    gridSection("title:$title") { GridSectionTitle(title, entries.size) }
    items(entries, key = { it.key }) { entry ->
        when (entry) {
            is SessionEntry.Live -> LiveCard(entry, !picking && entry.key == selectedKey) { onOpen(entry) }
            is SessionEntry.Past -> PastCard(
                record = entry.record,
                selected = if (picking) entry.record.id in picked else entry.key == selectedKey,
                pick = if (picking) entry.record.id in picked else null,
            ) { onOpen(entry) }
        }
    }
}

@Composable
private fun LiveCard(entry: SessionEntry.Live, selected: Boolean, onOpen: () -> Unit) {
    val colors = appColors
    val scope = rememberCoroutineScope()
    val session = entry.session
    val record = entry.record
    val target = when (session) {
        is HostSession -> listOfNotNull(session.identity?.username?.let { "$it@" }, session.host.endpoint).joinToString("")
        is SerialSession -> "${serialPortName(session.device.path)} · ${session.device.baudRate}"
        else -> session.subtitle
    }
    val detail = buildList {
        add(session.statusLabel)
        if (session.agentOwned) add("agent")
        val count = record?.commands?.size ?: 0
        if (count > 0) add(if (count == 1) "1 command" else "$count commands")
    }.joinToString(" · ")

    ItemCard(
        onClick = onOpen,
        selected = selected,
        title = session.title,
        subtitle = AnnotatedString(target),
        detail = withNetwork(detail, record?.networkId, record?.networkLabel),
        leading = { Badge(session is SerialSession, (session as? HostSession)?.let { it.profile?.osId ?: it.host.osId }) },
        titleTrailing = { Dot(statusColor(colors, session)) },
        trailing = {
            IconButton(onClick = { scope.launch { SessionManager.close(session.id) } }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textMuted, modifier = Modifier.size(15.dp))
            }
        },
    )
}

@Composable
private fun PastCard(record: ConnectionRecord, selected: Boolean, pick: Boolean?, onOpen: () -> Unit) {
    val colors = appColors
    val failed = record.outcome == ConnectionOutcome.FAILED
    val detail = buildList {
        add(formatRelative(record.startedAt))
        if (failed) add("failed")
        val count = record.commands.size
        if (count > 0) add(if (count == 1) "1 command" else "$count commands")
    }.joinToString(" · ")

    ItemCard(
        onClick = onOpen,
        selected = selected,
        title = record.label,
        subtitle = AnnotatedString(listOfNotNull(record.username?.let { "$it@" }, record.target).joinToString("")),
        detail = withNetwork(detail, record.networkId, record.networkLabel),
        leading = { Badge(record.kind == ConnectionKind.SERIAL, record.osId) },
        titleTrailing = if (failed) ({ Dot(colors.danger) }) else null,
        trailing = pick?.let {
            {
                Icon(
                    if (it) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (it) "Selected" else "Not selected",
                    tint = if (it) colors.accent else colors.textMuted,
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                )
            }
        },
    )
}

@Composable
private fun Badge(serial: Boolean, osId: String?) {
    if (serial) {
        QIconBadge(icon = Icons.Filled.Usb, color = appColors.info, size = 34.dp, iconSize = 19.dp)
    } else {
        QIconBadgeSvg(asset = osIconAsset(osId), color = Color(osColorValue(osId)), size = 34.dp, iconSize = 22.dp)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}
