package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PaperworkPrimaryDark,
    onPrimary = PaperworkOnPrimaryDark,
    primaryContainer = PaperworkPrimaryContainerDark,
    onPrimaryContainer = PaperworkOnPrimaryContainerDark,
    secondary = PaperworkSecondaryDark,
    onSecondary = PaperworkOnSecondaryDark,
    secondaryContainer = PaperworkSecondaryContainerDark,
    onSecondaryContainer = PaperworkOnSecondaryContainerDark,
    tertiary = PaperworkTertiaryDark,
    onTertiary = PaperworkOnTertiaryDark,
    tertiaryContainer = PaperworkTertiaryContainerDark,
    onTertiaryContainer = PaperworkOnTertiaryContainerDark,
    background = PaperworkBackgroundDark,
    onBackground = PaperworkOnBackgroundDark,
    surface = PaperworkSurfaceDark,
    onSurface = PaperworkOnSurfaceDark,
    surfaceVariant = PaperworkSurfaceVariantDark,
    onSurfaceVariant = PaperworkOnSurfaceVariantDark,
    surfaceContainerLowest = PaperworkSurfaceContainerLowestDark,
    surfaceContainerLow = PaperworkSurfaceContainerLowDark,
    surfaceContainer = PaperworkSurfaceContainerDark,
    surfaceContainerHigh = PaperworkSurfaceContainerHighDark,
    surfaceContainerHighest = PaperworkSurfaceContainerHighestDark,
    outline = PaperworkOutlineDark,
    outlineVariant = PaperworkOutlineVariantDark,
    error = PaperworkErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = PaperworkPrimaryLight,
    onPrimary = PaperworkOnPrimaryLight,
    primaryContainer = PaperworkPrimaryContainerLight,
    onPrimaryContainer = PaperworkOnPrimaryContainerLight,
    secondary = PaperworkSecondaryLight,
    onSecondary = PaperworkOnSecondaryLight,
    secondaryContainer = PaperworkSecondaryContainerLight,
    onSecondaryContainer = PaperworkOnSecondaryContainerLight,
    tertiary = PaperworkTertiaryLight,
    onTertiary = PaperworkOnTertiaryLight,
    tertiaryContainer = PaperworkTertiaryContainerLight,
    onTertiaryContainer = PaperworkOnTertiaryContainerLight,
    background = PaperworkBackgroundLight,
    onBackground = PaperworkOnBackgroundLight,
    surface = PaperworkSurfaceLight,
    onSurface = PaperworkOnSurfaceLight,
    surfaceVariant = PaperworkSurfaceVariantLight,
    onSurfaceVariant = PaperworkOnSurfaceVariantLight,
    surfaceContainerLowest = PaperworkSurfaceContainerLowestLight,
    surfaceContainerLow = PaperworkSurfaceContainerLowLight,
    surfaceContainer = PaperworkSurfaceContainerLight,
    surfaceContainerHigh = PaperworkSurfaceContainerHighLight,
    surfaceContainerHighest = PaperworkSurfaceContainerHighestLight,
    outline = PaperworkOutlineLight,
    outlineVariant = PaperworkOutlineVariantLight,
    error = PaperworkErrorLight
)

val PaperworkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PaperworkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PaperworkShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PaperworkTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
