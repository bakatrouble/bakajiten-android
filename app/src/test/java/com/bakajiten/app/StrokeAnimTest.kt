package com.bakajiten.app

import com.bakajiten.app.data.KanjiPoint
import com.bakajiten.app.domain.liveSlice
import org.junit.Assert.assertEquals
import org.junit.Test

// Port of the web player's fractional-reveal traces: constant
// drawing speed by arc length with an interpolated tip.

class StrokeAnimTest {
  private val line = listOf(KanjiPoint(0f, 0f), KanjiPoint(10f, 0f))

  @Test fun emptyAndZeroRevealNothing() {
    assertEquals(emptyList<KanjiPoint>(), liveSlice(emptyList(), 0.5f))
    assertEquals(emptyList<KanjiPoint>(), liveSlice(line, 0f))
  }

  @Test fun halfRevealsInterpolatedTip() {
    val out = liveSlice(line, 0.5f)
    assertEquals(2, out.size)
    assertEquals(0f, out[0].x)
    assertEquals(5f, out[1].x)
  }

  @Test fun fullRevealsAllPoints() {
    assertEquals(line, liveSlice(line, 1f))
    assertEquals(line, liveSlice(line, 2f))
  }

  @Test fun multiSegmentSplitsByLength() {
    // 10 right, then 10 down: half lands exactly on the corner.
    val pts = listOf(KanjiPoint(0f, 0f), KanjiPoint(10f, 0f), KanjiPoint(10f, 10f))
    val out = liveSlice(pts, 0.5f)
    assertEquals(2, out.size)
    assertEquals(10f, out[1].x)
    assertEquals(0f, out[1].y)
    val quarter = liveSlice(pts, 0.25f)
    assertEquals(5f, quarter[1].x)
  }

  @Test fun degenerateStrokeRevealsFirstPoint() {
    val pts = listOf(KanjiPoint(3f, 3f), KanjiPoint(3f, 3f))
    assertEquals(pts.take(1), liveSlice(pts, 0.5f))
  }
}
