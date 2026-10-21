package com.bakajiten.app

import com.bakajiten.app.domain.kanaToRomaji
import org.junit.Assert.assertEquals
import org.junit.Test

// Port of lib/libdict romaji acceptance traces: reading display
// must romanize exactly like the Rust engine.

class RomajiTest {
  private fun hepburn(s: String) = kanaToRomaji(s, true)
  private fun kunrei(s: String) = kanaToRomaji(s, false)

  @Test fun convertsBothSchools() {
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
