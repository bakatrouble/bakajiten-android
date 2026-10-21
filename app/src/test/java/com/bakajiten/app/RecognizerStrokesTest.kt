package com.bakajiten.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.RecognizerPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Stroke accumulation: each finished gesture appends to the panel's
// strokes instead of replacing them (the canvas gesture block runs
// once and must see the latest callbacks, not the first).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecognizerStrokesTest {

  @get:Rule val compose = createComposeRule()

  @Test fun consecutiveStrokesAccumulate() {
    // Compose state (like the app holds): each commit recomposes the
    // panel with the new list, so a plain local var would hide the bug.
    val state = mutableStateOf(emptyList<List<Offset>>())
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = state.value,
          onStrokes = { state.value = it },
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
    val canvas = compose.onNodeWithTag("recognizerCanvas")
    // Strokes stay clear of the bottom overlay (options + candidates
    // + grip float over the canvas there).
    fun upperSwipe(down: Boolean) {
      val size = canvas.fetchSemanticsNode().size
      val w = size.width.toFloat()
      val h = size.height.toFloat()
      val from = if (down) Offset(w / 2, h * 0.05f) else Offset(w / 2, h * 0.4f)
      val to = if (down) Offset(w / 2, h * 0.4f) else Offset(w / 2, h * 0.05f)
      canvas.performTouchInput { swipe(from, to) }
    }
    upperSwipe(false)
    assertEquals(1, state.value.size)
    upperSwipe(true)
    assertEquals("second stroke must append, got ${state.value}", 2, state.value.size)
  }

  @Test fun undoRedoRestoresStroke() {
    val state = mutableStateOf(
      listOf(listOf(Offset(0.1f, 0.1f)), listOf(Offset(0.2f, 0.2f))),
    )
    compose.setContent {
      BakajitenTheme {
        RecognizerPanel(
          strokes = state.value,
          onStrokes = { state.value = it },
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
    compose.onNodeWithTag("undoButton").performClick()
    assertEquals(1, state.value.size)
    compose.onNodeWithTag("redoButton").performClick()
    assertEquals(2, state.value.size)
    // A new stroke drops the redo stack.
    compose.onNodeWithTag("undoButton").performClick()
    compose.onNodeWithTag("recognizerCanvas").let { canvas ->
      val size = canvas.fetchSemanticsNode().size
      val w = size.width.toFloat()
      val h = size.height.toFloat()
      canvas.performTouchInput { swipe(Offset(w / 2, h * 0.05f), Offset(w / 2, h * 0.4f)) }
    }
    compose.onNodeWithTag("redoButton").performClick()
    assertEquals("redo stack must clear on new stroke", 2, state.value.size)
  }
}
