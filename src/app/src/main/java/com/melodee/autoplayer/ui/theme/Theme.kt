package com.melodee.autoplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
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
    PRIMARY_COLORS,
    RETRO_80S,
    WINAMP,
    BUBBLEGUM,
    JUST_GREY,
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
        palette == ThemePalette.PRIMARY_COLORS -> {
            if (darkTheme) PrimaryColorsDarkColorScheme else PrimaryColorsLightColorScheme
        }
        palette == ThemePalette.RETRO_80S -> {
            if (darkTheme) Retro80sDarkColorScheme else Retro80sLightColorScheme
        }
        palette == ThemePalette.WINAMP -> {
            if (darkTheme) WinAmpDarkColorScheme else WinAmpLightColorScheme
        }
        palette == ThemePalette.BUBBLEGUM -> {
            if (darkTheme) BubblegumDarkColorScheme else BubblegumLightColorScheme
        }
        palette == ThemePalette.JUST_GREY -> {
            if (darkTheme) JustGreyDarkColorScheme else JustGreyLightColorScheme
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

// Primary Colors Theme (RGB-inspired)
private val PrimaryColorsLightColorScheme = lightColorScheme(
    primary = AlternativePalettes.PrimaryColors.Primary50,
    onPrimary = AlternativePalettes.PrimaryColors.Primary99,
    primaryContainer = AlternativePalettes.PrimaryColors.Primary90,
    onPrimaryContainer = AlternativePalettes.PrimaryColors.Primary10,
    secondary = AlternativePalettes.PrimaryColors.Secondary50,
    onSecondary = AlternativePalettes.PrimaryColors.Secondary99,
    secondaryContainer = AlternativePalettes.PrimaryColors.Secondary90,
    onSecondaryContainer = AlternativePalettes.PrimaryColors.Secondary10,
    tertiary = AlternativePalettes.PrimaryColors.Tertiary50,
    onTertiary = AlternativePalettes.PrimaryColors.Tertiary99,
    tertiaryContainer = AlternativePalettes.PrimaryColors.Tertiary90,
    onTertiaryContainer = AlternativePalettes.PrimaryColors.Tertiary10
)

private val PrimaryColorsDarkColorScheme = darkColorScheme(
    primary = AlternativePalettes.PrimaryColors.Primary80,
    onPrimary = AlternativePalettes.PrimaryColors.Primary20,
    primaryContainer = AlternativePalettes.PrimaryColors.Primary30,
    onPrimaryContainer = AlternativePalettes.PrimaryColors.Primary90,
    secondary = AlternativePalettes.PrimaryColors.Secondary80,
    onSecondary = AlternativePalettes.PrimaryColors.Secondary20,
    secondaryContainer = AlternativePalettes.PrimaryColors.Secondary30,
    onSecondaryContainer = AlternativePalettes.PrimaryColors.Secondary90,
    tertiary = AlternativePalettes.PrimaryColors.Tertiary80,
    onTertiary = AlternativePalettes.PrimaryColors.Tertiary20,
    tertiaryContainer = AlternativePalettes.PrimaryColors.Tertiary30,
    onTertiaryContainer = AlternativePalettes.PrimaryColors.Tertiary90,
)

// 80s Retro Theme
private val Retro80sLightColorScheme = lightColorScheme(
    primary = AlternativePalettes.Retro80s.Primary60,
    onPrimary = MelodeeColors.Neutral10,
    primaryContainer = AlternativePalettes.Retro80s.Primary90,
    onPrimaryContainer = AlternativePalettes.Retro80s.Primary20,
    secondary = AlternativePalettes.Retro80s.Secondary60,
    onSecondary = MelodeeColors.Neutral10,
    secondaryContainer = AlternativePalettes.Retro80s.Secondary90,
    onSecondaryContainer = AlternativePalettes.Retro80s.Secondary20,
    tertiary = AlternativePalettes.Retro80s.Tertiary60,
    onTertiary = MelodeeColors.Neutral10,
    tertiaryContainer = AlternativePalettes.Retro80s.Tertiary90,
    onTertiaryContainer = AlternativePalettes.Retro80s.Tertiary20,
)

private val Retro80sDarkColorScheme = darkColorScheme(
    primary = AlternativePalettes.Retro80s.Primary80,
    onPrimary = AlternativePalettes.Retro80s.Primary20,
    primaryContainer = AlternativePalettes.Retro80s.Primary30,
    onPrimaryContainer = AlternativePalettes.Retro80s.Primary90,
    secondary = AlternativePalettes.Retro80s.Secondary80,
    onSecondary = AlternativePalettes.Retro80s.Secondary20,
    secondaryContainer = AlternativePalettes.Retro80s.Secondary30,
    onSecondaryContainer = AlternativePalettes.Retro80s.Secondary90,
    tertiary = AlternativePalettes.Retro80s.Tertiary80,
    onTertiary = AlternativePalettes.Retro80s.Tertiary20,
    tertiaryContainer = AlternativePalettes.Retro80s.Tertiary30,
    onTertiaryContainer = AlternativePalettes.Retro80s.Tertiary90,
    background = AlternativePalettes.Retro80s.GridBlack,
    surface = AlternativePalettes.Retro80s.GridBlack,
    onSurface = MelodeeColors.Neutral95,
    surfaceVariant = AlternativePalettes.Retro80s.GridGray,
    onSurfaceVariant = MelodeeColors.Neutral80,
)

// WinAmp Classic Theme
private val WinAmpLightColorScheme = lightColorScheme(
    primary = AlternativePalettes.WinAmpClassic.Gold,
    onPrimary = MelodeeColors.Neutral10,
    primaryContainer = Color(0xFFFFE680),
    onPrimaryContainer = AlternativePalettes.WinAmpClassic.GoldDark,
    secondary = AlternativePalettes.WinAmpClassic.Blue,
    onSecondary = MelodeeColors.Neutral99,
    secondaryContainer = Color(0xFFB3E0FF),
    onSecondaryContainer = AlternativePalettes.WinAmpClassic.BlueDark,
    tertiary = AlternativePalettes.WinAmpClassic.EQGreen,
    onTertiary = MelodeeColors.Neutral10,
    tertiaryContainer = Color(0xFFD6FFD6),
    onTertiaryContainer = Color(0xFF005500)
)

private val WinAmpDarkColorScheme = darkColorScheme(
    primary = AlternativePalettes.WinAmpClassic.Gold,
    onPrimary = AlternativePalettes.WinAmpClassic.Charcoal,
    primaryContainer = AlternativePalettes.WinAmpClassic.GoldDark,
    onPrimaryContainer = MelodeeColors.Neutral95,
    secondary = AlternativePalettes.WinAmpClassic.Blue,
    onSecondary = MelodeeColors.Neutral99,
    secondaryContainer = AlternativePalettes.WinAmpClassic.BlueDark,
    onSecondaryContainer = MelodeeColors.Neutral95,
    tertiary = AlternativePalettes.WinAmpClassic.EQGreen,
    onTertiary = AlternativePalettes.WinAmpClassic.Charcoal,
    tertiaryContainer = Color(0xFF2A7F2A),
    onTertiaryContainer = MelodeeColors.Neutral95,
    background = AlternativePalettes.WinAmpClassic.Charcoal,
    surface = AlternativePalettes.WinAmpClassic.Slate,
    onSurface = AlternativePalettes.WinAmpClassic.TextOnDark,
)

// Bubblegum Theme
private val BubblegumLightColorScheme = lightColorScheme(
    primary = AlternativePalettes.Bubblegum.Pink60,
    onPrimary = MelodeeColors.Neutral10,
    primaryContainer = AlternativePalettes.Bubblegum.Pink90,
    onPrimaryContainer = AlternativePalettes.Bubblegum.Pink20,
    secondary = AlternativePalettes.Bubblegum.CandyBlue50,
    onSecondary = MelodeeColors.Neutral10,
    secondaryContainer = AlternativePalettes.Bubblegum.CandyBlue90,
    onSecondaryContainer = AlternativePalettes.Bubblegum.Pink20,
    tertiary = AlternativePalettes.Bubblegum.Lavender50,
    onTertiary = MelodeeColors.Neutral10,
    tertiaryContainer = AlternativePalettes.Bubblegum.Lavender80,
    onTertiaryContainer = AlternativePalettes.Bubblegum.Pink20,
)

private val BubblegumDarkColorScheme = darkColorScheme(
    primary = AlternativePalettes.Bubblegum.Pink80,
    onPrimary = AlternativePalettes.Bubblegum.Pink20,
    primaryContainer = AlternativePalettes.Bubblegum.Pink30,
    onPrimaryContainer = AlternativePalettes.Bubblegum.Pink90,
    secondary = AlternativePalettes.Bubblegum.CandyBlue80,
    onSecondary = AlternativePalettes.Bubblegum.Pink20,
    secondaryContainer = AlternativePalettes.Bubblegum.CandyBlue50,
    onSecondaryContainer = MelodeeColors.Neutral10,
    tertiary = AlternativePalettes.Bubblegum.Lavender80,
    onTertiary = AlternativePalettes.Bubblegum.Pink20,
    tertiaryContainer = AlternativePalettes.Bubblegum.Lavender50,
    onTertiaryContainer = MelodeeColors.Neutral10,
    background = MelodeeColors.Neutral10,
    surface = MelodeeColors.Neutral10,
)

// Just Grey Theme
private val JustGreyLightColorScheme = lightColorScheme(
    primary = AlternativePalettes.JustGrey.Grey50,
    onPrimary = MelodeeColors.Neutral99,
    primaryContainer = AlternativePalettes.JustGrey.Grey90,
    onPrimaryContainer = AlternativePalettes.JustGrey.Grey10,
    secondary = AlternativePalettes.JustGrey.Grey60,
    onSecondary = MelodeeColors.Neutral99,
    secondaryContainer = AlternativePalettes.JustGrey.Grey95,
    onSecondaryContainer = AlternativePalettes.JustGrey.Grey20,
    tertiary = AlternativePalettes.JustGrey.Grey40,
    onTertiary = MelodeeColors.Neutral99,
    tertiaryContainer = AlternativePalettes.JustGrey.Grey80,
    onTertiaryContainer = AlternativePalettes.JustGrey.Grey10,
    background = AlternativePalettes.JustGrey.Grey99,
    surface = AlternativePalettes.JustGrey.Grey99,
    onSurface = AlternativePalettes.JustGrey.Grey10,
)

private val JustGreyDarkColorScheme = darkColorScheme(
    primary = AlternativePalettes.JustGrey.Grey80,
    onPrimary = AlternativePalettes.JustGrey.Grey20,
    primaryContainer = AlternativePalettes.JustGrey.Grey30,
    onPrimaryContainer = AlternativePalettes.JustGrey.Grey90,
    secondary = AlternativePalettes.JustGrey.Grey70,
    onSecondary = AlternativePalettes.JustGrey.Grey20,
    secondaryContainer = AlternativePalettes.JustGrey.Grey40,
    onSecondaryContainer = AlternativePalettes.JustGrey.Grey90,
    tertiary = AlternativePalettes.JustGrey.Grey60,
    onTertiary = AlternativePalettes.JustGrey.Grey20,
    tertiaryContainer = AlternativePalettes.JustGrey.Grey50,
    onTertiaryContainer = AlternativePalettes.JustGrey.Grey95,
    background = AlternativePalettes.JustGrey.Grey10,
    surface = AlternativePalettes.JustGrey.Grey20,
    onSurface = AlternativePalettes.JustGrey.Grey95,
)

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
