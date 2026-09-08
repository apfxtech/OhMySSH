package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.theme.appColors

@Composable
fun SettingsLabel(text: String, first: Boolean = false) {
    Text(
        text,
        style = TextStyle(
            color = appColors.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W700,
        ),
        modifier = Modifier.padding(top = if (first) 0.dp else 16.dp, bottom = 8.dp),
    )
}

/** A card of settings rows, divided from each other by a hairline. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = appColors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card),
        content = content,
    )
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(color = appColors.divider, modifier = Modifier.padding(start = 52.dp))
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    danger: Boolean = false,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(start = 12.dp, top = 9.dp, end = 10.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QIconBadge(icon = icon, color = if (enabled) tint else colors.textMuted, size = 30.dp, iconSize = 17.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(
                    color = when {
                        !enabled -> colors.textMuted
                        danger -> colors.danger
                        else -> colors.textPrimary
                    },
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                description,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 15.sp),
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            trailing != null -> trailing()
            onClick != null && chevron -> Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    warning: String? = null,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = 12.dp, top = 9.dp, end = 10.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QIconBadge(icon = icon, color = if (enabled) tint else colors.textMuted, size = 30.dp, iconSize = 17.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(
                    color = if (enabled) colors.textPrimary else colors.textMuted,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                warning ?: description,
                style = TextStyle(
                    color = if (warning != null) colors.warning else colors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
            ),
        )
    }
}
