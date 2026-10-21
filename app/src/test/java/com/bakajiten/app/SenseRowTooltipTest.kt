package com.bakajiten.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.bakajiten.app.domain.NumberedSense
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.TagKind
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.SenseRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tapping a tag badge reveals its description in the row's tooltip,
// placed at the tap point.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SenseRowTooltipTest {

  @get:Rule val compose = createComposeRule()

  @Test fun tapOnBadgeShowsTip() {
    compose.setContent {
      BakajitenTheme {
        SenseRow(
          NumberedSense(
            SenseDisplay(
              badges = listOf("vt"),
              tips = listOf("transitive verb"),
              kinds = listOf(TagKind.POS),
              glosses = listOf("to eat"),
              xref = emptyList(),
              ant = emptyList(),
              info = emptyList(),
              sources = emptyList(),
              restrictedKanji = emptyList(),
              restrictedReading = emptyList(),
              uk = false,
            ),
            1,
          ),
          highlight = "eat",
        )
      }
    }
    // Badges are spans in one Text (shared baseline), so tap
    // through the text layout: the row's tooltip shows the tip.
    val senses = compose.onNodeWithText("to eat", substring = true, useUnmergedTree = true)
    val layouts = mutableListOf<TextLayoutResult>()
    senses.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
      ?.action?.invoke(layouts)
    val layout = layouts.firstOrNull() ?: error("no text layout")
    val at = layout.layoutInput.text.indexOf("vt").takeIf { it >= 0 } ?: error("badge missing")
    val box = layout.getBoundingBox(at + 1)
    senses.performTouchInput {
      down(0, box.center)
      up(0)
    }
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithText("transitive verb").assertExists()
        true
      } catch (e: AssertionError) {
        false
      }
    }
  }
}
