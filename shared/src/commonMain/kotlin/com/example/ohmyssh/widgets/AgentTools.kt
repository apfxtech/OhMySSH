package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.ai.AppTools
import com.example.ohmyssh.theme.appColors

/** Every tool an agent gets, read straight from [AppTools] so it cannot drift. */
@Composable
fun AgentToolList(enabled: Boolean = true, mayAuthenticate: Boolean = true) {
    val colors = appColors
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp))) {
        AppTools.specs.forEachIndexed { index, spec ->
            val available = enabled && (spec.name != "send_password" || mayAuthenticate)
            if (index > 0) HorizontalDivider(color = colors.divider)
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spec.name,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            color = if (available) colors.textPrimary else colors.textMuted,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.W600,
                        ),
                    )
                    if (!available) {
                        Text("off", style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp))
                    }
                }
                Text(
                    spec.description,
                    style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 15.sp),
                )
            }
        }
    }
}
