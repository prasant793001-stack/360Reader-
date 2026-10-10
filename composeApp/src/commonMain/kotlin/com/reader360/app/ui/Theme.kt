package com.reader360.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFFA5B4FC)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFFF97316),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainer = Color(0xFFF1F2F8),
    surfaceContainerHigh = Color(0xFFE9EBF3),
    outline = Color(0xFFCBD0DC),
    outlineVariant = Color(0xFFE3E6EE),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFFDBA74),
    onSecondary = Color(0xFF431407),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF16181F),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1F222B),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainer = Color(0xFF1A1D25),
    surfaceContainerHigh = Color(0xFF22252E),
    outline = Color(0xFF3A3F4B),
    outlineVariant = Color(0xFF2A2E38),
    error = Color(0xFFF87171),
)

@Immutable
data class ViewerColors(val canvas: Color, val pageShadow: Color, val muted: Color)

val LocalViewerColors = staticCompositionLocalOf {
    ViewerColors(canvas = Color(0xFFE6E8EF), pageShadow = Color(0x33000000), muted = Color(0xFF6B7280))
}

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val BadgeTextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val viewerColors = if (darkTheme) {
        ViewerColors(canvas = Color(0xFF0B0C10), pageShadow = Color(0x66000000), muted = Color(0xFF9CA3AF))
    } else {
        ViewerColors(canvas = Color(0xFFE6E8EF), pageShadow = Color(0x33000000), muted = Color(0xFF6B7280))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalViewerColors provides viewerColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
