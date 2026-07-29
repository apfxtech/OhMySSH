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
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.serial.serialPortName
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

@Composable
fun SessionsListPage() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val sessions = SessionManager.sessions

    QScaffold(
        appBar = {
            QPageAppBar(
                title = "Sessions",
                subtitle = if (sessions.isEmpty()) null else "${sessions.size} open",
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
        if (sessions.isEmpty()) {
            QEmptyView(
                icon = Icons.Filled.Terminal,
                title = "Nothing open",
                message = "Connect to a system to start a session.",
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 20.dp),
            ) {
                GroupedCardList(
                    items = sessions.toList(),
                    onTap = { session -> { navigator.push { SessionPage(session.id) } } },
                    itemBuilder = { session -> SessionRow(session) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: TerminalSession) {
    val colors = appColors
    val scope = rememberCoroutineScope()

    val detail = when (session) {
        is HostSession -> "${session.statusLabel} · ${session.host.endpoint}" +
            if (session.sftpTabOpen) " · SFTP" else ""
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
