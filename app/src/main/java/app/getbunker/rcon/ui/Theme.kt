package app.getbunker.rcon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.getbunker.rcon.data.ThemeMode

/** The Bunker palette (taken from the desktop app) plus the few shades Material has no name for. */
@Immutable
data class BunkerColors(
    val bg: Color,
    val panel: Color,
    val input: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val chat: Color,
    val selection: Color,
    val matchBack: Color,
    val isLight: Boolean,
) {
    /**
     * Adjusts a colour made for the game's dark chat so it can be read on the current theme: dark colours are lightened on the
     * dark theme and bright colours darkened on the light theme.
     */
    fun readable(c: Color): Color {
        val l = c.luminance()
        if (!isLight && l < 0.18f) {
            val k = (0.30f - l) / (1f - l)
            return Color(c.red + (1 - c.red) * k, c.green + (1 - c.green) * k, c.blue + (1 - c.blue) * k)
        }
        if (isLight && l > 0.35f) {
            val k = 0.25f / l
            return Color(c.red * k, c.green * k, c.blue * k)
        }
        return c
    }
}

val DarkBunker = BunkerColors(
    bg = Color(0xFF111317), panel = Color(0xFF1A1D23), input = Color(0xFF20242B), border = Color(0xFF30353F),
    text = Color(0xFFE6E8EC), muted = Color(0xFF8A919C), dim = Color(0xFF5A606C),
    accent = Color(0xFFE0592A), ok = Color(0xFF4CC38A), warn = Color(0xFFF0B429), err = Color(0xFFEF5350), chat = Color(0xFF7CC4FF),
    selection = Color(0xFF342620), matchBack = Color(0xFF3E3214), isLight = false,
)

val LightBunker = BunkerColors(
    bg = Color(0xFFF3F4F7), panel = Color(0xFFFFFFFF), input = Color(0xFFF0F2F6), border = Color(0xFFD6DBE3),
    text = Color(0xFF1C1F26), muted = Color(0xFF68707E), dim = Color(0xFF969DA8),
    accent = Color(0xFFDC5222), ok = Color(0xFF168C58), warn = Color(0xFFB07600), err = Color(0xFFCE302C), chat = Color(0xFF126AC4),
    selection = Color(0xFFFDE9DF), matchBack = Color(0xFFFFF3D0), isLight = true,
)

val LocalBunker = staticCompositionLocalOf { DarkBunker }

/** Shorthand: `B.c.accent`. */
object B {
    val c: BunkerColors @Composable @ReadOnlyComposable get() = LocalBunker.current
}

/** Reads "#5af", "#55aaff" or "55aaff". Returns null if it is not a colour. */
fun parseHex(text: String?): Color? {
    if (text.isNullOrBlank()) return null
    var h = text.trim().trimStart('#')
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    if (h.length < 6 || !h.take(6).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return try {
        Color(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    } catch (_: NumberFormatException) {
        null
    }
}

@Composable
fun isDark(mode: String): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    else -> true
}

@Composable
fun BunkerTheme(mode: String, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val c = if (dark) DarkBunker else LightBunker
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White,
            primaryContainer = c.selection, onPrimaryContainer = c.text,
            secondary = c.chat, onSecondary = Color.Black,
            secondaryContainer = c.selection, onSecondaryContainer = c.text,
            tertiary = c.ok,
            background = c.bg, onBackground = c.text,
            surface = c.bg, onSurface = c.text,
            surfaceVariant = c.input, onSurfaceVariant = c.muted,
            surfaceTint = c.accent,
            surfaceContainerLowest = c.bg, surfaceContainerLow = c.bg, surfaceContainer = c.panel,
            surfaceContainerHigh = c.input, surfaceContainerHighest = c.input,
            outline = c.border, outlineVariant = c.border,
            error = c.err, onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White,
            primaryContainer = c.selection, onPrimaryContainer = c.text,
            secondary = c.chat, onSecondary = Color.White,
            secondaryContainer = c.selection, onSecondaryContainer = c.text,
            tertiary = c.ok,
            background = c.bg, onBackground = c.text,
            surface = c.bg, onSurface = c.text,
            surfaceVariant = c.input, onSurfaceVariant = c.muted,
            surfaceTint = c.accent,
            surfaceContainerLowest = c.panel, surfaceContainerLow = c.bg, surfaceContainer = c.panel,
            surfaceContainerHigh = c.input, surfaceContainerHighest = c.input,
            outline = c.border, outlineVariant = c.border,
            error = c.err, onError = Color.White,
        )
    }
    CompositionLocalProvider(LocalBunker provides c) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
