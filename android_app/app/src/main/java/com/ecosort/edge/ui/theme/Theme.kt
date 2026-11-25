package com.ecosort.edge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object EcoColors {
    val Canvas = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF7F9FF)
    val SurfaceCard = Color(0xFFEDF4FF)
    val Border = Color(0xFFE4E7E5)
    val TextPrimary = Color(0xFF131D26)
    val TextSecondary = Color(0xFF59636E)
    val ForestPrimary = Color(0xFF0D3B31)
    val ForestAccent = Color(0xFF124E40)
    val EmeraldBadge = Color(0xFF006D30)
    val EmeraldBg = Color(0xFF92F5A4)
    val HazardRed = Color(0xFFC62828)
    val HazardBg = Color(0xFFFFDAD6)
}

private val EcoLightScheme = lightColorScheme(
    primary = EcoColors.ForestPrimary,
    onPrimary = Color.White,
    secondary = EcoColors.EmeraldBadge,
    onSecondary = Color.White,
    background = EcoColors.Canvas,
    onBackground = EcoColors.TextPrimary,
    surface = EcoColors.Surface,
    onSurface = EcoColors.TextPrimary,
    surfaceVariant = EcoColors.SurfaceCard,
    onSurfaceVariant = EcoColors.TextSecondary,
    outline = EcoColors.Border,
    error = EcoColors.HazardRed
)

private val EcoTypography = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = EcoColors.TextPrimary),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = EcoColors.TextPrimary),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EcoColors.TextPrimary),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, color = EcoColors.TextPrimary),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp, color = EcoColors.TextSecondary),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = EcoColors.TextSecondary)
)

@Composable
fun EcoSortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EcoLightScheme,
        typography = EcoTypography,
        content = content
    )
}
