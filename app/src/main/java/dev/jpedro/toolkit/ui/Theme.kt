package dev.jpedro.toolkit.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import dev.jpedro.toolkit.data.AppSettings
import dev.jpedro.toolkit.data.ThemeName

data class Palette(
    val primary: Color,
    val hover: Color,
    val button: Color,
    val link: Color,
    val glow: Color
)

object Base {
    val background = Color(0xFF1A1B26)
    val text = Color(0xFFECEFF4)
    val muted = Color(0xFFB4BCCB)
    val border = Color(0x26FFFFFF)
    val field = Color(0x14FFFFFF)
    val danger = Color(0xB3B3402F)
    val error = Color(0xFFFFB4A9)
}

fun paletteFor(theme: ThemeName): Palette = when (theme) {
    ThemeName.BLUE -> Palette(
        Color(0x335D94C2), Color(0x33569CD6), Color(0x804A8AC7), Color(0xFF8EC5FF), Color(0x4D5D94C2)
    )
    ThemeName.NAVY -> Palette(
        Color(92, 112, 214, 41), Color(123, 143, 237, 41), Color(0x804052B5), Color(0xFFB6C0FF), Color(92, 112, 214, 87)
    )
    ThemeName.NORD -> Palette(
        Color(59, 66, 82, 89), Color(67, 76, 94, 110), Color(0x80434C5E), Color(0xFFA8D5DF), Color(136, 192, 208, 66)
    )
    ThemeName.MATERIAL_EXPRESSIVE -> Palette(
        Color(0x669C27B0), Color(0x88E91E63), Color(0x999C27B0), Color(0xFFFF80AB), Color(0x669C27B0)
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(ThemeName.MATERIAL_EXPRESSIVE) }
val LocalRoundness = staticCompositionLocalOf { 12.dp }
val LocalCardOpacity = staticCompositionLocalOf { 0.78f }
val LocalThemeName = staticCompositionLocalOf { ThemeName.MATERIAL_EXPRESSIVE }

@Composable
fun shape(scale: Float = 1f): RoundedCornerShape = RoundedCornerShape(LocalRoundness.current * scale)

@Composable
fun ToolkitTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val staticPalette = paletteFor(settings.theme)
    val radius = when (settings.theme) {
        ThemeName.MATERIAL_EXPRESSIVE -> 28.dp
        else -> settings.roundness.dp
    }
    val weight = if (settings.boldText) FontWeight.Bold else FontWeight.Normal
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontWeight = weight),
        bodyMedium = base.bodyMedium.copy(fontWeight = weight),
        bodySmall = base.bodySmall.copy(fontWeight = weight),
        labelLarge = base.labelLarge.copy(fontWeight = if (settings.boldText) FontWeight.Bold else FontWeight.Medium)
    )
    val colors = when (settings.theme) {
        ThemeName.MATERIAL_EXPRESSIVE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme(
                    primary = Color(0xFFFF80AB),
                    onPrimary = Color(0xFF510023),
                    primaryContainer = Color(0xFF7B1FA2),
                    onPrimaryContainer = Color(0xFFF3E5F5),
                    secondary = Color(0xFFEA80FC),
                    onSecondary = Color(0xFF4A0072),
                    secondaryContainer = Color(0xFF8E24AA),
                    onSecondaryContainer = Color(0xFFFCE4EC),
                    tertiary = Color(0xFF80D8FF),
                    onTertiary = Color(0xFF00363A),
                    background = Color(0xFF0D0814),
                    onBackground = Color(0xFFF5EBF8),
                    surface = Color(0xFF15101E),
                    onSurface = Color(0xFFF5EBF8),
                    surfaceVariant = Color(0xFF4A3B5C),
                    onSurfaceVariant = Color(0xFFD8B4FE),
                    outline = Color(0xFFBA68C8),
                    error = Color(0xFFFFB4A9)
                )
            }
        }
        else -> darkColorScheme(
            primary = staticPalette.link,
            onPrimary = Color.Black,
            secondary = staticPalette.link,
            background = Color(0xFF0F1218),
            onBackground = Base.text,
            surface = Color(0xFF141820),
            onSurface = Base.text,
            surfaceVariant = Color(0xFF222736),
            onSurfaceVariant = Base.muted,
            outline = Color(0x40FFFFFF)
        )
    }
    val palette = if (settings.theme == ThemeName.MATERIAL_EXPRESSIVE) {
        Palette(
            primary = colors.primaryContainer.copy(alpha = 0.45f),
            hover = colors.primary.copy(alpha = 0.5f),
            button = colors.primaryContainer,
            link = colors.primary,
            glow = colors.primary.copy(alpha = 0.35f)
        )
    } else {
        staticPalette
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(radius / 2),
        small = RoundedCornerShape(radius),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape(radius),
        extraLarge = RoundedCornerShape(radius)
    )
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalRoundness provides radius,
        LocalCardOpacity provides settings.cardOpacity,
        LocalContentColor provides Base.text,
        LocalThemeName provides settings.theme
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
    }
}

@Composable
fun AppBackground(settings: AppSettings, content: @Composable BoxScope.() -> Unit) {
    val isMaterialTheme = settings.theme == ThemeName.MATERIAL_EXPRESSIVE
    Box(Modifier.fillMaxSize().background(if (isMaterialTheme) MaterialTheme.colorScheme.background else Color(0xFF0F1218))) {
        if (!isMaterialTheme) {
            AsyncImage(
                model = dev.jpedro.toolkit.R.drawable.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(16.dp)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        content()
    }
}
