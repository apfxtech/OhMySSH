package com.example.ohmyssh.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCard
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.components.kGroupedGap
import com.example.ohmyssh.components.kGroupedHorizontalPadding
import com.example.ohmyssh.components.kGroupedInnerRadius
import com.example.ohmyssh.components.kGroupedOuterRadius
import com.example.ohmyssh.fs.FileBrowsers
import com.example.ohmyssh.fs.LocalSource
import com.example.ohmyssh.fs.SftpSource
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.session.LocalPane
import com.example.ohmyssh.session.PaneKind
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.session.paneKey
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.terminal.TerminalView
import com.example.ohmyssh.terminal.terminalPalette
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.PaneDrag
import com.example.ohmyssh.ui.PaneDragGhost
import com.example.ohmyssh.ui.paneDropTarget
import com.example.ohmyssh.widgets.PickOption
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.pickFromList
import kotlinx.coroutines.launch

private class TabItem(
    val key: String,
    val groupId: String,
    val kind: PaneKind,
    val title: String,
    val session: TerminalSession?,
    val local: LocalPane?,
)

private class TabGroup(val tabs: List<TabItem>)

private val baudRates = listOf(
    300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600,
)

private val kSideBySideWidth = 700.dp

private val kTabHeight = 42.dp
private val kMinTabWidth = 136.dp

@Composable
fun SessionPage(initialKey: String) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val groups = buildList {
        for (session in SessionManager.sessions) {
            val tabs = buildList {
                add(
                    TabItem(
                        key = paneKey(session.id, PaneKind.SHELL),
                        groupId = session.id,
                        kind = PaneKind.SHELL,
                        title = session.title,
                        session = session,
                        local = null,
                    ),
                )
                if (session is HostSession && session.sftpTabOpen) {
                    add(
                        TabItem(
                            key = paneKey(session.id, PaneKind.FILES),
                            groupId = session.id,
                            kind = PaneKind.FILES,
                            title = session.title,
                            session = session,
                            local = null,
                        ),
                    )
                }
            }
            add(TabGroup(tabs))
        }
        for (pane in Workspace.localPanes) {
            add(
                TabGroup(
                    listOf(
                        TabItem(
                            key = paneKey(pane.id, PaneKind.FILES),
                            groupId = pane.id,
                            kind = PaneKind.FILES,
                            title = "This device",
                            session = null,
                            local = pane,
                        ),
                    ),
                ),
            )
        }
    }
    val tabs = groups.flatMap { it.tabs }

    remember(initialKey) { Workspace.show(initialKey) }
    LaunchedEffect(tabs.map { it.key }) { Workspace.reconcile(tabs.map { it.key }) }

    val open = Workspace.slots.mapNotNull { key -> tabs.firstOrNull { it.key == key } }
    val focusedIndex = Workspace.focused.coerceIn(0, (open.size - 1).coerceAtLeast(0))
    val active = open.getOrNull(focusedIndex)

    LaunchedEffect(active?.session?.id) {
        active?.session?.let { SessionManager.activate(it.id) }
    }

    if (active == null) {
        QScaffold(appBar = { QPageAppBar(title = "Sessions") }) {
            QEmptyView(
                icon = Icons.Filled.Terminal,
                title = "No open sessions",
                message = "Tap a system to connect.",
            )
        }
        return
    }

    fun closeTab(tab: TabItem) {
        scope.launch {
            val target = tab.session
            if (tab.kind == PaneKind.FILES) {
                if (target is HostSession) {
                    target.sftpTabOpen = false
                    Workspace.hide(tab.key)
                    FileBrowsers.forget(tab.key)
                } else {
                    tab.local?.let { Workspace.closeLocal(it.id) }
                }
                if (tabs.size <= 1) navigator.pop()
                return@launch
            }

            target ?: return@launch
            val confirmed = if (target.isConnected) {
                confirmDestructive(
                    title = "Close session?",
                    message = "The connection to ${target.title} will be dropped.",
                    actionLabel = "Close",
                )
            } else {
                true
            }
            if (!confirmed) return@launch

            Workspace.hide(paneKey(target.id, PaneKind.SHELL))
            Workspace.hide(paneKey(target.id, PaneKind.FILES))
            SessionManager.close(target.id)
            if (SessionManager.sessions.isEmpty() && Workspace.localPanes.isEmpty()) {
                navigator.pop()
            }
        }
    }

    fun splitPartner(): String {
        val target = active.session
        if (active.kind == PaneKind.SHELL &&
            target is HostSession &&
            target.isConnected &&
            !target.sftpTabOpen
        ) {
            target.sftpTabOpen = true
            return paneKey(target.id, PaneKind.FILES)
        }
        tabs.firstOrNull { it.key != active.key && !Workspace.slots.contains(it.key) }
            ?.let { return it.key }
        return paneKey(Workspace.openLocal().id, PaneKind.FILES)
    }

    val session = active.session

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() },
    ) {
        QScaffold(
            appBar = {
                QPageAppBar(
                    title = active.title,
                    subtitle = session?.subtitle ?: "Local files",
                    statusColor = session?.let { statusColor(colors, it) },
                    actions = {
                        if (session != null && session.state == SessionState.CLOSED) {
                            QPageAppBarAction(
                                tooltip = "Reconnect",
                                icon = Icons.Filled.Autorenew,
                                native = true,
                                onPressed = { scope.launch { session.connect() } },
                            )
                        }
                        if (session is HostSession && session.isConnected && !session.sftpTabOpen) {
                            QPageAppBarAction(
                                tooltip = "Open SFTP",
                                icon = Icons.Filled.FolderOpen,
                                native = true,
                                onPressed = {
                                    session.sftpTabOpen = true
                                    Workspace.show(paneKey(session.id, PaneKind.FILES))
                                },
                            )
                        }
                        QPageAppBarAction(
                            tooltip = if (Workspace.isSplit) "Single pane" else "Split view",
                            icon = if (Workspace.isSplit) {
                                Icons.Filled.CloseFullscreen
                            } else {
                                Icons.Filled.VerticalSplit
                            },
                            native = true,
                            onPressed = {
                                if (Workspace.isSplit) {
                                    Workspace.unsplit()
                                } else {
                                    Workspace.showBeside(splitPartner())
                                }
                            },
                        )
                        QPageAppBarAction(
                            tooltip = "New session",
                            icon = Icons.Filled.Add,
                            iconSize = 22.dp,
                            native = true,
                            onPressed = { navigator.pop() },
                        )
                        if (session is SerialSession) {
                            QPageAppBarAction(
                                tooltip = "Baud rate",
                                icon = Icons.Filled.Speed,
                                native = true,
                                onPressed = {
                                    scope.launch {
                                        val picked = pickFromList(
                                            title = "Baud rate",
                                            current = session.device.baudRate,
                                            options = baudRates.map { PickOption(it, "$it") },
                                        ) ?: return@launch
                                        session.applySettings(
                                            session.device.copy(baudRate = picked.value),
                                        )
                                    }
                                },
                            )
                        } else if (session is HostSession) {
                            QPageAppBarAction(
                                tooltip = "System info",
                                icon = Icons.Filled.Speed,
                                native = true,
                                onPressed = { scope.launch { showSessionInfoDialog(session) } },
                            )
                        }
                    },
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(8.dp))
                TabStrip(
                    groups = groups,
                    visible = Workspace.slots.toList(),
                    focusedKey = active.key,
                    onSelect = { tab -> Workspace.show(tab.key) },
                    onSplit = { tab -> Workspace.showBeside(tab.key) },
                    onClose = ::closeTab,
                )
                Spacer(Modifier.height(8.dp))
                PaneArea(open = open, focused = focusedIndex)
            }
        }
        PaneDragGhost(rootOrigin)
    }
}

@Composable
private fun ColumnScope.PaneArea(open: List<TabItem>, focused: Int) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        if (open.size < 2) {
            open.firstOrNull()?.let { tab -> PaneHost(tab, index = 0, focused = true, split = false) }
            return@BoxWithConstraints
        }

        if (maxWidth >= kSideBySideWidth) {
            Row(Modifier.fillMaxSize()) {
                open.forEachIndexed { index, tab ->
                    if (index > 0) Spacer(Modifier.width(kGroupedGap).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        PaneHost(tab, index, index == focused, split = true)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                open.forEachIndexed { index, tab ->
                    if (index > 0) Spacer(Modifier.height(kGroupedGap).fillMaxWidth())
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PaneHost(tab, index, index == focused, split = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneHost(tab: TabItem, index: Int, focused: Boolean, split: Boolean) {
    val colors = appColors
    val dropping = PaneDrag.hovered == tab.key
    val highlight by animateFloatAsState(
        targetValue = when {
            dropping -> 1f
            focused && split -> 0.55f
            else -> 0f
        },
        animationSpec = tween(180),
        label = "paneHighlight",
    )

    Column(
        Modifier
            .fillMaxSize()
            .then(if (tab.kind == PaneKind.FILES) Modifier.paneDropTarget(tab.key) else Modifier)
            .then(
                if (split || dropping) {
                    Modifier
                        .clip(RoundedCornerShape(kGroupedInnerRadius))
                        .border(
                            width = if (dropping) 2.dp else 1.dp,
                            color = colors.accent.copy(alpha = highlight),
                            shape = RoundedCornerShape(kGroupedInnerRadius),
                        )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { Workspace.focus(index) },
    ) {
        if (split) PaneCaption(tab, focused)
        Box(Modifier.weight(1f).fillMaxWidth()) { PaneBody(tab) }
    }
}

@Composable
private fun PaneCaption(tab: TabItem, focused: Boolean) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(
                if (focused) colors.accent.copy(alpha = 0.12f) else colors.card.copy(alpha = 0.6f),
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tabIcon(tab),
            contentDescription = null,
            tint = if (focused) colors.accent else colors.textMuted,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${tab.title} · ${tabKindLabel(tab)}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = if (focused) colors.textPrimary else colors.textMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W600,
            ),
        )
    }
}

@Composable
private fun PaneBody(tab: TabItem) {
    val colors = appColors
    val session = tab.session
    val scope = rememberCoroutineScope()

    if (tab.kind == PaneKind.FILES) {
        when {
            session == null -> FileBrowserView(
                browser = FileBrowsers.of(tab.key) { LocalSource() },
                paneKey = tab.key,
            )
            session is HostSession && session.isConnected -> FileBrowserView(
                browser = FileBrowsers.of(tab.key) { SftpSource(session) },
                paneKey = tab.key,
            )
            else -> ConnectView(session = session) { scope.launch { session.connect() } }
        }
        return
    }

    session ?: return
    val connecting = session.state == SessionState.CONNECTING ||
        session.state == SessionState.FAILED ||
        session.state == SessionState.IDLE

    Box(Modifier.fillMaxSize()) {
        TerminalView(
            terminal = session.terminal,
            palette = terminalPalette(
                isDark = colors.isDark,
                cursor = colors.terminalCursor,
                selection = colors.terminalSelection,
                foreground = colors.terminalForeground,
                background = colors.terminalBackground,
            ),
            modifier = Modifier.fillMaxSize(),
            readOnly = !session.isConnected,
            autofocus = session.isConnected,
        )
        if (connecting) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                ConnectView(session = session) { scope.launch { session.connect() } }
            }
        }
    }
}

@Composable
private fun TabStrip(
    groups: List<TabGroup>,
    visible: List<String>,
    focusedKey: String,
    onSelect: (TabItem) -> Unit,
    onSplit: (TabItem) -> Unit,
    onClose: (TabItem) -> Unit,
) {
    val scroll = rememberScrollState()
    val groupGap = kGroupedGap * 3
    val tabCount = groups.sumOf { it.tabs.size }

    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = kGroupedHorizontalPadding),
    ) {
        val gaps = kGroupedGap * (tabCount - groups.size).coerceAtLeast(0) +
            groupGap * (groups.size - 1).coerceAtLeast(0)
        val even = if (tabCount > 0) (maxWidth - gaps) / tabCount else kMinTabWidth
        val tabWidth = if (even >= kMinTabWidth) even else kMinTabWidth

        Row(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) Spacer(Modifier.width(groupGap))
                group.tabs.forEachIndexed { index, tab ->
                    if (index > 0) Spacer(Modifier.width(kGroupedGap))
                    TabCard(
                        tab = tab,
                        width = tabWidth,
                        shape = clusterShape(index, group.tabs.size),
                        focused = tab.key == focusedKey,
                        showing = visible.contains(tab.key),
                        onSelect = { onSelect(tab) },
                        onSplit = { onSplit(tab) },
                        onClose = { onClose(tab) },
                    )
                }
            }
        }
    }
}

private fun clusterShape(index: Int, count: Int): RoundedCornerShape {
    fun corner(outer: Boolean): Dp = if (outer) kGroupedOuterRadius else kGroupedInnerRadius
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = corner(first),
        bottomStart = corner(first),
        topEnd = corner(last),
        bottomEnd = corner(last),
    )
}

@Composable
private fun TabCard(
    tab: TabItem,
    width: Dp,
    shape: RoundedCornerShape,
    focused: Boolean,
    showing: Boolean,
    onSelect: () -> Unit,
    onSplit: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = appColors
    GroupedCard(
        shape = shape,
        onTap = onSelect,
        onLongPress = onSplit,
        padding = PaddingValues(start = 9.dp, end = 1.dp),
        background = if (showing) {
            {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.accent.copy(alpha = if (focused) 0.16f else 0.07f)),
                )
            }
        } else {
            null
        },
        modifier = Modifier.width(width).height(kTabHeight),
    ) {
        TabContent(tab, focused, showing, onClose)
    }
}

@Composable
private fun TabContent(tab: TabItem, focused: Boolean, showing: Boolean, onClose: () -> Unit) {
    val colors = appColors
    val session = tab.session
    val foreground = if (showing) colors.textPrimary else colors.textSecondary

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            tabIcon(tab),
            contentDescription = null,
            tint = if (focused) colors.accent else colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tab.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = foreground,
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (focused) FontWeight.W700 else FontWeight.W500,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                session == null -> colors.info
                                session.isConnected -> colors.success
                                session.state == SessionState.CONNECTING -> colors.warning
                                else -> colors.textMuted
                            },
                        ),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    tabKindLabel(tab),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                    ),
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = colors.textMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun tabIcon(tab: TabItem): ImageVector = when {
    tab.kind == PaneKind.SHELL -> Icons.Outlined.Terminal
    tab.session == null -> Icons.Filled.Computer
    else -> Icons.Filled.FolderOpen
}

private fun tabKindLabel(tab: TabItem): String = when {
    tab.kind == PaneKind.SHELL -> "Shell"
    tab.session == null -> "Files"
    else -> "SFTP"
}

internal fun statusColor(colors: QAppColors, session: TerminalSession): Color = when (session.state) {
    SessionState.CONNECTED -> colors.success
    SessionState.CONNECTING -> colors.warning
    SessionState.FAILED -> colors.danger
    else -> colors.textMuted
}
