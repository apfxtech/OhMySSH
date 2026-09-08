package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.platform.FilePick
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.ui.Dialogs
import kotlinx.coroutines.launch

fun looksLikePrivateKey(text: String): Boolean =
    text.contains("-----BEGIN") && text.contains("PRIVATE KEY-----")

// sshj rejects a PEM whose last line is not newline-terminated.
fun normalisePrivateKey(text: String): String = text.trim() + "\n"

suspend fun promptForPrivateKey(): String? = Dialogs.show { dismiss ->
    val colors = appColors
    var value by rememberSaveable { mutableStateOf("") }
    var rejected by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { dismiss(null) },
        containerColor = colors.dialogBackground,
        title = { Text("Private key", color = colors.dialogText) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        rejected = false
                    },
                    isError = rejected,
                    minLines = 10,
                    maxLines = 16,
                    placeholder = {
                        Text(
                            "-----BEGIN OPENSSH PRIVATE KEY-----",
                            color = colors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rejected) {
                    Spacer(Modifier.height(8.dp))
                    Text("That is not a private key", color = colors.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (looksLikePrivateKey(value)) {
                        dismiss(normalisePrivateKey(value))
                    } else {
                        rejected = true
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text("Use key") }
        },
        dismissButton = {
            TextButton(
                onClick = { dismiss(null) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivateKeyPanel(
    status: String?,
    onApply: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = appColors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val loaded = status != null
    val tone = when {
        status == null -> colors.textMuted
        status.startsWith("Key could not") -> colors.danger
        status.startsWith("Encrypted") -> colors.warning
        else -> colors.success
    }

    fun accept(text: String, source: String) {
        if (!looksLikePrivateKey(text)) {
            AppToasts.show("$source does not hold a private key")
            return
        }
        onApply(normalisePrivateKey(text))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QIconBadge(icon = Icons.Outlined.VpnKey, color = tone, size = 30.dp, iconSize = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                status ?: "No private key",
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = if (loaded) colors.textPrimary else colors.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                ),
            )
            if (loaded) {
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
                ) { Text("Clear", fontSize = 12.5.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.divider)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeySourceButton("Import file", Icons.Outlined.FileOpen) {
                scope.launch {
                    val picked = FilePick.pickFile("Select a private key") ?: return@launch
                    accept(picked.bytes.decodeToString(), picked.name)
                }
            }
            KeySourceButton("Paste", Icons.Outlined.ContentPaste) {
                val text = clipboard.getText()?.text
                if (text.isNullOrBlank()) {
                    AppToasts.show("The clipboard is empty")
                } else {
                    accept(text, "The clipboard")
                }
            }
            KeySourceButton("Type it in", Icons.Outlined.EditNote) {
                scope.launch {
                    val typed = promptForPrivateKey() ?: return@launch
                    onApply(typed)
                }
            }
        }
    }
}

@Composable
private fun KeySourceButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = appColors
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(30.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp)
    }
}
