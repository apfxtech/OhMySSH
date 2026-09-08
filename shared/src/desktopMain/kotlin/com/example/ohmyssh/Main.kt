package com.example.ohmyssh

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.ohmyssh.mcp.McpService
import com.example.ohmyssh.platform.appVersion
import com.example.ohmyssh.platform.primeWindowAppearance
import com.example.ohmyssh.services.FileLog

fun main() {
    // First, so that whatever goes wrong during start-up is in the file too:
    // a bundled build has nowhere to put stderr, and without this a session
    // ends with no record of what an agent sent through it.
    FileLog.install("desktop $appVersion")
    primeWindowAppearance()
    // Agents talk to the running app, not to a second copy of it, so their
    // sessions show up on screen and their commands reach the same history.
    McpService.start()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ohmyssh",
            icon = painterResource("app-icon.png"),
            state = rememberWindowState(width = 1180.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
