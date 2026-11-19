package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = LiveCrimson,
    onPrimary = OnLiveCrimson,
    primaryContainer = LiveCrimsonContainer,
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = SignalCyan,
    onSecondary = Color(0xFF002538),
    secondaryContainer = SignalCyanContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = SignalEmerald,
    onTertiary = Color(0xFF002B1E),
    tertiaryContainer = SignalEmeraldContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = StudioVoid,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = StudioVoid,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHigh,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = Color(0xFFFF5C77),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF4C0A1E),
    onErrorContainer = Color(0xFFFFD9E0)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LiveCrimson,
    onPrimary = OnLiveCrimson,
    primaryContainer = Color(0xFFFFE4E9),
    onPrimaryContainer = Color(0xFF881337),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = LightStudioBg,
    onBackground = LightStudioText,
    surface = LightStudioSurface,
    onSurface = LightStudioText,
    surfaceVariant = LightStudioSurfaceVariant,
    onSurfaceVariant = LightStudioTextSecondary,
    outline = LightStudioOutline
)

val StreamCastShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StreamCastTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StreamCastTypography,
        shapes = StreamCastShapes,
        content = content
    )
}
