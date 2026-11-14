package com.throne.ui.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ThroneColors {
    val BgBase = Color(0xFF0B0D13)
    val BgCard = Color(0xFF141824)
    val BgCardElevated = Color(0xFF1A1F2E)
    val GlassFill = Color(0x16FFFFFF)
    val GlassFillStrong = Color(0x28FFFFFF)
    val GlassBorder = Color(0x30FFFFFF)
    val GlassBorderBright = Color(0x66FFFFFF)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xFF939BB4)
    val TextMuted = Color(0xFF6B7388)
    val MintAccent = Color(0xFF70E1BD)
    val MintGreen = Color(0xFF5EE6B5)
    val Amber = Color(0xFFF0CA75)
    val Danger = Color(0xFFFF8C9E)
    val DangerFill = Color(0x28FF8C9E)
}

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    secondary = ThroneColors.MintAccent,
    background = ThroneColors.BgBase,
    surface = ThroneColors.BgCard,
    onPrimary = Color(0xFF1A1E2E),
    onBackground = ThroneColors.TextPrimary,
    onSurface = ThroneColors.TextPrimary
)

@Composable
fun ThroneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
