package app.myzel394.alibi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaultTypography = Typography()

val Typography = defaultTypography.copy(
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = defaultTypography.bodyMedium.copy(
        fontFamily = FontFamily.Default,
        letterSpacing = 0.sp,
    ),
    labelLarge = defaultTypography.labelLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
)
