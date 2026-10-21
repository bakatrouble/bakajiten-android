package com.bakajiten.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.StrokePoint
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.CanvasViewport
import com.bakajiten.app.ui.RecognizerPanel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanvasGripTest {
  @get:Rule val compose = createComposeRule()

  @Test fun gripDragPersistsHeight() {
    var committed: Float? = null
    val backend = FakeRecognizerBackend()
    val pen = listOf(listOf(StrokePoint(10f, 10f), StrokePoint(200f, 200f)))
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = emptyList(),
          onStrokes = {},
          candidates = backend.recognize(pen, 5, true),
          kanaCandidates = true,
          onKana = {},
          onPick = {},
          canvasHeightDp = null,
          onCanvasHeightDp = { committed = it },
          toolbarOnTop = false,
        )
      }
    }
    compose.onNodeWithTag("canvasGrip").performScrollTo()
    compose.onNodeWithTag("canvasGrip").performTouchInput { swipeUp() }
    assertTrue("expected height persist after drag, got $committed", committed != null && committed!! > 120f)
  }

  @Test fun longPressClearsDrawing() {
    var strokes: List<List<Offset>> = listOf(listOf(Offset(0.1f, 0.1f), Offset(0.2f, 0.2f)))
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = strokes,
          onStrokes = { strokes = it },
          candidates = emptyList(),
          kanaCandidates = true,
          onKana = {},
          onPick = {},
          canvasHeightDp = null,
          onCanvasHeightDp = {},
          toolbarOnTop = false,
        )
      }
    }
    compose.onNodeWithTag("recognizerCanvas").performTouchInput { longClick() }
    assertTrue("expected drawing cleared, got $strokes", strokes.isEmpty())
  }

  @Test fun lateSettingsWriteDoesNotSnapLiveHeight() {
    // A slow settings write landing mid-session must not reset an
    // already-resized canvas (the second drag broke until remount).
    val prop = mutableStateOf<Float?>(null)
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = emptyList(),
          onStrokes = {},
          candidates = emptyList(),
          kanaCandidates = true,
          onKana = {},
          onPick = {},
          canvasHeightDp = prop.value,
          onCanvasHeightDp = {},
          toolbarOnTop = false,
        )
      }
    }
    fun canvasHeight() = compose.onNodeWithTag("recognizerCanvas").fetchSemanticsNode().size.height
    compose.onNodeWithTag("canvasGrip").performTouchInput { swipeDown() }
    val grown = canvasHeight()
    // A stale write arrives late (e.g. the first drag's persist).
    prop.value = 150f
    compose.waitForIdle()
    assertTrue(
      "live height snapped from $grown to ${canvasHeight()}",
      canvasHeight() == grown,
    )
  }

  @Test fun tallWindowCentersSquareContent() {
    // Window taller than wide (400dp persisted): the square keeps
    // the width side, centered vertically — content never squishes,
    // and there is no overflow box to drift off alignment.
    var fullH = 0f
    compose.setContent {
      BakajitenTheme {
        CanvasViewport(
          heightLive = 400f,
          strokes = emptyList(),
          onStroke = {},
          grid = true,
          onClear = {},
          onSquareSidePx = { fullH = it },
        )
      }
    }
    compose.waitForIdle()
    // Structural pin: no full-size inner box — an overflow child
    // inside the panel's scrolling column gets coerced and centered
    // on device when content overflows the 80% cap.
    org.junit.Assert.assertTrue(
      "overflow inner box regressed",
      compose.onAllNodesWithTag("canvasInner").fetchSemanticsNodes().isEmpty(),
    )
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val window = compose.onNodeWithTag("canvasViewport").fetchSemanticsNode().boundsInRoot
    val canvas = compose.onNodeWithTag("recognizerCanvas").fetchSemanticsNode().boundsInRoot
    org.junit.Assert.assertEquals("window did not take the persisted height: $window", 400f * density, window.height, 1f)
    org.junit.Assert.assertEquals("square did not keep the width side: $fullH", window.width, fullH, 1f)
    org.junit.Assert.assertEquals(
      "canvas not top-aligned in window: $canvas in $window",
      window.top,
      canvas.top,
      0.5f,
    )
  }

  @Test fun windowDerivesSquareFromWidth() {
    // No persisted height: the window is square and spans the
    // width, so the square is the window.
    var fullH = 0f
    compose.setContent {
      BakajitenTheme {
        CanvasViewport(
          heightLive = null,
          strokes = emptyList(),
          onStroke = {},
          grid = true,
          onClear = {},
          onSquareSidePx = { fullH = it },
        )
      }
    }
    compose.waitForIdle()
    val window = compose.onNodeWithTag("canvasViewport").fetchSemanticsNode().boundsInRoot
    org.junit.Assert.assertEquals("window is not square: $window", window.width, window.height, 1f)
    org.junit.Assert.assertEquals("square is not the window: $fullH", window.width, fullH, 1f)
  }

}
