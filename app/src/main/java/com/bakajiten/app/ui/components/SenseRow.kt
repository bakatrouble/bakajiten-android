package com.bakajiten.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bakajiten.app.domain.NumberedSense
import com.bakajiten.app.domain.TagKind
import com.bakajiten.app.domain.matchRange
import kotlinx.coroutines.launch

// One sense row: number, tags, and glosses in a single Text, so
// every span sits on one baseline (separate Texts with different
// font sizes in a FlowRow floated the small tags above the body
// baseline). The query-match span is highlighted; only tags carry
// ink. Tags are tappable: a dashed underline marks them, and a tap
// shows the full description in the row's tooltip, placed where
// the finger landed.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenseRow(
  row: NumberedSense,
  highlight: String,
  modifier: Modifier = Modifier,
) {
  // One tooltip per row, anchored to the row itself: the article is
  // a plain Column (never recycled), so no per-badge popup nodes
  // are needed. It opens at the last tap point.
  var tipTap by remember { mutableStateOf(TipTap("", Offset.Unspecified)) }
  val tipState = rememberTooltipState()
  val scope = rememberCoroutineScope()
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    TooltipBox(
      positionProvider = rememberTapPositionProvider(tipTap.window),
      tooltip = { PlainTooltip { Text(tipTap.tip) } },
      state = tipState,
      modifier = modifier,
    ) {
      Column {
        SenseText(
          rows = listOf(row),
          highlight = highlight,
          maxLines = Int.MAX_VALUE,
          overflow = TextOverflow.Clip,
          // Full width like the list line: hit-testing maps taps
          // against the laid-out line, not an intrinsic sliver.
          modifier = Modifier.fillMaxWidth(),
          onBadgeTap = { tap ->
            tipTap = tap
            scope.launch { tipState.show() }
          },
        )
        if (row.sense.restrictedKanji.isNotEmpty()) {
          Text(
            "(kanji only: ${row.sense.restrictedKanji.joinToString(", ")})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (row.sense.restrictedReading.isNotEmpty()) {
          Text(
            "(reading only: ${row.sense.restrictedReading.joinToString(", ")})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

// Every content sense flattened into one ellipsized line for the
// results list (`1. vt to eat  2. vi to live …`). Tags are plain
// spans in a single Text (no inline composables, no per-row popups:
// their layout nodes must never feed the rect tracker while rows
// recycle); each badge range gets a dashed underline drawn in one
// drawBehind pass over the same layout. Tapping one reports its
// tip plus the window tap point through onBadgeTap — the caller
// shows it in the one shared tooltip at the tap. Shares SenseText
// with the article row.

@Composable
fun SensesLine(
  rows: List<NumberedSense>,
  highlight: String,
  modifier: Modifier = Modifier,
  onBadgeTap: (TipTap) -> Unit = {},
) {
  SenseText(
    rows = rows,
    highlight = highlight,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
    onBadgeTap = onBadgeTap,
  )
}

// Shared sense text: number, tag spans, and glosses in a single
// Text on one baseline, with dashed badge underlines and tap
// reporting. Single-line ellipsized for the results list,
// multi-line for the article row.

// One badge range with its ink color for the dashed pass.
private data class BadgeSpan(val start: Int, val end: Int, val color: Color)

private fun Modifier.badgeDashes(
  spans: List<BadgeSpan>,
  layout: TextLayoutResult?,
  stroke: androidx.compose.ui.unit.Dp = 1.dp,
) = drawBehind {
  if (layout == null || spans.isEmpty()) return@drawBehind
  val w = stroke.toPx()
  val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
  val len = layout.layoutInput.text.length
  for (span in spans) {
    var line = -1
    var open = false
    var left = 0f
    var right = 0f
    var bottom = 0f
    fun flush() {
      if (!open) return
      open = false
      drawLine(
        color = span.color,
        start = Offset(left, bottom - w / 2),
        end = Offset(right, bottom - w / 2),
        strokeWidth = w,
        pathEffect = dash,
      )
    }
    for (off in span.start until span.end) {
      if (off < 0 || off >= len) continue
      val box = layout.getBoundingBox(off)
      val l = layout.getLineForOffset(off)
      if (!open || l != line) {
        flush()
        line = l
        left = box.left
        right = box.right
        bottom = box.bottom
        open = true
      } else {
        if (box.left < left) left = box.left
        if (box.right > right) right = box.right
        if (box.bottom > bottom) bottom = box.bottom
      }
    }
    flush()
  }
}

@Composable
private fun SenseText(
  rows: List<NumberedSense>,
  highlight: String,
  maxLines: Int,
  overflow: TextOverflow,
  modifier: Modifier = Modifier,
  onBadgeTap: (TipTap) -> Unit = {},
) {
  val pos = MaterialTheme.colorScheme.tertiary
  val error = MaterialTheme.colorScheme.error
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  val tagSize = MaterialTheme.typography.labelSmall.fontSize
  val badgeSpans = mutableListOf<BadgeSpan>()
  val inline = buildAnnotatedString {
    rows.forEachIndexed { r, row ->
      if (r > 0) append("  ")
      append("${row.n}. ")
      row.sense.badges.forEachIndexed { i, badge ->
        val color = when {
          badge == "kana" -> error
          row.sense.kinds.getOrNull(i) == TagKind.MISC -> muted
          row.sense.kinds.getOrNull(i) == TagKind.FIELD -> Color(0xFF1E88E5)
          else -> pos
        }
        val start = length
        withStyle(
          SpanStyle(
            color = color,
            fontSize = tagSize,
          ),
        ) {
          append(badge)
        }
        badgeSpans += BadgeSpan(start, length, color)
        addStringAnnotation("tip", row.sense.tips.getOrNull(i) ?: "", start, length)
        append(" ")
      }
      val gloss = row.sense.glosses.joinToString("; ")
      val range = matchRange(gloss, highlight)
      if (range == null) {
        append(gloss)
      } else {
        append(gloss.substring(0, range.first))
        withStyle(SpanStyle(color = pos, fontWeight = FontWeight.SemiBold)) {
          append(gloss.substring(range.first, range.last + 1))
        }
        append(gloss.substring(range.last + 1))
      }
    }
  }
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  // Window coordinates of this Text for tap-placed tooltips, kept
  // in a plain holder (not state): position updates on every scroll
  // frame must never recompose.
  val coordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
  val density = LocalDensity.current
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Text(
      inline,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = maxLines,
      overflow = overflow,
      onTextLayout = { layout = it },
      modifier = modifier.badgeDashes(badgeSpans, layout)
        .onGloballyPositioned { coordsHolder[0] = it }
        .pointerInput(onBadgeTap) {
          // Observe only, never consume: taps on plain gloss must
          // still reach the row. A badge tap reports its tip and
          // window tap point for the caller's tooltip.
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            // Tap = quick down-up in place; anything else is scroll
            // (cancelled) or press-and-hold.
            if ((up.position - down.position).getDistance() > with(density) { 8.dp.toPx() }) {
              return@awaitEachGesture
            }
            val offset = layout?.getOffsetForPosition(up.position) ?: return@awaitEachGesture
            val window = coordsHolder[0]?.localToWindow(up.position) ?: Offset.Unspecified
            // Primary: the offset's own annotation. Fallback: the tap
            // sits geometrically on a badge's glyphs while the offset
            // next to them carries no tip (tap slop at glyph edges).
            val tip = inline.getStringAnnotations("tip", offset, offset)
              .firstOrNull()?.item?.takeIf { it.isNotEmpty() }
              ?: badgeSpans.firstOrNull { span ->
                // contains() excludes boundary points (a zero-width
                // box never contains anything); compare manually with
                // a sliver of tap slop instead.
                (span.start until span.end).any { off ->
                  val box = layout?.getBoundingBox(off) ?: return@any false
                  val p = up.position
                  p.x >= box.left - 0.5f && p.x <= box.right + 0.5f &&
                    p.y >= box.top - 0.5f && p.y <= box.bottom + 0.5f
                }
              }?.let { span ->
                inline.getStringAnnotations("tip", span.start, span.start)
                  .firstOrNull()?.item?.takeIf { it.isNotEmpty() }
              }
            if (tip != null) onBadgeTap(TipTap(tip, window))
          }
        },
    )
  }
}

@androidx.compose.runtime.Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun SenseRowPreview() {
  com.bakajiten.app.theme.BakajitenTheme {
    SenseRow(
      com.bakajiten.app.domain.NumberedSense(
        com.bakajiten.app.domain.SenseDisplay(
          badges = listOf("vt", "arch"),
          tips = listOf("transitive verb", "archaic"),
          kinds = listOf(
            com.bakajiten.app.domain.TagKind.POS,
            com.bakajiten.app.domain.TagKind.MISC,
          ),
          glosses = listOf("to eat", "to live on"),
          xref = emptyList(),
          ant = emptyList(),
          info = emptyList(),
          sources = emptyList(),
          restrictedKanji = emptyList(),
          restrictedReading = emptyList(),
          uk = false,
        ),
        1,
      ),
      highlight = "eat",
    )
  }
}
