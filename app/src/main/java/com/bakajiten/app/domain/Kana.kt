package com.bakajiten.app.domain

// Romaji → hiragana query edge, ported row-for-row from
// lib/libdict/src/kana/{convert,table}.rs: greedy longest-token
// scan, whole-word exceptions, moraic-n / geminate lookahead.
// Total and infallible — unknown input falls through untouched.

private val EXCEPTIONS = mapOf(
  "konnichiwa" to "こんにちは",
  "konbanwa" to "こんばんは",
)

private val TABLE: Map<String, String> = mapOf(
  "-" to "ー", "a" to "あ", "ba" to "ば", "be" to "べ", "bi" to "び",
  "bo" to "ぼ", "bu" to "ぶ", "bya" to "びゃ", "byo" to "びょ",
  "byu" to "びゅ", "cha" to "ちゃ", "chi" to "ち", "cho" to "ちょ",
  "chu" to "ちゅ", "da" to "だ", "de" to "で", "dhi" to "でぃ",
  "dhu" to "でゅ", "di" to "ぢ", "do" to "ど", "du" to "づ",
  "dya" to "ぢゃ", "dyo" to "ぢょ", "dyu" to "ぢゅ", "e" to "え",
  "fa" to "ふぁ", "fe" to "ふぇ", "fi" to "ふぃ", "fo" to "ふぉ",
  "fu" to "ふ", "ga" to "が", "ge" to "げ", "gi" to "ぎ",
  "go" to "ご", "gu" to "ぐ", "gya" to "ぎゃ", "gyo" to "ぎょ",
  "gyu" to "ぎゅ", "ha" to "は", "he" to "へ", "hi" to "ひ",
  "ho" to "ほ", "hu" to "ふ", "hya" to "ひゃ", "hyo" to "ひょ",
  "hyu" to "ひゅ", "i" to "い", "ja" to "じゃ", "ji" to "じ",
  "jo" to "じょ", "ju" to "じゅ", "ka" to "か", "ke" to "け",
  "ki" to "き", "ko" to "こ", "ku" to "く", "kya" to "きゃ",
  "kyo" to "きょ", "kyu" to "きゅ", "la" to "ぁ", "le" to "ぇ",
  "li" to "ぃ", "lo" to "ぉ", "ltsu" to "っ", "ltu" to "っ",
  "lu" to "ぅ", "lwa" to "ゎ", "lya" to "ゃ", "lyo" to "ょ",
  "lyu" to "ゅ", "ma" to "ま", "me" to "め", "mi" to "み",
  "mo" to "も", "mu" to "む", "mya" to "みゃ", "myo" to "みょ",
  "myu" to "みゅ", "na" to "な", "ne" to "ね", "ni" to "に",
  "no" to "の", "nu" to "ぬ", "nya" to "にゃ", "nyo" to "にょ",
  "nyu" to "にゅ", "o" to "お", "pa" to "ぱ", "pe" to "ぺ",
  "pi" to "ぴ", "po" to "ぽ", "pu" to "ぷ", "pya" to "ぴゃ",
  "pyo" to "ぴょ", "pyu" to "ぴゅ", "ra" to "ら", "re" to "れ",
  "ri" to "り", "ro" to "ろ", "ru" to "る", "rya" to "りゃ",
  "ryo" to "りょ", "ryu" to "りゅ", "sa" to "さ", "se" to "せ",
  "sha" to "しゃ", "shi" to "し", "sho" to "しょ", "shu" to "しゅ",
  "si" to "し", "so" to "そ", "su" to "す", "sya" to "しゃ",
  "syo" to "しょ", "syu" to "しゅ", "ta" to "た", "tcha" to "っちゃ",
  "tchi" to "っち", "tcho" to "っちょ", "tchu" to "っちゅ",
  "te" to "て", "thi" to "てぃ", "thu" to "てゅ", "ti" to "ち",
  "to" to "と", "tsu" to "つ", "tu" to "つ", "tya" to "ちゃ",
  "tyo" to "ちょ", "tyu" to "ちゅ", "u" to "う", "va" to "ゔぁ",
  "ve" to "ゔぇ", "vi" to "ゔぃ", "vo" to "ゔぉ", "vu" to "ゔ",
  "wa" to "わ", "we" to "うぇ", "wi" to "うぃ", "wo" to "を",
  "xa" to "ぁ", "xe" to "ぇ", "xi" to "ぃ", "xo" to "ぉ",
  "xtsu" to "っ", "xtu" to "っ", "xu" to "ぅ", "xwa" to "ゎ",
  "xya" to "ゃ", "xyo" to "ょ", "xyu" to "ゅ", "ya" to "や",
  "ye" to "いぇ", "yo" to "よ", "yu" to "ゆ", "za" to "ざ",
  "ze" to "ぜ", "zi" to "じ", "zo" to "ぞ", "zu" to "ず",
  "zya" to "じゃ", "zyo" to "じょ", "zyu" to "じゅ",
)

private val GEMINABLE = "kstpcfhbdgjzv".toSet()

/** Live IME tail: a proper prefix of some table token. */
private fun isLivePrefix(suffix: String): Boolean =
  TABLE.keys.any { it.length > suffix.length && it.startsWith(suffix) }

private fun convertChunk(bytes: String, lookahead: Char?, out: StringBuilder) {
  var p = 0
  while (p < bytes.length) {
    val byte = bytes[p]
    if (byte.code < 128) {
      var matched = false
      for (len in 4 downTo 1) {
        if (p + len <= bytes.length) {
          val token = bytes.substring(p, p + len)
          if (token.all { it.code < 128 }) {
            val kana = TABLE[token]
            if (kana != null) {
              out.append(kana)
              p += len
              matched = true
              break
            }
          }
        }
      }
      if (matched) continue
      fun at(i: Int): Char? = if (i < bytes.length) bytes[i] else lookahead
      when {
        byte == 'n' -> {
          out.append('ん')
          p += if (at(p + 1) == '\'') 2 else 1
        }
        byte == 'm' && at(p + 1) == 'm' -> {
          out.append('ん')
          p += 1
        }
        at(p + 1) == byte && byte in GEMINABLE -> {
          out.append('っ')
          p += 1
        }
        else -> {
          out.append(byte)
          p += 1
        }
      }
    } else {
      // Non-ASCII passes through (mixed queries convert
      // character-class by character-class).
      out.append(byte)
      p += 1
    }
  }
}

/** Batch conversion: whole-word exceptions, then the greedy scan. */
fun romajiToKana(text: String): String {
  val lower = text.lowercase()
  EXCEPTIONS[lower]?.let { return it }
  return buildString { convertChunk(lower, null, this) }
}

/** True while the buffer ends mid-mora (some suffix is a live prefix). */
fun romajiHasPendingTail(raw: String): Boolean {
  val lower = raw.lowercase()
  return (lower.indices).any { isLivePrefix(lower.substring(it)) }
}

/**
 * Reconcile one field edit: the editable text is the converted
 * display, so end-insertions arrive as literal suffixes (the typed
 * key, not the converted result) and backspace arrives as a
 * shorter string. End-backspace deletes one whole display
 * character, walking raw back to the longest prefix that still
 * renders it (never un-converting stepwise); any other shape
 * (mid-string edits, selections) resets the buffer to the literal
 * text. Mirrors web `kanaField` over the same display function.
 */
fun applyKanaEdit(raw: String, newText: String): String {
  val display = romajiDisplay(raw)
  if (newText == display) return raw
  if (newText.length < display.length) {
    val want = display.substring(0, display.offsetByCodePoints(display.length, -1))
    if (newText == want) {
      for (i in raw.length - 1 downTo 0) {
        if (romajiDisplay(raw.substring(0, i)) == want) return raw.substring(0, i)
      }
      return if (raw.isNotEmpty()) raw.dropLast(1) else raw
    }
    return newText
  }
  if (newText.startsWith(display)) return raw + newText.substring(display.length)
  return newText
}

/**
 * IME display: longest live tail stays raw (`shin` → しn), the head
 * converts with one char of lookahead. Empty tail converts whole.
 */
fun romajiDisplay(raw: String): String {
  val lower = raw.lowercase()
  var split = lower.length
  for (i in lower.indices) {
    if (isLivePrefix(lower.substring(i))) {
      split = i
      break
    }
  }
  if (split == lower.length) return romajiToKana(raw)
  val head = lower.substring(0, split)
  val tail = lower.substring(split)
  val lookahead = tail.firstOrNull()
  val out = StringBuilder()
  convertChunk(head, lookahead, out)
  out.append(tail)
  return out.toString()
}
