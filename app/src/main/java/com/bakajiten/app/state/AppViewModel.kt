package com.bakajiten.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.DictionaryBackend
import com.bakajiten.app.data.KanjiBackend
import com.bakajiten.app.data.RecognizerBackend
import com.bakajiten.app.data.FuriganaBackend
import com.bakajiten.app.data.SearchHit
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.data.SettingsRepository
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.displaySenses
import com.bakajiten.app.domain.romajiToKana
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Search + navigation state. Mirrors the Qt DictionaryWidget and
// web search store: 200 ms debounce, pending-query park while the
// backend loads, affix-toggles → SearchMode, meaning-mode switch.

// Full result set: every tier, uncapped (the list virtualizes).
const val ALL = Int.MAX_VALUE

sealed interface Screen {
  data object Search : Screen
  /** Article for the hit at [index]; a separate screen on phones. */
  data class Article(val index: Int) : Screen
  data object Settings : Screen
  data object Dictionaries : Screen
  data class Kanji(val character: String) : Screen
}

data class UiState(
  val query: String = "",
  val hits: List<SearchHit> = emptyList(),
  val status: String = "Loading dictionaries…",
  val ready: Boolean = false,
  val screen: Screen = Screen.Search,
  /** Full navigation history (oldest first); back pops it. */
  val history: List<Screen> = listOf(Screen.Search),
  /** Direction of the last navigation (push vs pop). */
  val popping: Boolean = false,
)

/**
 * Backend bundle. Label/furigana functions come from the same
 * source as the dictionaries (fake tables in tests, UniFFI in
 * production), so display shaping never drifts from data.
 */
class Backends(
  val dict: DictionaryBackend,
  val kanji: KanjiBackend,
  val recognizer: RecognizerBackend,
  val furigana: FuriganaBackend,
  val posLabel: (Int) -> String,
  val posTip: (Int) -> String,
  val fieldLabel: (Int) -> String,
  val fieldTip: (Int) -> String,
  val miscLabel: (Int) -> String,
  val miscTip: (Int) -> String,
)

class AppViewModel(
  private val settingsRepo: SettingsRepository,
  private val backends: Backends,
) : ViewModel() {
  private val _ui = MutableStateFlow(UiState())
  val ui: StateFlow<UiState> = _ui.asStateFlow()

  private val _settings = MutableStateFlow(AppSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private var searchJob: Job? = null

  init {
    viewModelScope.launch {
      settingsRepo.settings.collect { s ->
        _settings.value = s
        runSearch()
      }
    }
    _ui.update { it.copy(ready = true, status = "") }
  }

  fun onQuery(raw: String) {
    // Romaji converts at the query edge (always hiragana out),
    // except in meaning mode where English goes through raw.
    val q = if (_settings.value.meaning) raw else romajiToKana(raw)
    _ui.update { it.copy(query = raw) }
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
      delay(200)
      runSearch(q)
    }
  }

  fun go(screen: Screen) {
    _ui.update {
      // Repeat visits don't stack (toggle-style navigation).
      if (it.history.lastOrNull() == screen) {
        it.copy(screen = screen, popping = false)
      } else {
        it.copy(screen = screen, history = (it.history + screen).takeLast(50), popping = false)
      }
    }
  }

  fun back(): Boolean {
    val history = _ui.value.history
    if (history.size <= 1) return false
    val next = history.dropLast(1)
    _ui.update { it.copy(screen = next.last(), history = next, popping = true) }
    return true
  }

  fun sensesAt(index: Int): List<SenseDisplay> {
    val hit = _ui.value.hits.getOrNull(index) ?: return emptyList()
    val entry = backends.dict.entryAt(hit.idx) ?: return emptyList()
    return displaySenses(
      entry,
      backends.posLabel, backends.posTip,
      backends.fieldLabel, backends.fieldTip,
      backends.miscLabel, backends.miscTip,
    )
  }

  fun updateSettings(transform: (AppSettings) -> AppSettings) {
    viewModelScope.launch { settingsRepo.update(transform) }
  }

  private fun runSearch(converted: String? = null) {
    val s = _settings.value
    val raw = _ui.value.query
    val q = converted ?: if (s.meaning) raw else romajiToKana(raw)
    if (!_ui.value.ready) return
    if (q == "") {
      _ui.update { it.copy(hits = emptyList(), status = "") }
      return
    }
    val mode = when {
      s.prefix && s.suffix -> SearchMode.MIDDLE
      s.prefix -> SearchMode.PREFIX
      s.suffix -> SearchMode.SUFFIX
      else -> SearchMode.EXACT
    }
    val hits = if (s.meaning) {
      backends.dict.searchMeaningIndices(q, ALL)
    } else {
      backends.dict.searchIndices(q, mode, ALL)
    }
    _ui.update {
      it.copy(
        hits = hits,
        status = if (hits.isEmpty()) "No matches" else "${hits.size} match${if (hits.size == 1) "" else "es"}",
      )
    }
  }
}
