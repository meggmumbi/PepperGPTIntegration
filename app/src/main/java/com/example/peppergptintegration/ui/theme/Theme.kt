package com.example.peppergptintegration.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One palette for the tablet and the researcher's console, so the two read as
 * one system. Sky is identity, coral is the call to action.
 */
object Palette {
    val Sky = Color(0xFF0B79C9)
    val SkyDark = Color(0xFF075C9A)
    val SkyDeep = Color(0xFF004B72)
    val SkyLight = Color(0xFFD4EBFF)
    val SkyWash = Color(0xFFEAF5FF)

    val Coral = Color(0xFFCC4A17)
    val CoralDark = Color(0xFF9C3710)
    val CoralLight = Color(0xFFFFDBCF)

    val Green = Color(0xFF2E7D32)
    val GreenDark = Color(0xFF1F5A23)
    val GreenLight = Color(0xFFD7F2D8)

    val Amber = Color(0xFFC98A00)
    val AmberLight = Color(0xFFFFE9A8)

    val Red = Color(0xFFB3261E)
    val RedLight = Color(0xFFF9DEDC)

    val Ink = Color(0xFF0F1A22)
    val Muted = Color(0xFF4B5E6B)
    val Line = Color(0xFFD5E2EC)
    val LineSoft = Color(0xFFE3EEF6)
    val Background = Color(0xFFF4F7FA)
    val Surface = Color(0xFFFFFFFF)
}

private val colors = lightColorScheme(
    primary = Palette.Sky,
    onPrimary = Color.White,
    primaryContainer = Palette.SkyLight,
    onPrimaryContainer = Palette.SkyDeep,
    secondary = Palette.Coral,
    onSecondary = Color.White,
    secondaryContainer = Palette.CoralLight,
    onSecondaryContainer = Palette.CoralDark,
    tertiary = Palette.Green,
    onTertiary = Color.White,
    tertiaryContainer = Palette.GreenLight,
    onTertiaryContainer = Palette.GreenDark,
    error = Palette.Red,
    onError = Color.White,
    errorContainer = Palette.RedLight,
    onErrorContainer = Palette.Red,
    background = Palette.Background,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SkyWash,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    outlineVariant = Palette.LineSoft,
)

/*
 * Sized for an arm's length. Pepper's tablet is 1280 x 800 at roughly 150 dpi,
 * so a density-independent pixel there is a physical one and text that reads
 * comfortably on a phone would be small on the robot.
 */
private val type = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp,
    ),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
}
