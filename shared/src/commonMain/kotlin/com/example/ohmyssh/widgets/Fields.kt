package com.example.ohmyssh.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.Dialogs

@Composable
fun QTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String? = null,
    obscure: Boolean = false,
    maxLines: Int = 1,
    digitsOnly: Boolean = false,
    autofocus: Boolean = false,
    onSubmitted: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(if (digitsOnly) raw.filter { it.isDigit() } else raw)
        },
        label = { Text(label, fontSize = 14.sp) },
        placeholder = hint?.let { { Text(it, fontSize = 14.sp, color = colors.textMuted) } },
        visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = obscure || maxLines == 1,
        minLines = if (obscure) 1 else maxLines.coerceAtMost(3),
        maxLines = if (obscure) 1 else maxLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                digitsOnly -> KeyboardType.Number
                obscure -> KeyboardType.Password
                else -> KeyboardType.Text
            },
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmitted?.invoke(value) }),
        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.card,
            unfocusedContainerColor = colors.card,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.textMuted,
            unfocusedLabelColor = colors.textMuted,
            cursorColor = colors.accent,
        ),
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    )

    if (autofocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
fun QFormLabel(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(
            color = appColors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.6.sp,
        ),
        modifier = Modifier.padding(start = 2.dp, top = 18.dp, end = 2.dp, bottom = 8.dp),
    )
}

@Composable
fun QEmptyView(
    icon: ImageVector,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = appColors
    Box(Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                textAlign = TextAlign.Center,
                style = TextStyle(color = colors.textMuted, fontSize = 13.sp, lineHeight = 17.5.sp),
            )
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                action()
            }
        }
    }
}

suspend fun confirmDestructive(
    title: String,
    message: String,
    actionLabel: String = "Delete",
): Boolean = Dialogs.show<Boolean> { dismiss ->
    val colors = appColors
    AlertDialog(
        onDismissRequest = { dismiss(false) },
        containerColor = colors.dialogBackground,
        title = { Text(title, color = colors.dialogText) },
        text = { Text(message, color = colors.dialogMuted) },
        confirmButton = {
            TextButton(
                onClick = { dismiss(true) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
            ) { Text(actionLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = { dismiss(false) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text("Cancel") }
        },
    )
} ?: false

class PickOption<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val isAction: Boolean = false,
)

suspend fun <T> pickFromList(
    title: String,
    options: List<PickOption<T>>,
    current: T,
): PickOption<T>? = Dialogs.show { dismiss ->
    val colors = appColors
    AlertDialog(
        onDismissRequest = { dismiss(null) },
        containerColor = colors.dialogBackground,
        title = { Text(title, color = colors.dialogText) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(options) { option ->
                    val selected = !option.isAction && option.value == current
                    val tint = if (option.isAction) colors.accent else colors.dialogText
                    ListItem(
                        headlineContent = {
                            Text(
                                option.label,
                                color = tint,
                                fontSize = 14.sp,
                                fontWeight = if (option.isAction) FontWeight.W600 else FontWeight.W400,
                            )
                        },
                        supportingContent = option.subtitle?.let {
                            { Text(it, color = colors.dialogMuted, fontSize = 12.sp) }
                        },
                        leadingContent = option.icon?.let {
                            {
                                Icon(
                                    it,
                                    contentDescription = null,
                                    tint = if (option.isAction) colors.accent else colors.textMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        trailingContent = if (selected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = colors.dialogBackground),
                        modifier = Modifier.clickableRow { dismiss(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = { dismiss(null) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text("Cancel") }
        },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

suspend fun promptForText(
    title: String,
    label: String,
    initial: String = "",
    obscure: Boolean = false,
    actionLabel: String = "Save",
): String? = Dialogs.show { dismiss ->
    val colors = appColors
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { dismiss(null) },
        containerColor = colors.dialogBackground,
        title = { Text(title, color = colors.dialogText) },
        text = {
            QTextField(
                value = value,
                onValueChange = { value = it },
                label = label,
                obscure = obscure,
                autofocus = true,
                onSubmitted = { dismiss(value.trim()) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { dismiss(value.trim()) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text(actionLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = { dismiss(null) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text("Cancel") }
        },
    )
}
