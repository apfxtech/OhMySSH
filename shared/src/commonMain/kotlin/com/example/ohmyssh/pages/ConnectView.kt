package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.session.Checkpoint
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.StageStatus
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors

@Composable
fun ConnectView(session: TerminalSession, onRetry: () -> Unit) {
    val colors = appColors
    val failed = session.state == SessionState.FAILED

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SessionBadge(session)
            Spacer(Modifier.height(16.dp))
            Text(
                session.title,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                endpointOf(session),
                textAlign = TextAlign.Center,
                style = TextStyle(color = colors.textMuted, fontSize = 13.sp),
            )
            Spacer(Modifier.height(28.dp))
            for (checkpoint in session.checkpoints) {
                CheckpointRow(checkpoint)
            }
            if (failed) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.danger.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    SelectionContainer {
                        Text(
                            "${session.error}",
                            style = TextStyle(
                                color = colors.danger,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { Text("Try again") }
            }
        }
    }
}

private fun endpointOf(session: TerminalSession): String = when (session) {
    is HostSession -> session.host.endpoint
    is SerialSession -> "${session.device.path} · ${session.device.lineSettings}"
    else -> session.subtitle
}

@Composable
private fun SessionBadge(session: TerminalSession) {
    if (session is SerialSession) {
        QIconBadge(
            icon = Icons.Filled.Usb,
            color = appColors.info,
            size = 72.dp,
            iconSize = 44.dp,
            borderRadius = 20.dp,
        )
        return
    }

    val osId = (session as? HostSession)?.let { it.profile?.osId ?: it.host.osId }
    QIconBadgeSvg(
        asset = osIconAsset(osId),
        color = Color(osColorValue(osId)),
        size = 72.dp,
        iconSize = 44.dp,
        borderRadius = 20.dp,
    )
}

@Composable
private fun CheckpointRow(checkpoint: Checkpoint) {
    val colors = appColors
    val (markColor, labelColor) = when (checkpoint.status) {
        StageStatus.WAITING -> colors.textMuted to colors.textMuted
        StageStatus.RUNNING -> colors.accent to colors.textPrimary
        StageStatus.DONE -> colors.success to colors.textSecondary
        StageStatus.FAILED -> colors.danger to colors.danger
        StageStatus.SKIPPED -> colors.warning to colors.textMuted
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            StatusMark(checkpoint.status, markColor)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                checkpoint.label,
                style = TextStyle(
                    color = labelColor,
                    fontSize = 14.sp,
                    lineHeight = 17.5.sp,
                    fontWeight = if (checkpoint.status == StageStatus.RUNNING) {
                        FontWeight.W700
                    } else {
                        FontWeight.W500
                    },
                ),
            )
            val detail = checkpoint.detail
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusMark(status: StageStatus, color: Color) {
    when (status) {
        StageStatus.RUNNING -> CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = color,
            modifier = Modifier.size(16.dp),
        )
        StageStatus.WAITING -> Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        StageStatus.DONE -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        StageStatus.FAILED -> Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        StageStatus.SKIPPED -> Icon(
            Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}
