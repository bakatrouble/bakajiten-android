package com.bakajiten.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.bakajiten.app.ui.components.tapPopupOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// Tooltip-at-tap positioning: above the finger when there is room,
// below otherwise, centered and clamped to the window.
@RunWith(JUnit4::class)
class TapPositionProviderTest {

  private val window = IntSize(400, 800)
  private val popup = IntSize(100, 40)
  private val anchor = IntRect(0, 700, 400, 750)

  @Test fun opensAboveTapWhenRoom() {
    assertEquals(
      IntOffset(150, 252),
      tapPopupOffset(Offset(200f, 300f), anchor, window, popup, 8),
    )
  }

  @Test fun opensBelowTapWhenNoRoomAbove() {
    assertEquals(
      IntOffset(150, 38),
      tapPopupOffset(Offset(200f, 30f), anchor, window, popup, 8),
    )
  }

  @Test fun clampsToRightEdge() {
    assertEquals(
      IntOffset(300, 252),
      tapPopupOffset(Offset(390f, 300f), anchor, window, popup, 8),
    )
  }

  @Test fun clampsToLeftEdge() {
    assertEquals(
      IntOffset(0, 252),
      tapPopupOffset(Offset(10f, 300f), anchor, window, popup, 8),
    )
  }

  @Test fun clampsToBottomEdge() {
    assertEquals(
      IntOffset(150, 20),
      tapPopupOffset(Offset(200f, 30f), anchor, IntSize(400, 60), popup, 8),
    )
  }

  @Test fun unspecifiedTapFallsBackToAnchor() {
    assertEquals(
      IntOffset(140, 652),
      tapPopupOffset(Offset.Unspecified, anchor, window, IntSize(120, 40), 8),
    )
  }
}
