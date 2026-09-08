package com.example.ohmyssh.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.QEmptyView
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

    val session = record.liveSessionId?.let { SessionManager.byId(it) }
    val commands = record.commands.toList()

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
                                clipboard.setText(AnnotatedString(commands.joinToString("\n") { it.text }))
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
                                        message = "Its ${commands.size} recorded commands go with it.",
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
        ConnectionDetail(record = record, session = session, header = false)
    }
}
