package com.bakajiten.app

import androidx.test.core.app.ApplicationProvider
import com.bakajiten.app.data.AppUpdater
import com.bakajiten.app.data.InMemorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Update state machine over the in-memory settings repo: prompt
// routing, skip persistence, session dismiss, manual notices.
// Unconfined dispatch keeps the updater's own scope synchronous
// under test (production passes nothing and gets Main).

private const val UPDATER_JSON = """
{"tag_name":"v0.2.0","body":"Notes.","assets":[
{"name":"bakajiten-android-v0.2.0.apk","browser_download_url":"https://example.invalid/b.apk","size":10}]}
"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdaterTest {
  private fun updater(
    repo: InMemorySettingsRepository = InMemorySettingsRepository(),
    fetch: suspend (String) -> String = { UPDATER_JSON },
  ) = AppUpdater(
    ApplicationProvider.getApplicationContext(),
    repo,
    fetch,
    "0.1.0",
    CoroutineScope(UnconfinedTestDispatcher()),
  )

  private suspend fun settled(u: AppUpdater) {
    withTimeout(5_000) { u.state.first { !it.checking } }
  }

  @Test fun availableReleasePrompts() = runTest {
    val u = updater()
    u.check(manual = true)
    settled(u)
    assertEquals("0.2.0", u.state.value.release?.version)
    assertNull(u.state.value.notice)
  }

  @Test fun upToDateManualNotices() = runTest {
    val past = AppUpdater(
      ApplicationProvider.getApplicationContext(),
      InMemorySettingsRepository(),
      { UPDATER_JSON },
      "9.9.9",
      CoroutineScope(UnconfinedTestDispatcher()),
    )
    past.check(manual = true)
    withTimeout(5_000) { past.state.first { !it.checking } }
    assertTrue(past.state.value.notice!!.contains("9.9.9"))
    assertNull(past.state.value.release)
  }

  @Test fun failedManualNotices() = runTest {
    val u = updater(fetch = { throw IllegalStateException("offline") })
    u.check(manual = true)
    settled(u)
    assertEquals("offline", u.state.value.notice)
  }

  @Test fun failedAutoStaysSilent() = runTest {
    val u = updater(fetch = { throw IllegalStateException("offline") })
    u.check(manual = false)
    settled(u)
    assertNull(u.state.value.notice)
    assertNull(u.state.value.release)
  }

  @Test fun skipPersistsAndSuppresses() = runTest {
    val repo = InMemorySettingsRepository()
    val u = updater(repo)
    u.check(manual = true)
    settled(u)
    assertNotNull(u.state.value.release)
    u.skipCurrent()
    assertEquals("0.2.0", repo.settings.first().skippedAppVersion)
    u.check(manual = true)
    settled(u)
    assertNull("skipped version must not prompt", u.state.value.release)
  }

  @Test fun laterSuppressesForTheSession() = runTest {
    val u = updater()
    u.check(manual = true)
    settled(u)
    assertNotNull(u.state.value.release)
    u.later()
    assertNull(u.state.value.release)
    u.check(manual = true)
    settled(u)
    assertNull("later must hold until relaunch", u.state.value.release)
  }

  @Test fun autoCheckRespectsOptOut() = runTest {
    var fetched = false
    val repo = InMemorySettingsRepository()
    repo.update { it.copy(checkAppUpdates = false) }
    val u = updater(repo, fetch = { fetched = true; UPDATER_JSON })
    u.check(manual = false)
    assertEquals(false, fetched)
    u.check(manual = true)
    settled(u)
    assertEquals(true, fetched)
  }

  @Test fun downloadWithoutReleaseIsNoOp() = runTest {
    val u = updater(fetch = { UPDATER_JSON })
    u.download()
    assertNull(u.state.value.downloading)
    assertEquals("", u.state.value.error)
  }
}
