package com.bakajiten.app.data

// Backend contract mirroring the UniFFI records in
// lib/libdict/src/ffi/records.rs + librecognizer/src/ffi.rs, so the
// fake (tests, previews, no-data dev) and the Rust/JNA
// implementations stay interchangeable.

enum class SearchMode {
  PREFIX,
  SUFFIX,
  MIDDLE,
  EXACT,
}

data class SearchHit(
  val idx: Int,
  /** Reason chain for deinflected hits; empty for direct matches. */
  val chain: List<String>,
  /** Dictionary form behind a deinflected hit; empty for direct hits. */
  val matched: String,
)

data class DictKanjiElement(
  val keb: String,
  val info: List<Int>,
  val priorities: List<String>,
)

data class DictReadingElement(
  val reb: String,
  val nokanji: Boolean,
  val restrictedTo: List<String>,
  val info: List<Int>,
  val priorities: List<String>,
)

data class LangSource(
  val lang: String?,
  val text: String,
)

data class DictSense(
  val pos: List<Int>,
  val field: List<Int>,
  val misc: List<Int>,
  val glosses: List<String>,
  val xref: List<String>,
  val ant: List<String>,
  val stagk: List<String>,
  val stagr: List<String>,
  val info: List<String>,
  val sources: List<LangSource>,
)

data class DictEntry(
  val heading: String,
  val reading: String,
  val senses: List<DictSense>,
  val kanji: List<DictKanjiElement>,
  val readings: List<DictReadingElement>,
  val isName: Boolean,
  val entSeq: Int,
  val jlpt: Int,
)

data class KanjiEntry(
  val character: String,
  val on: List<String>,
  val kun: List<String>,
  val meanings: List<String>,
  val grade: Int?,
  val jlpt: Int?,
  val freq: Int?,
  val strokes: Int,
  val radical: Int?,
  val parts: List<String>,
  val nanori: List<String> = emptyList(),
  val radNames: List<String> = emptyList(),
  val chinese: List<String> = emptyList(),
  val korean: List<String> = emptyList(),
  val vietnamese: List<String> = emptyList(),
  val skip: List<String> = emptyList(),
  val skipMisgrade: Boolean = false,
  val fourCorner: List<String> = emptyList(),
  val deroo: List<String> = emptyList(),
  val shDesc: List<String> = emptyList(),
  val jis208: JisKuten? = null,
  val jis208Refs: List<String> = emptyList(),
  val jis212: List<String> = emptyList(),
  val jis213: List<String> = emptyList(),
  val derooVariant: List<String> = emptyList(),
  val nelsonC: List<String> = emptyList(),
  val njecd: List<String> = emptyList(),
  val oneill: List<String> = emptyList(),
  val sH: List<String> = emptyList(),
)

data class JisKuten(val ku: Int, val ten: Int)

data class KanjiPoint(val x: Float, val y: Float)

/** One ruby span: [text] read as [ruby] (empty ruby = literal). */
data class FuriganaSpan(val text: String, val ruby: String)

/** Furigana segmentation (engine-owned, like the desktop). */
interface FuriganaBackend {
  fun segment(head: String, reading: String): List<FuriganaSpan>?
}

data class KanjiStroke(val points: List<KanjiPoint>)

data class Candidate(
  val character: String,
  val distance: Long,
  val isKana: Boolean,
)

data class StrokePoint(val x: Float, val y: Float)

/** Dictionary side of the backend (one scoped artifact at a time). */
interface DictionaryBackend {
  fun searchIndices(query: String, mode: SearchMode, cap: Int): List<SearchHit>
  fun searchMeaningIndices(query: String, cap: Int): List<SearchHit>
  fun entryAt(idx: Int): DictEntry?
  fun count(): Long
  fun close()
}

/** Kanji side of the backend (kanji.bin artifact). */
interface KanjiBackend {
  fun lookup(character: String): KanjiEntry?
  fun strokes(character: String): List<KanjiStroke>
  fun containing(part: String): List<String>
}

/** Handwriting side of the backend (templates.bin bank). */
interface RecognizerBackend {
  fun recognize(strokes: List<List<StrokePoint>>, topK: Int, includeKana: Boolean): List<Candidate>
  fun templateCount(): Long
  /** Look-alikes of drawn strokes, excluding [exclude] itself. */
  fun similar(strokes: List<List<StrokePoint>>, exclude: String, topK: Int): List<Candidate>
}
