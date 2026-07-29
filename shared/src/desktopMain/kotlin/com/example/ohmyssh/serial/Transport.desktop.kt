package com.example.ohmyssh.serial

import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.data.SerialFlowControl
import com.example.ohmyssh.data.SerialParity
import com.example.ohmyssh.platform.AppPlatform
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionError
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

actual fun createSerialScanner(): SerialScanner = DesktopSerialScanner

actual suspend fun openSerialLink(
    device: SerialDevice,
    port: SerialPortInfo,
): SerialLink = withContext(Dispatchers.IO) {
    val comPort = SerialPort.getCommPorts()
        .firstOrNull { it.systemPortPath == device.path || it.systemPortName == device.path }
        ?: SerialPort.getCommPort(device.path)

    comPort.setComPortParameters(
        device.baudRate,
        device.dataBits,
        stopBitsConstant(device.stopBits),
        parityConstant(device.parity),
    )
    comPort.setFlowControl(flowControlConstant(device.flowControl))
    // A short blocking read timeout rather than none at all: a non-blocking
    // read spins the reader thread.
    comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 250)

    if (!comPort.openPort()) {
        throw SessionError(explain(device.path, comPort.lastErrorCode))
    }

    // Flow control writes the RTS/DTR lines too, so the explicit ones have to
    // come after it or they are overwritten.
    if (device.dtr) comPort.setDTR() else comPort.clearDTR()
    if (device.rts) comPort.setRTS() else comPort.clearRTS()

    JSerialCommLink(comPort)
}

private class JSerialCommLink(private val port: SerialPort) : SerialLink {
    override val input: Flow<ByteArray> = callbackFlow {
        val buffer = ByteArray(4096)
        val reader = thread(isDaemon = true, name = "serial-read") {
            try {
                while (isActive && port.isOpen) {
                    val read = port.readBytes(buffer, buffer.size)
                    if (read < 0) break
                    if (read > 0) trySend(buffer.copyOf(read))
                }
            } catch (error: Exception) {
                close(error)
                return@thread
            }
            close()
        }
        awaitClose { reader.interrupt() }
    }.flowOn(Dispatchers.IO)

    override fun write(data: ByteArray) {
        try {
            port.writeBytes(data, data.size)
        } catch (error: Exception) {
            Log.warn("serial", "write failed: ${error.message ?: error}")
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) { runCatching { port.closePort() } }
    }
}

private fun parityConstant(parity: SerialParity): Int = when (parity) {
    SerialParity.NONE -> SerialPort.NO_PARITY
    SerialParity.ODD -> SerialPort.ODD_PARITY
    SerialParity.EVEN -> SerialPort.EVEN_PARITY
    SerialParity.MARK -> SerialPort.MARK_PARITY
    SerialParity.SPACE -> SerialPort.SPACE_PARITY
}

private fun stopBitsConstant(bits: Int): Int =
    if (bits >= 2) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT

private fun flowControlConstant(flow: SerialFlowControl): Int = when (flow) {
    SerialFlowControl.NONE -> SerialPort.FLOW_CONTROL_DISABLED
    SerialFlowControl.RTS_CTS ->
        SerialPort.FLOW_CONTROL_RTS_ENABLED or SerialPort.FLOW_CONTROL_CTS_ENABLED
    SerialFlowControl.DSR_DTR ->
        SerialPort.FLOW_CONTROL_DSR_ENABLED or SerialPort.FLOW_CONTROL_DTR_ENABLED
    SerialFlowControl.XON_XOFF ->
        SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED
}

private fun explain(path: String, errorCode: Int): String {
    if (errorCode == 13) {
        return when (appPlatform) {
            AppPlatform.LINUX ->
                "$path: permission denied. Add your user to the dialout group " +
                    "(sudo usermod -aG dialout ${System.getenv("USER") ?: "\$USER"}) " +
                    "and log back in."
            AppPlatform.MACOS ->
                "$path: permission denied. A sandboxed build needs the " +
                    "com.apple.security.device.serial entitlement."
            else -> "$path: permission denied."
        }
    }
    return "$path: could not be opened (error $errorCode)"
}

private object DesktopSerialScanner : SerialScanner {
    override val isSupported: Boolean = true

    override suspend fun scan(): List<SerialPortInfo> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, SerialPortInfo>()

        for (port in nativePorts()) found[port.path] = port
        if (appPlatform == AppPlatform.LINUX) {
            for (port in devNodes()) found[port.path] = merge(found[port.path], port)
        }

        dropDialinTwins(found.values.toList()).sortedBy { it.path }
    }

    override fun nodeExists(path: String): Boolean = try {
        appPlatform != AppPlatform.WINDOWS && File(path).exists()
    } catch (_: Exception) {
        false
    }

    private fun merge(existing: SerialPortInfo?, incoming: SerialPortInfo): SerialPortInfo {
        if (existing == null) return incoming
        val vendorId = existing.vendorId ?: incoming.vendorId
        val productId = existing.productId ?: incoming.productId
        val description = existing.description ?: incoming.description
        val manufacturer = existing.manufacturer ?: incoming.manufacturer
        val driver = existing.driver ?: incoming.driver
        val transport = if (existing.transport == SerialTransport.UNKNOWN) {
            incoming.transport
        } else {
            existing.transport
        }

        return SerialPortInfo(
            path = existing.path,
            backend = existing.backend,
            description = description,
            manufacturer = manufacturer,
            serialNumber = existing.serialNumber ?: incoming.serialNumber,
            vendorId = vendorId,
            productId = productId,
            driver = driver,
            byId = existing.byId ?: incoming.byId,
            transport = transport,
            kind = classifySerialPort(
                path = existing.path,
                description = description,
                manufacturer = manufacturer,
                driver = driver,
                vendorId = vendorId,
                productId = productId,
                transport = transport,
            ),
        )
    }

    /// Unix exposes one adapter twice — /dev/cu.X (callout) and /dev/tty.X
    /// (dial-in, blocks until carrier). Only the callout node is usable.
    private fun dropDialinTwins(ports: List<SerialPortInfo>): List<SerialPortInfo> {
        val callouts = ports.filter { it.name.startsWith("cu.") }
            .map { it.name.substring(3) }
            .toSet()
        return ports.filter { port ->
            !port.name.startsWith("tty.") || !callouts.contains(port.name.substring(4))
        }
    }

    private fun nativePorts(): List<SerialPortInfo> {
        val ports = mutableListOf<SerialPortInfo>()
        val discovered = try {
            SerialPort.getCommPorts()
        } catch (error: Exception) {
            Log.warn("serial", "jSerialComm unavailable: $error")
            return ports
        }

        for (port in discovered) {
            try {
                val vendorId = port.vendorID.takeIf { it > 0 }
                val productId = port.productID.takeIf { it > 0 }
                val transport =
                    if (vendorId != null) SerialTransport.USB else SerialTransport.UNKNOWN
                val description = port.portDescription?.takeIf { it.isNotBlank() }
                    ?: port.descriptivePortName
                ports.add(
                    SerialPortInfo(
                        path = port.systemPortPath ?: port.systemPortName,
                        backend = SerialBackend.NATIVE_PORT,
                        description = description,
                        manufacturer = port.manufacturer?.takeIf { it.isNotBlank() },
                        serialNumber = port.serialNumber?.takeIf {
                            it.isNotBlank() && it != "Unknown"
                        },
                        vendorId = vendorId,
                        productId = productId,
                        transport = transport,
                        kind = classifySerialPort(
                            path = port.systemPortPath ?: port.systemPortName,
                            description = description,
                            manufacturer = port.manufacturer,
                            vendorId = vendorId,
                            productId = productId,
                            transport = transport,
                        ),
                    ),
                )
            } catch (error: Exception) {
                Log.warn("serial", "could not describe ${port.systemPortName}: $error")
            }
        }
        return ports
    }

    private val candidateNode = Regex("^(tty|serial|rfcomm|cua|cu\\.)")

    private fun devNodes(): List<SerialPortInfo> {
        val ports = mutableListOf<SerialPortInfo>()
        val entries = try {
            File("/dev").listFiles() ?: return ports
        } catch (error: Exception) {
            Log.warn("serial", "/dev not listable: $error")
            return ports
        }

        val aliases = stableAliases()

        for (entry in entries) {
            val path = entry.path
            val name = serialPortName(path)
            if (!candidateNode.containsMatchIn(name)) continue

            if (resolve(path) != path) continue

            if (classifySerialPort(path = path) == SerialKind.VIRTUAL) continue

            val driver = sysfsDriver(name)
            val usb = sysfsUsb(name)
            val transport = if (usb == null) SerialTransport.UNKNOWN else SerialTransport.USB
            ports.add(
                SerialPortInfo(
                    path = path,
                    backend = SerialBackend.NATIVE_PORT,
                    description = usb?.product,
                    manufacturer = usb?.manufacturer,
                    serialNumber = usb?.serial,
                    vendorId = usb?.vendorId,
                    productId = usb?.productId,
                    driver = driver,
                    byId = aliases[path],
                    transport = transport,
                    kind = classifySerialPort(
                        path = path,
                        description = usb?.product,
                        manufacturer = usb?.manufacturer,
                        driver = driver,
                        vendorId = usb?.vendorId,
                        productId = usb?.productId,
                        transport = transport,
                    ),
                ),
            )
        }
        return ports
    }

    private fun stableAliases(): Map<String, String> {
        val aliases = HashMap<String, String>()

        for (directory in listOf("/dev/serial/by-id", "/dev/serial/by-path")) {
            try {
                val entries = File(directory).listFiles() ?: continue
                for (entry in entries) {
                    aliases.putIfAbsent(resolve(entry.path), entry.path)
                }
            } catch (_: Exception) {
            }
        }

        for (index in 0 until 4) {
            val alias = "/dev/serial$index"
            if (!File(alias).exists()) continue
            val target = resolve(alias)
            if (target != alias) aliases[target] = alias
        }
        return aliases
    }

    private fun resolve(path: String): String = try {
        File(path).canonicalPath
    } catch (_: Exception) {
        path
    }

    private fun sysfsDriver(name: String): String? = try {
        val link = File("/sys/class/tty/$name/device/driver")
        if (link.exists()) serialPortName(link.canonicalPath) else null
    } catch (_: Exception) {
        null
    }

    private class SysfsUsb(
        val vendorId: Int,
        val productId: Int?,
        val manufacturer: String?,
        val product: String?,
        val serial: String?,
    )

    private fun sysfsUsb(name: String): SysfsUsb? {
        var directory = try {
            File("/sys/class/tty/$name/device").canonicalPath
        } catch (_: Exception) {
            return null
        }

        var depth = 0
        while (depth < 5 && directory.length > 1) {
            val vendorId = readHex("$directory/idVendor")
            if (vendorId != null) {
                return SysfsUsb(
                    vendorId = vendorId,
                    productId = readHex("$directory/idProduct"),
                    manufacturer = readText("$directory/manufacturer"),
                    product = readText("$directory/product"),
                    serial = readText("$directory/serial"),
                )
            }
            directory = directory.substringBeforeLast('/')
            depth++
        }
        return null
    }

    private fun readText(path: String): String? = try {
        File(path).readText().trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun readHex(path: String): Int? = readText(path)?.toIntOrNull(16)
}
