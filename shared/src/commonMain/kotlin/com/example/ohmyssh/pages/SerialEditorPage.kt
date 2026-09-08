package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.data.SerialFlowControl
import com.example.ohmyssh.data.SerialParity
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.serial.formatUsbId
import com.example.ohmyssh.serial.serialPortName
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.DropdownField
import com.example.ohmyssh.widgets.DropdownOption
import com.example.ohmyssh.widgets.EditorScaffold
import com.example.ohmyssh.widgets.EditorSection
import com.example.ohmyssh.widgets.FieldGap
import com.example.ohmyssh.widgets.FieldGroupTitle
import com.example.ohmyssh.widgets.FieldRow
import com.example.ohmyssh.widgets.InfoTable
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.SegmentOption
import com.example.ohmyssh.widgets.SegmentedChoice
import com.example.ohmyssh.widgets.SwitchSetting
import com.example.ohmyssh.widgets.appTextFieldColors
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch

private const val DEVICE = "device"
private const val LINE = "line"
private const val SIGNALS = "signals"
private const val IDENTITY = "identity"

private val commonBaudRates = listOf(
    300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600,
)

@Composable
fun SerialEditorPage(entry: SerialDeviceEntry) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val device = entry.device

    var section by rememberSaveable(device.id) { mutableStateOf(DEVICE) }
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

    val rate = baudRate.trim().toIntOrNull()

    fun assemble(): SerialDevice = SerialDevice(
        id = device.id,
        label = label.trim(),
        path = path.trim(),
        baudRate = rate ?: device.baudRate,
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
    )

    val dirty = !entry.saved || assemble() != device

    fun problemIn(sectionId: String): String? = when (sectionId) {
        DEVICE -> if (path.isBlank()) "The port path is required" else null
        LINE -> if (rate == null || rate < 50 || rate > 4_000_000) "Baud rate must be 50–4000000" else null
        IDENTITY -> when {
            vendorId.isNotBlank() && parseHex(vendorId) == null -> "Vendor ID must be hex"
            productId.isNotBlank() && parseHex(productId) == null -> "Product ID must be hex"
            else -> null
        }
        else -> null
    }

    val line = "${rate ?: "?"} $dataBits${parity.wireName[0].uppercaseChar()}$stopBits"
    val sections = listOf(
        EditorSection(DEVICE, "Device", Icons.Filled.Usb, serialPortName(path.trim()).ifEmpty { "No port" }, problemIn(DEVICE)),
        EditorSection(
            LINE,
            "Line",
            Icons.Outlined.Tune,
            summary = if (flowControl == SerialFlowControl.NONE) line else "$line · ${flowControl.label}",
            problem = problemIn(LINE),
        ),
        EditorSection(
            SIGNALS,
            "Signals",
            Icons.Outlined.SwapHoriz,
            summary = listOfNotNull("DTR".takeIf { dtr }, "RTS".takeIf { rts })
                .joinToString(", ")
                .ifEmpty { "None asserted" },
        ),
        EditorSection(
            IDENTITY,
            "USB identity",
            Icons.Outlined.Cable,
            summary = assemble().usbIds ?: "Not pinned",
            problem = problemIn(IDENTITY),
        ),
    )

    fun save() {
        for (candidate in sections) {
            val problem = problemIn(candidate.id) ?: continue
            section = candidate.id
            AppToasts.show(problem)
            return
        }
        scope.launch {
            VaultStore.saveSerialDevice(assemble())
            SerialRegistry.refresh()
            navigator.pop()
        }
    }

    EditorScaffold(
        title = if (entry.saved) label.trim().ifEmpty { entry.title } else "New device",
        subtitle = "${serialPortName(path.trim())} · $line",
        sections = sections,
        selected = section,
        onSelect = { section = it },
        dirty = dirty,
        onSave = ::save,
        deleteTooltip = "Forget",
        onDelete = if (!entry.saved) {
            null
        } else {
            {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Forget this device?",
                        message = "${device.displayLabel} keeps working — only its saved settings are removed.",
                        actionLabel = "Forget",
                    )
                    if (confirmed) {
                        VaultStore.deleteSerialDevice(device.id)
                        SerialRegistry.refresh()
                        navigator.pop()
                    }
                }
            }
        },
    ) { current ->
        when (current) {
            DEVICE -> {
                QTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Name",
                    hint = entry.port.displayName,
                    autofocus = !entry.saved,
                )
                FieldGap()
                QTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = "Port",
                    hint = "/dev/ttyUSB0, /dev/serial0 or COM3",
                )
                FieldGap()
                QTextField(value = note, onValueChange = { note = it }, label = "Notes", maxLines = 4)
            }

            LINE -> {
                BaudRateField(value = baudRate, onValueChange = { baudRate = it })
                FieldGroupTitle("Data bits")
                SegmentedChoice(
                    options = listOf(5, 6, 7, 8).map { SegmentOption(it, "$it") },
                    selected = dataBits,
                    onSelect = { dataBits = it },
                )
                FieldGroupTitle("Stop bits")
                SegmentedChoice(
                    options = listOf(1, 2).map { SegmentOption(it, "$it") },
                    selected = stopBits,
                    onSelect = { stopBits = it },
                )
                FieldGroupTitle("Parity and flow control")
                FieldRow {
                    DropdownField(
                        label = "Parity",
                        value = parity,
                        options = SerialParity.entries.map { DropdownOption(it, it.label) },
                        onSelect = { parity = it },
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(
                        label = "Flow control",
                        value = flowControl,
                        options = SerialFlowControl.entries.map { DropdownOption(it, it.label) },
                        onSelect = { flowControl = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SIGNALS -> {
                SwitchSetting(
                    title = "Assert DTR",
                    description = "Boards wired for auto-reset reboot when this is on",
                    checked = dtr,
                    onChange = { dtr = it },
                )
                SwitchSetting(
                    title = "Assert RTS",
                    description = "Also part of the auto-reset circuit on ESP boards",
                    checked = rts,
                    onChange = { rts = it },
                )
            }

            IDENTITY -> {
                FieldRow {
                    QTextField(
                        value = vendorId,
                        onValueChange = { vendorId = it },
                        label = "Vendor ID",
                        hint = "1a86",
                        modifier = Modifier.weight(1f),
                    )
                    QTextField(
                        value = productId,
                        onValueChange = { productId = it },
                        label = "Product ID",
                        hint = "7523",
                        modifier = Modifier.weight(1f),
                    )
                }
                FieldGap()
                QTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = "Serial number",
                )
                FieldGroupTitle("Detected")
                val port = entry.port
                InfoTable(
                    buildList {
                        add("Kind" to port.kind.label)
                        add("Node" to port.path)
                        port.byId?.let { add("Stable name" to it) }
                        port.usbIds?.let { add("USB ids" to it) }
                        port.description?.let { add("Product" to it) }
                        port.manufacturer?.let { add("Vendor" to it) }
                        port.serialNumber?.let { add("Serial" to it) }
                        port.driver?.let { add("Driver" to it) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaudRateField(value: String, onValueChange: (String) -> Unit) {
    val colors = appColors
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }) },
            singleLine = true,
            label = { Text("Baud rate", fontSize = 13.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.dialogBackground,
        ) {
            Column {
                for (rate in commonBaudRates) {
                    DropdownMenuItem(
                        text = { Text("$rate", color = colors.dialogText, fontSize = 13.5.sp) },
                        onClick = {
                            onValueChange("$rate")
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun hex(value: Int?): String = value?.let { formatUsbId(it).uppercase() } ?: ""

private fun parseHex(raw: String): Int? {
    val cleaned = raw.trim().removePrefix("0x").removePrefix("0X")
    if (cleaned.isEmpty()) return null
    return cleaned.toIntOrNull(16)
}
