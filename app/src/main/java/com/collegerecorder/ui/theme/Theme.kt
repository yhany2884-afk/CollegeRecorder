package com.collegerecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PrimaryLight = Color(0xFF1B4F72)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val SecondaryLight = Color(0xFF2874A6)
private val TertiaryLight = Color(0xFF148F77)
private val BackgroundLight = Color(0xFFF8F9FA)
private val SurfaceLight = Color(0xFFFFFFFF)
private val ErrorLight = Color(0xFFB00020)

private val PrimaryDark = Color(0xFF5DADE2)
private val OnPrimaryDark = Color(0xFF0A1929)
private val SecondaryDark = Color(0xFF85C1E9)
private val TertiaryDark = Color(0xFF48C9B0)
private val BackgroundDark = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val ErrorDark = Color(0xFFCF6679)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    secondary = SecondaryLight, tertiary = TertiaryLight,
    background = BackgroundLight, surface = SurfaceLight, error = ErrorLight,
    onBackground = Color(0xFF1A1A1A), onSurface = Color(0xFF1A1A1A)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    secondary = SecondaryDark, tertiary = TertiaryDark,
    background = BackgroundDark, surface = SurfaceDark, error = ErrorDark,
    onBackground = Color(0xFFE6EDF3), onSurface = Color(0xFFE6EDF3)
)

@Composable
fun CollegeRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
