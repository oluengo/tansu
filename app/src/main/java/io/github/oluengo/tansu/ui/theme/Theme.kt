package io.github.oluengo.tansu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.oluengo.tansu.data.Accent

/** Colores propios de Tansu (los de la demo HTML): neutros cálidos, madera para carpetas y ámbar para «cifrado». */
@Immutable
data class TsColors(
    val muted: Color,
    val line: Color,
    val accentSoft: Color,
    val folder: Color,
    val folderSoft: Color,
    val lock: Color,
    val lockSoft: Color,
    val danger: Color,
)

private val LightTs = TsColors(
    muted = Color(0xFF6B7080), line = Color(0xFFE7E6E1), accentSoft = Color(0xFFE9ECF8),
    folder = Color(0xFFB07A4A), folderSoft = Color(0xFFF5ECDF), lock = Color(0xFFB5651D), lockSoft = Color(0xFFFFF4E6),
    danger = Color(0xFFC0392B),
)
private val DarkTs = TsColors(
    muted = Color(0xFF9A9EAC), line = Color(0xFF2A2D36), accentSoft = Color(0xFF1F2440),
    folder = Color(0xFFD9A273), folderSoft = Color(0xFF33281D), lock = Color(0xFFF0A65A), lockSoft = Color(0xFF33261A),
    danger = Color(0xFFFF7A6B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D4FA8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9ECF8), onPrimaryContainer = Color(0xFF26306E),
    secondary = Color(0xFF1E2230), onSecondary = Color(0xFFF7F7F4),
    background = Color(0xFFF7F7F4), onBackground = Color(0xFF1E2230),
    surface = Color(0xFFF7F7F4), onSurface = Color(0xFF1E2230),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDECE7),
    onSurfaceVariant = Color(0xFF6B7080), outline = Color(0xFFD3D2CC), outlineVariant = Color(0xFFE7E6E1),
    error = Color(0xFFC0392B),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AA8F0), onPrimary = Color(0xFF111528),
    primaryContainer = Color(0xFF1F2440), onPrimaryContainer = Color(0xFFD3DAFF),
    secondary = Color(0xFFE8E9EE), onSecondary = Color(0xFF121318),
    background = Color(0xFF121318), onBackground = Color(0xFFE8E9EE),
    surface = Color(0xFF121318), onSurface = Color(0xFFE8E9EE),
    surfaceContainer = Color(0xFF1A1C22), surfaceContainerHigh = Color(0xFF20232A),
    surfaceContainerLow = Color(0xFF1A1C22), surfaceVariant = Color(0xFF23262E),
    onSurfaceVariant = Color(0xFF9A9EAC), outline = Color(0xFF3A3D47), outlineVariant = Color(0xFF2A2D36),
    error = Color(0xFFFF7A6B),
)

val LocalTsColors = staticCompositionLocalOf { LightTs }

private val TsTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp),
    )
}

/** Acento: (principal, suave, texto sobre suave) en claro y en oscuro. */
private data class AccentColors(val light: Color, val lightSoft: Color, val lightOn: Color, val dark: Color, val darkSoft: Color, val darkOn: Color)

fun accentSwatch(a: Accent): Color = accentColors(a).light

private fun accentColors(a: Accent) = when (a) {
    Accent.INDIGO -> AccentColors(Color(0xFF3D4FA8), Color(0xFFE9ECF8), Color(0xFF26306E), Color(0xFF9AA8F0), Color(0xFF1F2440), Color(0xFFD3DAFF))
    Accent.VIOLET -> AccentColors(Color(0xFF6D28D9), Color(0xFFEDE6FB), Color(0xFF4C1D95), Color(0xFFA78BFA), Color(0xFF2A2142), Color(0xFFDDD0FF))
    Accent.GREEN -> AccentColors(Color(0xFF0E7C6B), Color(0xFFDCEFEA), Color(0xFF0B5E52), Color(0xFF3FBFA6), Color(0xFF1D332E), Color(0xFF9DE3D3))
    Accent.CORAL -> AccentColors(Color(0xFFD9480F), Color(0xFFFDE9DF), Color(0xFF9A3412), Color(0xFFFF8A5B), Color(0xFF3A2318), Color(0xFFFFCDB8))
    Accent.RASPBERRY -> AccentColors(Color(0xFFBE185D), Color(0xFFFBE4EE), Color(0xFF831843), Color(0xFFF472B6), Color(0xFF3B1A2B), Color(0xFFFFC9E2))
}

/** El modo claro/oscuro lo fija AppCompat (setDefaultNightMode) según el ajuste; aquí solo se lee. */
@Composable
fun TansuTheme(accent: Accent = Accent.INDIGO, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val a = accentColors(accent)
    val scheme = if (dark) {
        DarkScheme.copy(primary = a.dark, onPrimary = Color(0xFF0E0F14), primaryContainer = a.darkSoft, onPrimaryContainer = a.darkOn)
    } else {
        LightScheme.copy(primary = a.light, onPrimary = Color.White, primaryContainer = a.lightSoft, onPrimaryContainer = a.lightOn)
    }
    val ts = (if (dark) DarkTs else LightTs).copy(accentSoft = if (dark) a.darkSoft else a.lightSoft)
    CompositionLocalProvider(LocalTsColors provides ts) {
        MaterialTheme(colorScheme = scheme, typography = TsTypography, content = content)
    }
}

object Ts {
    val colors: TsColors @Composable get() = LocalTsColors.current
}
