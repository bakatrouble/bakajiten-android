package com.bakajiten.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.expressive.SettingsGroup
import com.bakajiten.app.data.DataManager
import com.bakajiten.app.data.FileStatus
import com.bakajiten.app.data.ManifestFile
import com.bakajiten.app.data.OPTIONAL_FILES
import com.bakajiten.app.data.REQUIRED_FILES
import com.bakajiten.app.theme.BakajitenTheme
import kotlinx.coroutines.launch

// First-run consent: lists every artifact with its status and a
// per-file action (Download when missing, Update when stale).
// Nothing downloads without a tap. Expressive segmented groups
// matching SettingsScreen: required files, then optionals.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictionaryManagementScreen(manager: DataManager, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  var manifest by remember { mutableStateOf(emptyMap<String, ManifestFile>()) }
  var statuses by remember { mutableStateOf(mapOf<String, FileStatus>()) }
  var saved by remember { mutableStateOf(mapOf<String, String?>()) }
  var savedBytes by remember { mutableStateOf(mapOf<String, Long?>()) }
  // Active downloads and their 0..1 progress (negative = unknown
  // total). Files download in parallel; streaming keeps RAM flat.
  var progress by remember { mutableStateOf(mapOf<String, Float>()) }
  var error by remember { mutableStateOf("") }

  suspend fun updateStatuses() {
    val names = REQUIRED_FILES + OPTIONAL_FILES
    statuses = names.associateWith { manager.status(it, manifest) }
    saved = names.associateWith { manager.localVersion(it) }
    savedBytes = names.associateWith { manager.localSize(it) }
  }

  suspend fun refresh() {
    manifest = runCatching { manager.manifest() }.getOrElse { emptyMap() }
    updateStatuses()
  }

  fun download(name: String) {
    error = ""
    scope.launch {
      progress = progress + (name to 0f)
      val r = runCatching {
        manager.download(name, manifest) { done, total ->
          val frac = if (total > 0) {
            (done.toFloat() / total).coerceIn(0f, 1f)
          } else {
            -1f
          }
          // Progress lands on IO threads; hop back to Main.
          scope.launch { progress = progress + (name to frac) }
        }
      }
      if (r.isFailure) {
        val msg = r.exceptionOrNull()?.message ?: "download failed"
        if (error == "") error = msg
      }
      progress = progress - name
      // Re-read statuses per completion so finished rows flip off
      // "Not downloaded" without waiting for the batch.
      updateStatuses()
    }
  }

  // "Download required" fans out: one coroutine per missing file,
  // each with its own progress bar.
  fun downloadAll(names: List<String>) {
    error = ""
    for (name in names) download(name)
  }

  fun remove(name: String) {
    error = ""
    scope.launch {
      runCatching { manager.delete(name) }
        .onFailure { error = it.message ?: "remove failed" }
      updateStatuses()
    }
  }

  fun clearAll() {
    error = ""
    scope.launch {
      val names = REQUIRED_FILES + OPTIONAL_FILES
      val failures = names.filter { runCatching { manager.delete(it) }.isFailure }
      if (failures.isNotEmpty()) {
        error = "could not remove ${failures.joinToString(", ")}"
      }
      updateStatuses()
    }
  }

  androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

  DictionaryManagementContent(
    statuses = statuses,
    saved = saved,
    latest = manifest.mapValues { it.value.version },
    savedBytes = savedBytes,
    latestBytes = manifest.mapValues { it.value.size },
    latestGz = manifest.mapValues { it.value.gz },
    progress = progress,
    error = error,
    onDownload = ::download,
    onDownloadAll = ::downloadAll,
    onDelete = ::remove,
    onClearAll = ::clearAll,
    onDone = onDone,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DictionaryManagementContent(
  statuses: Map<String, FileStatus>,
  saved: Map<String, String?>,
  latest: Map<String, String?>,
  savedBytes: Map<String, Long?>,
  latestBytes: Map<String, Long>,
  latestGz: Map<String, Long> = emptyMap(),
  progress: Map<String, Float>,
  error: String,
  onDownload: (String) -> Unit,
  onDownloadAll: (List<String>) -> Unit,
  onDelete: (String) -> Unit,
  onClearAll: () -> Unit = {},
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  )
  // Trash taps arm a confirmation dialog; only Remove deletes.
  var confirm by remember { mutableStateOf<String?>(null) }
  var confirmClear by remember { mutableStateOf(false) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  Scaffold(
    topBar = {
      MediumFlexibleTopAppBar(
        title = { Text("Dictionaries") },
        navigationIcon = {
          IconButton(onClick = onDone) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    modifier = modifier.background(MaterialTheme.colorScheme.background)
      .nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { innerPadding ->
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState())
        .padding(innerPadding)
        .padding(16.dp)
        .testTag("dictionariesScreen")
    ) {
      Text(
        "Dictionary files download on request and stay on this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
      )
      DictionaryGroup(
        title = "Required",
        names = REQUIRED_FILES,
        statuses = statuses,
        saved = saved,
        latest = latest,
        savedBytes = savedBytes,
        latestBytes = latestBytes,
        latestGz = latestGz,
        progress = progress,
        colors = colors,
        onDownload = onDownload,
        onDelete = { confirm = it },
      )
      DictionaryGroup(
        title = "Optional",
        names = OPTIONAL_FILES,
        statuses = statuses,
        saved = saved,
        latest = latest,
        savedBytes = savedBytes,
        latestBytes = latestBytes,
        latestGz = latestGz,
        progress = progress,
        colors = colors,
        onDownload = onDownload,
        onDelete = { confirm = it },
      )
      if (error != "") {
        Text(
          error,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      }
      // Without the required trio there is nothing to continue to:
      // offer a single tap that fetches every missing required file.
      val missingRequired = REQUIRED_FILES.filter { statuses[it] == FileStatus.MISSING }
      // Stale files (required or opt-in) refresh with one tap.
      val outdated = (REQUIRED_FILES + OPTIONAL_FILES).filter { statuses[it] == FileStatus.OUTDATED }
      val requiredTotal = missingRequired.map { latestGz[it] ?: -1 }
        .takeIf { totals -> totals.all { it >= 0 } }?.sum()
      val updateTotal = outdated.map { latestGz[it] ?: -1 }
        .takeIf { totals -> totals.all { it >= 0 } }?.sum()
      if (missingRequired.isEmpty()) {
        androidx.compose.foundation.layout.Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(onClick = onDone, modifier = Modifier.testTag("dictionariesDone")) { Text("Continue") }
          if (outdated.isNotEmpty()) {
            val updateLabel =
              if (updateTotal != null) "Update all (${formatBytes(updateTotal)})" else "Update all"
            Button(
              onClick = { onDownloadAll(outdated) },
              enabled = progress.isEmpty(),
              modifier = Modifier.testTag("updateAll"),
            ) { Text(updateLabel) }
          }
        }
      } else {
        val requiredLabel =
          if (requiredTotal != null) "Download required (${formatBytes(requiredTotal)})" else "Download required"
        Button(
          onClick = { onDownloadAll(missingRequired) },
          enabled = progress.isEmpty(),
          modifier = Modifier.testTag("downloadRequired"),
        ) { Text(requiredLabel) }
      }
      // Full reset (opt-ins and required alike; missing required
      // files re-offer download above).
      TextButton(
        onClick = { confirmClear = true },
        enabled = progress.isEmpty(),
        modifier = Modifier.testTag("clearCache"),
      ) { Text("Clear cached files") }
    }
  }
  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text("Clear all downloaded files?") },
      text = {
        Text("Required files download again on request.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            onClearAll()
            confirmClear = false
          },
          modifier = Modifier.testTag("confirmClear"),
        ) { Text("Clear") }
      },
      dismissButton = {
        TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
      },
    )
  }
  confirm?.let { name ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      title = { Text("Remove $name?") },
      text = {
        Text(
          if (REQUIRED_FILES.contains(name)) {
            "Required for search. The file downloads again on request."
          } else {
            "The file downloads again on request."
          }
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDelete(name)
            confirm = null
          },
          modifier = Modifier.testTag("confirmDelete"),
        ) { Text("Remove") }
      },
      dismissButton = {
        TextButton(onClick = { confirm = null }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictionaryGroup(
  title: String,
  names: List<String>,
  statuses: Map<String, FileStatus>,
  saved: Map<String, String?>,
  latest: Map<String, String?>,
  savedBytes: Map<String, Long?>,
  latestBytes: Map<String, Long>,
  latestGz: Map<String, Long>,
  progress: Map<String, Float>,
  colors: androidx.compose.material3.ListItemColors,
  onDownload: (String) -> Unit,
  onDelete: (String) -> Unit,
) {
  Text(
    title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 8.dp),
  )
  SettingsGroup(
    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    modifier = Modifier.padding(bottom = 16.dp),
  ) {
    names.forEachIndexed { index, name ->
      val st = statuses[name]
      SegmentedListItem(
        selected = false,
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index = index, count = names.size),
        supportingContent = {
          Column {
            Text(statusWord(st))
            versionLine("Saved", saved[name], savedBytes[name])?.let {
              Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            // Latest gets its own line, only when it differs from
            // what's saved (version or size).
            if (latest[name] != saved[name] || latestBytes[name] != savedBytes[name]) {
              versionLine("Latest", latest[name], latestBytes[name])?.let {
                Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            val frac = progress[name]
            if (frac != null) {
              if (frac >= 0f) {
                LinearProgressIndicator(
                  progress = { frac },
                  modifier = Modifier.testTag("progress-$name"),
                )
                Text(
                  "${(frac * 100).toInt()}%",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                LoadingIndicator()
              }
            }
          }
        },
        colors = colors,
        trailingContent = {
          androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val downloading = name in progress
            // Wire size on the button, unpacked sizes on the Saved /
            // Latest lines below (mirrors the web manager).
            val wire = latestGz[name]?.let(::formatBytes)
            when (st) {
              FileStatus.MISSING -> Button(
                onClick = { onDownload(name) },
                enabled = !downloading,
                modifier = Modifier.testTag("download-$name"),
              ) { Text(if (wire != null) "Download $wire" else "Download") }
              FileStatus.OUTDATED -> Button(
                onClick = { onDownload(name) },
                enabled = !downloading,
                modifier = Modifier.testTag("download-$name"),
              ) { Text(if (wire != null) "Update $wire" else "Update") }
              FileStatus.CURRENT -> Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.testTag("uptodate-$name"),
              ) { Text("Up to date") }
              null -> Unit
            }
            // Present opt-in files can be removed; required ones
            // have no delete button at all (use Clear cached files
            // below for a full reset). The row then falls back to
            // Download / Download required.
            val removable = (st == FileStatus.CURRENT || st == FileStatus.OUTDATED) &&
              !REQUIRED_FILES.contains(name)
            if (removable) {
              IconButton(
                onClick = { onDelete(name) },
                enabled = !downloading,
                modifier = Modifier.testTag("delete-$name"),
              ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove $name")
              }
            }
          }
        },
      ) {
        Text(name)
      }
    }
  }
}

private fun statusWord(st: FileStatus?): String = when (st) {
  FileStatus.MISSING -> "Not downloaded"
  FileStatus.OUTDATED -> "Update available"
  FileStatus.CURRENT -> "Up to date"
  null -> "Checking…"
}

private fun versionLine(prefix: String, version: String?, bytes: Long?): String? {
  if (version == null && bytes == null) return null
  return "$prefix " + listOfNotNull(version, bytes?.let(::formatBytes)).joinToString(" · ")
}

private fun formatBytes(bytes: Long): String? {
  if (bytes < 0) return null
  return when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
  }
}

@Preview(showBackground = true)
@Composable
private fun DictionaryManagementScreenPreview() {
  BakajitenTheme {
    DictionaryManagementContent(
      statuses = mapOf(
        "jmdict.bin" to FileStatus.CURRENT,
        "kanji.bin" to FileStatus.CURRENT,
        "templates.bin" to FileStatus.OUTDATED,
        "jmnedict.bin" to FileStatus.MISSING,
      ),
      saved = mapOf(
        "jmdict.bin" to "2026-09-01",
        "kanji.bin" to "2026-09-01",
        "templates.bin" to "2026-08-01",
      ),
      latest = mapOf(
        "jmdict.bin" to "2026-09-01",
        "kanji.bin" to "2026-09-01",
        "templates.bin" to "2026-10-01",
        "jmnedict.bin" to "2026-10-01",
      ),
      savedBytes = mapOf(
        "jmdict.bin" to 28_400_000L,
        "kanji.bin" to 6_100_000L,
        "templates.bin" to 900_000L,
      ),
      latestBytes = mapOf(
        "jmdict.bin" to 28_400_000L,
        "kanji.bin" to 6_100_000L,
        "templates.bin" to 950_000L,
        "jmnedict.bin" to 9_800_000L,
      ),
      progress = mapOf("jmnedict.bin" to 0.4f),
      error = "",
      onDownload = {},
      onDownloadAll = {},
      onDelete = {},
      onDone = {},
    )
  }
}
