package cn.silverdragon.draarl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Material3 color schemes ──────────────────────────────────────────────────

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
    onPrimaryContainer = Color(0xFFD9E8FF),
    secondary = Color(0xFFBCC7DC),
    tertiary = Color(0xFF80D5D1),
    tertiaryContainer = Color(0xFF004F4D),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF43474F),
    onSurfaceVariant = Color(0xFFC3C6CF),
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
    statusConnected = Color(0xFF087F5B),
    statusWarning   = Color(0xFF9A6700),
    statDevices     = Color(0xFF087F5B),
    statGroups      = Color(0xFF4C5D95),
    statComms       = Color(0xFF9A6700),
    statDuration    = Color(0xFF765B00),
    latencyGood     = Color(0xFF2E7D32),
    latencyWarn     = Color(0xFFF57C00),
)

private val DarkAppColors = AppColors(
    statusConnected = Color(0xFF40C79A),
    statusWarning   = Color(0xFFFFB951),
    statDevices     = Color(0xFF40C79A),
    statGroups      = Color(0xFF8FA3D1),
    statComms       = Color(0xFFFFB951),
    statDuration    = Color(0xFFD4A830),
    latencyGood     = Color(0xFF66BB6A),
    latencyWarn     = Color(0xFFFFB74D),
)

val LocalAppColors = compositionLocalOf { LightAppColors }

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun DraarlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}
