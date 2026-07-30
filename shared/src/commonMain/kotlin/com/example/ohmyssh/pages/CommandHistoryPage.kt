package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCard
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.components.groupedListShape
import com.example.ohmyssh.components.groupedRowShape
import com.example.ohmyssh.components.kGroupedGap
import com.example.ohmyssh.components.kGroupedHorizontalPadding
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.ConnectionRecord
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.LoggedCommand
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.platform.formatSpan
import com.example.ohmyssh.platform.formatWallClock
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

@Composable
fun CommandHistoryPage(recordId: String) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val record = HistoryStore.byId(recordId)
    if (record == null) {
        QScaffold(appBar = { QPageAppBar(title = "Commands") }) {
            QEmptyView(
                icon = Icons.Filled.Terminal,
                title = "Gone",
                message = "This connection is no longer in the history.",
            )
        }
        return
    }

    var filter by remember { mutableStateOf("") }
    val commands = record.commands.toList()
    val shown = remember(filter, commands.size) {
        if (filter.isBlank()) commands
        else commands.filter { it.text.contains(filter.trim(), ignoreCase = true) }
    }

    QScaffold(
        appBar = {
            QPageAppBar(
                title = record.label,
                subtitle = buildString {
                    record.username?.let { append("$it@") }
                    append(record.target)
                },
                statusColor = when {
                    record.isConnected -> colors.success
                    record.outcome == ConnectionOutcome.FAILED -> colors.danger
                    else -> null
                },
                actions = {
                    if (commands.isNotEmpty()) {
                        QPageAppBarAction(
                            tooltip = "Copy every command",
                            icon = Icons.Filled.ContentCopy,
                            onPressed = {
                                clipboard.setText(
                                    AnnotatedString(commands.joinToString("\n") { it.text }),
                                )
                                AppToasts.show("${commands.size} commands copied")
                            },
                        )
                    }
                    QPageAppBarAction(
                        tooltip = "Forget this connection",
                        icon = Icons.Filled.DeleteOutline,
                        onPressed = if (record.isLive) {
                            null
                        } else {
                            {
                                scope.launch {
                                    val confirmed = confirmDestructive(
                                        title = "Forget this connection?",
                                        message = "Its ${commands.size} recorded " +
                                            "commands go with it.",
                                        actionLabel = "Forget",
                                    )
                                    if (!confirmed) return@launch
                                    HistoryStore.delete(record)
                                    navigator.pop()
                                }
                            }
                        },
                    )
                },
            )
        },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Spacer(Modifier.height(14.dp))
                Summary(record)
            }

            if (commands.isEmpty()) {
                item {
                    QEmptyView(
                        icon = Icons.Filled.Terminal,
                        title = "Nothing run yet",
                        message = if (record.isConnected) {
                            "Commands show up here as they are entered."
                        } else {
                            "No commands were entered over this connection."
                        },
                    )
                }
                return@LazyColumn
            }

            if (commands.size > 8) {
                item {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.padding(horizontal = kGroupedHorizontalPadding)) {
                        QTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            label = "Filter",
                            hint = "Find a command",
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                GroupHeading(
                    text = if (filter.isBlank()) {
                        "${commands.size} commands"
                    } else {
                        "${shown.size} of ${commands.size}"
                    },
                    note = record.droppedCommands.takeIf { it > 0 }
                        ?.let { "$it older dropped" },
                )
            }

            if (shown.isEmpty()) {
                item {
                    Text(
                        "Nothing matches \"${filter.trim()}\".",
                        modifier = Modifier.padding(
                            horizontal = kGroupedHorizontalPadding + 12.dp,
                            vertical = 8.dp,
                        ),
                        style = TextStyle(color = colors.textMuted, fontSize = 12.5.sp),
                    )
                }
            }

            itemsIndexed(shown) { index, command ->
                if (index > 0) Spacer(Modifier.height(kGroupedGap))
                Box(Modifier.padding(horizontal = kGroupedHorizontalPadding)) {
                    CommandCard(
                        command = command,
                        shape = groupedListShape(index, shown.size),
                        onCopy = {
                            clipboard.setText(AnnotatedString(command.text))
                            AppToasts.show("Copied")
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GroupHeading(text: String, note: String?) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = kGroupedHorizontalPadding + 12.dp, end = kGroupedHorizontalPadding + 12.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600,
                color = colors.textSecondary,
            ),
        )
        if (note != null) {
            Spacer(Modifier.width(8.dp))
            Text(note, style = TextStyle(fontSize = 11.5.sp, color = colors.textMuted))
        }
    }
}

@Composable
private fun Summary(record: ConnectionRecord) {
    val colors = appColors
    val tiles = buildList<Pair<String, String>> {
        add("Started" to formatWallClock(record.startedAt))
        if (record.isConnected) {
            add("State" to "Connected")
        } else {
            add("Lasted" to (record.durationMs?.let { formatSpan(it) } ?: "—"))
        }
        add("Ended" to if (record.isConnected) "—" else record.outcome.label)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = kGroupedHorizontalPadding)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(kGroupedGap)) {
            tiles.forEachIndexed { index, (label, value) ->
                GroupedCard(
                    shape = groupedRowShape(index, tiles.size),
                    padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Column {
                        Text(
                            label,
                            style = TextStyle(color = colors.textMuted, fontSize = 10.5.sp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = if (record.outcome == ConnectionOutcome.FAILED &&
                                    label == "Ended"
                                ) {
                                    colors.danger
                                } else {
                                    colors.textPrimary
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                            ),
                        )
                    }
                }
            }
        }
        if (record.error != null) {
            Spacer(Modifier.height(kGroupedGap))
            GroupedCard(
                shape = RoundedCornerShape(12.dp),
                padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        record.error.orEmpty(),
                        style = TextStyle(
                            color = colors.danger,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: LoggedCommand,
    shape: RoundedCornerShape,
    onCopy: () -> Unit,
) {
    val colors = appColors
    val failed = command.exitCode != null && command.failed

    GroupedCard(
        shape = shape,
        onTap = onCopy,
        padding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
                val detail = commandDetail(command)
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        detail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.textMuted, fontSize = 11.sp),
                    )
                }
            }
            if (failed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "exit ${command.exitCode}",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.danger.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(
                        color = colors.danger,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.W700,
                    ),
                )
            }
        }
    }
}

private fun commandDetail(command: LoggedCommand): String = buildList {
    add(formatWallClock(command.at))
    command.cwd?.let { add(it) }
    command.durationMs?.let { add(formatSpan(it)) }
}.joinToString(" · ")
