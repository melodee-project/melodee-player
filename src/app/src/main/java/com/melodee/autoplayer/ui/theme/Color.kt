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
    
    // Primary Colors (RGB-inspired)
    object PrimaryColors {
        // Blue as primary
        val Primary10 = Color(0xFF001B3F)
        val Primary20 = Color(0xFF00306B)
        val Primary30 = Color(0xFF1649A1)
        val Primary40 = Color(0xFF1F57C6)
        val Primary50 = Color(0xFF2962FF) // Royal Blue
        val Primary60 = Color(0xFF5C85FF)
        val Primary80 = Color(0xFFB6C8FF)
        val Primary90 = Color(0xFFDDE7FF)
        val Primary99 = Color(0xFFFDFCFF)

        // Red as secondary
        val Secondary10 = Color(0xFF410002)
        val Secondary20 = Color(0xFF680005)
        val Secondary30 = Color(0xFF93000A)
        val Secondary40 = Color(0xFFB3261E)
        val Secondary50 = Color(0xFFD32F2F) // Crimson
        val Secondary60 = Color(0xFFFF6B6B)
        val Secondary80 = Color(0xFFFFB4A9)
        val Secondary90 = Color(0xFFFFDAD4)
        val Secondary99 = Color(0xFFFFFBF9)

        // Green as tertiary
        val Tertiary10 = Color(0xFF00210F)
        val Tertiary20 = Color(0xFF003922)
        val Tertiary30 = Color(0xFF005233)
        val Tertiary40 = Color(0xFF1B6A44)
        val Tertiary50 = Color(0xFF2E7D32) // Emerald
        val Tertiary60 = Color(0xFF59A95C)
        val Tertiary80 = Color(0xFFC0EBC0)
        val Tertiary90 = Color(0xFFCDECCB)
        val Tertiary99 = Color(0xFFFBFFFB)
    }

    // 80s Retro / WinAmp-esque neon palette
    object Retro80s {
        // Neon Magenta primary
        val Primary10 = Color(0xFF2A0016)
        val Primary20 = Color(0xFF55002E)
        val Primary30 = Color(0xFF7F0C55)
        val Primary40 = Color(0xFFB31E76)
        val Primary50 = Color(0xFFD62984)
        val Primary60 = Color(0xFFFF2D95) // Neon Magenta
        val Primary80 = Color(0xFFFFA3D1)
        val Primary90 = Color(0xFFFFD0EA)
        val Primary99 = Color(0xFFFFF8FC)

        // Electric Cyan secondary
        val Secondary10 = Color(0xFF002226)
        val Secondary20 = Color(0xFF003A40)
        val Secondary30 = Color(0xFF00535B)
        val Secondary40 = Color(0xFF007E89)
        val Secondary50 = Color(0xFF00A6B3)
        val Secondary60 = Color(0xFF00F0FF) // Electric Cyan
        val Secondary80 = Color(0xFF8CF6FF)
        val Secondary90 = Color(0xFFC8FBFF)
        val Secondary99 = Color(0xFFF5FEFF)

        // Neon Yellow tertiary
        val Tertiary10 = Color(0xFF2A2900)
        val Tertiary20 = Color(0xFF444100)
        val Tertiary30 = Color(0xFF6A6200)
        val Tertiary40 = Color(0xFF8C8300)
        val Tertiary50 = Color(0xFFB0A600)
        val Tertiary60 = Color(0xFFF9F871) // Neon Yellow
        val Tertiary80 = Color(0xFFFFF6A8)
        val Tertiary90 = Color(0xFFFFFFCF)
        val Tertiary99 = Color(0xFFFFFFF8)

        // Retro grids
        val GridBlack = Color(0xFF0A0A0F)
        val GridGray = Color(0xFF1E1E26)
    }

    // WinAmp classic vibes
    object WinAmpClassic {
        val Gold = Color(0xFFFFCC00)
        val GoldDark = Color(0xFFB38F00)
        val Blue = Color(0xFF0099FF)
        val BlueDark = Color(0xFF0066CC)
        val EQGreen = Color(0xFF66FF66)
        val Charcoal = Color(0xFF1C1C1C)
        val Slate = Color(0xFF232323)
        val TextOnDark = Color(0xFFE0E0E0)
    }

    // Bubblegum: an unapologetically pink, candy-like palette
    object Bubblegum {
        // Bubblegum pinks
        val Pink10 = Color(0xFF3F0016)
        val Pink20 = Color(0xFF6A1031)
        val Pink30 = Color(0xFF9A2154)
        val Pink40 = Color(0xFFC93A79)
        val Pink50 = Color(0xFFF2559A)
        val Pink60 = Color(0xFFFF69B4) // Hot pink
        val Pink70 = Color(0xFFFF8EC5)
        val Pink80 = Color(0xFFFFB6D6)
        val Pink90 = Color(0xFFFFD6E8)
        val Pink99 = Color(0xFFFFF7FB)

        // Cotton-candy accent (blue-ish)
        val CandyBlue50 = Color(0xFF69D2FF)
        val CandyBlue80 = Color(0xFFBDEBFF)
        val CandyBlue90 = Color(0xFFE3F6FF)

        // Lavender tertiary
        val Lavender50 = Color(0xFFB37DFF)
        val Lavender80 = Color(0xFFD9C3FF)
        val Lavender90 = Color(0xFFF0E7FF)
    }

    // Just Grey: minimalist grayscale palette
    object JustGrey {
        val Grey05 = Color(0xFF0D0D0D)
        val Grey10 = Color(0xFF161616)
        val Grey20 = Color(0xFF262626)
        val Grey30 = Color(0xFF333333)
        val Grey40 = Color(0xFF4D4D4D)
        val Grey50 = Color(0xFF666666)
        val Grey60 = Color(0xFF808080)
        val Grey70 = Color(0xFF999999)
        val Grey80 = Color(0xFFB3B3B3)
        val Grey90 = Color(0xFFCCCCCC)
        val Grey95 = Color(0xFFE6E6E6)
        val Grey99 = Color(0xFFFAFAFA)
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
