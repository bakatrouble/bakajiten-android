package com.bakajiten.app.data

import uniffi.dict.Dictionary as NativeDict
import uniffi.dict.KanjiDictionary as NativeKanji
import uniffi.recognizer.Recognizer as NativeRecognizer

// UniFFI/JNA implementation of the backend contract. Handles load
// from unpacked artifact bytes; missing/corrupt files throw the
// UniFFI DictException (callers fall back to FakeBackend).

private fun toHit(h: uniffi.dict.SearchHit) =
  SearchHit(h.idx.toInt(), h.chain.toList(), h.matched)

private fun toEntry(e: uniffi.dict.DictEntry) = DictEntry(
  heading = e.heading,
  reading = e.reading,
  senses = e.senses.map { s ->
    DictSense(
      pos = s.pos.map { it.toInt() and 0xFF },
      field = s.field.map { it.toInt() and 0xFF },
      misc = s.misc.map { it.toInt() and 0xFF },
      glosses = s.glosses.toList(),
      xref = s.xref.toList(),
      ant = s.ant.toList(),
      stagk = s.stagk.toList(),
      stagr = s.stagr.toList(),
      info = s.info.toList(),
      sources = s.sources.map { LangSource(it.lang, it.text) },
    )
  },
  kanji = e.kanji.map {
    DictKanjiElement(it.keb, it.info.map { b -> b.toInt() and 0xFF }, it.priorities.toList())
  },
  readings = e.readings.map {
    DictReadingElement(it.reb, it.nokanji, it.restrictedTo.toList(), it.info.map { b -> b.toInt() and 0xFF }, it.priorities.toList())
  },
  isName = e.isName,
  entSeq = e.entSeq.toInt(),
  jlpt = e.jlpt.toInt(),
)

private fun SearchMode.toNative() = when (this) {
  SearchMode.PREFIX -> uniffi.dict.SearchMode.PREFIX
  SearchMode.SUFFIX -> uniffi.dict.SearchMode.SUFFIX
  SearchMode.MIDDLE -> uniffi.dict.SearchMode.MIDDLE
  SearchMode.EXACT -> uniffi.dict.SearchMode.EXACT
}

class RustDictionaryBackend private constructor(private val native: NativeDict) : DictionaryBackend {
  companion object {
    fun fromBytes(bytes: ByteArray) = RustDictionaryBackend(NativeDict(bytes))
    fun fromPath(path: String) = RustDictionaryBackend(NativeDict.fromPath(path))
  }

  override fun searchIndices(query: String, mode: SearchMode, cap: Int): List<SearchHit> =
    native.searchIndices(query, mode.toNative(), cap.toUInt()).map(::toHit)

  override fun searchMeaningIndices(query: String, cap: Int): List<SearchHit> =
    native.searchMeaningIndices(query, cap.toUInt()).map(::toHit)

  override fun entryAt(idx: Int): DictEntry? = native.entryAt(idx.toUInt())?.let(::toEntry)

  override fun count(): Long = native.count().toLong()

  override fun close() = native.close()
}

class RustKanjiBackend private constructor(private val native: NativeKanji) : KanjiBackend {
  companion object {
    fun fromBytes(bytes: ByteArray) = RustKanjiBackend(NativeKanji(bytes))
    fun fromPath(path: String) = RustKanjiBackend(NativeKanji.fromPath(path))
  }

  override fun lookup(character: String): KanjiEntry? =
    native.lookup(character)?.let {
      KanjiEntry(
        character = it.character,
        on = it.on.toList(),
        kun = it.kun.toList(),
        meanings = it.meanings.toList(),
        grade = it.grade?.toInt(),
        jlpt = it.jlpt?.toInt(),
        freq = it.freq?.toInt(),
        strokes = it.strokes.toInt(),
        radical = it.radical?.toInt(),
        parts = it.parts.toList(),
        nanori = it.nanori.toList(),
        radNames = it.radNames.toList(),
        chinese = it.chinese.toList(),
        korean = it.korean.toList(),
        vietnamese = it.vietnamese.toList(),
        skip = it.skip.toList(),
        skipMisgrade = it.skipMisgrade,
        fourCorner = it.fourCorner.toList(),
        deroo = it.deroo.toList(),
        shDesc = it.shDesc.toList(),
        jis208 = it.jis208?.let { k -> JisKuten(k.ku.toInt(), k.ten.toInt()) },
        jis208Refs = it.jis208Refs.toList(),
        jis212 = it.jis212.toList(),
        jis213 = it.jis213.toList(),
        derooVariant = it.derooVariant.toList(),
        nelsonC = it.nelsonC.toList(),
        njecd = it.njecd.toList(),
        oneill = it.oneill.toList(),
        sH = it.sH.toList(),
      )
    }

  override fun strokes(character: String): List<KanjiStroke> =
    native.strokes(character).map { s ->
      KanjiStroke(s.points.map { KanjiPoint(it.x, it.y) })
    }

  override fun containing(part: String): List<String> = native.containing(part).toList()
}

class RustRecognizerBackend private constructor(private val native: NativeRecognizer) : RecognizerBackend {
  companion object {
    // The recognizer exposes only the bytes constructor (no
    // from_path): callers read templates.bin themselves.
    fun fromBytes(bytes: ByteArray) = RustRecognizerBackend(NativeRecognizer(bytes))
  }

  override fun recognize(
    strokes: List<List<StrokePoint>>,
    topK: Int,
    includeKana: Boolean,
  ): List<Candidate> =
    native.recognize(
      strokes.map { s -> s.map { uniffi.recognizer.StrokePoint(it.x, it.y) } },
      topK.toUInt(),
      includeKana,
    ).map { Candidate(it.character, it.distance.toLong(), it.isKana) }

  override fun templateCount(): Long = native.templateCount().toLong()

  override fun similar(strokes: List<List<StrokePoint>>, exclude: String, topK: Int): List<Candidate> =
    native.similar(
      strokes.map { s -> s.map { uniffi.recognizer.StrokePoint(it.x, it.y) } },
      exclude,
      topK.toUInt(),
    ).map { Candidate(it.character, it.distance.toLong(), it.isKana) }
}

/** UniFFI free functions with no backend handle (label tables,
 * furigana). Display shaping lives in RustShaping; this keeps only
 * what has no shaped counterpart. */
object RustLabels {
  fun furigana(head: String, reading: String): List<FuriganaSpan>? =
    uniffi.dict.furiganaSegment(head, reading)?.map { FuriganaSpan(it.text, it.ruby) }
}
