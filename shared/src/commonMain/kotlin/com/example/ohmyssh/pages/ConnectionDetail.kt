package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.SearchField
import com.example.ohmyssh.components.withNetwork
import com.example.ohmyssh.data.ConnectionKind
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.ConnectionRecord
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.LoggedCommand
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.platform.formatSpan
import com.example.ohmyssh.platform.formatWallClock
import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.ButtonTone
import com.example.ohmyssh.widgets.SmallButton
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

/**
 * One connection, live or past: who and where, how it went, and every command
 * run over it. The detail pane of the sessions page on a wide window and the
 * body of [CommandHistoryPage] on a narrow one.
 */
@Composable
fun ConnectionDetail(
    record: ConnectionRecord?,
    session: TerminalSession?,
    header: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val clipboard = LocalClipboardManager.current
    val commands = record?.commands?.toList().orEmpty()
    var filter by rememberSaveable(record?.id) { mutableStateOf("") }
    val shown = if (filter.isBlank()) {
        commands
    } else {
        commands.filter { it.text.contains(filter.trim(), ignoreCase = true) }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        if (header) {
            item(key = "header") { DetailHeader(record, session) }
            item(key = "actions") {
                Spacer(Modifier.height(12.dp))
                DetailActions(record, session, commands)
                Spacer(Modifier.height(14.dp))
            }
        }
        if (record != null) {
            item(key = "stats") {
                StatTiles(record)
                if (record.error != null) {
                    Spacer(Modifier.height(6.dp))
                    ErrorCard(record.error.orEmpty())
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        item(key = "commands-title") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        commands.isEmpty() -> "Commands"
                        filter.isBlank() -> "${commands.size} commands"
                        else -> "${shown.size} of ${commands.size}"
                    },
                    style = TextStyle(color = colors.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.W700),
                )
                val dropped = record?.droppedCommands ?: 0
                if (dropped > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$dropped older dropped", style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp))
                }
                Spacer(Modifier.weight(1f))
                if (commands.size > 8) {
                    SearchField(filter, { filter = it }, Modifier.width(200.dp), placeholder = "Find a command")
                }
            }
        }
        if (commands.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (record?.isConnected == true || session?.isConnected == true) {
                        "Nothing run yet. Commands show up here as they are entered."
                    } else {
                        "No commands were entered over this connection."
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = TextStyle(color = colors.textMuted, fontSize = 12.5.sp),
                )
            }
        } else if (shown.isEmpty()) {
            item(key = "no-match") {
                Text(
                    "Nothing matches “${filter.trim()}”.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = TextStyle(color = colors.textMuted, fontSize = 12.5.sp),
                )
            }
        }
        items(shown, key = { it.at.toString() + it.text.hashCode() }) { command ->
            CommandCard(command) {
                clipboard.setText(AnnotatedString(command.text))
                AppToasts.show("Copied")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DetailHeader(record: ConnectionRecord?, session: TerminalSession?) {
    val colors = appColors
    val title = session?.title ?: record?.label ?: "Session"
    val serial = session is com.example.ohmyssh.serial.SerialSession || record?.kind == ConnectionKind.SERIAL
    val osId = (session as? HostSession)?.let { it.profile?.osId ?: it.host.osId } ?: record?.osId
    val target = buildString {
        record?.username?.let { append("$it@") }
        append(record?.target ?: session?.subtitle ?: "")
    }
    val (statusText, statusColor) = status(colors, record, session)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (serial) {
            QIconBadge(icon = Icons.Filled.Usb, color = colors.info, size = 40.dp, iconSize = 22.dp)
        } else {
            QIconBadgeSvg(asset = osIconAsset(osId), color = Color(osColorValue(osId)), size = 40.dp, iconSize = 26.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.W700),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                withNetwork(target, record?.networkId, record?.networkLabel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(6.dp))
            Text(statusText, style = TextStyle(color = statusColor, fontSize = 11.5.sp, fontWeight = FontWeight.W700))
        }
    }
}

private fun status(
    colors: QAppColors,
    record: ConnectionRecord?,
    session: TerminalSession?,
): Pair<String, Color> = when {
    session != null -> session.statusLabel to statusColor(colors, session)
    record == null -> "Gone" to colors.textMuted
    record.outcome == ConnectionOutcome.FAILED -> "Failed" to colors.danger
    record.isConnected -> "Connected" to colors.success
    else -> "Closed" to colors.textMuted
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(record: ConnectionRecord?, session: TerminalSession?, commands: List<LoggedCommand>) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val host = record?.hostId?.let { id -> VaultStore.hosts.firstOrNull { it.id == id } }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (session != null) {
            SmallButton("Open", Icons.Outlined.OpenInNew, ButtonTone.FILLED) {
                val group = Workspace.reveal(PaneRef.Shell(session.id))
                navigator.push { SessionPage(group.id) }
            }
            if (session.state == SessionState.CLOSED || session.state == SessionState.FAILED) {
                SmallButton("Reconnect", Icons.Outlined.Autorenew) {
                    scope.launch { SessionManager.reconnect(session) }
                }
            }
            if (session is HostSession && session.isConnected) {
                SmallButton("System info", Icons.Outlined.Speed, ButtonTone.NEUTRAL) {
                    scope.launch { showSessionInfoDialog(session) }
                }
            }
        } else if (host != null) {
            SmallButton("Connect again", Icons.Outlined.Terminal, ButtonTone.FILLED) {
                if (VaultStore.identityFor(host) == null) {
                    AppToasts.show("Assign a user to this system first")
                    return@SmallButton
                }
                val opened = SessionManager.open(host)
                val group = Workspace.openGroup(PaneRef.Shell(opened.id))
                navigator.push { SessionPage(group.id) }
            }
        }
        if (commands.isNotEmpty()) {
            SmallButton("Copy all", Icons.Outlined.ContentCopy, ButtonTone.NEUTRAL) {
                clipboard.setText(AnnotatedString(commands.joinToString("\n") { it.text }))
                AppToasts.show("${commands.size} commands copied")
            }
        }
        if (session != null) {
            SmallButton("Close", Icons.Outlined.Close, ButtonTone.DANGER) {
                scope.launch {
                    val confirmed = !session.isConnected || confirmDestructive(
                        title = "Close session?",
                        message = "The connection to ${session.title} will be dropped.",
                        actionLabel = "Close",
                    )
                    if (confirmed) SessionManager.close(session.id)
                }
            }
        } else if (record != null) {
            SmallButton("Forget", Icons.Outlined.DeleteOutline, ButtonTone.DANGER) {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Forget this connection?",
                        message = "Its ${commands.size} recorded commands go with it.",
                        actionLabel = "Forget",
                    )
                    if (confirmed) HistoryStore.delete(record)
                }
            }
        }
    }
}

@Composable
private fun StatTiles(record: ConnectionRecord) {
    val colors = appColors
    val failed = record.outcome == ConnectionOutcome.FAILED
    val tiles = buildList {
        add(Triple("Started", formatWallClock(record.startedAt), colors.textPrimary))
        if (record.isConnected) {
            add(Triple("State", "Connected", colors.success))
        } else {
            add(Triple("Lasted", record.durationMs?.let { formatSpan(it) } ?: "—", colors.textPrimary))
            add(Triple("Ended", record.outcome.label, if (failed) colors.danger else colors.textPrimary))
        }
        add(Triple("Commands", "${record.commands.size}", colors.textPrimary))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, value, tone) in tiles) {
            Column(
                Modifier
                    .weight(1f)
                    .background(colors.card, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(label, style = TextStyle(color = colors.textMuted, fontSize = 10.5.sp))
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = tone, fontSize = 13.sp, fontWeight = FontWeight.W600),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    val colors = appColors
    SelectionContainer {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.danger.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            style = TextStyle(color = colors.danger, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

@Composable
private fun CommandCard(command: LoggedCommand, onCopy: () -> Unit) {
    val colors = appColors
    val failed = command.exitCode != null && command.failed

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .clickable(onClick = onCopy)
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (failed) colors.danger else colors.accent.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                command.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            val detail = buildList {
                add(formatWallClock(command.at))
                if (command.agent) add("agent")
                command.cwd?.let { add(it) }
                command.durationMs?.let { add(formatSpan(it)) }
            }.joinToString(" · ")
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 11.sp),
            )
        }
        if (failed) {
            Spacer(Modifier.width(8.dp))
            Text(
                "exit ${command.exitCode}",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.danger.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(color = colors.danger, fontSize = 10.5.sp, fontWeight = FontWeight.W700),
            )
        }
    }
}
