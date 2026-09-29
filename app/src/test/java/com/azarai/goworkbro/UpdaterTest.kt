package com.azarai.goworkbro

import com.azarai.goworkbro.core.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the GitHub updater: version compare + JSON parsing. */
class UpdaterTest {

    @Test
    fun `newer versions are detected`() {
        assertTrue(Updater.isNewer("3.1.0", "3.0.0"))
        assertTrue(Updater.isNewer("v3.1.0", "3.0.0")) // tags carry a v prefix
        assertTrue(Updater.isNewer("3.0.1", "3.0.0"))
        assertTrue(Updater.isNewer("4.0.0", "3.9.9"))
        assertTrue(Updater.isNewer("3.10.0", "3.9.0")) // numeric, not string compare
        assertTrue(Updater.isNewer("3.1.0.1", "3.1.0")) // longer tail wins
    }

    @Test
    fun `equal or older versions are not newer`() {
        assertFalse(Updater.isNewer("3.0.0", "3.0.0"))
        assertFalse(Updater.isNewer("v3.0.0", "3.0.0"))
        assertFalse(Updater.isNewer("3.0.0", "3.1.0"))
        assertFalse(Updater.isNewer("3.1", "3.1.0")) // missing segment reads as 0
    }

    @Test
    fun `garbage versions never count as newer`() {
        assertFalse(Updater.isNewer("latest", "3.0.0"))
        assertFalse(Updater.isNewer("3.x.0", "3.0.0"))
        assertFalse(Updater.isNewer("", "3.0.0"))
    }

    @Test
    fun `release json is parsed`() {
        val json = """
            {
              "tag_name": "v3.1.0",
              "assets": [
                {"name": "GoWorkBro-v3.1.0.sha256", "browser_download_url": "https://x/sha"},
                {"name": "GoWorkBro-v3.1.0.apk", "browser_download_url": "https://x/apk"}
              ]
            }
        """.trimIndent()
        val release = Updater.parseRelease(json)!!
        assertEquals("v3.1.0", release.tag)
        assertEquals("3.1.0", release.version)
        assertEquals("https://x/apk", release.apkUrl)
    }

    @Test
    fun `release without apk asset is rejected`() {
        val json = """{"tag_name": "v3.1.0", "assets": [{"name": "notes.txt", "browser_download_url": "https://x"}]}"""
        assertNull(Updater.parseRelease(json))
        assertNull(Updater.parseRelease("not json"))
        assertNull(Updater.parseRelease("""{"assets": []}"""))
    }
}
