package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ohmyssh.components.GroupedCardGrid
import com.example.ohmyssh.components.GroupedCardList
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.ssh.HostMetrics
import com.example.ohmyssh.ssh.HostProfile
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.ui.Dialogs
import com.example.ohmyssh.widgets.QSecretText
import kotlin.time.Duration
import kotlinx.coroutines.launch

private class DetailRow(
    val label: String,
    val value: String,
    val mono: Boolean = false,
    val secret: Boolean = false,
)

suspend fun showSessionInfoDialog(session: HostSession) {
    Dialogs.show<Unit> { dismiss ->
        val colors = appColors
        val scope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }
        val profile = session.profile
        val metrics = profile?.metrics

        Dialog(onDismissRequest = { dismiss(Unit) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.background,
                modifier = Modifier.widthIn(max = 460.dp),
            ) {
                Column(Modifier.heightIn(max = 640.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QIconBadgeSvg(
                            asset = osIconAsset(profile?.osId),
                            color = Color(osColorValue(profile?.osId)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile?.osPretty ?: "Unknown system",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.W700,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                session.host.endpoint,
                                style = TextStyle(color = colors.textMuted, fontSize = 12.sp),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (refreshing) return@IconButton
                                scope.launch {
                                    refreshing = true
                                    try {
                                        session.refreshProfile()
                                    } catch (failure: Exception) {
                                        Log.error("probe", "refresh failed", failure)
                                        AppToasts.show("Probe failed: $failure")
                                    } finally {
                                        refreshing = false
                                    }
                                }
                            },
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(onClick = { dismiss(Unit) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 18.dp),
                    ) {
                        if (metrics != null) {
                            MetricGrid(metrics)
                            Spacer(Modifier.height(14.dp))
                        }
                        GroupedCardList(
                            title = "Details",
                            items = details(session, profile),
                            itemBuilder = { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        row.label,
                                        modifier = Modifier.width(92.dp),
                                        style = TextStyle(
                                            color = colors.textMuted,
                                            fontSize = 12.5.sp,
                                        ),
                                    )
                                    val valueStyle = TextStyle(
                                        color = colors.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontFamily = if (row.mono) {
                                            FontFamily.Monospace
                                        } else {
                                            FontFamily.Default
                                        },
                                    )
                                    if (row.secret) {
                                        QSecretText(
                                            row.value,
                                            style = valueStyle,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        SelectionContainer(Modifier.weight(1f)) {
                                            Text(row.value, style = valueStyle)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun details(session: HostSession, profile: HostProfile?): List<DetailRow> = buildList {
    profile?.hostname?.let { add(DetailRow("Hostname", it)) }
    add(DetailRow("Address", session.host.endpoint))
    session.identity?.let { add(DetailRow("User", it.username)) }
    profile?.kernel?.let { add(DetailRow("Kernel", it)) }
    profile?.arch?.let { add(DetailRow("Architecture", it)) }
    profile?.metrics?.cpuCount?.let { add(DetailRow("CPUs", "$it")) }
    profile?.metrics?.uptime?.let { add(DetailRow("Uptime", formatUptime(it))) }
    session.fingerprint?.let { add(DetailRow("Host key", it, mono = true, secret = true)) }
}

private fun formatUptime(duration: Duration): String {
    val totalMinutes = duration.inWholeMinutes
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    if (days > 0) return "${days}d ${hours}h"
    if (hours > 0) return "${hours}h ${minutes}m"
    return "${minutes}m"
}

@Composable
private fun MetricGrid(metrics: HostMetrics) {
    val tiles = buildList<@Composable () -> Unit> {
        if (metrics.load1 != null) {
            add {
                MetricTile(
                    icon = Icons.Filled.Speed,
                    label = "Load avg",
                    value = format2(metrics.load1),
                    caption = "${metrics.load5?.let { format2(it) } ?: "—"} · " +
                        (metrics.load15?.let { format2(it) } ?: "—"),
                    ratio = metrics.cpuCount?.let {
                        (metrics.load1 / it).coerceIn(0.0, 1.0).toFloat()
                    },
                )
            }
        } else if (metrics.cpuPercent != null) {
            add {
                MetricTile(
                    icon = Icons.Filled.Speed,
                    label = "CPU",
                    value = "${metrics.cpuPercent.toLong()}%",
                    ratio = (metrics.cpuPercent / 100).coerceIn(0.0, 1.0).toFloat(),
                )
            }
        }
        if (metrics.memTotalKb != null) {
            add {
                MetricTile(
                    icon = Icons.Filled.Memory,
                    label = "Memory",
                    value = metrics.memUsedRatio?.let { "${(it * 100).toLong()}%" }
                        ?: formatBytes(metrics.memTotalKb * 1024),
                    caption = formatBytes(metrics.memTotalKb * 1024),
                    ratio = metrics.memUsedRatio?.toFloat(),
                )
            }
        }
        if (metrics.diskTotalKb != null) {
            add {
                MetricTile(
                    icon = Icons.Filled.Storage,
                    label = "Disk /",
                    value = metrics.diskUsedRatio?.let { "${(it * 100).toLong()}%" }
                        ?: formatBytes(metrics.diskTotalKb * 1024),
                    caption = "${formatBytes((metrics.diskFreeKb ?: 0) * 1024)} free",
                    ratio = metrics.diskUsedRatio?.toFloat(),
                )
            }
        }
    }

    if (tiles.isEmpty()) return

    GroupedCardGrid(
        title = "Live",
        items = tiles,
        crossAxisCount = tiles.size.coerceIn(1, 3),
        mainAxisExtent = null,
        cardPadding = PaddingValues(12.dp),
        itemBuilder = { tile -> tile() },
    )
}

private fun format2(value: Double): String {
    val scaled = (value * 100).toLong()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    caption: String? = null,
    ratio: Float? = null,
) {
    val colors = appColors
    val level = ratio ?: 0f
    val tint = when {
        level > 0.9f -> colors.danger
        level > 0.7f -> colors.warning
        else -> colors.accent
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = TextStyle(color = colors.textMuted, fontSize = 11.sp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 19.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.W700,
            ),
        )
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 10.5.sp),
            )
        }
        if (ratio != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ratio },
                color = tint,
                trackColor = colors.divider,
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}
