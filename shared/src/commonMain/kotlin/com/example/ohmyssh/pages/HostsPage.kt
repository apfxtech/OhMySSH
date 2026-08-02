package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.ohmyssh.components.QFloatingAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.HostGroup
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.promptForText
import kotlinx.coroutines.launch

@Composable
fun HostsPage() {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        SerialRegistry.watch()
        onDispose { SerialRegistry.unwatch() }
    }

    val buckets = VaultStore.hostsByGroup()
    val serialDevices = SerialRegistry.entries

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

    QScaffold(
        floatingActions = {
            QFloatingAction(
                tooltip = "Scan network",
                icon = Icons.Outlined.Radar,
                onPressed = { navigator.push { NetworkPage() } },
            )
            QFloatingAction(
                tooltip = "New system",
                icon = Icons.Filled.Add,
                onPressed = { navigator.push { HostEditorPage(null) } },
                primary = true,
            )
        },
    ) {
        if (buckets.isEmpty() && serialDevices.isEmpty()) {
            QEmptyView(
                icon = Icons.Outlined.Dns,
                title = "No systems yet",
                message = "Add a system to connect over SSH and browse it over SFTP.",
                action = {
                    Button(
                        onClick = { navigator.push { HostEditorPage(null) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                    ) { Text("Add system") }
                },
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 9.dp, bottom = 20.dp),
            ) {
                for ((group, hosts) in buckets) {
                    Spacer(Modifier.height(5.dp))
                    GroupedCardList(
                        title = if (group == null) "Ungrouped" else null,
                        header = group?.let { saved -> { GroupHeader(saved) } },
                        items = hosts,
                        onTap = { host -> { open(host, files = false) } },
                        itemBuilder = { host -> HostRow(host) { open(host, files = true) } },
                    )
                    Spacer(Modifier.height(5.dp))
                }
                if (serialDevices.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    GroupedCardList(
                        title = "Serial devices",
                        items = serialDevices,
                        onTap = { entry ->
                            {
                                val session = SessionManager.openSerial(entry)
                                val group = Workspace.reveal(PaneRef.Shell(session.id))
                                navigator.push { SessionPage(group.id) }
                            }
                        },
                        itemBuilder = { entry -> SerialRow(entry) },
                    )
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: HostGroup) {
    val colors = appColors
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                scope.launch {
                    val name = promptForText(
                        title = "Rename group",
                        label = "Group name",
                        initial = group.name,
                    )
                    if (!name.isNullOrEmpty() && name != group.name) {
                        VaultStore.saveGroup(group.copy(name = name))
                    }
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.name,
            style = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600,
                color = colors.textSecondary,
            ),
        )
    }
}

@Composable
private fun HostRow(host: Host, onSftp: () -> Unit) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val identity = VaultStore.identityFor(host)
    val subtitle = if (identity == null) {
        host.endpoint
    } else {
        "${identity.username}@${host.endpoint}"
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QIconBadgeSvg(
            asset = osIconAsset(host.osId),
            color = Color(osColorValue(host.osId)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                host.displayLabel,
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
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 14.4.sp),
            )
        }
        IconButton(onClick = onSftp) {
            Icon(
                Icons.Filled.FolderOpen,
                contentDescription = "Open SFTP",
                tint = colors.accent,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = { navigator.push { HostEditorPage(host) } }) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.padding(start = 2.dp).size(16.dp),
        )
    }
}

@Composable
private fun SerialRow(entry: SerialDeviceEntry) {
    val colors = appColors
    val navigator = LocalNavigator.current

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QIconBadge(icon = Icons.Filled.Usb, color = colors.info)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
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
            Text(
                entry.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 14.4.sp),
            )
        }
        IconButton(onClick = { navigator.push { SerialEditorPage(entry) } }) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.padding(start = 2.dp).size(16.dp),
        )
    }
}
