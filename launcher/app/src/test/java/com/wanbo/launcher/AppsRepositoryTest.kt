package com.wanbo.launcher

import com.wanbo.launcher.data.AppsRepository
import com.wanbo.launcher.model.AppItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for AppItem domain model, AppsRepository filtering/sorting logic,
 * centralized color palette definitions, and Zero Emojis compliance.
 */
class AppsRepositoryTest {

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
     * Verifies that AppItem default properties and initial state are correctly instantiated.
     */
    @Test
    fun testAppItemPropertiesAndDefaults() {
        val app = AppItem(
            packageName = "org.jellyfin.androidtv",
            label = "Jellyfin"
        )
        assertEquals("org.jellyfin.androidtv", app.packageName)
        assertEquals("Jellyfin", app.label)
        assertNull(app.icon)
        assertNull(app.banner)
        assertFalse("AppItem isFavorite should default to false", app.isFavorite)
    }

    /**
     * Verifies the mutability of isFavorite and immutability of core fields in AppItem.
     */
    @Test
    fun testAppItemMutabilityAndCopy() {
        val app = AppItem(
            packageName = "com.netflix.ninja",
            label = "Netflix",
            isFavorite = false
        )
        assertFalse(app.isFavorite)

        // Mutability of isFavorite
        app.isFavorite = true
        assertTrue("isFavorite should be mutable", app.isFavorite)

        // Copy functionality preserves immutability of core val properties
        val copied = app.copy(label = "Netflix TV")
        assertEquals("Netflix TV", copied.label)
        assertEquals("com.netflix.ninja", copied.packageName)
        assertTrue(copied.isFavorite)
        assertEquals("Netflix", app.label) // Original remains unchanged
    }

    /**
     * Verifies that filtering logic removes the launcher's own package name.
     */
    @Test
    fun testFilterSelfRemovesOwnPackage() {
        val repo = AppsRepository()
        val ownPackage = "com.wanbo.launcher"

        val apps = listOf(
            AppItem(packageName = "com.wanbo.launcher", label = "WanboOS"),
            AppItem(packageName = "org.jellyfin.androidtv", label = "Jellyfin"),
            AppItem(packageName = "com.google.android.youtube.tv", label = "YouTube")
        )

        val filtered = repo.filterSelf(apps, ownPackage)

        assertEquals(2, filtered.size)
        assertFalse(filtered.any { it.packageName == ownPackage })
        assertTrue(filtered.any { it.packageName == "org.jellyfin.androidtv" })
        assertTrue(filtered.any { it.packageName == "com.google.android.youtube.tv" })
    }

    /**
     * Verifies that blank and whitespace-only package names are filtered out.
     */
    @Test
    fun testFilterSelfRemovesBlankPackages() {
        val repo = AppsRepository()
        val ownPackage = "com.wanbo.launcher"

        val apps = listOf(
            AppItem(packageName = "", label = "Empty"),
            AppItem(packageName = "   ", label = "Whitespace"),
            AppItem(packageName = "org.videolan.vlc", label = "VLC")
        )

        val filtered = repo.filterSelf(apps, ownPackage)

        assertEquals(1, filtered.size)
        assertEquals("org.videolan.vlc", filtered.first().packageName)
    }

    /**
     * Verifies that deduplicate preserves the first occurrence of an application package.
     */
    @Test
    fun testDeduplicatePreservesFirstOccurrence() {
        val repo = AppsRepository()

        val apps = listOf(
            AppItem(packageName = "com.amazon.amazonvideo.livingroom", label = "Prime Video Leanback"),
            AppItem(packageName = "com.spotify.tv.android", label = "Spotify"),
            AppItem(packageName = "com.amazon.amazonvideo.livingroom", label = "Prime Video Sideloaded")
        )

        val deduplicated = repo.deduplicate(apps)

        assertEquals(2, deduplicated.size)
        assertEquals("Prime Video Leanback", deduplicated[0].label)
        assertEquals("Spotify", deduplicated[1].label)
    }

    /**
     * Verifies that sorting orders favorite apps first, then alphabetical by label.
     */
    @Test
    fun testSortAppsFavoritesFirstAndAlphabetical() {
        val repo = AppsRepository()

        val apps = listOf(
            AppItem(packageName = "com.google.android.youtube.tv", label = "YouTube", isFavorite = false),
            AppItem(packageName = "com.netflix.ninja", label = "Netflix", isFavorite = true),
            AppItem(packageName = "com.amazon.amazonvideo.livingroom", label = "Amazon Prime", isFavorite = true),
            AppItem(packageName = "com.disney.disneyplus", label = "Disney+", isFavorite = false)
        )

        val sortedFavoritesFirst = repo.sortApps(apps, favoritesFirst = true)
        val expectedLabelsFavoritesFirst = listOf("Amazon Prime", "Netflix", "Disney+", "YouTube")
        assertEquals(expectedLabelsFavoritesFirst, sortedFavoritesFirst.map { it.label })

        val sortedPurelyAlphabetical = repo.sortApps(apps, favoritesFirst = false)
        val expectedLabelsAlphabetical = listOf("Amazon Prime", "Disney+", "Netflix", "YouTube")
        assertEquals(expectedLabelsAlphabetical, sortedPurelyAlphabetical.map { it.label })
    }

    /**
     * Verifies that colors.xml defines the required centralized palette without emojis.
     */
    @Test
    fun testCentralizedColorsXmlPalette() {
        val colorsFile = resolveFile("src/main/res/values/colors.xml")
        assertTrue("colors.xml must exist at ${colorsFile.absolutePath}", colorsFile.exists())

        val content = colorsFile.readText()
        assertFalse("colors.xml must NOT contain any emojis", containsEmoji(content))

        assertTrue("colors.xml must define bg_dark #0A0A0E", content.contains("name=\"bg_dark\">#0A0A0E<"))
        assertTrue("colors.xml must define card_focused_stroke #FFFFFFFF", content.contains("name=\"card_focused_stroke\">#FFFFFFFF<"))
        assertTrue("colors.xml must define text_primary #F3F4F6", content.contains("name=\"text_primary\">#F3F4F6<"))
        assertTrue("colors.xml must define text_secondary #9CA3AF", content.contains("name=\"text_secondary\">#9CA3AF<"))
        assertTrue("colors.xml must define accent_blue #2563EB", content.contains("name=\"accent_blue\">#2563EB<"))
    }

    /**
     * Verifies that styles.xml references @color/bg_dark instead of hardcoded hex colors.
     */
    @Test
    fun testStylesXmlReferencesBgDark() {
        val stylesFile = resolveFile("src/main/res/values/styles.xml")
        assertTrue("styles.xml must exist at ${stylesFile.absolutePath}", stylesFile.exists())

        val content = stylesFile.readText()
        assertFalse("styles.xml must NOT contain any emojis", containsEmoji(content))
        assertTrue("styles.xml must reference @color/bg_dark for windowBackground", content.contains("<item name=\"android:windowBackground\">@color/bg_dark</item>"))
        assertTrue("styles.xml must reference @color/bg_dark for colorBackground", content.contains("<item name=\"android:colorBackground\">@color/bg_dark</item>"))
    }

    /**
     * Enforces the Zero Emojis policy across model and repository Kotlin files.
     */
    @Test
    fun testZeroEmojisInModelAndRepositorySourceFiles() {
        val modelFile = resolveFile("src/main/java/com/wanbo/launcher/model/AppItem.kt")
        val repoFile = resolveFile("src/main/java/com/wanbo/launcher/data/AppsRepository.kt")

        assertTrue("AppItem.kt must exist", modelFile.exists())
        assertTrue("AppsRepository.kt must exist", repoFile.exists())

        assertFalse("AppItem.kt must NOT contain any emojis", containsEmoji(modelFile.readText()))
        assertFalse("AppsRepository.kt must NOT contain any emojis", containsEmoji(repoFile.readText()))
    }
}
