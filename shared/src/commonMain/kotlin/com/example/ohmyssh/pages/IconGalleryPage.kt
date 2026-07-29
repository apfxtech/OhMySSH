package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCardGrid
import com.example.ohmyssh.components.QIcon
import com.example.ohmyssh.components.QIconBadgeSvg
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.ssh.kKnownOsIds
import com.example.ohmyssh.ssh.osColorValue
import com.example.ohmyssh.ssh.osIconAsset
import com.example.ohmyssh.theme.appColors

@Composable
fun IconGalleryPage() {
    val colors = appColors
    val ids = kKnownOsIds.sorted()

    QScaffold(
        appBar = {
            QPageAppBar(
                title = "OS icons",
                subtitle = "${ids.size} glyphs, ${if (colors.isDark) "dark" else "light"}",
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
        ) {
            GroupedCardGrid(
                items = ids,
                maxCrossAxisExtent = 118.dp,
                mainAxisExtent = 86.dp,
                itemBuilder = { id ->
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QIconBadgeSvg(
                                asset = osIconAsset(id),
                                color = Color(osColorValue(id)),
                            )
                            Spacer(Modifier.width(8.dp))
                            QIcon(
                                asset = osIconAsset(id),
                                color = colors.textPrimary,
                                size = 24.dp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            id,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                            ),
                        )
                    }
                },
            )
        }
    }
}
