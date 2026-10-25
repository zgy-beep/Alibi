package app.myzel394.alibi.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val AlibiLightColorScheme = lightColorScheme(
    primary = Color(0xFF315A73),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E7F4),
    onPrimaryContainer = Color(0xFF0C3449),
    secondary = Color(0xFF50606A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5EF),
    onSecondaryContainer = Color(0xFF0C1E27),
    tertiary = Color(0xFF775A35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB5),
    onTertiaryContainer = Color(0xFF2A1800),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF181C1F),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF181C1F),
    surfaceDim = Color(0xFFD8DDE0),
    surfaceBright = Color(0xFFF7F9FB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F6),
    surfaceContainer = Color(0xFFEDF1F3),
    surfaceContainerHigh = Color(0xFFE6EBEE),
    surfaceContainerHighest = Color(0xFFE1E6E9),
    surfaceVariant = Color(0xFFDDE3E8),
    onSurfaceVariant = Color(0xFF41484D),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFEFF1F2),
    inversePrimary = Color(0xFF99CEE0),
    surfaceTint = Color(0xFF315A73),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC1C7CC),
)

val AlibiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF99CEE0),
    onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF164C5F),
    onPrimaryContainer = Color(0xFFD1E7F4),
    secondary = Color(0xFFB7C9D5),
    onSecondary = Color(0xFF22323B),
    secondaryContainer = Color(0xFF384953),
    onSecondaryContainer = Color(0xFFD3E5EF),
    tertiary = Color(0xFFE9C18F),
    onTertiary = Color(0xFF422C0D),
    tertiaryContainer = Color(0xFF5E4220),
    onTertiaryContainer = Color(0xFFFFDDB5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE1E3E6),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE1E3E6),
    surfaceDim = Color(0xFF101416),
    surfaceBright = Color(0xFF363B3E),
    surfaceContainerLowest = Color(0xFF0B0F11),
    surfaceContainerLow = Color(0xFF181D20),
    surfaceContainer = Color(0xFF1C2124),
    surfaceContainerHigh = Color(0xFF252B2E),
    surfaceContainerHighest = Color(0xFF303639),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CC),
    inverseSurface = Color(0xFFE1E3E6),
    inverseOnSurface = Color(0xFF2E3133),
    inversePrimary = Color(0xFF315A73),
    surfaceTint = Color(0xFF99CEE0),
    outline = Color(0xFF8B9297),
    outlineVariant = Color(0xFF41484D),
)

@Immutable
data class AlibiExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val captureBackground: Color,
    val captureContent: Color,
)

val LightExtendedColors = AlibiExtendedColors(
    success = Color(0xFF276A49),
    onSuccess = Color.White,
    successContainer = Color(0xFFD7F4E3),
    onSuccessContainer = Color(0xFF0B3220),
    warning = Color(0xFF8A4F00),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE8C6),
    onWarningContainer = Color(0xFF3A2400),
    captureBackground = Color.Black,
    captureContent = Color.White,
)

val DarkExtendedColors = AlibiExtendedColors(
    success = Color(0xFFA5D9BA),
    onSuccess = Color(0xFF00391F),
    successContainer = Color(0xFF145232),
    onSuccessContainer = Color(0xFFC2F2D2),
    warning = Color(0xFFFFC778),
    onWarning = Color(0xFF4B2800),
    warningContainer = Color(0xFF6B3D00),
    onWarningContainer = Color(0xFFFFE8C6),
    captureBackground = Color.Black,
    captureContent = Color.White,
)

val LocalAlibiExtendedColors = staticCompositionLocalOf { LightExtendedColors }
