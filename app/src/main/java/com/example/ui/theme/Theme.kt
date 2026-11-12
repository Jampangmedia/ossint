package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SignalSemanticColors(
    val emerald: Color = SignalEmerald,
    val emeraldContainer: Color = SignalEmeraldContainer,
    val cyan: Color = TelemetryCyan,
    val cyanContainer: Color = TelemetryCyanContainer,
    val amber: Color = AmberSignal,
    val amberContainer: Color = AmberContainer,
    val coral: Color = CoralAlert,
    val coralContainer: Color = CoralContainer,
    val threadsViolet: Color = VioletThreads,
    val youtubeRed: Color = YouTubeRed,
    val facebookBlue: Color = FacebookBlue,
    val instagramPink: Color = InstagramPink,
    val textMuted: Color = TextMuted,
    val borderSubtle: Color = SlateOutline
)

val LocalSignalColors = staticCompositionLocalOf { SignalSemanticColors() }

private val ForensicDarkColorScheme = darkColorScheme(
    primary = SignalEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = SignalEmeraldContainer,
    onPrimaryContainer = SignalEmerald,
    secondary = TelemetryCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = TelemetryCyanContainer,
    onSecondaryContainer = TelemetryCyan,
    tertiary = AmberSignal,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberSignal,
    error = CoralAlert,
    onError = ObsidianBg,
    errorContainer = CoralContainer,
    onErrorContainer = CoralAlert,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateElevated,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutline
)

private val SignalShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun SignalTraceTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalSignalColors provides SignalSemanticColors()) {
        MaterialTheme(
            colorScheme = ForensicDarkColorScheme,
            typography = SignalTypography,
            shapes = SignalShapes,
            content = content
        )
    }
}

object SignalTheme {
    val colors: SignalSemanticColors
        @Composable
        get() = LocalSignalColors.current
}
