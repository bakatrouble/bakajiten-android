package com.bakajiten.app

import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import com.bakajiten.app.data.Theme
import com.bakajiten.app.theme.BakajitenTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Status/navigation bar icon contrast follows the app theme, not the
// system one (the user can force either mode over edge-to-edge).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeBarsTest {

  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun lightBars(): Boolean {
    val window = compose.activity.window
    return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
  }

  @Test fun darkThemeUsesLightBarIcons() {
    compose.setContent { BakajitenTheme(theme = Theme.DARK) { Text("x") } }
    assertFalse(lightBars())
  }

  @Test fun lightThemeUsesDarkBarIcons() {
    compose.setContent { BakajitenTheme(theme = Theme.LIGHT) { Text("x") } }
    assertTrue(lightBars())
  }
}
