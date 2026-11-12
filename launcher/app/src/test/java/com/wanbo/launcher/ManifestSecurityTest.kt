package com.wanbo.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test validating AndroidManifest security constraints and launcher intent categories.
 */
class ManifestSecurityTest {

    /**
     * Verifies that AndroidManifest.xml does not request INTERNET permission
     * and declares HOME and LEANBACK_LAUNCHER intent categories.
     */
    @Test
    fun testManifestHasNoInternetPermission() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()
        assertFalse("Manifest must NOT contain INTERNET permission!", content.contains("android.permission.INTERNET"))
        assertTrue("Manifest must declare HOME category", content.contains("android.intent.category.HOME"))
        assertTrue("Manifest must declare LEANBACK_LAUNCHER category", content.contains("android.intent.category.LEANBACK_LAUNCHER"))
    }
}
