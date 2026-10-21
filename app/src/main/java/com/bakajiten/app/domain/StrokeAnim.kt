package com.bakajiten.app.domain

import com.bakajiten.app.data.KanjiPoint

// Stroke animation shaping, ported from the web player (desktop
// subStroke): fractional reveal by arc length, so drawing speed is
// constant regardless of point density, with an interpolated tip
// instead of whole-point pops.

/** First `fraction` (0..1) of a polyline by arc length. */
fun liveSlice(pts: List<KanjiPoint>, fraction: Float): List<KanjiPoint> {
  val first = pts.firstOrNull() ?: return emptyList()
  if (fraction <= 0f) return emptyList()
  val segs = pts.zipWithNext { a, b ->
    kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
  }
  val total = segs.sum()
  if (total <= 0.0) return listOf(first)
  var target = minOf(1f, fraction).toDouble() * total
  val out = mutableListOf(first)
  for (i in 1 until pts.size) {
    val a = pts[i - 1]
    val b = pts[i]
    val seg = segs[i - 1]
    if (target > seg) {
      target -= seg
      out.add(b)
    } else {
      val t = if (seg > 0) target / seg else 0.0
      out.add(KanjiPoint((a.x + t * (b.x - a.x)).toFloat(), (a.y + t * (b.y - a.y)).toFloat()))
      break
    }
  }
  return out
}
