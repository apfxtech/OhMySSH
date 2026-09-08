package com.example.ohmyssh.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ohmyssh.components.HeaderAction
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.serial.SerialDeviceEntry

@Composable
fun ConnectionPicker(
    onOpenShell: (Host) -> Unit,
    onOpenSftp: (Host) -> Unit,
    onOpenSerial: (SerialDeviceEntry) -> Unit,
) {
    val navigator = LocalNavigator.current
    var mode by rememberSaveable { mutableStateOf(ConnectMode.SSH) }
    var query by rememberSaveable { mutableStateOf("") }

    SystemsBrowser(
        title = null,
        mode = mode,
        onModeChange = { mode = it },
        query = query,
        onQueryChange = { query = it },
        onOpenHost = { host, chosen ->
            if (chosen == ConnectMode.SFTP) onOpenSftp(host) else onOpenShell(host)
        },
        onOpenSerial = onOpenSerial,
        actions = {
            HeaderAction(
                label = "New system",
                icon = Icons.Filled.Add,
                wide = false,
                primary = true,
                onClick = { navigator.push { HostEditorPage(null) } },
            )
        },
    )
}
