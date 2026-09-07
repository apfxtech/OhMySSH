package com.example.ohmyssh.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.ohmyssh.platform.AndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun detectNetworkLink(adapter: LanInterface?): NetworkLink =
    withContext(Dispatchers.IO) {
        val context = runCatching { AndroidApp.context }.getOrNull()
            ?: return@withContext NetworkLink(NetworkKind.UNKNOWN)

        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        val kind = when {
            capabilities == null -> NetworkKind.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIRED
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            else -> NetworkKind.UNKNOWN
        }
        if (kind != NetworkKind.WIFI) return@withContext NetworkLink(kind)

        NetworkLink(kind, cleanSsid(ssidOf(context, capabilities)))
    }

/**
 * The SSID, on the days Android will name it. Since Oreo it is `<unknown ssid>`
 * unless the app holds a location permission — one an SSH client has no reason
 * to ask for — so this is a bonus, not the identity the fingerprint rests on.
 */
private fun ssidOf(context: Context, capabilities: NetworkCapabilities?): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (capabilities?.transportInfo as? WifiInfo)?.ssid
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.ssid
    }
}.getOrNull()

/**
 * Android would name the Wi-Fi for an app holding ACCESS_FINE_LOCATION. That
 * permission is not in the manifest and is not worth asking an SSH client's
 * users for, so there is nothing here to prompt about — the router MAC or the
 * subnet names the network instead, and a person can rename it.
 */
actual fun ssidAccess(): SsidAccess = SsidAccess.NOT_NEEDED

actual suspend fun requestSsidAccess(timeoutMs: Long): SsidAccess = SsidAccess.NOT_NEEDED
