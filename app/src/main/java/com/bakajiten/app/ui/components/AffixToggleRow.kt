package com.bakajiten.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import com.bakajiten.app.theme.BakajitenTheme

// Affix + mode toggles as icon buttons (Qt/Web icon set): prefix,
// suffix, and the Japanese/meaning switch (kana vs latin glyph).
// Toggles act in place and never dismiss the options menu.

@Composable
private fun AffixToggleButton(
  on: Boolean,
  onToggle: () -> Unit,
  icon: ImageVector,
  description: String,
  tag: String,
) {
  if (on) {
    FilledIconButton(onClick = onToggle, modifier = Modifier.testTag(tag)) {
      Icon(icon, contentDescription = description)
    }
  } else {
    IconButton(onClick = onToggle, modifier = Modifier.testTag(tag)) {
      Icon(icon, contentDescription = description)
    }
  }
}

@Composable
fun AffixToggleRow(
  prefix: Boolean,
  suffix: Boolean,
  meaning: Boolean,
  onPrefix: (Boolean) -> Unit,
  onSuffix: (Boolean) -> Unit,
  onMeaning: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    AffixToggleButton(
      on = suffix,
      onToggle = { onSuffix(!suffix) },
      icon = ImageVector.vectorResource(com.bakajiten.app.R.drawable.ic_affix_suffix),
      description = "Suffix match",
      tag = "suffixChip",
    )
    AffixToggleButton(
      on = prefix,
      onToggle = { onPrefix(!prefix) },
      icon = ImageVector.vectorResource(com.bakajiten.app.R.drawable.ic_affix_prefix),
      description = "Prefix match",
      tag = "prefixChip",
    )
    AffixToggleButton(
      on = !meaning,
      onToggle = { onMeaning(false) },
      icon = ImageVector.vectorResource(com.bakajiten.app.R.drawable.ic_mode_kana),
      description = "Japanese",
      tag = "kanaChip",
    )
    AffixToggleButton(
      on = meaning,
      onToggle = { onMeaning(true) },
      icon = ImageVector.vectorResource(com.bakajiten.app.R.drawable.ic_mode_latin),
      description = "English meanings",
      tag = "meaningChip",
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun AffixToggleRowPreview() {
  BakajitenTheme {
    AffixToggleRow(
      prefix = true,
      suffix = false,
      meaning = false,
      onPrefix = {},
      onSuffix = {},
      onMeaning = {},
    )
  }
}
