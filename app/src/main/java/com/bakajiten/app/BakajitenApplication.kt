package com.bakajiten.app

import android.app.Application
import com.bakajiten.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BakajitenApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    // Robolectric boots a fresh Application per test class in one
    // JVM (and unit tests never touch this graph anyway); only the
    // first start wins.
    if (org.koin.core.context.GlobalContext.getOrNull() == null) {
      startKoin {
        androidContext(this@BakajitenApplication)
        modules(appModule)
      }
    }
  }
}
