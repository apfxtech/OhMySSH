package com.example.ohmyssh

import com.example.ohmyssh.data.Vault
import com.example.ohmyssh.data.kKdfIterations
import com.example.ohmyssh.platform.VaultCrypto
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlutterVaultCompatTest {
    private lateinit var temp: File

    @BeforeTest
    fun setUp() {
        temp = File.createTempFile("ohmyssh_compat", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    @Test
    fun opensAnEnvelopeBuiltTheWayTheFlutterAppBuildsIt() = runBlocking {
        val password = "correct horse battery"
        val payload = """
            {"hosts":[{"id":"h1","label":"web-01","hostname":"10.0.0.5","port":2222,
            "identityId":"i1","knownHostKey":"SHA256:abc","osId":"ubuntu"}],
            "identities":[{"id":"i1","label":"root","username":"root","kind":"password",
            "password":"hunter2"}],
            "groups":[{"id":"g1","name":"Production"}],
            "serialDevices":[{"id":"s1","label":"","path":"/dev/ttyUSB0","baudRate":115200,
            "dataBits":8,"stopBits":1,"parity":"none","flowControl":"rtsCts","dtr":true,
            "rts":false,"vendorId":6790,"productId":29987}]}
        """.trimIndent().replace("\n", "")

        val b64 = Base64.getEncoder()
        val salt = VaultCrypto.randomBytes(16)
        val key = VaultCrypto.deriveKey(password, salt, kKdfIterations)
        val nonce = VaultCrypto.randomBytes(12)
        val (cipherText, mac) = VaultCrypto.encrypt(key, nonce, payload.encodeToByteArray())

        val envelope = """
            {"format":"ohmyssh.vault","version":1,
            "kdf":{"algo":"pbkdf2-hmac-sha256","iterations":$kKdfIterations,
            "salt":"${b64.encodeToString(salt)}"},
            "cipher":"aes-256-gcm","nonce":"${b64.encodeToString(nonce)}",
            "mac":"${b64.encodeToString(mac)}","data":"${b64.encodeToString(cipherText)}"}
        """.trimIndent().replace("\n", "")

        val path = File(temp, "flutter.vault").path
        File(path).writeText(envelope)

        val data = Vault.open(password, path).read()

        assertEquals(1, data.hosts.size)
        assertEquals("web-01", data.hosts.single().label)
        assertEquals(2222, data.hosts.single().port)
        assertEquals("SHA256:abc", data.hosts.single().knownHostKey)
        assertEquals("hunter2", data.identities.single().password)
        assertEquals("Production", data.groups.single().name)

        val device = data.serialDevices.single()
        assertEquals("/dev/ttyUSB0", device.path)
        assertEquals(115200, device.baudRate)
        assertEquals(com.example.ohmyssh.data.SerialFlowControl.RTS_CTS, device.flowControl)
        assertTrue(device.dtr)
        assertEquals(false, device.rts)
        assertEquals(0x1A86, device.vendorId)
        assertEquals(0x7523, device.productId)
    }

    @Test
    fun writesTheFieldNamesTheFlutterAppReads() = runBlocking {
        val path = File(temp, "written.vault").path
        val vault = Vault.create("pw", path, sampleData())
        val envelope = File(path).readText()

        for (field in listOf("format", "version", "kdf", "cipher", "nonce", "mac", "data")) {
            assertTrue(envelope.contains("\"$field\""), "envelope is missing \"$field\"")
        }
        assertTrue(envelope.contains("\"algo\":\"pbkdf2-hmac-sha256\""))
        assertTrue(envelope.contains("\"iterations\":$kKdfIterations"))
        assertTrue(envelope.contains("\"cipher\":\"aes-256-gcm\""))

        val macField = Regex("\"mac\":\"([^\"]+)\"").find(envelope)!!.groupValues[1]
        assertEquals(16, Base64.getDecoder().decode(macField).size)

        val reread = vault.read()
        assertEquals("web-01", reread.hosts.single().label)
        assertEquals("privateKey", reread.identities.single().kind.wireName)
    }

    private fun sampleData() = com.example.ohmyssh.data.VaultData(
        hosts = listOf(
            com.example.ohmyssh.data.Host(
                id = "h1",
                label = "web-01",
                hostname = "10.0.0.5",
                port = 22,
            ),
        ),
        identities = listOf(
            com.example.ohmyssh.data.Identity(
                id = "i1",
                label = "deploy",
                username = "deploy",
                kind = com.example.ohmyssh.data.AuthKind.PRIVATE_KEY,
                privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nx\n-----END OPENSSH PRIVATE KEY-----",
            ),
        ),
    )
}
