package cn.silverdragon.draarl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF4E5F7A),
    secondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFF006A67),
    tertiaryContainer = Color(0xFF9CF1ED),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF9F9FC),
    surface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFFE1E7F0),
    outline = Color(0xFF737780),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468B),
    secondary = Color(0xFFBCC7DC),
    tertiary = Color(0xFF80D5D1),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF43474F),
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
