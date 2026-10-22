package com.bakajiten.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.expressive.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsRadioButton
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.RomajiStyle
import com.bakajiten.app.data.SearchBarPosition
import com.bakajiten.app.data.Theme
import com.bakajiten.app.data.UpdateUiState
import com.bakajiten.app.theme.BakajitenTheme

// Settings apply instantly (no Apply/Cancel), like the web
// frontend: expressive tiles in one segmented connected list,
// ending in the scale slider row. Single-selects drill into radio
// dialogs; the slider previews locally and commits on release.
// Dictionary choice lives in the search options menu, kana matching
// in the recognizer panel; downloads/updates live on the
// dictionary-management screen linked below.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
  settings: AppSettings,
  onSettings: (AppSettings) -> Unit,
  onBack: () -> Unit,
  onDictionaries: () -> Unit,
  update: UpdateUiState = UpdateUiState(),
  onCheckUpdates: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  // The dpi slider previews locally and commits on release; its
  // range tracks the system density (±120 dpi). Null means system.
  // Read once per mount: re-keying would snap a later drag back
  // when the async settings write lands.
  val systemDpi = (LocalDensity.current.density * 160).toInt()
  val effectiveDpi = settings.densityDpi ?: systemDpi
  val range = ((systemDpi - 120).toFloat())..((systemDpi + 120).toFloat())
  var sliderDpi by remember {
    mutableFloatStateOf(effectiveDpi.toFloat().coerceIn(range.start, range.endInclusive))
  }
  val colors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  )
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  Scaffold(
    topBar = {
      MediumFlexibleTopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
      .nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { innerPadding ->
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState())
        .padding(innerPadding)
        .padding(16.dp)
    ) {
      SettingsGroup(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.padding(bottom = 16.dp).testTag("settings"),
      ) {
        SettingsSelectRow(
          title = "Theme",
          options = listOf(Theme.AUTO, Theme.LIGHT, Theme.DARK),
          selected = settings.theme,
          onSelect = { onSettings(settings.copy(theme = it)) },
          label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
          tag = { "theme${it.name}" },
          rowTag = "themeRow",
          index = 0,
          count = 4,
          colors = colors,
        )
        SettingsSelectRow(
          title = "Search bar position",
          options = listOf(SearchBarPosition.TOP, SearchBarPosition.BOTTOM),
          selected = settings.searchBarPosition,
          onSelect = { onSettings(settings.copy(searchBarPosition = it)) },
          label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
          tag = { "position${it.name}" },
          rowTag = "positionRow",
          index = 1,
          count = 4,
          colors = colors,
        )
        SettingsSelectRow(
          title = "Reading display",
          options = listOf(RomajiStyle.KANA, RomajiStyle.HEPBURN, RomajiStyle.KUNREI),
          selected = settings.romaji,
          onSelect = { onSettings(settings.copy(romaji = it)) },
          label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
          tag = { "romaji${it.name}" },
          rowTag = "romajiRow",
          index = 2,
          count = 4,
          colors = colors,
        )
        SegmentedListItem(
          selected = false,
          onClick = {},
          shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
          supportingContent = {
            Column {
              Slider(
                value = sliderDpi,
                onValueChange = { sliderDpi = it },
                onValueChangeFinished = {
                  onSettings(settings.copy(densityDpi = sliderDpi.toInt().coerceIn(120, 640)))
                },
                valueRange = range,
                steps = 5,
              )
              if (settings.densityDpi != null) {
                TextButton(onClick = { onSettings(settings.copy(densityDpi = null)) }) {
                  Text("Reset to system default")
                }
              }
            }
          },
          colors = colors,
          modifier = Modifier.testTag("scaleSlider"),
        ) {
          Text(
            if (settings.densityDpi != null) "Interface scale (${sliderDpi.toInt()} dpi)"
            else "Interface scale (system, $systemDpi dpi)",
          )
        }
      }
      SettingsGroup(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.padding(bottom = 16.dp),
      ) {
        SettingsMenuLink(
          title = { Text("Dictionaries") },
          subtitle = { Text("Download and update dictionary files") },
          colors = colors,
          shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
          action = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
          },
          onClick = onDictionaries,
          modifier = Modifier.testTag("dictionariesRow"),
        )
      }
      SettingsGroup(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.padding(bottom = 16.dp),
      ) {
        SettingsToggleRow(
          title = "Check for updates automatically",
          checked = settings.checkAppUpdates,
          onChecked = { onSettings(settings.copy(checkAppUpdates = it)) },
          tag = "autoUpdate",
          rowTag = "autoUpdateRow",
          index = 0,
          count = 2,
          colors = colors,
        )
        SettingsMenuLink(
          title = { Text("Check for app updates") },
          subtitle = {
            Text(if (update.checking) "Checking…" else updateSubtitle(update))
          },
          colors = colors,
          shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
          action = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
          },
          onClick = onCheckUpdates,
          modifier = Modifier.testTag("checkUpdatesRow"),
        )
      }
    }
  }
}

// Status line under the manual check row: what the last check
// found (empty until the first manual check reports).
private fun updateSubtitle(update: UpdateUiState): String = when {
  update.release != null -> "bakajiten ${update.release.version} is available"
  update.seenVersion != null -> "bakajiten ${update.seenVersion} is available"
  update.notice != null -> update.notice
  else -> "Up to date"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsToggleRow(
  title: String,
  checked: Boolean,
  onChecked: (Boolean) -> Unit,
  tag: String,
  rowTag: String,
  index: Int,
  count: Int,
  colors: ListItemColors,
) {
  SegmentedListItem(
    onClick = { onChecked(!checked) },
    shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
    trailingContent = {
      Switch(
        checked = checked,
        onCheckedChange = onChecked,
        modifier = Modifier.testTag(tag),
      )
    },
    colors = colors,
    modifier = Modifier.testTag(rowTag),
  ) {
    Text(title)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SettingsSelectRow(
  title: String,
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  label: (T) -> String,
  tag: (T) -> String,
  rowTag: String,
  index: Int,
  count: Int,
  colors: ListItemColors,
) {
  var open by remember { mutableStateOf(false) }
  SettingsMenuLink(
    title = { Text(title) },
    subtitle = { Text(label(selected)) },
    colors = colors,
    shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
    action = {
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    },
    onClick = { open = true },
    modifier = Modifier.testTag(rowTag),
  )
  if (open) {
    AlertDialog(
      onDismissRequest = { open = false },
      title = { Text(title) },
      text = {
        Column {
          for (o in options) {
            SettingsRadioButton(
              state = o == selected,
              title = { Text(label(o)) },
              onClick = {
                onSelect(o)
                open = false
              },
              modifier = Modifier.testTag(tag(o)),
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { open = false }) { Text("Cancel") }
      },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
  BakajitenTheme {
    SettingsScreen(settings = AppSettings(searchBarPosition = SearchBarPosition.BOTTOM), onSettings = {}, onBack = {}, onDictionaries = {})
  }
}
