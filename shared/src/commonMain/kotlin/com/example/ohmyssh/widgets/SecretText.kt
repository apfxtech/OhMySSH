package com.example.ohmyssh.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

@Composable
fun QSecretText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { revealed = !revealed },
    ) {
        if (revealed) {
            SelectionContainer { Text(text, style = style) }
        } else {
            Text("••••••••••••••••", style = style)
        }
    }
}
