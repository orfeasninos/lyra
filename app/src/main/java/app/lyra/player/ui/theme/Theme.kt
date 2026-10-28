package app.lyra.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Gold = Color(0xFFE8C77A)
private val Ink = Color(0xFF121214)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2B2108),
    secondary = Color(0xFFB9B2D6),
    background = Ink,
    surface = Ink,
    surfaceVariant = Color(0xFF26252C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5C12),
    secondary = Color(0xFF5B5675),
)

@Composable
fun LyraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
