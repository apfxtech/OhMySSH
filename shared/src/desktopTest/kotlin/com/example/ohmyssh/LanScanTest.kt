package com.example.ohmyssh

import androidx.compose.ui.unit.dp
import com.example.ohmyssh.components.QCol
import com.example.ohmyssh.components.kTableFlexMinWidth
import com.example.ohmyssh.components.layoutTableColumns
import com.example.ohmyssh.net.LanInterface
import com.example.ohmyssh.net.ProbeCanary
import com.example.ohmyssh.net.SWEEP_LIMIT
import com.example.ohmyssh.net.canonicalIpv6
import com.example.ohmyssh.net.chooseSweepInterface
import com.example.ohmyssh.net.formatIpv4
import com.example.ohmyssh.net.isLinkLocalIpv6
import com.example.ohmyssh.net.networkOf
import com.example.ohmyssh.net.normalizeMac
import com.example.ohmyssh.net.parseIpv4
import com.example.ohmyssh.net.parseNeighbours
import com.example.ohmyssh.net.probesAreHonest
import com.example.ohmyssh.net.sweepRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanScanTest {
    @Test
    fun `parses and formats ipv4`() {
        assertEquals(0L, parseIpv4("0.0.0.0"))
        assertEquals(3232235777L, parseIpv4("192.168.1.1"))
        assertEquals("192.168.1.1", formatIpv4(3232235777L))
        assertNull(parseIpv4("192.168.1"))
        assertNull(parseIpv4("192.168.1.256"))
        assertNull(parseIpv4("fe80::1"))
    }

    @Test
    fun `sweeps a slash 24 without network or broadcast`() {
        val hosts = sweepRange("192.168.1.42", 24)
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
    }

    @Test
    fun `collapses a wide prefix onto the local slash 24`() {
        val hosts = sweepRange("10.4.7.9", 8)
        assertEquals(254, hosts.size)
        assertEquals("10.4.7.1", hosts.first())
        assertTrue(hosts.size <= SWEEP_LIMIT)
    }

    @Test
    fun `keeps a narrow prefix as declared`() {
        assertEquals(listOf("192.168.1.65", "192.168.1.66"), sweepRange("192.168.1.65", 30))
        assertEquals(listOf("192.168.1.65"), sweepRange("192.168.1.65", 32))
    }

    @Test
    fun `names the network of an address`() {
        assertEquals("192.168.1.0/24", networkOf("192.168.1.42", 24))
        assertEquals("10.0.0.0/8", networkOf("10.4.7.9", 8))
    }

    @Test
    fun `normalizes macs from every dump format`() {
        assertEquals("3c:22:fb:01:02:03", normalizeMac("3C:22:FB:01:02:03"))
        assertEquals("3c:22:fb:01:02:03", normalizeMac("3c-22-fb-01-02-03"))
        assertEquals("3c:22:fb:01:02:03", normalizeMac("3c:22:fb:1:2:3"))
        assertNull(normalizeMac("ff:ff:ff:ff:ff:ff"))
        assertNull(normalizeMac("00:00:00:00:00:00"))
        assertNull(normalizeMac("3c:22:fb:01:02"))
        assertNull(normalizeMac("incomplete"))
    }

    @Test
    fun `reads macos arp and ndp dumps`() {
        val dump = """
            ? (192.168.1.1) at 3c:22:fb:1:2:3 on en0 ifscope [ethernet]
            ? (192.168.1.55) at (incomplete) on en0 ifscope [ethernet]
            fe80::1%en0                          3c:22:fb:1:2:3     en0 23h59m58s R  R
        """.trimIndent()

        val entries = parseNeighbours(dump)
        assertEquals(2, entries.size)
        assertEquals("192.168.1.1", entries[0].ip)
        assertEquals("3c:22:fb:01:02:03", entries[0].mac)
        // The MAC is cut out before the address is read, or its colons would win.
        assertEquals("fe80::1%en0", entries[1].ip)
    }

    @Test
    fun `reads linux and windows dumps`() {
        val dump = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.10     0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan0
            192.168.1.11 dev wlan0 lladdr aa:bb:cc:dd:ee:02 REACHABLE
            192.168.1.12 dev wlan0  FAILED
              192.168.1.13          aa-bb-cc-dd-ee-03     dynamic
        """.trimIndent()

        val entries = parseNeighbours(dump)
        assertEquals(
            listOf("192.168.1.10", "192.168.1.11", "192.168.1.13"),
            entries.map { it.ip },
        )
        assertEquals("aa:bb:cc:dd:ee:03", entries.last().mac)
    }

    @Test
    fun `skips a vpn tunnel when choosing the interface to sweep`() {
        val tunnel = LanInterface("utun75", "172.18.0.1", 30)
        val wifi = LanInterface("en0", "192.168.137.253", 24, mac = "3a:33:54:78:de:34")

        // The kernel routes off-link traffic down the tunnel, but the LAN is on en0.
        assertEquals("en0", chooseSweepInterface(listOf(tunnel, wifi), "172.18.0.1")?.name)
        assertEquals("en0", chooseSweepInterface(listOf(tunnel, wifi), "192.168.137.253")?.name)
        // Nothing better than the tunnel: sweep it rather than give up.
        assertEquals("utun75", chooseSweepInterface(listOf(tunnel), "172.18.0.1")?.name)
        assertNull(chooseSweepInterface(emptyList(), "172.18.0.1"))
    }

    @Test
    fun `prefers the routed adapter over another real one`() {
        val wired = LanInterface("en5", "10.0.9.4", 24, mac = "aa:bb:cc:dd:ee:01")
        val wifi = LanInterface("en0", "192.168.1.20", 24, mac = "aa:bb:cc:dd:ee:02")
        assertEquals("en0", chooseSweepInterface(listOf(wired, wifi), "192.168.1.20")?.name)
    }

    @Test
    fun `keeps probe results when only the tunnel answers for everything`() {
        // A full tunnel holds the route to the canary while the LAN keeps its own,
        // so its "everything answers" verdict describes a path the sweep never takes.
        val tunnel = ProbeCanary(answered = true, source = "172.18.0.1")
        assertTrue(probesAreHonest(tunnel, "192.168.137.253"))
        val silent = ProbeCanary(answered = false, source = "192.168.137.253")
        assertTrue(probesAreHonest(silent, "192.168.137.253"))
    }

    @Test
    fun `drops probe results when the swept path answers for everything`() {
        val swept = ProbeCanary(answered = true, source = "192.168.137.253")
        assertFalse(probesAreHonest(swept, "192.168.137.253"))
        // No source to compare against: believe the verdict rather than the probes.
        assertFalse(probesAreHonest(ProbeCanary(answered = true, source = null), "192.168.137.253"))
    }

    @Test
    fun `spots link local ipv6`() {
        assertTrue(isLinkLocalIpv6("fe80::1"))
        assertTrue(isLinkLocalIpv6("FE80::1%en0"))
        assertTrue(!isLinkLocalIpv6("2001:db8::1"))
    }

    @Test
    fun `collapses both spellings of one ipv6 address`() {
        // What the JVM prints and what a neighbour dump prints, same address.
        assertEquals(
            canonicalIpv6("fe80::14b6:c343:6419:2e88"),
            canonicalIpv6("fe80:0:0:0:14b6:c343:6419:2e88"),
        )
        assertEquals("fe80::14b6:c343:6419:2e88", canonicalIpv6("fe80:0:0:0:14b6:c343:6419:2e88"))
        assertEquals("fe80::1", canonicalIpv6("FE80:0000:0000:0000:0000:0000:0000:0001%en0"))
        assertEquals("2001:db8:0:1:1:1:1:1", canonicalIpv6("2001:db8:0:1:1:1:1:1"))
        assertEquals("::", canonicalIpv6("0:0:0:0:0:0:0:0"))
        // Unparseable input stays as it came in rather than being mangled.
        assertEquals("not-an-address", canonicalIpv6("not-an-address"))
        assertEquals("::ffff:192.168.1.1", canonicalIpv6("::ffff:192.168.1.1"))
    }

    @Test
    fun `drops columns until the flexible one fits`() {
        val columns = listOf(
            QCol("Host", 0.dp, sortKey = "host"),
            QCol("IPv4", 132.dp, sortKey = "ipv4", mono = true),
            QCol("IPv6", 230.dp, sortKey = "ipv6", mono = true, hideLevel = 2),
            QCol("MAC", 148.dp, sortKey = "mac", mono = true, hideLevel = 3),
            QCol("Ping", 66.dp, sortKey = "rtt", right = true, hideLevel = 1),
        )
        val rows = listOf("2001:db8:1234:5678:9abc:def0:1234:5678")
        fun value(col: QCol, row: String) = if (col.sortKey == "ipv6") row else "192.168.1.100"

        val wide = layoutTableColumns(columns, 1200.dp, rows, ::value)
        assertEquals(listOf("Host", "IPv4", "IPv6", "MAC", "Ping"), wide.map { it.col.label })
        assertTrue(wide.first().width >= kTableFlexMinWidth)

        val narrow = layoutTableColumns(columns, 420.dp, rows, ::value)
        assertEquals(listOf("Host", "IPv4", "MAC"), narrow.map { it.col.label })
        assertTrue(narrow.first().width >= kTableFlexMinWidth)

        // Even at phone width the flexible column keeps its floor.
        val tiny = layoutTableColumns(columns, 240.dp, rows, ::value)
        assertEquals(listOf("Host", "IPv4"), tiny.map { it.col.label })
        assertTrue(tiny.first().width >= kTableFlexMinWidth)
    }
}
