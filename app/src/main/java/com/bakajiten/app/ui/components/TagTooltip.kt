package com.bakajiten.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

// Badge tap: description + tap point in window coordinates (for
// placing the shared tooltip where the finger landed).
data class TipTap(val tip: String, val window: Offset)

// Positions the shared tag tooltip above the tap point (below when
// there is no room), centered on it and clamped to the window.
// Falls back to the anchor when the tap point is unknown.

// Pure offset math (headless-testable without composition).
fun tapPopupOffset(
  tap: Offset,
  anchorBounds: IntRect,
  windowSize: IntSize,
  popupContentSize: IntSize,
  marginPx: Int,
): IntOffset {
  if (tap == Offset.Unspecified) {
    return IntOffset(
      (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
        .coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
      (anchorBounds.top - popupContentSize.height - marginPx)
        .coerceAtLeast(0),
    )
  }
  val x = (tap.x - popupContentSize.width / 2).toInt()
    .coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
  val above = (tap.y - popupContentSize.height - marginPx).toInt()
  val y = if (above >= 0) {
    above
  } else {
    (tap.y + marginPx).toInt()
      .coerceAtMost(maxOf(0, windowSize.height - popupContentSize.height))
  }
  return IntOffset(x, y)
}

@Composable
fun rememberTapPositionProvider(tap: Offset): PopupPositionProvider {
  val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
  return remember(tap, marginPx) {
    object : PopupPositionProvider {
      override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
      ): IntOffset = tapPopupOffset(tap, anchorBounds, windowSize, popupContentSize, marginPx)
    }
  }
}
