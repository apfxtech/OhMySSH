package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.data.SerialFlowControl
import com.example.ohmyssh.data.SerialParity
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialPortInfo
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.serial.formatUsbId
import com.example.ohmyssh.serial.serialPortName
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.PickOption
import com.example.ohmyssh.widgets.QFormLabel
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.pickFromList
import kotlinx.coroutines.launch

private val commonBaudRates = listOf(
    300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SerialEditorPage(entry: SerialDeviceEntry) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val device = entry.device

    var label by rememberSaveable(device.id) { mutableStateOf(device.label) }
    var path by rememberSaveable(device.id) { mutableStateOf(device.path) }
    var baudRate by rememberSaveable(device.id) { mutableStateOf("${device.baudRate}") }
    var vendorId by rememberSaveable(device.id) { mutableStateOf(hex(device.vendorId)) }
    var productId by rememberSaveable(device.id) { mutableStateOf(hex(device.productId)) }
    var serialNumber by rememberSaveable(device.id) { mutableStateOf(device.serialNumber ?: "") }
    var note by rememberSaveable(device.id) { mutableStateOf(device.note ?: "") }
    var dataBits by rememberSaveable(device.id) { mutableStateOf(device.dataBits) }
    var stopBits by rememberSaveable(device.id) { mutableStateOf(device.stopBits) }
    var parity by rememberSaveable(device.id) { mutableStateOf(device.parity) }
    var flowControl by rememberSaveable(device.id) { mutableStateOf(device.flowControl) }
    var dtr by rememberSaveable(device.id) { mutableStateOf(device.dtr) }
    var rts by rememberSaveable(device.id) { mutableStateOf(device.rts) }

    suspend fun save() {
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) {
            AppToasts.show("The port path is required")
            return
        }
        val rate = baudRate.trim().toIntOrNull() ?: 0
        if (rate < 50 || rate > 4_000_000) {
            AppToasts.show("Baud rate must be 50–4000000")
            return
        }

        VaultStore.saveSerialDevice(
            SerialDevice(
                id = device.id,
                label = label.trim(),
                path = trimmedPath,
                baudRate = rate,
                dataBits = dataBits,
                stopBits = stopBits,
                parity = parity,
                flowControl = flowControl,
                dtr = dtr,
                rts = rts,
                vendorId = parseHex(vendorId),
                productId = parseHex(productId),
                serialNumber = serialNumber.trim().ifEmpty { null },
                byId = device.byId,
                hardware = device.hardware,
                note = note.trim().ifEmpty { null },
            ),
        )
        SerialRegistry.refresh()
        navigator.pop()
    }

    QScaffold(
        appBar = {
            QPageAppBar(
                title = if (entry.saved) "Edit device" else "New device",
                subtitle = serialPortName(device.path),
                actions = {
                    if (entry.saved) {
                        QPageAppBarAction(
                            tooltip = "Forget",
                            icon = Icons.Outlined.Delete,
                            onPressed = {
                                scope.launch {
                                    val confirmed = confirmDestructive(
                                        title = "Forget this device?",
                                        message = "${device.displayLabel} keeps working — only " +
                                            "its saved settings are removed.",
                                        actionLabel = "Forget",
                                    )
                                    if (confirmed) {
                                        VaultStore.deleteSerialDevice(device.id)
                                        SerialRegistry.refresh()
                                        navigator.pop()
                                    }
                                }
                            },
                        )
                    }
                    QPageAppBarAction(
                        tooltip = "Save",
                        icon = Icons.Filled.Check,
                        iconSize = 22.dp,
                        onPressed = { scope.launch { save() } },
                    )
                },
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 28.dp),
        ) {
            QFormLabel("Device")
            QTextField(
                value = label,
                onValueChange = { label = it },
                label = "Name",
                hint = entry.port.displayName,
                autofocus = !entry.saved,
            )
            Spacer(Modifier.height(10.dp))
            QTextField(
                value = path,
                onValueChange = { path = it },
                label = "Port",
                hint = "/dev/ttyUSB0, /dev/serial0 or COM3",
            )

            QFormLabel("Line")
            QTextField(
                value = baudRate,
                onValueChange = { baudRate = it },
                label = "Baud rate",
                digitsOnly = true,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (rate in commonBaudRates) {
                    AssistChip(
                        onClick = { baudRate = "$rate" },
                        label = { Text("$rate", fontSize = 12.sp, color = colors.textSecondary) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = colors.card),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = colors.divider,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PickerRow(
                icon = Icons.Filled.Tune,
                title = "Data bits: $dataBits",
                subtitle = "Eight is right for anything modern",
                onTap = {
                    scope.launch {
                        pickFromList(
                            title = "Data bits",
                            current = dataBits,
                            options = listOf(5, 6, 7, 8).map { PickOption(it, "$it") },
                        )?.let { dataBits = it.value }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            PickerRow(
                icon = Icons.Filled.MoreVert,
                title = "Stop bits: $stopBits",
                subtitle = "One, unless the device asks for two",
                onTap = {
                    scope.launch {
                        pickFromList(
                            title = "Stop bits",
                            current = stopBits,
                            options = listOf(1, 2).map { PickOption(it, "$it") },
                        )?.let { stopBits = it.value }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            PickerRow(
                icon = Icons.Filled.Rule,
                title = "Parity: ${parity.label}",
                subtitle = "None for 8N1",
                onTap = {
                    scope.launch {
                        pickFromList(
                            title = "Parity",
                            current = parity,
                            options = SerialParity.entries.map { PickOption(it, it.label) },
                        )?.let { parity = it.value }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            PickerRow(
                icon = Icons.Filled.SwapHoriz,
                title = "Flow control: ${flowControl.label}",
                subtitle = "Hardware handshaking, off by default",
                onTap = {
                    scope.launch {
                        pickFromList(
                            title = "Flow control",
                            current = flowControl,
                            options = SerialFlowControl.entries.map { PickOption(it, it.label) },
                        )?.let { flowControl = it.value }
                    }
                },
            )

            QFormLabel("Signals")
            SwitchRow(
                title = "Assert DTR",
                // Arduino and most ESP boards wire DTR to reset; dropping it
                // keeps the board from rebooting when the console attaches.
                subtitle = "Boards wired for auto-reset reboot when this is on",
                value = dtr,
                onChanged = { dtr = it },
            )
            SwitchRow(
                title = "Assert RTS",
                subtitle = "Also part of the auto-reset circuit on ESP boards",
                value = rts,
                onChanged = { rts = it },
            )

            QFormLabel("USB identity")
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    QTextField(
                        value = vendorId,
                        onValueChange = { vendorId = it },
                        label = "Vendor ID",
                        hint = "1a86",
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    QTextField(
                        value = productId,
                        onValueChange = { productId = it },
                        label = "Product ID",
                        hint = "7523",
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            QTextField(
                value = serialNumber,
                onValueChange = { serialNumber = it },
                label = "Serial number",
                hint = "Pins one physical adapter",
            )

            QFormLabel("Notes")
            QTextField(value = note, onValueChange = { note = it }, label = "Notes", maxLines = 3)

            QFormLabel("Detected")
            DetectedCard(entry.port)
        }
    }
}

private fun hex(value: Int?): String = value?.let { formatUsbId(it).uppercase() } ?: ""

private fun parseHex(raw: String): Int? {
    val cleaned = raw.trim().removePrefix("0x").removePrefix("0X")
    if (cleaned.isEmpty()) return null
    return cleaned.toIntOrNull(16)
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onTap: () -> Unit,
) {
    PickerCard(icon = icon, title = title, subtitle = subtitle, onTap = onTap)
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = TextStyle(color = colors.textMuted, fontSize = 12.sp))
        }
        Switch(
            checked = value,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
            ),
        )
    }
}

@Composable
private fun DetectedCard(port: SerialPortInfo) {
    val colors = appColors
    val rows = buildList {
        add("Kind" to port.kind.label)
        add("Node" to port.path)
        port.byId?.let { add("Stable name" to it) }
        port.usbIds?.let { add("USB ids" to it) }
        port.description?.let { add("Product" to it) }
        port.manufacturer?.let { add("Vendor" to it) }
        port.serialNumber?.let { add("Serial" to it) }
        port.driver?.let { add("Driver" to it) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        for ((label, value) in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    label,
                    modifier = Modifier.width(96.dp),
                    style = TextStyle(color = colors.textMuted, fontSize = 12.sp),
                )
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        value,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
    }
}
