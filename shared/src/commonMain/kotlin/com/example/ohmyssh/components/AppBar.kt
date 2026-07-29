package com.example.ohmyssh.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.theme.appColors

val kToolbarHeight = 68.dp

internal class AppBarTooltipState {
    var message: String? by mutableStateOf(null)
    var arrowCenterX: Float by mutableStateOf(0f)
}

@Composable
fun QScaffold(
    appBar: (@Composable () -> Unit)? = null,
    background: Color = appColors.background,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(background)) {
        if (appBar != null) {
            Box(Modifier.zIndex(1f)) { appBar() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
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
    val foreground = foregroundColor ?: colors.onAccent
    val background = backgroundColor ?: colors.accent
    val tooltip = remember { AppBarTooltipState() }
    val navigator = LocalNavigator.current

    Box(Modifier.fillMaxWidth()) {
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
                    val scope = remember(tooltip) { AppBarActionsScope(tooltip) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        scope.actions()
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }

        val message = tooltip.message
        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = kToolbarHeight)
                .fillMaxWidth(),
            enter = fadeIn(tween(160)) + expandVertically(tween(160), expandFrom = Alignment.Top),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120), shrinkTowards = Alignment.Top),
        ) {
            var lastMessage by remember { mutableStateOf("") }
            if (message != null) lastMessage = message
            WideTooltip(
                text = lastMessage,
                arrowCenter = tooltip.arrowCenterX,
                appBarColor = background,
            )
        }
    }
}

class AppBarActionsScope internal constructor(
    internal val tooltip: AppBarTooltipState,
)

@Composable
fun AppBarActionsScope.QPageAppBarAction(
    tooltip: String,
    icon: ImageVector,
    iconTint: Color? = null,
    iconSize: Dp = 20.dp,
    native: Boolean = false,
    onPressed: (() -> Unit)?,
) {
    val colors = appColors
    val tint = iconTint ?: colors.onAccent
    val state = this.tooltip
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var centerX by remember { mutableStateOf(0f) }

    if (!native) {
        LaunchedEffect(hovered, tooltip) {
            if (hovered) {
                kotlinx.coroutines.delay(350)
                state.arrowCenterX = centerX
                state.message = tooltip
            } else if (state.message == tooltip) {
                state.message = null
            }
        }
    }

    IconButton(
        onClick = { onPressed?.invoke() },
        enabled = onPressed != null,
        modifier = Modifier
            .hoverable(interaction)
            .onGloballyPositioned {
                centerX = it.positionInParent().x + it.size.width / 2f
            },
    ) {
        Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun WideTooltip(
    text: String,
    arrowCenter: Float,
    appBarColor: Color,
) {
    val colors = appColors
    val arrowSize = 8.dp
    val tooltipColor = appBarColor.copy(alpha = 0.30f).compositeOver(colors.background)

    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(arrowSize)) {
            val arrowPx = arrowSize.toPx()
            val maxLeft = size.width - arrowPx * 2
            val left = (arrowCenter - arrowPx).coerceIn(0f, maxLeft.coerceAtLeast(0f))
            val path = Path().apply {
                moveTo(left, arrowPx)
                lineTo(left + arrowPx, 0f)
                lineTo(left + arrowPx * 2, arrowPx)
                close()
            }
            drawPath(path, tooltipColor)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(tooltipColor)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
