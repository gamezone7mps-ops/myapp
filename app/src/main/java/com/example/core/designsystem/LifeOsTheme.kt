package com.example.core.designsystem

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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode

private val LightColors = lightColorScheme(
  primary = Color(0xFF0F4C5C),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFCDE9EE),
  onPrimaryContainer = Color(0xFF062730),
  secondary = Color(0xFF28726E),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFCFEBE8),
  onSecondaryContainer = Color(0xFF082927),
  tertiary = Color(0xFFB56A23),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFF8E2CB),
  onTertiaryContainer = Color(0xFF3A1E04),
  background = Color(0xFFF7F6F2),
  onBackground = Color(0xFF151C1F),
  surface = Color(0xFFFFFFFF),
  onSurface = Color(0xFF151C1F),
  surfaceVariant = Color(0xFFECEAE3),
  onSurfaceVariant = Color(0xFF525D63),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF2F0EA),
  surfaceContainer = Color(0xFFECE9E1),
  surfaceContainerHigh = Color(0xFFE4E0D6),
  outline = Color(0xFF7D888E),
  outlineVariant = Color(0xFFD2CFC6),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFF68D6C1),
  onPrimary = Color(0xFF032B24),
  primaryContainer = Color(0xFF114449),
  onPrimaryContainer = Color(0xFFC6F4EA),
  secondary = Color(0xFF8CC9C2),
  onSecondary = Color(0xFF072B29),
  secondaryContainer = Color(0xFF1B3F3D),
  onSecondaryContainer = Color(0xFFCDEEE9),
  tertiary = Color(0xFFE7AD6A),
  onTertiary = Color(0xFF361D03),
  tertiaryContainer = Color(0xFF4A2F11),
  onTertiaryContainer = Color(0xFFFBE2C6),
  background = Color(0xFF0D1417),
  onBackground = Color(0xFFECF1F3),
  surface = Color(0xFF131D22),
  onSurface = Color(0xFFECF1F3),
  surfaceVariant = Color(0xFF1E2B31),
  onSurfaceVariant = Color(0xFFA8B5BC),
  surfaceContainerLowest = Color(0xFF0A1013),
  surfaceContainerLow = Color(0xFF131C20),
  surfaceContainer = Color(0xFF182429),
  surfaceContainerHigh = Color(0xFF1F2E34),
  outline = Color(0xFF6B7980),
  outlineVariant = Color(0xFF27353C),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

val VazirmatnFontFamily = FontFamily(
  Font(R.font.vazirmatn, FontWeight.Normal),
  Font(R.font.vazirmatn, FontWeight.Medium),
  Font(R.font.vazirmatn, FontWeight.SemiBold),
  Font(R.font.vazirmatn, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.Medium),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

fun buildLifeOsTypography(language: AppLanguage): Typography {
  val fontFamily = if (language == AppLanguage.PERSIAN) {
    VazirmatnFontFamily
  } else {
    PlusJakartaSansFontFamily
  }
  return Typography(
    headlineLarge = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 28.sp,
      lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 22.sp,
      lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 19.sp,
      lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 16.sp,
      lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 14.sp,
      lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 15.sp,
      lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 12.sp,
      lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
      fontFamily = fontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      lineHeight = 15.sp
    )
  )
}

val LifeOsShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LifeOsTheme(
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  language: AppLanguage = AppLanguage.PERSIAN,
  content: @Composable () -> Unit
) {
  val isDark = when (themeMode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
  }
  val colorScheme = if (isDark) DarkColors else LightColors
  MaterialTheme(
    colorScheme = colorScheme,
    typography = buildLifeOsTypography(language),
    shapes = LifeOsShapes,
    content = content
  )
}
