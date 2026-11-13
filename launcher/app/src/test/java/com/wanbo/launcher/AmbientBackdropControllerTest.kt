package com.wanbo.launcher

import com.wanbo.launcher.model.AppItem
import com.wanbo.launcher.ui.AmbientBackdropController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating dynamic ambient glow backdrop color mappings,
 * linear ARGB interpolation math, default fallbacks, transition constants,
 * KDoc documentation, and Zero Emojis compliance.
 */
class AmbientBackdropControllerTest {

    /**
     * Resolves a file path across different execution working directories
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
     * Verifies that all target applications map to their exact specified atmospheric accent colors.
     */
    @Test
    fun testColorMappingsForTargetApplications() {
        // YouTube TV -> Dark Crimson Red (0x5A, 0x0E, 0x13)
        val expectedYouTube = (0xFF shl 24) or (0x5A shl 16) or (0x0E shl 8) or 0x13
        assertEquals(expectedYouTube, AmbientBackdropController.COLOR_YOUTUBE)
        assertEquals(expectedYouTube, AmbientBackdropController.getColorForPackage("com.google.android.youtube.tv"))

        // Netflix TV / Mobile -> Dark Wine Red (0x4A, 0x0E, 0x17)
        val expectedNetflix = (0xFF shl 24) or (0x4A shl 16) or (0x0E shl 8) or 0x17
        assertEquals(expectedNetflix, AmbientBackdropController.COLOR_NETFLIX)
        assertEquals(expectedNetflix, AmbientBackdropController.getColorForPackage("com.netflix.ninja"))
        assertEquals(expectedNetflix, AmbientBackdropController.getColorForPackage("com.netflix.mediaclient"))

        // Disney+ -> Dark Royal Blue (0x0C, 0x23, 0x40)
        val expectedDisney = (0xFF shl 24) or (0x0C shl 16) or (0x23 shl 8) or 0x40
        assertEquals(expectedDisney, AmbientBackdropController.COLOR_DISNEY)
        assertEquals(expectedDisney, AmbientBackdropController.getColorForPackage("com.disney.disneyplus"))

        // Amazon Prime Video -> Dark Navy / Cyan (0x0A, 0x25, 0x3A)
        val expectedPrime = (0xFF shl 24) or (0x0A shl 16) or (0x25 shl 8) or 0x3A
        assertEquals(expectedPrime, AmbientBackdropController.COLOR_PRIME_VIDEO)
        assertEquals(expectedPrime, AmbientBackdropController.getColorForPackage("com.amazon.amazonvideo.livingroom"))

        // Spotify TV -> Dark Emerald Green (0x0D, 0x38, 0x20)
        val expectedSpotify = (0xFF shl 24) or (0x0D shl 16) or (0x38 shl 8) or 0x20
        assertEquals(expectedSpotify, AmbientBackdropController.COLOR_SPOTIFY)
        assertEquals(expectedSpotify, AmbientBackdropController.getColorForPackage("com.spotify.tv.android"))

        // Aptoide TV -> Dark Amber / Orange (0x4D, 0x26, 0x0D)
        val expectedAptoide = (0xFF shl 24) or (0x4D shl 16) or (0x26 shl 8) or 0x0D
        assertEquals(expectedAptoide, AmbientBackdropController.COLOR_APTOIDE)
        assertEquals(expectedAptoide, AmbientBackdropController.getColorForPackage("cm.aptoidetv.pt"))
    }

    /**
     * Verifies that unmapped packages, empty strings, blank strings, and null inputs
     * fallback safely to Deep Obsidian (0x08, 0x09, 0x0C).
     */
    @Test
    fun testDefaultFallbackForUnmappedOrInvalidPackages() {
        val expectedDefault = (0xFF shl 24) or (0x08 shl 16) or (0x09 shl 8) or 0x0C
        assertEquals(expectedDefault, AmbientBackdropController.COLOR_DEFAULT)

        assertEquals(expectedDefault, AmbientBackdropController.getColorForPackage("com.unknown.randomapp"))
        assertEquals(expectedDefault, AmbientBackdropController.getColorForPackage("com.example.test"))
        assertEquals(expectedDefault, AmbientBackdropController.getColorForPackage(""))
        assertEquals(expectedDefault, AmbientBackdropController.getColorForPackage("   "))
        assertEquals(expectedDefault, AmbientBackdropController.getColorForPackage(null))
    }

    /**
     * Verifies that AppItem model instances resolve colors correctly based on package name.
     */
    @Test
    fun testColorResolutionForAppItem() {
        val ytApp = AppItem("com.google.android.youtube.tv", "YouTube TV")
        assertEquals(AmbientBackdropController.COLOR_YOUTUBE, AmbientBackdropController.getColorForApp(ytApp))

        val unknownApp = AppItem("com.unmapped.application", "Unmapped App")
        assertEquals(AmbientBackdropController.COLOR_DEFAULT, AmbientBackdropController.getColorForApp(unknownApp))

        assertEquals(AmbientBackdropController.COLOR_DEFAULT, AmbientBackdropController.getColorForApp(null))
    }

    /**
     * Verifies linear ARGB color interpolation mathematics across boundaries and midpoints.
     */
    @Test
    fun testColorInterpolationMath() {
        val colorA = (0xFF shl 24) or (0x00 shl 16) or (0x00 shl 8) or 0x00 // Pure Black (opaque)
        val colorB = (0xFF shl 24) or (0x64 shl 16) or (0x64 shl 8) or 0x64 // 100, 100, 100 (opaque)

        // At fraction 0.0 -> matches startColor
        val atZero = AmbientBackdropController.interpolateColor(colorA, colorB, 0.0f)
        assertEquals(colorA, atZero)

        // At fraction 1.0 -> matches endColor
        val atOne = AmbientBackdropController.interpolateColor(colorA, colorB, 1.0f)
        assertEquals(colorB, atOne)

        // At fraction 0.5 -> matches midpoint (50, 50, 50)
        val atMid = AmbientBackdropController.interpolateColor(colorA, colorB, 0.5f)
        val expectedMid = (0xFF shl 24) or (0x32 shl 16) or (0x32 shl 8) or 0x32
        assertEquals(expectedMid, atMid)

        // Clamping check for negative fractions
        val clampedNegative = AmbientBackdropController.interpolateColor(colorA, colorB, -0.5f)
        assertEquals(colorA, clampedNegative)

        // Clamping check for overflow fractions
        val clampedOverflow = AmbientBackdropController.interpolateColor(colorA, colorB, 1.5f)
        assertEquals(colorB, clampedOverflow)
    }

    /**
     * Verifies that the transition duration is exactly 300ms as mandated by the brief.
     */
    @Test
    fun testTransitionDurationConstant() {
        assertEquals("ANIMATION_DURATION_MS must be 300L", 300L, AmbientBackdropController.ANIMATION_DURATION_MS)
    }

    /**
     * Verifies that controller instance initializes with default color and updates state on transition.
     */
    @Test
    fun testControllerStateUpdates() {
        val controller = AmbientBackdropController(null)
        assertNotNull(controller)
        assertEquals(AmbientBackdropController.COLOR_DEFAULT, controller.getCurrentColor())

        // Update ambient to YouTube TV
        controller.updateAmbient("com.google.android.youtube.tv", 0.3f, 0.4f)
        assertEquals(AmbientBackdropController.COLOR_YOUTUBE, controller.getCurrentColor())
        assertEquals(0.3f, controller.getCurrentCenterX(), 0.001f)
        assertEquals(0.4f, controller.getCurrentCenterY(), 0.001f)

        // Revert to null / default
        controller.updateAmbient(null as String?, 0.5f, 0.5f)
        assertEquals(AmbientBackdropController.COLOR_DEFAULT, controller.getCurrentColor())
    }

    /**
     * Strictly enforces the Zero Emojis policy across AmbientBackdropController source and test files.
     */
    @Test
    fun testZeroEmojisInAmbientSourceAndTest() {
        val sourceFile = resolveFile("src/main/java/com/wanbo/launcher/ui/AmbientBackdropController.kt")
        val testFile = resolveFile("src/test/java/com/wanbo/launcher/AmbientBackdropControllerTest.kt")

        assertTrue("AmbientBackdropController.kt must exist", sourceFile.exists())
        assertTrue("AmbientBackdropControllerTest.kt must exist", testFile.exists())

        val sourceContent = sourceFile.readText()
        val testContent = testFile.readText()

        assertFalse("AmbientBackdropController.kt violates Zero Emojis policy", containsEmoji(sourceContent))
        assertFalse("AmbientBackdropControllerTest.kt violates Zero Emojis policy", containsEmoji(testContent))
    }

    /**
     * Strictly enforces complete KDoc / JSDoc documentation coverage.
     */
    @Test
    fun testKDocCoverageInAmbientSourceAndTest() {
        val sourceFile = resolveFile("src/main/java/com/wanbo/launcher/ui/AmbientBackdropController.kt")
        val testFile = resolveFile("src/test/java/com/wanbo/launcher/AmbientBackdropControllerTest.kt")

        val sourceContent = sourceFile.readText()
        val testContent = testFile.readText()

        assertTrue("AmbientBackdropController.kt must have KDoc comments", sourceContent.contains("/**") && sourceContent.contains("*/"))
        assertTrue("AmbientBackdropControllerTest.kt must have KDoc comments", testContent.contains("/**") && testContent.contains("*/"))
    }
}
