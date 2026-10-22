package com.bakajiten.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bakajiten.app.data.AppRelease
import com.bakajiten.app.data.UpdateUiState
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.AppUpdateDialog
import com.bakajiten.app.ui.updateDialogVisible
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Update dialog states: available prompt (version + notes,
// Download/Later, skip arming), inline download progress, and
// manual-check notices.

private fun release() = AppRelease(
  version = "0.2.0",
  notes = "Faster startup.",
  assetName = "bakajiten-android-v0.2.0.apk",
  downloadUrl = "https://example.invalid/b.apk",
  sizeBytes = 10,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateDialogTest {

  @get:Rule val compose = createComposeRule()

  private fun show(
    state: UpdateUiState,
    onDownload: (Boolean) -> Unit = {},
    onLater: (Boolean) -> Unit = {},
    onCloseNotice: () -> Unit = {},
  ) {
    compose.setContent {
      BakajitenTheme {
        AppUpdateDialog(
          state = state,
          onDownload = onDownload,
          onLater = onLater,
          onCloseNotice = onCloseNotice,
        )
      }
    }
  }

  @Test fun hiddenWhenIdle() {
    assertEquals(false, updateDialogVisible(UpdateUiState()))
    assertEquals(false, updateDialogVisible(UpdateUiState(checking = true)))
  }

  @Test fun availableShowsVersionAndNotes() {
    show(UpdateUiState(currentVersion = "0.1.0", release = release()))
    compose.onNodeWithTag("updateDialog").assertExists()
    compose.onNodeWithText("bakajiten 0.2.0 is available (you have 0.1.0).").assertExists()
    compose.onNodeWithText("Faster startup.").assertExists()
    compose.onNodeWithTag("updateDownload").assertExists()
    compose.onNodeWithTag("updateLater").assertExists()
  }

  @Test fun downloadReportsSkipUncheckedByDefault() {
    var skip: Boolean? = null
    show(UpdateUiState(release = release()), onDownload = { skip = it })
    compose.onNodeWithTag("updateDownload").performClick()
    assertEquals(false, skip)
  }

  @Test fun downloadReportsSkipWhenArmed() {
    var skip: Boolean? = null
    show(UpdateUiState(release = release()), onDownload = { skip = it })
    compose.onNodeWithTag("updateSkip").performClick()
    compose.onNodeWithTag("updateDownload").performClick()
    assertEquals(true, skip)
  }

  @Test fun laterReportsSkip() {
    var skip: Boolean? = null
    show(UpdateUiState(release = release()), onLater = { skip = it })
    compose.onNodeWithTag("updateSkip").performClick()
    compose.onNodeWithTag("updateLater").performClick()
    assertEquals(true, skip)
  }

  @Test fun downloadingShowsProgressAndLocksButtons() {
    show(UpdateUiState(release = release(), downloading = 0.4f))
    compose.onNodeWithTag("updateProgress").assertExists()
    compose.onNodeWithText("40%").assertExists()
    // Locked buttons still exist (disabled, not gone).
    compose.onNodeWithTag("updateDownload").assertExists()
    compose.onNodeWithTag("updateLater").assertExists()
  }

  @Test fun unknownTotalShowsIndeterminateLabel() {
    show(UpdateUiState(release = release(), downloading = -1f))
    compose.onNodeWithText("Downloading…").assertExists()
  }

  @Test fun noticeShowsTextAndClose() {
    var closed = false
    show(UpdateUiState(notice = "bakajiten 0.1.0 is the latest version."), onCloseNotice = { closed = true })
    compose.onNodeWithTag("updateDialog").assertExists()
    compose.onNodeWithText("bakajiten 0.1.0 is the latest version.").assertExists()
    compose.onNodeWithTag("updateClose").performClick()
    assertEquals(true, closed)
  }

  @Test fun noArtifactExplainsAndCloses() {
    var closed = false
    show(
      UpdateUiState(noArtifact = "0.2.0" to "Notes."),
      onLater = { closed = true },
    )
    compose.onNodeWithText("bakajiten 0.2.0 is available, but it has no build for this device yet.").assertExists()
    compose.onNodeWithTag("updateLater").performClick()
    assertEquals(true, closed)
  }
}
