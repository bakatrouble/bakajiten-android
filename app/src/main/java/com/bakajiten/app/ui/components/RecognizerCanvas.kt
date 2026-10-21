package com.bakajiten.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakajiten.app.theme.BakajitenTheme

// Handwriting capture surface: accent ink over a Qt-style grid
// (tenths + halves), with centered help text on the bare canvas.
// Strokes cross to the matcher in 0–1 fractions; the desktop 1.2px
// move gate applies in panel pixels.

// Paper + inks, unified from the resolved scheme: the paper is the
// surface darkened a touch, in both themes — so the finalized
// stroke can always be the plain foreground ink, while the active
// stroke gets the accent (theme primary on light paper, amber on
// dark, where primary would drown). Pure for unit tests.
internal data class CanvasInks(val paper: Color, val active: Color, val final: Color)

internal fun darken(color: Color, factor: Float = 0.93f): Color =
  color.copy(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
  )

internal fun canvasInks(surface: Color, primary: Color, onSurface: Color): CanvasInks {
  // Plain sRGB luminance; the branch only tells near-white paper
  // from near-black, so gamma precision does not matter.
  val luminance = 0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue
  return if (luminance > 0.5f) {
    CanvasInks(paper = darken(surface), active = primary, final = onSurface)
  } else {
    CanvasInks(paper = darken(surface), active = Color(0xFFFFB300), final = onSurface)
  }
}

@Composable
fun RecognizerCanvas(
  strokes: List<List<Offset>>,
  onStroke: (List<Offset>) -> Unit,
  grid: Boolean,
  modifier: Modifier = Modifier,
  onClear: () -> Unit = {},
  helpText: String = "Draw a Japanese character here and select a candidate! Long-press clears the drawing.",
  // Fitted square in px (side + top-left offset in the window).
  // Strokes and the grid draw in square fractions, so the content
  // stays registered and square at any window size. Null fits to
  // the window itself (previews).
  squareSidePx: Float? = null,
  squareOffsetPx: Offset = Offset.Zero,
) {
  var current by remember { mutableStateOf(emptyList<Offset>()) }
  var panelSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
  // pointerInput(Unit) launches once: route completion through the
  // latest callbacks, or every stroke after the first overwrites the
  // previous ones (stale `strokes` capture).
  val latestOnStroke by rememberUpdatedState(onStroke)
  val latestOnClear by rememberUpdatedState(onClear)
  val inks = canvasInks(
    MaterialTheme.colorScheme.surface,
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.onSurface,
  )
  // Fitted geometry for gestures (composition values drive draw).
  val latestGeo by rememberUpdatedState(squareSidePx to squareOffsetPx)
  fun geometry(): Pair<Float, Offset> {
    val (side, off) = latestGeo
    if (side != null && side > 0f) return side to off
    val fit = minOf(panelSize.width, panelSize.height).toFloat()
    return fit to Offset((panelSize.width - fit) / 2f, (panelSize.height - fit) / 2f)
  }
  fun norm(o: Offset): Offset {
    val (side, off) = geometry()
    if (panelSize.width == 0 || side == 0f) {
      return o
    }
    // Square fractions, clamped: touches in the letterbox bands map
    // to the square edges, never out-of-range fractions.
    return Offset(
      ((o.x - off.x) / side).coerceIn(0f, 1f),
      ((o.y - off.y) / side).coerceIn(0f, 1f),
    )
  }
  Box(modifier) {
    Canvas(
      modifier = Modifier.fillMaxSize()
        .onSizeChanged { panelSize = it }
        .testTag("recognizerCanvas")
        .pointerInput(Unit) {
          detectTapGestures(
            onLongPress = {
              current = emptyList()
              latestOnClear()
            },
          )
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { current = listOf(norm(it)) },
            onDrag = { change, _ ->
              change.consume()
              val pos = norm(change.position)
              val last = current.lastOrNull()
              if (last == null || (pos - last).getDistance() * panelSize.width > 1.2f) {
                current = current + pos
              }
            },
            onDragEnd = {
              if (current.isNotEmpty()) {
                latestOnStroke(current)
                current = emptyList()
              }
            },
          )
        },
    ) {
      // Square fractions of the fitted, centered square: grid and
      // strokes share one mapping, so cells stay square and drawing
      // stays registered at any window size.
      val side = squareSidePx ?: minOf(size.width, size.height)
      val off = if (squareSidePx != null) {
        squareOffsetPx
      } else {
        Offset((size.width - side) / 2f, (size.height - side) / 2f)
      }
      drawRect(inks.paper)
      if (grid) {
        for (f in listOf(0.1f, 0.5f, 0.9f)) {
          drawLine(Color.Gray, Offset(off.x + side * f, off.y), Offset(off.x + side * f, off.y + side), strokeWidth = 1f)
          drawLine(Color.Gray, Offset(off.x, off.y + side * f), Offset(off.x + side, off.y + side * f), strokeWidth = 1f)
        }
      }
      fun Path.lines(pts: List<Offset>) {
        val px = pts.map { Offset(off.x + it.x * side, off.y + it.y * side) }
        if (px.isEmpty()) return
        moveTo(px[0].x, px[0].y)
        for (p in px.drop(1)) lineTo(p.x, p.y)
      }
      for (s in strokes) drawPath(Path().apply { lines(s) }, inks.final, style = Stroke(6f))
      if (current.isNotEmpty()) drawPath(Path().apply { lines(current) }, inks.active, style = Stroke(6f))
    }
    if (strokes.isEmpty() && current.isEmpty()) {
      // Below the top (0.1) grid line of the fitted square, so the
      // line stays readable; the hint rides the square, never the
      // window, so it never recenters on resize.
      val density = LocalDensity.current
      val hintTop = squareSidePx?.let {
        with(density) { (squareOffsetPx.y + 0.1f * it).toDp() } + 8.dp
      } ?: 16.dp
      Text(
        helpText,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.TopCenter)
          .padding(top = hintTop, start = 32.dp, end = 32.dp)
          .testTag("canvasHint"),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun RecognizerCanvasPreview() {
  BakajitenTheme {
    RecognizerCanvas(
      strokes = listOf(listOf(Offset(0.1f, 0.1f), Offset(0.9f, 0.9f))),
      onStroke = {},
      grid = true,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RecognizerCanvasNoGridPreview() {
  BakajitenTheme {
    RecognizerCanvas(strokes = emptyList(), onStroke = {}, grid = false)
  }
}
