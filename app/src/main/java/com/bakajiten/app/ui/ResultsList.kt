package com.bakajiten.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.RomajiStyle
import com.bakajiten.app.data.SearchHit
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.displaySenses
import com.bakajiten.app.domain.displayReading
import com.bakajiten.app.domain.isKanaEntry
import com.bakajiten.app.domain.preferKeb
import com.bakajiten.app.domain.preferReb
import com.bakajiten.app.state.Backends
import com.bakajiten.app.ui.components.ResultRow
import com.bakajiten.app.ui.components.TipTap
import com.bakajiten.app.ui.components.rememberTapPositionProvider
import kotlinx.coroutines.launch

// Virtualized result list (LazyColumn materializes only visible
// rows). Entries resolve lazily, one per visible row: entryAt
// crosses JNA, so resolving the whole (uncapped) hit list up front
// would stall every search commit, keystroke, and back navigation.
// Segmented shapes only need the position and the hit count, so no
// full mapping is ever required.

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ResultsList(
  hits: List<SearchHit>,
  query: String,
  backends: Backends,
  onOpen: (Int) -> Unit,
  modifier: Modifier = Modifier,
  // Taps on the list (rows or empty space) dismiss the handwriting
  // panel behind it on touchstart (finger down, before click/scroll
  // resolves); the down event is only observed, never consumed.
  onBackgroundTap: () -> Unit = {},
  // Whether the panel is currently open: the press that dismisses
  // it also disarms the row tap of the same gesture (the collapsing
  // panel shifts the list, so the release would hit the wrong row).
  panelOpen: Boolean = false,
  // Gloss-match highlighting only makes sense for English
  // (meaning) searches; Japanese queries match the headwords.
  meaning: Boolean = false,
  romaji: RomajiStyle = RomajiStyle.KANA,
) {
  // Armed by a dismissing press or a badge tap, consumed by the
  // next row tap (which then navigates on the tap after that).
  var consumeNextTap by remember { mutableStateOf(false) }
  // One tooltip for the whole list, anchored to the list container
  // (never recycled): badge taps retarget it instead of each row
  // owning a popup. Per-row TooltipBoxes fed the rect tracker from
  // recycled nodes and crashed scrolls (LayoutNode not found in
  // RectList), so rows stay popup-free. It opens at the tap point.
  var tipTap by remember { mutableStateOf(TipTap("", Offset.Unspecified)) }
  val tooltipState = rememberTooltipState()
  val scope = rememberCoroutineScope()
  TooltipBox(
    positionProvider = rememberTapPositionProvider(tipTap.window),
    tooltip = { PlainTooltip { Text(tipTap.tip) } },
    state = tooltipState,
    modifier = modifier.testTag("resultsList"),
  ) {
  LazyColumn(
    Modifier.fillMaxSize().pointerInput(onBackgroundTap, panelOpen) {
      awaitEachGesture {
        // Observe even consumed downs: rows consume presses for
        // their own tap detection, and this watcher must still see
        // them (it never consumes anything itself).
        awaitFirstDown(requireUnconsumed = false)
        if (panelOpen) {
          consumeNextTap = true
          onBackgroundTap()
        } else {
          consumeNextTap = false
        }
      }
    },
    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
  ) {
    itemsIndexed(hits, key = { i, h -> "${h.idx}:$i" }) { i, hit ->
      // Null entries (unknown indices) render nothing; indices come
      // straight from search, so this is not expected to happen —
      // and the shapes below assume dense positions.
      val entry = backends.dict.entryAt(hit.idx) ?: return@itemsIndexed
      val senses = displaySenses(
        entry,
        backends.posLabel, backends.posTip,
        backends.fieldLabel, backends.fieldTip,
        backends.miscLabel, backends.miscTip,
      )
      ResultRow(
        index = i,
        count = hits.size,
        heading = preferKeb(entry, hit.matched.ifEmpty { query }),
        reading = displayReading(
          preferReb(entry, hit.matched.ifEmpty { query }),
          romanize = romaji != RomajiStyle.KANA,
          hepburn = romaji == RomajiStyle.HEPBURN,
        ),
        kanaOnly = isKanaEntry(senses),
        chain = hit.chain,
        senses = senses,
        highlight = if (meaning) hit.matched.ifEmpty { query } else "",
        onBadgeTap = { tap ->
          tipTap = tap
          scope.launch { tooltipState.show() }
          consumeNextTap = true
        },
        onOpen = {
          if (consumeNextTap) {
            consumeNextTap = false
          } else {
            onOpen(i)
          }
        },
      )
    }
  }
  }
}

@androidx.compose.runtime.Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun ResultsListPreview() {
  val fake = com.bakajiten.app.data.FakeDictionaryBackend()
  val backends = com.bakajiten.app.state.Backends(
    fake,
    com.bakajiten.app.data.FakeKanjiBackend(),
    com.bakajiten.app.data.FakeRecognizerBackend(),
    com.bakajiten.app.data.FakeFuriganaBackend(),
    fake::posLabel, fake::posTip, { "" }, { "" }, fake::miscLabel, fake::miscTip,
  )
  com.bakajiten.app.theme.BakajitenTheme {
    ResultsList(
      hits = fake.searchIndices("た", com.bakajiten.app.data.SearchMode.PREFIX, 50),
      query = "た",
      backends = backends,
      onOpen = {},
    )
  }
}
