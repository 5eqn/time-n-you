package com.seqn.timewatcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Primary, onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer, onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary, onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer, onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary, onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer, onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundL, onBackground = OnBackgroundL,
    surface = SurfaceL, onSurface = OnSurfaceL,
    surfaceVariant = SurfaceVariant, onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    error = ErrorC, onError = OnError,
    errorContainer = ErrorContainer, onErrorContainer = OnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryD, onPrimary = OnPrimaryD,
    primaryContainer = PrimaryContainerD, onPrimaryContainer = OnPrimaryContainerD,
    secondary = SecondaryD, onSecondary = OnSecondaryD,
    secondaryContainer = SecondaryContainerD, onSecondaryContainer = OnSecondaryContainerD,
    tertiary = TertiaryD, onTertiary = OnTertiaryD,
    tertiaryContainer = TertiaryContainerD, onTertiaryContainer = OnTertiaryContainerD,
    background = BackgroundD, onBackground = OnBackgroundD,
    surface = SurfaceD, onSurface = OnSurfaceD,
    surfaceVariant = SurfaceVariantD, onSurfaceVariant = OnSurfaceVariantD,
    outline = OutlineD,
    error = ErrorCD, onError = OnErrorD,
    errorContainer = ErrorContainerD, onErrorContainer = OnErrorContainerD,
)

@Composable
fun TimeWatcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
