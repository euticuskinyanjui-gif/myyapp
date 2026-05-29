package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BoldColorScheme = lightColorScheme(
    primary = PrimaryNeon,
    secondary = SecondaryNeon,
    tertiary = TertiaryAccent,
    background = LuxuryBlackBg,
    surface = OnyxSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextWhiteRegular,
    onSurface = TextWhiteRegular,
    surfaceVariant = DeepGreyCard,
    onSurfaceVariant = TextMutedGrey,
    outline = BorderDarkLight
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Force the cozy light peach Theme configuration
    MaterialTheme(
        colorScheme = BoldColorScheme,
        typography = Typography,
        content = content
    )
}
