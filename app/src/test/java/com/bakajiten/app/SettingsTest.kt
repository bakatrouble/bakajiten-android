package com.bakajiten.app

import androidx.test.core.app.ApplicationProvider
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.DataStoreSettingsRepository
import com.bakajiten.app.data.InMemorySettingsRepository
import com.bakajiten.app.data.SearchBarPosition
import com.bakajiten.app.data.Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class SettingsTest {
  @Test fun inMemoryDefaults() = runTest {
    val repo = InMemorySettingsRepository()
    assertEquals(AppSettings(), repo.settings.first())
  }

  @Test fun inMemoryUpdateRoundTrips() = runTest {
    val repo = InMemorySettingsRepository()
    repo.update { it.copy(searchBarPosition = SearchBarPosition.BOTTOM) }
    assertEquals(SearchBarPosition.BOTTOM, repo.settings.first().searchBarPosition)
    repo.update { it.copy(searchBarPosition = SearchBarPosition.TOP) }
    assertEquals(SearchBarPosition.TOP, repo.settings.first().searchBarPosition)
  }

  @Test fun canvasHeightRoundTrips() = runTest {
    val repo = InMemorySettingsRepository()
    assertEquals(null, repo.settings.first().recognizerCanvasHeightDp)
    repo.update { it.copy(recognizerCanvasHeightDp = 320f) }
    assertEquals(320f, repo.settings.first().recognizerCanvasHeightDp)
    repo.update { it.copy(recognizerCanvasHeightDp = null) }
    assertEquals(null, repo.settings.first().recognizerCanvasHeightDp)
  }

  @Test fun themeUpdateRoundTrips() = runTest {
    val repo = InMemorySettingsRepository()
    repo.update { it.copy(theme = Theme.DARK, densityDpi = 320) }
    val s = repo.settings.first()
    assertEquals(Theme.DARK, s.theme)
    assertEquals(320, s.densityDpi)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDataStoreTest {
  @Test fun dpiClampsAndDefaultsToSystem() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val repo = DataStoreSettingsRepository(context)
    assertEquals(null, repo.settings.first().densityDpi)
    repo.update { it.copy(densityDpi = 9999) }
    assertEquals(640, repo.settings.first().densityDpi)
    repo.update { it.copy(densityDpi = 1) }
    assertEquals(120, repo.settings.first().densityDpi)
    repo.update { it.copy(densityDpi = null) }
    assertEquals(null, repo.settings.first().densityDpi)
  }

  @Test fun updatePrefsRoundTrip() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val repo = DataStoreSettingsRepository(context)
    assertEquals(true, repo.settings.first().checkAppUpdates)
    assertEquals(null, repo.settings.first().skippedAppVersion)
    repo.update { it.copy(checkAppUpdates = false, skippedAppVersion = "0.2.0") }
    assertEquals(false, repo.settings.first().checkAppUpdates)
    assertEquals("0.2.0", repo.settings.first().skippedAppVersion)
    repo.update { it.copy(checkAppUpdates = true, skippedAppVersion = null) }
    assertEquals(true, repo.settings.first().checkAppUpdates)
    assertEquals(null, repo.settings.first().skippedAppVersion)
  }
}
