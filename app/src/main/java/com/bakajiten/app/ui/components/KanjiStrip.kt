package com.bakajiten.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.expressive.SettingsGroup
import com.bakajiten.app.theme.BakajitenTheme

// Kanji composition group (Similar / Parts / Part of): one
// segmented row per character, settings-screen style. Always
// expanded.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KanjiStrip(
  title: String,
  chars: List<String>,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  )
  Column(modifier.fillMaxWidth()) {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 8.dp),
    )
    SettingsGroup(
      verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
      if (chars.isEmpty()) {
        SegmentedListItem(
          selected = false,
          onClick = {},
          shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
          colors = colors,
        ) {
          Text(
            "—",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        chars.forEachIndexed { i, ch ->
          SegmentedListItem(
            selected = false,
            onClick = { onPick(ch) },
            shapes = ListItemDefaults.segmentedShapes(index = i, count = chars.size),
            colors = colors,
          ) {
            Text(ch, style = MaterialTheme.typography.titleLarge)
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun KanjiStripPreview() {
  BakajitenTheme {
    KanjiStrip(
      title = "Similar",
      chars = listOf("目", "白", "百"),
      onPick = {},
    )
  }
}
