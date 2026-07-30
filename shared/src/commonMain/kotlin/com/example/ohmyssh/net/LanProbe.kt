package com.example.ohmyssh.net

/** One local interface that carries an IPv4 address, with whatever else sits on it. */
class LanInterface(
    val name: String,
    val ipv4: String,
    val prefixLength: Int,
    val ipv6: List<String> = emptyList(),
    val mac: String? = null,
)

/** An entry from an OS neighbour cache: ARP holds the IPv4 side, NDP the IPv6 one. */
class LanNeighbour(val ip: String, val mac: String)

interface LanProbe {
    suspend fun interfaces(): List<LanInterface>

    /** Source address the kernel picks for off-link traffic — it names the LAN we sit on. */
    suspend fun outboundIpv4(): String?

    /**
     * Probes an address that cannot exist. False means something on the path
     * answers for everything — a transparent proxy or a VPN client — so probe
     * results carry no information and only the neighbour table can be believed.
     */
    suspend fun probesAreHonest(): Boolean

    /** Round trip in milliseconds, or null when nothing answered within [timeoutMs]. */
    suspend fun ping(ip: String, timeoutMs: Int): Int?

    suspend fun neighbours(): List<LanNeighbour>

    suspend fun reverseDns(ip: String): String?

    suspend fun resolveIpv6(hostname: String): List<String>
}

expect fun createLanProbe(): LanProbe
