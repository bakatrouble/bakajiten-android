package com.bakajiten.app.di

import com.bakajiten.app.data.DataManager
import com.bakajiten.app.data.DataStoreSettingsRepository
import com.bakajiten.app.data.FakeDictionaryBackend
import com.bakajiten.app.data.FakeFuriganaBackend
import com.bakajiten.app.data.FakeKanjiBackend
import com.bakajiten.app.data.FakeRecognizerBackend
import com.bakajiten.app.data.REQUIRED_FILES
import com.bakajiten.app.data.RustDictionaryBackend
import com.bakajiten.app.data.RustKanjiBackend
import com.bakajiten.app.data.RustLabels
import com.bakajiten.app.data.RustRecognizerBackend
import com.bakajiten.app.data.SettingsRepository
import com.bakajiten.app.state.AppViewModel
import com.bakajiten.app.state.Backends
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Global object graph: the data dir, the DataStore settings, the
// backend bundle (Rust when packed artifacts are present, fake
// otherwise), and the single app view-model. Unit tests keep
// constructing fakes directly and never touch this graph.

private fun createBackends(manager: DataManager): Backends {
  val dictPath = REQUIRED_FILES.firstOrNull()?.let { manager.path(it) }
  return try {
    if (dictPath != null) {
      Backends(
        dict = RustDictionaryBackend.fromPath(dictPath),
        kanji = manager.path(REQUIRED_FILES[1])?.let { RustKanjiBackend.fromPath(it) }
          ?: FakeKanjiBackend(),
        recognizer = manager.bytes(REQUIRED_FILES[2])?.let { RustRecognizerBackend.fromBytes(it) }
          ?: FakeRecognizerBackend(),
        furigana = object : com.bakajiten.app.data.FuriganaBackend {
          override fun segment(head: String, reading: String) = RustLabels.furigana(head, reading)
        },
        posLabel = RustLabels::posLabel,
        posTip = RustLabels::posTip,
        fieldLabel = RustLabels::fieldLabel,
        fieldTip = RustLabels::fieldTip,
        miscLabel = RustLabels::miscLabel,
        miscTip = RustLabels::miscTip,
      )
    } else {
      fakeBackends()
    }
  } catch (t: Throwable) {
    // Native load failures are Errors (missing JNA/ABIs), never
    // Exceptions — catch everything so search still starts.
    fakeBackends()
  }
}

private fun fakeBackends(): Backends {
  val fake = FakeDictionaryBackend()
  return Backends(
    fake,
    FakeKanjiBackend(),
    FakeRecognizerBackend(),
    FakeFuriganaBackend(),
    fake::posLabel,
    fake::posTip,
    { "" },
    { "" },
    fake::miscLabel,
    fake::miscTip,
  )
}

val appModule = module {
  single { DataManager(androidContext().filesDir) }
  single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
  single { createBackends(get()) }
  viewModel { AppViewModel(get(), get()) }
}
