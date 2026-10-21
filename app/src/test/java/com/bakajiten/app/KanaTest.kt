package com.bakajiten.app

import com.bakajiten.app.domain.applyKanaEdit
import com.bakajiten.app.domain.romajiDisplay
import com.bakajiten.app.domain.romajiHasPendingTail
import com.bakajiten.app.domain.romajiToKana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Port of lib/libdict kana acceptance traces: the Android query
// edge must convert exactly like the Rust engine.

class KanaTest {
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
      assertEquals("batch $input", expected, romajiToKana(input))
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
      assertEquals("display $raw", expected, romajiDisplay(raw))
    }
  }

  @Test fun pendingTailTraces() {
    assertTrue(romajiHasPendingTail("n"))
    assertTrue(romajiHasPendingTail("shin"))
    assertTrue(romajiHasPendingTail("sh"))
    assertTrue(romajiHasPendingTail("t"))
    assertTrue(romajiHasPendingTail("たn"))
    assertFalse(romajiHasPendingTail(""))
    assertFalse(romajiHasPendingTail("ta"))
    assertFalse(romajiHasPendingTail("shi"))
    assertFalse(romajiHasPendingTail("たべる"))
    assertFalse(romajiHasPendingTail("食べる"))
  }

  @Test fun fieldEditTraces() {
    // Typing: literal suffixes accumulate in the raw buffer while
    // the display converts (pending tails stay raw).
    assertEquals("t", applyKanaEdit("", "t"))
    assertEquals("ta", applyKanaEdit("t", "ta"))
    assertEquals("taberu", applyKanaEdit("", "taberu"))
    assertEquals("shin", applyKanaEdit("shi", "しn"))
    assertEquals("shinn", applyKanaEdit("shin", "しnn"))
    assertEquals("あ", applyKanaEdit("", "あ"))
    // End-backspace deletes one whole display character, walking
    // raw back without un-converting stepwise.
    assertEquals("", applyKanaEdit("ta", ""))
    assertEquals("shi", applyKanaEdit("shin", "し"))
    assertEquals("shin", applyKanaEdit("shinn", "しん"))
    assertEquals("shi", applyKanaEdit("shin'", "し"))
    // Anything else resets to the literal text.
    assertEquals("たえる", applyKanaEdit("taberu", "たえる"))
    assertEquals("taberu!", applyKanaEdit("taberu", "たべる!"))
  }
}
