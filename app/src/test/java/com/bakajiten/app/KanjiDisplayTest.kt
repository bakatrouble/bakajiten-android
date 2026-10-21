package com.bakajiten.app

import com.bakajiten.app.domain.codepointLabel
import com.bakajiten.app.domain.jisHex
import com.bakajiten.app.domain.kanjiGradeKind
import com.bakajiten.app.domain.kanjiRadicalGlyph
import org.junit.Assert.assertEquals
import org.junit.Test

// Port of lib/libdict kanji/display traces: the Android display
// helpers must match the Rust engine exactly.

class KanjiDisplayTest {
  @Test fun gradeKindTraces() {
    assertEquals("Kyouiku", kanjiGradeKind(1))
    assertEquals("Kyouiku", kanjiGradeKind(6))
    assertEquals("Jouyou", kanjiGradeKind(8))
    assertEquals("Jinmeiyou", kanjiGradeKind(9))
    assertEquals("Jinmeiyou", kanjiGradeKind(10))
    assertEquals("Other", kanjiGradeKind(7))
  }

  @Test fun radicalGlyphTraces() {
    assertEquals("一", kanjiRadicalGlyph(1))
    assertEquals("二", kanjiRadicalGlyph(7))
    assertEquals("艮", kanjiRadicalGlyph(138))
    assertEquals("色", kanjiRadicalGlyph(139))
    assertEquals("龠", kanjiRadicalGlyph(214))
    assertEquals("", kanjiRadicalGlyph(0))
    assertEquals("", kanjiRadicalGlyph(215))
  }

  @Test fun jisHexTrace() {
    assertEquals("0x385e", jisHex(24, 62))
  }

  @Test fun codepointTrace() {
    assertEquals("U+65E5", codepointLabel("日"))
  }
}
