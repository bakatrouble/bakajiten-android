package com.bakajiten.app

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.state.Backends
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.ResultsList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// List press detection must observe consumed downs too: rows eat
// the press for their own tap handling, and the background watcher
// still has to see it (it consumes nothing itself).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultsListPressTest {

  @get:Rule val compose = createComposeRule()

  private fun show(onBackgroundTap: () -> Unit, panelOpen: Boolean) {
    val fake = FakeDictionaryBackend()
    val backends = Backends(
      fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    )
    compose.setContent {
      BakajitenTheme {
        ResultsList(
          hits = fake.searchIndices("た", SearchMode.PREFIX, 50),
          query = "た",
          backends = backends,
          onOpen = {},
          modifier = Modifier.height(400.dp),
          onBackgroundTap = onBackgroundTap,
          panelOpen = panelOpen,
        )
      }
    }
  }

  private fun pressList() {
    val node = compose.onNodeWithTag("resultsList").fetchSemanticsNode()
    val center = node.boundsInRoot.center - node.positionInRoot
    compose.onNodeWithTag("resultsList").performTouchInput {
      down(0, center)
      up(0)
    }
  }

  @Test fun pressFiresBackgroundTapWhenPanelOpen() {
    var fired = false
    show(onBackgroundTap = { fired = true }, panelOpen = true)
    pressList()
    assertTrue("press must dismiss the panel", fired)
  }

  @Test fun pressStaysQuietWhenPanelClosed() {
    var fired = false
    show(onBackgroundTap = { fired = true }, panelOpen = false)
    pressList()
    assertFalse("no dismissal without an open panel", fired)
  }

  // Badge taps retarget the one list-level tooltip (no per-row
  // popups): tapping "v1" surfaces its description as a tooltip.
  @Test fun badgeTapShowsSharedTooltip() {
    show(onBackgroundTap = {}, panelOpen = false)
    // Unmerged tree: the row merges headline + senses into one
    // semantics node, so the senses Text is only addressable here.
    val senses = compose.onNodeWithText("to eat", substring = true, useUnmergedTree = true)
    val layouts = mutableListOf<TextLayoutResult>()
    senses.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
      ?.action?.invoke(layouts)
    val layout = layouts.firstOrNull() ?: error("no text layout")
    val at = layout.layoutInput.text.indexOf("v1").takeIf { it >= 0 } ?: error("badge missing")
    val box = layout.getBoundingBox(at + 1)
    senses.performTouchInput {
      down(0, box.center)
      up(0)
    }
    compose.waitForIdle()
    compose.onNodeWithText("Ichidan verb").assertIsDisplayed()
  }
}
