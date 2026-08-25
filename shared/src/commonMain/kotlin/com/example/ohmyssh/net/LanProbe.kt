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

/**
 * What probing an address that cannot exist did, and which of our own addresses
 * the probe left from. [source] is what makes the answer usable: a full tunnel
 * answers for every address it carries while the LAN keeps its own route, so a
 * verdict reached down the tunnel says nothing about the LAN being swept.
 */
class ProbeCanary(val answered: Boolean, val source: String?)

interface LanProbe {
    suspend fun interfaces(): List<LanInterface>

    /** Source address the kernel picks for off-link traffic — it names the LAN we sit on. */
    suspend fun outboundIpv4(): String?

    /**
     * Probes an address that cannot exist. An answer means something on that
     * path answers for everything — a transparent proxy or a VPN client — so
     * probes down it carry no information; [probesAreHonest] decides whether
     * that path is the one being swept.
     */
    suspend fun probeCanary(): ProbeCanary

    /** Round trip in milliseconds, or null when nothing answered within [timeoutMs]. */
    suspend fun ping(ip: String, timeoutMs: Int): Int?

    suspend fun neighbours(): List<LanNeighbour>

    suspend fun reverseDns(ip: String): String?

    suspend fun resolveIpv6(hostname: String): List<String>
}

expect fun createLanProbe(): LanProbe
