package com.bakajiten.app

import androidx.compose.ui.graphics.Color
import com.bakajiten.app.ui.components.canvasInks
import com.bakajiten.app.ui.components.darken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// Canvas paper + inks derive from the scheme, so they stay
// readable in both themes without branching call sites.
@RunWith(JUnit4::class)
class CanvasInkTest {

  @Test fun darkenScalesChannels() {
    assertEquals(Color(0.93f, 0.93f, 0.93f, 1f), darken(Color.White))
    // 8-bit packing in the Color(float) ctor adds noise: compare
    // with a delta.
    val d = darken(Color(0.5f, 0.25f, 0.125f, 1f))
    assertEquals(0.5f * 0.93f, d.red, 0.005f)
    assertEquals(0.25f * 0.93f, d.green, 0.005f)
    assertEquals(0.125f * 0.93f, d.blue, 0.005f)
    assertEquals(1f, d.alpha, 0f)
  }

  @Test fun lightSchemeUsesDarkenedSurfacePrimaryAndForeground() {
    val inks = canvasInks(
      surface = Color.White,
      primary = Color(0xFFEA580C),
      onSurface = Color(0xFF1A1A1A),
    )
    assertEquals(Color(0.93f, 0.93f, 0.93f, 1f), inks.paper)
    assertEquals(Color(0xFFEA580C), inks.active)
    assertEquals(Color(0xFF1A1A1A), inks.final)
  }

  @Test fun darkSchemeKeepsAmberOnDarkenedSurface() {
    val inks = canvasInks(
      surface = Color(0xFF323232),
      primary = Color(0xFFF97316),
      onSurface = Color(0xFFF5F5F5),
    )
    assertEquals(darken(Color(0xFF323232)), inks.paper)
    assertEquals(Color(0xFFFFB300), inks.active)
    assertEquals(Color(0xFFF5F5F5), inks.final)
  }
}
