package com.example.ohmyssh.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ohmyssh.components.HeaderAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.net.NetworkWatcher
import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ui.AppToasts

@Composable
fun HostsPage() {
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) { NetworkWatcher.refresh() }

    var mode by rememberSaveable { mutableStateOf(ConnectMode.SSH) }
    var query by rememberSaveable { mutableStateOf("") }

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
        val ref = if (files) PaneRef.Files(session.id) else PaneRef.Shell(session.id)
        val group = Workspace.openGroup(ref)
        navigator.push { SessionPage(group.id) }
    }

    QScaffold {
        SystemsBrowser(
            title = "Systems",
            mode = mode,
            onModeChange = { mode = it },
            query = query,
            onQueryChange = { query = it },
            onOpenHost = { host, chosen -> open(host, files = chosen == ConnectMode.SFTP) },
            onOpenSerial = { entry ->
                val session = SessionManager.openSerial(entry)
                val group = Workspace.reveal(PaneRef.Shell(session.id))
                navigator.push { SessionPage(group.id) }
            },
            actions = { wide ->
                HeaderAction(
                    label = "Scan network",
                    icon = Icons.Outlined.Radar,
                    wide = wide,
                    onClick = { navigator.push { NetworkPage() } },
                )
                HeaderAction(
                    label = "New system",
                    icon = Icons.Filled.Add,
                    wide = wide,
                    primary = true,
                    onClick = { navigator.push { HostEditorPage(null) } },
                )
            },
        )
    }
}
