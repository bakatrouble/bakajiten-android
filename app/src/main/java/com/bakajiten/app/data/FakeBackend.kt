package com.bakajiten.app.data

// In-memory backend for JVM unit tests, headless e2e, previews,
// and dev runs without packed data. Toy records mirror the web
// backend tests: たべる/食べる (v1 "to eat"), あの人 (arch sense),
// and a kana-only entry. Tag indices are REAL table indices
// (v1=31, arch=1, uk=47) so the Rust shaper resolves them exactly
// like packed data — the fake owns no label tables anymore.

private fun sense(
  pos: List<Int> = emptyList(),
  misc: List<Int> = emptyList(),
  gloss: String,
): DictSense = DictSense(
  pos = pos,
  field = emptyList(),
  misc = misc,
  glosses = listOf(gloss),
  xref = emptyList(),
  ant = emptyList(),
  stagk = emptyList(),
  stagr = emptyList(),
  info = emptyList(),
  sources = emptyList(),
)

private fun taberuEntry() = DictEntry(
  heading = "食べる",
  reading = "たべる",
  senses = listOf(
    sense(pos = listOf(31), gloss = "to eat"),
    sense(pos = listOf(31), gloss = "to live on"),
  ),
  kanji = listOf(DictKanjiElement("食べる", emptyList(), listOf("ichi1"))),
  readings = listOf(DictReadingElement("たべる", false, emptyList(), emptyList(), listOf("ichi1"))),
  isName = false,
  entSeq = 1_000_100,
  jlpt = 5,
)

private fun anoHitoEntry() = DictEntry(
  heading = "あの人",
  reading = "あのひと",
  senses = listOf(
    sense(gloss = "he"),
    sense(misc = listOf(1), gloss = "you"),
  ),
  kanji = emptyList(),
  readings = listOf(DictReadingElement("あのひと", false, emptyList(), emptyList(), emptyList())),
  isName = false,
  entSeq = 1_000_200,
  jlpt = 0,
)

private fun areEntry() = DictEntry(
  heading = "あれ",
  reading = "あれ",
  senses = listOf(sense(misc = listOf(47), gloss = "that (far from speaker and listener)")),
  kanji = emptyList(),
  readings = listOf(DictReadingElement("あれ", true, emptyList(), emptyList(), emptyList())),
  isName = false,
  entSeq = 1_000_300,
  jlpt = 5,
)

class FakeDictionaryBackend : DictionaryBackend {
  private val entries = listOf(taberuEntry(), anoHitoEntry(), areEntry())

  private fun keys(e: DictEntry): List<String> =
    listOf(e.heading, e.reading) + e.kanji.map { it.keb } + e.readings.map { it.reb }

  override fun searchIndices(query: String, mode: SearchMode, cap: Int): List<SearchHit> {
    if (query == "" || cap == 0) return emptyList()
    val hits = entries.mapIndexedNotNull { i, e ->
      val matched = when (mode) {
        SearchMode.EXACT -> keys(e).any { it == query }
        SearchMode.PREFIX -> keys(e).any { it.startsWith(query) }
        SearchMode.SUFFIX -> keys(e).any { it.endsWith(query) }
        SearchMode.MIDDLE -> keys(e).any { it.contains(query) }
      }
      if (matched) SearchHit(i, emptyList(), "") else null
    }
    return hits.take(cap)
  }

  override fun searchMeaningIndices(query: String, cap: Int): List<SearchHit> {
    if (query == "" || cap == 0) return emptyList()
    val q = query.lowercase()
    return entries.mapIndexedNotNull { i, e ->
      if (e.senses.any { s -> s.glosses.any { it.lowercase().contains(q) } }) {
        SearchHit(i, emptyList(), "")
      } else {
        null
      }
    }.take(cap)
  }

  override fun entryAt(idx: Int): DictEntry? = entries.getOrNull(idx)

  override fun count(): Long = entries.size.toLong()

  override fun close() = Unit
}

class FakeKanjiBackend : KanjiBackend {
  override fun lookup(character: String): KanjiEntry? =
    if (character == "日") {
      KanjiEntry(
        character = "日",
        on = listOf("ニチ", "ジツ"),
        kun = listOf("ひ", "か"),
        meanings = listOf("day", "sun"),
        grade = 1,
        jlpt = 5,
        freq = 1,
        strokes = 4,
        radical = 72,
        parts = listOf("日"),
        nanori = listOf("あき"),
        radNames = listOf("ひ"),
        chinese = listOf("ri4"),
        korean = listOf("il"),
        jis208 = JisKuten(16, 1),
        skip = listOf("4-4-3"),
      )
    } else {
      null
    }

  override fun strokes(character: String): List<KanjiStroke> =
    if (character == "日") {
      listOf(
        KanjiStroke(listOf(KanjiPoint(20f, 40f), KanjiPoint(235f, 40f))),
        KanjiStroke(listOf(KanjiPoint(60f, 10f), KanjiPoint(60f, 120f))),
        KanjiStroke(listOf(KanjiPoint(60f, 120f), KanjiPoint(200f, 120f))),
        KanjiStroke(listOf(KanjiPoint(120f, 60f), KanjiPoint(120f, 220f))),
      )
    } else {
      emptyList()
    }

  override fun containing(part: String): List<String> =
    if (part == "日") listOf("日", "曜", "明") else emptyList()
}

class FakeRecognizerBackend : RecognizerBackend {
  override fun recognize(
    strokes: List<List<StrokePoint>>,
    topK: Int,
    includeKana: Boolean,
  ): List<Candidate> {
    if (strokes.isEmpty()) return emptyList()
    val all = buildList {
      if (includeKana) {
        add(Candidate("し", 100, true))
        add(Candidate("つ", 200, true))
      }
      add(Candidate("日", 300, false))
      add(Candidate("目", 400, false))
    }
    return if (topK == 0) all else all.take(topK)
  }

  override fun templateCount(): Long = 4

  override fun similar(strokes: List<List<StrokePoint>>, exclude: String, topK: Int): List<Candidate> {
    if (strokes.isEmpty()) return emptyList()
    val all = listOf(Candidate("目", 100, false), Candidate("白", 200, false))
      .filter { it.character != exclude }
    return if (topK == 0) all else all.take(topK)
  }
}

/** Fake furigana: no ruby (article renders the plain heading). */
class FakeFuriganaBackend : FuriganaBackend {
  override fun segment(head: String, reading: String): List<FuriganaSpan>? = null
}
