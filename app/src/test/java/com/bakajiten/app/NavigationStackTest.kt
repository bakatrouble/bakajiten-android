package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.InMemorySettingsRepository
import com.bakajiten.app.state.AppViewModel
import com.bakajiten.app.state.Backends
import com.bakajiten.app.state.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Full navigation history: go() pushes, back() pops step by step
// instead of always landing on search.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationStackTest {

  private fun vm(): AppViewModel {
    val fake = FakeDictionaryBackend()
    val backends = Backends(
      fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
      fake::posLabel, fake::posTip, { "" }, { "" }, fake::miscLabel, fake::miscTip,
    )
    return AppViewModel(InMemorySettingsRepository(), backends)
  }

  @Test fun backWalksHistoryInReverse() {
    val vm = vm()
    vm.go(Screen.Article(0))
    vm.go(Screen.Settings)
    vm.go(Screen.Kanji("日"))
    assertEquals(Screen.Kanji("日"), vm.ui.value.screen)
    assertTrue(vm.back())
    assertEquals(Screen.Settings, vm.ui.value.screen)
    assertTrue(vm.ui.value.popping)
    assertTrue(vm.back())
    assertEquals(Screen.Article(0), vm.ui.value.screen)
    assertTrue(vm.back())
    assertEquals(Screen.Search, vm.ui.value.screen)
    assertFalse(vm.back())
  }

  @Test fun repeatVisitsDoNotStack() {
    val vm = vm()
    vm.go(Screen.Article(0))
    vm.go(Screen.Article(0))
    assertTrue(vm.back())
    assertEquals(Screen.Search, vm.ui.value.screen)
    assertFalse(vm.back())
  }
}
