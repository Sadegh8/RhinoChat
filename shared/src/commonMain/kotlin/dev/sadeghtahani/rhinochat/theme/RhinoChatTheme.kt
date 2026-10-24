package dev.sadeghtahani.rhinochat.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF65518A), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF), onPrimaryContainer = Color(0xFF27163F),
    secondary = Color(0xFF625B70), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DFF2), onSecondaryContainer = Color(0xFF211A2C),
    tertiary = Color(0xFF80525D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E1), onTertiaryContainer = Color(0xFF32101A),
    background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF65518A),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F1FA),
    surfaceContainer = Color(0xFFF2ECF4), surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF7A7480), outlineVariant = Color(0xFFCBC4D0),
    inverseSurface = Color(0xFF322F35), inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = Color(0xFFD0B9FA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0B9FA), onPrimary = Color(0xFF362650),
    primaryContainer = Color(0xFF4D3B6A), onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFCCC2DA), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4358), onSecondaryContainer = Color(0xFFE8DFF2),
    tertiary = Color(0xFFF3B7C6), onTertiary = Color(0xFF4B2530),
    tertiaryContainer = Color(0xFF653B46), onTertiaryContainer = Color(0xFFFFD9E1),
    background = Color(0xFF151218), onBackground = Color(0xFFE8E0EB),
    surface = Color(0xFF151218), onSurface = Color(0xFFE8E0EB),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCBC4D0),
    surfaceTint = Color(0xFFD0B9FA),
    surfaceContainerLowest = Color(0xFF100D12), surfaceContainerLow = Color(0xFF1D1A20),
    surfaceContainer = Color(0xFF211E24), surfaceContainerHigh = Color(0xFF2C282F),
    surfaceContainerHighest = Color(0xFF37333A),
    outline = Color(0xFF958D9A), outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE8E0EB), inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF65518A),
)

private val RhinoTypography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val RhinoShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun RhinoChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RhinoTypography,
        shapes = RhinoShapes,
        content = content,
    )
}
