package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ohmyssh.components.BrowseGrid
import com.example.ohmyssh.components.BrowseHeader
import com.example.ohmyssh.components.CardEditButton
import com.example.ohmyssh.components.GridSectionTitle
import com.example.ohmyssh.components.ItemCard
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.gridSection
import com.example.ohmyssh.components.networkLabel
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.HostGroup
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.net.NetworkWatcher
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.SegmentOption
import com.example.ohmyssh.widgets.SegmentedChoice
import com.example.ohmyssh.widgets.promptForText
import kotlinx.coroutines.launch

enum class ConnectMode(val label: String, val icon: ImageVector) {
    SSH("SSH", Icons.Outlined.Terminal),
    SFTP("SFTP", Icons.Outlined.FolderOpen),
    SERIAL("Serial", Icons.Outlined.Usb),
}

private val kOneRowHeader = 960.dp

@Composable
fun SystemsBrowser(
    title: String?,
    mode: ConnectMode,
    onModeChange: (ConnectMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenHost: (Host, ConnectMode) -> Unit,
    onOpenSerial: (SerialDeviceEntry) -> Unit,
    actions: @Composable RowScope.(wide: Boolean) -> Unit,
) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val serialSupported = SerialRegistry.isSupported

    if (serialSupported) {
        DisposableEffect(Unit) {
            SerialRegistry.watch()
            onDispose { SerialRegistry.unwatch() }
        }
    }

    val current = if (!serialSupported && mode == ConnectMode.SERIAL) ConnectMode.SSH else mode
    val needle = query.trim().lowercase()
    val buckets = VaultStore.hostsByGroup()
        .mapValues { (group, hosts) -> hosts.filter { matches(it, group, needle) } }
        .filterValues { it.isNotEmpty() }
    val serialDevices = SerialRegistry.entries.filter { matches(it, needle) }
    val open = SessionManager.sessions
        .filterIsInstance<HostSession>()
        .filter { it.isConnected }
        .map { it.host.id }
        .toSet()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= kOneRowHeader
        Column(Modifier.fillMaxSize()) {
            BrowseHeader(
                title = title,
                wide = wide,
                query = query,
                onQueryChange = onQueryChange,
                filter = { oneRow ->
                    SegmentedChoice(
                        options = ConnectMode.entries
                            .filter { it != ConnectMode.SERIAL || serialSupported }
                            .map { SegmentOption(it, it.label, it.icon) },
                        selected = current,
                        onSelect = onModeChange,
                        modifier = if (oneRow) Modifier.width(270.dp) else Modifier.fillMaxWidth(),
                    )
                },
                actions = { actions(wide) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    current == ConnectMode.SERIAL && SerialRegistry.entries.isEmpty() -> Centered {
                        QEmptyView(
                            icon = Icons.Filled.Usb,
                            title = "No serial devices",
                            message = "A USB adapter or board shows up here as soon as it is plugged in.",
                        )
                    }
                    current == ConnectMode.SERIAL && serialDevices.isEmpty() -> NothingMatches(query)
                    current == ConnectMode.SERIAL -> BrowseGrid(wide) {
                        items(serialDevices, key = { it.device.id }) { entry ->
                            SerialCard(entry, onOpen = { onOpenSerial(entry) })
                        }
                    }
                    VaultStore.hosts.isEmpty() -> Centered {
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
                    }
                    buckets.isEmpty() -> NothingMatches(query)
                    else -> BrowseGrid(wide) {
                        for ((group, hosts) in buckets) {
                            gridSection(key = "group:${group?.id}") {
                                GroupTitle(group, hosts.size)
                            }
                            items(hosts, key = { it.id }) { host ->
                                HostCard(
                                    host = host,
                                    mode = current,
                                    open = host.id in open,
                                    onOpen = { onOpenHost(host, current) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun matches(host: Host, group: HostGroup?, needle: String): Boolean {
    if (needle.isEmpty()) return true
    val user = VaultStore.identityFor(host)
    return listOfNotNull(host.label, host.hostname, host.note, host.osPretty, group?.name, user?.username, user?.label)
        .any { it.lowercase().contains(needle) }
}

private fun matches(entry: SerialDeviceEntry, needle: String): Boolean {
    if (needle.isEmpty()) return true
    return listOfNotNull(entry.title, entry.subtitle, entry.device.note, entry.port.kind.label)
        .any { it.lowercase().contains(needle) }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun NothingMatches(query: String) {
    Centered {
        QEmptyView(
            icon = Icons.Filled.Search,
            title = "Nothing matches",
            message = "No entry matches “${query.trim()}”.",
        )
    }
}

@Composable
private fun GroupTitle(group: HostGroup?, count: Int) {
    val scope = rememberCoroutineScope()
    val modifier = if (group == null) {
        Modifier
    } else {
        Modifier
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
            .padding(horizontal = 4.dp)
    }
    GridSectionTitle(group?.name ?: "Ungrouped", count, modifier)
}

@Composable
private fun HostCard(host: Host, mode: ConnectMode, open: Boolean, onOpen: () -> Unit) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val identity = VaultStore.identityFor(host)
    val network = host.networks.firstOrNull { it == NetworkWatcher.current?.key }
        ?: host.networks.firstOrNull()

    ItemCard(
        onClick = onOpen,
        title = host.displayLabel,
        subtitle = AnnotatedString(
            if (identity == null) host.endpoint else "${identity.username}@${host.endpoint}",
        ),
        detail = hostDetail(host, network),
        leading = {
            QIconBadgeSvg(
                asset = osIconAsset(host.osId),
                color = Color(osColorValue(host.osId)),
                size = 34.dp,
                iconSize = 21.dp,
            )
        },
        titleTrailing = if (open) {
            { Box(Modifier.size(6.dp).clip(CircleShape).background(colors.success)) }
        } else {
            null
        },
        trailing = {
            if (mode == ConnectMode.SFTP) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            CardEditButton { navigator.push { HostEditorPage(host) } }
        },
    )
}

@Composable
private fun hostDetail(host: Host, network: String?): AnnotatedString = buildAnnotatedString {
    val os = host.osPretty
    if (os != null) append(os)
    if (network == null) {
        if (os == null) append("Never connected")
        return@buildAnnotatedString
    }
    if (os != null) append(" · ")
    if (NetworkWatcher.current?.key == network) {
        withStyle(SpanStyle(color = appColors.accent, fontWeight = FontWeight.W600)) {
            append(networkLabel(network))
        }
    } else {
        append(networkLabel(network))
    }
}

@Composable
private fun SerialCard(entry: SerialDeviceEntry, onOpen: () -> Unit) {
    val colors = appColors
    val navigator = LocalNavigator.current
    ItemCard(
        onClick = onOpen,
        title = entry.title,
        subtitle = AnnotatedString(entry.subtitle),
        detail = AnnotatedString(
            entry.port.kind.label + if (entry.saved) " · saved" else "",
        ),
        leading = {
            QIconBadge(
                icon = Icons.Filled.Usb,
                color = colors.info,
                size = 34.dp,
                iconSize = 20.dp,
            )
        },
        trailing = { CardEditButton { navigator.push { SerialEditorPage(entry) } } },
    )
}
