package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.WbAuto
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.ai.AgentServer
import com.example.ohmyssh.ai.AppTools
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.AutoLogin
import com.example.ohmyssh.data.AutoLoginException
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.Vault
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.WrongPasswordException
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.platform.AppFiles
import com.example.ohmyssh.platform.FilePick
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.platform.appVersion
import com.example.ohmyssh.platform.displayName
import com.example.ohmyssh.platform.isDesktop
import com.example.ohmyssh.platform.releaseTag
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.theme.QAppThemeController
import com.example.ohmyssh.theme.QThemeMode
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.AgentToolList
import com.example.ohmyssh.widgets.EditorSection
import com.example.ohmyssh.widgets.InfoTable
import com.example.ohmyssh.widgets.SectionedLayout
import com.example.ohmyssh.widgets.SegmentOption
import com.example.ohmyssh.widgets.SegmentedChoice
import com.example.ohmyssh.widgets.SettingsDivider
import com.example.ohmyssh.widgets.SettingsGroup
import com.example.ohmyssh.widgets.SettingsLabel
import com.example.ohmyssh.widgets.SettingsRow
import com.example.ohmyssh.widgets.SettingsToggle
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.promptForPassword
import kotlinx.coroutines.launch

private const val APPEARANCE = "appearance"
private const val SECURITY = "security"
private const val VAULT = "vault"
private const val HISTORY = "history"
private const val AGENT = "agent"
private const val ABOUT = "about"

@Composable
fun SettingsPage(onLocked: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(APPEARANCE) }

    var autoLogin by remember { mutableStateOf(false) }
    var autoLoginAvailable by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val available = AutoLogin.isAvailable()
        autoLoginAvailable = available
        autoLogin = available && AutoLogin.isEnabled()
    }

    val hosts = VaultStore.hosts.size
    val identities = VaultStore.identities.size
    val past = HistoryStore.past.size
    val sections = listOf(
        EditorSection(APPEARANCE, "Appearance", Icons.Outlined.Palette, QAppThemeController.themeMode.label),
        EditorSection(
            SECURITY,
            "Security",
            Icons.Outlined.Shield,
            if (autoLogin) "Unlocks automatically" else "Asks for the password",
        ),
        EditorSection(VAULT, "Vault", Icons.Outlined.Inventory2, "${count(hosts, "system")} · ${count(identities, "user")}"),
        EditorSection(HISTORY, "History", Icons.Outlined.History, count(past, "connection")),
        EditorSection(AGENT, "Agent", Icons.Outlined.SmartToy, AgentServer.endpoint ?: "Off"),
        EditorSection(ABOUT, "About", Icons.Outlined.Info, "v$appVersion"),
    )

    QScaffold {
        SectionedLayout(sections, selected, onSelect = { selected = it }) { id ->
            when (id) {
                APPEARANCE -> AppearanceSection()
                SECURITY -> SecuritySection(
                    autoLogin = autoLogin,
                    autoLoginAvailable = autoLoginAvailable,
                    onAutoLogin = { autoLogin = it },
                    onLocked = onLocked,
                )
                VAULT -> VaultSection(onLocked)
                HISTORY -> HistorySection()
                AGENT -> AgentSection()
                ABOUT -> AboutSection()
            }
        }
    }
}

private fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"

@Composable
private fun AppearanceSection() {
    val colors = appColors
    SettingsLabel("Theme", first = true)
    SegmentedChoice(
        options = listOf(
            SegmentOption(QThemeMode.SYSTEM, "System", Icons.Outlined.WbAuto),
            SegmentOption(QThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
            SegmentOption(QThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
        ),
        selected = QAppThemeController.themeMode,
        onSelect = { QAppThemeController.applyThemeMode(it) },
    )
    if (QAppThemeController.dynamicColorsSupported) {
        SettingsLabel("Colors")
        SettingsGroup {
            SettingsToggle(
                icon = Icons.Outlined.Palette,
                tint = colors.accent,
                title = "System colors",
                description = "Follow the wallpaper palette",
                checked = QAppThemeController.dynamicColors,
                onChange = { QAppThemeController.applyDynamicColors(it) },
            )
        }
    }
}

@Composable
private fun SecuritySection(
    autoLogin: Boolean,
    autoLoginAvailable: Boolean,
    onAutoLogin: (Boolean) -> Unit,
    onLocked: () -> Unit,
) {
    val colors = appColors
    val scope = rememberCoroutineScope()

    fun enableAutoLogin() {
        scope.launch {
            if (!autoLoginAvailable) {
                val reason = AutoLogin.unavailableReason ?: "no usable keystore"
                Log.warn("settings", "auto-unlock unavailable: $reason")
                AppToasts.show(reason)
                return@launch
            }
            promptForPassword(
                message = "Confirm your master password to store it in this device's keystore. " +
                    "The app will then open without asking.",
                actionLabel = "Enable",
                verify = { candidate ->
                    if (!VaultStore.verifyPassword(candidate)) {
                        "Wrong master password"
                    } else {
                        try {
                            AutoLogin.enable(candidate)
                            null
                        } catch (failure: AutoLoginException) {
                            failure.message
                        } catch (failure: Exception) {
                            Log.error("settings", "enabling auto-unlock failed", failure)
                            "$failure"
                        }
                    }
                },
            ) ?: return@launch
            onAutoLogin(true)
        }
    }

    SettingsLabel("Startup", first = true)
    SettingsGroup {
        SettingsToggle(
            icon = if (autoLogin) Icons.Filled.LockOpen else Icons.Outlined.Lock,
            tint = if (autoLogin) colors.success else colors.textMuted,
            title = "Unlock automatically",
            description = if (autoLogin) "Opens straight to your systems" else "Ask for the master password on every launch",
            checked = autoLogin,
            enabled = autoLoginAvailable || autoLogin,
            warning = if (autoLoginAvailable) null else AutoLogin.unavailableReason,
            onChange = { value ->
                if (value) {
                    enableAutoLogin()
                } else {
                    onAutoLogin(false)
                    scope.launch { AutoLogin.disable() }
                }
            },
        )
    }

    SettingsLabel("Master password")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.Password,
            tint = colors.warning,
            title = "Change master password",
            description = "Re-encrypts the vault in place",
            onClick = {
                scope.launch {
                    promptForPassword(
                        message = "Pick a new master password. The vault is re-encrypted in place; " +
                            "existing exports keep their old password.",
                        actionLabel = "Change",
                        confirm = true,
                        minLength = 8,
                        verify = { candidate ->
                            try {
                                VaultStore.changeMasterPassword(candidate)
                                null
                            } catch (failure: Exception) {
                                Log.error("settings", "changing master password failed", failure)
                                "$failure"
                            }
                        },
                    ) ?: return@launch
                    AppToasts.show("Master password changed")
                }
            },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Outlined.Lock,
            tint = colors.danger,
            title = "Lock now",
            description = "Closes every session and turns auto-unlock off",
            chevron = false,
            onClick = {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Lock the app?",
                        message = "Open sessions will be closed and auto-unlock turned off.",
                        actionLabel = "Lock",
                    )
                    if (!confirmed) return@launch
                    SessionManager.closeAll()
                    AutoLogin.disable()
                    VaultStore.lock()
                    onLocked()
                }
            },
        )
    }
}

@Composable
private fun VaultSection(onLocked: () -> Unit) {
    val colors = appColors
    val scope = rememberCoroutineScope()

    InfoTable(
        listOf(
            "Systems" to "${VaultStore.hosts.size}",
            "Users" to "${VaultStore.identities.size}",
            "Serial" to "${VaultStore.serialDevices.size}",
            "File" to Vault.defaultPath(),
        ),
    )

    SettingsLabel("Copies")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Outlined.FileUpload,
            tint = colors.info,
            title = "Export vault",
            description = "Save an encrypted copy",
            onClick = {
                scope.launch {
                    try {
                        val path = VaultStore.exportVault() ?: return@launch
                        AppToasts.show("Exported to $path")
                    } catch (failure: Exception) {
                        Log.error("settings", "export failed", failure)
                        AppToasts.show("Export failed: $failure")
                    }
                }
            },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Outlined.FileDownload,
            tint = colors.success,
            title = "Import vault",
            description = "Merge systems and users from a file",
            onClick = {
                scope.launch {
                    try {
                        val picked = FilePick.pickFile("Select a vault file") ?: return@launch
                        val password = promptForPassword(
                            message = "Enter the master password of the vault file you picked. " +
                                "Matching entries are updated, new ones are added.",
                            actionLabel = "Import",
                        ) ?: return@launch
                        val summary = VaultStore.importVault(
                            fileText = picked.bytes.decodeToString(),
                            password = password,
                        )
                        AppToasts.show(
                            if (summary.total == 0) {
                                "Nothing new to import"
                            } else {
                                "Imported ${summary.hostsAdded} systems, ${summary.identitiesAdded} users " +
                                    "(${summary.hostsUpdated + summary.identitiesUpdated} updated)"
                            },
                        )
                    } catch (_: WrongPasswordException) {
                        Log.warn("settings", "import: wrong password for the chosen file")
                        AppToasts.show("Wrong password for that file")
                    } catch (failure: Exception) {
                        Log.error("settings", "import failed", failure)
                        AppToasts.show("Import failed: $failure")
                    }
                }
            },
        )
    }

    SettingsLabel("Danger zone")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.DeleteOutline,
            tint = colors.danger,
            title = "Delete vault",
            description = "Every system, user, password and key on this device",
            danger = true,
            chevron = false,
            onClick = {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Delete the vault?",
                        message = "The vault file and its history are deleted from this device for good, " +
                            "and open sessions are closed. Without an export there is no way back.",
                        actionLabel = "Delete",
                    )
                    if (!confirmed) return@launch
                    SessionManager.closeAll()
                    AutoLogin.disable()
                    VaultStore.deleteVault()
                    AppToasts.show("Vault deleted")
                    onLocked()
                }
            },
        )
    }
}

@Composable
private fun HistorySection() {
    val colors = appColors
    val scope = rememberCoroutineScope()
    val connections = HistoryStore.connections
    val commands = connections.sumOf { it.commands.size }

    InfoTable(
        listOf(
            "Yours" to "${HistoryStore.clientPast.size}",
            "Agent" to "${HistoryStore.agentPast.size}",
            "Commands" to "$commands",
        ),
    )

    SettingsLabel("Copies")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Outlined.FileUpload,
            tint = colors.info,
            title = "Export history",
            description = "Save an encrypted copy of past connections",
            onClick = {
                scope.launch {
                    if (connections.isEmpty()) {
                        AppToasts.show("No history to export")
                        return@launch
                    }
                    try {
                        val path = HistoryStore.exportHistory() ?: return@launch
                        AppToasts.show("Exported to $path")
                    } catch (failure: Exception) {
                        Log.error("settings", "history export failed", failure)
                        AppToasts.show("Export failed: $failure")
                    }
                }
            },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Outlined.FileDownload,
            tint = colors.success,
            title = "Import history",
            description = "Merge past connections from a file",
            onClick = {
                scope.launch {
                    try {
                        val picked = FilePick.pickFile("Select a history file") ?: return@launch
                        val password = promptForPassword(
                            message = "Enter the master password of the vault the history file was " +
                                "exported beside. New connections are added, existing ones are kept.",
                            actionLabel = "Import",
                        ) ?: return@launch
                        val added = HistoryStore.importHistory(
                            fileText = picked.bytes.decodeToString(),
                            password = password,
                        )
                        AppToasts.show(
                            when (added) {
                                0 -> "Nothing new to import"
                                1 -> "Imported 1 connection"
                                else -> "Imported $added connections"
                            },
                        )
                    } catch (_: WrongPasswordException) {
                        Log.warn("settings", "history import: wrong password for the chosen file")
                        AppToasts.show("Wrong password for that file")
                    } catch (failure: Exception) {
                        Log.error("settings", "history import failed", failure)
                        AppToasts.show("Import failed: $failure")
                    }
                }
            },
        )
    }

    SettingsLabel("Danger zone")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.DeleteSweep,
            tint = colors.danger,
            title = "Clear history",
            description = "Every past connection and the commands run over it",
            danger = true,
            chevron = false,
            enabled = HistoryStore.past.isNotEmpty(),
            onClick = {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Clear connection history?",
                        message = "Every past connection and the commands recorded over it will be forgotten.",
                        actionLabel = "Clear",
                    )
                    if (!confirmed) return@launch
                    HistoryStore.clearAll()
                    AppToasts.show("Connection history cleared")
                }
            },
        )
    }
}

@Composable
private fun AgentSection() {
    val colors = appColors
    val endpoint = AgentServer.endpoint
    val live = SessionManager.sessions.count { it.agentOwned }

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QIconBadge(
            icon = if (appPlatform.isDesktop) Icons.Outlined.SmartToy else Icons.Outlined.PhoneAndroid,
            color = if (endpoint != null) colors.success else colors.textMuted,
            size = 30.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    endpoint != null -> "Listening on $endpoint"
                    appPlatform.isDesktop -> "Not listening"
                    else -> "Desktop only"
                },
                style = TextStyle(color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.W600),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                when {
                    endpoint != null && live > 0 -> "${count(live, "session")} open by an agent"
                    endpoint != null -> "Loopback only. Point an MCP client at tools/ohmyssh-mcp."
                    appPlatform.isDesktop -> "The port was taken when the app started"
                    else -> "Agents connect to the desktop app"
                },
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 15.sp),
            )
        }
    }

    SettingsLabel("${AppTools.specs.size} tools")
    AgentToolList()
}

@Composable
private fun AboutSection() {
    val navigator = LocalNavigator.current
    val tag = releaseTag()
    InfoTable(
        buildList {
            add("Version" to appVersion)
            add("Build" to tag.ifEmpty { "local" })
            add("Platform" to appPlatform.displayName)
            add("Data" to AppFiles.appSupportDirectory())
            add("Log" to (Log.location ?: "stderr"))
        },
    )
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { navigator.push { IconGalleryPage() } })
            },
    )
}
