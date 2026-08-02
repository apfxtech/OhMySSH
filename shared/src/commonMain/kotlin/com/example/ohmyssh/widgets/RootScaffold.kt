package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors

enum class RootTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    SYSTEMS("Systems", Icons.Outlined.Dns, Icons.Filled.Dns),
    USERS("Users", Icons.Outlined.Person, Icons.Filled.Person),
    SESSIONS("Sessions", Icons.Outlined.Terminal, Icons.Filled.Terminal),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun RootScaffold(
    currentTab: RootTab,
    onTabSelected: (RootTab) -> Unit,
    sessionCount: Int = 0,
    content: @Composable () -> Unit,
) {
    val colors = appColors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        // The tab bar already covers the navigation-bar inset; without
        // consuming it here every tab page would pad the bottom twice.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) { content() }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.card)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                for (tab in RootTab.entries) {
                    BottomTab(
                        tab = tab,
                        selected = currentTab == tab,
                        badge = if (tab == RootTab.SESSIONS) sessionCount else 0,
                        onTap = { onTabSelected(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTab(
    tab: RootTab,
    selected: Boolean,
    badge: Int,
    onTap: () -> Unit,
) {
    val colors = appColors
    val color = if (selected) colors.accent else colors.textMuted

    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(42.dp).height(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                tint = color,
                modifier = Modifier.size(23.dp),
            )
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 0.dp)
                        .background(colors.accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "$badge",
                        style = TextStyle(
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.W700,
                            color = colors.onAccent,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W700, color = color),
        )
    }
}
