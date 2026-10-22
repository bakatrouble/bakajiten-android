package com.bakajiten.app.data

// Romaji → hiragana query edge, direct UniFFI free functions (total
// and infallible on both sides). No artifacts needed; the native
// library must load — unit tests point the JVM at the host cdylib
// (see app/build.gradle.kts), previews pass canned strings instead.
object RustKana {
  fun convert(text: String): String = uniffi.dict.romajiToKana(text)

  fun display(raw: String): String = uniffi.dict.romajiDisplay(raw)

  fun hasPending(raw: String): Boolean = uniffi.dict.romajiHasPending(raw)
}
