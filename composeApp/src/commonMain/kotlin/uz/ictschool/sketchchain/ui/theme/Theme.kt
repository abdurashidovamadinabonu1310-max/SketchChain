package uz.ictschool.sketchchain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary = VibrantCoral,
    secondary = SoftMint,
    tertiary = PastelPurple,
    background = DeepCharcoal,
    surface = SoftDark,
    surfaceVariant = DeepCharcoal,
    onPrimary = Color.White,
    onSecondary = DeepCharcoal,
    onTertiary = DeepCharcoal,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextGray
)

@Composable
fun SketchChainTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        // Using default typography for now, shapes will be handled via modifiers
        content = content
    )
}
