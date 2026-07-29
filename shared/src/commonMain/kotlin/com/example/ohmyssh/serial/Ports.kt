package com.example.ohmyssh.serial

enum class SerialTransport { USB, BLUETOOTH, NATIVE, UNKNOWN }

enum class SerialBackend { NATIVE_PORT, USB_HOST }

private val virtualNames = listOf(
    Regex("^tty$"),
    Regex("^tty\\d+$"),
    Regex("^ttyv[0-9a-f]+$"),
    Regex("^console$"),
    Regex("^ptmx$"),
    Regex("^pts$"),
    Regex("^(pty|tty)[p-za-e][0-9a-f]$"),
    Regex("^ttyprintk$"),
    Regex("^ttynull$"),
    Regex("^ttyGS\\d+$"),
    Regex("^ttyDBC\\d+$"),
    Regex("^hvc\\d+$"),
    Regex("^hvsi\\d+$"),
    Regex("^xvc\\d+$"),
    Regex("^ttysclp\\d+$"),
    Regex("^sclp_line\\d+$"),
    Regex("^ttyprintk\\d*$"),
    Regex("^cu\\.Bluetooth-Incoming-Port$"),
    Regex("^tty\\.Bluetooth-Incoming-Port$"),
    Regex("^cu\\.debug-console$"),
    Regex("^tty\\.debug-console$"),
    Regex("^cu\\.wlan-debug$"),
    Regex("^tty\\.wlan-debug$"),
    Regex("^(cu|tty)\\.(MALS|SOC|iap-.*)$"),
)

private val attachedNames = listOf(
    Regex("^ttyUSB\\d+$") to SerialKind.BRIDGE,
    Regex("^ttyACM\\d+$") to SerialKind.CDC,
    Regex("^ttyXRUSB\\d+$") to SerialKind.BRIDGE,
    Regex("^ttyCH(343|9344)USB\\d+$") to SerialKind.BRIDGE,
    Regex("^ttyRFCOMM\\d+$") to SerialKind.BLUETOOTH,
    Regex("^rfcomm\\d+$") to SerialKind.BLUETOOTH,
    // FreeBSD: capital U is the USB adapter, lowercase is the onboard UART.
    Regex("^cuaU\\d+$") to SerialKind.BRIDGE,
    Regex("^(cu|tty)\\.usbserial.*$") to SerialKind.BRIDGE,
    Regex("^(cu|tty)\\.wchusbserial.*$") to SerialKind.BRIDGE,
    Regex("^(cu|tty)\\.SLAB_USBtoUART.*$") to SerialKind.BRIDGE,
    Regex("^(cu|tty)\\.(PL2303|Repleo|URT).*$") to SerialKind.BRIDGE,
    Regex("^(cu|tty)\\.usbmodem.*$") to SerialKind.CDC,
)

private val socUartNames = listOf(
    Regex("^serial\\d+$"),
    Regex("^ttyAMA\\d+$"),
    Regex("^ttyAML\\d+$"),
    Regex("^ttymxc\\d+$"),
    Regex("^ttyO\\d+$"),
    Regex("^ttySAC\\d+$"),
    Regex("^ttyTHS\\d+$"),
    Regex("^ttyLP\\d+$"),
    Regex("^ttySTM\\d+$"),
    Regex("^ttyMSM\\d+$"),
    Regex("^ttyHSL?\\d+$"),
    Regex("^ttyFIQ\\d+$"),
    Regex("^ttyPS\\d+$"),
    Regex("^ttySC\\d+$"),
    Regex("^ttyMV\\d+$"),
    Regex("^ttyBCM\\d+$"),
    Regex("^ttyUL\\d+$"),
)

private val legacyNames = Regex("^(ttyS\\d+|cuau\\d+|ttyu\\d+)$")

fun classifySerialPort(
    path: String,
    description: String? = null,
    manufacturer: String? = null,
    driver: String? = null,
    vendorId: Int? = null,
    productId: Int? = null,
    transport: SerialTransport = SerialTransport.UNKNOWN,
): SerialKind {
    val name = serialPortName(path)

    for (pattern in virtualNames) {
        if (pattern.matches(name)) return SerialKind.VIRTUAL
    }

    val driverKind = driver?.let { kSerialDrivers[it] }
    if (driverKind != null && driverKind != SerialKind.ONBOARD) return driverKind

    usbVendor(vendorId)?.kind?.let { return it }

    for ((pattern, kind) in attachedNames) {
        if (pattern.matches(name)) return kind
    }

    // A ttyS* is the motherboard's 16550 on a PC and the mini-UART on a Pi. The
    // driver is what tells them apart; with no sysfs to read, assume the PC.
    if (legacyNames.matches(name)) {
        return if (driver == null || driverKind == SerialKind.ONBOARD) {
            SerialKind.ONBOARD
        } else {
            SerialKind.UART
        }
    }
    for (pattern in socUartNames) {
        if (pattern.matches(name)) return SerialKind.UART
    }

    val described = kindFromDescription(description) ?: kindFromDescription(manufacturer)
    if (described != null) return described

    if (driverKind != null) return driverKind

    return when (transport) {
        SerialTransport.USB -> SerialKind.CDC
        SerialTransport.BLUETOOTH -> SerialKind.BLUETOOTH
        SerialTransport.NATIVE -> SerialKind.ONBOARD
        SerialTransport.UNKNOWN -> SerialKind.UNKNOWN
    }
}

class SerialPortInfo(
    val path: String,
    val kind: SerialKind,
    val backend: SerialBackend,
    val description: String? = null,
    val manufacturer: String? = null,
    val serialNumber: String? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val driver: String? = null,
    val byId: String? = null,
    val transport: SerialTransport = SerialTransport.UNKNOWN,
    val androidDeviceId: Int? = null,
) {
    val isExternal: Boolean get() = kind.isExternal

    val name: String get() = serialPortName(path)

    val usbIds: String?
        get() = vendorId?.let { "${formatUsbId(it)}:${formatUsbId(productId ?: 0)}" }

    val displayName: String
        get() = describeUsbDevice(vendorId, productId)
            ?: cleaned(description)
            ?: cleaned(manufacturer)
            ?: name

    private fun cleaned(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
