package com.bakajiten.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bakajiten.app.data.DataManager
import com.bakajiten.app.data.FileStatus
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.DictionaryManagementContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Delete confirmation over the stateless content (no DataManager,
// no native lib): trash arms the dialog, Remove deletes, Cancel
// aborts.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryManagementTest {

  @get:Rule val compose = createComposeRule()

  private fun show(deleted: (String) -> Unit) {
    compose.setContent {
      BakajitenTheme {
        DictionaryManagementContent(
          statuses = mapOf(
            "jmdict.bin" to FileStatus.CURRENT,
            "kanji.bin" to FileStatus.CURRENT,
            "templates.bin" to FileStatus.CURRENT,
            "jmnedict.bin" to FileStatus.CURRENT,
            "kokugo.bin" to FileStatus.MISSING,
          ),
          saved = emptyMap(),
          latest = emptyMap(),
          savedBytes = emptyMap(),
          latestBytes = emptyMap(),
          progress = emptyMap(),
          error = "",
          onDownload = {},
          onDownloadAll = {},
          onDelete = deleted,
          onDone = {},
        )
      }
    }
  }

  @Test fun trashArmsConfirmationBeforeDeleting() {
    var deleted: String? = null
    show { deleted = it }
    compose.onNodeWithTag("delete-jmnedict.bin").performScrollTo()
    compose.onNodeWithTag("delete-jmnedict.bin").performClick()
    compose.onNodeWithTag("confirmDelete", useUnmergedTree = true).assertExists()
    assertNull("no delete before confirm, got $deleted", deleted)
    compose.onNodeWithTag("confirmDelete").performClick()
    assertEquals("jmnedict.bin", deleted)
  }

  @Test fun cancelAbortsDelete() {
    var deleted: String? = null
    show { deleted = it }
    compose.onNodeWithTag("delete-jmnedict.bin").performScrollTo()
    compose.onNodeWithTag("delete-jmnedict.bin").performClick()
    compose.onNodeWithText("Cancel").performClick()
    compose.onNodeWithText("Remove jmnedict.bin?").assertDoesNotExist()
    assertNull("cancel must not delete, got $deleted", deleted)
  }

  @Test fun activeDownloadShowsProgressBar() {
    compose.setContent {
      BakajitenTheme {
        DictionaryManagementContent(
          statuses = mapOf("kokugo.bin" to FileStatus.MISSING),
          saved = emptyMap(),
          latest = emptyMap(),
          savedBytes = emptyMap(),
          latestBytes = emptyMap(),
          progress = mapOf("kokugo.bin" to 0.4f),
          error = "",
          onDownload = {},
          onDownloadAll = {},
          onDelete = {},
          onDone = {},
        )
      }
    }
    compose.onNodeWithTag("progress-kokugo.bin").assertExists()
    compose.onNodeWithText("40%").assertExists()
  }

  @Test fun updateAllOffersOutdated() {
    var updated: List<String>? = null
    compose.setContent {
      BakajitenTheme {
        DictionaryManagementContent(
          statuses = mapOf(
            "jmdict.bin" to FileStatus.CURRENT,
            "kanji.bin" to FileStatus.OUTDATED,
            "templates.bin" to FileStatus.CURRENT,
            "jmnedict.bin" to FileStatus.OUTDATED,
            "kokugo.bin" to FileStatus.MISSING,
          ),
          saved = emptyMap(),
          latest = emptyMap(),
          savedBytes = emptyMap(),
          latestBytes = emptyMap(),
          progress = emptyMap(),
          error = "",
          onDownload = {},
          onDownloadAll = { updated = it },
          onDelete = {},
          onDone = {},
        )
      }
    }
    compose.onNodeWithTag("updateAll").performScrollTo()
    compose.onNodeWithTag("updateAll").performClick()
    assertEquals(listOf("kanji.bin", "jmnedict.bin"), updated)
  }

  @Test fun requiredFilesHaveNoDeleteButton() {
    compose.setContent {
      BakajitenTheme {
        DictionaryManagementContent(
          statuses = mapOf(
            "jmdict.bin" to FileStatus.CURRENT,
            "kanji.bin" to FileStatus.MISSING,
            "templates.bin" to FileStatus.MISSING,
            "jmnedict.bin" to FileStatus.CURRENT,
            "kokugo.bin" to FileStatus.MISSING,
          ),
          saved = emptyMap(),
          latest = emptyMap(),
          savedBytes = emptyMap(),
          latestBytes = emptyMap(),
          progress = emptyMap(),
          error = "",
          onDownload = {},
          onDownloadAll = {},
          onDelete = {},
          onDone = {},
        )
      }
    }
    // Required artifacts never offer individual delete (opt-ins
    // only); a full reset goes through Clear cached files.
    compose.onNodeWithTag("delete-jmdict.bin").assertDoesNotExist()
    compose.onNodeWithTag("delete-jmnedict.bin").assertExists()
  }

  @Test fun clearCacheConfirmsBeforeClearing() {
    var cleared = false
    compose.setContent {
      BakajitenTheme {
        DictionaryManagementContent(
          statuses = mapOf("jmdict.bin" to FileStatus.CURRENT),
          saved = emptyMap(),
          latest = emptyMap(),
          savedBytes = emptyMap(),
          latestBytes = emptyMap(),
          progress = emptyMap(),
          error = "",
          onDownload = {},
          onDownloadAll = {},
          onDelete = {},
          onClearAll = { cleared = true },
          onDone = {},
        )
      }
    }
    compose.onNodeWithTag("clearCache").performScrollTo()
    compose.onNodeWithTag("clearCache").performClick()
    compose.onNodeWithTag("confirmClear", useUnmergedTree = true).assertExists()
    assertEquals(false, cleared)
    compose.onNodeWithTag("confirmClear").performClick()
    assertEquals(true, cleared)
  }

  @Test fun localVersionNullWithoutNatives() {
    // No Rust .so on the JVM (and none for this ABI on a fresh
    // device): version reads must degrade to null, never throw —
    // native failures surface as Errors, not Exceptions.
    val dir = java.nio.file.Files.createTempDirectory("bakajiten-dm").toFile()
    assertNull(DataManager(dir).localVersion("jmdict.bin"))
    java.io.File(dir, "jmdict.bin").writeText("not an artifact")
    assertNull(DataManager(dir).localVersion("jmdict.bin"))
    assertNull(DataManager(dir).localVersion("kanji.bin"))
    assertNull(DataManager(dir).localVersion("templates.bin"))
  }
}
