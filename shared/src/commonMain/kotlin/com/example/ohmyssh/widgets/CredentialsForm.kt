package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.platform.FilePick
import com.example.ohmyssh.ssh.describePrivateKey
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import kotlinx.coroutines.launch

class CredentialsState(initial: Identity? = null) {
    var username by mutableStateOf(initial?.username ?: "")
    var password by mutableStateOf(initial?.password ?: "")
    var passphrase by mutableStateOf(initial?.passphrase ?: "")

    var privateKey by mutableStateOf(initial?.privateKey)
        private set

    var keyStatus by mutableStateOf(
        initial?.privateKey?.let { describePrivateKey(it, initial.passphrase) },
    )
        private set

    val kind: AuthKind
        get() = if (privateKey == null) AuthKind.PASSWORD else AuthKind.PRIVATE_KEY

    val isEmpty: Boolean
        get() = username.isBlank() && password.isEmpty() && privateKey == null

    fun applyPrivateKey(pem: String?) {
        privateKey = pem
        keyStatus = pem?.let { describePrivateKey(it, passphrase.ifEmpty { null }) }
    }

    fun validate(): String? {
        if (username.isBlank()) return "Username is required"
        return null
    }

    fun build(id: String, label: String? = null): Identity {
        val user = username.trim()
        val resolved = (label ?: "").trim()
        val key = privateKey
        return Identity(
            id = id,
            label = resolved.ifEmpty { user },
            username = user,
            kind = kind,
            password = if (key == null && password.isNotEmpty()) password else null,
            privateKey = key,
            passphrase = if (key != null && passphrase.isNotEmpty()) passphrase else null,
        )
    }
}

@Composable
fun rememberCredentialsState(initial: Identity?): CredentialsState =
    rememberSaveable(
        initial?.id,
        saver = Saver(
            save = { state ->
                listOf(state.username, state.password, state.passphrase, state.privateKey)
            },
            restore = { saved ->
                CredentialsState().apply {
                    username = saved[0] as? String ?: ""
                    password = saved[1] as? String ?: ""
                    passphrase = saved[2] as? String ?: ""
                    applyPrivateKey(saved[3] as? String)
                }
            },
        ),
    ) { CredentialsState(initial) }

@Composable
fun CredentialsEditor(
    state: CredentialsState,
    usernameHint: String = "root",
    autofocusUsername: Boolean = false,
) {
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        QTextField(
            value = state.username,
            onValueChange = { state.username = it },
            label = "Username",
            hint = usernameHint,
            autofocus = autofocusUsername,
        )
        Spacer(Modifier.height(12.dp))
        KeyCard(
            status = state.keyStatus,
            onImport = {
                scope.launch {
                    val picked = FilePick.pickFile("Select a private key") ?: return@launch
                    val pem = picked.bytes.decodeToString()
                    if (!pem.contains("PRIVATE KEY")) {
                        AppToasts.show("That file does not look like a private key")
                        return@launch
                    }
                    state.applyPrivateKey(pem)
                }
            },
            onClear = if (state.privateKey == null) null else { -> state.applyPrivateKey(null) },
        )
        Spacer(Modifier.height(10.dp))
        if (state.privateKey == null) {
            QTextField(
                value = state.password,
                onValueChange = { state.password = it },
                label = "Password",
                obscure = true,
            )
        } else {
            QTextField(
                value = state.passphrase,
                onValueChange = { state.passphrase = it },
                label = "Key passphrase (if encrypted)",
                obscure = true,
            )
        }
    }
}

@Composable
private fun KeyCard(
    status: String?,
    onImport: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val colors = appColors
    val loaded = status != null

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QIconBadge(
            icon = Icons.Outlined.VpnKey,
            color = if (loaded) colors.success else colors.textMuted,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            status ?: "No private key — the password below is used",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = if (loaded) colors.textPrimary else colors.textMuted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            ),
        )
        if (onClear != null) {
            TextButton(
                onClick = onClear,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
            ) { Text("Clear") }
        }
        TextButton(
            onClick = onImport,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) { Text("Import") }
    }
}
