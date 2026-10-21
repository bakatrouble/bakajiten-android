package com.bakajiten.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.bakajiten.app.domain.NumberedSense
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.TagKind
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.SensesLine
import com.bakajiten.app.ui.components.TipTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Results-list tags report their description on tap (no per-row
// popup: rows stay anchor-free while recycling; ResultsList shows
// the tip in its one shared list-level tooltip): the tap offset
// maps through the text layout to the badge's string annotation.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensesLineTest {

  @get:Rule val compose = createComposeRule()

  private fun row() = NumberedSense(
    SenseDisplay(
      badges = listOf("vt", "arch"),
      tips = listOf("transitive verb", "archaic"),
      kinds = listOf(TagKind.POS, TagKind.MISC),
      glosses = listOf("to eat", "to live on"),
      xref = emptyList(),
      ant = emptyList(),
      info = emptyList(),
      sources = emptyList(),
      restrictedKanji = emptyList(),
      restrictedReading = emptyList(),
      uk = false,
    ),
    1,
  )

  private fun show(onBadgeTap: (TipTap) -> Unit = {}) {
    compose.setContent {
      BakajitenTheme {
        SensesLine(
          rows = listOf(row()),
          highlight = "eat",
          modifier = Modifier.fillMaxWidth(),
          onBadgeTap = onBadgeTap,
        )
      }
    }
    compose.waitForIdle()
  }

  private fun node() = compose.onNodeWithText("to eat", substring = true).fetchSemanticsNode()

  private fun layoutOf(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    node().config[SemanticsActions.GetTextLayoutResult]?.action?.invoke(layouts)
    return layouts.firstOrNull() ?: error("no text layout")
  }

  // Badge "vt" sits at a known offset in the single line; tap its
  // middle glyph through the layout box (node-relative coords).
  private fun tapBadge() {
    val layout = layoutOf()
    val at = layout.layoutInput.text.indexOf("vt").takeIf { it >= 0 } ?: error("badge missing")
    val box = layout.getBoundingBox(at + 1)
    compose.onNodeWithText("to eat", substring = true).performTouchInput {
      down(0, box.center)
      up(0)
    }
    compose.waitForIdle()
  }

  @Test fun badgeTapReportsTip() {
    var tip: String? = "unset"
    show { tip = it.tip }
    tapBadge()
    assertEquals("transitive verb", tip)
  }

  @Test fun glossTapReportsNothing() {
    var tip: String? = null
    show { tip = it.tip }
    val size = node().size
    compose.onNodeWithText("to eat", substring = true).performTouchInput {
      down(0, Offset(size.width - 30f, (size.height / 2).toFloat()))
      up(0)
    }
    compose.waitForIdle()
    assertNull("gloss tap must not report a tip, got $tip", tip)
  }
}
