package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
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
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.HostGroup
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.session.SessionManager
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

    fun connect(host: Host) {
        if (VaultStore.identityFor(host) == null) {
            AppToasts.show("Assign a user to this system first", actionLabel = "Edit") {
                navigator.push { HostEditorPage(host) }
            }
            return
        }
        val session = SessionManager.open(host)
        navigator.push { SessionPage(session.id) }
    }

    QScaffold(
        appBar = {
            QPageAppBar(
                title = "Systems",
                subtitle = "${VaultStore.hosts.size} saved",
                actions = {
                    QPageAppBarAction(
                        tooltip = "New group",
                        icon = Icons.Outlined.CreateNewFolder,
                        onPressed = {
                            scope.launch {
                                val name = promptForText(
                                    title = "New group",
                                    label = "Group name",
                                    actionLabel = "Create",
                                )
                                if (!name.isNullOrEmpty()) {
                                    VaultStore.saveGroup(HostGroup(id = newId(), name = name))
                                }
                            }
                        },
                    )
                    QPageAppBarAction(
                        tooltip = "New system",
                        icon = Icons.Filled.Add,
                        onPressed = { navigator.push { HostEditorPage(null) } },
                    )
                },
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
                        title = group?.name ?: "Ungrouped",
                        items = hosts,
                        onTap = { host -> { connect(host) } },
                        itemBuilder = { host -> HostRow(host) },
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
                                navigator.push { SessionPage(session.id) }
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
private fun HostRow(host: Host) {
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
