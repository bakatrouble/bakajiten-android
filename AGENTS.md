# AGENTS.md — frontends/android notes (untracked, do not publish)

# bakajiten-android — Android frontend (Kotlin + Jetpack Compose)

Dictionary + handwriting search over the same Rust backend as
everything else, through UniFFI Kotlin bindings (JNA). Adaptive
layout: phones show the article as a new screen (list → article
navigation with system-back support); wide screens (tablet /
foldable, `AdaptiveWidth.EXPANDED` at ≥ 840dp) keep the desktop
two-pane with the article beside the results.

- `app/src/main/java/com/bakajiten/app/`
  - `MainActivity.kt` — wires DataStore settings + the Rust
    backend when packed artifacts are present (downloaded through
    the dictionary screen), the fake backend otherwise. Native load
    failures are `Error`s, so the fallback catches `Throwable` —
    missing JNA/Rust ABIs never crash, search just runs fake.
  - `state/AppViewModel.kt` — search state (200 ms debounce,
    affix-toggles → SearchMode, meaning mode), screen nav.
  - `data/Backend.kt` — backend contract (records mirror the
    UniFFI records 1:1).  - `data/RustBackend.kt` — UniFFI/JNA implementation + label
    tables; `data/FakeBackend.kt` — toy data for JVM tests,
    headless e2e, previews, and no-data dev.
  - `data/DataManager.kt` — consent-gated `.bin.gz` downloads
    (streamed to disk, size + sha256 verify, atomic install),
    mirroring web `Backend.ts`; saved versions read from the
    artifacts themselves via UniFFI (`gloss_version`,
    `kanji_versions`, `templates_packed_at`). Nothing downloads
    without a tap.
  - `data/SettingsRepository.kt` — DataStore prefs + in-memory
    fake. `searchBarPosition` (TOP/BOTTOM) is the Android-only
    addition: the search bar docks top or bottom per settings.
  - `domain/Kana.kt` — pure-Kotlin port of the romaji→kana edge
    (row-for-row with `lib/libdict/src/kana/`).
  - `domain/Romaji.kt` — pure-Kotlin port of kana→romaji display
    (`lib/libdict/src/romaji.rs` traces); drives the reading
    display setting with no JNA in the UI.
  - `domain/Labels.kt` — sense display shaping (badges, tips,
    numbering, heading preference), mirroring web `labels.ts`.
  - `ui/` — `SearchScreen` (search bar top/bottom per settings;
    the opaque recognizer panel overlays the list and the matches
    count, never squeezes them; while it is open the field keeps
    `showKeyboardOnFocus = false` so no virtual keyboard opens —
    the field itself never changes, so its autofill node stays put
    (toggling `readOnly` instead crashed with "LayoutNode not found
    in RectList" when rect callbacks fired mid-churn); opening the
    panel only hides a live keyboard, selection stays intact),
    `ResultsList` (rows are `SegmentedListItem`s with joined
    shapes over `surfaceContainer`, gaps instead of dividers; shapes
    need only the position and `hits.size`, so entries resolve
    lazily per visible row — never map the whole (uncapped) hit
    list up front, each `entryAt` crosses JNA on the main thread;
    row content lives in
    `ui/components/ResultRow.kt`, and tapping a tag in the compact
    senses line reports its tip plus the window tap point through
    `onBadgeTap` — the list shows it in its one shared tooltip,
    positioned at the tap (above, or below without room; clamped
    to the window). No per-row popups or inline content in rows: their
    layout nodes must never feed the rect tracker while the list
    recycles (`IllegalArgumentException: LayoutNode ... not found
    in RectList` on scroll)), `ArticleScreen` / `KanjiScreen`
    info tab (body on a `surfaceContainer` sheet),
    `KanjiScreen` (Info/Words/Composition tabs, `StrokePlayer`, full detail
    table, always-expanded `KanjiStrip` surfaces),
    `RecognizerPanel` (no keyboard reaction by design — the field
    keeps `showKeyboardOnFocus = false` while the panel is open, so
    panel never reads IME insets; `CanvasViewport` fits the square
    grid to the min window edge, centered, with touch mapped to the
    same square fractions — never an overflow inner box, which the
    panel's scrolling column coerces and misplaces on device; the
    persisted height sizes the window only),
    `SettingsScreen`, `DictionaryManagementScreen`,
    `components/` (`SearchBar`, `AffixToggleRow` (Qt/Web icon
    toggles), `SearchOptionsMenu` (Dictionaries section, uncached
    scopes grey out into dictionary management), `TagBadges`,
    `SenseRow`).
  - `di/` — Koin graph (`appModule`: data dir, DataStore
    settings, backend bundle with fake fallback, view-model).
    Unit tests construct fakes directly and never touch it.
    Every screen/component carries an `@Preview`. The whole tree is
    Material 3 Expressive (`MaterialExpressiveTheme` + expressive
    motion in `theme/`; `ToggleButton` toggles, `LoadingIndicator`,
    flexible app bars, segmented lists elsewhere — plain M3
    components inherit expressive shapes/motion from the theme).
- No checked-in `uniffi/` sources: the bindings generate at build
  time from the just-built host cdylibs (`buildHostCdylibs` →
  `uniffiDict`/`uniffiRecognizer` → `patchUniffiBindings`, wired into
  `preBuild`; outputs land in `app/build/generated/` and compile as
  an extra source dir — nothing generated is checked in, same
  never-drift rule as the Qt C++ bindings). Needs `cargo` and
  `uniffi-bindgen` 0.31 on PATH
  (`cargo install uniffi --version 0.31.2 --locked --features cli`);
  missing tools fail the build with the install command.
  `patchUniffiBindings` also folds the generated `val message` into
  an `override val` (Kotlin 1.9 rejects the emitted pair).
  Device `.so` files (`libdict.so`, `librecognizer.so`) build with
  `cargo-ndk` (`buildDeviceCdylibs` → `build/rust-jni/<abi>`,
  arm64-v8a + x86_64, NDK auto-found under `ANDROID_HOME`) and
  package via a `jniLibs` src dir on the merge task only — unit
  tests never need the NDK. JNA must be the `@aar` artifact: the
  plain jar carries no Android dispatch `.so`, so every device boot
  would die in `loadBackends` with `UnsatisfiedLinkError`.

## Build

```sh
cd frontends/android
./gradlew :app:assembleDebug   # needs ANDROID_HOME (or local.properties)
```

CI (`.github/workflows/android.yml`, runs here, not in the root
repo): checks out `bakatrouble/bakajiten` underneath and builds the
nested `ws/frontends/android` layout the Gradle scripts expect;
JDK 17 + SDK platform 37 + NDK r27 + stable Rust with the Android
targets + `uniffi-bindgen` 0.31.2 + `cargo-ndk`, with Gradle / Rust
/ cargo-bin caches. Pushes and PRs run headless unit tests and
upload both APKs; tags `v*` cut here publish a GitHub Release
(debug APK always, signed release APK when the
`ANDROID_KEYSTORE_*` secrets are set, unsigned otherwise).
`gradle.properties` pins `org.gradle.java.home` to a local path —
CI overrides it with `-Dorg.gradle.java.home="$JAVA_HOME"`.

Toolchain: Gradle 9.6.0 (wrapper; runs on modern JVMs, daemon stays
on JDK 17 via `org.gradle.java.home`), AGP 9.4.1 (built-in Kotlin:
no `kotlin.android` plugin), KGP 2.3.21 + Compose Compiler plugin
2.3.21 (`compilerOptions.jvmTarget`, no `kotlinOptions` /
`composeOptions`)
Compose BOM 2026.02.01 (debug `ui-tooling` included: Studio
previews need it), compileSdk/targetSdk 37, minSdk 26, JVM 17
(`org.gradle.java.home` pins JDK 17). Do NOT add a
`dependencyResolutionManagement` block to `settings.gradle.kts`:
its mere presence breaks Kotlin plugin application on this
Gradle/KGP pair (duplicate `kotlin` extension) — module
`repositories {}` in `app/build.gradle.kts` instead. KGP stays on
the 2.3.x line: 2.2.x has no Gradle 9 variant and misbehaves on
Gradle 9.

## Tests (JVM, headless — no emulator)

```sh
./gradlew :app:testDebugUnitTest
```

- `KanaTest`, `LabelsTest`, `FakeBackendTest`, `SettingsTest`
  — pure JVM JUnit4 (kana acceptance traces ported from the
  Rust tests; display shaping over the fake backend).
- `SettingsDataStoreTest`, `AppE2eTest` — Robolectric + Compose
  UI test (`createComposeRule`, `@Config(sdk = [34])`):
  search → results, article-as-new-screen, bottom-dock search
  bar, recognizer toggle, affix chips.
- `RecognizerPanelTest`, `CanvasGripTest` — square-fit behavior
  (`onSquareSidePx` hook reports the square side); centered canvas
  and hint follow the min edge, touch maps to square fractions.
  `boundsInRoot` is clip-aware: assert the window against it, never
  bounds-vs-layout equality.
