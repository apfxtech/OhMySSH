package com.example.ohmyssh.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.ohmyssh.net.NetworkKind
import com.example.ohmyssh.net.NetworkWatcher
import com.example.ohmyssh.net.SsidAccess
import com.example.ohmyssh.net.networkKind
import com.example.ohmyssh.net.networkName
import com.example.ohmyssh.net.requestSsidAccess
import com.example.ohmyssh.net.ssidAccess
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.promptForText

fun networkIcon(kind: NetworkKind): ImageVector = when (kind) {
    NetworkKind.WIFI -> Icons.Filled.Wifi
    NetworkKind.WIRED -> Icons.Outlined.Lan
    NetworkKind.CELLULAR -> Icons.Filled.SignalCellularAlt
    NetworkKind.UNKNOWN -> Icons.Outlined.Public
}

/** What a network is called when nothing has managed to name it yet. */
fun networkKindLabel(kind: NetworkKind): String = when (kind) {
    NetworkKind.WIFI -> "Wi-Fi"
    NetworkKind.WIRED -> "Ethernet"
    NetworkKind.CELLULAR -> "Cellular"
    NetworkKind.UNKNOWN -> "Network"
}

fun networkLabel(id: String, fallbackName: String? = null): String =
    networkName(id, fallbackName).ifEmpty { networkKindLabel(networkKind(id)) }

/**
 * A subtitle with the network it was reached on carried on the end of it, in
 * the app's own `a · b · c` rhythm: `root@10.0.0.5 · Larka`.
 *
 * The name is drawn in the accent when it is the network this device is on
 * right now. That colour is the whole signal — whether the system in front of
 * you is reachable from where you are — and it costs the row no extra line, no
 * badge and no width.
 */
@Composable
fun withNetwork(text: String, id: String?, fallbackName: String? = null): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (id == null) return@buildAnnotatedString
        append(" · ")
        // Left unstyled when it is not the current one, so it inherits the
        // muted colour of the line it is part of.
        if (NetworkWatcher.current?.key != id) {
            append(networkLabel(id, fallbackName))
            return@buildAnnotatedString
        }
        withStyle(SpanStyle(color = appColors.accent, fontWeight = FontWeight.W600)) {
            append(networkLabel(id, fallbackName))
        }
    }

/**
 * Names the network the person just asked about.
 *
 * On macOS the OS may still be sitting on the real name behind a Location
 * authorisation, so that is offered first and the typing is skipped when it
 * works. Otherwise the name is typed by hand, which is the only way a router
 * MAC ever becomes "Home".
 */
suspend fun nameNetwork(id: String) {
    val before = networkName(id)

    if (NetworkWatcher.current?.key == id && ssidAccess() == SsidAccess.ASKABLE) {
        if (requestSsidAccess() == SsidAccess.GRANTED) {
            NetworkWatcher.invalidate()
            NetworkWatcher.refresh()
            val named = NetworkWatcher.currentTag()
            if (named != null && named.name != before) {
                AppToasts.show("Network named ${named.name}")
                return
            }
        }
    }

    val name = promptForText(
        title = "Name this network",
        label = "Network name",
        initial = before,
    )
    if (!name.isNullOrEmpty()) NetworkWatcher.rename(id, name)
}
