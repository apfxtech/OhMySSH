package com.example.ohmyssh.net

import com.example.ohmyssh.platform.AppPlatform
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.services.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

actual suspend fun detectNetworkLink(adapter: LanInterface?): NetworkLink =
    withContext(Dispatchers.IO) {
        when (appPlatform) {
            AppPlatform.MACOS -> macLink(adapter)
            AppPlatform.LINUX -> linuxLink(adapter)
            AppPlatform.WINDOWS -> windowsLink(adapter)
            else -> NetworkLink(NetworkKind.UNKNOWN)
        }
    }

/**
 * macOS names the port an interface belongs to, so the kind is exact. The SSID
 * comes from CoreWLAN, which answers honestly once the app holds a Location
 * authorisation and returns nothing until then; the router MAC carries the
 * identity either way, so a network keeps its badge whatever the answer.
 */
private fun macLink(adapter: LanInterface?): NetworkLink {
    val device = adapter?.name
    val port = device?.let { name ->
        blocksOf(runCommand(listOf("/usr/sbin/networksetup", "-listallhardwareports")))
            .firstOrNull { fieldIn(it, "Device") == name }
            ?.let { fieldIn(it, "Hardware Port") }
    }
    val wifi = port?.contains("Wi-Fi", ignoreCase = true) == true ||
        port?.contains("AirPort", ignoreCase = true) == true
    if (!wifi) return NetworkLink(if (port == null) NetworkKind.UNKNOWN else NetworkKind.WIRED)

    val ssid = cleanSsid(coreWlanSsid(device))
        ?: cleanSsid(fieldIn(runCommand(listOf("/usr/sbin/ipconfig", "getsummary", device)), "SSID"))
        ?: cleanSsid(
            runCommand(listOf("/usr/sbin/networksetup", "-getairportnetwork", device))
                .substringAfter("Network:", "")
                .trim()
                .ifEmpty { null },
        )
        ?: cleanSsid(device?.let(::cachedScanSsid))
    return NetworkLink(NetworkKind.WIFI, ssid)
}

actual fun ssidAccess(): SsidAccess =
    if (appPlatform == AppPlatform.MACOS) MacLocation.access() else SsidAccess.NOT_NEEDED

actual suspend fun requestSsidAccess(timeoutMs: Long): SsidAccess {
    if (ssidAccess() != SsidAccess.ASKABLE) return ssidAccess()
    MacLocation.request()

    // The prompt is modal to the person, not to us, and there is no callback
    // without a delegate, so the status is watched until it moves. An app run
    // outside its bundle never gets a prompt at all: that is the timeout.
    val step = 250L
    var waited = 0L
    while (waited < timeoutMs) {
        delay(step)
        waited += step
        val answered = ssidAccess()
        if (answered != SsidAccess.ASKABLE) return answered
        if (waited == 2000L) MacLocation.requestHere()
    }
    Log.warn("network", "the Location prompt never came back")
    return SsidAccess.ASKABLE
}

private fun linuxLink(adapter: LanInterface?): NetworkLink {
    val device = adapter?.name
    val wireless = device != null &&
        (File("/sys/class/net/$device/wireless").exists() || device.startsWith("wl"))
    if (!wireless) return NetworkLink(if (device == null) NetworkKind.UNKNOWN else NetworkKind.WIRED)

    val ssid = cleanSsid(runCommand(listOf("iwgetid", "-r", device)).trim().ifEmpty { null })
        ?: cleanSsid(
            runCommand(listOf("nmcli", "-t", "-f", "active,ssid", "dev", "wifi"))
                .lineSequence()
                .firstOrNull { it.startsWith("yes:") }
                ?.substringAfter("yes:"),
        )
        ?: cleanSsid(fieldIn(runCommand(listOf("iw", "dev", device, "link")), "SSID"))
    return NetworkLink(NetworkKind.WIFI, ssid)
}

/**
 * Windows hands out the SSID freely, but names its adapters differently from the
 * JVM ("Wi-Fi" against "eth5"), so the hardware address is what joins the two —
 * without it a wired machine with an idle Wi-Fi radio would file its connections
 * under the network it is not using.
 */
private fun windowsLink(adapter: LanInterface?): NetworkLink {
    // An SSID line at all means that radio is associated; a disconnected one
    // prints its state and stops, in whatever language Windows is installed in.
    val blocks = blocksOf(runCommand(listOf("netsh", "wlan", "show", "interfaces")))
        .filter { cleanSsid(fieldIn(it, "SSID")) != null }

    val block = when {
        adapter?.mac != null -> blocks.firstOrNull { blockHasMac(it, adapter.mac) }
        blocks.size == 1 -> blocks.first()
        else -> null
    } ?: return NetworkLink(if (adapter == null) NetworkKind.UNKNOWN else NetworkKind.WIRED)

    return NetworkLink(NetworkKind.WIFI, cleanSsid(fieldIn(block, "SSID")))
}
