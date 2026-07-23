package cn.silverdragon.draarl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E1),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4B635E),
    secondaryContainer = Color(0xFFCDE8E1),
    tertiary = Color(0xFF765B00),
    tertiaryContainer = Color(0xFFFFDF8A),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF8FAF9),
    surface = Color(0xFFF8FAF9),
    surfaceVariant = Color(0xFFDAE5E1),
    outline = Color(0xFF6F7976),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C5),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    secondary = Color(0xFFB2CCC5),
    tertiary = Color(0xFFF1C84F),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF3F4946),
)

@Composable
fun DraarlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
