package com.example.ohmyssh.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.fs.FileBrowsers
import com.example.ohmyssh.fs.LocalSource
import com.example.ohmyssh.fs.SftpSource
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.session.PaneGroup
import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.PaneWindow
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.session.sessionId
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.terminal.TerminalView
import com.example.ohmyssh.terminal.terminalPalette
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.ui.PaneDrag
import com.example.ohmyssh.ui.PaneDragGhost
import com.example.ohmyssh.ui.paneDropTarget
import com.example.ohmyssh.widgets.PickOption
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.pickFromList
import kotlinx.coroutines.launch

private val baudRates = listOf(
    300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600,
)

private val kSideBySideWidth = 700.dp

private val kTabHeight = 32.dp
private val kMinTabWidth = 136.dp

private fun sessionOf(ref: PaneRef): TerminalSession? =
    ref.sessionId()?.let { SessionManager.byId(it) }

@Composable
fun SessionPage(groupId: String) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    remember(groupId) { Workspace.activate(groupId) }

    val groups = Workspace.groups.toList()
    val windows = Workspace.windows
    val focused = Workspace.focusedWindow

    if (focused == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        QScaffold(appBar = { QPageAppBar(title = "Sessions") }) {
            QEmptyView(
                icon = Icons.Filled.Terminal,
                title = "No open sessions",
                message = "Tap a system to connect.",
            )
        }
        return
    }

    val session = sessionOf(focused.ref)

    LaunchedEffect(session?.id) {
        session?.let { SessionManager.activate(it.id) }
    }

    fun closeWindow(window: PaneWindow) {
        scope.launch {
            val target = sessionOf(window.ref)
            if (window.ref !is PaneRef.Shell || target == null) {
                Workspace.closeWindow(window.id)
                return@launch
            }

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
            SessionManager.close(target.id)
        }
    }

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() },
    ) {
        QScaffold(
            appBar = {
                QPageAppBar(
                    title = windowTitle(focused.ref),
                    subtitle = when {
                        session != null -> session.subtitle
                        focused.ref is PaneRef.Picker -> "Choose a system"
                        else -> "Local files"
                    },
                    statusColor = session?.let { statusColor(colors, it) },
                    actions = {
                        if (session != null && session.state == SessionState.CLOSED) {
                            QPageAppBarAction(
                                tooltip = "Reconnect",
                                icon = Icons.Filled.Autorenew,
                                native = true,
                                onPressed = { scope.launch { SessionManager.reconnect(session) } },
                            )
                        }
                        QPageAppBarAction(
                            tooltip = "New group",
                            icon = Icons.Filled.Add,
                            iconSize = 22.dp,
                            native = true,
                            onPressed = { Workspace.openGroup(PaneRef.Picker) },
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
                if (groups.sumOf { it.windows.size } > 1) {
                    TabStrip(
                        groups = groups,
                        activeGroupId = Workspace.activeGroupId,
                        focusedWindowId = focused.id,
                        onSelect = { window -> Workspace.focusWindow(window.id) },
                        onClose = ::closeWindow,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                WindowArea(windows = windows, focusedId = focused.id)
            }
        }
        PaneDragGhost(rootOrigin)
    }
}

@Composable
private fun ColumnScope.WindowArea(windows: List<PaneWindow>, focusedId: String) {
    BoxWithConstraints(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
    ) {
        if (windows.size < 2) {
            windows.firstOrNull()?.let { window ->
                WindowHost(window, focused = true, split = false)
            }
            return@BoxWithConstraints
        }

        if (maxWidth >= kSideBySideWidth) {
            Row(Modifier.fillMaxSize()) {
                windows.forEachIndexed { index, window ->
                    if (index > 0) Spacer(Modifier.width(kGroupedGap).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        WindowHost(window, window.id == focusedId, split = true)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                windows.forEachIndexed { index, window ->
                    if (index > 0) Spacer(Modifier.height(kGroupedGap).fillMaxWidth())
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        WindowHost(window, window.id == focusedId, split = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowHost(window: PaneWindow, focused: Boolean, split: Boolean) {
    val colors = appColors
    val dropping = PaneDrag.hovered == window.id
    val frame by animateColorAsState(
        targetValue = when {
            dropping -> colors.accent
            focused && split -> colors.accent.copy(alpha = 0.75f)
            else -> colors.divider
        },
        animationSpec = tween(180),
        label = "windowFrame",
    )
    val takesFiles = window.ref is PaneRef.Files || window.ref is PaneRef.Local

    Box(
        Modifier
            .fillMaxSize()
            .then(if (takesFiles) Modifier.paneDropTarget(window.id) else Modifier)
            .clip(RoundedCornerShape(kGroupedInnerRadius))
            .border(
                width = if (dropping) 2.dp else 1.dp,
                color = frame,
                shape = RoundedCornerShape(kGroupedInnerRadius),
            )
            .focusOnPress(focused) { Workspace.focusWindow(window.id) },
    ) {
        WindowBody(window)
    }
}

private fun Modifier.focusOnPress(focused: Boolean, onFocus: () -> Unit): Modifier =
    pointerInput(focused) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!focused && event.type == PointerEventType.Press) onFocus()
            }
        }
    }

@Composable
private fun WindowBody(window: PaneWindow) {
    val colors = appColors
    val scope = rememberCoroutineScope()
    val ref = window.ref

    if (ref is PaneRef.Picker) {
        PickerBody(window)
        return
    }

    if (ref is PaneRef.Local) {
        FileBrowserView(
            browser = FileBrowsers.of(window.id) { LocalSource() },
            windowId = window.id,
        )
        return
    }

    val session = sessionOf(ref) ?: return

    if (ref is PaneRef.Files) {
        if (session is HostSession && session.isConnected) {
            FileBrowserView(
                browser = FileBrowsers.of(window.id) { SftpSource(session) },
                windowId = window.id,
            )
        } else {
            ConnectView(session = session) { scope.launch { SessionManager.reconnect(session) } }
        }
        return
    }

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
                ConnectView(session = session) { scope.launch { SessionManager.reconnect(session) } }
            }
        }
    }
}

@Composable
private fun PickerBody(window: PaneWindow) {
    val navigator = LocalNavigator.current

    fun open(host: Host, files: Boolean) {
        if (VaultStore.identityFor(host) == null) {
            AppToasts.show("Assign a user to this system first", actionLabel = "Edit") {
                navigator.push { HostEditorPage(host) }
            }
            return
        }
        val session = if (files) {
            SessionManager.sessions.filterIsInstance<HostSession>()
                .firstOrNull { it.host.id == host.id && it.isConnected }
                ?: SessionManager.open(host)
        } else {
            SessionManager.open(host)
        }

        if (!files) {
            Workspace.resolve(window.id, PaneRef.Shell(session.id))
            return
        }

        Workspace.closeWindow(window.id)
        Workspace.openGroup(PaneRef.Files(session.id))
    }

    ConnectionPicker(
        onOpenShell = { host -> open(host, files = false) },
        onOpenSftp = { host -> open(host, files = true) },
        onOpenSerial = { entry ->
            val session = SessionManager.openSerial(entry)
            Workspace.resolve(window.id, PaneRef.Shell(session.id))
        },
    )
}

@Composable
private fun TabStrip(
    groups: List<PaneGroup>,
    activeGroupId: String?,
    focusedWindowId: String,
    onSelect: (PaneWindow) -> Unit,
    onClose: (PaneWindow) -> Unit,
) {
    val scroll = rememberScrollState()
    val groupGap = kGroupedGap * 3
    val tabCount = groups.sumOf { it.windows.size }

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
                val onScreen = group.id == activeGroupId
                group.windows.forEachIndexed { index, window ->
                    if (index > 0) Spacer(Modifier.width(kGroupedGap))
                    TabCard(
                        window = window,
                        width = tabWidth,
                        shape = clusterShape(index, group.windows.size),
                        focused = onScreen && window.id == focusedWindowId,
                        onScreen = onScreen,
                        onSelect = { onSelect(window) },
                        onClose = { onClose(window) },
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
    window: PaneWindow,
    width: Dp,
    shape: RoundedCornerShape,
    focused: Boolean,
    onScreen: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = appColors
    GroupedCard(
        shape = shape,
        onTap = onSelect,
        padding = PaddingValues(start = 9.dp, end = 1.dp),
        background = if (onScreen) {
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
        TabContent(window, focused, onScreen, onClose)
    }
}

@Composable
private fun TabContent(
    window: PaneWindow,
    focused: Boolean,
    onScreen: Boolean,
    onClose: () -> Unit,
) {
    val colors = appColors
    val foreground = if (onScreen) colors.textPrimary else colors.textSecondary

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            windowIcon(window.ref),
            contentDescription = windowKindLabel(window.ref),
            tint = if (focused) colors.accent else colors.textMuted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            windowTitle(window.ref),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = foreground,
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                fontWeight = if (focused) FontWeight.W700 else FontWeight.W500,
            ),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = colors.textMuted,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

private fun windowTitle(ref: PaneRef): String = when (ref) {
    is PaneRef.Picker -> "New window"
    is PaneRef.Local -> "This device"
    else -> sessionOf(ref)?.title ?: "Session"
}

private fun windowIcon(ref: PaneRef): ImageVector = when (ref) {
    is PaneRef.Picker -> Icons.Filled.Add
    is PaneRef.Local -> Icons.Filled.Computer
    is PaneRef.Files -> Icons.Filled.FolderOpen
    is PaneRef.Shell -> Icons.Outlined.Terminal
}

private fun windowKindLabel(ref: PaneRef): String = when (ref) {
    is PaneRef.Picker -> "Choose"
    is PaneRef.Local -> "Files"
    is PaneRef.Files -> "SFTP"
    is PaneRef.Shell -> "Shell"
}

internal fun statusColor(colors: QAppColors, session: TerminalSession): Color = when (session.state) {
    SessionState.CONNECTED -> colors.success
    SessionState.CONNECTING -> colors.warning
    SessionState.FAILED -> colors.danger
    else -> colors.textMuted
}
