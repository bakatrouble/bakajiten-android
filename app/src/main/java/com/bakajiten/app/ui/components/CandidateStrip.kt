package com.bakajiten.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.bakajiten.app.data.Candidate
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.StrokePoint
import com.bakajiten.app.theme.BakajitenTheme

// Horizontal candidate strip: one tappable chip per match. Kana
// cells tint like the desktop (katakana blue, hiragana green,
// theme lightness); kanji cells stay default.

private val KATAKANA_SEED = Color(0xFF4285F4)
private val HIRAGANA_SEED = Color(0xFF34A853)

/** Seed with an explicit lightness, hue/saturation kept unless overridden. */
private fun tone(seed: Color, lightness: Float, saturation: Float = -1f): Color {
  val hsl = FloatArray(3)
  ColorUtils.RGBToHSL(
    (seed.red * 255).toInt(),
    (seed.green * 255).toInt(),
    (seed.blue * 255).toInt(),
    hsl,
  )
  if (saturation >= 0f) hsl[1] = saturation
  hsl[2] = lightness.coerceIn(0f, 1f)
  return Color(ColorUtils.HSLToColor(hsl))
}

/** Kana cell colors for the current theme, null for kanji cells. */
@Composable
private fun kanaSpot(character: String, kana: Boolean): Pair<Color, Color>? {
  if (!kana) return null
  val katakana = (character.firstOrNull() ?: return null) in 'ァ'..'ヶ'
  val seed = if (katakana) KATAKANA_SEED else HIRAGANA_SEED
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  return if (dark) {
    Pair(tone(seed, 0.30f), tone(seed, 0.92f, 0.10f))
  } else {
    Pair(tone(seed, 0.88f), tone(seed, 0.12f, 0.15f))
  }
}

// Horizontal candidate strip: one tappable chip per match. Kana
// cells tint like the desktop (katakana blue, hiragana green,
// theme lightness); kanji cells stay default.

@Composable
fun CandidateStrip(
  candidates: List<Candidate>,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier.fillMaxWidth().testTag("candidateStrip"),
    contentAlignment = Alignment.Center,
  ) {
    if (candidates.isEmpty()) {
      // Empty strip keeps its full chip height with a notice.
      Text(
        "No candidates",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(48.dp).wrapContentHeight(Alignment.CenterVertically),
      )
    } else {
      LazyRow(Modifier.fillMaxWidth()) {
        itemsIndexed(candidates, key = { i, c -> "$i:${c.character}" }) { _, c ->
          CandidateChip(c, onPick)
        }
      }
    }
  }
}

@Composable
private fun CandidateChip(c: Candidate, onPick: (String) -> Unit) {
  val spot = kanaSpot(c.character, c.isKana)
  // AssistChip exposes no content padding; a Surface chip keeps
  // the look with tight glyph padding.
  val container = spot?.first ?: Color.Transparent
  val content = spot?.second ?: MaterialTheme.colorScheme.onSurface
  val border = if (spot != null) {
    Color.Transparent
  } else {
    MaterialTheme.colorScheme.outlineVariant
  }
  Surface(
    onClick = { onPick(c.character) },
    shape = RoundedCornerShape(8.dp),
    color = container,
    contentColor = content,
    border = BorderStroke(1.dp, border),
    modifier = Modifier.padding(end = 2.dp).size(48.dp).testTag("candidate${c.character}"),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      Text(
        c.character,
        style = MaterialTheme.typography.titleLarge,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun CandidateStripPreview() {
  val backend = FakeRecognizerBackend()
  val pen = listOf(listOf(StrokePoint(10f, 10f), StrokePoint(200f, 200f)))
  BakajitenTheme {
    CandidateStrip(candidates = backend.recognize(pen, 5, true), onPick = {})
  }
}
