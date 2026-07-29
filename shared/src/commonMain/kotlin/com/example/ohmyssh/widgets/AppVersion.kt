package com.example.ohmyssh.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.platform.appPlatform
import com.example.ohmyssh.platform.appVersion
import com.example.ohmyssh.platform.displayName
import com.example.ohmyssh.platform.releaseTag
import com.example.ohmyssh.theme.appColors

private fun releaseType(): String? {
    val tag = releaseTag()
    if (tag.isEmpty()) return "local"
    return Regex("^([A-Za-z][A-Za-z0-9]*)-v?[0-9]").find(tag)?.groupValues?.get(1)
}

fun appVersionText(): String {
    val type = releaseType()
    val suffix = if (type == null) "" else " ($type)"
    return "ohmyssh for ${appPlatform.displayName} v$appVersion$suffix"
}

@Composable
fun AppVersionLabel(modifier: Modifier = Modifier) {
    Text(
        appVersionText(),
        textAlign = TextAlign.Center,
        style = TextStyle(color = appColors.textMuted, fontSize = 12.sp, lineHeight = 14.4.sp),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    )
}
