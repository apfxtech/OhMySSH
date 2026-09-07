package com.example.ohmyssh.net

enum class NetworkKind {
    WIFI,
    WIRED,
    CELLULAR,
    UNKNOWN;

    val wireName: String get() = name.lowercase()

    companion object {
        fun parse(raw: String?): NetworkKind =
            entries.firstOrNull { it.wireName == raw } ?: UNKNOWN
    }
}

/** What the platform can say about the link an interface is carried on. */
class NetworkLink(val kind: NetworkKind, val ssid: String? = null)

/**
 * The network this device sits on right now, as far as it can be told apart
 * from every other one.
 *
 * [key] is what a system remembers, so it has to survive a DHCP lease and mean
 * the same network tomorrow. The router's MAC leads: it needs no permission,
 * holds still across a mesh, tells two offices on one corporate SSID apart —
 * and, unlike the SSID, does not change the day macOS is finally allowed to
 * name the network, which would orphan every badge already saved.
 */
class NetworkFingerprint(
    val key: String,
    val kind: NetworkKind,
    val ssid: String?,
    val gatewayMac: String?,
    val subnet: String?,
    val interfaceName: String?,
) {
    /**
     * The only thing worth printing as a name. A subnet is not one: "192.168.0.0/24"
     * on a badge answers a question nobody asked and hides the one that matters,
     * so a network the OS will not name stays nameless until someone names it.
     */
    val name: String? get() = ssid

    val detail: String? get() = subnet ?: gatewayMac
}

fun networkKeyFor(ssid: String?, gatewayMac: String?, subnet: String?): String? = when {
    gatewayMac != null -> "lan:$gatewayMac"
    !ssid.isNullOrEmpty() -> "wifi:$ssid"
    subnet != null -> "net:$subnet"
    else -> null
}

/** Readable stand-in for a key whose network is no longer in the vault. */
fun networkKeyLabel(key: String): String {
    val body = key.substringAfter(':', key)
    return if (body.isEmpty()) key else body
}

/**
 * An SSID an OS hands back when it is refusing to name the network: macOS prints
 * `<redacted>` without Location, Android `<unknown ssid>` without a location
 * permission. Treated as no answer, so the fingerprint falls through to the
 * router MAC instead of filing every network under one bogus name.
 */
fun cleanSsid(raw: String?): String? {
    val ssid = raw?.trim()?.trim('"')?.ifEmpty { null } ?: return null
    if (ssid.startsWith('<') && ssid.endsWith('>')) return null
    if (ssid.equals("unknown ssid", ignoreCase = true)) return null
    if (ssid.equals("redacted", ignoreCase = true)) return null
    return ssid
}

private val IPV4 = Regex("""\b((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\b""")

/**
 * Reads the default gateway out of whatever the platform's routing table looks
 * like — `route -n get default` on macOS, `ip route show default` on Linux and
 * Android, `route print -4` on Windows. Windows lists every interface's default
 * route in one table, so [interfaceIp] picks the row that left by ours.
 */
fun parseDefaultGateway(dump: String, interfaceIp: String? = null): String? {
    var fallback: String? = null
    for (line in dump.lineSequence()) {
        val text = line.trim()
        if (text.isEmpty()) continue

        var owner: String? = null
        val gateway = when {
            text.startsWith("gateway:") -> IPV4.find(text)?.value
            text.contains(" via ") -> IPV4.find(text.substringAfter(" via "))?.value
            // Windows: destination, netmask, gateway, interface, metric.
            text.startsWith("0.0.0.0") -> text.split(Regex("\\s+")).let { columns ->
                owner = columns.getOrNull(3)?.takeIf { IPV4.matches(it) }
                columns.getOrNull(2)?.takeIf { IPV4.matches(it) }
            }
            else -> null
        }?.takeUnless { it == "0.0.0.0" } ?: continue

        if (interfaceIp == null || owner == null || owner == interfaceIp) return gateway
        if (fallback == null) fallback = gateway
    }
    return fallback
}

/**
 * One block per interface. Split on blank lines rather than on a leading field
 * name, because `netsh` translates its labels and a Russian Windows would
 * otherwise look like a machine with no Wi-Fi at all.
 */
internal fun blocksOf(dump: String): List<String> = dump
    .split(Regex("\\r?\\n\\s*\\r?\\n"))
    .filter { it.isNotBlank() }

/** Whether [block] mentions [mac], whatever spelling the tool that printed it used. */
internal fun blockHasMac(block: String, mac: String): Boolean =
    MAC_SHAPED.findAll(block).any { normalizeMac(it.value) == mac }

private val MAC_SHAPED = Regex("[0-9a-fA-F]{1,2}([:-][0-9a-fA-F]{1,2}){5}")

/** `Key : value` as every one of these tools prints it, colon-separated. */
internal fun fieldIn(block: String, name: String): String? {
    for (line in block.lineSequence()) {
        val label = line.substringBefore(':', "").trim()
        if (!label.equals(name, ignoreCase = true)) continue
        return line.substringAfter(':').trim().ifEmpty { null }
    }
    return null
}
