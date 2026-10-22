package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.RustShaping
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.components.codepointLabel
import org.junit.Assert.assertEquals
import org.junit.Test

// Kanji display helpers through the real Rust engine (Backends
// defaults load the host cdylib).

class KanjiDisplayTest {
  private val backends = Backends(
    FakeDictionaryBackend(),
    FakeKanjiBackend(),
    FakeRecognizerBackend(),
    FakeFuriganaBackend(),
  )

  @Test fun gradeKindTraces() {
    assertEquals("Kyouiku", RustShaping.gradeKind(1))
    assertEquals("Kyouiku", RustShaping.gradeKind(6))
    assertEquals("Jouyou", RustShaping.gradeKind(8))
    assertEquals("Jinmeiyou", RustShaping.gradeKind(9))
    assertEquals("Jinmeiyou", RustShaping.gradeKind(10))
    assertEquals("Other", RustShaping.gradeKind(7))
  }

  @Test fun radicalGlyphTraces() {
    assertEquals("一", backends.radicalGlyph(1))
    assertEquals("二", backends.radicalGlyph(7))
    assertEquals("艮", backends.radicalGlyph(138))
    assertEquals("色", backends.radicalGlyph(139))
    assertEquals("龠", backends.radicalGlyph(214))
    assertEquals("", backends.radicalGlyph(0))
    assertEquals("", backends.radicalGlyph(215))
  }

  @Test fun jisHexTrace() {
    assertEquals("0x385e", RustShaping.jisHex(24, 62))
  }

  @Test fun codepointTrace() {
    assertEquals("U+65E5", codepointLabel("日"))
  }

  @Test fun detailsTableMatchesConsumers() {
    val entry = FakeKanjiBackend().lookup("日")!!
    val rows = backends.kanjiDetails(entry).associate { it.key to it.value }
    val total = backends.kanjiDetails(entry)
    assertEquals(27, total.size)
    assertEquals("day; sun", rows[uniffi.dict.KanjiDetailKey.MEANING])
    assertEquals("ニチ、ジツ", rows[uniffi.dict.KanjiDetailKey.ON])
    assertEquals("Kyouiku 1", rows[uniffi.dict.KanjiDetailKey.GRADE])
    assertEquals("N5", rows[uniffi.dict.KanjiDetailKey.JLPT])
    assertEquals("日 (72)", rows[uniffi.dict.KanjiDetailKey.RADICAL])
    assertEquals("U+65E5", rows[uniffi.dict.KanjiDetailKey.UNICODE])
    assertEquals("16-1", rows[uniffi.dict.KanjiDetailKey.JIS0208])
    assertEquals("4-4-3", rows[uniffi.dict.KanjiDetailKey.SKIP])
    assertEquals("-", rows[uniffi.dict.KanjiDetailKey.VIETNAMESE])
  }
}
