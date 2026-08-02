package com.example.ohmyssh.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.theme.appColors

val kToolbarHeight = 68.dp

@Composable
fun QScaffold(
    appBar: (@Composable () -> Unit)? = null,
    background: Color = appColors.background,
    floatingActions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(background)) {
        if (appBar != null) {
            appBar()
        }
        // Background bleeds edge to edge; content stays clear of the system
        // bars. The app bar covers the status bar inset itself.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(
                    if (appBar == null) {
                        WindowInsets.systemBars
                    } else {
                        WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
                    },
                ),
        ) {
            content()
            if (floatingActions != null) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    floatingActions()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QFloatingAction(
    tooltip: String,
    icon: ImageVector,
    onPressed: (() -> Unit)?,
    primary: Boolean = false,
    iconSize: Dp = 22.dp,
) {
    val colors = appColors
    val enabled = onPressed != null

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        if (primary) {
            FloatingActionButton(
                onClick = { onPressed?.invoke() },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
            ) {
                Icon(icon, contentDescription = tooltip, modifier = Modifier.size(iconSize))
            }
        } else {
            SmallFloatingActionButton(
                onClick = { onPressed?.invoke() },
                containerColor = colors.card,
                contentColor = colors.textPrimary,
                modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
            ) {
                Icon(icon, contentDescription = tooltip, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
fun QPageAppBar(
    title: String,
    subtitle: String? = null,
    statusColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable AppBarActionsScope.() -> Unit)? = null,
    backgroundColor: Color? = null,
    foregroundColor: Color? = null,
) {
    val colors = appColors
    val foreground = foregroundColor ?: colors.textPrimary
    val background = backgroundColor ?: colors.card
    val navigator = LocalNavigator.current

    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.fillMaxWidth().height(kToolbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> leading()
                navigator.canPop -> {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = foreground,
                        )
                    }
                }
                else -> Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = foreground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W700,
                    ),
                )
                if (subtitle != null) {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = TextStyle(
                                color = foreground.copy(alpha = 0.72f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W600,
                            ),
                        )
                        if (statusColor != null) {
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                        }
                    }
                }
            }
            if (actions != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBarActionsScope.actions()
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
    }
}

object AppBarActionsScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarActionsScope.QPageAppBarAction(
    tooltip: String,
    icon: ImageVector,
    iconTint: Color? = null,
    iconSize: Dp = 20.dp,
    onPressed: (() -> Unit)?,
) {
    val colors = appColors
    val tint = iconTint ?: colors.textPrimary

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = { onPressed?.invoke() },
            enabled = onPressed != null,
        ) {
            Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}
