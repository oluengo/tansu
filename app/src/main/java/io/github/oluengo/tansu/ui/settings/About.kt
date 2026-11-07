package io.github.oluengo.tansu.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oluengo.tansu.BuildConfig
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.AppLanguage
import io.github.oluengo.tansu.ui.theme.Ts
import io.github.oluengo.tansu.ui.common.Group
import java.util.Locale

const val REPO_URL = "https://github.com/oluengo/tansu"
const val NOSTRICH_URL = "https://github.com/SovrynMatt/Nostr-Website-Button-Design"

/** Las mismas direcciones que en Usus. Se copian al tocar. */
private data class Donation(val name: String, val address: String, val color: Color, val symbol: String, val symbolColor: Color)

private val DONATIONS = listOf(
    Donation("Bitcoin", "bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa", Color(0xFFF7931A), "₿", Color.White),
    Donation("Lightning / zaps", "oluengo@rizful.com", Color(0xFF7B3FE4), "⚡", Color(0xFFFFD43B)),
)

/** Texto legal válido: el original en inglés (igual que LICENSE). */

private const val MIT_TEXT = """MIT License

Copyright (c) 2026 Oscar Luengo

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""


/** Datos técnicos para los avisos de fallo (nunca incluye datos personales ni la npub). */
fun techInfo(): String =
    "Tansu ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""}\n" +
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
        (AppLanguage.current() ?: "auto:" + Locale.getDefault().language)

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun issueUrl(ctx: Context, bug: Boolean): String {
    val body = if (bug) {
        "**${ctx.getString(R.string.issue_what_happened)}**\n\n\n**${ctx.getString(R.string.issue_steps)}**\n1. \n\n**${ctx.getString(R.string.issue_expected)}**\n\n\n---\n" + techInfo()
    } else {
        "**${ctx.getString(R.string.issue_what_you_want)}**\n\n\n**${ctx.getString(R.string.issue_what_for)}**\n\n\n---\nTansu ${BuildConfig.VERSION_NAME}"
    }
    return "$REPO_URL/issues/new?labels=${if (bug) "bug" else "enhancement"}&body=${Uri.encode(body)}"
}

/** Acerca de: versión, «hecha con IA · probada por humanos», licencia, enlaces y apoyo. */
@Composable
fun AboutSection() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var licenseOpen by remember { mutableStateOf(false) }

    Group {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.version, versionLabel), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.about_ai), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        Text(
            (if (licenseOpen) "▾ " else "▸ ") + stringResource(R.string.about_license_title),
            color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp).clickable { licenseOpen = !licenseOpen }.padding(vertical = 4.dp),
        )
        if (licenseOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.about_license_body), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_warranty), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    MIT_TEXT, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState()).padding(10.dp),
                )
                if (ctx.resources.configuration.locales[0].language != "en") {
                    Text(stringResource(R.string.about_legal_english), color = Ts.colors.muted, fontSize = 11.sp)
                }
            }
        }
    }

    Box(Modifier.padding(top = 8.dp)) {
        Group {
            LinkRow(Icons.Outlined.Code, R.string.about_source, "github.com/oluengo/tansu", "↗") { openUrl(ctx, REPO_URL) }
            HorizontalDivider(color = Ts.colors.line)
            LinkRow(Icons.Outlined.BugReport, R.string.about_bug, stringResource(R.string.about_bug_sub), "↗") { openUrl(ctx, issueUrl(ctx, bug = true)) }
            HorizontalDivider(color = Ts.colors.line)
            LinkRow(Icons.Outlined.Lightbulb, R.string.about_idea, stringResource(R.string.about_idea_sub), "↗") { openUrl(ctx, issueUrl(ctx, bug = false)) }
        }
    }

    androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
    io.github.oluengo.tansu.ui.common.SectionLabel(stringResource(R.string.credits_title))
    Group {
        Row(Modifier.fillMaxWidth().clickable { openUrl(ctx, NOSTRICH_URL) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            io.github.oluengo.tansu.ui.common.AppLogo(36.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.credits_nostrich), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.credits_nostrich_sub), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("↗", color = Ts.colors.muted)
        }
    }

    androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
    io.github.oluengo.tansu.ui.common.SectionLabel(stringResource(R.string.support_title))
    Group {
        Text(stringResource(R.string.support_body), color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        val copied = stringResource(R.string.copied)
        DONATIONS.forEach { d ->
            val label = stringResource(R.string.copy_address, d.name)
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = label) {
                    clipboard.setText(AnnotatedString(d.address))
                    Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show()
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(32.dp).background(d.color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(d.symbol, color = d.symbolColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.SemiBold)
                    Text(shortAddr(d.address), fontFamily = FontFamily.Monospace, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.ContentCopy, contentDescription = label, tint = Ts.colors.muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun shortAddr(a: String) = if (a.length > 24) "${a.take(12)}…${a.takeLast(8)}" else a

@Composable
private fun LinkRow(icon: ImageVector, @StringRes title: Int, sub: String, end: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(sub, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(end, color = Ts.colors.muted)
    }
}
