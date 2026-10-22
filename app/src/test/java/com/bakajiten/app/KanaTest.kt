package com.bakajiten.app

import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.RustKana
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.components.applyKanaEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Romaji→kana query edge through the real Rust engine (Backends
// defaults load the host cdylib). Mirrors the lib/libdict kana
// acceptance traces.

class KanaTest {
  private val backends = Backends(
    FakeDictionaryBackend(),
    FakeKanjiBackend(),
    FakeRecognizerBackend(),
    FakeFuriganaBackend(),
  )
  private val convert = backends.romajiToKana
  private val display = RustKana::display
  private val pending = RustKana::hasPending
  private fun edit(raw: String, new: String) = applyKanaEdit(raw, new, RustKana::display)

  @Test fun batchAcceptance() {
    val rows = listOf(
      "a" to "あ", "shi" to "し", "chi" to "ち", "tsu" to "つ",
      "fu" to "ふ", "wo" to "を", "n" to "ん", "si" to "し",
      "ti" to "ち", "tu" to "つ", "hu" to "ふ", "zi" to "じ",
      "sya" to "しゃ", "sha" to "しゃ", "sho" to "しょ", "shu" to "しゅ",
      "tyu" to "ちゅ", "zyo" to "じょ",
      "di" to "ぢ", "du" to "づ", "dya" to "ぢゃ",
      "kya" to "きゃ", "cho" to "ちょ",
      "nyu" to "にゅ", "ja" to "じゃ", "byo" to "びょ",
      "konnichiwa" to "こんにちは", "konbanwa" to "こんばんは",
      "arigatou" to "ありがとう", "gakkou" to "がっこう",
      "shinjuku" to "しんじゅく", "matcha" to "まっちゃ",
      "kissaten" to "きっさてん", "beddo" to "べっど",
      "emma" to "えんま", "komatta" to "こまった",
      "kinen" to "きねん", "ginen" to "ぎねん",
      "kin'en" to "きんえん", "shin" to "しん", "hon" to "ほん",
      "shinya" to "しにゃ", "shin'ya" to "しんや",
      "xtsu" to "っ", "ltu" to "っ", "xa" to "ぁ",
      "xyo" to "ょ", "xwa" to "ゎ", "fa" to "ふぁ",
      "vu" to "ゔ", "thi" to "てぃ", "dhu" to "でゅ",
      "wi" to "うぃ", "ye" to "いぇ",
      "pa-thi" to "ぱーてぃ", "食べる" to "食べる",
      "あ" to "あ", "漢字" to "漢字",
      "taberu食べる" to "たべる食べる", "hello" to "へlぉ",
      "'" to "'",
    )
    for ((input, expected) in rows) {
      assertEquals("batch $input", expected, convert(input))
    }
  }

  @Test fun displayAcceptance() {
    val rows = listOf(
      "s" to "s", "sh" to "sh", "shi" to "し",
      "n" to "n", "na" to "な", "nn" to "んn", "nna" to "んな",
      "t" to "t", "tc" to "tc", "tch" to "tch", "tchi" to "っち",
      "shin" to "しn", "shina" to "しな", "shinya" to "しにゃ",
      "sha" to "しゃ", "sho" to "しょ", "shu" to "しゅ",
      "shin'a" to "しんあ", "ka" to "か", "kat" to "かt",
      "katta" to "かった", "katt" to "かっt",
      "gakk" to "がっk", "ko" to "こ", "kon" to "こn",
      "konn" to "こんn", "konni" to "こんに",
      "konnichi" to "こんにち", "konnichiw" to "こんにちw",
      "konnichiwa" to "こんにちは", "tsu" to "つ", "du" to "づ",
      "thi" to "てぃ", "wo" to "を", "-" to "ー",
      "xtsu" to "っ", "n'" to "ん", "q" to "q",
    )
    for ((raw, expected) in rows) {
      assertEquals("display $raw", expected, display(raw))
    }
  }

  @Test fun pendingTailTraces() {
    assertTrue(pending("n"))
    assertTrue(pending("shin"))
    assertTrue(pending("sh"))
    assertTrue(pending("t"))
    assertTrue(pending("たn"))
    assertFalse(pending(""))
    assertFalse(pending("ta"))
    assertFalse(pending("shi"))
    assertFalse(pending("たべる"))
    assertFalse(pending("食べる"))
  }

  @Test fun fieldEditTraces() {
    // Typing: literal suffixes accumulate in the raw buffer while
    // the display converts (pending tails stay raw).
    assertEquals("t", edit("", "t"))
    assertEquals("ta", edit("t", "ta"))
    assertEquals("taberu", edit("", "taberu"))
    assertEquals("shin", edit("shi", "しn"))
    assertEquals("shinn", edit("shin", "しnn"))
    assertEquals("あ", edit("", "あ"))
    // End-backspace deletes one whole display character, walking
    // raw back without un-converting stepwise.
    assertEquals("", edit("ta", ""))
    assertEquals("shi", edit("shin", "し"))
    assertEquals("shin", edit("shinn", "しん"))
    assertEquals("shi", edit("shin'", "し"))
    // Anything else resets to the literal text.
    assertEquals("たえる", edit("taberu", "たえる"))
    assertEquals("taberu!", edit("taberu", "たべる!"))
  }
}
