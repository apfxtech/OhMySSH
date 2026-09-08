package com.example.ohmyssh

import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.SftpSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SftpSettingsTest {
    @Test
    fun defaultsLeaveTheVaultUntouched() {
        val host = Host(id = "h", label = "", hostname = "box")
        assertFalse(host.toJson().containsKey("sftp"))
        assertEquals(SftpSettings.default, Host.fromJson(host.toJson()).sftp)
    }

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val host = Host(
            id = "h",
            label = "",
            hostname = "box",
            sftp = SftpSettings(
                enabled = false,
                startPath = "/var/www",
                showHidden = false,
                inlineIdentity = Identity(id = "s", label = "www", username = "www", kind = AuthKind.PASSWORD, password = "p"),
            ),
        )
        assertTrue(host.toJson().containsKey("sftp"))
        assertEquals(host, Host.fromJson(host.toJson()))
    }

    @Test
    fun savedUserReferenceRoundTrips() {
        val host = Host(id = "h", label = "", hostname = "box", sftp = SftpSettings(identityId = "i9"))
        val back = Host.fromJson(host.toJson())
        assertEquals("i9", back.sftp.identityId)
        assertTrue(back.sftp.hasOwnLogin)
    }
}
