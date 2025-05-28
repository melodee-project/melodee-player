package com.melodee.autoplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

// Primary Brand Colors
object MelodeeColors {
    // Primary Purple Palette
    val Purple10 = Color(0xFF1A0A2E)
    val Purple20 = Color(0xFF2B1452)
    val Purple30 = Color(0xFF3D1D75)
    val Purple40 = Color(0xFF4F2798)
    val Purple50 = Color(0xFF6131BB)
    val Purple60 = Color(0xFF7C4DFF)
    val Purple70 = Color(0xFF9C7AFF)
    val Purple80 = Color(0xFFBCA7FF)
    val Purple90 = Color(0xFFDDD4FF)
    val Purple95 = Color(0xFFEEE9FF)
    val Purple99 = Color(0xFFFFFBFF)
    
    // Secondary Blue Palette
    val Blue10 = Color(0xFF0A1A2E)
    val Blue20 = Color(0xFF142B52)
    val Blue30 = Color(0xFF1D3D75)
    val Blue40 = Color(0xFF274F98)
    val Blue50 = Color(0xFF3161BB)
    val Blue60 = Color(0xFF4D7CFF)
    val Blue70 = Color(0xFF7A9CFF)
    val Blue80 = Color(0xFFA7BCFF)
    val Blue90 = Color(0xFFD4DDFF)
    val Blue95 = Color(0xFFE9EEFF)
    val Blue99 = Color(0xFFFBFCFF)
    
    // Accent Orange Palette
    val Orange10 = Color(0xFF2E1A0A)
    val Orange20 = Color(0xFF522B14)
    val Orange30 = Color(0xFF753D1D)
    val Orange40 = Color(0xFF984F27)
    val Orange50 = Color(0xFFBB6131)
    val Orange60 = Color(0xFFFF7C4D)
    val Orange70 = Color(0xFFFF9C7A)
    val Orange80 = Color(0xFFFFBCA7)
    val Orange90 = Color(0xFFFFDDD4)
    val Orange95 = Color(0xFFFFEEE9)
    val Orange99 = Color(0xFFFFFBF9)
    
    // Neutral Palette
    val Neutral10 = Color(0xFF1A1A1A)
    val Neutral20 = Color(0xFF2E2E2E)
    val Neutral30 = Color(0xFF424242)
    val Neutral40 = Color(0xFF565656)
    val Neutral50 = Color(0xFF6A6A6A)
    val Neutral60 = Color(0xFF7E7E7E)
    val Neutral70 = Color(0xFF929292)
    val Neutral80 = Color(0xFFA6A6A6)
    val Neutral90 = Color(0xFFBABABA)
    val Neutral95 = Color(0xFFCECECE)
    val Neutral99 = Color(0xFFFAFAFA)
    
    // Error Palette
    val Error10 = Color(0xFF2E0A0A)
    val Error20 = Color(0xFF521414)
    val Error30 = Color(0xFF751D1D)
    val Error40 = Color(0xFF982727)
    val Error50 = Color(0xFFBB3131)
    val Error60 = Color(0xFFFF4D4D)
    val Error70 = Color(0xFFFF7A7A)
    val Error80 = Color(0xFFFFA7A7)
    val Error90 = Color(0xFFFFD4D4)
    val Error95 = Color(0xFFFFE9E9)
    val Error99 = Color(0xFFFFFBFB)
}

// Alternative Color Palettes
object AlternativePalettes {
    // Music-themed Green Palette
    object MusicGreen {
        val Primary10 = Color(0xFF0A2E1A)
        val Primary20 = Color(0xFF14522B)
        val Primary30 = Color(0xFF1D753D)
        val Primary40 = Color(0xFF27984F)
        val Primary50 = Color(0xFF31BB61)
        val Primary60 = Color(0xFF4DFF7C)
        val Primary70 = Color(0xFF7AFF9C)
        val Primary80 = Color(0xFFA7FFBC)
        val Primary90 = Color(0xFFD4FFDD)
        val Primary95 = Color(0xFFE9FFEE)
        val Primary99 = Color(0xFFFBFFFB)
    }
    

    
    // Dark Mode Optimized Palette
    object DarkOptimized {
        val Primary10 = Color(0xFF000000)
        val Primary20 = Color(0xFF1A1A1A)
        val Primary30 = Color(0xFF2E2E2E)
        val Primary40 = Color(0xFF424242)
        val Primary50 = Color(0xFF565656)
        val Primary60 = Color(0xFF6A6A6A)
        val Primary70 = Color(0xFF7E7E7E)
        val Primary80 = Color(0xFF929292)
        val Primary90 = Color(0xFFA6A6A6)
        val Primary95 = Color(0xFFBABABA)
        val Primary99 = Color(0xFFFFFFFF)
    }
}

// Semantic Colors
object SemanticColors {
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Info = Color(0xFF2196F3)
    val Error = Color(0xFFF44336)
    

}

// Gradient Colors
object GradientColors {
    val PrimaryGradient = listOf(
        MelodeeColors.Purple40,
        MelodeeColors.Purple60,
        MelodeeColors.Blue60
    )
    
    val SecondaryGradient = listOf(
        MelodeeColors.Orange40,
        MelodeeColors.Orange60,
        MelodeeColors.Purple60
    )
    

} 