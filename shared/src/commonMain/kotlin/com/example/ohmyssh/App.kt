package com.example.ohmyssh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ohmyssh.data.AutoLogin
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.navigation.NavigationHost
import com.example.ohmyssh.navigation.Navigator
import com.example.ohmyssh.pages.HostsPage
import com.example.ohmyssh.pages.IdentitiesPage
import com.example.ohmyssh.pages.LockPage
import com.example.ohmyssh.pages.SessionsListPage
import com.example.ohmyssh.pages.SettingsPage
import com.example.ohmyssh.platform.applyPlatformTheme
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.theme.OhMySshTheme
import com.example.ohmyssh.theme.QAppThemeController
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.ui.DialogsHost
import com.example.ohmyssh.widgets.RootScaffold
import com.example.ohmyssh.widgets.RootTab

suspend fun bootstrap(): Boolean {
    QAppThemeController.loadThemeMode()
    applyPlatformTheme(QAppThemeController.themeMode)
    AutoLogin.isAvailable()

    val exists = VaultStore.vaultExists()
    Log.info("startup", "vault ${if (exists) "found" else "not created yet"}")
    if (exists) tryAutoUnlock()
    return exists
}

private suspend fun tryAutoUnlock() {
    val password = AutoLogin.readPassword() ?: return
    try {
        VaultStore.unlock(password)
        Log.info("startup", "vault unlocked automatically")
    } catch (error: Exception) {
        Log.error("startup", "auto-unlock failed, clearing it: $error", error)
        AutoLogin.disable()
    }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    AppToasts.scope = scope

    var booted by remember { mutableStateOf(false) }
    var vaultExists by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(RootTab.SYSTEMS) }
    val navigator = remember { Navigator() }

    LaunchedEffect(Unit) {
        vaultExists = bootstrap()
        booted = true
    }

    OhMySshTheme {
        Box(Modifier.fillMaxSize().background(appColors.background)) {
            if (!booted) {
                SnackbarHost(AppToasts.hostState, Modifier.align(Alignment.BottomCenter))
                return@Box
            }

            if (!VaultStore.isUnlocked) {
                // Once a vault exists the lock screen must stop offering to
                // create another one, or unlocking would overwrite it.
                LockPage(vaultExists = vaultExists, onUnlocked = { vaultExists = true })
            } else {
                NavigationHost(navigator) {
                    RootScaffold(
                        currentTab = tab,
                        sessionCount = SessionManager.sessions.size,
                        onTabSelected = { tab = it },
                    ) {
                        when (tab) {
                            RootTab.SYSTEMS -> HostsPage()
                            RootTab.USERS -> IdentitiesPage()
                            RootTab.SESSIONS -> SessionsListPage()
                            RootTab.SETTINGS -> SettingsPage(
                                onLocked = { tab = RootTab.SYSTEMS },
                            )
                        }
                    }
                }
            }

            DialogsHost()
            SnackbarHost(AppToasts.hostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}
