package io.github.oluengo.tansu.ui.common

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.oluengo.tansu.R
import io.github.oluengo.tansu.data.Account
import io.github.oluengo.tansu.data.AppData
import io.github.oluengo.tansu.data.Method
import io.github.oluengo.tansu.data.Profile
import io.github.oluengo.tansu.data.VaultException
import io.github.oluengo.tansu.nostr.Nip19
import io.github.oluengo.tansu.ui.theme.Ts
import kotlin.math.absoluteValue

/** Bloque con borde y fondo de tarjeta (como en Wavestr). */
@Composable
fun Group(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, Ts.colors.line),
        modifier = modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ts.colors.muted, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Etiqueta pequeña redondeada. */
@Composable
fun Badge(text: String, color: Color = Ts.colors.muted, background: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Ts.colors.muted, style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(vertical = 24.dp))
}

/** Icono de la app (carpeta con el Nostrich) sobre su fondo. */
@Composable
fun AppLogo(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(colorResource(R.color.ic_bg))) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private val AVATAR_COLORS = listOf(0xFF3D4FA8, 0xFFB07A4A, 0xFF7B4FD6, 0xFF2E8A5A, 0xFFC2410C, 0xFF9D174D, 0xFF334155).map { Color(it) }

/** Foto del perfil o inicial sobre un color estable. */
@Composable
fun Avatar(account: Account, profile: Profile?, size: Dp = 32.dp) {
    val name = accountName(account, profile)
    Box(
        Modifier.size(size).clip(CircleShape).background(AVATAR_COLORS[account.pubkey.hashCode().absoluteValue % AVATAR_COLORS.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        profile?.picture?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

fun accountName(a: Account, p: Profile?): String = a.label ?: p?.name ?: Nip19.shortNpub(a.pubkey)

fun methodLabel(m: Method): Int = when (m) {
    Method.AMBER -> R.string.method_amber
    Method.BUNKER -> R.string.method_bunker
    Method.READ_ONLY -> R.string.method_readonly
}

/** Nombre visible de una carpeta de app. */
@Composable
fun appName(app: String): String = when {
    app.isEmpty() -> stringResource(R.string.folder_other)
    else -> AppData.KNOWN[app] ?: app
}

fun ago(ctx: Context, sec: Long): String =
    DateUtils.getRelativeTimeSpanString(sec * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun fullDate(sec: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(sec * 1000))

fun size(ctx: Context, bytes: Int): String = android.text.format.Formatter.formatShortFileSize(ctx, bytes.toLong())

/** Texto de error para cada motivo. */
fun errorText(ctx: Context, e: Throwable?): String = ctx.getString(
    when ((e as? VaultException)?.reason) {
        VaultException.Reason.NEEDS_SIGNER -> R.string.err_signer
        VaultException.Reason.READ_ONLY -> R.string.err_readonly
        VaultException.Reason.UNREADABLE -> R.string.err_unreadable
        VaultException.Reason.TOO_BIG -> R.string.err_too_big
        VaultException.Reason.NOT_ENCRYPTED -> R.string.err_not_encrypted
        VaultException.Reason.OFFLINE, null -> R.string.err_offline
    }
)

/** Fila de texto con etiqueta a la izquierda (detalles de una entrada). */
@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Ts.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.65f))
    }
}
