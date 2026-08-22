package com.example.ohmyssh

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.ohmyssh.mcp.McpService
import com.example.ohmyssh.platform.primeWindowAppearance

fun main() {
    primeWindowAppearance()
    // Agents talk to the running app, not to a second copy of it, so their
    // sessions show up on screen and their commands reach the same history.
    McpService.start()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ohmyssh",
            icon = painterResource("app-icon.png"),
            state = rememberWindowState(width = 480.dp, height = 820.dp),
        ) {
            App()
        }
    }
}
