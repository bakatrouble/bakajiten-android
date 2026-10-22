package com.bakajiten.app.data

import com.bakajiten.app.domain.NumberedSense
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.TagKind
import com.bakajiten.app.state.Backends

// Hand-written expected shaping for the three toy entries, mirroring
// current engine output. Previews only: Android Studio cannot load
// the host cdylib, so preview composition must never touch JNA.
// Tests exercise the real Rust functions through the Backends
// defaults instead. These are fixtures, not logic — update them if
// the toy entries change.
object CannedShaping {
  fun displaySenses(entry: DictEntry): List<NumberedSense> = when (entry.heading) {
    "食べる" -> listOf(
      NumberedSense(
        SenseDisplay(
          badges = listOf("v1"),
          tips = listOf("Ichidan verb"),
          kinds = listOf(TagKind.POS),
          glosses = listOf("to eat"),
          xref = emptyList(),
          ant = emptyList(),
          info = emptyList(),
          sources = emptyList(),
          restrictedKanji = emptyList(),
          restrictedReading = emptyList(),
          uk = false,
        ),
        1,
      ),
      NumberedSense(
        SenseDisplay(
          badges = listOf("v1"),
          tips = listOf("Ichidan verb"),
          kinds = listOf(TagKind.POS),
          glosses = listOf("to live on"),
          xref = emptyList(),
          ant = emptyList(),
          info = emptyList(),
          sources = emptyList(),
          restrictedKanji = emptyList(),
          restrictedReading = emptyList(),
          uk = false,
        ),
        2,
      ),
    )
    "あの人" -> listOf(
      NumberedSense(
        SenseDisplay(
          badges = emptyList(),
          tips = emptyList(),
          kinds = emptyList(),
          glosses = listOf("he"),
          xref = emptyList(),
          ant = emptyList(),
          info = emptyList(),
          sources = emptyList(),
          restrictedKanji = emptyList(),
          restrictedReading = emptyList(),
          uk = false,
        ),
        1,
      ),
      NumberedSense(
        SenseDisplay(
          badges = listOf("arch"),
          tips = listOf("archaic"),
          kinds = listOf(TagKind.MISC),
          glosses = listOf("you"),
          xref = emptyList(),
          ant = emptyList(),
          info = emptyList(),
          sources = emptyList(),
          restrictedKanji = emptyList(),
          restrictedReading = emptyList(),
          uk = false,
        ),
        2,
      ),
    )
    "あれ" -> listOf(
      NumberedSense(
        SenseDisplay(
          badges = listOf("kana"),
          tips = listOf("word usually written using kana alone"),
          kinds = listOf(TagKind.MISC),
          glosses = listOf("that (far from speaker and listener)"),
          xref = emptyList(),
          ant = emptyList(),
          info = emptyList(),
          sources = emptyList(),
          restrictedKanji = emptyList(),
          restrictedReading = emptyList(),
          uk = true,
        ),
        1,
      ),
    )
    else -> emptyList()
  }

  // Trivial stdlib matching for preview highlight (same UTF-16
  // semantics as the engine for this purpose).
  fun matchRange(text: String, query: String): IntRange? {
    if (query.isEmpty()) return null
    val at = text.indexOf(query)
    return if (at < 0) null else IntRange(at, at + query.length - 1)
  }

  // Preview entries always show their default heading/reading.
  fun preferKeb(entry: DictEntry, query: String): String = entry.heading

  fun preferReb(entry: DictEntry, query: String): String = entry.reading

  // Toy entries carry no xrefs; identity is exact here.
  fun queryFor(term: String): String = term

  fun isCommonWord(entry: DictEntry): Boolean =
    (entry.kanji.flatMap { it.priorities } + entry.readings.flatMap { it.priorities })
      .any { it == "news1" || it == "news2" || it == "ichi1" || it == "ichi2" }

  // Preview kanji (日 only): canned to avoid JNA in Studio.

  /** Preview radical glyph (日 only): mirrors engine output. */
  fun kanjiRadicalGlyph(number: Int): String = if (number == 72) "日" else ""

  /** Full canned detail table for 日 (mirrors engine output). */
  fun kanjiDetails(entry: KanjiEntry): List<uniffi.dict.KanjiDetailRow> = kanjiDetails()

  fun kanjiDetails(): List<uniffi.dict.KanjiDetailRow> {
    fun row(key: uniffi.dict.KanjiDetailKey, label: String, value: String) =
      uniffi.dict.KanjiDetailRow(key, label, value)
    return listOf(
      row(uniffi.dict.KanjiDetailKey.MEANING, "Meaning", "day; sun"),
      row(uniffi.dict.KanjiDetailKey.ON, "ON", "ニチ、ジツ"),
      row(uniffi.dict.KanjiDetailKey.KUN, "Kun", "ひ、か"),
      row(uniffi.dict.KanjiDetailKey.NANORI, "Nanori", "あき"),
      row(uniffi.dict.KanjiDetailKey.CHINESE, "Chinese", "rì"),
      row(uniffi.dict.KanjiDetailKey.KOREAN, "Korean", "il"),
      row(uniffi.dict.KanjiDetailKey.VIETNAMESE, "Vietnamese", "-"),
      row(uniffi.dict.KanjiDetailKey.STROKES, "Strokes", "4"),
      row(uniffi.dict.KanjiDetailKey.GRADE, "Grade", "Kyouiku 1"),
      row(uniffi.dict.KanjiDetailKey.JLPT, "JLPT", "N5"),
      row(uniffi.dict.KanjiDetailKey.FREQUENCY, "Frequency", "1"),
      row(uniffi.dict.KanjiDetailKey.RADICAL, "Radical", "日 (72)"),
      row(uniffi.dict.KanjiDetailKey.RADICAL_NAMES, "Radical names", "ひ"),
      row(uniffi.dict.KanjiDetailKey.UNICODE, "Unicode", "U+65E5"),
      row(uniffi.dict.KanjiDetailKey.JIS0208, "JIS X0208", "16-1"),
      row(uniffi.dict.KanjiDetailKey.JIS0212, "JIS X0212", "-"),
      row(uniffi.dict.KanjiDetailKey.JIS0213, "JIS X0213", "-"),
      row(uniffi.dict.KanjiDetailKey.JIS0208_REFS, "JIS X0208 refs", "-"),
      row(uniffi.dict.KanjiDetailKey.SKIP, "SKIP", "4-4-3"),
      row(uniffi.dict.KanjiDetailKey.FOUR_CORNER, "Four corner", "-"),
      row(uniffi.dict.KanjiDetailKey.DEROO, "De Roo", "-"),
      row(uniffi.dict.KanjiDetailKey.SH_DESC, "SH", "-"),
      row(uniffi.dict.KanjiDetailKey.NELSON, "Nelson", "-"),
      row(uniffi.dict.KanjiDetailKey.NJECD, "NJECD", "-"),
      row(uniffi.dict.KanjiDetailKey.ONEILL, "O'Neill", "-"),
      row(uniffi.dict.KanjiDetailKey.DEROO_VARIANT, "De Roo refs", "-"),
      row(uniffi.dict.KanjiDetailKey.SH_REFS, "SH refs", "-"),
    )
  }

  // Preview IME inputs only: the SearchBar preview types "taberu",
  // everything else is already kana.
  fun kanaDisplay(raw: String): String = when (raw) {
    "taberu" -> "たべる"
    else -> raw
  }

  fun kanaToRomaji(text: String): String = when (text) {
    "taberu" -> "たべる"
    else -> text
  }

  // Preview readings: Hepburn and Kunrei coincide on the toy
  // entries, so one map covers both.
  fun displayReading(reading: String, romanize: Boolean, hepburn: Boolean): String {
    if (!romanize) return reading
    return when (reading) {
      "たべる" -> "taberu"
      "あのひと" -> "anohito"
      "あれ" -> "are"
      else -> reading
    }
  }
}

/** Preview bundle: fake search data with canned shaping (no JNA). */
fun cannedBackends(fake: FakeDictionaryBackend = FakeDictionaryBackend()): Backends = Backends(
  fake,
  FakeKanjiBackend(),
  FakeRecognizerBackend(),
  FakeFuriganaBackend(),
  displaySenses = CannedShaping::displaySenses,
  matchRange = CannedShaping::matchRange,
  preferKeb = CannedShaping::preferKeb,
  preferReb = CannedShaping::preferReb,
  queryFor = CannedShaping::queryFor,
  isCommonWord = CannedShaping::isCommonWord,
  displayReading = CannedShaping::displayReading,
  romajiToKana = CannedShaping::kanaToRomaji,
  radicalGlyph = CannedShaping::kanjiRadicalGlyph,
  kanjiDetails = CannedShaping::kanjiDetails,
)
