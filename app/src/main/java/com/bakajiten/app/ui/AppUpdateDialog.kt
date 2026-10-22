package com.bakajiten.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.AppRelease
import com.bakajiten.app.data.UpdateUiState
import com.bakajiten.app.theme.BakajitenTheme

// Update prompt (mirrors the Qt UpdatePrompt): version + release
// notes, a do-not-notify checkbox, Download/Later; download
// progress inline; manual-check outcomes as a notice dialog. The
// checkbox arms a skip that only applies on Download/Later —
// dismissing the notice never persists anything.

// Dialog visibility lives here so previews and tests drive it
// without the updater.
fun updateDialogVisible(state: UpdateUiState): Boolean =
  state.release != null || state.noArtifact != null || state.notice != null

@Composable
fun AppUpdateDialog(
  state: UpdateUiState,
  onDownload: (skip: Boolean) -> Unit,
  onLater: (skip: Boolean) -> Unit,
  onCloseNotice: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (state.release != null) {
    ReleasePrompt(
      title = "bakajiten ${state.release.version} is available (you have ${state.currentVersion}).",
      notes = state.release.notes,
      downloading = state.downloading,
      error = state.error,
      confirmLabel = "Download",
      onConfirm = onDownload,
      onDismiss = onLater,
      modifier = modifier,
    )
  } else if (state.noArtifact != null) {
    ReleasePrompt(
      title = "bakajiten ${state.noArtifact.first} is available, but it has no build for this device yet.",
      notes = state.noArtifact.second,
      downloading = null,
      error = "",
      confirmLabel = "Close",
      onConfirm = onLater,
      onDismiss = onLater,
      modifier = modifier,
    )
  } else if (state.notice != null) {
    AlertDialog(
      onDismissRequest = onCloseNotice,
      title = { Text("App updates") },
      text = { Text(state.notice) },
      confirmButton = {
        TextButton(onClick = onCloseNotice, modifier = Modifier.testTag("updateClose")) {
          Text("Close")
        }
      },
      modifier = modifier.testTag("updateDialog"),
    )
  }
}

@Composable
private fun ReleasePrompt(
  title: String,
  notes: String,
  downloading: Float?,
  error: String,
  confirmLabel: String,
  onConfirm: (skip: Boolean) -> Unit,
  onDismiss: (skip: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var skip by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (downloading == null) onDismiss(skip) },
    title = { Text(title) },
    text = {
      Column {
        if (notes.isNotEmpty()) {
          Text(
            notes,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
              .padding(bottom = 8.dp),
          )
        }
        if (downloading != null) {
          val frac = downloading.coerceIn(0f, 1f)
          LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().testTag("updateProgress"),
          )
          Text(
            if (downloading < 0) "Downloading…" else "${(frac * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
        if (error != "") {
          Text(error, color = MaterialTheme.colorScheme.error)
        }
        ListItem(
          headlineContent = { Text("Do not notify about this version") },
          trailingContent = {
            Checkbox(
              checked = skip,
              onCheckedChange = { skip = it },
              modifier = Modifier.testTag("updateSkip"),
            )
          },
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(skip) },
        enabled = downloading == null,
        modifier = Modifier.testTag("updateDownload"),
      ) { Text(confirmLabel) }
    },
    dismissButton = {
      TextButton(
        onClick = { onDismiss(skip) },
        enabled = downloading == null,
        modifier = Modifier.testTag("updateLater"),
      ) { Text("Later") }
    },
    modifier = modifier.testTag("updateDialog"),
  )
}

@Preview(showBackground = true)
@Composable
private fun AppUpdateDialogPreview() {
  BakajitenTheme {
    AppUpdateContentPreview()
  }
}

@Composable
private fun AppUpdateContentPreview() {
  AppUpdateDialog(
    state = UpdateUiState(
      currentVersion = "0.1.0",
      release = AppRelease(
        version = "0.2.0",
        notes = "New recognizer model and faster startup.",
        assetName = "bakajiten-android-v0.2.0.apk",
        downloadUrl = "https://example.invalid/bakajiten.apk",
        sizeBytes = 41943040,
      ),
    ),
    onDownload = {},
    onLater = {},
    onCloseNotice = {},
  )
}
