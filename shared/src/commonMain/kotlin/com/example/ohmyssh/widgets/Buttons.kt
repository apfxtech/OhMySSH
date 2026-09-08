package com.example.ohmyssh.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors

enum class ButtonTone { ACCENT, NEUTRAL, DANGER, FILLED }

/** A 30dp button for rows of secondary actions inside a panel. */
@Composable
fun SmallButton(
    label: String,
    icon: ImageVector,
    tone: ButtonTone = ButtonTone.ACCENT,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = appColors
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 12.dp)
    val modifier = Modifier.height(30.dp)
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.W600, maxLines = 1, softWrap = false)
    }
    if (tone == ButtonTone.FILLED) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
        ) { content() }
        return
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = padding,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = when (tone) {
                ButtonTone.DANGER -> colors.danger
                ButtonTone.NEUTRAL -> colors.textPrimary
                else -> colors.accent
            },
        ),
    ) { content() }
}
