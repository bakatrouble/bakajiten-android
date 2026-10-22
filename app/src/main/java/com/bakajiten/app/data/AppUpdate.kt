package com.bakajiten.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Self-update for the sideloaded APK (no Play): GitHub release
// check, user-confirmed download, then the system installer.
// Mirrors the Qt UpdateChecker/UpdatePrompt flow: automatic checks
// stay silent unless an installable update is actionable; manual
// checks also report up-to-date and failures. Skipped versions
// (SettingsRepository) suppress even manual checks.

// Latest app release (tags cut here own the Android releases, so
// this never phones anywhere else).
const val APP_RELEASES_URL =
  "https://api.github.com/repos/bakatrouble/bakajiten-android/releases/latest"

/** Newer release with a downloadable build for this device. */
data class AppRelease(
  val version: String,
  val notes: String,
  val assetName: String,
  val downloadUrl: String,
  val sizeBytes: Long,
)

/** Outcome of one update check. */
sealed interface UpdateCheck {
  data class Available(val release: AppRelease) : UpdateCheck
  data class NoArtifact(val version: String, val notes: String) : UpdateCheck
  data object UpToDate : UpdateCheck
  data object Skipped : UpdateCheck
  data class Failed(val reason: String) : UpdateCheck
}

/** Tag newer than current (`v` prefix tolerated; unparseable either side reads as not-newer). */
fun isAppNewer(current: String, tag: String): Boolean {
  val stripped = tag.removePrefix("v").removePrefix("V")
  val have = numericParts(current) ?: return false
  val latest = numericParts(stripped) ?: return false
  val width = maxOf(have.size, latest.size)
  for (i in 0 until width) {
    val a = have.getOrElse(i) { 0 }
    val b = latest.getOrElse(i) { 0 }
    if (b != a) return b > a
  }
  return false
}

private fun numericParts(version: String): List<Int>? {
  if (version.isEmpty()) return null
  val parts = version.split(".")
  if (parts.isEmpty()) return null
  return parts.map { it.toIntOrNull() ?: return null }
}

/**
 * First installable asset name: the signed release APK, then the
 * debug APK (installable over debug installs; release installs
 * reject it on signature mismatch at install time).
 */
fun matchedAppAsset(names: List<String>, tag: String): String? {
  val plain = tag.removePrefix("v").removePrefix("V")
  val candidates = listOf(
    "bakajiten-android-$tag.apk",
    "bakajiten-android-$tag-debug.apk",
    "bakajiten-android-$plain.apk",
    "bakajiten-android-$plain-debug.apk",
  )
  for (candidate in candidates) {
    if (names.contains(candidate)) return candidate
  }
  return null
}

private fun parseRelease(body: String): Triple<String, String, List<Pair<String, Pair<String, Long>>>>? {
  val release = runCatching { JSONObject(body) }.getOrNull() ?: return null
  val tag = release.optString("tag_name", "")
  if (tag.isEmpty()) return null
  val notes = release.optString("body", "")
  val assets = release.optJSONArray("assets") ?: return null
  val files = (0 until assets.length()).mapNotNull { i ->
    val asset = assets.optJSONObject(i) ?: return@mapNotNull null
    val name = asset.optString("name", "")
    if (name.isEmpty()) return@mapNotNull null
    name to (asset.optString("browser_download_url", "") to asset.optLong("size", 0))
  }
  return Triple(tag, notes, files)
}

/**
 * One update check. [fetch] performs the GET (injected so unit
 * tests serve canned JSON); it owns its own threading — this
 * runs in the caller's context. [current] is the running version
 * name, [skipped] the do-not-notify version.
 */
suspend fun checkAppUpdate(
  fetch: suspend (String) -> String,
  current: String,
  skipped: String?,
): UpdateCheck {
  val parsed = try {
    parseRelease(fetch(APP_RELEASES_URL))
  } catch (e: Exception) {
    return UpdateCheck.Failed(e.message ?: "update check failed")
  } ?: return UpdateCheck.Failed("could not read the app release")
  val (tag, notes, assets) = parsed
  if (!isAppNewer(current, tag)) return UpdateCheck.UpToDate
  val version = tag.removePrefix("v").removePrefix("V")
  if (skipped == version) return UpdateCheck.Skipped
  val match = matchedAppAsset(assets.map { it.first }, tag)
  if (match == null) return UpdateCheck.NoArtifact(version, notes)
  val (url, size) = assets.first { it.first == match }.second
  if (url.isEmpty()) return UpdateCheck.Failed("release has no download URL")
  return UpdateCheck.Available(AppRelease(version, notes, match, url, size))
}

/** Download an APK asset with progress (0..1, negative when the total is unknown). */
suspend fun downloadApp(
  url: String,
  dest: File,
  onProgress: (Float) -> Unit = {},
): Unit = withContext(Dispatchers.IO) {
  val conn = URL(url).openConnection() as HttpURLConnection
  conn.connectTimeout = 15_000
  conn.readTimeout = 60_000
  conn.setRequestProperty("Accept", "application/octet-stream")
  try {
    val total = conn.contentLengthLong
    val buf = ByteArray(256 * 1024)
    val part = File(dest.parentFile, "${dest.name}.part")
    conn.inputStream.use { input ->
      part.outputStream().buffered().use { output ->
        var done = 0L
        while (true) {
          val n = input.read(buf)
          if (n < 0) break
          output.write(buf, 0, n)
          done += n
          onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
        }
      }
    }
    if (dest.exists() && !dest.delete()) throw IllegalStateException("cannot replace ${dest.name}")
    if (!part.renameTo(dest)) throw IllegalStateException("cannot install ${dest.name}")
  } finally {
    conn.disconnect()
  }
}

/** System-installer intent for a downloaded APK (FileProvider URI; the installer guides unknown-source opt-in). */
fun appInstallIntent(context: Context, apk: File): Intent {
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
  return Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
