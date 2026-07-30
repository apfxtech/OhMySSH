package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCardList
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.ConnectionKind
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.ConnectionRecord
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.platform.formatRelative
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.serial.serialPortName
import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

@Composable
fun SessionsListPage() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val sessions = SessionManager.sessions
    val past = HistoryStore.past

    QScaffold(
        appBar = {
            QPageAppBar(
                title = "Sessions",
                subtitle = when {
                    sessions.isNotEmpty() -> "${sessions.size} open"
                    past.isNotEmpty() -> "${past.size} in history"
                    else -> null
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        QPageAppBarAction(
                            tooltip = "Close all",
                            icon = Icons.Filled.LinkOff,
                            onPressed = {
                                scope.launch {
                                    val confirmed = confirmDestructive(
                                        title = "Close all sessions?",
                                        message = "Every open connection will be dropped.",
                                        actionLabel = "Close all",
                                    )
                                    if (confirmed) SessionManager.closeAll()
                                }
                            },
                        )
                    }
                },
            )
        },
    ) {
        if (sessions.isEmpty() && past.isEmpty()) {
            QEmptyView(
                icon = Icons.Filled.Terminal,
                title = "Nothing open",
                message = "Connect to a system to start a session.",
            )
            return@QScaffold
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = 20.dp),
        ) {
            if (sessions.isNotEmpty()) {
                GroupedCardList(
                    title = if (past.isEmpty()) null else "Open",
                    items = sessions.toList(),
                    onTap = { session ->
                        {
                            val group = Workspace.reveal(PaneRef.Shell(session.id))
                            navigator.push { SessionPage(group.id) }
                        }
                    },
                    itemBuilder = { session -> SessionRow(session) },
                )
            }

            if (past.isNotEmpty()) {
                if (sessions.isNotEmpty()) Spacer(Modifier.height(18.dp))
                GroupedCardList(
                    title = "History",
                    items = past,
                    onTap = { record ->
                        { navigator.push { CommandHistoryPage(record.id) } }
                    },
                    itemBuilder = { record -> HistoryRow(record) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: TerminalSession) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val record = HistoryStore.forSession(session.id)

    val detail = when (session) {
        is HostSession -> "${session.statusLabel} · ${session.host.endpoint}"
        is SerialSession -> "${session.statusLabel} · " +
            "${serialPortName(session.device.path)} · ${session.device.baudRate}"
        else -> session.statusLabel
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (session is SerialSession) {
            QIconBadge(icon = Icons.Filled.Usb, color = colors.info)
        } else {
            val osId = (session as? HostSession)?.let { it.profile?.osId ?: it.host.osId }
            QIconBadgeSvg(asset = osIconAsset(osId), color = Color(osColorValue(osId)))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.5.sp,
                    lineHeight = 17.4.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor(colors, session)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 14.4.sp,
                    ),
                )
            }
        }
        if (record != null) {
            IconButton(onClick = { navigator.push { CommandHistoryPage(record.id) } }) {
                CommandCountIcon(record.commands.size)
            }
        }
        IconButton(onClick = { scope.launch { SessionManager.close(session.id) } }) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CommandCountIcon(count: Int) {
    val colors = appColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.History,
            contentDescription = "Commands",
            tint = if (count > 0) colors.accent else colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        if (count > 0) {
            Spacer(Modifier.width(3.dp))
            Text(
                "$count",
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.W700,
                ),
            )
        }
    }
}

@Composable
private fun HistoryRow(record: ConnectionRecord) {
    val colors = appColors

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (record.kind == ConnectionKind.SERIAL) {
            QIconBadge(icon = Icons.Filled.Usb, color = colors.info)
        } else {
            QIconBadgeSvg(
                asset = osIconAsset(record.osId),
                color = Color(osColorValue(record.osId)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.5.sp,
                    lineHeight = 17.4.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(outcomeColor(colors, record.outcome)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    connectionSubtitle(record),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 14.4.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        CommandCountIcon(record.commands.size)
        Spacer(Modifier.width(8.dp))
    }
}

private fun outcomeColor(colors: QAppColors, outcome: ConnectionOutcome): Color = when (outcome) {
    ConnectionOutcome.FAILED -> colors.danger
    ConnectionOutcome.OPEN -> colors.success
    ConnectionOutcome.DISCONNECTED -> colors.textMuted
}

private fun connectionSubtitle(record: ConnectionRecord): String = buildList {
    add(formatRelative(record.startedAt))
    if (record.outcome == ConnectionOutcome.FAILED) add("failed")
    add(record.target)
    val count = record.commands.size
    if (count > 0) add(if (count == 1) "1 command" else "$count commands")
}.joinToString(" · ")
