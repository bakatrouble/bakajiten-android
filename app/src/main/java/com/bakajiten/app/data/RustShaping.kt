package com.bakajiten.app.data

import com.bakajiten.app.domain.NumberedSense
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.TagKind
import uniffi.dict.TagKind as NativeTag

// UniFFI display shaping (pure free functions from libdict; no
// artifacts needed, unlike search). The native library must load:
// unit tests point the JVM at the host cdylib (see
// app/build.gradle.kts), while previews pass canned fixtures
// through the Backends bundle instead.

private fun toNative(e: DictEntry): uniffi.dict.DictEntry = uniffi.dict.DictEntry(
  heading = e.heading,
  reading = e.reading,
  senses = e.senses.map { s ->
    uniffi.dict.DictSense(
      pos = s.pos.map { it.toByte() }.toByteArray(),
      field = s.field.map { it.toByte() }.toByteArray(),
      misc = s.misc.map { it.toByte() }.toByteArray(),
      glosses = s.glosses.toList(),
      xref = s.xref.toList(),
      ant = s.ant.toList(),
      stagk = s.stagk.toList(),
      stagr = s.stagr.toList(),
      info = s.info.toList(),
      sources = s.sources.map { uniffi.dict.LangSource(it.lang, it.text) },
    )
  },
  kanji = e.kanji.map {
    uniffi.dict.DictKanjiElement(it.keb, it.info.map { b -> b.toByte() }.toByteArray(), it.priorities.toList())
  },
  readings = e.readings.map {
    uniffi.dict.DictReadingElement(it.reb, it.nokanji, it.restrictedTo.toList(), it.info.map { b -> b.toByte() }.toByteArray(), it.priorities.toList())
  },
  isName = e.isName,
  entSeq = e.entSeq.toUInt(),
  jlpt = e.jlpt.toUByte(),
)

private fun toKind(kind: NativeTag): TagKind = when (kind) {
  NativeTag.POS -> TagKind.POS
  NativeTag.FIELD -> TagKind.FIELD
  NativeTag.MISC -> TagKind.MISC
}

private fun toSenseDisplay(s: uniffi.dict.ShapedSense): SenseDisplay {
  val badges = mutableListOf<String>()
  val tips = mutableListOf<String>()
  val kinds = mutableListOf<TagKind>()
  for (t in s.pos + s.field + s.misc) {
    badges += t.label
    tips += t.tip
    kinds += toKind(t.kind)
  }
  return SenseDisplay(
    badges = badges,
    tips = tips,
    kinds = kinds,
    glosses = s.glosses.toList(),
    xref = s.xref.toList(),
    ant = s.ant.toList(),
    info = s.info.toList(),
    sources = s.sources.map { (it.lang ?: "") to it.text },
    restrictedKanji = s.restrictedKanji.toList(),
    restrictedReading = s.restrictedReading.toList(),
    uk = s.uk,
  )
}

object RustShaping {
  fun displaySenses(entry: DictEntry): List<NumberedSense> =
    uniffi.dict.displaySenses(toNative(entry)).map { s ->
      NumberedSense(toSenseDisplay(s), s.n.toInt())
    }

  fun matchRange(text: String, query: String): IntRange? =
    uniffi.dict.matchRange(text, query)?.let {
      IntRange(it.start.toInt(), (it.start + it.len - 1u).toInt())
    }

  fun preferKeb(entry: DictEntry, query: String): String =
    uniffi.dict.preferKeb(toNative(entry), query)

  fun preferReb(entry: DictEntry, query: String): String =
    uniffi.dict.preferReb(toNative(entry), query)

  fun displayReading(reading: String, romanize: Boolean, hepburn: Boolean): String {
    if (!romanize) return reading
    val style = if (hepburn) uniffi.dict.RomajiStyle.HEPBURN else uniffi.dict.RomajiStyle.KUNREI
    return uniffi.dict.kanaToRomaji(reading, style)
  }

  fun queryFor(term: String): String = uniffi.dict.queryFor(term)

  fun isCommonWord(entry: DictEntry): Boolean = uniffi.dict.isCommonWord(toNative(entry))

  fun gradeKind(grade: Int): String = when (uniffi.dict.gradeKind(grade.toUInt())) {
    uniffi.dict.GradeKind.KYOUIKU -> "Kyouiku"
    uniffi.dict.GradeKind.JOUYOU -> "Jouyou"
    uniffi.dict.GradeKind.JINMEIYOU -> "Jinmeiyou"
    uniffi.dict.GradeKind.OTHER -> "Other"
  }

  fun radicalGlyph(number: Int): String = uniffi.dict.radicalGlyph(number.toUInt())

  fun jisHex(ku: Int, ten: Int): String = uniffi.dict.jisHex(ku.toUByte(), ten.toUByte())

  private fun toNativeKanji(e: KanjiEntry): uniffi.dict.KanjiEntry = uniffi.dict.KanjiEntry(
    e.character, e.on.toList(), e.kun.toList(), e.meanings.toList(),
    e.grade?.toUInt(), e.jlpt?.toUInt(), e.freq?.toUInt(), e.strokes.toUInt(),
    e.radical?.toUInt(), e.parts.toList(),
    e.jis208?.let { uniffi.dict.JisKuten(it.ku.toUByte(), it.ten.toUByte()) },
    e.nanori.toList(), e.radNames.toList(), e.chinese.toList(), e.korean.toList(),
    e.vietnamese.toList(), e.skip.toList(), e.skipMisgrade, e.fourCorner.toList(),
    e.deroo.toList(), e.shDesc.toList(), e.jis208Refs.toList(), e.jis212.toList(),
    e.jis213.toList(), e.derooVariant.toList(), e.nelsonC.toList(), e.njecd.toList(),
    e.oneill.toList(), e.sH.toList(),
  )

  fun kanjiDetails(entry: KanjiEntry): List<uniffi.dict.KanjiDetailRow> =
    uniffi.dict.kanjiDetails(toNativeKanji(entry))
}
