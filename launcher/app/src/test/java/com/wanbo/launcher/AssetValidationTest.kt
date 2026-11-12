package com.wanbo.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the launcher asset suite: vector drawables, focus backgrounds,
 * Leanback theme styles, and strictly enforces the Zero Emojis policy.
 */
class AssetValidationTest {

    /**
     * Resolves a relative resource directory path across different execution contexts
     * (repo root, launcher/ folder, or launcher/app/ folder).
     *
     * @param subPath The subpath inside res (e.g., "drawable" or "values").
     * @return The resolved directory File.
     */
    private fun resolveResDir(subPath: String): File {
        val candidates = listOf(
            File("src/main/res", subPath),
            File("app/src/main/res", subPath),
            File("launcher/app/src/main/res", subPath)
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: File("src/main/res", subPath)
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
            in 0x2600..0x26FF,   // Misc symbols (weather, zodiac, pointing hands, etc.)
            in 0x2700..0x27BF,   // Dingbats
            in 0xFE00..0xFE0F,   // Variation Selectors
            in 0x1F1E6..0x1F1FF  // Regional Indicator Symbols
            -> true
            else -> Character.getType(codePoint) == Character.SURROGATE.toInt()
        }
    }

    /**
     * Checks whether a string contains any emoji character code points.
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
     * Verifies that all required vector drawables exist, declare the `<vector` root tag,
     * and contain no emoji characters.
     */
    @Test
    fun testVectorDrawablesExistAndHaveNoEmojis() {
        val drawableDir = resolveResDir("drawable")
        val requiredVectors = listOf(
            "ic_hdmi.xml",
            "ic_trapezoid_calibrate.xml",
            "ic_settings.xml",
            "ic_wifi.xml",
            "ic_launcher.xml",
            "banner.xml"
        )

        for (name in requiredVectors) {
            val file = File(drawableDir, name)
            assertTrue("Required vector drawable $name must exist in $drawableDir", file.exists())
            val content = file.readText()
            assertTrue("Vector drawable $name must declare <vector tag", content.contains("<vector"))
            assertFalse("Vector drawable $name must NOT contain any emojis", containsEmoji(content))
        }
    }

    /**
     * Verifies that all background and state-list drawables exist, declare valid
     * `<shape` or `<selector` root tags, and contain no emoji characters.
     */
    @Test
    fun testBackgroundDrawablesExistAndHaveNoEmojis() {
        val drawableDir = resolveResDir("drawable")
        val requiredDrawables = listOf(
            "bg_card_focused.xml",
            "bg_card_unfocused.xml",
            "bg_button_quick.xml",
            "bg_top_shelf.xml"
        )

        for (name in requiredDrawables) {
            val file = File(drawableDir, name)
            assertTrue("Required background drawable $name must exist in $drawableDir", file.exists())
            val content = file.readText()
            assertTrue(
                "Drawable $name must declare <shape or <selector tag",
                content.contains("<shape") || content.contains("<selector")
            )
            assertFalse("Drawable $name must NOT contain any emojis", containsEmoji(content))
        }
    }

    /**
     * Verifies that the Leanback theme styles.xml exists, defines Theme.WanboLauncher
     * extending Theme.Leanback, has dark background, and contains no emojis.
     */
    @Test
    fun testLeanbackThemeStylesExistAndHaveNoEmojis() {
        val valuesDir = resolveResDir("values")
        val stylesFile = File(valuesDir, "styles.xml")
        assertTrue("styles.xml must exist in $valuesDir", stylesFile.exists())

        val content = stylesFile.readText()
        assertTrue("styles.xml must declare Theme.WanboLauncher", content.contains("name=\"Theme.WanboLauncher\""))
        assertTrue("Theme.WanboLauncher must inherit from Theme.Leanback", content.contains("parent=\"Theme.Leanback\""))
        assertTrue("Theme.WanboLauncher must specify dark windowBackground", content.contains("windowBackground"))
        assertFalse("styles.xml must NOT contain any emojis", containsEmoji(content))
    }

    /**
     * Scans all XML files under res/drawable and res/values to strictly enforce the
     * Zero Emojis policy across the entire asset suite.
     */
    @Test
    fun testZeroEmojisAcrossAllResXmlFiles() {
        val drawableDir = resolveResDir("drawable")
        val valuesDir = resolveResDir("values")

        val xmlFiles = (drawableDir.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray()) +
                (valuesDir.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray())

        assertTrue("At least one res XML file should be found", xmlFiles.isNotEmpty())

        for (file in xmlFiles) {
            val content = file.readText()
            assertFalse(
                "File ${file.name} in ${file.parent} violates Zero Emojis policy!",
                containsEmoji(content)
            )
        }
    }
}
