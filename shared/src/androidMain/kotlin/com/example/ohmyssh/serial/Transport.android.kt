package com.example.ohmyssh.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.data.SerialFlowControl
import com.example.ohmyssh.data.SerialParity
import com.example.ohmyssh.platform.AndroidApp
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionError
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.coroutines.resume

private const val ACTION_USB_PERMISSION = "com.example.ohmyssh.USB_PERMISSION"

actual fun createSerialScanner(): SerialScanner = AndroidSerialScanner

/// An unrooted Android cannot open /dev/tty* at all, so an OTG adapter is only
/// ever reachable through UsbManager.
private object AndroidSerialScanner : SerialScanner {
    override val isSupported: Boolean = true

    override val hasHotplugEvents: Boolean = true

    /// Android hands over every attached USB device, keyboards included, and
    /// tells us nothing useful about their interface classes here. Only devices
    /// the catalog or the product string recognises as serial are kept —
    /// classifySerialPort is deliberately called without a transport hint so an
    /// unrecognised device stays UNKNOWN instead of falling through to CDC.
    override suspend fun scan(): List<SerialPortInfo> = withContext(Dispatchers.IO) {
        val manager = AndroidApp.context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return@withContext emptyList()

        val ports = mutableListOf<SerialPortInfo>()
        for (device in manager.deviceList.values) {
            val product = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                device.productName
            } else {
                null
            }
            val manufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                device.manufacturerName
            } else {
                null
            }
            val kind = classifySerialPort(
                path = device.deviceName,
                description = product,
                manufacturer = manufacturer,
                vendorId = device.vendorId,
                productId = device.productId,
            )
            val hasDriver = UsbSerialProber.getDefaultProber().probeDevice(device) != null
            if (!kind.isExternal && !hasDriver) continue

            ports.add(
                SerialPortInfo(
                    path = device.deviceName,
                    backend = SerialBackend.USB_HOST,
                    kind = if (kind.isExternal) kind else SerialKind.CDC,
                    description = product,
                    manufacturer = manufacturer,
                    serialNumber = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) null else device.serialNumber
                    }.getOrNull(),
                    vendorId = device.vendorId,
                    productId = device.productId,
                    transport = SerialTransport.USB,
                    androidDeviceId = device.deviceId,
                ),
            )
        }
        ports
    }

    override fun watchHotplug(onEvent: () -> Unit): AutoCloseable {
        val context = AndroidApp.context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = onEvent()
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        return AutoCloseable { runCatching { context.unregisterReceiver(receiver) } }
    }
}

actual suspend fun openSerialLink(
    device: SerialDevice,
    port: SerialPortInfo,
): SerialLink = withContext(Dispatchers.IO) {
    val manager = AndroidApp.context.getSystemService(Context.USB_SERVICE) as? UsbManager
        ?: throw SessionError("This device has no USB host support")

    val usbDevice = manager.deviceList.values.firstOrNull {
        it.deviceId == port.androidDeviceId || it.deviceName == device.path
    } ?: manager.deviceList.values.firstOrNull {
        it.vendorId == device.vendorId && it.productId == device.productId
    } ?: throw SessionError("The device is no longer attached")

    if (!manager.hasPermission(usbDevice) && !requestPermission(manager, usbDevice)) {
        throw SessionError("USB permission was denied")
    }

    val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
        ?: throw SessionError("No serial driver matches ${port.displayName}")

    val connection = manager.openDevice(usbDevice)
        ?: throw SessionError("Could not open ${port.displayName}")

    val serialPort = driver.ports.firstOrNull()
        ?: throw SessionError("The USB device exposes no serial port")

    try {
        serialPort.open(connection)
        serialPort.setParameters(
            device.baudRate,
            device.dataBits,
            stopBitsConstant(device.stopBits),
            parityConstant(device.parity),
        )
        runCatching { serialPort.dtr = device.dtr }
        runCatching { serialPort.rts = device.rts }
        if (device.flowControl != SerialFlowControl.NONE) {
            runCatching { serialPort.flowControl = flowControlConstant(device.flowControl) }
        }
    } catch (error: Exception) {
        runCatching { serialPort.close() }
        throw SessionError(error.message ?: "$error")
    }

    UsbHostLink(serialPort)
}

private suspend fun requestPermission(manager: UsbManager, device: UsbDevice): Boolean =
    suspendCancellableCoroutine { continuation ->
        val context = AndroidApp.context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                runCatching { AndroidApp.context.unregisterReceiver(this) }
                val granted = intent?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (continuation.isActive) continuation.resume(granted == true)
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.requestPermission(device, intent)

        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
    }

private class UsbHostLink(private val port: UsbSerialPort) : SerialLink {
    override val input: Flow<ByteArray> = callbackFlow {
        val buffer = ByteArray(4096)
        val reader = thread(isDaemon = true, name = "usb-serial-read") {
            try {
                while (isActive && port.isOpen) {
                    val read = port.read(buffer, 200)
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
            port.write(data, 250)
        } catch (error: Exception) {
            Log.warn("serial", "write failed: ${error.message ?: error}")
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) { runCatching { port.close() } }
    }
}

private fun parityConstant(parity: SerialParity): Int = when (parity) {
    SerialParity.NONE -> UsbSerialPort.PARITY_NONE
    SerialParity.ODD -> UsbSerialPort.PARITY_ODD
    SerialParity.EVEN -> UsbSerialPort.PARITY_EVEN
    SerialParity.MARK -> UsbSerialPort.PARITY_MARK
    SerialParity.SPACE -> UsbSerialPort.PARITY_SPACE
}

private fun stopBitsConstant(bits: Int): Int =
    if (bits >= 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1

private fun flowControlConstant(flow: SerialFlowControl): UsbSerialPort.FlowControl = when (flow) {
    SerialFlowControl.NONE -> UsbSerialPort.FlowControl.NONE
    SerialFlowControl.RTS_CTS -> UsbSerialPort.FlowControl.RTS_CTS
    SerialFlowControl.DSR_DTR -> UsbSerialPort.FlowControl.DTR_DSR
    SerialFlowControl.XON_XOFF -> UsbSerialPort.FlowControl.XON_XOFF
}
