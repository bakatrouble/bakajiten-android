package com.bakajiten.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.CannedShaping
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.theme.BakajitenTheme
import uniffi.dict.KanjiDetailRow

// Kanji detail table: one row per Rust-compiled detail
// (lib/libdict kanji_details), labels as provided (Qt maps the
// keys to translated captions instead).

@Composable
private fun KanjiInfoRow(label: String, value: String) {
  Row(Modifier.padding(vertical = 1.dp)) {
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(0.35f),
    )
    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
  }
}

/** `U+65E5` form for a single character. Pure formatting (no engine counterpart). */
internal fun codepointLabel(character: String): String {
  val cp = character.codePointAt(0)
  return "U+" + cp.toString(16).uppercase().padStart(4, '0')
}

@Composable
fun KanjiInfoTable(
  rows: List<KanjiDetailRow>,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    rows.forEach { KanjiInfoRow(it.label, it.value) }
  }
}

@Preview(showBackground = true)
@Composable
private fun KanjiInfoTablePreview() {
  BakajitenTheme {
    KanjiInfoTable(
      rows = CannedShaping.kanjiDetails(FakeKanjiBackend().lookup("日")!!),
      modifier = Modifier.padding(16.dp),
    )
  }
}
