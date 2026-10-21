package com.bakajiten.app.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.dict.glossVersion
import uniffi.dict.kanjiVersions
import uniffi.recognizer.templatesPackedAt

// Artifact fetching: mirrors frontends/web Backend.ts. Nothing
// downloads without a user tap: the UI checks status() first and
// shows the consent screen when the required trio is missing or
// stale. Transports are the data-repo release `.bin.gz` files,
// unpacked with java.util.zip (CRC left to the sha256 check).

const val RELEASE_BASE =
  "https://github.com/bakatrouble/bakajiten-data/releases/latest/download"

val REQUIRED_FILES = listOf("jmdict.bin", "kanji.bin", "templates.bin")
val OPTIONAL_FILES = listOf("jmnedict.bin", "kokugo.bin")

val SCOPE_FILE = mapOf(Scope.ENGLISH to "jmdict.bin", Scope.NAMES to "jmnedict.bin", Scope.KOKUGO to "kokugo.bin")

enum class FileStatus { MISSING, OUTDATED, CURRENT }

data class ManifestFile(
  val size: Long,
  val sha256: String?,
  val version: String?,
)

class DataManager(private val dir: File) {
  private fun file(name: String) = File(dir, name)
  private fun gzName(name: String) = "$name.gz"

  /**
   * Saved version stamp read from the artifact itself through
   * UniFFI (gloss/kanji stamps, templates pack date): no sidecars,
   * never stale. Throws when the file is missing or invalid;
   * [localVersion] nulls that out.
   */
  private fun versionOf(name: String, path: String): String = when (name) {
    "kanji.bin" -> {
      val v = kanjiVersions(path)
      if (v.kanjivg == "unknown") v.kanjidic2 else "${v.kanjidic2} · VG ${v.kanjivg}"
    }
    "templates.bin" -> packedDate(templatesPackedAt(path))
    else -> glossVersion(path)
  }

  private fun packedDate(epochSeconds: ULong): String =
    java.time.Instant.ofEpochSecond(epochSeconds.toLong())
      .atZone(java.time.ZoneOffset.UTC)
      .toLocalDate()
      .toString()

  /** Saved version stamp, read from the artifact via UniFFI (null when missing/invalid). */
  fun localVersion(name: String): String? =
    runCatching { versionOf(name, file(name).absolutePath) }.getOrNull()

  /** Saved artifact size in bytes (null when not downloaded). */
  fun localSize(name: String): Long? = file(name).takeIf { it.exists() }?.length()

  suspend fun manifest(dataBase: String = RELEASE_BASE): Map<String, ManifestFile> =
    withContext(Dispatchers.IO) {
      val conn = URL("$dataBase/manifest.json").openConnection() as HttpURLConnection
      conn.connectTimeout = 15_000
      conn.readTimeout = 30_000
      try {
        val body = conn.inputStream.bufferedReader().readText()
        val files = JSONObject(body).getJSONObject("files")
        buildMap {
          for (key in files.keys()) {
            val o = files.getJSONObject(key)
            put(
              key,
              ManifestFile(
                size = o.optLong("size", -1),
                sha256 = if (o.isNull("sha256")) null else o.optString("sha256"),
                version = if (o.isNull("version")) null else o.optString("version"),
              ),
            )
          }
        }
      } finally {
        conn.disconnect()
      }
    }

  suspend fun status(name: String, manifest: Map<String, ManifestFile>): FileStatus =
    withContext(Dispatchers.IO) {
      val local = file(name)
      val want = manifest[name] ?: return@withContext if (local.exists()) FileStatus.CURRENT else FileStatus.MISSING
      if (!local.exists() || (want.size >= 0 && local.length() != want.size)) {
        return@withContext FileStatus.MISSING
      }
      if (want.sha256 != null && sha256(local) != want.sha256.lowercase()) {
        return@withContext FileStatus.OUTDATED
      }
      FileStatus.CURRENT
    }

  /** Download + verify + atomically install one artifact. */
  suspend fun download(
    name: String,
    manifest: Map<String, ManifestFile>,
    dataBase: String = RELEASE_BASE,
    onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
  ): Unit = withContext(Dispatchers.IO) {
    val want = manifest[name]
    val conn = URL("$dataBase/${gzName(name)}").openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 60_000
    try {
      val total = conn.contentLengthLong.takeIf { it > 0 } ?: want?.size ?: -1
      // Stream through a fixed buffer: readBytes() would pin the
      // whole (compressed + decompressed) artifact in RAM at once.
      val buf = ByteArray(256 * 1024)
      val gzPart = File(dir, "${gzName(name)}.part")
      conn.inputStream.use { input ->
        gzPart.outputStream().buffered().use { output ->
          var done = 0L
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            done += n
            onProgress(done, total)
          }
        }
      }
      val tmp = File(dir, "$name.part")
      val gzipped = gzPart.inputStream().buffered().use { it.read() == 0x1f && it.read() == 0x8b }
      if (gzipped) {
        try {
          GZIPInputStream(gzPart.inputStream().buffered()).use { gz ->
            tmp.outputStream().buffered().use { output ->
              while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
              }
            }
          }
        } finally {
          gzPart.delete()
        }
      } else {
        // Host transcoded the .gz: the bytes are already raw.
        if (!gzPart.renameTo(tmp)) throw IllegalStateException("cannot stage $name")
      }
      if (want != null) {
        if (want.size >= 0 && tmp.length() != want.size) {
          tmp.delete()
          throw IllegalStateException("size mismatch for $name")
        }
        if (want.sha256 != null && sha256(tmp) != want.sha256.lowercase()) {
          tmp.delete()
          throw IllegalStateException("sha256 mismatch for $name")
        }
      }
      val dest = file(name)
      if (dest.exists() && !dest.delete()) throw IllegalStateException("cannot replace $name")
      if (!tmp.renameTo(dest)) throw IllegalStateException("cannot install $name")
    } finally {
      conn.disconnect()
    }
  }

  /** Remove a downloaded artifact (required ones re-offer download). */
  fun delete(name: String): Boolean {
    val f = file(name)
    return !f.exists() || f.delete()
  }

  fun bytes(name: String): ByteArray? {
    val f = file(name)
    return if (f.exists()) f.readBytes() else null
  }

  fun path(name: String): String? {
    val f = file(name)
    return if (f.exists()) f.absolutePath else null
  }
}

fun sha256(f: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  f.inputStream().use { input ->
    val buf = ByteArray(1 shl 16)
    while (true) {
      val n = input.read(buf)
      if (n < 0) break
      digest.update(buf, 0, n)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}
