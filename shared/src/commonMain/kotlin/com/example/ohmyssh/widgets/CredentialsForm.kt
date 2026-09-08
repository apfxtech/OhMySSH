package com.example.ohmyssh.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.ssh.describePrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class CredentialsState(initial: Identity? = null) {
    var username by mutableStateOf(initial?.username ?: "")
    var password by mutableStateOf(initial?.password ?: "")
    var passphrase by mutableStateOf(initial?.passphrase ?: "")
    var method by mutableStateOf(initial?.kind ?: AuthKind.PASSWORD)

    var privateKey by mutableStateOf(initial?.privateKey)
        private set

    var keyStatus by mutableStateOf(
        initial?.privateKey?.let { describePrivateKey(it, initial.passphrase) },
    )
        internal set

    val kind: AuthKind get() = method

    val isEmpty: Boolean
        get() = username.isBlank() && password.isEmpty() && privateKey == null

    fun applyPrivateKey(pem: String?) {
        privateKey = pem
        keyStatus = pem?.let { describePrivateKey(it, passphrase.ifEmpty { null }) }
        if (pem != null) method = AuthKind.PRIVATE_KEY
    }

    fun validate(): String? {
        if (username.isBlank()) return "Username is required"
        if (method == AuthKind.PRIVATE_KEY && privateKey == null) {
            return "Add a private key, or use a password"
        }
        return null
    }

    fun build(id: String, label: String? = null): Identity {
        val user = username.trim()
        val resolved = (label ?: "").trim()
        val key = if (method == AuthKind.PRIVATE_KEY) privateKey else null
        return Identity(
            id = id,
            label = resolved.ifEmpty { user },
            username = user,
            kind = method,
            password = if (key == null && password.isNotEmpty()) password else null,
            privateKey = key,
            passphrase = if (key != null && passphrase.isNotEmpty()) passphrase else null,
        )
    }

    fun snapshot(): List<Any?> = listOf(username, password, passphrase, method, privateKey)
}

@Composable
fun rememberCredentialsState(initial: Identity?): CredentialsState =
    rememberSaveable(
        initial?.id,
        saver = Saver(
            save = { state ->
                listOf(
                    state.username,
                    state.password,
                    state.passphrase,
                    state.method.name,
                    state.privateKey,
                )
            },
            restore = { saved ->
                CredentialsState().apply {
                    username = saved[0] as? String ?: ""
                    password = saved[1] as? String ?: ""
                    passphrase = saved[2] as? String ?: ""
                    applyPrivateKey(saved[4] as? String)
                    method = AuthKind.entries.firstOrNull { it.name == saved[3] } ?: method
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
    // Parsing an encrypted OpenSSH key runs bcrypt, so the status follows the
    // passphrase with a pause rather than on every keystroke.
    LaunchedEffect(state.privateKey, state.passphrase) {
        val pem = state.privateKey ?: return@LaunchedEffect
        delay(350)
        val passphrase = state.passphrase.ifEmpty { null }
        state.keyStatus = withContext(Dispatchers.Default) { describePrivateKey(pem, passphrase) }
    }

    Column(Modifier.fillMaxWidth()) {
        QTextField(
            value = state.username,
            onValueChange = { state.username = it },
            label = "Username",
            hint = usernameHint,
            autofocus = autofocusUsername,
        )
        FieldGap()
        SegmentedChoice(
            options = listOf(
                SegmentOption(AuthKind.PASSWORD, "Password", Icons.Filled.Password),
                SegmentOption(AuthKind.PRIVATE_KEY, "Private key", Icons.Outlined.VpnKey),
            ),
            selected = state.method,
            onSelect = { state.method = it },
        )
        FieldGap()
        if (state.method == AuthKind.PASSWORD) {
            QTextField(
                value = state.password,
                onValueChange = { state.password = it },
                label = "Password",
                obscure = true,
            )
        } else {
            PrivateKeyPanel(
                status = state.keyStatus,
                onApply = { state.applyPrivateKey(it) },
                onClear = { state.applyPrivateKey(null) },
            )
            FieldGap()
            QTextField(
                value = state.passphrase,
                onValueChange = { state.passphrase = it },
                label = "Key passphrase",
                obscure = true,
            )
        }
    }
}
