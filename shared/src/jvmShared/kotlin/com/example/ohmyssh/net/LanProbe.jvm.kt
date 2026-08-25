package com.example.ohmyssh.net

import com.example.ohmyssh.platform.AppPlatform
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

actual fun createLanProbe(): LanProbe = JvmLanProbe

/** Ports common enough that a knock on one of them usually finds an answer. */
private val PROBE_PORTS = intArrayOf(22, 80, 443, 445)

private const val PROC_NET_ARP = "/proc/net/arp"

/** Reached to learn which of our own addresses the kernel would send from. */
private const val BEACON_ADDRESS = "8.8.8.8"

/** RFC 5737 TEST-NET-1: documentation only, so nothing may ever answer for it. */
private const val CANARY_ADDRESS = "192.0.2.1"
private const val CANARY_TIMEOUT_MS = 400

private object JvmLanProbe : LanProbe {
    override suspend fun interfaces(): List<LanInterface> = withContext(Dispatchers.IO) {
        val adapters = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrNull() ?: emptyList()

        adapters.mapNotNull { adapter ->
            val usable = runCatching { adapter.isUp && !adapter.isLoopback }.getOrDefault(false)
            if (!usable) return@mapNotNull null
            val bindings = runCatching { adapter.interfaceAddresses }.getOrNull() ?: return@mapNotNull null
            val ipv4 = bindings.firstOrNull { it.address is Inet4Address } ?: return@mapNotNull null
            val address = ipv4.address.hostAddress ?: return@mapNotNull null

            LanInterface(
                name = adapter.name ?: address,
                ipv4 = address,
                prefixLength = ipv4.networkPrefixLength.toInt(),
                ipv6 = bindings.mapNotNull { (it.address as? Inet6Address)?.let(::plainAddress) },
                mac = runCatching { adapter.hardwareAddress }.getOrNull()?.let(::formatMac),
            )
        }
    }

    override suspend fun outboundIpv4(): String? = withContext(Dispatchers.IO) {
        addressFor(BEACON_ADDRESS)?.let(::sourceAddressFor)
    }

    override suspend fun probeCanary(): ProbeCanary = withContext(Dispatchers.IO) {
        val canary = addressFor(CANARY_ADDRESS)
            ?: return@withContext ProbeCanary(answered = false, source = null)
        val source = sourceAddressFor(canary)

        if (runCatching { canary.isReachable(CANARY_TIMEOUT_MS) }.getOrDefault(false)) {
            Log.info("lan", "$CANARY_ADDRESS answered a ping from ${source ?: "?"}")
            return@withContext ProbeCanary(answered = true, source = source)
        }
        val faked = PROBE_PORTS.any { knocks(canary, it, CANARY_TIMEOUT_MS) }
        if (faked) Log.info("lan", "$CANARY_ADDRESS accepted a connection from ${source ?: "?"}")
        ProbeCanary(answered = faked, source = source)
    }

    override suspend fun ping(ip: String, timeoutMs: Int): Int? = withContext(Dispatchers.IO) {
        val address = addressFor(ip) ?: return@withContext null
        val started = System.nanoTime()

        // ICMP where the OS allows it unprivileged, a TCP echo knock where it does not.
        if (runCatching { address.isReachable(timeoutMs) }.getOrDefault(false)) {
            return@withContext elapsedMs(started)
        }
        for (port in PROBE_PORTS) {
            if (knocks(address, port, timeoutMs)) return@withContext elapsedMs(started)
        }
        null
    }

    override suspend fun neighbours(): List<LanNeighbour> = withContext(Dispatchers.IO) {
        val dumps = StringBuilder()
        val arpFile = File(PROC_NET_ARP)
        if (runCatching { arpFile.canRead() }.getOrDefault(false)) {
            runCatching { dumps.appendLine(arpFile.readText()) }
        }
        for (command in neighbourCommands()) dumps.appendLine(runCommand(command))
        parseNeighbours(dumps.toString())
    }

    override suspend fun reverseDns(ip: String): String? = withContext(Dispatchers.IO) {
        val address = addressFor(ip) ?: return@withContext null
        val name = runCatching { address.canonicalHostName }.getOrNull()?.trimEnd('.')
        name?.takeUnless { it.isEmpty() || it == ip }
    }

    override suspend fun resolveIpv6(hostname: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            InetAddress.getAllByName(hostname)
                .filterIsInstance<Inet6Address>()
                .mapNotNull(::plainAddress)
        }.getOrDefault(emptyList())
    }
}

private fun neighbourCommands(): List<List<String>> = when (appPlatform) {
    AppPlatform.MACOS -> listOf(
        listOf("/usr/sbin/arp", "-an"),
        listOf("/usr/sbin/ndp", "-an"),
    )
    AppPlatform.LINUX -> listOf(
        listOf("ip", "neigh", "show"),
        listOf("ip", "-6", "neigh", "show"),
    )
    AppPlatform.ANDROID -> listOf(
        listOf("/system/bin/ip", "neigh", "show"),
        listOf("/system/bin/ip", "-6", "neigh", "show"),
    )
    AppPlatform.WINDOWS -> listOf(
        listOf("arp", "-a"),
        listOf("netsh", "interface", "ipv6", "show", "neighbors"),
    )
    AppPlatform.IOS -> emptyList()
}

private fun runCommand(command: List<String>): String = try {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy()
    output
} catch (error: Exception) {
    Log.info("lan", "${command.first()} gave nothing: ${error.message}")
    ""
}

private val MAC_TOKEN = Regex("[0-9a-fA-F]{1,2}([:-][0-9a-fA-F]{1,2}){5}")
private val IPV4_TOKEN =
    Regex("""\b((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\b""")
private val IPV6_TOKEN = Regex("""\b([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(%[0-9a-zA-Z]+)?""")

/**
 * Pulls ip/MAC pairs out of whatever the platform's neighbour dump looks like —
 * `/proc/net/arp`, `ip neigh`, `arp -an`, `ndp -an` or `netsh`. The MAC is cut
 * out of the line before the address is read, or its colons would parse as IPv6.
 */
internal fun parseNeighbours(dumps: String): List<LanNeighbour> {
    val entries = mutableListOf<LanNeighbour>()
    for (line in dumps.lineSequence()) {
        val token = MAC_TOKEN.find(line) ?: continue
        val mac = normalizeMac(token.value) ?: continue
        val rest = line.removeRange(token.range)
        val ip = IPV4_TOKEN.find(rest)?.value ?: IPV6_TOKEN.find(rest)?.value ?: continue
        entries.add(LanNeighbour(ip, mac))
    }
    return entries
}

private fun addressFor(ip: String): InetAddress? =
    runCatching { InetAddress.getByName(ip) }.getOrNull()

/**
 * The address the kernel would send to [destination] from. A connected UDP socket
 * sends nothing; it just makes the kernel pick the route, and with it the source.
 */
private fun sourceAddressFor(destination: InetAddress): String? = runCatching {
    DatagramSocket().use { socket ->
        socket.connect(InetSocketAddress(destination, 53))
        (socket.localAddress as? Inet4Address)?.hostAddress
    }
}.getOrNull()?.takeUnless { it == "0.0.0.0" }

private fun knocks(address: InetAddress, port: Int, timeoutMs: Int): Boolean = try {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(address, port), timeoutMs)
        true
    }
} catch (refused: ConnectException) {
    // A reset is still an answer: something is there, closing the door.
    true
} catch (unreachable: IOException) {
    false
}

private fun elapsedMs(startedNanos: Long): Int =
    ((System.nanoTime() - startedNanos) / 1_000_000).toInt()

private fun plainAddress(address: Inet6Address): String? =
    address.hostAddress?.substringBefore('%')?.takeUnless { it.isEmpty() }

private fun formatMac(bytes: ByteArray): String? =
    if (bytes.size != 6) null else normalizeMac(bytes.joinToString(":") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    })
