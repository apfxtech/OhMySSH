package com.example.ohmyssh.net

private const val IPV4_MAX = 0xFFFFFFFFL

/** Widest sweep a single refresh will do; anything wider collapses to the local /24. */
const val SWEEP_LIMIT = 1022

fun parseIpv4(ip: String): Long? {
    val parts = ip.split('.')
    if (parts.size != 4) return null
    var packed = 0L
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet < 0 || octet > 255) return null
        packed = (packed shl 8) or octet.toLong()
    }
    return packed
}

fun formatIpv4(packed: Long): String {
    val value = packed and IPV4_MAX
    return "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}." +
        "${(value shr 8) and 0xFF}.${value and 0xFF}"
}

fun isPrivateIpv4(ip: String): Boolean {
    val packed = parseIpv4(ip) ?: return false
    val first = (packed shr 24) and 0xFF
    val second = (packed shr 16) and 0xFF
    return when {
        first == 10L -> true
        first == 172L && second in 16L..31L -> true
        first == 192L && second == 168L -> true
        else -> false
    }
}

/** fe80::/10 — an address only usable with the interface it was learned on. */
fun isLinkLocalIpv6(ip: String): Boolean {
    val head = ip.substringBefore('%').lowercase()
    return head.startsWith("fe8") || head.startsWith("fe9") ||
        head.startsWith("fea") || head.startsWith("feb")
}

/**
 * RFC 5952 short form, scope suffix dropped. The JVM prints an address as
 * `fe80:0:0:0:14b6::2e88` while a neighbour dump prints `fe80::14b6:...:2e88`, and
 * without one spelling the same address would land in the list twice.
 */
fun canonicalIpv6(address: String): String {
    val raw = address.substringBefore('%').lowercase()
    val groups = expandIpv6(raw) ?: return raw

    var runStart = -1
    var runLength = 0
    var index = 0
    while (index < groups.size) {
        if (groups[index] != 0) {
            index++
            continue
        }
        var end = index
        while (end < groups.size && groups[end] == 0) end++
        if (end - index > runLength) {
            runStart = index
            runLength = end - index
        }
        index = end
    }
    if (runLength < 2) return groups.joinToString(":") { it.toString(16) }

    val head = groups.take(runStart).joinToString(":") { it.toString(16) }
    val tail = groups.drop(runStart + runLength).joinToString(":") { it.toString(16) }
    return "$head::$tail"
}

private fun expandIpv6(raw: String): IntArray? {
    if (!raw.contains(':') || raw.contains('.')) return null
    val halves = raw.split("::")
    if (halves.size > 2) return null

    fun groupsOf(text: String): List<Int>? {
        if (text.isEmpty()) return emptyList()
        val parsed = text.split(':').map { it.toIntOrNull(16) ?: return null }
        return if (parsed.any { it < 0 || it > 0xFFFF }) null else parsed
    }

    val head = groupsOf(halves[0]) ?: return null
    val tail = if (halves.size == 2) groupsOf(halves[1]) ?: return null else emptyList()
    if (halves.size == 1) return if (head.size == 8) head.toIntArray() else null

    val gap = 8 - head.size - tail.size
    if (gap < 1) return null
    return (head + List(gap) { 0 } + tail).toIntArray()
}

fun networkOf(ip: String, prefixLength: Int): String? {
    val packed = parseIpv4(ip) ?: return null
    val prefix = prefixLength.coerceIn(0, 32)
    val mask = if (prefix == 0) 0L else (IPV4_MAX shl (32 - prefix)) and IPV4_MAX
    return "${formatIpv4(packed and mask)}/$prefix"
}

/**
 * Every host address to probe for [ip]/[prefixLength]. A prefix wider than /22
 * would mean thousands of probes for a LAN that in practice fills one /24, so it
 * collapses onto the /24 the address itself sits in.
 */
fun sweepRange(ip: String, prefixLength: Int, limit: Int = SWEEP_LIMIT): List<String> {
    val packed = parseIpv4(ip) ?: return emptyList()
    val declared = prefixLength.coerceIn(0, 32)
    if (declared >= 31) return listOf(ip)

    val prefix = if ((1L shl (32 - declared)) - 2 > limit) 24 else declared
    val mask = (IPV4_MAX shl (32 - prefix)) and IPV4_MAX
    val network = packed and mask
    val broadcast = network or (mask.inv() and IPV4_MAX)

    val hosts = ArrayList<String>((broadcast - network - 1).toInt().coerceAtMost(limit))
    var address = network + 1
    while (address < broadcast && hosts.size < limit) {
        hosts.add(formatIpv4(address))
        address++
    }
    return hosts
}

/**
 * Picks the interface to sweep. The kernel's own outbound choice comes first, but
 * a VPN tunnel holds that route while carrying a point-to-point /30 with no
 * neighbours on it — so a real adapter (one with a MAC) on a private subnet wins
 * over one, which is what keeps the scan pointed at the LAN while a VPN is up.
 */
fun chooseSweepInterface(interfaces: List<LanInterface>, outboundIpv4: String?): LanInterface? {
    fun isLan(adapter: LanInterface): Boolean = adapter.mac != null &&
        isPrivateIpv4(adapter.ipv4) &&
        adapter.prefixLength in 8..29

    val outbound = interfaces.firstOrNull { it.ipv4 == outboundIpv4 }
    if (outbound != null && isLan(outbound)) return outbound
    return interfaces.firstOrNull(::isLan)
        ?: outbound
        ?: interfaces.firstOrNull { isPrivateIpv4(it.ipv4) }
        ?: interfaces.firstOrNull()
}

/**
 * Whether ping and knock answers from a sweep of [sweepIpv4] mean anything. A
 * canary that left from a different address of ours measured a different path:
 * a full-tunnel VPN answers for the whole space it carries, while the LAN it
 * leaves on its own route still answers only for the devices on it. Applying
 * the tunnel's verdict there would throw away every real device the sweep found.
 */
fun probesAreHonest(canary: ProbeCanary, sweepIpv4: String): Boolean =
    !canary.answered || (canary.source != null && canary.source != sweepIpv4)

private val MAC_SHAPE = Regex("^[0-9a-f]{1,2}([:-][0-9a-f]{1,2}){5}$")

/**
 * Lowercase colon form, zero padded. Neighbour tables print MACs three ways —
 * `3c-22-fb-01-02-03` on Windows, `3c:22:fb:1:2:3` on macOS, padded on Linux —
 * and they all have to collapse onto one string or the IPv6 join by MAC misses.
 */
fun normalizeMac(raw: String): String? {
    val lower = raw.trim().lowercase()
    if (!MAC_SHAPE.matches(lower)) return null
    val octets = lower.split(':', '-').map { it.padStart(2, '0') }
    if (octets.all { it == "00" } || octets.all { it == "ff" }) return null
    return octets.joinToString(":")
}
