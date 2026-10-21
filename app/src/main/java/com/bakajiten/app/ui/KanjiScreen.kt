package com.bakajiten.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.RomajiStyle
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.data.StrokePoint
import com.bakajiten.app.domain.kanjiRadicalGlyph
import com.bakajiten.app.state.Backends
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.KanjiInfoTable
import com.bakajiten.app.ui.components.KanjiStrip
import com.bakajiten.app.ui.components.StrokePlayer

// Kanji detail screen (web dialog content): Info tab (stroke
// player + detail table) and Words tab (shared results list), with
// togglable Similar / Parts / Part-of strips below. Strip
// visibility persists in settings.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KanjiScreen(
  character: String,
  backends: Backends,
  onSearch: (String) -> Unit,
  onBack: () -> Unit,
  onKanji: (String) -> Unit = {},
  romaji: RomajiStyle = RomajiStyle.KANA,
  modifier: Modifier = Modifier,
) {
  val entry = backends.kanji.lookup(character)
  val strokes = backends.kanji.strokes(character)
  val partOf = backends.kanji.containing(character)
  // Look-alikes come from the recognizer bank; missing templates
  // (or an empty bank) simply yield no strip.
  val similar = remember(character) {
    runCatching {
      backends.recognizer.similar(
        strokes.map { s -> s.points.map { StrokePoint(it.x, it.y) } },
        character,
        12,
      ).map { it.character }
    }.getOrDefault(emptyList())
  }
  var tab by remember(character) { mutableIntStateOf(0) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  // Title stays "Kanji information" (Qt dialog); stroke count
  // and radical (%2 = number + glyph, "-" when none) go in the
  // subtitle. Bare character title when there is no entry.
  val radicalNumber = entry?.radical?.takeIf { it > 0 }
  val radical = if (radicalNumber != null) {
    "$radicalNumber ${kanjiRadicalGlyph(radicalNumber)}"
  } else {
    "-"
  }
  Scaffold(
    topBar = {
      MediumFlexibleTopAppBar(
        title = { Text(if (entry != null) "Kanji information" else character) },
        subtitle = {
          if (entry != null) {
            Text("Stroke count: ${entry.strokes} Radical: $radical")
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("kanjiBack")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  ) { innerPadding ->
    Column(
      Modifier.fillMaxSize().padding(innerPadding).testTag("kanji"),
    ) {
      PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.testTag("kanjiTabs")) {
        Tab(
          selected = tab == 0,
          onClick = { tab = 0 },
          text = { Text("Info") },
          modifier = Modifier.testTag("kanjiTabInfo"),
        )
        Tab(
          selected = tab == 1,
          onClick = { tab = 1 },
          text = { Text("Words") },
          modifier = Modifier.testTag("kanjiTabWords"),
        )
        Tab(
          selected = tab == 2,
          onClick = { tab = 2 },
          text = { Text("Composition") },
          modifier = Modifier.testTag("kanjiTabComposition"),
        )
      }
        when (tab) {
            0 -> {
                // Detail content on a surface sheet (not the bare
                // background), matching the article pane.
                Column(Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        ) {
                            if (entry == null) {
                                Text("No data for this character")
                            } else {
                                if (strokes.isNotEmpty()) StrokePlayer(strokes)
                                KanjiInfoTable(entry)
                            }
                        }
                    }
                }
            }
            1 -> {
                val words = remember(character) {
                    backends.dict.searchIndices(character, SearchMode.MIDDLE, 30)
                }
                ResultsList(
                    hits = words,
                    query = character,
                    backends = backends,
                    onOpen = { i -> words.getOrNull(i)?.let { backends.dict.entryAt(it.idx)?.heading }?.let(onSearch) },
                    modifier = Modifier.weight(1f),
                    romaji = romaji,
                )
            }
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KanjiStrip(title = "Similar", chars = similar, onPick = onKanji)
                    KanjiStrip(
                        title = "Parts",
                        chars = entry?.parts ?: emptyList(),
                        onPick = onKanji,
                    )
                    KanjiStrip(title = "Part of", chars = partOf, onPick = onKanji)
                }
            }
        }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun KanjiScreenPreview() {
  val fake = FakeDictionaryBackend()
  val backends = Backends(
    fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    fake::posLabel, fake::posTip, { "" }, { "" }, fake::miscLabel, fake::miscTip,
  )
  BakajitenTheme {
    KanjiScreen(character = "日", backends = backends, onSearch = {}, onBack = {})
  }
}
