package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.state.Backends
import org.junit.Assert.assertEquals
import org.junit.Test

// Reading display through the real Rust engine (Backends defaults
// load the host cdylib). Mirrors the lib/libdict romaji acceptance
// traces: Hepburn macrons, Kunrei circumflexes, geminates, ん
// rules, digraphs, katakana folding, verb endings staying split.

class RomajiTest {
  private val backends = Backends(
    FakeDictionaryBackend(),
    FakeKanjiBackend(),
    FakeRecognizerBackend(),
    FakeFuriganaBackend(),
  )

  private fun hepburn(s: String) = backends.displayReading(s, true, true)
  private fun kunrei(s: String) = backends.displayReading(s, true, false)

  @Test fun convertsBothSchools() {
    assertEquals("たべる", backends.displayReading("たべる", false, true))
    assertEquals("taberu", hepburn("たべる"))
    assertEquals("sushi", hepburn("すし"))
    assertEquals("chizu", hepburn("ちず"))
    assertEquals("fuji", hepburn("ふじ"))
    assertEquals("sensei", hepburn("せんせい"))
    assertEquals("tōkyō", hepburn("とうきょう"))
    assertEquals("ōsaka", hepburn("おおさか"))
    assertEquals("gakusei", hepburn("がくせい"))
    assertEquals("susi", kunrei("すし"))
    assertEquals("tizu", kunrei("ちず"))
    assertEquals("huzi", kunrei("ふじ"))
    assertEquals("sensê", kunrei("せんせい"))
    assertEquals("tôkyô", kunrei("とうきょう"))
    assertEquals("kitte", hepburn("きって"))
    assertEquals("sakka", hepburn("さっか"))
    assertEquals("hon", hepburn("ほん"))
    assertEquals("sampo", hepburn("さんぽ"))
    assertEquals("hon'yaku", hepburn("ほんやく"))
    assertEquals("kyō", hepburn("きょう"))
    assertEquals("rāmen", hepburn("ラーメン"))
    assertEquals("terasuhausu", hepburn("テラスハウス"))
    assertEquals("you", hepburn("よう"))
    assertEquals("kau", hepburn("かう"))
    assertEquals("omou", hepburn("おもう"))
  }
}
