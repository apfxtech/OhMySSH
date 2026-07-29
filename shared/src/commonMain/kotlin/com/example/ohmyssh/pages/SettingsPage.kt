package com.example.ohmyssh.pages

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCardList
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.AutoLogin
import com.example.ohmyssh.data.AutoLoginException
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.WrongPasswordException
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.platform.FilePick
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.theme.QAppThemeController
import com.example.ohmyssh.theme.QThemeMode
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.AppVersionLabel
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.promptForPassword
import kotlinx.coroutines.launch

private class SettingsAction(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val onTap: () -> Unit,
)

@Composable
fun SettingsPage(onLocked: () -> Unit) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    var autoLogin by remember { mutableStateOf(false) }
    var autoLoginAvailable by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val available = AutoLogin.isAvailable()
        autoLoginAvailable = available
        autoLogin = available && AutoLogin.isEnabled()
    }

    val actions = listOf(
        SettingsAction(
            icon = Icons.Outlined.FileUpload,
            color = Color(0xFF589DFF),
            title = "Export vault",
            subtitle = "Save an encrypted copy",
            onTap = {
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
        ),
        SettingsAction(
            icon = Icons.Outlined.FileDownload,
            color = Color(0xFF2ED34A),
            title = "Import vault",
            subtitle = "Merge systems and users from a file",
            onTap = {
                scope.launch {
                    val picked = FilePick.pickFile("Select a vault file") ?: return@launch
                    val password = promptForPassword(
                        message = "Enter the master password of the vault file you picked. " +
                            "Matching entries are updated, new ones are added.",
                        actionLabel = "Import",
                    ) ?: return@launch

                    try {
                        val summary = VaultStore.importVault(
                            fileText = picked.bytes.decodeToString(),
                            password = password,
                        )
                        AppToasts.show(
                            if (summary.total == 0) {
                                "Nothing new to import"
                            } else {
                                "Imported ${summary.hostsAdded} systems, " +
                                    "${summary.identitiesAdded} users (" +
                                    "${summary.hostsUpdated + summary.identitiesUpdated} updated)"
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
        ),
        SettingsAction(
            icon = Icons.Filled.Password,
            color = Color(0xFFFF9B34),
            title = "Change master password",
            subtitle = "Re-encrypts the vault in place",
            onTap = {
                scope.launch {
                    val next = promptForPassword(
                        message = "Pick a new master password. The vault is re-encrypted in " +
                            "place; existing exports keep their old password.",
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
        ),
        SettingsAction(
            icon = Icons.Outlined.Lock,
            color = Color(0xFFE85858),
            title = "Lock now",
            subtitle = "Closes every session and clears the vault from memory",
            onTap = {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Lock the vault?",
                        message = "Open sessions will be closed.",
                        actionLabel = "Lock",
                    )
                    if (!confirmed) return@launch
                    SessionManager.closeAll()
                    VaultStore.lock()
                    onLocked()
                }
            },
        ),
    )

    QScaffold(appBar = { QPageAppBar(title = "Settings") }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
        ) {
            GroupedCardList(
                title = "Theme",
                items = QThemeMode.entries,
                onTap = { mode -> { QAppThemeController.applyThemeMode(mode) } },
                itemBuilder = { mode ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            mode.label,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W500,
                            ),
                        )
                        Icon(
                            if (QAppThemeController.themeMode == mode) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Circle
                            },
                            contentDescription = null,
                            tint = if (QAppThemeController.themeMode == mode) {
                                colors.accent
                            } else {
                                colors.textMuted
                            },
                            modifier = Modifier.size(21.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(14.dp))
            GroupedCardList(
                title = "Startup",
                items = listOf(0),
                itemBuilder = {
                    AutoLoginRow(
                        value = autoLogin,
                        enabled = autoLoginAvailable,
                        unavailableReason = if (autoLoginAvailable) {
                            null
                        } else {
                            AutoLogin.unavailableReason
                        },
                        onChanged = { value ->
                            scope.launch {
                                if (!value) {
                                    autoLogin = false
                                    AutoLogin.disable()
                                    return@launch
                                }
                                if (!autoLoginAvailable) {
                                    val reason =
                                        AutoLogin.unavailableReason ?: "no usable keystore"
                                    Log.warn("settings", "auto-unlock unavailable: $reason")
                                    AppToasts.show(reason)
                                    return@launch
                                }
                                val password = promptForPassword(
                                    message = "Confirm your master password to store it in this " +
                                        "device's keystore. The app will then open without asking.",
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
                                                Log.error(
                                                    "settings",
                                                    "enabling auto-unlock failed",
                                                    failure,
                                                )
                                                "$failure"
                                            }
                                        }
                                    },
                                ) ?: return@launch
                                autoLogin = true
                            }
                        },
                    )
                },
            )

            Spacer(Modifier.height(14.dp))
            GroupedCardList(
                title = "Vault",
                items = actions,
                onTap = { action -> action.onTap },
                itemBuilder = { action -> ActionRow(action) },
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "The vault file holds every system, user, password and private key, " +
                    "encrypted with your master password. Exporting copies that file as-is — " +
                    "the other device only needs the password. Auto-unlock keeps a copy of the " +
                    "password in the device keystore; it is never written to the vault or to " +
                    "an export.",
                modifier = Modifier.padding(horizontal = 26.dp),
                style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp, lineHeight = 16.sp),
            )

            Spacer(Modifier.height(18.dp))
            AppVersionLabel(
                Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { navigator.push { IconGalleryPage() } })
                },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AutoLoginRow(
    value: Boolean,
    enabled: Boolean,
    unavailableReason: String?,
    onChanged: (Boolean) -> Unit,
) {
    val colors = appColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QIconBadge(
            icon = if (value) Icons.Filled.LockOpen else Icons.Outlined.Lock,
            color = if (value) colors.success else colors.textMuted,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Unlock automatically",
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                unavailableReason ?: if (value) {
                    "Opens straight to your systems"
                } else {
                    "Ask for the master password on every launch"
                },
                maxLines = 3,
                style = TextStyle(
                    color = if (unavailableReason == null) colors.textMuted else colors.warning,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                ),
            )
        }
        Switch(
            checked = value,
            onCheckedChange = if (enabled || value) onChanged else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
            ),
        )
    }
}

@Composable
private fun ActionRow(action: SettingsAction) {
    val colors = appColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QIconBadge(icon = action.icon, color = action.color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                action.title,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                action.subtitle,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 14.4.sp),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
