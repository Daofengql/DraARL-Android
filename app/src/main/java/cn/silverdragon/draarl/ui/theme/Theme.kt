package cn.silverdragon.draarl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B75),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EFF2),
    onPrimaryContainer = Color(0xFF0A4148),
    inversePrimary = Color(0xFF7DD4DD),
    secondary = Color(0xFF4E5F61),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1EBEB),
    onSecondaryContainer = Color(0xFF344748),
    tertiary = Color(0xFF805D16),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E3B2),
    onTertiaryContainer = Color(0xFF4E3600),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF171C1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C1B),
    surfaceVariant = Color(0xFFE8ECEB),
    onSurfaceVariant = Color(0xFF505A59),
    surfaceTint = Color(0xFF176B75),
    inverseSurface = Color(0xFF2A302F),
    inverseOnSurface = Color(0xFFF1F4F3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF7C1A16),
    outline = Color(0xFF707978),
    outlineVariant = Color(0xFFCDD5D3),
    scrim = Color(0x99000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEE4E2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFEFF3F1),
    surfaceContainerHigh = Color(0xFFE8EDEB),
    surfaceContainerHighest = Color(0xFFE0E6E4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD4DD),
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF174D55),
    onPrimaryContainer = Color(0xFFBDEBF0),
    inversePrimary = Color(0xFF176B75),
    secondary = Color(0xFFB9CACA),
    onSecondary = Color(0xFF263738),
    secondaryContainer = Color(0xFF334746),
    onSecondaryContainer = Color(0xFFD5E5E4),
    tertiary = Color(0xFFEAC16F),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5C420D),
    onTertiaryContainer = Color(0xFFFFDEA1),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE1E6E3),
    surface = Color(0xFF141816),
    onSurface = Color(0xFFE1E6E3),
    surfaceVariant = Color(0xFF282E2B),
    onSurfaceVariant = Color(0xFFBAC4C0),
    surfaceTint = Color(0xFF7DD4DD),
    inverseSurface = Color(0xFFE1E6E3),
    inverseOnSurface = Color(0xFF2A302D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5B211E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3B4441),
    scrim = Color(0xB3000000),
    surfaceBright = Color(0xFF323936),
    surfaceDim = Color(0xFF101412),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = Color(0xFF171C19),
    surfaceContainer = Color(0xFF1C221F),
    surfaceContainerHigh = Color(0xFF222925),
    surfaceContainerHighest = Color(0xFF29312D)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
)

@Immutable
data class AppColors(
    val statusConnected: Color,
    val statusConnecting: Color,
    val statusOffline: Color,
    val transmit: Color,
    val onTransmit: Color,
    val transmitContainer: Color,
    val onTransmitContainer: Color,
    val receive: Color,
    val onReceive: Color,
    val receiveContainer: Color,
    val onReceiveContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val disabled: Color,
    val onDisabled: Color,
    val divider: Color,
    val latencyGood: Color,
    val latencyWarn: Color,
    val latencyPoor: Color,
    val statDevices: Color,
    val statGroups: Color,
    val statComms: Color,
    val statDuration: Color
)

private val LightAppColors = AppColors(
    statusConnected = Color(0xFF167153),
    statusConnecting = Color(0xFF9A5B00),
    statusOffline = Color(0xFF686D75),
    transmit = Color(0xFFB3261E),
    onTransmit = Color(0xFFFFFFFF),
    transmitContainer = Color(0xFFFCE8E6),
    onTransmitContainer = Color(0xFF7C1A16),
    receive = Color(0xFF006C67),
    onReceive = Color(0xFFFFFFFF),
    receiveContainer = Color(0xFFD8EFEC),
    onReceiveContainer = Color(0xFF154B47),
    warning = Color(0xFF8A5500),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE9C6),
    onWarningContainer = Color(0xFF603A00),
    disabled = Color(0xFFE0E6E4),
    onDisabled = Color(0xFF747E7B),
    divider = Color(0xFFCDD5D3),
    latencyGood = Color(0xFF167153),
    latencyWarn = Color(0xFF9A5B00),
    latencyPoor = Color(0xFFB3261E),
    statDevices = Color(0xFF006C67),
    statGroups = Color(0xFF6F5A8B),
    statComms = Color(0xFF9B4E24),
    statDuration = Color(0xFF785C00)
)

private val DarkAppColors = AppColors(
    statusConnected = Color(0xFF76D6AD),
    statusConnecting = Color(0xFFFFC46B),
    statusOffline = Color(0xFFA9AFB8),
    transmit = Color(0xFFFFB4AB),
    onTransmit = Color(0xFF690005),
    transmitContainer = Color(0xFF5B211E),
    onTransmitContainer = Color(0xFFFFDAD6),
    receive = Color(0xFF77D6CE),
    onReceive = Color(0xFF003733),
    receiveContainer = Color(0xFF174C48),
    onReceiveContainer = Color(0xFFB7EEE9),
    warning = Color(0xFFFFC46B),
    onWarning = Color(0xFF4A2B00),
    warningContainer = Color(0xFF573C14),
    onWarningContainer = Color(0xFFFFDDA6),
    disabled = Color(0xFF29312D),
    onDisabled = Color(0xFF7C8682),
    divider = Color(0xFF3B4441),
    latencyGood = Color(0xFF76D6AD),
    latencyWarn = Color(0xFFFFC46B),
    latencyPoor = Color(0xFFFFB4AB),
    statDevices = Color(0xFF77D6CE),
    statGroups = Color(0xFFCFC1EE),
    statComms = Color(0xFFFFB68F),
    statDuration = Color(0xFFE5CA65)
)

@Immutable
data class AppDimensions(
    val pagePadding: Dp = 16.dp,
    val compactPadding: Dp = 12.dp,
    val itemGap: Dp = 8.dp,
    val sectionGap: Dp = 24.dp,
    val divider: Dp = 1.dp,
    val controlHeight: Dp = 44.dp,
    val largeControlHeight: Dp = 52.dp,
    val bottomBarHeight: Dp = 64.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 20.dp,
    val iconLarge: Dp = 24.dp
)

@Immutable
data class AppMotion(val quick: Int = 80, val short: Int = 140, val medium: Int = 220, val long: Int = 320)

@Immutable
data class DataTypography(val identity: TextStyle, val value: TextStyle, val compact: TextStyle)

private val DefaultDataTypography = DataTypography(
    identity = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    value = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    compact = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
)

private val LocalAppColors = compositionLocalOf { LightAppColors }
private val LocalAppDimensions = compositionLocalOf { AppDimensions() }
private val LocalAppMotion = compositionLocalOf { AppMotion() }
private val LocalDataTypography = compositionLocalOf { DefaultDataTypography }
private val LocalAppDarkTheme = compositionLocalOf { false }

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

val MaterialTheme.appDimensions: AppDimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalAppDimensions.current

val MaterialTheme.appMotion: AppMotion
    @Composable
    @ReadOnlyComposable
    get() = LocalAppMotion.current

val MaterialTheme.dataTypography: DataTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalDataTypography.current

val MaterialTheme.isDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAppDarkTheme.current

@Composable
fun DraarlTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors,
        LocalAppDimensions provides AppDimensions(),
        LocalAppMotion provides AppMotion(),
        LocalDataTypography provides DefaultDataTypography,
        LocalAppDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
