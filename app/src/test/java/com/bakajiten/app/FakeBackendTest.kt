package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.data.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBackendTest {
  @Test fun exactFindsTaberu() {
    val fake = FakeDictionaryBackend()
    val hits = fake.searchIndices("たべる", SearchMode.EXACT, 5)
    assertTrue(hits.isNotEmpty())
    assertEquals("食べる", fake.entryAt(hits[0].idx)?.heading)
  }

  @Test fun emptyOrZeroCapMisses() {
    val fake = FakeDictionaryBackend()
    assertTrue(fake.searchIndices("", SearchMode.PREFIX, 5).isEmpty())
    assertTrue(fake.searchIndices("た", SearchMode.PREFIX, 0).isEmpty())
    assertTrue(fake.searchMeaningIndices("", 5).isEmpty())
  }

  @Test fun meaningSearchesGlosses() {
    val fake = FakeDictionaryBackend()
    val hits = fake.searchMeaningIndices("to eat", 5)
    assertTrue(hits.isNotEmpty())
    assertEquals("食べる", fake.entryAt(hits[0].idx)?.heading)
  }

  @Test fun outOfRangeEntryIsNull() {
    assertNull(FakeDictionaryBackend().entryAt(999))
  }

  @Test fun kanjiLookup() {
    val kanji = FakeKanjiBackend()
    assertEquals("日", kanji.lookup("日")?.character)
    assertNull(kanji.lookup("x"))
    assertEquals(listOf("日", "曜", "明"), kanji.containing("日"))
  }

  @Test fun recognizerNeedsStrokes() {
    val rec = FakeRecognizerBackend()
    assertTrue(rec.recognize(emptyList(), 5, true).isEmpty())
    val pen = listOf(listOf(StrokePoint(10f, 10f), StrokePoint(200f, 200f)))
    val kana = rec.recognize(pen, 5, true)
    assertTrue(kana.any { it.isKana })
    val kanjiOnly = rec.recognize(pen, 5, false)
    assertTrue(kanjiOnly.none { it.isKana })
    assertEquals(4, rec.templateCount())
  }
}
