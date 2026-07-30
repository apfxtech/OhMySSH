package com.example.ohmyssh

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.ohmyssh.platform.primeWindowAppearance

fun main() {
    primeWindowAppearance()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ohmyssh",
            state = rememberWindowState(width = 480.dp, height = 820.dp),
        ) {
            App()
        }
    }
}
