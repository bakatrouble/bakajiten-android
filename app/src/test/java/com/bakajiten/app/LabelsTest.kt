package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.domain.TagKind
import com.bakajiten.app.domain.isKanaEntry
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.components.badgeColorName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Display shaping through the real Rust engine (Backends defaults
// load the host cdylib — see app/build.gradle.kts). Toy entries
// use real table indices, so these expectations pin engine output,
// not fake label maps.

class LabelsTest {
  private val fake = FakeDictionaryBackend()
  private val backends = Backends(
    fake,
    FakeKanjiBackend(),
    FakeRecognizerBackend(),
    FakeFuriganaBackend(),
  )

  private fun shown(idx: Int) = backends.displaySenses(fake.entryAt(idx)!!)

  @Test fun badgesPosTagsWithTips() {
    val hit = fake.searchIndices("たべる", SearchMode.EXACT, 5).first()
    val entry = fake.entryAt(hit.idx)!!
    assertEquals("食べる", backends.preferKeb(entry, "たべる"))
    assertEquals("たべる", backends.preferReb(entry, "たべる"))
    assertEquals("たべる", backends.queryFor("たべる・1"))
    val shown = shown(hit.idx)
    assertTrue(shown[0].sense.badges.contains("v1"))
    assertTrue(shown[0].sense.tips.contains("Ichidan verb"))
  }

  @Test fun kanaMarkerIsKanaWithIntactTip() {
    val broad = fake.searchIndices("あ", SearchMode.PREFIX, 50)
    val kana = broad.firstOrNull { isKanaEntry(shown(it.idx).map { r -> r.sense }) }
    assertTrue(kana != null)
    val shown = shown(kana!!.idx)
    val i = shown.indexOfFirst { it.sense.badges.contains("kana") }
    assertTrue(i >= 0)
    assertEquals(TagKind.MISC, shown[i].sense.kinds[shown[i].sense.badges.indexOf("kana")])
    assertEquals("error", badgeColorName("kana", TagKind.MISC))
    assertTrue(shown[i].sense.tips[shown[i].sense.badges.indexOf("kana")].contains("kana alone"))
  }

  @Test fun miscTagsDimmedLikeDesktop() {
    val hit = fake.searchIndices("あの人", SearchMode.EXACT, 5).first()
    val shown = shown(hit.idx)
    val i = shown.indexOfFirst { it.sense.badges.contains("arch") }
    assertTrue(i >= 0)
    assertEquals(TagKind.MISC, shown[i].sense.kinds[shown[i].sense.badges.indexOf("arch")])
    assertEquals("muted", badgeColorName("arch", TagKind.MISC))
    assertEquals("pos", badgeColorName("v1", TagKind.POS))
    assertEquals("field", badgeColorName("ling", TagKind.FIELD))
    assertTrue(shown[i].sense.tips[shown[i].sense.badges.indexOf("arch")].contains("archaic"))
  }

  @Test fun numbersKeepGaps() {
    val hit = fake.searchIndices("あの人", SearchMode.EXACT, 5).first()
    val numbered = shown(hit.idx)
    assertTrue(numbered.isNotEmpty())
    assertTrue(numbered.all { it.sense.glosses.isNotEmpty() || it.sense.info.isNotEmpty() })
    val ns = numbered.map { it.n }
    assertEquals(ns.sorted(), ns)
  }

  @Test fun matchRangeSpansQuery() {
    assertEquals(3..5, backends.matchRange("to eat", "eat"))
    assertEquals(null, backends.matchRange("to eat", "zzz"))
    assertEquals(null, backends.matchRange("to eat", ""))
  }

  @Test fun commonWordNeedsNewsOrIchi() {
    val taberu = fake.searchIndices("たべる", SearchMode.EXACT, 5).first()
    assertTrue(backends.isCommonWord(fake.entryAt(taberu.idx)!!))
    val are = fake.searchIndices("あれ", SearchMode.EXACT, 5).first()
    assertTrue(!backends.isCommonWord(fake.entryAt(are.idx)!!))
  }
}
