package com.compx551.rhythmrun.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KineticObsidianColorScheme = darkColorScheme(
    primary = Color(0xFF4BE277),
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF22C55E),
    onPrimaryContainer = Color(0xFF004B1E),
    secondary = Color(0xFF4CD7F6),
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF03B5D3),
    onSecondaryContainer = Color(0xFF00424E),
    tertiary = Color(0xFFFFB3B7),
    onTertiary = Color(0xFF67001B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF0B1326),
    onBackground = Color(0xFFDAE2FD),
    surface = Color(0xFF0B1326),
    onSurface = Color(0xFFDAE2FD),
    surfaceVariant = Color(0xFF2D3449),
    onSurfaceVariant = Color(0xFFBCCBB9),
    outline = Color(0xFF869585),
    outlineVariant = Color(0xFF3D4A3D),
    surfaceContainerLowest = Color(0xFF060E20),
    surfaceContainerLow = Color(0xFF131B2E),
    surfaceContainer = Color(0xFF171F33),
    surfaceContainerHigh = Color(0xFF222A3D),
    surfaceContainerHighest = Color(0xFF2D3449),
)

private val RhythmRunShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun RhythmRunTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KineticObsidianColorScheme,
        shapes = RhythmRunShapes,
        content = content,
    )
}
