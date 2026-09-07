package com.example.ohmyssh.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.NetworkTag
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Which network this device is on, and the name it goes by.
 *
 * Detection is a few shell calls and an ARP read, so the answer is held for a
 * short while: a burst of connects on one Wi-Fi asks the OS once.
 */
object NetworkWatcher {
    private const val FRESH_MS = 20000L

    /** How stale a sighting has to be before a connect writes the vault again. */
    private const val SEEN_AGAIN_MS = 3600000L

    private val probe = createLanProbe()
    private val lock = Mutex()

    var current: NetworkFingerprint? by mutableStateOf(null)
        private set

    private var checkedAt = 0L

    suspend fun refresh(maxAgeMs: Long = FRESH_MS): NetworkFingerprint? = lock.withLock {
        val age = epochMillis() - checkedAt
        if (checkedAt > 0 && age in 0..maxAgeMs) return@withLock current

        val found = try {
            detect()
        } catch (failure: Exception) {
            Log.warn("network", "could not name the network: $failure")
            null
        }
        checkedAt = epochMillis()
        current = found
        found
    }

    /** Forgets the cached answer, so the next look asks the OS again. */
    fun invalidate() {
        checkedAt = 0
    }

    private suspend fun detect(): NetworkFingerprint? {
        val adapter = chooseSweepInterface(probe.interfaces(), probe.outboundIpv4())
        val link = detectNetworkLink(adapter)
        val gateway = probe.defaultGatewayIpv4(adapter)
        val gatewayMac = gateway?.let { ip ->
            probe.neighbours().firstOrNull { it.ip == ip }?.mac
        }
        val subnet = adapter?.let { networkOf(it.ipv4, it.prefixLength) }
        val ssid = cleanSsid(link.ssid)

        val key = networkKeyFor(ssid, gatewayMac, subnet) ?: return null
        return NetworkFingerprint(
            key = key,
            kind = link.kind,
            ssid = ssid,
            gatewayMac = gatewayMac,
            subnet = subnet,
            interfaceName = adapter?.name,
        )
    }

    /**
     * The current network as something a system can point at, saved on first
     * sight. A network nobody has connected on is never written: an evening in a
     * café should not leave a row in the vault.
     */
    suspend fun currentTag(): NetworkTag? {
        val found = refresh() ?: return null
        if (!VaultStore.isUnlocked) return null

        val known = VaultStore.networkById(found.key)
        val now = epochMillis()
        val tag = known?.copy(
            // Nameless until the OS gives up the SSID; when it does, the network
            // takes it, unless its owner already named it something better.
            name = if (known.named) known.name else found.name.orEmpty(),
            kind = if (known.kind == NetworkKind.UNKNOWN) found.kind else known.kind,
            detail = found.detail ?: known.detail,
            lastSeenAt = now,
        ) ?: NetworkTag(
            id = found.key,
            name = found.name.orEmpty(),
            kind = found.kind,
            detail = found.detail,
            lastSeenAt = now,
        )

        val worthWriting = known == null ||
            known.name != tag.name ||
            known.kind != tag.kind ||
            known.detail != tag.detail ||
            now - known.lastSeenAt > SEEN_AGAIN_MS
        if (worthWriting) VaultStore.saveNetwork(tag)
        return tag
    }

    /** Renames a network, saving it if this is the first thing said about it. */
    suspend fun rename(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        val known = VaultStore.networkById(id)
        if (known != null) {
            VaultStore.saveNetwork(known.copy(name = clean, named = true))
            return
        }
        val found = current?.takeIf { it.key == id }
        VaultStore.saveNetwork(
            NetworkTag(
                id = id,
                name = clean,
                kind = found?.kind ?: NetworkKind.UNKNOWN,
                detail = found?.detail,
                lastSeenAt = epochMillis(),
                named = true,
            ),
        )
    }
}

/**
 * What to call [id] on screen, or empty when nothing has named it yet. The saved
 * name wins, then [fallback] — the name a history entry was written with — then
 * whatever the network calls itself right now. Empty is a real answer: a badge
 * shows its icon alone rather than inventing a name out of an address.
 */
fun networkName(id: String, fallback: String? = null): String =
    VaultStore.networkById(id)?.readableName()
        ?: fallback?.ifEmpty { null }?.takeUnless(::looksLikeAddress)
        ?: NetworkWatcher.current?.takeIf { it.key == id }?.name
        ?: ""

/**
 * The name, unless it is an address wearing one.
 *
 * Networks saved before the app stopped naming them after their subnet still
 * carry "192.168.0.0/24" as a name, and a badge reading that answers a question
 * nobody asked. A name a person typed is kept whatever it looks like — someone
 * may well call their lab network 10.0.0.0/8.
 */
private fun NetworkTag.readableName(): String? =
    name.ifEmpty { null }?.takeIf { named || !looksLikeAddress(it) }

internal fun looksLikeAddress(text: String): Boolean =
    parseIpv4(text.substringBefore('/')) != null || normalizeMac(text) != null

/** For a details list, where an address is worth printing and a badge is not. */
fun networkDescription(id: String, fallback: String? = null): String =
    networkName(id, fallback).ifEmpty { null }
        ?: VaultStore.networkById(id)?.detail
        ?: NetworkWatcher.current?.takeIf { it.key == id }?.detail
        ?: networkKeyLabel(id)

fun networkKind(id: String): NetworkKind =
    VaultStore.networkById(id)?.kind?.takeIf { it != NetworkKind.UNKNOWN }
        ?: NetworkWatcher.current?.takeIf { it.key == id }?.kind
        ?: if (id.startsWith("wifi:")) NetworkKind.WIFI else NetworkKind.UNKNOWN
