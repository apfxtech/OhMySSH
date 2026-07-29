package com.example.ohmyssh.serial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class SerialDeviceEntry(
    val device: SerialDevice,
    val port: SerialPortInfo,
    val saved: Boolean,
) {
    val title: String
        get() = device.label.ifEmpty { port.displayName.ifEmpty { device.path } }

    val subtitle: String
        get() {
            val parts = mutableListOf(serialPortName(port.path), "${device.baudRate}")
            (port.usbIds ?: device.usbIds)?.let { parts.add(it) }
            return parts.joinToString(" · ")
        }
}

object SerialRegistry {
    private val pollInterval = 4.seconds
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = createSerialScanner()

    private var ports: List<SerialPortInfo> = emptyList()
    private var scanning = false
    private var watchers = 0
    private var pollJob: Job? = null
    private var hotplug: AutoCloseable? = null

    var entries: List<SerialDeviceEntry> by mutableStateOf(emptyList())
        private set

    val isSupported: Boolean get() = scanner.isSupported

    val hasDevices: Boolean get() = entries.isNotEmpty()

    fun watch() {
        watchers++
        if (watchers > 1) return
        if (!isSupported) return

        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(pollInterval)
            }
        }
        if (scanner.hasHotplugEvents) {
            hotplug = try {
                scanner.watchHotplug { scope.launch { refresh() } }
            } catch (error: Exception) {
                Log.warn("serial", "hotplug events unavailable: $error")
                null
            }
        }
    }

    fun unwatch() {
        if (watchers == 0) return
        watchers--
        if (watchers > 0) return

        pollJob?.cancel()
        pollJob = null
        runCatching { hotplug?.close() }
        hotplug = null
    }

    suspend fun refresh() {
        if (!isSupported || scanning) return
        scanning = true
        try {
            ports = scanner.scan()
            remerge()
        } catch (error: Exception) {
            Log.error("serial", "scan failed: $error", error)
        } finally {
            scanning = false
        }
    }

    fun entryForDevice(deviceId: String): SerialDeviceEntry? =
        entries.firstOrNull { it.device.id == deviceId }

    private fun remerge() {
        val next = merge(ports, VaultStore.serialDevices)
        if (!sameEntries(entries, next)) entries = next
    }

    private fun merge(
        ports: List<SerialPortInfo>,
        saved: List<SerialDevice>,
    ): List<SerialDeviceEntry> {
        val attached = ports.filter { it.isExternal }
        val unclaimed = saved.toMutableList()
        val result = mutableListOf<SerialDeviceEntry>()

        for (port in attached) {
            val match = bestMatch(port, unclaimed)
            if (match != null) unclaimed.remove(match)
            result.add(
                SerialDeviceEntry(
                    port = port,
                    saved = match != null,
                    device = match?.copy(
                        path = port.path,
                        hardware = port.displayName,
                        byId = port.byId ?: match.byId,
                    ) ?: describe(port),
                ),
            )
        }

        for (device in unclaimed) {
            if (device.path.isEmpty()) continue
            if (attached.any { it.path == device.path }) continue
            if (!scanner.nodeExists(device.path)) continue
            result.add(
                SerialDeviceEntry(
                    device = device,
                    saved = true,
                    port = SerialPortInfo(
                        path = device.path,
                        backend = SerialBackend.NATIVE_PORT,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        serialNumber = device.serialNumber,
                        byId = device.byId,
                        description = device.hardware,
                        kind = classifySerialPort(
                            path = device.path,
                            description = device.hardware,
                            vendorId = device.vendorId,
                            productId = device.productId,
                        ),
                    ),
                ),
            )
        }

        return result.sortedBy { it.title.lowercase() }
    }

    private fun bestMatch(port: SerialPortInfo, candidates: List<SerialDevice>): SerialDevice? {
        var best: SerialDevice? = null
        var bestScore = 0
        for (device in candidates) {
            val score = score(port, device)
            if (score > bestScore) {
                best = device
                bestScore = score
            }
        }
        return best
    }

    private fun score(port: SerialPortInfo, device: SerialDevice): Int {
        val sameIds = port.vendorId != null &&
            device.vendorId == port.vendorId &&
            device.productId == port.productId

        if (sameIds && port.serialNumber != null && port.serialNumber == device.serialNumber) {
            return 4
        }
        if (port.byId != null && port.byId == device.byId) return 3
        if (sameIds) return 2
        if (device.path == port.path) return 1
        return 0
    }

    private fun describe(port: SerialPortInfo): SerialDevice = SerialDevice(
        id = newId(),
        path = port.path,
        vendorId = port.vendorId,
        productId = port.productId,
        serialNumber = port.serialNumber,
        byId = port.byId,
        hardware = port.displayName,
    )

    private fun sameEntries(a: List<SerialDeviceEntry>, b: List<SerialDeviceEntry>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i].device.id != b[i].device.id ||
                a[i].device.path != b[i].device.path ||
                a[i].title != b[i].title ||
                a[i].subtitle != b[i].subtitle ||
                a[i].saved != b[i].saved
            ) {
                return false
            }
        }
        return true
    }
}
