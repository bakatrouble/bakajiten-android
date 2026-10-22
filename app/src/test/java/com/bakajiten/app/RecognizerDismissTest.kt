package com.bakajiten.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.InMemorySettingsRepository
import com.bakajiten.app.state.AppViewModel
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.AdaptiveWidth
import com.bakajiten.app.ui.BakajitenApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Recognizer dismissal: system back hides the panel (it must not
// navigate away), and tapping the list behind hides it too.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1000dp")
class RecognizerDismissTest {

  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun launch() {
    val fake = FakeDictionaryBackend()
    val backends = Backends(
      fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    )
    val vm = AppViewModel(InMemorySettingsRepository(), backends)
    compose.setContent { BakajitenApp(vm = vm, backends = backends, widthClass = AdaptiveWidth.COMPACT) }
    idleMain()
  }

  private fun idleMain(ms: Long = 500) {
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
      .idleFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
  }

  private fun searchTaberu() {
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
  }

  @Test fun backHidesPanelInsteadOfNavigating() {
    launch()
    compose.onNodeWithTag("recognizerToggle").performClick()
    compose.onNodeWithTag("recognizerPanel").assertIsDisplayed()
    compose.activity.onBackPressedDispatcher.onBackPressed()
    compose.onNodeWithTag("recognizerPanel").assertDoesNotExist()
    // Still on search — back must not have navigated anywhere.
    compose.onNodeWithTag("searchField").assertIsDisplayed()
  }

  @Test fun tappingListHidesPanel() {
    launch()
    searchTaberu()
    compose.onNodeWithTag("recognizerToggle").performClick()
    compose.onNodeWithTag("recognizerPanel").assertIsDisplayed()
    // True touch injection (not semantics click): the panel overlays
    // the list, so tap visible list area *below* the panel — a tap
    // on the panel itself draws instead of dismissing. The finger
    // down fires the list's press handler first, so the panel
    // collapses and the row tap itself cancels mid-shift — first
    // tap only dismisses.
    val panel = compose.onNodeWithTag("recognizerPanel").fetchSemanticsNode().boundsInRoot
    val mask = compose.onNodeWithTag("listMask").fetchSemanticsNode().boundsInRoot
    val tap = androidx.compose.ui.geometry.Offset(mask.center.x, (panel.bottom + mask.bottom) / 2f)
    org.junit.Assert.assertTrue("no visible list below the panel: $panel in $mask", tap.y < mask.bottom - 1f)
    compose.onNodeWithTag("searchTop").performTouchInput {
      down(0, tap)
      up(0)
    }
    compose.onNodeWithTag("recognizerPanel").assertDoesNotExist()
    compose.onNodeWithTag("searchField").assertIsDisplayed()
    // Second tap, panel gone and layout stable, opens the article.
    val rowCenter2 = compose.onNodeWithTag("resultRow0").fetchSemanticsNode().boundsInRoot.center
    compose.onNodeWithTag("searchTop").performTouchInput {
      down(1, rowCenter2)
      up(1)
    }
    compose.onNodeWithTag("article").assertIsDisplayed()
  }

  @Test fun fieldFocusKeepsPanelOpen() {
    // The field stays focusable (tap-and-hold selection) with the
    // panel open — focusing it must not dismiss the panel. The
    // readOnly switch is what keeps the keyboard away instead.
    launch()
    compose.onNodeWithTag("recognizerToggle").performClick()
    compose.onNodeWithTag("recognizerPanel").assertIsDisplayed()
    compose.onNodeWithTag("searchField").performClick()
    compose.onNodeWithTag("recognizerPanel").assertIsDisplayed()
    compose.onNodeWithTag("searchField").assertIsFocused()
  }

  @Test fun openingPanelKeepsFocus() {
    // Opening the panel hides a live keyboard but preserves field
    // focus (and any selection): only the keyboard goes away.
    launch()
    compose.onNodeWithTag("searchField").performClick()
    compose.onNodeWithTag("searchField").assertIsFocused()
    compose.onNodeWithTag("recognizerToggle").performClick()
    compose.onNodeWithTag("recognizerPanel").assertIsDisplayed()
    compose.onNodeWithTag("searchField").assertIsFocused()
  }
}
