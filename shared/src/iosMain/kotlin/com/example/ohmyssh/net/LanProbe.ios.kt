package com.example.ohmyssh.net

import com.example.ohmyssh.platform.epochMicros
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AI_NUMERICHOST
import platform.posix.ECONNREFUSED
import platform.posix.EINPROGRESS
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.IPPROTO_TCP
import platform.posix.IPPROTO_UDP
import platform.posix.NI_MAXHOST
import platform.posix.NI_NUMERICHOST
import platform.posix.O_NONBLOCK
import platform.posix.POLLOUT
import platform.posix.SOCK_DGRAM
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getnameinfo
import platform.posix.getsockname
import platform.posix.getsockopt
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.sockaddr_storage
import platform.posix.socket
import platform.posix.socklen_t
import platform.posix.socklen_tVar

actual fun createLanProbe(): LanProbe = IosLanProbe

private val PROBE_PORTS = intArrayOf(22, 80, 443, 445)

/** Reached to learn which of our own addresses the kernel would send from. */
private const val IPV4_BEACON = "8.8.8.8"
private const val IPV6_BEACON = "2001:4860:4860::8888"

/** RFC 5737 TEST-NET-1: documentation only, so nothing may ever answer for it. */
private const val CANARY_ADDRESS = "192.0.2.1"
private const val CANARY_TIMEOUT_MS = 400

@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
private object IosLanProbe : LanProbe {
    // Native has no Dispatchers.IO, and Default is sized to the core count, which
    // would let a handful of blocked sockets serialise the whole sweep.
    private val sockets by lazy { newFixedThreadPoolContext(16, "lan-sweep") }

    override suspend fun interfaces(): List<LanInterface> = withContext(sockets) {
        val ipv4 = outboundAddress(AF_INET, IPV4_BEACON) ?: return@withContext emptyList()
        listOfNotNull(
            LanInterface(
                name = "",
                ipv4 = ipv4,
                // Kotlin/Native's posix package leaves out getifaddrs, so the real
                // netmask is out of reach here; a /24 is what LANs run in practice.
                prefixLength = 24,
                ipv6 = listOfNotNull(outboundAddress(AF_INET6, IPV6_BEACON)),
            ),
        )
    }

    override suspend fun outboundIpv4(): String? = withContext(sockets) {
        outboundAddress(AF_INET, IPV4_BEACON)
    }

    override suspend fun probesAreHonest(): Boolean = withContext(sockets) {
        val canary = parseIpv4(CANARY_ADDRESS) ?: return@withContext true
        !knock(canary, PROBE_PORTS, CANARY_TIMEOUT_MS)
    }

    override suspend fun ping(ip: String, timeoutMs: Int): Int? = withContext(sockets) {
        val packed = parseIpv4(ip) ?: return@withContext null
        val started = epochMicros()
        if (!knock(packed, PROBE_PORTS, timeoutMs)) return@withContext null
        ((epochMicros() - started) / 1000).toInt()
    }

    /** The ARP cache sits behind sysctl route access that iOS does not hand to apps. */
    override suspend fun neighbours(): List<LanNeighbour> = emptyList()

    override suspend fun reverseDns(ip: String): String? = withContext(sockets) {
        val packed = parseIpv4(ip) ?: return@withContext null
        memScoped {
            val address = alloc<sockaddr_in>()
            fillIpv4(address, packed, 0)
            val name = addressText(
                address.ptr.reinterpret(),
                sizeOf<sockaddr_in>().convert(),
                numeric = false,
            )
            name?.trimEnd('.')?.takeUnless { it.isEmpty() || it == ip }
        }
    }

    override suspend fun resolveIpv6(hostname: String): List<String> = withContext(sockets) {
        val found = mutableListOf<String>()
        memScoped {
            walkAddresses<Unit>(hostname, null, AF_INET6, numericHost = false) { node ->
                val address = node.pointed.ai_addr ?: return@walkAddresses null
                addressText(address, node.pointed.ai_addrlen, numeric = true)?.let(found::add)
                null
            }
        }
        found.distinct()
    }
}

/**
 * One non-blocking connect per port, all waited on together: a single 250 ms poll
 * for every port instead of one per port keeps a whole-subnet sweep in seconds.
 */
@OptIn(ExperimentalForeignApi::class)
private fun knock(packed: Long, ports: IntArray, timeoutMs: Int): Boolean = memScoped {
    val descriptors = IntArray(ports.size) { -1 }
    try {
        val pollers = allocArray<pollfd>(ports.size)
        val target = alloc<sockaddr_in>()
        var waiting = 0

        for (port in ports) {
            val descriptor = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
            if (descriptor < 0) continue
            fcntl(descriptor, F_SETFL, fcntl(descriptor, F_GETFL, 0) or O_NONBLOCK)
            fillIpv4(target, packed, port)

            if (connect(descriptor, target.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
                close(descriptor)
                return@memScoped true
            }
            val reason = errno
            if (reason != EINPROGRESS) {
                close(descriptor)
                // A refusal is an answer: something is there, closing the door.
                if (reason == ECONNREFUSED) return@memScoped true
                continue
            }
            descriptors[waiting] = descriptor
            pollers[waiting].fd = descriptor
            pollers[waiting].events = POLLOUT.convert()
            waiting++
        }

        if (waiting == 0) return@memScoped false
        if (poll(pollers, waiting.convert(), timeoutMs) <= 0) return@memScoped false

        val pending = alloc<IntVar>()
        val length = alloc<socklen_tVar>()
        for (index in 0 until waiting) {
            if (pollers[index].revents.toInt() == 0) continue
            length.value = sizeOf<IntVar>().convert()
            if (getsockopt(pollers[index].fd, SOL_SOCKET, SO_ERROR, pending.ptr, length.ptr) != 0) {
                continue
            }
            if (pending.value == 0 || pending.value == ECONNREFUSED) return@memScoped true
        }
        false
    } finally {
        for (descriptor in descriptors) if (descriptor >= 0) close(descriptor)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun outboundAddress(family: Int, beacon: String): String? = memScoped {
    walkAddresses(beacon, "53", family, numericHost = true) { node ->
        val descriptor = socket(family, SOCK_DGRAM, IPPROTO_UDP)
        if (descriptor < 0) return@walkAddresses null
        try {
            val remote = node.pointed.ai_addr ?: return@walkAddresses null
            // Connecting a UDP socket sends nothing; it only makes the kernel
            // commit to a route, and with it to a source address.
            if (connect(descriptor, remote, node.pointed.ai_addrlen) != 0) return@walkAddresses null

            val local = alloc<sockaddr_storage>()
            val length = alloc<socklen_tVar>()
            length.value = sizeOf<sockaddr_storage>().convert()
            if (getsockname(descriptor, local.ptr.reinterpret(), length.ptr) != 0) {
                return@walkAddresses null
            }
            addressText(local.ptr.reinterpret(), length.value, numeric = true)
                ?.substringBefore('%')
                ?.takeUnless { it.isEmpty() || it == "0.0.0.0" || it == "::" }
        } finally {
            close(descriptor)
        }
    }
}

/**
 * Runs [visit] over the resolver's answers until one returns non-null, then frees
 * the list — the leak would otherwise be one addrinfo chain per lookup.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun <T> MemScope.walkAddresses(
    host: String,
    service: String?,
    family: Int,
    numericHost: Boolean,
    visit: (CPointer<addrinfo>) -> T?,
): T? {
    val hints = alloc<addrinfo>()
    memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
    hints.ai_family = family
    hints.ai_socktype = SOCK_STREAM
    if (numericHost) hints.ai_flags = AI_NUMERICHOST

    val answer = allocPointerTo<addrinfo>()
    if (getaddrinfo(host, service, hints.ptr, answer.ptr) != 0) return null
    val head = answer.value ?: return null
    try {
        var node: CPointer<addrinfo>? = head
        while (node != null) {
            val result = visit(node)
            if (result != null) return result
            node = node.pointed.ai_next
        }
        return null
    } finally {
        freeaddrinfo(head)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.addressText(
    address: CPointer<sockaddr>,
    length: socklen_t,
    numeric: Boolean,
): String? {
    val text = allocArray<ByteVar>(NI_MAXHOST)
    val flags = if (numeric) NI_NUMERICHOST else 0
    val failed = getnameinfo(
        address, length, text, NI_MAXHOST.convert(), null, 0.convert(), flags,
    ) != 0
    if (failed) return null
    return text.toKString().takeUnless { it.isEmpty() }
}

/**
 * Fills a sockaddr_in with [packed] and [port] in network order. Apple platforms
 * are little-endian, so both fields go in with their bytes swapped.
 */
@OptIn(ExperimentalForeignApi::class)
private fun fillIpv4(address: sockaddr_in, packed: Long, port: Int) {
    memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
    address.sin_len = sizeOf<sockaddr_in>().convert()
    address.sin_family = AF_INET.convert()
    address.sin_port = ((((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)).toUShort())
    address.sin_addr.s_addr = (
        (((packed shr 24) and 0xFF)) or
            (((packed shr 16) and 0xFF) shl 8) or
            (((packed shr 8) and 0xFF) shl 16) or
            ((packed and 0xFF) shl 24)
        ).toUInt()
}
