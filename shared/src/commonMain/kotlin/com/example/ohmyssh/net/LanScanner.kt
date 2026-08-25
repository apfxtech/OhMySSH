package com.example.ohmyssh.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

data class LanDevice(
    val ipv4: String,
    val ipv6: List<String> = emptyList(),
    val mac: String? = null,
    val hostname: String? = null,
    val rttMs: Int? = null,
    val self: Boolean = false,
) {
    val order: Long get() = parseIpv4(ipv4) ?: 0L

    val shortName: String? get() = hostname?.substringBefore('.')?.ifEmpty { null }
}

private typealias Found = MutableMap<String, LanDevice>

/**
 * Sweeps the LAN this device is attached to and keeps the answers as observable
 * state, so a refresh streams rows in instead of blocking on the whole sweep.
 */
object LanScanner {
    private const val PROBE_TIMEOUT_MS = 250
    private const val PROBES_IN_FLIGHT = 64
    private const val LOOKUPS_IN_FLIGHT = 12
    private const val LOOKUP_TIMEOUT_MS = 1500L

    private val probe = createLanProbe()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Bumped per sweep so a cancelled one stops publishing over its replacement. */
    private var generation = 0

    var devices: List<LanDevice> by mutableStateOf(emptyList())
        private set

    var scanning: Boolean by mutableStateOf(false)
        private set

    var probed: Int by mutableStateOf(0)
        private set

    var total: Int by mutableStateOf(0)
        private set

    var subnet: String? by mutableStateOf(null)
        private set

    var interfaceName: String? by mutableStateOf(null)
        private set

    var lastError: String? by mutableStateOf(null)
        private set

    var hasSwept: Boolean by mutableStateOf(false)
        private set

    /** False once a sweep has caught the network answering for a bogus address. */
    var trustsProbes: Boolean by mutableStateOf(true)
        private set

    val progress: Float? get() = if (!scanning || total == 0) null else probed.toFloat() / total

    fun refresh() {
        if (scanning) return
        start()
    }

    fun cancel() {
        job?.cancel()
        job = null
        scanning = false
    }

    private fun start() {
        val id = ++generation
        // Flipped before the launch: two taps in one frame would otherwise both
        // pass the guard in refresh() and sweep the subnet twice.
        scanning = true
        job = scope.launch {
            try {
                sweep(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (id == generation) lastError = error.message?.ifEmpty { null } ?: "$error"
                Log.error("lan", "scan failed: $error", error)
            } finally {
                if (id == generation) scanning = false
            }
        }
    }

    private suspend fun sweep(id: Int) {
        probed = 0
        total = 0
        lastError = null

        val primary = chooseSweepInterface(probe.interfaces(), probe.outboundIpv4())
        if (primary == null) {
            publish(id, mutableMapOf())
            subnet = null
            interfaceName = null
            lastError = "No interface with an IPv4 address is up"
            hasSwept = true
            return
        }

        interfaceName = primary.name
        subnet = networkOf(primary.ipv4, primary.prefixLength)

        trustsProbes = probesAreHonest(probe.probeCanary(), primary.ipv4)

        val targets = sweepRange(primary.ipv4, primary.prefixLength)
        total = targets.size
        val found: Found = mutableMapOf(
            primary.ipv4 to LanDevice(
                ipv4 = primary.ipv4,
                ipv6 = orderIpv6(primary.ipv6),
                mac = primary.mac,
                rttMs = 0,
                self = true,
            ),
        )
        publish(id, found)

        pingSweep(id, found, targets, primary.ipv4)
        mergeNeighbours(id, found, targets.toSet())
        resolveNames(id, found)
        hasSwept = true
    }

    private suspend fun pingSweep(
        id: Int,
        found: Found,
        targets: List<String>,
        selfIp: String,
    ) = coroutineScope {
        val gate = Semaphore(PROBES_IN_FLIGHT)
        val answers = Channel<Pair<String, Int?>>(Channel.UNLIMITED)
        val workers = targets.map { ip ->
            launch {
                val rtt = if (ip == selfIp) 0 else gate.withPermit { probe.ping(ip, PROBE_TIMEOUT_MS) }
                answers.send(ip to rtt)
            }
        }
        launch {
            workers.joinAll()
            answers.close()
        }

        // A single consumer owns the counter and the row map; incrementing from 64
        // probe coroutines would drop updates and stall the progress bar short.
        for ((ip, rtt) in answers) {
            probed++
            // The probes still run when they cannot be trusted — the packets are
            // what pull each address into the neighbour table — but an answer
            // from a proxy that accepts everything is not a device.
            if (rtt == null || !trustsProbes) continue
            val known = found[ip]
            found[ip] = known?.copy(rttMs = known.rttMs ?: rtt) ?: LanDevice(ipv4 = ip, rttMs = rtt)
            publish(id, found)
        }
    }

    private suspend fun mergeNeighbours(id: Int, found: Found, targets: Set<String>) {
        val neighbours = probe.neighbours()
        if (neighbours.isEmpty()) return

        val macForIp = mutableMapOf<String, String>()
        val ipv6ForMac = mutableMapOf<String, MutableList<String>>()
        for (entry in neighbours) {
            val mac = normalizeMac(entry.mac) ?: continue
            val ip = entry.ip.substringBefore('%')
            if (parseIpv4(ip) != null) {
                macForIp[ip] = mac
            } else if (ip.contains(':')) {
                ipv6ForMac.getOrPut(mac) { mutableListOf() }.add(ip)
            }
        }

        // An ARP entry is proof of life on its own: plenty of devices answer the
        // address resolution the sweep triggers and then drop every TCP probe.
        for ((ip, mac) in macForIp) {
            if (ip in targets && ip !in found) found[ip] = LanDevice(ipv4 = ip, mac = mac)
        }

        for ((ip, device) in found.toList()) {
            val mac = device.mac ?: macForIp[ip]
            val ipv6 = device.ipv6 + (mac?.let { ipv6ForMac[it] } ?: emptyList())
            found[ip] = device.copy(mac = mac, ipv6 = orderIpv6(ipv6))
        }
        publish(id, found)
    }

    private suspend fun resolveNames(id: Int, found: Found) = coroutineScope {
        val gate = Semaphore(LOOKUPS_IN_FLIGHT)
        val resolved = found.keys.toList().map { ip ->
            async {
                val name = gate.withPermit {
                    withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { probe.reverseDns(ip) }
                }
                val ipv6 = if (name == null) {
                    emptyList()
                } else {
                    gate.withPermit {
                        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { probe.resolveIpv6(name) }
                    } ?: emptyList()
                }
                Triple(ip, name, ipv6)
            }
        }.awaitAll()

        for ((ip, name, ipv6) in resolved) {
            val device = found[ip] ?: continue
            found[ip] = device.copy(
                hostname = name ?: device.hostname,
                ipv6 = orderIpv6(device.ipv6 + ipv6),
            )
        }
        publish(id, found)
    }

    private fun publish(id: Int, found: Found) {
        if (id != generation) return
        devices = found.values.sortedBy { it.order }
    }

    private fun orderIpv6(addresses: List<String>): List<String> = addresses
        .map { canonicalIpv6(it) }
        .filter { it.isNotEmpty() && it != "::1" }
        .distinct()
        .sortedBy { if (isLinkLocalIpv6(it)) 1 else 0 }
}
