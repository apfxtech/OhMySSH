package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ohmyssh.data.VaultException
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.widgets.PasswordForm

@Composable
fun LockPage(
    vaultExists: Boolean,
    onUnlocked: () -> Unit,
) {
    val creating = !vaultExists

    Box(
        Modifier.fillMaxSize().background(appColors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = 360.dp)
                .padding(horizontal = 28.dp, vertical = 40.dp),
        ) {
            PasswordForm(
                title = if (creating) "Create your vault" else "ohmyssh",
                message = if (creating) {
                    "Everything — systems, users, keys — is encrypted with this " +
                        "password. There is no recovery if you lose it."
                } else {
                    "Enter your master password to unlock."
                },
                actionLabel = if (creating) "Create vault" else "Unlock",
                confirm = creating,
                minLength = if (creating) 8 else 0,
                onSubmit = { password -> submit(password, creating, onUnlocked) },
            )
        }
    }
}

private suspend fun submit(
    password: String,
    creating: Boolean,
    onUnlocked: () -> Unit,
): String? = try {
    if (creating) {
        VaultStore.create(password)
        Log.info("vault", "created")
    } else {
        VaultStore.unlock(password)
        Log.info("vault", "unlocked")
    }
    onUnlocked()
    null
} catch (error: VaultException) {
    Log.error("vault", error.message, error)
    error.message
} catch (error: Exception) {
    Log.error("vault", error, error)
    "$error"
}
