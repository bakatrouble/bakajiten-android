package com.bakajiten.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakajiten.app.data.cannedBackends
import com.bakajiten.app.data.Candidate
import com.bakajiten.app.data.DataManager
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.InMemorySettingsRepository
import com.bakajiten.app.data.SCOPE_FILE
import com.bakajiten.app.data.Scope
import com.bakajiten.app.data.SearchBarPosition
import com.bakajiten.app.data.SearchHit
import com.bakajiten.app.state.AppViewModel
import com.bakajiten.app.state.Backends
import com.bakajiten.app.state.Screen
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.components.KanaFieldState

// Root: theme + navigation. Phones show the article as a new
// screen; wide (tablet/foldable, >= 840dp) screens keep the
// desktop two-pane with the article beside the results.

/** Adaptive width bucket (avoids the window-size-class dep). */
enum class AdaptiveWidth { COMPACT, EXPANDED }

@Composable
fun rememberAdaptiveWidth(): AdaptiveWidth {
  // containerSize is pixels; the 840dp breakpoint needs density.
  val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
  return if (widthDp >= 840.dp) AdaptiveWidth.EXPANDED else AdaptiveWidth.COMPACT
}

@Composable
fun BakajitenApp(
  modifier: Modifier = Modifier,
  vm: AppViewModel,
  backends: Backends,
  widthClass: AdaptiveWidth,
  dataScreen: (@Composable () -> Unit)? = null,
  manager: DataManager? = null,
) {
  val ui by vm.ui.collectAsState()
  val settings by vm.settings.collectAsState()
  var recognizerOpen by remember { mutableStateOf(false) }
  var strokes by remember { mutableStateOf(emptyList<List<Offset>>()) }
  var candidates by remember { mutableStateOf(emptyList<Candidate>()) }

  BackHandler(enabled = ui.history.size > 1) { vm.back() }
  // Back hides the handwriting panel before navigating anywhere.
  BackHandler(enabled = recognizerOpen) { recognizerOpen = false }

  BakajitenTheme(theme = settings.theme, densityDpi = settings.densityDpi) {
    // Directional transitions: pushes slide in from the right,
    // pops (back) always slide the new screen in from the left.
    AnimatedContent(
      targetState = ui.screen,
      transitionSpec = {
        val back = ui.popping
        val enter = if (back) { w: Int -> -w } else { w: Int -> w }
        val exit = if (back) { w: Int -> w } else { w: Int -> -w }
        (slideInHorizontally(initialOffsetX = enter) + fadeIn()) togetherWith
          (slideOutHorizontally(targetOffsetX = exit) + fadeOut())
      },
      label = "screen",
    ) { screen ->
      when (screen) {
        is Screen.Search -> SearchWithDetail(
          vm = vm,
          backends = backends,
          widthClass = widthClass,
          recognizerOpen = recognizerOpen,
          onRecognizer = { recognizerOpen = !recognizerOpen },
          onHideRecognizer = { recognizerOpen = false },
          // Fresh every composition: downloads land while away.
          cachedScopes = manager?.let { m ->
            SCOPE_FILE.filterValues { f -> m.path(f) != null }.keys
          } ?: Scope.entries.toSet(),
          onOpenDictionaries = { vm.go(Screen.Dictionaries) },
          strokes = strokes,
          onStrokes = { strokes = it },
          candidates = candidates,
          onCandidates = { candidates = it },
          modifier = modifier,
        )
        is Screen.Article -> ArticleRoute(vm, backends, screen.index)
        is Screen.Settings -> SettingsScreen(
          settings,
          { vm.updateSettings { _ -> it } },
          { vm.back() },
          { vm.go(Screen.Dictionaries) },
        )
        is Screen.Dictionaries -> {
          if (manager != null) {
            DictionaryManagementScreen(manager = manager, onDone = { vm.go(Screen.Settings) })
          } else {
            Text("Dictionary management is unavailable here.")
          }
        }
        is Screen.Kanji -> KanjiScreen(
          screen.character,
          backends,
          onSearch = {
            vm.onQuery(it)
            vm.go(Screen.Search)
          },
          onBack = { vm.back() },
          onKanji = { vm.go(Screen.Kanji(it)) },
          romaji = settings.romaji,
        )
      }
    }
    if (dataScreen != null && !ui.ready) dataScreen()
  }
}

@Composable
private fun SearchWithDetail(
  vm: AppViewModel,
  backends: Backends,
  widthClass: AdaptiveWidth,
  recognizerOpen: Boolean,
  onRecognizer: () -> Unit,
  onHideRecognizer: () -> Unit,
  cachedScopes: Set<Scope>,
  onOpenDictionaries: () -> Unit,
  strokes: List<List<Offset>>,
  onStrokes: (List<List<Offset>>) -> Unit,
  candidates: List<Candidate>,
  onCandidates: (List<Candidate>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val ui by vm.ui.collectAsState()
  val settings by vm.settings.collectAsState()
  var selected by remember { mutableIntStateOf(0) }

  @Composable
  fun SearchPane() {
    val kanaField = remember { KanaFieldState("") }
    kanaField.syncExternal(ui.query)
    SearchScreen(
      query = ui.query,
      field = kanaField,
      onQuery = vm::onQuery,
      settings = settings,
      onSettings = { vm.updateSettings { _ -> it } },
      backends = backends,
      status = ui.status,
      hits = ui.hits,
      onOpen = { i ->
        selected = i
        vm.go(Screen.Article(i))
      },
      onScope = { vm.updateSettings { s -> s.copy(scope = it) } },
      onOpenSettings = { vm.go(Screen.Settings) },
      recognizerOpen = recognizerOpen,
      onRecognizer = onRecognizer,
      onHideRecognizer = onHideRecognizer,
      cachedScopes = cachedScopes,
      onOpenDictionaries = onOpenDictionaries,
      recognizerPanel = {
        RecognizerPanel(
            strokes = strokes,
            onStrokes = {
              onStrokes(it)
              onCandidates(
                backends.recognizer.recognize(
                  it.map { s -> s.map { p -> com.bakajiten.app.data.StrokePoint(p.x * 255, p.y * 255) } },
                  20,
                  settings.kanaCandidates,
                ),
              )
            },
            candidates = candidates,
            kanaCandidates = settings.kanaCandidates,
            onKana = { vm.updateSettings { s -> s.copy(kanaCandidates = it) } },
            // Picks insert at the cursor (replacing any selection),
            // advancing past the inserted text like the web field.
            onPick = {
              kanaField.insertChar(it, settings.meaning, vm::onQuery)
            },
            canvasHeightDp = settings.recognizerCanvasHeightDp,
            onCanvasHeightDp = { vm.updateSettings { s -> s.copy(recognizerCanvasHeightDp = it) } },
            toolbarOnTop = settings.searchBarPosition == SearchBarPosition.BOTTOM,
          )
      },
    )
  }

  if (widthClass == AdaptiveWidth.EXPANDED) {
    // Desktop-like two-pane: the article stays beside the list.
    Row(modifier.fillMaxSize()) {
      Box(Modifier.weight(1f)) { SearchPane() }
      Box(Modifier.weight(1f)) {
        val hit: SearchHit? = ui.hits.getOrNull(selected)
        if (hit != null) {
          ArticleScreen(
            hit,
            ui.query,
            backends,
            onSearch = vm::onQuery,
            onKanji = { vm.go(Screen.Kanji(it)) },
            onBack = { vm.back() },
            meaning = settings.meaning,
            romaji = settings.romaji,
          )
        } else {
          Text("Select an entry")
        }
      }
    }
  } else {
    SearchPane()
  }
}

@Composable
private fun ArticleRoute(vm: AppViewModel, backends: Backends, index: Int) {
  val ui by vm.ui.collectAsState()
  val settings by vm.settings.collectAsState()
  val hit = ui.hits.getOrNull(index)
  if (hit == null) {
    Text("Entry unavailable")
    return
  }
  ArticleScreen(
    hit,
    ui.query,
    backends,
    onSearch = {
      vm.onQuery(it)
      vm.go(Screen.Search)
    },
    onKanji = { vm.go(Screen.Kanji(it)) },
    onBack = { vm.back() },
    meaning = settings.meaning,
    romaji = settings.romaji,
  )
}

@Preview(showBackground = true)
@Composable
private fun BakajitenAppPreview() {
  val fake = FakeDictionaryBackend()
  val backends = cannedBackends(fake)
  val vm = AppViewModel(InMemorySettingsRepository(), backends)
  BakajitenApp(vm = vm, backends = backends, widthClass = AdaptiveWidth.COMPACT)
}
