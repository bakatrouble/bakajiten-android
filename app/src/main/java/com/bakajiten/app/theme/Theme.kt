package com.bakajiten.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.bakajiten.app.data.Theme as AppTheme

// Generic neutral grounds with orange accents (web palette parity);
// green tertiary stays semantic (pos tags, up-to-date states).

private val Light = lightColorScheme(
  primary = Color(0xFFEA580C),
  onPrimary = Color.White,
  background = Color(0xFFE0E0E0),
  onBackground = Color(0xFF1A1A1A),
  surface = Color.White,
  onSurface = Color(0xFF1A1A1A),
  surfaceVariant = Color(0xFFE2E2E2),
  onSurfaceVariant = Color(0xFF444444),
  tertiary = Color(0xFF2E7D32),
)

private val Dark = darkColorScheme(
  primary = Color(0xFFF97316),
  onPrimary = Color.Black,
  background = Color(0xFF2C2C2C),
  onBackground = Color(0xFFF5F5F5),
  surface = Color(0xFF323232),
  onSurface = Color(0xFFF5F5F5),
  surfaceVariant = Color(0xFF4B4B4B),
  onSurfaceVariant = Color(0xFFCACACA),
  tertiary = Color(0xFF81C784),
)

// Whole-app Material 3 Expressive: the expressive theme drives
// morphing buttons/chips, expressive motion, and expressive
// defaults for every M3 component below it.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BakajitenTheme(
  theme: AppTheme = AppTheme.AUTO,
  densityDpi: Int? = null,
  content: @Composable () -> Unit,
) {
  val dark = when (theme) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.AUTO -> isSystemInDarkTheme()
  }
  val system = LocalDensity.current
  // Explicit dpi overrides density (font scale stays the system's).
  val scaled = if (densityDpi == null) {
    system
  } else {
    Density(densityDpi / 160f, system.fontScale)
  }
  // Edge-to-edge bars are transparent; icon contrast must follow the
  // app theme (not the system one — the user can force either mode).
  val window = (LocalContext.current as? android.app.Activity)?.window
  SideEffect {
    window?.let {
      WindowCompat.getInsetsController(it, it.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
      }
    }
  }
  CompositionLocalProvider(LocalDensity provides scaled) {
    MaterialExpressiveTheme(
      colorScheme = if (dark) Dark else Light,
      motionScheme = MotionScheme.expressive(),
      content = content,
    )
  }
}
