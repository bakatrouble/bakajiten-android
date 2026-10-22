package com.bakajiten.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// Persisted UI prefs. SearchBarPosition is the Android-specific
// addition: phones put thumbs at the bottom, so the search bar
// docks top or bottom per this setting.

enum class SearchBarPosition { TOP, BOTTOM }
enum class Theme { AUTO, LIGHT, DARK }
enum class RomajiStyle { KANA, HEPBURN, KUNREI }
enum class Scope { ENGLISH, NAMES, KOKUGO }

data class AppSettings(
  val theme: Theme = Theme.AUTO,
  /** Explicit screen density in dpi (null = system default), clamped 120–640. */
  val densityDpi: Int? = null,
  val romaji: RomajiStyle = RomajiStyle.HEPBURN,
  val kanaCandidates: Boolean = true,
  /** Recognizer canvas height in dp (null = aspect-driven). */
  val recognizerCanvasHeightDp: Float? = null,
  val searchBarPosition: SearchBarPosition = SearchBarPosition.TOP,
  val scope: Scope = Scope.ENGLISH,
  val prefix: Boolean = true,
  val suffix: Boolean = false,
  val meaning: Boolean = false,
  /** Automatic app-update checks (startup + hourly). Off opts out. */
  val checkAppUpdates: Boolean = true,
  /** App version the user asked not to be notified about (null = notify about everything newer). */
  val skippedAppVersion: String? = null,
)

interface SettingsRepository {
  val settings: Flow<AppSettings>
  suspend fun update(transform: (AppSettings) -> AppSettings)
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("bakajiten")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
  private val store = context.applicationContext.store

  private fun prefs(p: Preferences) = AppSettings(
    theme = p[stringPreferencesKey("theme")]?.let { runCatching { Theme.valueOf(it) }.getOrNull() } ?: Theme.AUTO,
    densityDpi = p[intPreferencesKey("density_dpi")]?.coerceIn(120, 640),
    romaji = p[stringPreferencesKey("romaji")]?.let { runCatching { RomajiStyle.valueOf(it) }.getOrNull() } ?: RomajiStyle.HEPBURN,
    kanaCandidates = p[booleanPreferencesKey("kana_candidates")] ?: true,
    recognizerCanvasHeightDp = p[floatPreferencesKey("recognizer_canvas_height")],
    searchBarPosition = p[stringPreferencesKey("search_bar")]?.let {
      runCatching { SearchBarPosition.valueOf(it) }.getOrNull()
    } ?: SearchBarPosition.TOP,
    scope = p[stringPreferencesKey("scope")]?.let { runCatching { Scope.valueOf(it) }.getOrNull() } ?: Scope.ENGLISH,
    prefix = p[booleanPreferencesKey("prefix")] ?: true,
    suffix = p[booleanPreferencesKey("suffix")] ?: false,
    meaning = p[booleanPreferencesKey("meaning")] ?: false,
    checkAppUpdates = p[booleanPreferencesKey("check_app_updates")] ?: true,
    skippedAppVersion = p[stringPreferencesKey("skipped_app_version")],
  )

  override val settings: Flow<AppSettings> = store.data.map(::prefs)

  override suspend fun update(transform: (AppSettings) -> AppSettings) {
    val next = transform(store.data.map(::prefs).first())
    store.edit { p ->
      p[stringPreferencesKey("theme")] = next.theme.name
      val dpi = next.densityDpi?.coerceIn(120, 640)
      if (dpi == null) {
        p.remove(intPreferencesKey("density_dpi"))
      } else {
        p[intPreferencesKey("density_dpi")] = dpi
      }
      // Retired percent key: drop it on the next write.
      p.remove(intPreferencesKey("scale"))
      p[stringPreferencesKey("romaji")] = next.romaji.name
      p[booleanPreferencesKey("kana_candidates")] = next.kanaCandidates
      // Retired width-fraction key: drop it on the next write.
      p.remove(floatPreferencesKey("recognizer_canvas"))
      val canvasHeight = next.recognizerCanvasHeightDp
      if (canvasHeight == null) {
        p.remove(floatPreferencesKey("recognizer_canvas_height"))
      } else {
        p[floatPreferencesKey("recognizer_canvas_height")] = canvasHeight.coerceIn(120f, 2000f)
      }
      p[stringPreferencesKey("search_bar")] = next.searchBarPosition.name
      p[stringPreferencesKey("scope")] = next.scope.name
      p[booleanPreferencesKey("prefix")] = next.prefix
      p[booleanPreferencesKey("suffix")] = next.suffix
      p[booleanPreferencesKey("meaning")] = next.meaning
      p[booleanPreferencesKey("check_app_updates")] = next.checkAppUpdates
      val skipped = next.skippedAppVersion
      if (skipped == null) {
        p.remove(stringPreferencesKey("skipped_app_version"))
      } else {
        p[stringPreferencesKey("skipped_app_version")] = skipped
      }
      // Retired strip-visibility keys: drop them on the next write.
      p.remove(booleanPreferencesKey("kanji_strip_similar"))
      p.remove(booleanPreferencesKey("kanji_strip_parts"))
      p.remove(booleanPreferencesKey("kanji_strip_partof"))
    }
  }
}

/** In-memory repo for JVM unit tests, headless e2e, and previews. */
class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
  private val state = MutableStateFlow(initial)
  override val settings: Flow<AppSettings> = state.asStateFlow()
  override suspend fun update(transform: (AppSettings) -> AppSettings) {
    state.update(transform)
  }
}
