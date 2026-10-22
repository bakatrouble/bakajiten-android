package com.bakajiten.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakajiten.app.data.KanjiPoint
import com.bakajiten.app.data.KanjiStroke
import com.bakajiten.app.data.RustPlayback
import com.bakajiten.app.theme.BakajitenTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

// Stroke-order player (web dialog parity): completed strokes dim,
// the live stroke reveals by arc length at constant drawing speed
// (desktop pacing: 1100ms per stroke at speed 1, 100ms less per
// level), with numbered start badges on completed strokes and a
// transport bar (restart, step, play, stop, speed, counter).

@Composable
fun StrokePlayer(
  strokes: List<KanjiStroke>,
  modifier: Modifier = Modifier,
  slice: (List<KanjiPoint>, Float) -> List<KanjiPoint> = RustPlayback::slice,
) {
  var done by remember(strokes) { mutableIntStateOf(0) }
  var speed by remember(strokes) { mutableIntStateOf(5) }
  // Starts paused like the web player; the user presses play.
  var playing by remember(strokes) { mutableStateOf(false) }
  val frac = remember(strokes) { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val strokeMs = maxOf(120, 1100 - 100 * speed)

  LaunchedEffect(playing, done, speed, strokes) {
    if (!playing || done >= strokes.size) return@LaunchedEffect
    val remaining = ((1f - frac.value) * strokeMs).toInt()
    frac.animateTo(1f, tween(remaining, easing = LinearEasing))
    done += 1
    frac.snapTo(0f)
  }

  fun stop() {
    playing = false
    done = 0
    scope.launch { frac.snapTo(0f) }
  }

  Column(modifier.testTag("strokePlayer")) {
    val measurer = rememberTextMeasurer()
    val widthBase = MaterialTheme.colorScheme.onSurfaceVariant
    val brightBase = Color(0xFFFFB300)
    ElevatedCard(Modifier.fillMaxWidth()) {
      Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
      // Dark backdrop in both themes: amber ink needs the contrast.
      drawRect(Color(0xFF1F1F1F))
      // Fitted frame (web parity): strokes map into the inner cell
      // with a badge-sized margin reserved around it.
      var lo = Float.MAX_VALUE
      var hi = -Float.MAX_VALUE
      for (s in strokes) {
        for (p in s.points) {
          lo = minOf(lo, p.x, p.y)
          hi = maxOf(hi, p.x, p.y)
        }
      }
      if (lo > hi) {
        lo = 0f
        hi = 1f
      }
      val span = maxOf(1f, hi - lo)
      val badgePx = maxOf(12f, size.minDimension * 0.075f)
      val margin = badgePx * 1.1f
      val cell = size.minDimension - 2 * margin
      fun map(v: Float): Float = margin + ((v - lo) / span) * cell
      // Grid over the fitted cell only (desktop KanjiStrokeView):
      // rect plus thirds.
      val grid = Color(0xFF888888)
      drawRect(grid, Offset(margin, margin), Size(cell, cell), style = Stroke(1f))
      for (i in 1..3) {
        val t = margin + cell * i / 4
        drawLine(grid, Offset(t, margin), Offset(t, margin + cell), strokeWidth = 1f)
        drawLine(grid, Offset(margin, t), Offset(margin + cell, t), strokeWidth = 1f)
      }
      val width = maxOf(3f, size.minDimension * 0.035f)
      val pen = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
      val dim = widthBase
      val bright = brightBase
      // Pristine (paused at zero): the full static diagram, so the
      // character is visible before the first play press.
      val pristine = done == 0 && frac.value == 0f && !playing
      for (i in strokes.indices) {
        if (!pristine && i >= done) break
        val pts = strokes[i].points.map { Offset(map(it.x), map(it.y)) }
        if (pts.size >= 2) {
          val path = Path()
          path.moveTo(pts[0].x, pts[0].y)
          for (p in pts.drop(1)) path.lineTo(p.x, p.y)
          drawPath(path, dim, style = pen)
        }
      }
      if (!pristine && done < strokes.size) {
        val live = slice(strokes[done].points, frac.value)
          .map { Offset(map(it.x), map(it.y)) }
        if (live.size >= 2) {
          val path = Path()
          path.moveTo(live[0].x, live[0].y)
          for (p in live.drop(1)) path.lineTo(p.x, p.y)
          drawPath(path, bright, style = pen)
        }
      }
      drawBadges(strokes, done, measurer, ::map)
      if (playing && done < strokes.size) {
        val tip = slice(strokes[done].points, frac.value).lastOrNull()
        if (tip != null) {
          drawCircle(bright, radius = width * 0.7f, center = Offset(map(tip.x), map(tip.y)))
        }
      }
    }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = {
        done = 0
        playing = false
        scope.launch { frac.snapTo(0f) }
      }, modifier = Modifier.testTag("strokeRestart")) {
        Icon(Icons.Filled.Refresh, contentDescription = "Restart")
      }
      IconButton(
        onClick = {
          if (playing) {
            playing = false
          } else {
            if (done >= strokes.size) {
              done = 0
              scope.launch { frac.snapTo(0f) }
            }
            playing = true
          }
        },
        modifier = Modifier.testTag("strokePlayToggle"),
      ) {
        Icon(
          if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (playing) "Pause" else "Play",
        )
      }
      IconButton(onClick = ::stop, modifier = Modifier.testTag("strokeStop")) {
        Icon(Icons.Filled.Stop, contentDescription = "Stop")
      }
      IconButton(
        onClick = {
          playing = false
          // Mid-stroke cancels back to its start; otherwise the
          // previous stroke un-completes.
          if (frac.value <= 0f) done = maxOf(0, done - 1)
          scope.launch { frac.snapTo(0f) }
        },
        modifier = Modifier.testTag("strokeStepBack"),
      ) {
        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous stroke")
      }
      Text("$done/${strokes.size}")
      IconButton(
        onClick = {
          playing = false
          scope.launch { frac.snapTo(0f) }
          done = minOf(strokes.size, done + 1)
        },
        modifier = Modifier.testTag("strokeStepForward"),
      ) {
        Icon(Icons.Filled.SkipNext, contentDescription = "Next stroke")
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text("Speed")
      Slider(
        value = speed.toFloat(),
        onValueChange = { speed = it.toInt().coerceIn(1, 10) },
        valueRange = 1f..10f,
        steps = 8,
        modifier = Modifier.weight(1f).testTag("strokeSpeed"),
      )
      Text(
        "${speed}×",
        modifier = Modifier.width(36.dp).testTag("strokeSpeedLabel"),
      )
    }
  }
}

// Numbered start badges on completed strokes (desktop rules):
// rounded rect with a pointer triangle apexed at the stroke start,
// fill ramping blue (223°) → green (119°). Default slot first
// (left of vertical strokes, top-right of horizontal ones), then a
// compass ring at 1.0 and 1.9 badge units for the first clash-free
// center, clamped on-canvas.
private fun DrawScope.drawBadges(
  strokes: List<KanjiStroke>,
  done: Int,
  measurer: TextMeasurer,
  map: (Float) -> Float,
) {
  val n = minOf(done, strokes.size)
  if (n <= 0) return
  val badge = maxOf(12f, size.minDimension * 0.075f)
  val gap = badge * 0.1f
  val d = 0.7071f
  val compass = listOf(
    Offset(1f, 0f), Offset(d, d), Offset(0f, 1f), Offset(-d, d),
    Offset(-1f, 0f), Offset(-d, -d), Offset(0f, -1f), Offset(d, -d),
  )
  val placed = mutableListOf<Offset>()
  val fontSize = maxOf(4f, badge * 0.275f).sp
  val labelStyle = TextStyle(color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
  for (i in 0 until n) {
    val pts = strokes[i].points
    if (pts.isEmpty()) continue
    val s = Offset(map(pts[0].x), map(pts[0].y))
    val e = pts.lastOrNull()?.let { Offset(map(it.x), map(it.y)) } ?: s
    val dx = e.x - s.x
    val dy = e.y - s.y
    val default = if (abs(dx) + abs(dy) < 1f || abs(dx) >= abs(dy)) {
      Offset(d, -d)
    } else {
      Offset(-1f, 0f)
    }
    var center: Offset? = null
    outer@ for (dist in listOf(1f, 1.9f)) {
      for (dir in listOf(default) + compass) {
        val cx = (s.x + dir.x * dist * badge).coerceIn(badge / 2, size.width - badge / 2)
        val cy = (s.y + dir.y * dist * badge).coerceIn(badge / 2, size.height - badge / 2)
        if (placed.none { abs(it.x - cx) < badge + gap && abs(it.y - cy) < badge + gap }) {
          center = Offset(cx, cy)
          break@outer
        }
      }
    }
    val c = center ?: continue
    placed.add(c)
    // Fill ramps over the whole bank, like the desktop.
    val t = if (strokes.size > 1) i.toFloat() / (strokes.size - 1) else 0f
    val fill = Color.hsl(223f - t * 104f, 0.7f, 0.42f)
    drawRoundRect(fill, c - Offset(badge / 2, badge / 2), Size(badge, badge), CornerRadius(badge / 3))
    // Pointer triangle, apex at the stroke start.
    val vx = c.x - s.x
    val vy = c.y - s.y
    val dist = maxOf(1f, sqrt((vx * vx + vy * vy).toDouble()).toFloat())
    val ax = vx / dist
    val ay = vy / dist
    val bx = s.x + ax * badge * 0.55f
    val by = s.y + ay * badge * 0.55f
    val half = badge * 0.24f
    val tri = Path()
    tri.moveTo(s.x, s.y)
    tri.lineTo(bx - ay * half, by + ax * half)
    tri.lineTo(bx + ay * half, by - ax * half)
    tri.close()
    drawPath(tri, fill)
    val label = measurer.measure(AnnotatedString("${i + 1}"), style = labelStyle)
    drawText(
      measurer,
      "${i + 1}",
      topLeft = c - Offset(label.size.width / 2f, label.size.height / 2f),
      style = labelStyle,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun StrokePlayerPreview() {
  BakajitenTheme {
    StrokePlayer(
      strokes = listOf(
        KanjiStroke(listOf(KanjiPoint(20f, 40f), KanjiPoint(235f, 40f))),
        KanjiStroke(
          listOf(
            KanjiPoint(60f, 10f),
            KanjiPoint(60f, 120f),
            KanjiPoint(120f, 200f),
            KanjiPoint(200f, 230f),
          ),
        ),
      ),
    )
  }
}
