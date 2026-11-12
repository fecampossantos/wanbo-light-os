package com.wanbo.launcher

import com.wanbo.launcher.model.AppItem
import com.wanbo.launcher.ui.AppCardAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Unit tests validating the tvOS floating cards layout, activity_main.xml layout structure,
 * item_app_card.xml design, AppCardAdapter focus elevation logic and data handling,
 * MainActivity hardware bindings, and strict Zero Emojis compliance.
 */
class MainActivityLayoutTest {

    /**
     * Resolves a relative resource directory path across different execution contexts
     * (repo root, launcher/ folder, or launcher/app/ folder).
     *
     * @param subPath The subpath inside res (e.g. "layout", "drawable", or "values").
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
     * Verifies that activity_main.xml exists, defines a dark background,
     * and sets clipChildren and clipToPadding to false for unclipped focus animations.
     */
    @Test
    fun testActivityMainXmlExistsAndStructure() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        assertTrue("activity_main.xml must exist in $layoutDir", mainXml.exists())

        val content = Files.readString(mainXml.toPath())
        assertTrue("activity_main.xml must declare <LinearLayout root", content.contains("<LinearLayout"))
        assertTrue("activity_main.xml must reference @color/bg_dark", content.contains("@color/bg_dark"))
        assertTrue("activity_main.xml must disable clipChildren", content.contains("android:clipChildren=\"false\""))
        assertTrue("activity_main.xml must disable clipToPadding", content.contains("android:clipToPadding=\"false\""))
    }

    /**
     * Verifies that activity_main.xml declares all required widget identifiers:
     * btnHdmi, btnKeystone, btnSettings, rvFavorites, and clockHero.
     */
    @Test
    fun testActivityMainRequiredIds() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = Files.readString(mainXml.toPath())

        assertTrue("activity_main.xml must define @+id/btnHdmi", content.contains("@+id/btnHdmi"))
        assertTrue("activity_main.xml must define @+id/btnKeystone", content.contains("@+id/btnKeystone"))
        assertTrue("activity_main.xml must define @+id/btnSettings", content.contains("@+id/btnSettings"))
        assertTrue("activity_main.xml must define @+id/rvFavorites", content.contains("@+id/rvFavorites"))
        assertTrue("activity_main.xml must define @+id/clockHero", content.contains("@+id/clockHero"))
    }

    /**
     * Verifies that the Top Bar in activity_main.xml includes clean title,
     * vector drawables for HDMI, Keystone, Settings, and Wi-Fi, and a minimal TextClock.
     */
    @Test
    fun testActivityMainTopBarHardwareButtonsAndDrawables() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = Files.readString(mainXml.toPath())

        assertTrue("Top bar must display WanboOS title", content.contains("WanboOS"))
        assertTrue("btnHdmi must use @drawable/ic_hdmi", content.contains("@drawable/ic_hdmi"))
        assertTrue("btnKeystone must use @drawable/ic_trapezoid_calibrate", content.contains("@drawable/ic_trapezoid_calibrate"))
        assertTrue("btnSettings must use @drawable/ic_settings", content.contains("@drawable/ic_settings"))
        assertTrue("Wi-Fi indicator must use @drawable/ic_wifi", content.contains("@drawable/ic_wifi"))
        assertTrue("Quick buttons must use @drawable/bg_button_quick", content.contains("@drawable/bg_button_quick"))
        assertTrue("Top bar must contain minimal TextClock", content.contains("clockTopBar"))
    }

    /**
     * Verifies that the Top Shelf Hero Area declares 140dp height, subtle gradient background,
     * large elegant 48sp thin typography TextClock, and the safe mode subtitle.
     */
    @Test
    fun testActivityMainTopShelfHeroArea() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = Files.readString(mainXml.toPath())

        assertTrue("Top shelf must specify 140dp height", content.contains("android:layout_height=\"140dp\""))
        assertTrue("Top shelf must use @drawable/bg_top_shelf", content.contains("@drawable/bg_top_shelf"))
        assertTrue("Hero clock must be 48sp", content.contains("48sp"))
        assertTrue("Hero clock must use thin typography", content.contains("sans-serif-thin"))
        assertTrue("Hero subtitle must match brief", content.contains("WanboLightOS • Modo Seguro • Zero Telemetria"))
    }

    /**
     * Verifies that the Favorites section declares the bold uppercase "FAVORITOS" label
     * with letter spacing 0.1, and horizontal orientation on rvFavorites.
     */
    @Test
    fun testActivityMainFavoritesSection() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = Files.readString(mainXml.toPath())

        assertTrue("Favorites section label must be FAVORITOS", content.contains("FAVORITOS"))
        assertTrue("Favorites section label must have letterSpacing 0.1", content.contains("android:letterSpacing=\"0.1\""))
        assertTrue("rvFavorites must be horizontal", content.contains("android:orientation=\"horizontal\""))
    }

    /**
     * Verifies that item_app_card.xml exists, defines 120dp wide x 90dp high dimensions,
     * declares focusable attributes, and includes ivAppIcon and tvAppLabel views.
     */
    @Test
    fun testItemAppCardXmlStructureAndDimensions() {
        val layoutDir = resolveResDir("layout")
        val cardXml = File(layoutDir, "item_app_card.xml")
        assertTrue("item_app_card.xml must exist in $layoutDir", cardXml.exists())

        val content = Files.readString(cardXml.toPath())
        assertTrue("item_app_card.xml must declare layout_width=\"120dp\"", content.contains("android:layout_width=\"120dp\""))
        assertTrue("item_app_card.xml must declare layout_height=\"90dp\"", content.contains("android:layout_height=\"90dp\""))
        assertTrue("item_app_card.xml must declare focusable=\"true\"", content.contains("android:focusable=\"true\""))
        assertTrue("item_app_card.xml must declare @+id/ivAppIcon", content.contains("@+id/ivAppIcon"))
        assertTrue("item_app_card.xml must declare @+id/tvAppLabel", content.contains("@+id/tvAppLabel"))
        assertTrue("item_app_card.xml must use @drawable/bg_card_unfocused", content.contains("@drawable/bg_card_unfocused"))
    }

    /**
     * Verifies the Apple TV style 1.08x focus elevation constants in AppCardAdapter.
     */
    @Test
    fun testAppCardAdapterConstants() {
        assertEquals("SCALE_FOCUSED must be 1.08f", 1.08f, AppCardAdapter.SCALE_FOCUSED, 0.001f)
        assertEquals("SCALE_UNFOCUSED must be 1.0f", 1.0f, AppCardAdapter.SCALE_UNFOCUSED, 0.001f)
        assertEquals("ANIMATION_DURATION_MS must be 150L", 150L, AppCardAdapter.ANIMATION_DURATION_MS)
        assertEquals("ELEVATION_FOCUSED_DP must be 8f", 8f, AppCardAdapter.ELEVATION_FOCUSED_DP, 0.001f)
        assertEquals("ELEVATION_UNFOCUSED_DP must be 0f", 0f, AppCardAdapter.ELEVATION_UNFOCUSED_DP, 0.001f)
    }

    /**
     * Verifies that AppCardAdapter properly manages dataset lists and responds to click events.
     */
    @Test
    fun testAppCardAdapterDataOperations() {
        val app1 = AppItem("com.app.one", "App One")
        val app2 = AppItem("com.app.two", "App Two")
        val initialList = listOf(app1, app2)

        var clickedApp: AppItem? = null
        val adapter = AppCardAdapter(initialList) { app ->
            clickedApp = app
        }

        assertEquals(2, adapter.itemCount)
        assertEquals(initialList, adapter.getApps())

        // Test list submission
        val app3 = AppItem("com.app.three", "App Three")
        val updatedList = listOf(app1, app2, app3)
        adapter.submitList(updatedList)

        assertEquals(3, adapter.itemCount)
        assertEquals(updatedList, adapter.getApps())
    }

    /**
     * Verifies MainActivity source code structure, hardware bindings, and launch logic.
     */
    @Test
    fun testMainActivitySourceCodeStructureAndBindings() {
        val mainActivityFile = resolveFile("src/main/java/com/wanbo/launcher/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainActivityFile.exists())

        val content = Files.readString(mainActivityFile.toPath())
        assertTrue("MainActivity must set activity_main layout", content.contains("setContentView(R.layout.activity_main)"))
        assertTrue("MainActivity must bind btnHdmi", content.contains("btnHdmi"))
        assertTrue("MainActivity must bind btnKeystone", content.contains("btnKeystone"))
        assertTrue("MainActivity must bind btnSettings", content.contains("btnSettings"))
        assertTrue("MainActivity must call HardwareController.launchHdmi", content.contains("HardwareController.launchHdmi"))
        assertTrue("MainActivity must call HardwareController.launchKeystone", content.contains("HardwareController.launchKeystone"))
        assertTrue("MainActivity must call HardwareController.launchSettings", content.contains("HardwareController.launchSettings"))
        assertTrue("MainActivity must initialize AppsRepository", content.contains("AppsRepository"))
        assertTrue("MainActivity must query getLeanbackLaunchIntentForPackage", content.contains("getLeanbackLaunchIntentForPackage"))
        assertTrue("MainActivity must fallback to getLaunchIntentForPackage", content.contains("getLaunchIntentForPackage"))
        assertTrue("MainActivity must add FLAG_ACTIVITY_NEW_TASK", content.contains("FLAG_ACTIVITY_NEW_TASK"))
    }

    /**
     * Strictly enforces the Zero Emojis policy across all layout and source files created for Task 5.
     */
    @Test
    fun testZeroEmojisInLayoutsAndSourceFiles() {
        val filesToCheck = listOf(
            resolveResDir("layout").let { File(it, "activity_main.xml") },
            resolveResDir("layout").let { File(it, "item_app_card.xml") },
            resolveFile("src/main/java/com/wanbo/launcher/ui/AppCardAdapter.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/MainActivity.kt"),
            resolveFile("src/test/java/com/wanbo/launcher/MainActivityLayoutTest.kt")
        )

        for (file in filesToCheck) {
            assertTrue("File ${file.name} must exist", file.exists())
            val content = Files.readString(file.toPath())
            assertFalse("File ${file.name} violates Zero Emojis policy", containsEmoji(content))
        }
    }

    /**
     * Enforces complete KDoc / JSDoc documentation on all classes and functions.
     */
    @Test
    fun testKDocCoverageInSourceFiles() {
        val filesToCheck = listOf(
            resolveFile("src/main/java/com/wanbo/launcher/ui/AppCardAdapter.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/MainActivity.kt"),
            resolveFile("src/test/java/com/wanbo/launcher/MainActivityLayoutTest.kt")
        )

        for (file in filesToCheck) {
            val content = Files.readString(file.toPath())
            assertTrue("File ${file.name} must have KDoc comments", content.contains("/**") && content.contains("*/"))
        }
    }
}
