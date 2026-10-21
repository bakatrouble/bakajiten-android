package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.domain.TagKind
import com.bakajiten.app.domain.displaySenses
import com.bakajiten.app.domain.isKanaEntry
import com.bakajiten.app.domain.matchRange
import com.bakajiten.app.domain.numberedContentSenses
import com.bakajiten.app.domain.preferKeb
import com.bakajiten.app.domain.preferReb
import com.bakajiten.app.domain.previewGloss
import com.bakajiten.app.domain.queryFor
import com.bakajiten.app.ui.components.badgeColorName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Display shaping over the fake backend (same assertions as the
// web backend tests): pos tips, kana marker, dimmed misc tags.

class LabelsTest {
  private val fake = FakeDictionaryBackend()
  private fun senses(idx: Int) = displaySenses(
    fake.entryAt(idx)!!,
    fake::posLabel, fake::posTip, { "" }, { "" }, fake::miscLabel, fake::miscTip,
  )

  @Test fun badgesPosTagsWithTips() {
    val hit = fake.searchIndices("たべる", SearchMode.EXACT, 5).first()
    val entry = fake.entryAt(hit.idx)!!
    assertEquals("食べる", entry.heading)
    assertEquals("たべる", entry.reading)
    assertEquals("to eat", previewGloss(entry))
    val shown = senses(hit.idx)
    assertTrue(shown[0].badges.contains("vt"))
    assertTrue(shown[0].tips.contains("transitive verb"))
  }

  @Test fun kanaMarkerIsRedWithIntactTip() {
    val broad = fake.searchIndices("あ", SearchMode.PREFIX, 50)
    val kana = broad.firstOrNull { isKanaEntry(senses(it.idx)) }
    assertTrue(kana != null)
    val shown = senses(kana!!.idx)
    val i = shown.indexOfFirst { it.badges.contains("kana") }
    assertTrue(i >= 0)
    assertEquals(TagKind.MISC, shown[i].kinds[shown[i].badges.indexOf("kana")])
    assertEquals("error", badgeColorName("kana", TagKind.MISC))
    assertTrue(shown[i].tips[shown[i].badges.indexOf("kana")].contains("kana alone"))
  }

  @Test fun miscTagsDimmedLikeDesktop() {
    val hit = fake.searchIndices("あの人", SearchMode.EXACT, 5).first()
    val shown = senses(hit.idx)
    val i = shown.indexOfFirst { it.badges.contains("arch") }
    assertTrue(i >= 0)
    assertEquals(TagKind.MISC, shown[i].kinds[shown[i].badges.indexOf("arch")])
    assertEquals("muted", badgeColorName("arch", TagKind.MISC))
    assertEquals("pos", badgeColorName("vt", TagKind.POS))
    assertEquals("field", badgeColorName("ling", TagKind.FIELD))
    assertTrue(shown[i].tips[shown[i].badges.indexOf("arch")].contains("archaic"))
  }

  @Test fun numbersKeepGaps() {
    val hit = fake.searchIndices("あの人", SearchMode.EXACT, 5).first()
    val numbered = numberedContentSenses(senses(hit.idx))
    assertTrue(numbered.isNotEmpty())
    assertTrue(numbered.all { it.sense.glosses.isNotEmpty() || it.sense.info.isNotEmpty() })
    val ns = numbered.map { it.n }
    assertEquals(ns.sorted(), ns)
  }

  @Test fun headingReadingPreferQuery() {
    val hit = fake.searchIndices("たべる", SearchMode.EXACT, 5).first()
    val entry = fake.entryAt(hit.idx)!!
    assertEquals("食べる", preferKeb(entry, "たべる"))
    assertEquals("たべる", preferReb(entry, "たべる"))
    assertEquals("たべる", queryFor("たべる・1"))
  }

  @Test fun matchRangeSpansQuery() {
    assertEquals(3..5, matchRange("to eat", "eat"))
    assertEquals(null, matchRange("to eat", "zzz"))
    assertEquals(null, matchRange("to eat", ""))
  }
}
