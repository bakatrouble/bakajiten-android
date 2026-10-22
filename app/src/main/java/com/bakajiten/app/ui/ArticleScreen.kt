package com.bakajiten.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.cannedBackends
import com.bakajiten.app.data.RomajiStyle
import com.bakajiten.app.data.SearchHit
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.components.SenseRow

// Article pane: heading (ruby when the engine segments it),
// common/JLPT badges, numbered senses, other forms, xrefs. Used
// full-screen on phones and as the detail pane on wide screens.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArticleScreen(
    hit: SearchHit,
    query: String,
    backends: Backends,
    onSearch: (String) -> Unit,
    onKanji: (String) -> Unit,
    onBack: () -> Unit,
    meaning: Boolean = false,
    romaji: RomajiStyle = RomajiStyle.KANA,
    modifier: Modifier = Modifier,
) {
    val entry = backends.dict.entryAt(hit.idx)
    val highlight = hit.matched.ifEmpty { query }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(text = "Word article") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("articleBack")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        if (entry == null) {
            Text(
                "Entry unavailable",
                modifier = Modifier
                  .padding(innerPadding)
                  .testTag("articleMissing"),
            )
            return@Scaffold
        }
        // Body on a surface sheet (not the bare background).
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                ArticleSheet(backends, entry, if (meaning) highlight else "", onSearch, onKanji, romaji)
            }
        }
    }
}

@Composable
private fun ArticleSheet(
    backends: Backends,
    entry: com.bakajiten.app.data.DictEntry,
    highlight: String,
    onSearch: (String) -> Unit,
    onKanji: (String) -> Unit,
    romaji: RomajiStyle,
) {
    val rows = backends.displaySenses(entry)
    Column(
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp)
          .testTag("article"),
    ) {
        HeadingBlock(
            heading = backends.preferKeb(entry, highlight),
            reading = backends.displayReading(
                backends.preferReb(entry, highlight),
                romaji != RomajiStyle.KANA,
                romaji == RomajiStyle.HEPBURN,
            ),
            // Ruby segments kana; a romanized reading shows plain.
            ruby = if (romaji == RomajiStyle.KANA) {
                backends.furigana.segment(
                    backends.preferKeb(entry, highlight),
                    backends.preferReb(entry, highlight)
                )
            } else {
                null
            },
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (backends.isCommonWord(entry)) AssistChip(onClick = {}, label = { Text("common") })
            if (entry.jlpt in 1..5) AssistChip(onClick = {}, label = { Text("N${entry.jlpt}") })
            if (rows.firstOrNull()?.sense?.uk == true) {
                AssistChip(
                    onClick = {},
                    label = { Text("kana", color = MaterialTheme.colorScheme.error) })
            }
        }
        Spacer(Modifier.height(8.dp))
        // Long-press anywhere starts text selection (links and badge
        // tooltips keep their taps).
        SelectionContainer {
            Column {
                for (row in rows) {
                    SenseRow(row, highlight, matchRange = backends.matchRange)
                    for (ref in row.sense.xref) {
                        XrefLine(ref, backends.queryFor, onSearch)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        OtherForms(entry.kanji.map { it.keb }, entry.readings.map { it.reb }, backends.queryFor, onSearch)
        KanjiLinks(backends.preferKeb(entry, highlight), onKanji)
    }
}

@Composable
private fun HeadingBlock(
    heading: String,
    reading: String,
    ruby: List<com.bakajiten.app.data.FuriganaSpan>?,
) {
    if (ruby != null) {
        // Ruby columns: base text with the reading above each span.
        Column {
            Row {
                for (span in ruby) {
                    Column {
                        if (span.ruby != "") Text(
                            span.ruby,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(span.text, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Text(
                reading,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        Text(heading, style = MaterialTheme.typography.headlineSmall)
        Text(
            reading,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun XrefLine(ref: String, queryFor: (String) -> String, onSearch: (String) -> Unit) {
    Text(
        "see: $ref",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { onSearch(queryFor(ref)) },
    )
}

@Composable
private fun OtherForms(kanji: List<String>, readings: List<String>, queryFor: (String) -> String, onSearch: (String) -> Unit) {
    if (kanji.size + readings.size <= 2) return
    Text("Other forms:", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (form in (kanji + readings).distinct().take(12)) {
            Text(
                "$form",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                  .clickable { onSearch(queryFor(form)) },
            )
        }
    }
}

@Composable
private fun KanjiLinks(heading: String, onKanji: (String) -> Unit) {
    val chars = heading.toSet().filter { it.code in 0x4E00..0x9FFF }
    if (chars.isEmpty()) return
    Text("Kanji:", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (ch in chars) {
            Text(
                "$ch",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                  .clickable { onKanji(ch.toString()) },
            )
        }
    }
}

@androidx.compose.runtime.Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun ArticleScreenPreview() {
    val fake = com.bakajiten.app.data.FakeDictionaryBackend()
    val backends = cannedBackends(fake)
    val hit = fake.searchIndices("たべる", com.bakajiten.app.data.SearchMode.EXACT, 5).first()
    com.bakajiten.app.theme.BakajitenTheme {
        ArticleScreen(hit, "たべる", backends, onSearch = {}, onKanji = {}, onBack = {})
    }
}
