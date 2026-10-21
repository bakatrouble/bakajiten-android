package com.bakajiten.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bakajiten.app.data.KanjiPoint
import com.bakajiten.app.data.KanjiStroke
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.StrokePlayer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Stroke player transport: stepping advances the counter,
// restart returns to zero. Playback progress itself is
// time-driven and covered by the animation traces.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrokePlayerTest {

  @get:Rule val compose = createComposeRule()

  private val strokes = listOf(
    KanjiStroke(listOf(KanjiPoint(20f, 40f), KanjiPoint(235f, 40f))),
    KanjiStroke(listOf(KanjiPoint(60f, 10f), KanjiPoint(60f, 120f))),
  )

  private fun show() {
    compose.setContent {
      BakajitenTheme {
        StrokePlayer(strokes = strokes)
      }
    }
  }

  @Test fun stepForwardAndBackMoveCounter() {
    show()
    compose.onNodeWithText("0/2").assertExists()
    compose.onNodeWithTag("strokeStepForward").performClick()
    compose.onNodeWithText("1/2").assertExists()
    compose.onNodeWithTag("strokeStepBack").performClick()
    compose.onNodeWithText("0/2").assertExists()
  }

  @Test fun restartReturnsToZero() {
    show()
    compose.onNodeWithTag("strokeStepForward").performClick()
    compose.onNodeWithTag("strokeStepForward").performClick()
    compose.onNodeWithText("2/2").assertExists()
    compose.onNodeWithTag("strokeRestart").performClick()
    compose.onNodeWithText("0/2").assertExists()
  }

  @Test fun stopReturnsToZero() {
    show()
    compose.onNodeWithTag("strokeStepForward").performClick()
    compose.onNodeWithTag("strokeStop").performClick()
    compose.onNodeWithText("0/2").assertExists()
  }
}
