package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.windowWidthFor

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
fun AppShell(
    currentTab: RootTab,
    onTabSelected: (RootTab) -> Unit,
    sessionCount: Int = 0,
    content: @Composable () -> Unit,
) {
    val colors = appColors
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        if (windowWidthFor(maxWidth).isCompact) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(WindowInsets.navigationBars),
                ) { content() }
                NavigationBar(containerColor = colors.card, tonalElevation = 0.dp) {
                    for (tab in RootTab.entries) {
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onTabSelected(tab) },
                            icon = { TabIcon(tab, selected, badgeFor(tab, sessionCount)) },
                            label = { TabLabel(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.textPrimary,
                                indicatorColor = colors.accent.copy(alpha = 0.16f),
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                            ),
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = colors.card,
                    header = { Spacer(Modifier.height(12.dp)) },
                ) {
                    for (tab in RootTab.entries) {
                        val selected = tab == currentTab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTabSelected(tab) },
                            icon = { TabIcon(tab, selected, badgeFor(tab, sessionCount)) },
                            label = { TabLabel(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.textPrimary,
                                indicatorColor = colors.accent.copy(alpha = 0.16f),
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                            ),
                        )
                    }
                }
                VerticalDivider(color = colors.divider)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)),
                ) { content() }
            }
        }
    }
}

private fun badgeFor(tab: RootTab, sessionCount: Int): Int =
    if (tab == RootTab.SESSIONS) sessionCount else 0

@Composable
private fun TabIcon(tab: RootTab, selected: Boolean, badge: Int) {
    val colors = appColors
    BadgedBox(
        badge = {
            if (badge > 0) {
                Badge(containerColor = colors.accent, contentColor = colors.onAccent) {
                    Text("$badge")
                }
            }
        },
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
    }
}

@Composable
private fun TabLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.W600)
}
