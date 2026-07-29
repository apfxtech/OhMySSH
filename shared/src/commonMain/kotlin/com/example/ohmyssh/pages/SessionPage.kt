package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCardGrid
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.terminal.TerminalView
import com.example.ohmyssh.terminal.terminalPalette
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.PickOption
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.pickFromList
import kotlinx.coroutines.launch

private enum class TabMode { TERMINAL, SFTP }

private class SessionTab(val session: TerminalSession, val mode: TabMode) {
    val key: String get() = "${session.id}:${mode.name.lowercase()}"
}

private val baudRates = listOf(
    300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600,
)

@Composable
fun SessionPage(sessionId: String) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    var activeKey by remember { mutableStateOf("$sessionId:terminal") }
    remember(sessionId) { SessionManager.activate(sessionId); sessionId }

    val tabs = buildList {
        for (session in SessionManager.sessions) {
            add(SessionTab(session, TabMode.TERMINAL))
            if (session is HostSession && session.sftpTabOpen) {
                add(SessionTab(session, TabMode.SFTP))
            }
        }
    }
    val active = tabs.firstOrNull { it.key == activeKey } ?: tabs.firstOrNull()

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

    val session = active.session

    QScaffold(
        appBar = {
            QPageAppBar(
                title = session.title,
                subtitle = session.subtitle,
                statusColor = statusColor(colors, session),
                actions = {
                    if (session.state == SessionState.CLOSED) {
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
                                activeKey = "${session.id}:sftp"
                            },
                        )
                    }
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
                tabs = tabs,
                activeKey = active.key,
                onSelect = { tab ->
                    activeKey = tab.key
                    SessionManager.activate(tab.session.id)
                },
                onClose = { tab ->
                    scope.launch {
                        val target = tab.session
                        if (tab.mode == TabMode.SFTP && target is HostSession) {
                            target.sftpTabOpen = false
                            if (activeKey == tab.key) activeKey = "${target.id}:terminal"
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
                        if (SessionManager.sessions.isEmpty()) {
                            navigator.pop()
                        } else {
                            activeKey = "${SessionManager.sessions.first().id}:terminal"
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                for (tab in tabs) {
                    val visible = tab.key == active.key
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (visible) Modifier else Modifier.size(0.dp)),
                    ) {
                        if (visible) TabBody(tab)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBody(tab: SessionTab) {
    val colors = appColors
    val session = tab.session
    val scope = rememberCoroutineScope()

    if (session.state == SessionState.CONNECTING ||
        session.state == SessionState.FAILED ||
        session.state == SessionState.IDLE
    ) {
        ConnectView(session = session) { scope.launch { session.connect() } }
        return
    }

    when {
        tab.mode == TabMode.TERMINAL -> TerminalView(
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
        )
        session is HostSession -> SftpView(session)
    }
}

@Composable
private fun TabStrip(
    tabs: List<SessionTab>,
    activeKey: String,
    onSelect: (SessionTab) -> Unit,
    onClose: (SessionTab) -> Unit,
) {
    val colors = appColors
    GroupedCardGrid(
        items = tabs,
        crossAxisCount = tabs.size.coerceAtLeast(1),
        mainAxisExtent = 42.dp,
        cardPadding = PaddingValues(start = 9.dp, end = 1.dp),
        onTap = { tab -> { onSelect(tab) } },
        backgroundBuilder = { tab ->
            if (tab.key == activeKey) {
                {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(colors.accent.copy(alpha = 0.16f)),
                    )
                }
            } else {
                null
            }
        },
        itemBuilder = { tab -> TabContent(tab, tab.key == activeKey) { onClose(tab) } },
    )
}

@Composable
private fun TabContent(tab: SessionTab, selected: Boolean, onClose: () -> Unit) {
    val colors = appColors
    val session = tab.session
    val foreground = if (selected) colors.textPrimary else colors.textSecondary

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (tab.mode == TabMode.SFTP) Icons.Filled.FolderOpen else Icons.Outlined.Terminal,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = foreground,
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
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
                                session.isConnected -> colors.success
                                session.state == SessionState.CONNECTING -> colors.warning
                                else -> colors.textMuted
                            },
                        ),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (tab.mode == TabMode.SFTP) "SFTP" else "Shell",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textMuted, fontSize = 10.sp, lineHeight = 11.sp),
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

internal fun statusColor(colors: QAppColors, session: TerminalSession): Color = when (session.state) {
    SessionState.CONNECTED -> colors.success
    SessionState.CONNECTING -> colors.warning
    SessionState.FAILED -> colors.danger
    else -> colors.textMuted
}
