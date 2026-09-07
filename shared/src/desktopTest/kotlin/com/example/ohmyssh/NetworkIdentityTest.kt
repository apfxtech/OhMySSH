package com.example.ohmyssh

import com.example.ohmyssh.net.NetworkKind
import com.example.ohmyssh.net.cleanSsid
import com.example.ohmyssh.net.looksLikeAddress
import com.example.ohmyssh.net.networkKeyFor
import com.example.ohmyssh.net.networkKeyLabel
import com.example.ohmyssh.net.parseDefaultGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class NetworkIdentityTest {
    @Test
    fun `reads the gateway macos route prints`() {
        val dump = """
               route to: default
            destination: default
                   mask: default
                gateway: 10.237.184.249
              interface: en0
                  flags: <UP,GATEWAY,DONE,STATIC,PRCLONING,GLOBAL>
        """.trimIndent()
        assertEquals("10.237.184.249", parseDefaultGateway(dump))
    }

    @Test
    fun `reads the gateway ip route prints`() {
        val dump = "default via 192.168.1.1 dev wlan0 proto dhcp src 192.168.1.42 metric 600"
        assertEquals("192.168.1.1", parseDefaultGateway(dump))
    }

    @Test
    fun `picks the windows route that leaves by our interface`() {
        val dump = """
            ===========================================================================
            Active Routes:
            Network Destination        Netmask          Gateway       Interface  Metric
                      0.0.0.0          0.0.0.0     10.8.0.1        10.8.0.6     15
                      0.0.0.0          0.0.0.0    192.168.1.1     192.168.1.42     25
                192.168.1.0    255.255.255.0         On-link      192.168.1.42    281
            ===========================================================================
        """.trimIndent()
        assertEquals("192.168.1.1", parseDefaultGateway(dump, "192.168.1.42"))
        // With no interface to prefer, the first default route stands.
        assertEquals("10.8.0.1", parseDefaultGateway(dump))
    }

    @Test
    fun `has no gateway to report`() {
        assertNull(parseDefaultGateway(""))
        assertNull(parseDefaultGateway("route: writing to routing socket: not in table"))
    }

    @Test
    fun `drops an ssid the os refused to name`() {
        assertEquals("Home", cleanSsid("\"Home\""))
        assertEquals("Home", cleanSsid("  Home  "))
        assertNull(cleanSsid("<redacted>"))
        assertNull(cleanSsid("<unknown ssid>"))
        assertNull(cleanSsid(""))
        assertNull(cleanSsid(null))
    }

    @Test
    fun `prefers the router then the ssid then the subnet`() {
        val router = "3c:22:fb:01:02:03"
        // The SSID names the network but never identifies it: granting macOS
        // Location would otherwise renumber every network the day it arrives.
        assertEquals("lan:$router", networkKeyFor("Home", router, "192.168.1.0/24"))
        assertEquals("lan:$router", networkKeyFor(null, router, "192.168.1.0/24"))
        assertEquals("wifi:Home", networkKeyFor("Home", null, "192.168.1.0/24"))
        assertEquals("net:192.168.1.0/24", networkKeyFor(null, null, "192.168.1.0/24"))
        assertNull(networkKeyFor(null, null, null))
    }

    @Test
    fun `names a key nothing has been saved for`() {
        assertEquals("Home", networkKeyLabel("wifi:Home"))
        assertEquals("192.168.1.0/24", networkKeyLabel("net:192.168.1.0/24"))
        assertEquals("3c:22:fb:01:02:03", networkKeyLabel("lan:3c:22:fb:01:02:03"))
    }

    @Test
    fun `tells a name from an address`() {
        assertTrue(looksLikeAddress("192.168.0.0/24"))
        assertTrue(looksLikeAddress("10.0.0.1"))
        assertTrue(looksLikeAddress("3c:22:fb:01:02:03"))
        assertFalse(looksLikeAddress("Larka"))
        assertFalse(looksLikeAddress("Home 5G"))
        assertFalse(looksLikeAddress("192.168.1.x office"))
    }

    @Test
    fun `parses a kind off the wire`() {
        assertEquals(NetworkKind.WIFI, NetworkKind.parse("wifi"))
        assertEquals(NetworkKind.UNKNOWN, NetworkKind.parse("carrier pigeon"))
    }
}
