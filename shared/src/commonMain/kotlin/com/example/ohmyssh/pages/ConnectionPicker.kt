package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.QEmptyView

@Composable
fun ConnectionPicker(
    onOpenShell: (Host) -> Unit,
    onOpenSftp: (Host) -> Unit,
    onOpenSerial: (SerialDeviceEntry) -> Unit,
    onOpenLocal: () -> Unit,
) {
    val colors = appColors
    val navigator = LocalNavigator.current

    DisposableEffect(Unit) {
        SerialRegistry.watch()
        onDispose { SerialRegistry.unwatch() }
    }

    val buckets = VaultStore.hostsByGroup()
    val serialDevices = SerialRegistry.entries

    if (buckets.isEmpty() && serialDevices.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 9.dp, bottom = 20.dp),
        ) {
            QEmptyView(
                icon = Icons.Outlined.Dns,
                title = "No systems yet",
                message = "Add a system to open it here.",
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
            GroupedCardList(
                title = "This device",
                items = listOf(Unit),
                onTap = { { onOpenLocal() } },
                itemBuilder = { LocalFilesRow() },
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 9.dp, bottom = 20.dp),
    ) {
        for ((group, hosts) in buckets) {
            Spacer(Modifier.height(5.dp))
            GroupedCardList(
                title = group?.name ?: "Ungrouped",
                items = hosts,
                onTap = { host -> { onOpenShell(host) } },
                itemBuilder = { host -> PickerHostRow(host) { onOpenSftp(host) } },
            )
            Spacer(Modifier.height(5.dp))
        }
        Spacer(Modifier.height(5.dp))
        GroupedCardList(
            title = "This device",
            items = listOf(Unit),
            onTap = { { onOpenLocal() } },
            itemBuilder = { LocalFilesRow() },
        )
        Spacer(Modifier.height(5.dp))
        if (serialDevices.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            GroupedCardList(
                title = "Serial devices",
                items = serialDevices,
                onTap = { entry -> { onOpenSerial(entry) } },
                itemBuilder = { entry -> PickerSerialRow(entry) },
            )
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun PickerHostRow(host: Host, onSftp: () -> Unit) {
    val colors = appColors
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
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.padding(start = 2.dp).size(16.dp),
        )
    }
}

@Composable
private fun PickerSerialRow(entry: SerialDeviceEntry) {
    val colors = appColors

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
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.padding(start = 2.dp).size(16.dp),
        )
    }
}

@Composable
private fun LocalFilesRow() {
    val colors = appColors

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QIconBadge(icon = Icons.Filled.Folder, color = colors.accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Local files",
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
                "Browse this device and drag files across",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 14.4.sp),
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
