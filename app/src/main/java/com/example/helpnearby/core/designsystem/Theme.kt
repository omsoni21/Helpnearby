package com.example.helpnearby.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.helpnearby.core.common.AppStringsProvider
import com.example.helpnearby.core.common.LocalizedCopy
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.AppThemeMode

private val HelpNearbyLightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoContainer,
    onPrimaryContainer = BrandIndigoOnContainer,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = BrandTealContainer,
    onSecondaryContainer = BrandTealOnContainer,
    tertiary = BrandAmber,
    onTertiary = Color.White,
    tertiaryContainer = BrandAmberContainer,
    onTertiaryContainer = BrandAmberOnContainer,
    error = BrandUrgent,
    onError = Color.White,
    errorContainer = BrandUrgentContainer,
    onErrorContainer = BrandUrgentOnContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

private val HelpNearbyDarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF881337),
    onErrorContainer = Color(0xFFFFE4E6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

val HelpNearbyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

val LocalAppStrings = staticCompositionLocalOf {
    AppStringsProvider.forLanguage(AppLanguage.EN)
}

@Composable
fun HelpNearbyTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.EN,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colors = if (darkTheme) HelpNearbyDarkColors else HelpNearbyLightColors
    val strings: LocalizedCopy = AppStringsProvider.forLanguage(language)

    CompositionLocalProvider(LocalAppStrings provides strings) {
        MaterialTheme(
            colorScheme = colors,
            typography = HelpNearbyTypography,
            shapes = HelpNearbyShapes,
            content = content
        )
    }
}
