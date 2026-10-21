package com.bakajiten.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.Scope
import com.bakajiten.app.theme.BakajitenTheme

// Search options pop-out: affix/mode icon toggles (a persistent
// top or bottom row that never dismisses the menu), a Dictionaries
// section (not-yet-downloaded scopes grey out and open dictionary
// management on tap), and Settings. Test tags keep e2e stable.

@Composable
fun SearchOptionsMenu(
  prefix: Boolean,
  suffix: Boolean,
  meaning: Boolean,
  onPrefix: (Boolean) -> Unit,
  onSuffix: (Boolean) -> Unit,
  onMeaning: (Boolean) -> Unit,
  scope: Scope,
  onScope: (Scope) -> Unit,
  onSettings: () -> Unit,
  cachedScopes: Set<Scope> = Scope.entries.toSet(),
  onOpenDictionaries: () -> Unit = {},
  affixRowTop: Boolean = true,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  fun dismiss() {
    expanded = false
  }
  // Own Box: the dropdown anchors to it, so the menu opens under
  // the ⋮ button instead of at the app-bar row start.
  Box {
    IconButton(onClick = { expanded = true }, modifier = modifier.testTag("optionsButton")) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Search options")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = ::dismiss) {
    if (affixRowTop) {
      AffixToggleRow(prefix, suffix, meaning, onPrefix, onSuffix, onMeaning)
      HorizontalDivider()
    }
    SectionHeader("Dictionaries")
    for (s in Scope.entries) {
      val label = when (s) {
        Scope.ENGLISH -> "English"
        Scope.NAMES -> "Names"
        Scope.KOKUGO -> "Kokugo"
      }
      val tag = when (s) {
        Scope.ENGLISH -> "scopeEnglish"
        Scope.NAMES -> "scopeNames"
        Scope.KOKUGO -> "scopeKokugo"
      }
      if (s in cachedScopes) {
        DropdownMenuItem(
          text = { Text(label) },
          leadingIcon = { if (scope == s) Icon(Icons.Filled.Check, contentDescription = null) },
          onClick = {
            dismiss()
            onScope(s)
          },
          modifier = Modifier.testTag(tag),
        )
      } else {
        // Greyed out until downloaded; tapping offers the download.
        DropdownMenuItem(
          text = {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
          },
          trailingIcon = {
            Icon(
              Icons.Filled.Download,
              contentDescription = "Download $label",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          onClick = {
            dismiss()
            onOpenDictionaries()
          },
          modifier = Modifier.testTag(tag),
        )
      }
    }
    HorizontalDivider()
    DropdownMenuItem(
      text = { Text("Settings") },
      leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
      onClick = {
        dismiss()
        onSettings()
      },
      modifier = Modifier.testTag("settingsButton"),
    )
    if (!affixRowTop) {
      HorizontalDivider()
      AffixToggleRow(prefix, suffix, meaning, onPrefix, onSuffix, onMeaning)
    }
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
  )
}

@Preview(showBackground = true)
@Composable
private fun SearchOptionsMenuPreview() {
  BakajitenTheme {
    SearchOptionsMenu(
      prefix = true,
      suffix = false,
      meaning = false,
      onPrefix = {},
      onSuffix = {},
      onMeaning = {},
      scope = Scope.ENGLISH,
      onScope = {},
      onSettings = {},
      cachedScopes = setOf(Scope.ENGLISH),
    )
  }
}
