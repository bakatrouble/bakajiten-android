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
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.KanjiEntry
import com.bakajiten.app.domain.codepointLabel
import com.bakajiten.app.domain.jisHex
import com.bakajiten.app.domain.kanjiGradeKind
import com.bakajiten.app.domain.kanjiRadicalGlyph
import com.bakajiten.app.theme.BakajitenTheme

// Kanji detail table: every field always shown, `-` when the source
// carries none (desktop KanjiDialog order).

private fun dash(values: List<String>, sep: String = ", "): String =
  if (values.isNotEmpty()) values.joinToString(sep) else "-"

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

@Composable
fun KanjiInfoTable(entry: KanjiEntry, modifier: Modifier = Modifier) {
  val grade = entry.grade?.let { "${kanjiGradeKind(it)} $it" } ?: "-"
  val radical = entry.radical?.let { "${kanjiRadicalGlyph(it)} ($it)" } ?: "-"
  val skip = entry.skip.joinToString(", ")
    .let { if (entry.skipMisgrade && it != "") "$it (miscount)" else it }
  Column(modifier) {
    KanjiInfoRow("Meaning", dash(entry.meanings, "; "))
    KanjiInfoRow("ON", dash(entry.on, "、"))
    KanjiInfoRow("Kun", dash(entry.kun, "、"))
    KanjiInfoRow("Nanori", dash(entry.nanori, "、"))
    KanjiInfoRow("Chinese", dash(entry.chinese))
    KanjiInfoRow("Korean", dash(entry.korean))
    KanjiInfoRow("Vietnamese", dash(entry.vietnamese))
    KanjiInfoRow("Strokes", entry.strokes.toString())
    KanjiInfoRow("Grade", grade)
    KanjiInfoRow("JLPT", entry.jlpt?.let { "N$it" } ?: "-")
    KanjiInfoRow("Frequency", entry.freq?.toString() ?: "-")
    KanjiInfoRow("Radical", radical)
    KanjiInfoRow("Radical names", dash(entry.radNames))
    KanjiInfoRow("Unicode", codepointLabel(entry.character))
    KanjiInfoRow("JIS X0208", entry.jis208?.let { "${it.ku}-${it.ten}" } ?: "-")
    KanjiInfoRow("JIS X0212", dash(entry.jis212))
    KanjiInfoRow("JIS X0213", dash(entry.jis213))
    KanjiInfoRow("JIS X0208 refs", dash(entry.jis208Refs))
    KanjiInfoRow("SKIP", skip.ifEmpty { "-" })
    KanjiInfoRow("Four corner", dash(entry.fourCorner))
    KanjiInfoRow("De Roo", dash(entry.deroo))
    KanjiInfoRow("SH", dash(entry.shDesc))
    KanjiInfoRow("Nelson", dash(entry.nelsonC))
    KanjiInfoRow("NJECD", dash(entry.njecd))
    KanjiInfoRow("O'Neill", dash(entry.oneill))
    KanjiInfoRow("De Roo refs", dash(entry.derooVariant))
    KanjiInfoRow("SH refs", dash(entry.sH))
  }
}

@Preview(showBackground = true)
@Composable
private fun KanjiInfoTablePreview() {
  BakajitenTheme {
    KanjiInfoTable(entry = FakeKanjiBackend().lookup("日")!!, modifier = Modifier.padding(16.dp))
  }
}
