package com.bakajiten.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.Candidate
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.StrokePoint
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.CandidateStrip
import com.bakajiten.app.ui.components.RecognizerCanvas

// Handwriting panel: grid canvas, options, candidate strip. Canvas
// sizing (fraction + explicit height) persists; the grid toggle is
// session-local, like the desktop canvas chrome.

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecognizerPanel(
  strokes: List<List<Offset>>,
  onStrokes: (List<List<Offset>>) -> Unit,
  candidates: List<Candidate>,
  kanaCandidates: Boolean,
  onKana: (Boolean) -> Unit,
  onPick: (String) -> Unit,
  canvasHeightDp: Float? = null,
  onCanvasHeightDp: (Float?) -> Unit = {},
  toolbarOnTop: Boolean = false,
  // Test seam: reports the fitted square side in px.
  onSquareSidePx: ((Float) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  // Live height while dragging; the persisted value lands on
  // release. Read once per mount: re-keying on the prop would snap
  // a later drag back when the async settings write lands.
  var heightLive by remember { mutableStateOf(canvasHeightDp) }
  // Fitted square side in px (min window edge); the persisted
  // height sizes the window only, never the grid.
  var squarePx by remember { mutableFloatStateOf(0f) }
  var grid by remember { mutableStateOf(true) }
  // Redo stack for the undo button: popped strokes wait here until
  // the next new stroke (or a clear) drops them.
  var redo by remember { mutableStateOf(emptyList<List<Offset>>()) }
  fun addStroke(stroke: List<Offset>) {
    onStrokes(strokes + listOf(stroke))
    redo = emptyList()
  }
  fun undoStroke() {
    if (strokes.isNotEmpty()) {
      redo = redo + listOf(strokes.last())
      onStrokes(strokes.dropLast(1))
    }
  }
  fun redoStroke() {
    if (redo.isNotEmpty()) {
      onStrokes(strokes + listOf(redo.last()))
      redo = redo.dropLast(1)
    }
  }
  fun clearStrokes() {
    onStrokes(emptyList())
    redo = emptyList()
  }
  val density = LocalDensity.current.density
  // The whole panel (canvas + toolbar + candidates) never exceeds
  // 80% of the screen height; taller content scrolls inside it.
  // No keyboard reaction here by design: the search field is
  // read-only while the panel is open, so no virtual keyboard ever
  // shares the screen with it.
  val panelMaxDp = LocalConfiguration.current.screenHeightDp.toFloat() * 0.8f
  val maxHeightDp = panelMaxDp
  val growSign = if (toolbarOnTop) -1f else 1f
  // Resize pill as a full-width edge strip in the content flow, so
  // the whole hit area lands: top edge for a bottom dock (above the
  // toolbar), bottom edge for a top dock (below the candidates).
  // Vertical drag sets the canvas height. Release persists.
  @Composable
  fun GripStrip() {
    // Claims the down event so the panel scroll (80% cap) never
    // steals a resize drag; moves below touch slop are taps.
    // NOTE: never `return` early out of awaitEachGesture to disable
    // it — it loops forever and spins the main thread.
    val touchSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
      Modifier.fillMaxWidth()
        .height(28.dp)
        .testTag("canvasGrip")
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            var acc = 0f
            var pastSlop = false
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.find { it.id == down.id } ?: break
              if (!change.pressed) {
                if (pastSlop) onCanvasHeightDp(heightLive)
                change.consume()
                break
              }
              val dy = change.position.y - change.previousPosition.y
              if (!pastSlop) {
                acc += dy
                if (kotlin.math.abs(acc) > touchSlopPx) pastSlop = true
              }
              if (pastSlop) {
                change.consume()
                // Drag from the current window height: the persisted
                // value is the window, never the fitted square.
                val basePx = heightLive?.times(density) ?: squarePx
                heightLive =
                  ((basePx + growSign * dy) / density).coerceIn(120f, maxHeightDp)
              }
            }
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier.width(48.dp)
          .height(4.dp)
          .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
      )
    }
  }
  Box(
    modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).testTag("recognizerPanel"),
  ) {
    Column(
      Modifier.fillMaxWidth()
        .heightIn(max = panelMaxDp.dp)
        .verticalScroll(rememberScrollState()),
    ) {
      // Options row below the canvas normally, above it when docked
      // at the bottom.
      @Composable
      fun OptionsRow() {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ToggleButton(
            checked = kanaCandidates,
            onCheckedChange = onKana,
            modifier = Modifier.testTag("kanaToggle"),
          ) {
            Text(if (kanaCandidates) "あ" else "ア")
          }
          ToggleButton(
            checked = grid,
            onCheckedChange = { grid = it },
            modifier = Modifier.testTag("gridToggle"),
          ) {
            Text("田")
          }
          IconButton(
            onClick = ::undoStroke,
            modifier = Modifier.testTag("undoButton"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke")
          }
          IconButton(
            onClick = ::redoStroke,
            modifier = Modifier.testTag("redoButton"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo stroke")
          }
          IconButton(onClick = ::clearStrokes, modifier = Modifier.testTag("clearButton")) {
            Icon(Icons.Filled.Clear, contentDescription = "Clear drawing")
          }
        }
      }
      if (toolbarOnTop) {
        GripStrip()
      }
      // Canvas window: the square grid fits the min edge,
      // centered, so it stays square at any window size or aspect
      // ratio. The window is the persisted height (square by
      // default); it never reacts to the keyboard — the search
      // field is read-only while the panel is open.
      CanvasViewport(
        heightLive = heightLive,
        strokes = strokes,
        onStroke = { addStroke(it) },
        grid = grid,
        onClear = { clearStrokes() },
        onSquareSidePx = {
          squarePx = it
          onSquareSidePx?.invoke(it)
        },
      )
      OptionsRow()
      CandidateStrip(candidates = candidates, onPick = onPick)
      if (!toolbarOnTop) {
        GripStrip()
      }
    }
  }
}

// Canvas viewport: the canvas itself is window-sized and draws
// the fitted square (side = min edge) centered, so the grid stays
// square at any window size or aspect ratio. Strokes map to the
// same square fractions, so drawing stays registered. There is
// deliberately no full-size inner box: an overflow child inside the
// panel's scrolling column gets coerced and mis-placed (bounded
// top-down measuring when content overflows the 80% cap).

@Composable
internal fun CanvasViewport(
  heightLive: Float?,
  strokes: List<List<Offset>>,
  onStroke: (List<Offset>) -> Unit,
  grid: Boolean,
  onClear: () -> Unit,
  onSquareSidePx: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Measured window height in px (the panel column may squeeze it
  // below the request when content overflows the 80% cap).
  var windowHPx by remember { mutableFloatStateOf(0f) }
  BoxWithConstraints(
    modifier.fillMaxWidth().then(
      if (heightLive != null) {
        Modifier.height(requireNotNull(heightLive).dp)
      } else {
        Modifier.aspectRatio(1f)
      },
    ).clipToBounds().onSizeChanged { windowHPx = it.height.toFloat() }.testTag("canvasViewport"),
  ) {
    // Fitted square: side = min edge, centered in the measured window.
    // (Measured, not requested: the panel column may squeeze the
    // window below the request when content overflows the 80% cap.)
    val density = LocalDensity.current
    val windowHdp = if (windowHPx > 0f) {
      with(density) { windowHPx.toDp() }
    } else {
      heightLive?.dp ?: maxWidth
    }
    val sideDp = minOf(maxWidth, windowHdp)
    val sidePx = with(density) { sideDp.toPx() }
    val offXPx = with(density) { ((maxWidth - sideDp) / 2).toPx() }
    val offYPx = with(density) { ((windowHdp - sideDp) / 2).toPx() }
    SideEffect { onSquareSidePx(sidePx) }
    RecognizerCanvas(
      strokes = strokes,
      // Explicit lambdas, never ::refs: a bound reference to a
      // local fun does not invalidate rememberUpdatedState in
      // the canvas, so later strokes would close over the
      // first composition's list.
      onStroke = onStroke,
      grid = grid,
      onClear = onClear,
      squareSidePx = sidePx,
      squareOffsetPx = Offset(offXPx, offYPx),
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RecognizerPanelPreview() {
  val backend = FakeRecognizerBackend()
  val pen = listOf(listOf(StrokePoint(10f, 10f), StrokePoint(200f, 200f)))
  val candidates = backend.recognize(pen, 5, true)
  BakajitenTheme {
    RecognizerPanel(
      strokes = listOf(listOf(Offset(0.1f, 0.1f), Offset(0.9f, 0.9f))),
      onStrokes = {},
      candidates = candidates,
      kanaCandidates = true,
      onKana = {},
      onPick = {},
      toolbarOnTop = false,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RecognizerPanelTopPreview() {
  val backend = FakeRecognizerBackend()
  val pen = listOf(listOf(StrokePoint(10f, 10f), StrokePoint(200f, 200f)))
  val candidates = backend.recognize(pen, 5, true)
  BakajitenTheme {
    RecognizerPanel(
      strokes = listOf(listOf(Offset(0.1f, 0.1f), Offset(0.9f, 0.9f))),
      onStrokes = {},
      candidates = candidates,
      kanaCandidates = true,
      onKana = {},
      onPick = {},
      toolbarOnTop = true,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RecognizerPanelEmptyPreview() {
  val backend = FakeRecognizerBackend()
  val pen = listOf(listOf(StrokePoint(10f, 10f), StrokePoint(200f, 200f)))
  val candidates = backend.recognize(pen, 5, true)
  BakajitenTheme {
    RecognizerPanel(
      strokes = listOf(),
      onStrokes = {},
      candidates = candidates,
      kanaCandidates = true,
      onKana = {},
      onPick = {},
      toolbarOnTop = true,
    )
  }
}
