package com.bakajiten.app.data

// Fractional stroke reveal for the stroke-order player (arc length
// + interpolated tip). Pure geometry; the preview never reaches it
// (the player starts paused on the static diagram), so no canned
// fixture is needed — unlike shaping, which previews render.
object RustPlayback {
  fun slice(points: List<KanjiPoint>, fraction: Float): List<KanjiPoint> =
    uniffi.recognizer.strokeSlice(
      points.map { uniffi.recognizer.StrokePoint(it.x, it.y) },
      fraction,
    ).map { KanjiPoint(it.x, it.y) }
}
