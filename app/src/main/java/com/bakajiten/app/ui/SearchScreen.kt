package com.bakajiten.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.Scope
import com.bakajiten.app.data.SearchBarPosition
import com.bakajiten.app.state.Backends
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.KanaFieldState
import com.bakajiten.app.ui.components.RecognizerToggleButton
import com.bakajiten.app.ui.components.SearchOptionsMenu
import com.bakajiten.app.ui.components.SearchFieldRow

// Search screen: the search bar docks top or bottom per settings
// (phones rest thumbs at the bottom); the list fills the rest.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  query: String,
  field: KanaFieldState,
  onQuery: (String) -> Unit,
  settings: AppSettings,
  onSettings: (AppSettings) -> Unit,
  backends: Backends,
  status: String,
  hits: List<com.bakajiten.app.data.SearchHit>,
  onOpen: (Int) -> Unit,
  onScope: (Scope) -> Unit,
  onOpenSettings: () -> Unit,
  recognizerOpen: Boolean,
  onRecognizer: () -> Unit,
  recognizerPanel: @Composable () -> Unit,
  onHideRecognizer: () -> Unit = {},
  cachedScopes: Set<Scope> = Scope.entries.toSet(),
  onOpenDictionaries: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(if (settings.searchBarPosition == SearchBarPosition.TOP) "searchTop" else "searchBottom"),
    topBar = {
      if (settings.searchBarPosition == SearchBarPosition.TOP) {
        TopAppBar(
            title = {},
            actions = {
              RecognizerToggleButton(recognizerOpen, onRecognizer)
              SearchFieldRow(
                field,
                onQuery,
                settings.meaning,
                Modifier.weight(1f),
                // No virtual keyboard while the panel is open; the
                // field stays editable, focusable and selectable.
                showKeyboardOnFocus = !recognizerOpen,
              )
              SearchOptionsMenu(
                prefix = settings.prefix,
                suffix = settings.suffix,
                meaning = settings.meaning,
                onPrefix = { onSettings(settings.copy(prefix = it)) },
                onSuffix = { onSettings(settings.copy(suffix = it)) },
                onMeaning = { onSettings(settings.copy(meaning = it)) },
                scope = settings.scope,
                onScope = onScope,
                onSettings = onOpenSettings,
                cachedScopes = cachedScopes,
                onOpenDictionaries = onOpenDictionaries,
                affixRowTop = settings.searchBarPosition == SearchBarPosition.TOP,
              )
            },
          )
      }
    },
    bottomBar = {
      if (settings.searchBarPosition == SearchBarPosition.BOTTOM) {
        Column(Modifier.imePadding()) {
          BottomAppBar(
            actions = {
              RecognizerToggleButton(recognizerOpen, onRecognizer)
              SearchFieldRow(
                field,
                onQuery,
                settings.meaning,
                Modifier.weight(1f),
                // No virtual keyboard while the panel is open; the
                // field stays editable, focusable and selectable.
                showKeyboardOnFocus = !recognizerOpen,
              )
              SearchOptionsMenu(
                prefix = settings.prefix,
                suffix = settings.suffix,
                meaning = settings.meaning,
                onPrefix = { onSettings(settings.copy(prefix = it)) },
                onSuffix = { onSettings(settings.copy(suffix = it)) },
                onMeaning = { onSettings(settings.copy(meaning = it)) },
                scope = settings.scope,
                onScope = onScope,
                onSettings = onOpenSettings,
                cachedScopes = cachedScopes,
                onOpenDictionaries = onOpenDictionaries,
                affixRowTop = settings.searchBarPosition == SearchBarPosition.TOP,
              )
            },
          )
        }
      }
    },
  ) { padding ->
    // Opening the panel hides a live keyboard. Focus and selection
    // stay untouched; showKeyboardOnFocus keeps any keyboard from
    // coming back while the panel is open (the field itself never
    // changes, so its autofill node stays put).
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(recognizerOpen) {
      if (recognizerOpen) keyboard?.hide()
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
      // The list keeps its full size underneath; the handwriting
      // panel floats above it (top or bottom per dock) instead of
      // squeezing it — over the matches count too. Capped to the
      // content area so a tall panel never paints over the search
      // bars.
      Box(
        Modifier.weight(1f),
      ) {
        Column(Modifier.fillMaxSize()) {
          if (status != "") {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
              Text(status, modifier = Modifier.testTag("searchStatus"))
            }
          }
          Box(
            Modifier.weight(1f),
          ) {
            ResultsList(
              hits,
              query,
              backends,
              onOpen,
              Modifier.fillMaxSize(),
              onBackgroundTap = onHideRecognizer,
              panelOpen = recognizerOpen,
              meaning = settings.meaning,
              romaji = settings.romaji,
            )
          }
        }
        if (recognizerOpen) {
          // Semitransparent mask over everything under the panel:
          // purely visual, handles no input, so dismiss + navigate
          // underneath keep working.
          Box(
            Modifier.matchParentSize()
              .background(Color.Black.copy(alpha = 0.45f))
              .testTag("listMask"),
          )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
          Box(
            Modifier.align(if (settings.searchBarPosition == SearchBarPosition.BOTTOM) Alignment.BottomStart else Alignment.TopStart)
              .fillMaxWidth()
              .heightIn(max = maxHeight),
          ) {
            PanelSlot(
              fromBottom = settings.searchBarPosition == SearchBarPosition.BOTTOM,
              visible = recognizerOpen,
            ) {
              recognizerPanel()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PanelSlot(
  fromBottom: Boolean,
  visible: Boolean,
  content: @Composable () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter = expandVertically(
      expandFrom = if (fromBottom) Alignment.Bottom else Alignment.Top,
    ) + fadeIn(),
    exit = shrinkVertically(
      shrinkTowards = if (fromBottom) Alignment.Bottom else Alignment.Top,
    ) + fadeOut(),
  ) {
    content()
  }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
  val fake = FakeDictionaryBackend()
  val backends = Backends(
    fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    fake::posLabel, fake::posTip, { "" }, { "" }, fake::miscLabel, fake::miscTip,
  )
  var query by remember { mutableStateOf("たべる") }
  BakajitenTheme {
    val previewField = remember(query) { KanaFieldState(query) }
    SearchScreen(
      query = query,
      field = previewField,
      onQuery = { query = it },
      settings = AppSettings(),
      onSettings = {},
      backends = backends,
      status = "2 matches",
      hits = fake.searchIndices("たべる", com.bakajiten.app.data.SearchMode.EXACT, 50),
      onOpen = {},
      onScope = {},
      onOpenSettings = {},
      recognizerOpen = false,
      onRecognizer = {},
      recognizerPanel = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenBottomPreview() {
  val fake = FakeDictionaryBackend()
  val backends = Backends(
    fake, FakeKanjiBackend(), FakeRecognizerBackend(), FakeFuriganaBackend(),
    fake::posLabel, fake::posTip, { "" }, { "" }, fake::miscLabel, fake::miscTip,
  )
  BakajitenTheme {
    val previewFieldBottom = remember("あ") { KanaFieldState("あ") }
    SearchScreen(
      query = "あ",
      field = previewFieldBottom,
      onQuery = {},
      settings = AppSettings(searchBarPosition = SearchBarPosition.BOTTOM),
      onSettings = {},
      backends = backends,
      status = "2 matches",
      hits = fake.searchIndices("あ", com.bakajiten.app.data.SearchMode.PREFIX, 50),
      onOpen = {},
      onScope = {},
      onOpenSettings = {},
      recognizerOpen = true,
      onRecognizer = {},
      recognizerPanel = {},
    )
  }
}
