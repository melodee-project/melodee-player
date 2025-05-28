package com.melodee.autoplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


// Default Light Color Scheme (Purple Theme)
private val LightColorScheme = lightColorScheme(
    primary = MelodeeColors.Purple60,
    onPrimary = MelodeeColors.Purple99,
    primaryContainer = MelodeeColors.Purple90,
    onPrimaryContainer = MelodeeColors.Purple10,
    secondary = MelodeeColors.Blue60,
    onSecondary = MelodeeColors.Blue99,
    secondaryContainer = MelodeeColors.Blue90,
    onSecondaryContainer = MelodeeColors.Blue10,
    tertiary = MelodeeColors.Orange60,
    onTertiary = MelodeeColors.Orange99,
    tertiaryContainer = MelodeeColors.Orange90,
    onTertiaryContainer = MelodeeColors.Orange10,
    error = MelodeeColors.Error60,
    onError = MelodeeColors.Error99,
    errorContainer = MelodeeColors.Error90,
    onErrorContainer = MelodeeColors.Error10,
    background = MelodeeColors.Neutral99,
    onBackground = MelodeeColors.Neutral10,
    surface = MelodeeColors.Neutral99,
    onSurface = MelodeeColors.Neutral10,
    surfaceVariant = MelodeeColors.Neutral90,
    onSurfaceVariant = MelodeeColors.Neutral30,
    outline = MelodeeColors.Neutral50,
    outlineVariant = MelodeeColors.Neutral80,
    scrim = MelodeeColors.Neutral10,
    inverseSurface = MelodeeColors.Neutral20,
    inverseOnSurface = MelodeeColors.Neutral95,
    inversePrimary = MelodeeColors.Purple80,
    surfaceDim = MelodeeColors.Neutral95,
    surfaceBright = MelodeeColors.Neutral99,
    surfaceContainerLowest = MelodeeColors.Neutral99,
    surfaceContainerLow = MelodeeColors.Neutral95,
    surfaceContainer = MelodeeColors.Neutral90,
    surfaceContainerHigh = MelodeeColors.Neutral80,
    surfaceContainerHighest = MelodeeColors.Neutral70
)

// Default Dark Color Scheme (Purple Theme)
private val DarkColorScheme = darkColorScheme(
    primary = MelodeeColors.Purple80,
    onPrimary = MelodeeColors.Purple20,
    primaryContainer = MelodeeColors.Purple30,
    onPrimaryContainer = MelodeeColors.Purple90,
    secondary = MelodeeColors.Blue80,
    onSecondary = MelodeeColors.Blue20,
    secondaryContainer = MelodeeColors.Blue30,
    onSecondaryContainer = MelodeeColors.Blue90,
    tertiary = MelodeeColors.Orange80,
    onTertiary = MelodeeColors.Orange20,
    tertiaryContainer = MelodeeColors.Orange30,
    onTertiaryContainer = MelodeeColors.Orange90,
    error = MelodeeColors.Error80,
    onError = MelodeeColors.Error20,
    errorContainer = MelodeeColors.Error30,
    onErrorContainer = MelodeeColors.Error90,
    background = MelodeeColors.Neutral10,
    onBackground = MelodeeColors.Neutral90,
    surface = MelodeeColors.Neutral10,
    onSurface = MelodeeColors.Neutral90,
    surfaceVariant = MelodeeColors.Neutral30,
    onSurfaceVariant = MelodeeColors.Neutral80,
    outline = MelodeeColors.Neutral60,
    outlineVariant = MelodeeColors.Neutral30,
    scrim = MelodeeColors.Neutral10,
    inverseSurface = MelodeeColors.Neutral90,
    inverseOnSurface = MelodeeColors.Neutral20,
    inversePrimary = MelodeeColors.Purple40,
    surfaceDim = MelodeeColors.Neutral10,
    surfaceBright = MelodeeColors.Neutral30,
    surfaceContainerLowest = MelodeeColors.Neutral10,
    surfaceContainerLow = MelodeeColors.Neutral20,
    surfaceContainer = MelodeeColors.Neutral30,
    surfaceContainerHigh = MelodeeColors.Neutral40,
    surfaceContainerHighest = MelodeeColors.Neutral50
)



// Music Green Theme
private val MusicGreenLightColorScheme = lightColorScheme(
    primary = AlternativePalettes.MusicGreen.Primary60,
    onPrimary = AlternativePalettes.MusicGreen.Primary99,
    primaryContainer = AlternativePalettes.MusicGreen.Primary90,
    onPrimaryContainer = AlternativePalettes.MusicGreen.Primary10,
    secondary = MelodeeColors.Orange60,
    onSecondary = MelodeeColors.Orange99,
    secondaryContainer = MelodeeColors.Orange90,
    onSecondaryContainer = MelodeeColors.Orange10,
    tertiary = MelodeeColors.Purple60,
    onTertiary = MelodeeColors.Purple99,
    tertiaryContainer = MelodeeColors.Purple90,
    onTertiaryContainer = MelodeeColors.Purple10
)

private val MusicGreenDarkColorScheme = darkColorScheme(
    primary = AlternativePalettes.MusicGreen.Primary80,
    onPrimary = AlternativePalettes.MusicGreen.Primary20,
    primaryContainer = AlternativePalettes.MusicGreen.Primary30,
    onPrimaryContainer = AlternativePalettes.MusicGreen.Primary90,
    secondary = MelodeeColors.Orange80,
    onSecondary = MelodeeColors.Orange20,
    secondaryContainer = MelodeeColors.Orange30,
    onSecondaryContainer = MelodeeColors.Orange90,
    tertiary = MelodeeColors.Purple80,
    onTertiary = MelodeeColors.Purple20,
    tertiaryContainer = MelodeeColors.Purple30,
    onTertiaryContainer = MelodeeColors.Purple90
)

// Theme Selection Enum
enum class ThemePalette {
    DEFAULT,
    MUSIC_GREEN,
    DYNAMIC // Uses system dynamic colors on Android 12+
}

@Composable
fun MelodeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.DEFAULT,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        palette == ThemePalette.MUSIC_GREEN -> {
            if (darkTheme) MusicGreenDarkColorScheme else MusicGreenLightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MobileTypography,
        content = content
    )
}

// Extension function to get current theme palette
@Composable
fun getCurrentColorScheme(): ColorScheme = MaterialTheme.colorScheme

// Helper functions for accessing semantic colors
object ThemeColors {
    val success: androidx.compose.ui.graphics.Color
        @Composable get() = SemanticColors.Success
    
    val warning: androidx.compose.ui.graphics.Color
        @Composable get() = SemanticColors.Warning
    
    val info: androidx.compose.ui.graphics.Color
        @Composable get() = SemanticColors.Info
    

} 