package com.bakajiten.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.InMemorySettingsRepository
import com.bakajiten.app.data.SearchBarPosition
import com.bakajiten.app.state.AppViewModel
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.AdaptiveWidth
import com.bakajiten.app.ui.BakajitenApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Headless e2e on the JVM (no emulator): full app with the fake
// backend, driven through the same test tags as the UI. Run with
// ./gradlew :app:testDebugUnitTest (headless, --no-daemon safe).

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppE2eTest {
  @get:Rule val compose = createComposeRule()

  private fun backends(): Backends {
    val fake = FakeDictionaryBackend()
    return Backends(
      fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    )
  }

  private fun launch(
    width: AdaptiveWidth = AdaptiveWidth.COMPACT,
    repo: InMemorySettingsRepository = InMemorySettingsRepository(),
  ): AppViewModel {
    val vm = AppViewModel(repo, backends())
    compose.setContent { BakajitenApp(vm = vm, backends = backends(), widthClass = width) }
    // Let the view-model's collectors start (Robolectric parks
    // the main looper; Compose's waitUntil doesn't pump it).
    idleMain()
    return vm
  }

  /** Run pending + delayed main-looper work (debounced searches). */
  private fun idleMain(ms: Long = 500) {
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
      .idleFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
  }

  @Test fun searchFindsResults() {
    launch()
    compose.onNodeWithTag("searchField").performTextInput("taberu")
    idleMain()
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithTag("resultRow0").assertIsDisplayed()
        true
      } catch (e: AssertionError) {
        false
      }
    }
    compose.onNodeWithText("食べる", substring = true).assertIsDisplayed()
  }

  @Test fun kanaFieldConvertsWhileTyping() {
    launch()
    compose.onNodeWithTag("searchField").performTextInput("taberu")
    // The field shows converted kana, not the raw romaji buffer.
    compose.onNodeWithText("たべる").assertIsDisplayed()
    idleMain()
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithTag("resultRow0").assertIsDisplayed()
        true
      } catch (e: AssertionError) {
        false
      }
    }
  }

  @Test fun hepburnSettingRomanizesReading() {
    val repo = InMemorySettingsRepository()
    kotlinx.coroutines.runBlocking {
      repo.update { it.copy(romaji = com.bakajiten.app.data.RomajiStyle.HEPBURN) }
    }
    launch(repo = repo)
    compose.onNodeWithTag("searchField").performTextInput("taberu")
    idleMain()
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithText("taberu", substring = true).assertIsDisplayed()
        true
      } catch (e: AssertionError) {
        false
      }
    }
  }

  @Test fun articleOpensAsNewScreen() {
    launch()
    compose.onNodeWithTag("searchField").performTextInput("taberu")
    idleMain()
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithTag("resultRow0").assertIsDisplayed()
        true
      } catch (e: AssertionError) {
        false
      }
    }
    // Phones push the article as a new screen (no article yet).
    compose.onNodeWithTag("article").assertDoesNotExist()
    compose.onNodeWithTag("resultRow0").performClick()
    compose.onNodeWithTag("article").assertIsDisplayed()
    compose.onNodeWithText("to eat", substring = true).assertIsDisplayed()
    // The app-bar back returns to search.
    compose.onNodeWithTag("articleBack").performClick()
    compose.onNodeWithTag("article").assertDoesNotExist()
    compose.onNodeWithTag("searchField").assertIsDisplayed()
  }

  @Test fun searchBarDocksBottom() {
    val repo = InMemorySettingsRepository()
    val vm = AppViewModel(repo, backends())
    compose.setContent { BakajitenApp(vm = vm, backends = backends(), widthClass = AdaptiveWidth.COMPACT) }
    compose.onNodeWithTag("searchTop").assertIsDisplayed()
    kotlinx.coroutines.runBlocking {
      repo.update { it.copy(searchBarPosition = SearchBarPosition.BOTTOM) }
    }
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithTag("searchBottom").assertIsDisplayed()
        true
      } catch (e: AssertionError) {
        false
      }
    }
  }

  @Test fun recognizerTogglesPanel() {
    launch()
    compose.onNodeWithTag("recognizerToggle").performClick()
    compose.onNodeWithTag("recognizerPanel").assertIsDisplayed()
    compose.onNodeWithTag("listMask").assertIsDisplayed()
    compose.onNodeWithTag("recognizerToggle").performClick()
    compose.onNodeWithTag("recognizerPanel").assertDoesNotExist()
    compose.onNodeWithTag("listMask").assertDoesNotExist()
  }

  @Test fun affixChipsToggle() {
    launch()
    compose.onNodeWithTag("optionsButton").performClick()
    compose.onNodeWithTag("prefixChip").assertIsDisplayed()
    compose.onNodeWithTag("meaningChip").performClick()
    idleMain()
    compose.onNodeWithTag("searchField").performTextInput("eat")
    idleMain()
    compose.waitUntil(10_000) {
      try {
        compose.onNodeWithTag("resultRow0").assertIsDisplayed()
        true
      } catch (e: AssertionError) {
        false
      }
    }
  }
}
