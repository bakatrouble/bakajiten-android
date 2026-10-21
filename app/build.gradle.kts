repositories {
  google()
  mavenCentral()
}

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

// UniFFI Kotlin bindings generate at build time from the just-built
// host cdylibs (same never-drift rule as the Qt C++ bindings), land
// in build/generated, and compile as an extra source dir — nothing
// generated is checked in. Needs `cargo` and `uniffi-bindgen` 0.31
// (`cargo install uniffi --version 0.31.2 --locked --features cli`)
// on PATH. Device .so files build via `cargo-ndk` (plus the Android
// NDK) into build/rust-jni and package per ABI.
val repoRoot = rootDir.resolve("../..").normalize()
val generatedUniffi = layout.buildDirectory.dir("generated/uniffi")
val patchedUniffiDir = layout.buildDirectory.dir("generated/uniffi-patched")
val deviceJniLibs = layout.buildDirectory.dir("rust-jni")

android {
  namespace = "com.bakajiten.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.bakajiten.app"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

androidComponents {
    // Generated UniFFI sources, registered per variant (AGP 9 no
    // longer accepts providers in sourceSets). Ordering comes from
    // the preBuild task chain; this only declares the path.
    onVariants(selector().all()) { variant ->
        @Suppress("UnstableApiUsage")
        variant.sources.kotlin?.addStaticSourceDirectory(
            patchedUniffiDir.get().asFile.absolutePath,
        )
    }
}

fun requireTool(name: String, hint: String) {
  val found = try {
    Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v $name")).waitFor() == 0
  } catch (e: Exception) {
    false
  }
  if (!found) throw GradleException("$name not found on PATH. $hint")
}

val buildHostCdylibs = tasks.register<Exec>("buildHostCdylibs") {
  workingDir(repoRoot)
  commandLine("cargo", "build", "-p", "libdict", "-p", "librecognizer")
  inputs.dir(repoRoot.resolve("lib/libdict/src"))
  inputs.dir(repoRoot.resolve("lib/librecognizer/src"))
  inputs.file(repoRoot.resolve("lib/libdict/Cargo.toml"))
  inputs.file(repoRoot.resolve("lib/librecognizer/Cargo.toml"))
  inputs.file(repoRoot.resolve("Cargo.toml"))
  inputs.file(repoRoot.resolve("Cargo.lock"))
  outputs.file(repoRoot.resolve("target/debug/libdict.so"))
  outputs.file(repoRoot.resolve("target/debug/librecognizer.so"))
  doFirst { requireTool("cargo", "Install the Rust toolchain (rustup).") }
}

// Device cdylibs for the APK (arm64 phones + x86_64 emulator),
// built with cargo-ndk into build/rust-jni/<abi>. Needs the Android
// NDK (`cargo install cargo-ndk`). Unit tests never touch this path
// (JVM runs the fake backend), so a missing NDK must not break them:
// the task only runs on the assemble chain via preBuild.
val buildDeviceCdylibs = tasks.register<Exec>("buildDeviceCdylibs") {
  workingDir(repoRoot)
  commandLine(
    "cargo", "ndk",
    "-t", "arm64-v8a",
    "-t", "x86_64",
    "-o", deviceJniLibs.get().asFile.absolutePath,
    "build", "--release",
    "-p", "libdict",
    "-p", "librecognizer",
  )
  inputs.dir(repoRoot.resolve("lib/libdict/src"))
  inputs.dir(repoRoot.resolve("lib/librecognizer/src"))
  inputs.file(repoRoot.resolve("lib/libdict/Cargo.toml"))
  inputs.file(repoRoot.resolve("lib/librecognizer/Cargo.toml"))
  inputs.file(repoRoot.resolve("Cargo.toml"))
  inputs.file(repoRoot.resolve("Cargo.lock"))
  outputs.dir(deviceJniLibs)
  // cargo-ndk wants ANDROID_NDK_HOME; fall back to the newest NDK
  // under ANDROID_HOME so plain SDK installs work untouched.
  System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_HOME")?.let { home ->
    project.file("$home/ndk").listFiles()?.maxOfOrNull { it.name }?.let { "$home/ndk/$it" }
  }?.let { environment("ANDROID_NDK_HOME", it) }
  doFirst {
    requireTool("cargo", "Install the Rust toolchain (rustup).")
    requireTool("cargo-ndk", "Install it with: cargo install cargo-ndk.")
    requireTool("rustc", "Install the Rust toolchain (rustup).")
  }
}

fun uniffiTask(taskName: String, libFile: String, subDir: String) =
  tasks.register<Exec>(taskName) {
    dependsOn(buildHostCdylibs)
    val outDir = generatedUniffi.map { it.dir(subDir) }
    inputs.file(repoRoot.resolve("target/debug/$libFile"))
    outputs.dir(outDir)
    doFirst {
      requireTool(
        "uniffi-bindgen",
        "Install it with: cargo install uniffi --version 0.31.2 --locked --features cli",
      )
      delete(outDir.get().asFile)
    }
    commandLine(
      "uniffi-bindgen", "generate",
      "--library", repoRoot.resolve("target/debug/$libFile").absolutePath,
      "--language", "kotlin",
      "--no-format",
      "--out-dir", outDir.get().asFile.absolutePath,
    )
  }

val uniffiDict = uniffiTask("uniffiDict", "libdict.so", "dict")
val uniffiRecognizer = uniffiTask("uniffiRecognizer", "librecognizer.so", "recognizer")

val patchUniffiBindings = tasks.register("patchUniffiBindings") {
  dependsOn(uniffiDict, uniffiRecognizer)
  inputs.dir(generatedUniffi)
  outputs.dir(patchedUniffiDir)
  doLast {
    // Kotlin 1.9 rejects the generated `val message` + computed
    // `override val message` pair (hides supertype member): fold
    // the constructor property into the override.
    val pattern = Regex(
      """class (\w+)\(\s*\n\s*val `message`: kotlin\.String\s*\n\s*\) : (\w+)\(\) \{\s*\n\s*override val message\s*\n\s*get\(\) = "[^"]*"\s*\n""",
    )
    val dest = patchedUniffiDir.get().asFile
    delete(dest)
    dest.mkdirs()
    // Each crate generates its own `uniffi/<crate>/…` tree; merge
    // them so the source root holds `uniffi/…` directly.
    generatedUniffi.get().asFile.walkTopDown().maxDepth(1)
      .filter { it.isDirectory && it.resolve("uniffi").isDirectory }
      .forEach { it.resolve("uniffi").copyRecursively(dest.resolve("uniffi")) }
    dest.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
      val original = file.readText()
      val updated = pattern.replace(original) { match ->
        "class ${match.groupValues[1]}(\n        \n        override val `message`: kotlin.String\n        ) : ${match.groupValues[2]}() {\n"
      }
      // Newer kotlinc flags the intentional bare `UniffiLib`
      // references as unused expressions; they only force
      // initialization order, so extend the generated file-level
      // suppression (present in every emitted file).
      val silenced = if ("UNUSED_EXPRESSION" in updated) {
        updated
      } else if ("@file:Suppress(" in updated) {
        updated.replace("@file:Suppress(", "@file:Suppress(\"UNUSED_EXPRESSION\", ")
      } else {
        "@file:Suppress(\"UNUSED_EXPRESSION\")\n$updated"
      }
      if (silenced != original) file.writeText(silenced)
    }
  }
}

tasks.named("preBuild") { dependsOn(patchUniffiBindings) }

// Device .so files land in build/rust-jni/<abi>; unit-test builds
// skip the NDK build (missing NDK must not break `./gradlew test`),
// so this hooks the merge task rather than preBuild.
tasks.matching { it.name.startsWith("merge") && it.name.contains("JniLibFolders") }.configureEach {
  dependsOn(buildDeviceCdylibs)
}

android {
  sourceSets {
    named("main") {
      // Eager path: providers are rejected here on AGP 9, and
      // build/ always resolves at configuration time.
      @Suppress("DEPRECATION")
      jniLibs.srcDir(deviceJniLibs.get().asFile)
    }
  }
}

dependencies {
  implementation("androidx.compose.material3:material3:1.4.0")
  val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.core:core-ktx:1.19.1")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
  implementation("androidx.datastore:datastore-preferences:1.2.1")
  // Settings screen tiles (alorma/Compose-Settings, expressive).
  implementation("com.github.alorma.compose-settings:ui-tiles-expressive:3.3.0")
  // UniFFI Kotlin bindings call into the Rust cdylib through JNA;
  // the AAR (not the plain jar) carries JNA's own dispatch .so per
  // Android ABI — without it every device boot crashes in loadBackends.
  implementation("net.java.dev.jna:jna:5.19.1@aar")
  // Global singletons (settings, data dir, backends, view-model).
  implementation(platform("io.insert-koin:koin-bom:4.1.1"))
  implementation("io.insert-koin:koin-android")
  implementation("io.insert-koin:koin-androidx-compose")

  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.17")
  testImplementation("androidx.test:core:1.7.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
