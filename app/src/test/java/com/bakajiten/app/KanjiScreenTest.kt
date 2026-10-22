package com.bakajiten.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.state.Backends
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.KanjiScreen
import com.bakajiten.app.ui.components.KanjiStrip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Kanji screen over the fake backend: info table, stroke player,
// Words tab, and togglable strips with persisted state.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1000dp")
class KanjiScreenTest {

  @get:Rule val compose = createComposeRule()

  private fun backends(): Backends {
    val fake = FakeDictionaryBackend()
    return Backends(
      fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    )
  }

  private fun show() {
    compose.setContent {
      BakajitenTheme {
        KanjiScreen(
          character = "日",
          backends = backends(),
          onSearch = {},
          onBack = {},
          onKanji = {},
        )
      }
    }
  }

  @Test fun kanjiSubtitleMatchesQtDialog() {
    // Title stays "Kanji information"; the subtitle carries the Qt
    // dialog wording ("Stroke count: %1 Radical: %2"). Fake 日 has
    // 4 strokes and radical 72.
    show()
    compose.onNodeWithText("Kanji information", substring = true).assertExists()
    compose.onNodeWithText("Stroke count: 4", substring = true).assertExists()
    compose.onNodeWithText("Radical: 72 ${backends().radicalGlyph(72)}", substring = true).assertExists()
  }

  @Test fun infoTabShowsTableAndPlayer() {
    show()
    // Presence (not display): the player + table can sit below the
    // fold in short viewports.
    compose.onNodeWithText("Meaning").assertExists()
    compose.onNodeWithText("Nanori").assertExists()
    compose.onNodeWithTag("strokePlayer").assertExists()
  }

  @Test fun wordsTabShowsSharedList() {
    show()
    compose.onNodeWithTag("kanjiTabWords").performClick()
    compose.onNodeWithTag("resultsList").assertExists()
  }

  @Test fun compositionTabShowsStrips() {
    show()
    compose.onNodeWithTag("kanjiTabComposition").performClick()
    compose.onNodeWithText("Similar").assertExists()
    compose.onNodeWithText("Parts").assertExists()
    compose.onNodeWithText("Part of").assertExists()
    // Fake look-alikes always expanded.
    compose.onNodeWithText("目").assertExists()
  }

  @Test fun duplicateStripCharsDoNotCrashKeys() {
    // Backend lists can repeat a character: keys must stay unique.
    compose.setContent {
      BakajitenTheme {
        KanjiStrip(
          title = "Similar",
          chars = listOf("条", "条"),
          onPick = {},
        )
      }
    }
    compose.onNodeWithText("Similar").assertExists()
  }
}
