package com.wanbo.launcher

import com.wanbo.launcher.data.AppsRepository
import com.wanbo.launcher.data.FavoriteAppsManager
import com.wanbo.launcher.model.AppItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating favorite application persistence, default pinning behavior,
 * duplicate handling, input sanitization, LiveTV filtering, and Zero Emojis compliance.
 */
class FavoriteAppsManagerTest {

    /**
     * Resolves a file path across different working directories
     * (repo root, launcher/ folder, or launcher/app/ folder).
     *
     * @param relativePath Relative path from the launcher app module.
     * @return Resolved File instance.
     */
    private fun resolveFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("launcher/app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    /**
     * Checks if a given Unicode code point corresponds to an emoji or pictorial symbol.
     *
     * @param codePoint The Unicode code point to inspect.
     * @return True if the code point is within emoji/pictorial ranges; false otherwise.
     */
    private fun isEmoji(codePoint: Int): Boolean {
        return when (codePoint) {
            in 0x1F600..0x1F64F, // Emoticons
            in 0x1F300..0x1F5FF, // Misc Symbols and Pictographs
            in 0x1F680..0x1F6FF, // Transport and Map
            in 0x1F700..0x1F77F, // Alchemical Symbols
            in 0x1F780..0x1F7FF, // Geometric Shapes Extended
            in 0x1F800..0x1F8FF, // Supplemental Arrows-C
            in 0x1F900..0x1F9FF, // Supplemental Symbols and Pictographs
            in 0x1FA00..0x1FA6F, // Chess Symbols
            in 0x1FA70..0x1FAFF, // Symbols and Pictographs Extended-A
            in 0x2600..0x26FF,   // Misc symbols
            in 0x2700..0x27BF,   // Dingbats
            in 0xFE00..0xFE0F,   // Variation Selectors
            in 0x1F1E6..0x1F1FF  // Regional Indicator Symbols
            -> true
            else -> Character.getType(codePoint) == Character.SURROGATE.toInt()
        }
    }

    /**
     * Checks whether a text string contains any emoji character code points.
     *
     * @param text The text string to test.
     * @return True if any emoji code point is detected, false otherwise.
     */
    private fun containsEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (isEmoji(codePoint)) {
                return true
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    /**
     * Verifies that when persistent storage is empty (returns null),
     * FavoriteAppsManager defaults to the required 5 applications in order.
     */
    @Test
    fun testDefaultFavoritesWhenStorageIsEmpty() {
        val manager = FavoriteAppsManager(
            loadRaw = { null },
            saveRaw = {}
        )

        val favorites = manager.getFavoritePackageNames()
        val expected = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "cm.aptoidetv.pt"
        )

        assertEquals(5, favorites.size)
        assertEquals(expected, favorites)

        for (pkg in expected) {
            assertTrue("Expected $pkg to be marked favorite by default", manager.isFavorite(pkg))
        }

        assertFalse("Unrelated app should not be marked as favorite", manager.isFavorite("org.videolan.vlc"))
    }

    /**
     * Verifies adding a new package to favorites updates the stored set
     * and reports true on success.
     */
    @Test
    fun testAddFavoriteUpdatesStorage() {
        var stored: Set<String>? = null
        val manager = FavoriteAppsManager(
            loadRaw = { stored },
            saveRaw = { stored = it }
        )

        val added = manager.addFavorite("org.videolan.vlc")
        assertTrue("addFavorite should return true for new package", added)

        val favorites = manager.getFavoritePackageNames()
        assertEquals(6, favorites.size)
        assertTrue(favorites.contains("org.videolan.vlc"))
        assertTrue(manager.isFavorite("org.videolan.vlc"))
        assertEquals(6, stored?.size)
    }

    /**
     * Verifies removing an existing package from favorites updates the stored set
     * and reports true on success.
     */
    @Test
    fun testRemoveFavoriteUpdatesStorage() {
        var stored: Set<String>? = null
        val manager = FavoriteAppsManager(
            loadRaw = { stored },
            saveRaw = { stored = it }
        )

        val removed = manager.removeFavorite("com.netflix.ninja")
        assertTrue("removeFavorite should return true when package exists", removed)

        val favorites = manager.getFavoritePackageNames()
        assertEquals(4, favorites.size)
        assertFalse(favorites.contains("com.netflix.ninja"))
        assertFalse(manager.isFavorite("com.netflix.ninja"))

        val removedNonExistent = manager.removeFavorite("org.videolan.vlc")
        assertFalse("removeFavorite should return false for non-existent package", removedNonExistent)
    }

    /**
     * Verifies that adding an already pinned application is rejected and returns false.
     */
    @Test
    fun testPreventDuplicateAdditions() {
        var stored: Set<String>? = null
        val manager = FavoriteAppsManager(
            loadRaw = { stored },
            saveRaw = { stored = it }
        )

        val duplicateDefault = manager.addFavorite("com.google.android.youtube.tv")
        assertFalse("Adding an existing default favorite must return false", duplicateDefault)

        val addedFirst = manager.addFavorite("org.jellyfin.androidtv")
        assertTrue("First addition must return true", addedFirst)

        val duplicateNew = manager.addFavorite("org.jellyfin.androidtv")
        assertFalse("Second addition of the same package must return false", duplicateNew)

        assertEquals(6, manager.getFavoritePackageNames().size)
    }

    /**
     * Verifies that blank, whitespace-only, or invalid package names are rejected
     * by addFavorite, removeFavorite, and isFavorite without throwing exceptions.
     */
    @Test
    fun testEmptyOrWhitespacePackageHandling() {
        val manager = FavoriteAppsManager(
            loadRaw = { null },
            saveRaw = {}
        )

        assertFalse("addFavorite with empty string must return false", manager.addFavorite(""))
        assertFalse("addFavorite with whitespace string must return false", manager.addFavorite("   "))

        assertFalse("removeFavorite with empty string must return false", manager.removeFavorite(""))
        assertFalse("removeFavorite with whitespace string must return false", manager.removeFavorite("   "))

        assertFalse("isFavorite with empty string must return false", manager.isFavorite(""))
        assertFalse("isFavorite with whitespace string must return false", manager.isFavorite("   "))
    }

    /**
     * Verifies that package names with leading or trailing whitespace are trimmed correctly.
     */
    @Test
    fun testWhitespaceTrimming() {
        var stored: Set<String>? = null
        val manager = FavoriteAppsManager(
            loadRaw = { stored },
            saveRaw = { stored = it }
        )

        val added = manager.addFavorite("  org.videolan.vlc  ")
        assertTrue("addFavorite with padded whitespace must succeed", added)
        assertTrue("isFavorite without whitespace must match", manager.isFavorite("org.videolan.vlc"))
        assertTrue("isFavorite with whitespace must match", manager.isFavorite("  org.videolan.vlc  "))

        val removed = manager.removeFavorite("  org.videolan.vlc  ")
        assertTrue("removeFavorite with padded whitespace must succeed", removed)
        assertFalse("Package should no longer be favorite", manager.isFavorite("org.videolan.vlc"))
    }

    /**
     * Verifies that FavoriteAppsManager default constructor operates with in-memory storage fallback.
     */
    @Test
    fun testInMemoryFallbackWithoutInjectedLambdas() {
        val manager = FavoriteAppsManager()

        assertEquals(5, manager.getFavoritePackageNames().size)
        assertTrue(manager.addFavorite("org.videolan.vlc"))
        assertEquals(6, manager.getFavoritePackageNames().size)
        assertTrue(manager.isFavorite("org.videolan.vlc"))
        assertTrue(manager.removeFavorite("org.videolan.vlc"))
        assertEquals(5, manager.getFavoritePackageNames().size)
    }

    /**
     * Verifies that LiveTV packages (com.mediatek.tvinput and com.android.tv) are identified
     * by AppsRepository.isExcluded and stripped by AppsRepository.filterExcluded.
     */
    @Test
    fun testLiveTvFilteringInAppsRepository() {
        val repo = AppsRepository()

        assertTrue("com.mediatek.tvinput must be excluded", repo.isExcluded("com.mediatek.tvinput"))
        assertTrue("com.android.tv must be excluded", repo.isExcluded("com.android.tv"))
        assertFalse("YouTube must not be excluded", repo.isExcluded("com.google.android.youtube.tv"))

        val appList = listOf(
            AppItem(packageName = "com.mediatek.tvinput", label = "LiveTV MTK"),
            AppItem(packageName = "com.android.tv", label = "LiveTV Android"),
            AppItem(packageName = "org.jellyfin.androidtv", label = "Jellyfin"),
            AppItem(packageName = "com.google.android.youtube.tv", label = "YouTube")
        )

        val filtered = repo.filterExcluded(appList)
        assertEquals(2, filtered.size)
        assertFalse(filtered.any { it.packageName == "com.mediatek.tvinput" })
        assertFalse(filtered.any { it.packageName == "com.android.tv" })
        assertTrue(filtered.any { it.packageName == "org.jellyfin.androidtv" })
        assertTrue(filtered.any { it.packageName == "com.google.android.youtube.tv" })
    }

    /**
     * Enforces the Zero Emojis policy on FavoriteAppsManager and its test suite.
     */
    @Test
    fun testZeroEmojisInFavoriteAppsSourceAndTest() {
        val srcFile = resolveFile("src/main/java/com/wanbo/launcher/data/FavoriteAppsManager.kt")
        val testFile = resolveFile("src/test/java/com/wanbo/launcher/FavoriteAppsManagerTest.kt")

        assertTrue("FavoriteAppsManager.kt must exist", srcFile.exists())
        assertTrue("FavoriteAppsManagerTest.kt must exist", testFile.exists())

        assertFalse("FavoriteAppsManager.kt must NOT contain emojis", containsEmoji(srcFile.readText()))
        assertFalse("FavoriteAppsManagerTest.kt must NOT contain emojis", containsEmoji(testFile.readText()))
    }
}
