package com.bakajiten.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bakajiten.app.data.Scope
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.SearchOptionsMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Options menu: affix toggles never dismiss it, and uncached
// scopes route to dictionary management instead of switching.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptionsMenuTest {

  @get:Rule val compose = createComposeRule()

  private fun show(
    cached: Set<Scope> = Scope.entries.toSet(),
    onScope: (Scope) -> Unit = {},
    onDictionaries: () -> Unit = {},
  ) {
    compose.setContent {
      BakajitenTheme {
        SearchOptionsMenu(
          prefix = true,
          suffix = false,
          meaning = false,
          onPrefix = {},
          onSuffix = {},
          onMeaning = {},
          scope = Scope.ENGLISH,
          onScope = onScope,
          onSettings = {},
          cachedScopes = cached,
          onOpenDictionaries = onDictionaries,
        )
      }
    }
  }

  @Test fun affixToggleKeepsMenuOpen() {
    show()
    compose.onNodeWithTag("optionsButton").performClick()
    compose.onNodeWithTag("prefixChip").assertIsDisplayed()
    compose.onNodeWithTag("prefixChip").performClick()
    // Still open: toggles must not dismiss.
    compose.onNodeWithTag("scopeEnglish").assertIsDisplayed()
    compose.onNodeWithTag("prefixChip").assertIsDisplayed()
  }

  @Test fun uncachedScopeOpensDictionaries() {
    var scoped: Scope? = null
    var dictionaries = false
    show(
      cached = setOf(Scope.ENGLISH),
      onScope = { scoped = it },
      onDictionaries = { dictionaries = true },
    )
    compose.onNodeWithTag("optionsButton").performClick()
    compose.onNodeWithTag("scopeNames").performClick()
    assertEquals(null, scoped)
    assertTrue("uncached tap must open dictionaries", dictionaries)
  }

  @Test fun cachedScopeSwitches() {
    var scoped: Scope? = null
    var dictionaries = false
    show(onScope = { scoped = it }, onDictionaries = { dictionaries = true })
    compose.onNodeWithTag("optionsButton").performClick()
    compose.onNodeWithTag("scopeNames").performClick()
    assertEquals(Scope.NAMES, scoped)
    assertTrue(!dictionaries)
  }
}
