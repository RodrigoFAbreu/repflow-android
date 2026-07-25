package com.repflow.app.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * Minimal Material 3 theme wrapper. Real design tokens (colors, typography,
 * shapes) are an open product decision and will be filled in once the visual
 * identity is defined; this only provides a valid Material 3 theme context so
 * Compose components render correctly.
 */
@Composable
fun RepFlowTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
