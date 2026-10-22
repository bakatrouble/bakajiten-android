package com.bakajiten.app

import com.bakajiten.app.data.UpdateCheck
import com.bakajiten.app.data.checkAppUpdate
import com.bakajiten.app.data.isAppNewer
import com.bakajiten.app.data.matchedAppAsset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Release-check logic: version comparison (Qt QVersionNumber
// semantics), asset matching, and check routing over a fake
// fetcher — no network. Robolectric (not plain JVM): the release
// parse uses org.json, an Android-framework class.

private const val RELEASE_JSON = """
{"tag_name":"v0.2.0","body":"Faster startup.","assets":[
{"name":"bakajiten-android-v0.2.0.apk","browser_download_url":"https://example.invalid/b.apk","size":12345},
{"name":"bakajiten-android-v0.2.0-debug.apk","browser_download_url":"https://example.invalid/d.apk","size":12347}]}
"""

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateTest {
  @Test fun equalIsNotNewer() {
    assertEquals(false, isAppNewer("0.1.0", "0.1.0"))
    assertEquals(false, isAppNewer("0.1.0", "v0.1.0"))
  }

  @Test fun newerTagIsNewer() {
    assertTrue(isAppNewer("0.1.0", "v0.2.0"))
    assertTrue(isAppNewer("0.1.0", "0.1.10"))
    assertTrue(isAppNewer("0.2", "0.10"))
    assertTrue(isAppNewer("1.0", "1.0.1"))
  }

  @Test fun olderTagIsNotNewer() {
    assertEquals(false, isAppNewer("0.2.0", "v0.1.0"))
    assertEquals(false, isAppNewer("1.0.1", "1.0"))
  }

  @Test fun garbageReadsAsNotNewer() {
    assertEquals(false, isAppNewer("", "v0.2.0"))
    assertEquals(false, isAppNewer("0.1.0", ""))
    assertEquals(false, isAppNewer("0.1.0", "soon"))
    assertEquals(false, isAppNewer("soon", "v0.2.0"))
    assertEquals(false, isAppNewer("0.1.0", "v0.1.x"))
  }

  @Test fun signedAssetPreferredOverDebug() {
    val names = listOf("bakajiten-android-v0.2.0-debug.apk", "bakajiten-android-v0.2.0.apk")
    assertEquals("bakajiten-android-v0.2.0.apk", matchedAppAsset(names, "v0.2.0"))
  }

  @Test fun debugIsFallback() {
    assertEquals(
      "bakajiten-android-v0.2.0-debug.apk",
      matchedAppAsset(listOf("bakajiten-android-v0.2.0-debug.apk"), "v0.2.0"),
    )
  }

  @Test fun noApkMatchesNothing() {
    assertNull(matchedAppAsset(listOf("notes.txt"), "v0.2.0"))
    assertNull(matchedAppAsset(emptyList(), "v0.2.0"))
  }

  @Test fun checkFindsSignedRelease() = runTest {
    val result = checkAppUpdate({ RELEASE_JSON }, "0.1.0", null)
    val available = result as UpdateCheck.Available
    assertEquals("0.2.0", available.release.version)
    assertEquals("Faster startup.", available.release.notes)
    assertEquals("bakajiten-android-v0.2.0.apk", available.release.assetName)
    assertEquals("https://example.invalid/b.apk", available.release.downloadUrl)
    assertEquals(12345, available.release.sizeBytes)
  }

  @Test fun checkWithoutApkIsNoArtifact() = runTest {
    val json = """{"tag_name":"v0.2.0","body":"notes","assets":[{"name":"notes.txt","browser_download_url":"","size":0}]}"""
    val result = checkAppUpdate({ json }, "0.1.0", null)
    val noArtifact = result as UpdateCheck.NoArtifact
    assertEquals("0.2.0", noArtifact.version)
    assertEquals("notes", noArtifact.notes)
  }

  @Test fun checkCurrentIsUpToDate() = runTest {
    assertTrue(checkAppUpdate({ RELEASE_JSON }, "0.2.0", null) is UpdateCheck.UpToDate)
    assertTrue(checkAppUpdate({ RELEASE_JSON }, "0.3.0", null) is UpdateCheck.UpToDate)
  }

  @Test fun skippedSuppressesAvailable() = runTest {
    assertTrue(checkAppUpdate({ RELEASE_JSON }, "0.1.0", "0.2.0") is UpdateCheck.Skipped)
  }

  @Test fun fetcherFailureReportsFailed() = runTest {
    val result = checkAppUpdate({ throw IllegalStateException("offline") }, "0.1.0", null)
    val failed = result as UpdateCheck.Failed
    assertEquals("offline", failed.reason)
  }

  @Test fun garbageJsonReportsFailed() = runTest {
    assertTrue(checkAppUpdate({ "not json" }, "0.1.0", null) is UpdateCheck.Failed)
    assertTrue(checkAppUpdate({ "{}" }, "0.1.0", null) is UpdateCheck.Failed)
  }
}
