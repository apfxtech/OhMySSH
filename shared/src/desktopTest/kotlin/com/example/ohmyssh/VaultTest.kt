package com.example.ohmyssh

import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.HostGroup
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.Vault
import com.example.ohmyssh.data.VaultData
import com.example.ohmyssh.data.VaultException
import com.example.ohmyssh.data.WrongPasswordException
import com.example.ohmyssh.data.kVaultFormat
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultTest {
    private lateinit var temp: File

    @BeforeTest
    fun setUp() {
        temp = File.createTempFile("ohmyssh_vault_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private fun vaultPath(): String = File(temp, "test.vault").path

    private fun sample() = VaultData(
        hosts = listOf(
            Host(
                id = "h1",
                label = "web-01",
                hostname = "10.0.0.5",
                port = 2222,
                identityId = "i1",
                knownHostKey = "SHA256:abc",
                osId = "ubuntu",
            ),
        ),
        identities = listOf(
            Identity(
                id = "i1",
                label = "root",
                username = "root",
                kind = AuthKind.PASSWORD,
                password = "hunter2",
            ),
        ),
        groups = listOf(HostGroup(id = "g1", name = "Production")),
    )

    @Test
    fun roundTripsDataThroughCreateAndOpen() = runBlocking {
        val path = vaultPath()
        val vault = Vault.create("correct horse battery", path, sample())
        assertTrue(File(path).exists())

        val reopened = Vault.open("correct horse battery", path)
        val data = reopened.read()

        assertEquals(1, data.hosts.size)
        assertEquals("10.0.0.5", data.hosts.single().hostname)
        assertEquals(2222, data.hosts.single().port)
        assertEquals("SHA256:abc", data.hosts.single().knownHostKey)
        assertEquals("hunter2", data.identities.single().password)
        assertEquals("Production", data.groups.single().name)

        vault.save(data)
    }

    @Test
    fun rejectsTheWrongPassword() = runBlocking {
        val path = vaultPath()
        Vault.create("right", path, sample())

        assertFailsWith<WrongPasswordException> { Vault.open("wrong", path) }
        Unit
    }

    @Test
    fun secretsAreNotReadableInTheFileOnDisk() = runBlocking {
        val path = vaultPath()
        Vault.create("right", path, sample())

        val raw = File(path).readText()
        assertFalse(raw.contains("hunter2"))
        assertFalse(raw.contains("10.0.0.5"))
        assertFalse(raw.contains("web-01"))
        assertContains(raw, kVaultFormat)
    }

    @Test
    fun changePasswordReEncryptsWithoutLosingData() = runBlocking {
        val path = vaultPath()
        val vault = Vault.create("old-password", path, sample())

        vault.changePassword("new-password")

        assertFailsWith<WrongPasswordException> { Vault.open("old-password", path) }

        val reopened = Vault.open("new-password", path)
        assertEquals("web-01", reopened.read().hosts.single().label)
    }

    @Test
    fun refusesAFileThatIsNotAnOhmysshVault() = runBlocking {
        val path = vaultPath()
        File(path).writeText("""{"format":"something-else"}""")
        assertFailsWith<VaultException> { Vault.open("x", path) }
        Unit
    }
}
