package com.example.ohmyssh.pages

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QCol
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.components.QTableCellText
import com.example.ohmyssh.components.QTableHeader
import com.example.ohmyssh.components.QTableRow
import com.example.ohmyssh.components.layoutTableColumns
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.net.LanDevice
import com.example.ohmyssh.net.LanScanner
import com.example.ohmyssh.platform.AppPlatform
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.QEmptyView

private val NETWORK_COLUMNS = listOf(
    QCol("Host", 0.dp, sortKey = "host"),
    QCol("IPv4", 132.dp, sortKey = "ipv4", mono = true),
    QCol("IPv6", 230.dp, sortKey = "ipv6", mono = true, hideLevel = 2),
    QCol("MAC", 148.dp, sortKey = "mac", mono = true, hideLevel = 3),
    QCol("Ping", 66.dp, sortKey = "rtt", right = true, hideLevel = 1),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPage() {
    val colors = appColors
    val navigator = LocalNavigator.current

    var query by remember { mutableStateOf("") }
    var sortKey by remember { mutableStateOf("ipv4") }
    var sortAscending by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (!LanScanner.hasSwept) LanScanner.refresh()
    }

    val devices = LanScanner.devices
    val rows = remember(devices, query, sortKey, sortAscending) {
        sortDevices(devices.filter { matches(it, query) }, sortKey, sortAscending)
    }
    val hosts = VaultStore.hosts

    QScaffold(
        appBar = {
            QPageAppBar(
                title = "Network",
                subtitle = statusLine(),
                actions = {
                    ScanPill(scanning = LanScanner.scanning, onTap = { LanScanner.refresh() })
                    CountPill(shown = rows.size, total = devices.size)
                },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(colors.accent)) {
                SearchBar(query = query, onQueryChange = { query = it })
                if (LanScanner.scanning) {
                    LinearProgressIndicator(
                        progress = { LanScanner.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = colors.onAccent.copy(alpha = 0.9f),
                        trackColor = colors.onAccent.copy(alpha = 0.25f),
                    )
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = layoutTableColumns(NETWORK_COLUMNS, maxWidth, rows, ::cellValue)

                PullToRefreshBox(
                    isRefreshing = LanScanner.scanning,
                    onRefresh = { LanScanner.refresh() },
                    // The bar under the app bar already carries the sweep; a second
                    // spinner riding down with the gesture just doubles it up.
                    indicator = {},
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (rows.isNotEmpty()) {
                            QTableHeader(
                                columns = columns,
                                sortKey = sortKey,
                                sortAscending = sortAscending,
                                onSort = { key ->
                                    if (key == sortKey) {
                                        sortAscending = !sortAscending
                                    } else {
                                        sortKey = key
                                        sortAscending = true
                                    }
                                },
                            )
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (rows.isEmpty()) {
                                item {
                                    Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                                        EmptyState(query)
                                    }
                                }
                            }
                            items(rows, key = { it.ipv4 }) { device ->
                                val saved = savedHostFor(device, hosts)
                                QTableRow(
                                    columns = columns,
                                    tint = if (device.self) {
                                        colors.accent.copy(alpha = 0.1f).compositeOver(colors.card)
                                    } else {
                                        null
                                    },
                                    onTap = {
                                        navigator.push {
                                            HostEditorPage(saved, draft = draftFor(device))
                                        }
                                    },
                                ) { col ->
                                    Cell(col, device, saved)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(col: QCol, device: LanDevice, saved: Host?) {
    when (col.sortKey) {
        "host" -> HostCell(device, saved)
        "ipv6" -> QTableCellText(
            ipv6Text(device),
            mono = true,
            muted = device.ipv6.isEmpty(),
        )
        "mac" -> QTableCellText(device.mac ?: "—", mono = true, muted = device.mac == null)
        "rtt" -> QTableCellText(rttText(device), right = true, muted = true)
        else -> QTableCellText(device.ipv4, mono = true)
    }
}

@Composable
private fun HostCell(device: LanDevice, saved: Host?) {
    val colors = appColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (saved != null) {
            QIconBadgeSvg(
                asset = osIconAsset(saved.osId),
                color = Color(osColorValue(saved.osId)),
                size = 28.dp,
                iconSize = 16.dp,
                borderRadius = 7.dp,
            )
        } else {
            QIconBadge(
                icon = when {
                    device.self && appPlatform.isMobile -> Icons.Filled.Smartphone
                    device.self -> Icons.Filled.Computer
                    else -> Icons.Outlined.Lan
                },
                color = if (device.self) colors.accent else colors.textMuted,
                size = 28.dp,
                iconSize = 15.dp,
                borderRadius = 7.dp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                titleFor(device, saved),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W500,
                ),
            )
            Text(
                detailFor(device, saved),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val colors = appColors
    val onAccent = colors.onAccent

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 8.dp, bottom = 10.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(onAccent.copy(alpha = 0.16f))
                .border(1.dp, onAccent.copy(alpha = 0.28f), RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = onAccent.copy(alpha = 0.75f),
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search host, IP or MAC…",
                        style = TextStyle(color = onAccent.copy(alpha = 0.6f), fontSize = 13.sp),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = onAccent, fontSize = 13.sp),
                    cursorBrush = SolidColor(onAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = onAccent.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
    }
}

@Composable
private fun ScanPill(scanning: Boolean, onTap: () -> Unit) {
    val colors = appColors
    val angle = if (scanning) spinAngle() else 0f

    Box(
        Modifier
            .padding(end = 6.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.onAccent.copy(alpha = if (scanning) 0.88f else 0.22f))
            .then(
                if (scanning) {
                    Modifier
                } else {
                    Modifier.border(1.dp, colors.onAccent.copy(alpha = 0.26f), RoundedCornerShape(10.dp))
                },
            )
            .clickable(enabled = !scanning, onClick = onTap)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Sync,
            contentDescription = "Rescan the network",
            tint = if (scanning) colors.accent else colors.onAccent,
            modifier = Modifier.size(14.dp).rotate(angle),
        )
    }
}

@Composable
private fun CountPill(shown: Int, total: Int) {
    val colors = appColors
    Box(
        Modifier
            .padding(end = 6.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.onAccent.copy(alpha = 0.22f))
            .border(1.dp, colors.onAccent.copy(alpha = 0.26f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (shown < total) "$shown/$total" else "$total",
            style = TextStyle(
                color = colors.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                fontFamily = FontFamily.Default,
            ),
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    QEmptyView(
        icon = Icons.Outlined.WifiTethering,
        title = when {
            LanScanner.scanning -> "Sweeping the subnet…"
            query.isNotEmpty() -> "Nothing matches \"$query\""
            LanScanner.hasSwept -> "Nothing answered"
            else -> "Not scanned yet"
        },
        message = when {
            LanScanner.scanning -> "Devices appear as they answer."
            query.isNotEmpty() -> "Clear the search to see every device again."
            else -> "Pull down, or tap the sync button, to ping every address on this subnet."
        },
    )
}

@Composable
private fun spinAngle(): Float {
    val transition = rememberInfiniteTransition(label = "scan-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "scan-spin",
    )
    return angle
}

private val AppPlatform.isMobile: Boolean
    get() = this == AppPlatform.ANDROID || this == AppPlatform.IOS

@Composable
private fun statusLine(): String {
    val subnet = LanScanner.subnet
    val adapter = LanScanner.interfaceName?.ifEmpty { null }
    return when {
        LanScanner.scanning ->
            "${subnet ?: "Local network"} · ${LanScanner.probed}/${LanScanner.total} probed"
        subnet != null -> listOfNotNull(subnet, adapter).joinToString(" · ")
        else -> "Not scanned yet"
    }
}

private fun cellValue(col: QCol, device: LanDevice): String = when (col.sortKey) {
    "host" -> device.hostname ?: device.ipv4
    "ipv6" -> ipv6Text(device)
    "mac" -> device.mac ?: "—"
    "rtt" -> rttText(device)
    else -> device.ipv4
}

private fun ipv6Text(device: LanDevice): String {
    val first = device.ipv6.firstOrNull() ?: return "—"
    return if (device.ipv6.size == 1) first else "$first  +${device.ipv6.size - 1}"
}

private fun rttText(device: LanDevice): String = when {
    device.self -> "self"
    device.rttMs == null -> "—"
    device.rttMs == 0 -> "<1 ms"
    else -> "${device.rttMs} ms"
}

private fun titleFor(device: LanDevice, saved: Host?): String =
    saved?.displayLabel ?: device.shortName ?: "Unnamed device"

private fun detailFor(device: LanDevice, saved: Host?): String = when {
    device.self -> "This device"
    saved != null -> "Saved system"
    device.hostname != null -> device.hostname
    // Nothing answered a probe, so the row exists only because the address
    // resolution the sweep triggered came back with a MAC.
    device.rttMs == null -> "ARP only"
    else -> "No name"
}

private fun draftFor(device: LanDevice): Host = Host(
    id = newId(),
    label = device.shortName ?: "",
    hostname = device.ipv4,
)

private fun savedHostFor(device: LanDevice, hosts: List<Host>): Host? = hosts.firstOrNull { host ->
    val target = host.hostname.trim().lowercase()
    target.isNotEmpty() && (
        target == device.ipv4 ||
            target == device.hostname?.lowercase() ||
            target == device.shortName?.lowercase()
        )
}

private fun matches(device: LanDevice, query: String): Boolean {
    if (query.isEmpty()) return true
    val needle = query.trim().lowercase()
    return device.ipv4.contains(needle) ||
        device.mac?.contains(needle) == true ||
        device.hostname?.lowercase()?.contains(needle) == true ||
        device.ipv6.any { it.lowercase().contains(needle) }
}

private fun sortDevices(
    devices: List<LanDevice>,
    sortKey: String,
    ascending: Boolean,
): List<LanDevice> {
    // Rows with nothing in the sorted column sink to the bottom of an ascending
    // sort instead of crowding the top as empty strings.
    val sorted = when (sortKey) {
        "host" -> devices.sortedBy { it.hostname?.lowercase() ?: "￿" }
        "ipv6" -> devices.sortedBy { it.ipv6.firstOrNull() ?: "￿" }
        "mac" -> devices.sortedBy { it.mac ?: "￿" }
        "rtt" -> devices.sortedBy { it.rttMs ?: Int.MAX_VALUE }
        else -> devices.sortedBy { it.order }
    }
    return if (ascending) sorted else sorted.reversed()
}
