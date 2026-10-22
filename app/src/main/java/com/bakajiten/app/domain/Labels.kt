package com.bakajiten.app.domain

// Sense display models. Shaping (badges/tips/numbering/headings)
// lives in Rust (lib/libdict/src/gloss/display.rs) behind the
// Backends bundle; this file keeps the UI-side models plus the
// trivially pure helpers.

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

/** Content-bearing sense with its original 1-based number. */
data class NumberedSense(val sense: SenseDisplay, val n: Int)

/** Entry is usually written in kana alone (first-sense uk tag). */
fun isKanaEntry(senses: List<SenseDisplay>): Boolean = senses.firstOrNull()?.uk == true
