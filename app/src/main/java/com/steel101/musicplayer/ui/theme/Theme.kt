package com.steel101.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

fun getDynamicColorScheme(seedColor: Color, isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = seedColor,
            onPrimary = Color.Black,
            primaryContainer = seedColor.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = seedColor.copy(alpha = 0.8f),
            onSecondary = Color.Black,
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            surfaceVariant = seedColor.copy(alpha = 0.15f),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.LightGray
        )
    } else {
        lightColorScheme(
            primary = seedColor,
            onPrimary = Color.White,
            primaryContainer = seedColor.copy(alpha = 0.15f),
            onPrimaryContainer = Color.Black,
            secondary = seedColor.copy(alpha = 0.7f),
            onSecondary = Color.White,
            background = Color.White,
            surface = Color(0xFFF5F5F5),
            surfaceVariant = seedColor.copy(alpha = 0.05f),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color.DarkGray
        )
    }
}

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        seedColor != null -> {
            getDynamicColorScheme(seedColor, darkTheme)
        }
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
        content = content
    )
}
