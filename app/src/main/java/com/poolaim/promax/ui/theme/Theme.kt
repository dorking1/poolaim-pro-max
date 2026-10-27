package com.poolaim.promax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary = Color(0xFF4CAF50)
private val GreenDark = Color(0xFF2E7D32)

private val DarkColors = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFC107),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
)

/**
 * PoolAim Pro Max always uses a dark pool-table theme for consistency.
 */
@Composable
fun PoolAimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
