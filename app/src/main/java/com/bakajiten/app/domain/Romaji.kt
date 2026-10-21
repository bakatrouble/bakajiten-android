package com.bakajiten.app.domain

// Kana → romaji for reading display, ported from lib/libdict
// romaji: Hepburn uses macrons, Kunrei-shiki circumflexes. Long
// vowels merge (`aa` → ā, `ou` → ō) except word-final `ou` (verb
// endings like かう stay split); Hepburn additionally keeps `ei`
// split (せんせい, not sensē). Katakana folds first, non-kana
// passes through untouched. を never merges.

/** Reading display: kana as-is, otherwise romanized. */
fun displayReading(reading: String, romanize: Boolean, hepburn: Boolean = true): String =
  if (romanize) kanaToRomaji(reading, hepburn) else reading

/** Convert kana (hiragana or katakana) to romaji. */
fun kanaToRomaji(kana: String, hepburn: Boolean): String {
  val chars = kana.toList()
  val n = chars.size
  val out = StringBuilder()
  var prevVowel: Int? = null
  var prevDigraph = false
  var i = 0
  while (i < n) {
    val french = i == n - 1
    val c = foldKana(chars[i])
    // ヴ + small vowel: va/vi/vu/ve/vo; bare ヴ → vu.
    if (c == 'ヴ') {
      if (i + 1 < n) {
        val tail = when (foldKana(chars[i + 1])) {
          'ぁ' -> Pair("va", 'a'.code)
          'ぃ' -> Pair("vi", 'i'.code)
          'ぇ' -> Pair("ve", 'e'.code)
          'ぉ' -> Pair("vo", 'o'.code)
          else -> null
        }
        if (tail != null) {
          out.append(tail.first)
          prevVowel = tail.second
          prevDigraph = false
          i += 2
          continue
        }
      }
      out.append("vu")
      prevVowel = 'u'.code
      prevDigraph = false
      i += 1
      continue
    }
    // Small っ: double the next consonant; trailing っ → tsu/tu.
    if (c == 'っ') {
      if (i + 1 >= n) {
        out.append(if (hepburn) "tsu" else "tu")
        prevVowel = 'u'.code
        prevDigraph = false
        i += 1
        continue
      }
      val d = foldKana(chars[i + 1])
      var next: Char? = null
      next = mora(d, hepburn)?.text?.get(0) ?: glideTail(d)?.first?.get(0)
      if (next != null) {
        out.append(next)
        prevVowel = null // geminate breaks vowel runs
        prevDigraph = false
        i += 1 // re-read the next mora normally
        continue
      }
      i += 1
      continue
    }
    // Consonant + small kana digraph (きゃ, しゅ, くゎ …).
    if (i + 1 < n) {
      val d = foldKana(chars[i + 1])
      val stem = digraphStem(c, hepburn)
      val tail = glideTail(d)
      if (stem != null && tail != null) {
        out.append(stem)
        out.append(tail.first)
        prevVowel = tail.second
        prevDigraph = true
        i += 2
        continue
      }
    }
    // Long mark: replace the previous vowel with its marked form.
    if (c == 'ー') {
      val prev = prevVowel
      if (prev != null) {
        out.deleteCharAt(out.length - 1)
        out.append(longMark(prev, hepburn))
      } else {
        out.append('-')
      }
      prevVowel = null
      prevDigraph = false
      i += 1
      continue
    }
    // ん: n' before vowels and y, m before b/m/p, else n
    // (trailing ん falls through to the plain table entry).
    if (c == 'ん' && i + 1 < n) {
      var next: Char? = null
      val d = foldKana(chars[i + 1])
      if (i + 2 < n) {
        val e2 = foldKana(chars[i + 2])
        val stem = digraphStem(d, hepburn)
        if (stem != null && glideTail(e2) != null) next = stem[0]
      }
      if (next == null) {
        next = mora(d, hepburn)?.text?.get(0)
          ?: if (d == 'っ' || d == 'ん') {
            val key = if (d == 'ん') 'ん' else 'つ'
            mora(key, hepburn)?.text?.get(0)
          } else {
            null
          }
      }
      val head = next
      when {
        head != null && "aiueoy".contains(head) -> out.append("n'")
        head == 'b' || head == 'm' || head == 'p' -> out.append('m')
        else -> out.append('n')
      }
      prevVowel = null
      i += 1
      continue
    }
    // Bare vowel mora: merge long vowels, else emit.
    if (isBareVowel(c)) {
      val cur = when (c) {
        'あ' -> 'a'.code
        'い' -> 'i'.code
        'う' -> 'u'.code
        'え' -> 'e'.code
        else -> 'o'.code
      }
      if (mergesWith(prevVowel, cur, french, prevDigraph, hepburn)) {
        out.deleteCharAt(out.length - 1)
        out.append(longMark(prevVowel ?: cur, hepburn))
        prevVowel = null
        prevDigraph = false
        i += 1
        continue
      }
      out.append(mora(c, hepburn)?.text ?: "")
      prevVowel = cur
      prevDigraph = false
      i += 1
      continue
    }
    // Plain mora (を carries no mergeable vowel, ん none).
    val plain = mora(c, hepburn)
    if (plain != null) {
      out.append(plain.text)
      prevVowel = plain.vowel
      prevDigraph = false
      i += 1
      continue
    }
    // Non-kana (kanji, punctuation): pass through, break runs.
    out.append(chars[i])
    prevVowel = null
    prevDigraph = false
    i += 1
  }
  return out.toString()
}

private data class Mora(val text: String, val vowel: Int?)

private fun mora(c: Char, hepburn: Boolean): Mora? {
  val plain = when (c) {
    'あ' -> Mora("a", 'a'.code)
    'い' -> Mora("i", 'i'.code)
    'う' -> Mora("u", 'u'.code)
    'え' -> Mora("e", 'e'.code)
    'お' -> Mora("o", 'o'.code)
    'か' -> Mora("ka", 'a'.code)
    'き' -> Mora("ki", 'i'.code)
    'く' -> Mora("ku", 'u'.code)
    'け' -> Mora("ke", 'e'.code)
    'こ' -> Mora("ko", 'o'.code)
    'さ' -> Mora("sa", 'a'.code)
    'す' -> Mora("su", 'u'.code)
    'せ' -> Mora("se", 'e'.code)
    'そ' -> Mora("so", 'o'.code)
    'た' -> Mora("ta", 'a'.code)
    'て' -> Mora("te", 'e'.code)
    'と' -> Mora("to", 'o'.code)
    'な' -> Mora("na", 'a'.code)
    'に' -> Mora("ni", 'i'.code)
    'ぬ' -> Mora("nu", 'u'.code)
    'ね' -> Mora("ne", 'e'.code)
    'の' -> Mora("no", 'o'.code)
    'は' -> Mora("ha", 'a'.code)
    'ひ' -> Mora("hi", 'i'.code)
    'へ' -> Mora("he", 'e'.code)
    'ほ' -> Mora("ho", 'o'.code)
    'ま' -> Mora("ma", 'a'.code)
    'み' -> Mora("mi", 'i'.code)
    'む' -> Mora("mu", 'u'.code)
    'め' -> Mora("me", 'e'.code)
    'も' -> Mora("mo", 'o'.code)
    'や' -> Mora("ya", 'a'.code)
    'ゆ' -> Mora("yu", 'u'.code)
    'よ' -> Mora("yo", 'o'.code)
    'ら' -> Mora("ra", 'a'.code)
    'り' -> Mora("ri", 'i'.code)
    'る' -> Mora("ru", 'u'.code)
    'れ' -> Mora("re", 'e'.code)
    'ろ' -> Mora("ro", 'o'.code)
    'わ' -> Mora("wa", 'a'.code)
    'ゐ' -> Mora("wi", 'i'.code)
    'ゑ' -> Mora("we", 'e'.code)
    'を' -> Mora("wo", null)
    'ん' -> Mora("n", null)
    'が' -> Mora("ga", 'a'.code)
    'ぎ' -> Mora("gi", 'i'.code)
    'ぐ' -> Mora("gu", 'u'.code)
    'げ' -> Mora("ge", 'e'.code)
    'ご' -> Mora("go", 'o'.code)
    'ざ' -> Mora("za", 'a'.code)
    'ず' -> Mora("zu", 'u'.code)
    'ぜ' -> Mora("ze", 'e'.code)
    'ぞ' -> Mora("zo", 'o'.code)
    'だ' -> Mora("da", 'a'.code)
    'で' -> Mora("de", 'e'.code)
    'ど' -> Mora("do", 'o'.code)
    'ば' -> Mora("ba", 'a'.code)
    'び' -> Mora("bi", 'i'.code)
    'ぶ' -> Mora("bu", 'u'.code)
    'べ' -> Mora("be", 'e'.code)
    'ぼ' -> Mora("bo", 'o'.code)
    'ぱ' -> Mora("pa", 'a'.code)
    'ぴ' -> Mora("pi", 'i'.code)
    'ぷ' -> Mora("pu", 'u'.code)
    'ぺ' -> Mora("pe", 'e'.code)
    'ぽ' -> Mora("po", 'o'.code)
    'ぁ' -> Mora("a", 'a'.code)
    'ぃ' -> Mora("i", 'i'.code)
    'ぅ' -> Mora("u", 'u'.code)
    'ぇ' -> Mora("e", 'e'.code)
    'ぉ' -> Mora("o", 'o'.code)
    'ゎ' -> Mora("wa", 'a'.code)
    else -> null
  }
  if (plain != null) return plain
  return when (c) {
    'し' -> Mora(if (hepburn) "shi" else "si", 'i'.code)
    'ち' -> Mora(if (hepburn) "chi" else "ti", 'i'.code)
    'つ' -> Mora(if (hepburn) "tsu" else "tu", 'u'.code)
    'ふ' -> Mora(if (hepburn) "fu" else "hu", 'u'.code)
    'じ', 'ぢ' -> Mora(if (hepburn) "ji" else "zi", 'i'.code)
    'づ' -> Mora("zu", 'u'.code)
    else -> null
  }
}

private fun digraphStem(c: Char, hepburn: Boolean): String? = when (c) {
  'き' -> "k"
  'し' -> if (hepburn) "sh" else "s"
  'ち' -> if (hepburn) "ch" else "t"
  'に' -> "n"
  'ひ' -> "h"
  'み' -> "m"
  'り' -> "r"
  'ぎ' -> "g"
  'じ', 'ぢ' -> if (hepburn) "j" else "z"
  'び' -> "b"
  'ぴ' -> "p"
  'く' -> "k"
  'う' -> "w"
  else -> null
}

private fun glideTail(c: Char): Pair<String, Int>? = when (c) {
  'ゃ' -> Pair("ya", 'a'.code)
  'ゅ' -> Pair("yu", 'u'.code)
  'ょ' -> Pair("yo", 'o'.code)
  'ぁ' -> Pair("a", 'a'.code)
  'ぃ' -> Pair("i", 'i'.code)
  'ぅ' -> Pair("u", 'u'.code)
  'ぇ' -> Pair("e", 'e'.code)
  'ぉ' -> Pair("o", 'o'.code)
  'ゎ' -> Pair("wa", 'a'.code)
  else -> null
}

private fun longMark(vowel: Int, hepburn: Boolean): Char = when {
  vowel == 'a'.code && hepburn -> 'ā'
  vowel == 'a'.code -> 'â'
  vowel == 'i'.code && hepburn -> 'ī'
  vowel == 'i'.code -> 'î'
  vowel == 'u'.code && hepburn -> 'ū'
  vowel == 'u'.code -> 'û'
  vowel == 'e'.code && hepburn -> 'ē'
  vowel == 'e'.code -> 'ê'
  hepburn -> 'ō'
  else -> 'ô'
}

private fun mergesWith(prev: Int?, cur: Int, isFinal: Boolean, prevDigraph: Boolean, hepburn: Boolean): Boolean {
  val p = prev ?: return false
  if (p == cur) return true
  if (p == 'o'.code && cur == 'u'.code) return !isFinal || prevDigraph
  return !hepburn && p == 'e'.code && cur == 'i'.code
}

private fun foldKana(c: Char): Char = when (c) {
  'ヵ' -> 'か'
  'ヶ' -> 'け'
  in 'ァ'..'ヴ' -> (c.code - 0x60).toChar()
  else -> c
}

private fun isBareVowel(c: Char): Boolean = c == 'あ' || c == 'い' || c == 'う' || c == 'え' || c == 'お'
