package com.bakajiten.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.SearchBarPosition
import com.bakajiten.app.state.Backends
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.RecognizerPanel
import com.bakajiten.app.ui.SearchScreen
import com.bakajiten.app.ui.components.KanaFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The panel never reacts to the keyboard (the search field is
// read-only while it is open): the canvas window is the persisted
// height, the square grid fits the min edge centered, and the hook
// reports the square side. Grip drags persist on release.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecognizerPanelTest {

  @get:Rule val compose = createComposeRule()

  private var committed: Float? = null
  private var fullH = 0f

  private fun show(canvasHeightDp: Float? = null) {
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = emptyList(),
          onStrokes = {},
          candidates = emptyList(),
          kanaCandidates = true,
          onKana = {},
          onPick = {},
          canvasHeightDp = canvasHeightDp,
          onCanvasHeightDp = { committed = it },
          toolbarOnTop = false,
          onSquareSidePx = { fullH = it },
        )
      }
    }
    compose.waitForIdle()
  }

  private fun canvasBounds() =
    compose.onNodeWithTag("recognizerCanvas").fetchSemanticsNode().boundsInRoot

  private fun viewportBounds() =
    compose.onNodeWithTag("canvasViewport").fetchSemanticsNode().boundsInRoot

  private fun hintTop() =
    compose.onNodeWithTag("canvasHint").fetchSemanticsNode().boundsInRoot.top

  @Test fun squareSpansWidthAtRest() {
    committed = null
    show()
    val full = canvasBounds()
    assertTrue("panel never measured: $full", full.height > 0)
    assertEquals("rest square must span the width", full.width, fullH, 1f)
  }

  @Test fun hintSitsBelowTopGridLine() {
    committed = null
    show()
    val full = canvasBounds()
    val top = hintTop() - full.top
    assertEquals("hint not below the top grid line", 0.1f * fullH + 8f, top, 1.5f)
  }

  @Test fun shortWindowCentersSquare() {
    // Window 200dp on a wider screen: the square fits the height
    // (side 200), centered horizontally with empty bands left and
    // right. (A window taller than the width would overflow the
    // headless screen's 80% panel cap; the overflow test covers
    // that path.)
    committed = null
    show(canvasHeightDp = 200f)
    val viewport = viewportBounds()
    assertEquals(200f, viewport.height, 1f)
    assertEquals("square did not fit the min edge", minOf(viewport.width, viewport.height), fullH, 1f)
    val offX = (viewport.width - fullH) / 2f
    assertEquals(
      "hint misplaced in centered square",
      0.1f * fullH + 8f,
      hintTop() - viewport.top,
      1.5f,
    )
    assertTrue("square not centered horizontally: offX=$offX", offX > 0f)
  }

  @Test fun touchMapsToCenteredSquare() {
    // The essential fit proof: a horizontal drag at the square
    // mid-height maps to y=0.5 throughout — offset subtracted,
    // scale exact, letterbox bands clamped away. Window 200dp
    // here: square side 200 at x-offset 60.
    committed = null
    var got: List<List<Offset>> = emptyList()
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = emptyList(),
          onStrokes = { got = it },
          candidates = emptyList(),
          kanaCandidates = true,
          onKana = {},
          onPick = {},
          canvasHeightDp = 200f,
          onCanvasHeightDp = {},
          toolbarOnTop = false,
          onSquareSidePx = { fullH = it },
        )
      }
    }
    compose.waitForIdle()
    val viewport = viewportBounds()
    assertEquals(200f, viewport.height, 1f)
    assertEquals(200f, fullH, 1f)
    val offX = (viewport.width - fullH) / 2f
    val midY = (viewport.height - fullH) / 2f + fullH / 2f
    compose.onNodeWithTag("recognizerCanvas").performTouchInput {
      swipe(Offset(offX + 1f, midY), Offset(offX + fullH - 1f, midY), 300)
    }
    compose.waitForIdle()
    assertTrue("no stroke reported: $got", got.isNotEmpty())
    val stroke = got.last()
    assertTrue("stroke too short: $stroke", stroke.size >= 2)
    for (p in stroke) {
      assertEquals("touch offset wrong at $p", 0.5f, p.y, 0.04f)
    }
    assertTrue("drag did not span the square: $stroke", stroke.last().x - stroke.first().x > 0.8f)
  }

  @Test fun panelNeighborsDoNotOverlap() {
    committed = null
    show()
    val viewport = viewportBounds()
    val strip = compose.onNodeWithTag("candidateStrip").fetchSemanticsNode().boundsInRoot
    val toggle = compose.onNodeWithTag("gridToggle").fetchSemanticsNode().boundsInRoot
    // Options row directly below the canvas...
    assertTrue(
      "toolbar overlaps canvas bottom: $toggle vs $viewport",
      toggle.top >= viewport.bottom - 1f,
    )
    // ...and the candidate strip too when it has content (empty
    // candidates measure zero, which trivially cannot overlap).
    if (strip.height > 0) {
      assertTrue(
        "candidates overlap canvas bottom: $strip vs $viewport",
        strip.top >= viewport.bottom - 1f,
      )
    }
  }

  // Full assembly: the real SearchScreen (Scaffold + PanelSlot +
  // weights) around the real panel.
  private fun showAssembly(
    dock: SearchBarPosition,
    canvasHeightDp: Float? = null,
    status: String = "",
  ) {
    val fake = FakeDictionaryBackend()
    val be = Backends(
      fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    )
    compose.setContent {
      BakajitenTheme {
        SearchScreen(
          query = "",
          field = KanaFieldState(""),
          onQuery = {},
          settings = AppSettings(searchBarPosition = dock),
          onSettings = {},
          backends = be,
          status = status,
          hits = emptyList(),
          onOpen = {},
          onScope = {},
          onOpenSettings = {},
          recognizerOpen = true,
          onRecognizer = {},
          recognizerPanel = {
            RecognizerPanel(
              strokes = emptyList(),
              onStrokes = {},
              candidates = emptyList(),
              kanaCandidates = true,
              onKana = {},
              onPick = {},
              canvasHeightDp = canvasHeightDp,
              onCanvasHeightDp = { committed = it },
              toolbarOnTop = dock == SearchBarPosition.BOTTOM,
              onSquareSidePx = { fullH = it },
            )
          },
        )
      }
    }
    compose.waitForIdle()
  }

  private fun assertFittedSquare(tag: String) {
    val viewport = viewportBounds()
    val canvas = canvasBounds()
    assertTrue("square side never measured ($tag): $fullH", fullH > 0)
    assertEquals("square did not fit the min edge ($tag)", minOf(viewport.width, viewport.height), fullH, 1f)
    assertEquals("canvas does not fill the window ($tag)", viewport.height, canvas.height, 0.5f)
    assertEquals("canvas not top-aligned in window ($tag): $canvas in $viewport", viewport.top, canvas.top, 0.5f)
  }

  @Test fun topDockPanelCoversMatchesCount() {
    // The overlay spans the matches count too: with the panel
    // open, the status line sits under the panel window.
    committed = null
    showAssembly(SearchBarPosition.TOP, status = "2 matches")
    val status = compose.onNodeWithTag("searchStatus").fetchSemanticsNode().boundsInRoot
    val viewport = viewportBounds()
    assertTrue(
      "panel does not cover the matches count: $status vs $viewport",
      status.top < viewport.bottom - 1f,
    )
  }

  @Test fun topDockAssemblyFitsSquareCentered() {
    committed = null
    showAssembly(SearchBarPosition.TOP)
    assertFittedSquare("top dock")
  }

  @Test fun bottomDockAssemblyFitsSquareCentered() {
    committed = null
    showAssembly(SearchBarPosition.BOTTOM)
    assertFittedSquare("bottom dock")
  }

  @Test fun persistedWindowCentersSquare() {
    // Persisted window shorter than the width: the square fits the
    // min edge (the window height), centered horizontally.
    committed = null
    showAssembly(SearchBarPosition.BOTTOM, canvasHeightDp = 250f)
    val viewport = viewportBounds()
    assertEquals("window did not take the persisted height: $viewport", 250f, viewport.height, 2f)
    assertEquals("square did not fit the min edge", minOf(viewport.width, viewport.height), fullH, 1f)
    val offX = (viewport.width - fullH) / 2f
    assertTrue("square not centered horizontally: offX=$offX", offX > 0f)
  }

  @Test fun overflowAssemblyDegradesGracefully() {
    // Oversize persisted window (content taller than the panel's
    // 80% cap): the window may end up shorter, but the square still
    // fits the min edge — canvas filling the window, side intact.
    // Never a stretched grid.
    committed = null
    showAssembly(SearchBarPosition.BOTTOM, canvasHeightDp = 400f)
    val viewport = viewportBounds()
    val canvas = canvasBounds()
    assertTrue("window taller than persisted: $viewport", viewport.height <= 400f + 2f)
    assertEquals("canvas does not fill the window", viewport.height, canvas.height, 0.5f)
    assertEquals(
      "canvas not top-aligned in window: $canvas in $viewport",
      viewport.top,
      canvas.top,
      0.5f,
    )
    assertEquals("square did not fit the min edge under overflow", minOf(viewport.width, viewport.height), fullH, 1f)
  }
}
