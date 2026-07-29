package cn.silverdragon.draarl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Material3 color schemes ──────────────────────────────────────────────────

private val LightColors = lightColorScheme(
    primary = Color(0xFF2856D7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF10265F),
    secondary = Color(0xFF50637B),
    secondaryContainer = Color(0xFFE3E9F2),
    tertiary = Color(0xFF087F79),
    tertiaryContainer = Color(0xFFB8F2E9),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFCFCFE),
    surfaceVariant = Color(0xFFE8ECF3),
    outline = Color(0xFF737A88),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC2FF),
    onPrimary = Color(0xFF172D72),
    primaryContainer = Color(0xFF29479A),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFBBC7DC),
    secondaryContainer = Color(0xFF344258),
    tertiary = Color(0xFF72D9CB),
    tertiaryContainer = Color(0xFF07534F),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0D1422),
    surface = Color(0xFF121B2A),
    surfaceVariant = Color(0xFF273347),
    onSurfaceVariant = Color(0xFFC1C9D8),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val AppTypography = Typography(
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.SemiBold,
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.SemiBold,
    ),
)

// ── Semantic / status colours ────────────────────────────────────────────────

/**
 * App-specific semantic colours that sit outside the Material3 role system
 * (connection status, latency grades, dashboard stat accents).
 *
 * Access them in any @Composable as [MaterialTheme.appColors].
 */
data class AppColors(
    /** UDP connected / approved / online-device indicator */
    val statusConnected: Color,
    /** Connecting / pending-approval / in-progress indicator */
    val statusWarning: Color,
    /** Dashboard: devices stat accent */
    val statDevices: Color,
    /** Dashboard: groups stat accent */
    val statGroups: Color,
    /** Dashboard: comms-count stat accent */
    val statComms: Color,
    /** Dashboard: cumulative-duration stat accent */
    val statDuration: Color,
    /** Latency ≤ 80 ms */
    val latencyGood: Color,
    /** Latency 81–180 ms */
    val latencyWarn: Color,
)

private val LightAppColors = AppColors(
    statusConnected = Color(0xFF087F79),
    statusWarning   = Color(0xFFB26A00),
    statDevices     = Color(0xFF087F79),
    statGroups      = Color(0xFF2856D7),
    statComms       = Color(0xFFC05621),
    statDuration    = Color(0xFF8E5A00),
    latencyGood     = Color(0xFF147D55),
    latencyWarn     = Color(0xFFB26A00),
)

private val DarkAppColors = AppColors(
    statusConnected = Color(0xFF72D9CB),
    statusWarning   = Color(0xFFFFC46B),
    statDevices     = Color(0xFF72D9CB),
    statGroups      = Color(0xFFAEC2FF),
    statComms       = Color(0xFFFFAA78),
    statDuration    = Color(0xFFFFC46B),
    latencyGood     = Color(0xFF7ED9A8),
    latencyWarn     = Color(0xFFFFC46B),
)

val LocalAppColors = compositionLocalOf { LightAppColors }
val LocalAppDarkTheme = compositionLocalOf { false }

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

val MaterialTheme.isDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAppDarkTheme.current

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun DraarlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors,
        LocalAppDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
