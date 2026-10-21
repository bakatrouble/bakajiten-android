package com.bakajiten.app.domain

import com.bakajiten.app.data.DictEntry
import com.bakajiten.app.data.DictSense

// Sense display shaping, mirroring web labels.ts + Qt
// DictionaryModel::displaySenses: entity labels abbreviated
// (`&vt;` -> `vt`), deduplicated, with description tips.

/** Tag category per badge (parallel to badges/tips). */
enum class TagKind { POS, FIELD, MISC }

data class SenseDisplay(
  val badges: List<String>,
  val tips: List<String>,
  val kinds: List<TagKind>,
  val glosses: List<String>,
  val xref: List<String>,
  val ant: List<String>,
  val info: List<String>,
  val sources: List<Pair<String, String>>,
  val restrictedKanji: List<String>,
  val restrictedReading: List<String>,
  /** Usually written in kana alone (uk tag: badge reads "kana"). */
  val uk: Boolean,
)

fun abbreviate(label: String): String =
  if (label.startsWith("&") && label.endsWith(";")) label.substring(1, label.length - 1) else label

private fun collect(
  indices: List<Int>,
  labelOf: (Int) -> String,
  tipOf: (Int) -> String,
  kind: TagKind,
): Triple<List<String>, List<String>, List<TagKind>> {
  val badges = mutableListOf<String>()
  val tips = mutableListOf<String>()
  val kinds = mutableListOf<TagKind>()
  for (tag in indices) {
    val b = abbreviate(labelOf(tag))
    if (b != "" && b !in badges) {
      badges += b
      tips += tipOf(tag)
      kinds += kind
    }
  }
  return Triple(badges, tips, kinds)
}

/**
 * Shape one entry's senses for display. Label lookup comes from the
 * backend (UniFFI entity/description functions), so names resolve
 * the name-type table while words resolve pos.
 */
fun displaySenses(
  entry: DictEntry,
  posLabel: (Int) -> String,
  posTip: (Int) -> String,
  fieldLabel: (Int) -> String,
  fieldTip: (Int) -> String,
  miscLabel: (Int) -> String,
  miscTip: (Int) -> String,
): List<SenseDisplay> =
  entry.senses.map { s ->
    val pos = collect(s.pos, posLabel, posTip, TagKind.POS)
    val field = collect(s.field, fieldLabel, fieldTip, TagKind.FIELD)
    val miscRaw = collect(s.misc, miscLabel, miscTip, TagKind.MISC)
    val uk = "uk" in miscRaw.first
    val miscBadges = miscRaw.first.map { if (it == "uk") "kana" else it }
    SenseDisplay(
      badges = pos.first + field.first + miscBadges,
      tips = pos.second + field.second + miscRaw.second,
      kinds = pos.third + field.third + miscRaw.third,
      glosses = s.glosses,
      xref = s.xref,
      ant = s.ant,
      info = s.info,
      sources = s.sources.map { (it.lang ?: "") to it.text },
      restrictedKanji = s.stagk.toList(),
      restrictedReading = s.stagr.toList(),
      uk = uk,
    )
  }

/** Entry is usually written in kana alone (first-sense uk tag). */
fun isKanaEntry(senses: List<SenseDisplay>): Boolean = senses.firstOrNull()?.uk == true

/** Common-word badge: news/ichi priorities on any form. */
fun isCommonWord(entry: DictEntry): Boolean =
  (entry.kanji.flatMap { it.priorities } + entry.readings.flatMap { it.priorities })
    .any { it == "news1" || it == "news2" || it == "ichi1" || it == "ichi2" }

/** Drop a numeric ・n tail from cross-reference queries. */
fun queryFor(term: String): String = term.replace(Regex("・\\d+$"), "")

/** Content-bearing senses with original numbering (gaps stay visible). */
data class NumberedSense(val sense: SenseDisplay, val n: Int)

fun numberedContentSenses(senses: List<SenseDisplay>, limit: Int = Int.MAX_VALUE): List<NumberedSense> {
  val out = mutableListOf<NumberedSense>()
  for ((i, sense) in senses.withIndex()) {
    if (out.size >= limit) break
    if (sense.glosses.isEmpty() && sense.info.isEmpty()) continue
    out += NumberedSense(sense, i + 1)
  }
  return out
}

/** First gloss of the first sense (row preview). */
fun previewGloss(entry: DictEntry): String = entry.senses.firstOrNull()?.glosses?.firstOrNull() ?: ""

/**
 * Heading form preferring the kanji spelling that contains the
 * query (desktop heading rule); otherwise the first form.
 */
fun preferKeb(entry: DictEntry, query: String): String {
  if (query != "") entry.kanji.firstOrNull { it.keb.contains(query) }?.let { return it.keb }
  return entry.heading
}

/** Reading preferring the spelling containing the query. */
fun preferReb(entry: DictEntry, query: String): String {
  if (query != "") entry.readings.firstOrNull { it.reb.contains(query) }?.let { return it.reb }
  return entry.reading
}

/** Byte offset of the query-match span inside [text], if present. */
fun matchRange(text: String, matched: String): IntRange? {
  if (matched == "") return null
  val at = text.indexOf(matched)
  return if (at < 0) null else at until at + matched.length
}
