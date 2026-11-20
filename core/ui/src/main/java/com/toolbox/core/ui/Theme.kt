package com.toolbox.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ToolboxPalette(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val pine: Color,
    val pineSoft: Color,
    val hairline: Color,
    val clay: Color,
    val claySoft: Color,
    val onPine: Color
)

val LightPalette = ToolboxPalette(
    paper = Color(0xFFF7F5EF),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF17170F),
    ink2 = Color(0xFF5E5C52),
    ink3 = Color(0xFF99968A),
    pine = Color(0xFF1E4B3D),
    pineSoft = Color(0xFFE7EDE9),
    hairline = Color(0xFFE7E3D8),
    clay = Color(0xFFA9552F),
    claySoft = Color(0xFFF7E9E1),
    onPine = Color(0xFFFBFAF5)
)

val DarkPalette = ToolboxPalette(
    paper = Color(0xFF0E0F0C),
    surface = Color(0xFF181914),
    ink = Color(0xFFF2F1E9),
    ink2 = Color(0xFFA7A499),
    ink3 = Color(0xFF8A887C),
    pine = Color(0xFF7FBFA4),
    pineSoft = Color(0xFF1B2A24),
    hairline = Color(0xFF2A2B25),
    clay = Color(0xFFD08462),
    claySoft = Color(0xFF2C1D17),
    onPine = Color(0xFF0E0F0C)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object ToolboxType {
    val Title = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
        fontWeight = FontWeight.SemiBold
    )

    val CardTitle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp,
        fontWeight = FontWeight.SemiBold
    )

    val Body = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp
    )

    val Caption = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )

    val Eyebrow = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 2.6.sp,
        fontWeight = FontWeight.SemiBold
    )

    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum"
    )

    val MonoLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.4).sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum"
    )
}

@Composable
fun ToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.pine,
            onPrimary = palette.onPine,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            outline = palette.hairline
        )
    } else {
        lightColorScheme(
            primary = palette.pine,
            onPrimary = palette.onPine,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            outline = palette.hairline
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}