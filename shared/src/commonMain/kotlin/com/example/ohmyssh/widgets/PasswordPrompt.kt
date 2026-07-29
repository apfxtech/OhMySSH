package com.example.ohmyssh.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.Dialogs
import kotlinx.coroutines.launch

@Composable
fun PasswordForm(
    message: String,
    actionLabel: String,
    onSubmit: suspend (password: String) -> String?,
    title: String? = null,
    confirm: Boolean = false,
    minLength: Int = 0,
    onCancel: (() -> Unit)? = null,
) {
    val colors = appColors
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmValue by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        if (password.isEmpty()) {
            error = "Enter a master password"
            return
        }
        if (minLength > 0 && password.length < minLength) {
            error = "Use at least $minLength characters"
            return
        }
        if (confirm && password != confirmValue) {
            error = "Passwords do not match"
            return
        }
        busy = true
        error = null
        scope.launch {
            val problem = onSubmit(password)
            busy = false
            error = problem
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QIconBadge(
                icon = Icons.Outlined.Lock,
                color = colors.accent,
                size = 60.dp,
                iconSize = 32.dp,
                borderRadius = 16.dp,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (title != null) {
            Text(
                title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                ),
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            message,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(color = colors.textMuted, fontSize = 13.sp, lineHeight = 17.5.sp),
        )
        Spacer(Modifier.height(22.dp))
        QTextField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            obscure = true,
            autofocus = true,
            onSubmitted = { if (!confirm) submit() },
        )
        if (confirm) {
            Spacer(Modifier.height(10.dp))
            QTextField(
                value = confirmValue,
                onValueChange = { confirmValue = it },
                label = "Confirm password",
                obscure = true,
                onSubmitted = { submit() },
            )
        }
        val problem = error
        if (problem != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                problem,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = colors.danger, fontSize = 12.5.sp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            if (onCancel != null) {
                TextButton(
                    onClick = { if (!busy) onCancel() },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Cancel") }
                Spacer(Modifier.width(10.dp))
            }
            Button(
                onClick = { submit() },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.accent.copy(alpha = 0.6f),
                    disabledContentColor = colors.onAccent,
                ),
                modifier = Modifier.weight(2f).height(46.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(actionLabel)
                }
            }
        }
    }
}

suspend fun promptForPassword(
    message: String,
    actionLabel: String = "Confirm",
    confirm: Boolean = false,
    minLength: Int = 0,
    verify: (suspend (password: String) -> String?)? = null,
): String? = Dialogs.show { dismiss ->
    val colors = appColors
    Dialog(onDismissRequest = { dismiss(null) }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.background,
            modifier = Modifier.widthIn(max = 380.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 20.dp),
            ) {
                PasswordForm(
                    message = message,
                    actionLabel = actionLabel,
                    confirm = confirm,
                    minLength = minLength,
                    onCancel = { dismiss(null) },
                    onSubmit = { password ->
                        val problem = verify?.invoke(password)
                        if (problem != null) {
                            problem
                        } else {
                            dismiss(password)
                            null
                        }
                    },
                )
            }
        }
    }
}
