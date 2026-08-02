package com.example.ohmyssh.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors

private val pathCache = HashMap<String, List<Path>>()

private fun pathsFor(id: String): List<Path> = pathCache.getOrPut(id) {
    val icon = osIcons[id] ?: osIcons.getValue("unknown")
    icon.paths.map { data ->
        PathParser().parsePathString(data).toPath().apply {
            fillType = PathFillType.NonZero
        }
    }
}

@Composable
fun QIcon(
    asset: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val icon = osIcons[asset] ?: osIcons.getValue("unknown")
    val paths = remember(asset) { pathsFor(asset) }

    Canvas(modifier.size(size)) {
        val scaleFactor = if (icon.viewportWidth <= 0f || icon.viewportHeight <= 0f) {
            1f
        } else {
            this.size.minDimension / maxOf(icon.viewportWidth, icon.viewportHeight)
        }
        val dx = (this.size.width - icon.viewportWidth * scaleFactor) / 2
        val dy = (this.size.height - icon.viewportHeight * scaleFactor) / 2
        translate(dx, dy) {
            scale(scaleFactor, scaleFactor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                for (path in paths) {
                    drawPath(path, color)
                }
            }
        }
    }
}

class QIconBadgeStyle(
    val background: Color,
    val foreground: Color,
) {
    companion object {
        fun forColors(
            colors: QAppColors,
            color: Color,
            darkOpacity: Float = 0.18f,
        ): QIconBadgeStyle {
            if (colors.isDark) {
                return QIconBadgeStyle(
                    background = color.copy(alpha = darkOpacity),
                    foreground = color,
                )
            }
            val isNearWhite = color.luminance() > 0.9f
            val background = if (isNearWhite) colors.textMuted else color
            return QIconBadgeStyle(
                background = background,
                foreground = QAppColors.onColorFor(background),
            )
        }
    }
}

@Composable
fun QIconBadge(
    icon: ImageVector,
    color: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 22.dp,
    backgroundOpacity: Float = 0.18f,
    borderRadius: Dp = 8.dp,
) {
    val style = QIconBadgeStyle.forColors(appColors, color, backgroundOpacity)
    Box(
        Modifier
            .size(size)
            .background(style.background, RoundedCornerShape(borderRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = style.foreground, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun QIconBadgeSvg(
    asset: String,
    color: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 24.dp,
    backgroundOpacity: Float = 0.18f,
    borderRadius: Dp = 8.dp,
) {
    val style = QIconBadgeStyle.forColors(appColors, color, backgroundOpacity)
    Box(
        Modifier
            .size(size)
            .background(style.background, RoundedCornerShape(borderRadius)),
        contentAlignment = Alignment.Center,
    ) {
        QIcon(asset = asset, color = style.foreground, size = iconSize)
    }
}
