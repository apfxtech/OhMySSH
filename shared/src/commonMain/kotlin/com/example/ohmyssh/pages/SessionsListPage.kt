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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GridSectionTitle
import com.example.ohmyssh.components.HeaderAction
import com.example.ohmyssh.components.ItemCard
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.components.BrowseHeader
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
import com.example.ohmyssh.ui.WindowWidth
import com.example.ohmyssh.ui.windowWidthFor
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

private val kListPaneWidth = 360.dp

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

/**
 * Open sessions and the history behind them as a list beside a detail pane on
 * a wide window, and a list that opens each item on a narrow one.
 */
@Composable
fun SessionsListPage() {
    val navigator = LocalNavigator.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { NetworkWatcher.refresh() }

    val needle = query.trim().lowercase()
    val open = SessionManager.sessions.map { SessionEntry.Live(it, HistoryStore.forSession(it.id)) }
        .filter { it.matches(needle) }
    val past = HistoryStore.clientPast.map { SessionEntry.Past(it) }.filter { it.matches(needle) }
    val agent = HistoryStore.agentPast.map { SessionEntry.Past(it) }.filter { it.matches(needle) }
    val nothingAtAll = SessionManager.sessions.isEmpty() && HistoryStore.past.isEmpty()

    QScaffold {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = windowWidthFor(maxWidth)
            val twoPane = width == WindowWidth.EXPANDED
            val entries = open + past + agent
            val current = if (twoPane) entries.firstOrNull { it.key == selectedKey } ?: entries.firstOrNull() else null

            fun openEntry(entry: SessionEntry) {
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
                ListPane(
                    modifier = if (twoPane) Modifier.width(kListPaneWidth) else Modifier.weight(1f),
                    query = query,
                    onQueryChange = { query = it },
                    open = open,
                    past = past,
                    agent = agent,
                    empty = nothingAtAll,
                    grid = width == WindowWidth.MEDIUM,
                    selectedKey = current?.key,
                    onOpen = ::openEntry,
                )
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
private fun ListPane(
    modifier: Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    open: List<SessionEntry.Live>,
    past: List<SessionEntry.Past>,
    agent: List<SessionEntry.Past>,
    empty: Boolean,
    grid: Boolean,
    selectedKey: String?,
    onOpen: (SessionEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxHeight()) {
        BrowseHeader(
            title = null,
            wide = false,
            query = query,
            onQueryChange = onQueryChange,
            actions = {
                if (SessionManager.sessions.isNotEmpty()) {
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
                if (HistoryStore.past.isNotEmpty()) {
                    HeaderAction(label = "Clear history", icon = Icons.Outlined.DeleteSweep, wide = false, onClick = {
                        scope.launch {
                            val confirmed = confirmDestructive(
                                title = "Clear connection history?",
                                message = "Every past connection and the commands recorded over it will be forgotten.",
                                actionLabel = "Clear",
                            )
                            if (confirmed) HistoryStore.clearAll()
                        }
                    })
                }
            },
        )

        if (empty) {
            QEmptyView(
                icon = Icons.Outlined.Terminal,
                title = "Nothing open",
                message = "Connect to a system to start a session.",
            )
            return@Column
        }

        if (open.isEmpty() && past.isEmpty() && agent.isEmpty()) {
            QEmptyView(
                icon = Icons.Filled.Search,
                title = "Nothing matches",
                message = "No session matches “${query.trim()}”.",
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = if (grid) GridCells.Adaptive(264.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid) 8.dp else 6.dp),
        ) {
            section("Open", open, selectedKey, onOpen)
            section("History", past, selectedKey, onOpen)
            section("Agent", agent, selectedKey, onOpen)
        }
    }
}

private fun LazyGridScope.section(
    title: String,
    entries: List<SessionEntry>,
    selectedKey: String?,
    onOpen: (SessionEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    gridSection("title:$title") { GridSectionTitle(title, entries.size) }
    items(entries, key = { it.key }) { entry ->
        when (entry) {
            is SessionEntry.Live -> LiveCard(entry, entry.key == selectedKey) { onOpen(entry) }
            is SessionEntry.Past -> PastCard(entry.record, entry.key == selectedKey) { onOpen(entry) }
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
private fun PastCard(record: ConnectionRecord, selected: Boolean, onOpen: () -> Unit) {
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
