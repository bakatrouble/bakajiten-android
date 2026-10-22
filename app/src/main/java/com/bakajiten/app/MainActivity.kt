package com.bakajiten.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.bakajiten.app.data.AppSettings
import com.bakajiten.app.data.AppUpdater
import com.bakajiten.app.data.DataManager
import com.bakajiten.app.data.REQUIRED_FILES
import com.bakajiten.app.data.SettingsRepository
import com.bakajiten.app.state.AppViewModel
import com.bakajiten.app.state.Backends
import com.bakajiten.app.theme.BakajitenTheme
import com.bakajiten.app.ui.AppUpdateDialog
import com.bakajiten.app.ui.BakajitenApp
import com.bakajiten.app.ui.DictionaryManagementScreen
import com.bakajiten.app.ui.rememberAdaptiveWidth
import com.bakajiten.app.ui.updateDialogVisible
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// Entry point: DataStore settings + the Rust backend when the
// packed artifacts are present (downloaded through the data
// screen), the fake backend otherwise — the app always starts.

class MainActivity : ComponentActivity() {
  // Field initializers run before attach (no Context yet): the
  // manager comes from Koin, and the filesDir check waits for onCreate.
  private val manager: DataManager by inject()
  private val backends: Backends by inject()
  private val settingsRepo: SettingsRepository by inject()
  private val updater: AppUpdater by inject()
  private var dataReady by mutableStateOf(false)

  private val vm: AppViewModel by viewModel()


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    dataReady = REQUIRED_FILES.all { manager.path(it) != null }
    // Self-update: automatic check shortly after startup, then
    // hourly while the app lives (mirrors the Qt cadence); silent
    // unless an installable update is actionable.
    lifecycleScope.launch {
      delay(30_000)
      while (true) {
        if (settingsRepo.settings.first().checkAppUpdates) updater.check(manual = false)
        delay(3_600_000)
      }
    }
    enableEdgeToEdge()
    setContent {
      // Missing dictionaries redirect to data management instead
      // of running on the fake backend; Continue rechecks and
      // enters the app once the required files are present. The
      // gate gets the real theme (not the default) from DataStore.
      val settings by settingsRepo.settings.collectAsState(AppSettings())
      val update by updater.state.collectAsState()
      BakajitenTheme(theme = settings.theme, densityDpi = settings.densityDpi) {
        if (dataReady) {
          BakajitenApp(
            vm = vm,
            backends = backends,
            widthClass = rememberAdaptiveWidth(),
            manager = manager,
            update = update,
            onCheckUpdates = { updater.check(manual = true) },
          )
        } else {
          DictionaryManagementScreen(
            manager = manager,
            onDone = {
              if (REQUIRED_FILES.all { manager.path(it) != null }) {
                dataReady = true
              }
            },
          )
        }
        if (updateDialogVisible(update)) {
          AppUpdateDialog(
            state = update,
            onDownload = { skip -> updater.download(skip) },
            onLater = { skip -> if (skip) updater.skipCurrent() else updater.later() },
            onCloseNotice = { updater.dismissNotice() },
          )
        }
      }
    }
  }
}
