package com.bakajiten.app.data

import android.content.Context
import com.bakajiten.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Update state machine: periodic/manual checks, the prompt
// dialog, download progress, and install handoff. Automatic
// checks stay silent unless an installable update (or a
// platform-less notice) is actionable and undis missed; manual
// checks also report up-to-date and failures. Mirrors the Qt
// UpdateChecker/UpdatePrompt session rules: Later dismisses for
// the process lifetime, the checkbox persists across restarts.

data class UpdateUiState(
  val currentVersion: String = "",
  val checking: Boolean = false,
  /** Newer release with a downloadable build: prompt the user. */
  val release: AppRelease? = null,
  /** Newer release with no build for this device: notice + Close. */
  val noArtifact: Pair<String, String>? = null,
  /** Active download progress (0..1, negative when unknown); null when idle. */
  val downloading: Float? = null,
  /** Manual-check outcome (up to date / failure). */
  val notice: String? = null,
  /** Download failure (stays in the dialog for retry). */
  val error: String = "",
  /** Newest version seen (kept after Later so settings stays honest). */
  val seenVersion: String? = null,
)

class AppUpdater(
  private val context: Context,
  private val settings: SettingsRepository,
  private val fetch: suspend (String) -> String = ::defaultReleaseFetch,
  private val currentVersion: String = BuildConfig.VERSION_NAME,
  scope: CoroutineScope? = null,
) {
  private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _state = MutableStateFlow(UpdateUiState(currentVersion = currentVersion))
  val state: StateFlow<UpdateUiState> = _state.asStateFlow()
  private var dismissedVersion: String? = null
  private var downloadJob: Job? = null

  fun check(manual: Boolean = false) {
    if (_state.value.checking) return
    scope.launch {
      val prefs = settings.settings.first()
      if (!manual && !prefs.checkAppUpdates) return@launch
      _state.update { it.copy(checking = true, notice = null, error = "") }
      val result = checkAppUpdate(fetch, currentVersion, prefs.skippedAppVersion)
      _state.update { cur ->
        when (result) {
          is UpdateCheck.Available ->
            if (dismissedVersion == result.release.version) cur.copy(checking = false)
            else cur.copy(checking = false, release = result.release, noArtifact = null, seenVersion = result.release.version)
          is UpdateCheck.NoArtifact ->
            if (dismissedVersion == result.version) cur.copy(checking = false)
            else cur.copy(checking = false, noArtifact = result.version to result.notes, release = null, seenVersion = result.version)
          UpdateCheck.UpToDate ->
            if (manual) cur.copy(checking = false, notice = "bakajiten $currentVersion is the latest version.", seenVersion = null)
            else cur.copy(checking = false, seenVersion = null)
          UpdateCheck.Skipped -> cur.copy(checking = false)
          is UpdateCheck.Failed ->
            if (manual) cur.copy(checking = false, notice = result.reason)
            else cur.copy(checking = false)
        }
      }
    }
  }

  /** Later: session-dismiss the visible prompt (nag again after relaunch). */
  fun later() {
    dismissedVersion = _state.value.release?.version ?: _state.value.noArtifact?.first
    _state.update { it.copy(release = null, noArtifact = null, downloading = null, error = "") }
  }

  /** Checkbox: never notify about the visible version again (survives restarts). */
  fun skipCurrent() {
    val version = _state.value.release?.version ?: _state.value.noArtifact?.first ?: return
    scope.launch {
      settings.update { it.copy(skippedAppVersion = version) }
      _state.update { it.copy(release = null, noArtifact = null, downloading = null, error = "") }
    }
  }

  fun dismissNotice() {
    _state.update { it.copy(notice = null) }
  }

  /**
   * Download the prompted release, then hand it to the system
   * installer. The dialog stays up with progress (buttons lock
   * while it flies); [skipVersion] persists do-not-notify without
   * closing the progress view.
   */
  fun download(skipVersion: Boolean = false) {
    val release = _state.value.release ?: return
    if (downloadJob?.isActive == true) return
    _state.update { it.copy(error = "") }
    if (skipVersion) {
      scope.launch { settings.update { it.copy(skippedAppVersion = release.version) } }
    }
    downloadJob = scope.launch {
      val dir = File(context.cacheDir, "updates").apply { mkdirs() }
      val dest = File(dir, release.assetName)
      val r = runCatching {
        downloadApp(release.downloadUrl, dest) { frac ->
          _state.update { it.copy(downloading = frac) }
        }
      }
      if (r.isFailure) {
        _state.update {
          it.copy(downloading = null, error = r.exceptionOrNull()?.message ?: "download failed")
        }
        return@launch
      }
      _state.update { it.copy(downloading = null, release = null) }
      context.startActivity(appInstallIntent(context, dest))
    }
  }
}

/** Plain GET for the latest-release endpoint (injected in tests). Hops to IO itself. */
suspend fun defaultReleaseFetch(url: String): String = withContext(Dispatchers.IO) {
  val conn = URL(url).openConnection() as HttpURLConnection
  conn.connectTimeout = 15_000
  conn.readTimeout = 30_000
  conn.setRequestProperty("User-Agent", "bakajiten-update-check")
  conn.setRequestProperty("Accept", "application/vnd.github+json")
  try {
    if (conn.responseCode !in 200..299) throw IllegalStateException("update check failed: HTTP ${conn.responseCode}")
    return@withContext conn.inputStream.bufferedReader().readText()
  } finally {
    conn.disconnect()
  }
}
