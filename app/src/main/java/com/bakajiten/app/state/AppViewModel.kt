package com.bakajiten.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.DictEntry
import com.bakajiten.app.data.DictionaryBackend
import com.bakajiten.app.data.KanjiBackend
import com.bakajiten.app.data.KanjiEntry
import com.bakajiten.app.data.RecognizerBackend
import com.bakajiten.app.data.FuriganaBackend
import com.bakajiten.app.data.RustKana
import com.bakajiten.app.data.RustShaping
import com.bakajiten.app.data.SearchHit
import com.bakajiten.app.data.SearchMode
import com.bakajiten.app.data.SettingsRepository
import com.bakajiten.app.domain.NumberedSense
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
 * Backend bundle. Display shaping runs in Rust (pure UniFFI free
 * functions — no artifacts needed, but the native library must
 * load); the function fields default to it so tests exercise the
 * real shaping, while previews pass canned fixtures (Studio cannot
 * load the host cdylib). Fake search backends stay dumb data.
 */
class Backends(
  val dict: DictionaryBackend,
  val kanji: KanjiBackend,
  val recognizer: RecognizerBackend,
  val furigana: FuriganaBackend,
  val displaySenses: (DictEntry) -> List<NumberedSense> = RustShaping::displaySenses,
  val matchRange: (String, String) -> IntRange? = RustShaping::matchRange,
  val preferKeb: (DictEntry, String) -> String = RustShaping::preferKeb,
  val preferReb: (DictEntry, String) -> String = RustShaping::preferReb,
  val queryFor: (String) -> String = RustShaping::queryFor,
  val isCommonWord: (DictEntry) -> Boolean = RustShaping::isCommonWord,
  val displayReading: (String, Boolean, Boolean) -> String = RustShaping::displayReading,
  val romajiToKana: (String) -> String = RustKana::convert,
  val radicalGlyph: (Int) -> String = RustShaping::radicalGlyph,
  val kanjiDetails: (KanjiEntry) -> List<uniffi.dict.KanjiDetailRow> = RustShaping::kanjiDetails,
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
    val q = if (_settings.value.meaning) raw else backends.romajiToKana(raw)
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

  fun updateSettings(transform: (AppSettings) -> AppSettings) {
    viewModelScope.launch { settingsRepo.update(transform) }
  }

  private fun runSearch(converted: String? = null) {
    val s = _settings.value
    val raw = _ui.value.query
    val q = converted ?: if (s.meaning) raw else backends.romajiToKana(raw)
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
