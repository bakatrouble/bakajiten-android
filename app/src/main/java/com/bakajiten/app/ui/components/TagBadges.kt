package com.bakajiten.app.ui.components

import com.bakajiten.app.domain.TagKind

// Sense-tag ink (pos green, misc dimmed, uk-derived "kana" red).
// SenseRow inlines the badges into the definition flow; the names
// stay shared with tests (desktop MeaningBuilder pens).

/** Ink for a badge, shared with tests (desktop MeaningBuilder pens). */
fun badgeColorName(badge: String, kind: TagKind): String = when {
  badge == "kana" -> "error"
  kind == TagKind.MISC -> "muted"
  kind == TagKind.FIELD -> "field"
  else -> "pos"
}
