package com.example.ohmyssh.net

import com.example.ohmyssh.platform.ObjC
import com.example.ohmyssh.services.Log
import com.sun.jna.Pointer
import java.util.concurrent.TimeUnit

private const val CORE_LOCATION =
    "/System/Library/Frameworks/CoreLocation.framework/CoreLocation"
private const val CORE_WLAN =
    "/System/Library/Frameworks/CoreWLAN.framework/CoreWLAN"

// CLAuthorizationStatus.
private const val NOT_DETERMINED = 0
private const val RESTRICTED = 1
private const val DENIED = 2
private const val AUTHORIZED_ALWAYS = 3
private const val AUTHORIZED_WHEN_IN_USE = 4

/**
 * The Wi-Fi name, asked in-process rather than shelled out for.
 *
 * Which matters: the redaction is decided per client, and a `ipconfig` we spawn
 * is a different client from us. CoreWLAN asked from inside the app is the only
 * caller whose authorisation is our own.
 */
internal fun coreWlanSsid(device: String?): String? = runCatching {
    if (!ObjC.loadFramework(CORE_WLAN)) return null
    val client = ObjC.send(ObjC.cls("CWWiFiClient"), "sharedWiFiClient") ?: return null
    val adapter = if (device == null) {
        ObjC.send(client, "interface")
    } else {
        ObjC.send(client, "interfaceWithName:", ObjC.string(device))
    } ?: return null
    ObjC.text(ObjC.send(adapter, "ssid"))
}.getOrElse { failure ->
    Log.warn("network", "CoreWLAN gave nothing: $failure")
    null
}

private val SCAN_RECORD = Regex("CachedScanRecord\\s*:\\s*<data>\\s*0x([0-9a-fA-F]+)")

/**
 * The Wi-Fi name as the system configuration store still knows it.
 *
 * macOS blanks `SSID_STR` in that store and redacts every other reading of the
 * name, but the cached scan record beside them is an archived blob it forgot to
 * clean, and the name is in there in the clear. It is a cache: it names the
 * network of the last scan, which is all but always the one we are on. So it is
 * the last resort, after CoreWLAN has answered for the association itself.
 */
internal fun cachedScanSsid(device: String): String? = runCatching {
    val dump = runScutil("show State:/Network/Interface/$device/AirPort")
    val hex = SCAN_RECORD.find(dump)?.groupValues?.get(1) ?: return null
    if (hex.length % 2 != 0) return null

    val bytes = ByteArray(hex.length / 2) {
        ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte()
    }
    archivedString(BinaryPlist.parse(bytes), "SSID_STR")
}.getOrElse { failure ->
    Log.warn("network", "no cached scan to read: $failure")
    null
}

/** scutil takes its commands on stdin and has no one-shot form. */
private fun runScutil(command: String): String {
    val process = ProcessBuilder("/usr/sbin/scutil").redirectErrorStream(true).start()
    process.outputStream.bufferedWriter().use { it.write("$command\n") }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy()
    return output
}

/**
 * The Location authorisation macOS gates the SSID behind since Sonoma. Nothing
 * here reads a coordinate: the app never starts location updates, it only holds
 * the authorisation that stops the OS from blanking every network name.
 */
internal object MacLocation {
    /**
     * TCC identifies an app by its bundle, and a Gradle run is a bare `java`
     * with none — the prompt never appears and the status never moves. Saying
     * so up front is what keeps a dev build from waiting ten seconds on a
     * dialog that was never going to be shown.
     */
    private val bundled: Boolean by lazy {
        val binary = runCatching { ProcessHandle.current().info().command().orElse(null) }
            .getOrNull()
        binary?.contains(".app/Contents/MacOS/") == true
    }

    private val manager: Pointer? by lazy {
        runCatching {
            if (!ObjC.loadFramework(CORE_LOCATION)) return@runCatching null
            val type = ObjC.cls("CLLocationManager") ?: return@runCatching null
            ObjC.send(ObjC.send(type, "alloc"), "init")
        }.getOrElse { failure ->
            Log.warn("network", "CoreLocation is not reachable: $failure")
            null
        }
    }

    fun access(): SsidAccess {
        if (!bundled) return SsidAccess.NOT_NEEDED
        val handle = manager ?: return SsidAccess.NOT_NEEDED
        return when (runCatching { ObjC.sendInt(handle, "authorizationStatus") }.getOrNull()) {
            NOT_DETERMINED -> SsidAccess.ASKABLE
            AUTHORIZED_ALWAYS, AUTHORIZED_WHEN_IN_USE -> SsidAccess.GRANTED
            RESTRICTED, DENIED -> SsidAccess.DENIED
            else -> SsidAccess.NOT_NEEDED
        }
    }

    /**
     * Puts the prompt on screen. It goes through the AppKit thread because that
     * is the one with a live run loop; a request made from a worker never
     * reaches the user, and the status simply never changes.
     */
    fun request() {
        val handle = manager ?: return
        runCatching {
            ObjC.send(
                handle, "performSelectorOnMainThread:withObject:waitUntilDone:",
                ObjC.sel("requestWhenInUseAuthorization"), null, false,
            )
        }
    }

    /** Second attempt, straight from the caller, for a run loop that never ran. */
    fun requestHere() {
        val handle = manager ?: return
        runCatching { ObjC.send(handle, "requestWhenInUseAuthorization") }
    }
}
