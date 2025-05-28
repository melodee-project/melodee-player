package com.melodee.autoplayer.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.melodee.autoplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themeManager = rememberThemeManager(context)
    val themeState = rememberThemeState(themeManager)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Theme Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dark Theme Toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (themeState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Dark Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (themeState.isDarkTheme) "Dark mode enabled" else "Light mode enabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = themeState.isDarkTheme,
                            onCheckedChange = { themeManager.setDarkTheme(it) }
                        )
                    }
                }
            }
            
            // Dynamic Color Toggle (Android 12+)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Dynamic Colors",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Use system wallpaper colors (Android 12+)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = themeState.isDynamicColorEnabled,
                            onCheckedChange = { themeManager.setDynamicColorEnabled(it) }
                        )
                    }
                }
            }
            
            // Color Palette Selection
            item {
                Text(
                    text = "Color Palettes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(getPaletteOptions()) { paletteOption ->
                PaletteCard(
                    paletteOption = paletteOption,
                    isSelected = paletteOption.palette == themeState.palette,
                    onClick = { themeManager.setPalette(paletteOption.palette) }
                )
            }
        }
    }
}

data class PaletteOption(
    val palette: ThemePalette,
    val name: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color
)

private fun getPaletteOptions(): List<PaletteOption> = listOf(
    PaletteOption(
        palette = ThemePalette.DEFAULT,
        name = "Melodee Purple",
        description = "The signature Melodee brand colors with purple and blue accents",
        primaryColor = MelodeeColors.Purple60,
        secondaryColor = MelodeeColors.Blue60,
        tertiaryColor = MelodeeColors.Orange60
    ),
    PaletteOption(
        palette = ThemePalette.MUSIC_GREEN,
        name = "Music Green",
        description = "Fresh green palette inspired by music and nature",
        primaryColor = AlternativePalettes.MusicGreen.Primary60,
        secondaryColor = MelodeeColors.Orange60,
        tertiaryColor = MelodeeColors.Purple60
    ),
    PaletteOption(
        palette = ThemePalette.DYNAMIC,
        name = "Dynamic Colors",
        description = "Adapts to your system wallpaper colors (Android 12+)",
        primaryColor = Color(0xFF6750A4),
        secondaryColor = Color(0xFF625B71),
        tertiaryColor = Color(0xFF7D5260)
    )
)

@Composable
private fun PaletteCard(
    paletteOption: PaletteOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color preview circles
            Row(
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                ColorCircle(
                    color = paletteOption.primaryColor,
                    size = 32.dp
                )
                ColorCircle(
                    color = paletteOption.secondaryColor,
                    size = 32.dp
                )
                ColorCircle(
                    color = paletteOption.tertiaryColor,
                    size = 32.dp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Palette info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = paletteOption.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = paletteOption.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            )
    )
} 