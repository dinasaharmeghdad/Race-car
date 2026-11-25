package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ApexDarkColorScheme = darkColorScheme(
    primary = ApexCrimson,
    onPrimary = TextPrimary,
    primaryContainer = ApexCrimsonDark,
    onPrimaryContainer = TextPrimary,
    secondary = ChampGold,
    onSecondary = ObsidianBg,
    secondaryContainer = ElevatedCarbon,
    onSecondaryContainer = ChampGold,
    tertiary = NitroCyan,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCarbon,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder
)

val ApexShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = Typography,
        shapes = ApexShapes,
        content = content
    )
}
