package app.carouselmaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0F0F11)
    val Bg2 = Color(0xFF08080A)
    val Surface = Color(0xFF1A1A1F)
    val Surface2 = Color(0xFF26262D)
    val Surface3 = Color(0xFF33333B)
    val Bar = Color(0xFF141418)
    val Text = Color(0xFFF4F4F5)
    val TextDim = Color(0xFFA1A1AA)
    val Accent = Color(0xFFD4FF3A)
    val OnAccent = Color(0xFF0F0F11)
    val Paper = Color(0xFFF5F3EE)
    val Danger = Color(0xFFFF8A80)
    val Snap = Color(0xFF3AD4FF)
}

private val Scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.TextDim,
    secondary = Palette.Accent,
    error = Palette.Danger,
)

@Composable
fun CarouselTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}
