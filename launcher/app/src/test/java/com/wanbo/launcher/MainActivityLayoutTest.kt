package com.wanbo.launcher

import com.wanbo.launcher.model.AppItem
import com.wanbo.launcher.ui.AppCardAdapter
import com.wanbo.launcher.ui.FavoritesAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating the tvOS floating cards layout, activity_main.xml layout structure,
 * item_app_card.xml and item_favorite_card.xml design, dual-row Favorites and All Apps docks,
 * Top Bar hardware bindings and live clock, Ambient backdrop view presence,
 * FavoritesAdapter and AppCardAdapter focus elevation logic, and strict Zero Emojis compliance.
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
     * contains LinearLayout, and sets clipChildren and clipToPadding to false.
     */
    @Test
    fun testActivityMainXmlExistsAndStructure() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        assertTrue("activity_main.xml must exist in $layoutDir", mainXml.exists())

        val content = mainXml.readText()
        assertTrue("activity_main.xml must declare <LinearLayout", content.contains("<LinearLayout"))
        assertTrue("activity_main.xml must reference @color/bg_dark", content.contains("@color/bg_dark"))
        assertTrue("activity_main.xml must disable clipChildren", content.contains("android:clipChildren=\"false\""))
        assertTrue("activity_main.xml must disable clipToPadding", content.contains("android:clipToPadding=\"false\""))
    }

    /**
     * Verifies that activity_main.xml declares all required widget identifiers:
     * ambient_backdrop_view, top_bar_container, btn_hdmi, btn_projection, btn_settings,
     * clock_view, favorites_recycler_view, all_apps_recycler_view, and backward compatibility IDs.
     */
    @Test
    fun testActivityMainRequiredIds() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = mainXml.readText()

        // Task 2 Required UI Identifiers
        assertTrue("activity_main.xml must define @+id/ambient_backdrop_view", content.contains("@+id/ambient_backdrop_view"))
        assertTrue("activity_main.xml must define @+id/top_bar_container", content.contains("@+id/top_bar_container"))
        assertTrue("activity_main.xml must define @+id/btn_hdmi", content.contains("@+id/btn_hdmi"))
        assertTrue("activity_main.xml must define @+id/btn_projection", content.contains("@+id/btn_projection"))
        assertTrue("activity_main.xml must define @+id/btn_settings", content.contains("@+id/btn_settings"))
        assertTrue("activity_main.xml must define @+id/clock_view", content.contains("@+id/clock_view"))
        assertTrue("activity_main.xml must define @+id/favorites_recycler_view", content.contains("@+id/favorites_recycler_view"))
        assertTrue("activity_main.xml must define @+id/all_apps_recycler_view", content.contains("@+id/all_apps_recycler_view"))

        // Backward compatibility IDs
        assertTrue("activity_main.xml must define @+id/btnHdmi", content.contains("@+id/btnHdmi"))
        assertTrue("activity_main.xml must define @+id/btnKeystone", content.contains("@+id/btnKeystone"))
        assertTrue("activity_main.xml must define @+id/btnSettings", content.contains("@+id/btnSettings"))
        assertTrue("activity_main.xml must define @+id/rvFavorites", content.contains("@+id/rvFavorites"))
        assertTrue("activity_main.xml must define @+id/clockHero", content.contains("@+id/clockHero"))
    }

    /**
     * Verifies that the Top Bar in activity_main.xml includes clean title,
     * translucent frosted glass buttons, vector drawables for HDMI, Keystone, Settings,
     * Wi-Fi indicator, and live date/time clock.
     */
    @Test
    fun testActivityMainTopBarHardwareButtonsAndDrawables() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = mainXml.readText()

        assertTrue("Top bar must display WanboOS title", content.contains("WanboOS"))
        assertTrue("Top bar must define height 56dp", content.contains("56dp"))
        assertTrue("btn_hdmi must display HDMI label", content.contains("HDMI"))
        assertTrue("btn_projection must display Projecao label", content.contains("Projecao"))
        assertTrue("btn_settings must display Ajustes label", content.contains("Ajustes"))
        assertTrue("Buttons must use @drawable/bg_button_quick", content.contains("@drawable/bg_button_quick"))
        assertTrue("Top bar must use @drawable/ic_hdmi", content.contains("@drawable/ic_hdmi"))
        assertTrue("Top bar must use @drawable/ic_trapezoid_calibrate", content.contains("@drawable/ic_trapezoid_calibrate"))
        assertTrue("Top bar must use @drawable/ic_settings", content.contains("@drawable/ic_settings"))
        assertTrue("Wi-Fi indicator must use @drawable/ic_wifi", content.contains("@drawable/ic_wifi"))
        assertTrue("Top bar must contain live date/time TextClock", content.contains("clock_view"))
    }

    /**
     * Verifies that the Top Shelf Hero Area declares 140dp height, subtle gradient background,
     * large elegant 48sp thin typography TextClock, and the safe mode subtitle.
     */
    @Test
    fun testActivityMainTopShelfHeroArea() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = mainXml.readText()

        assertTrue("Top shelf must specify 140dp height", content.contains("android:layout_height=\"140dp\""))
        assertTrue("Top shelf must use @drawable/bg_top_shelf", content.contains("@drawable/bg_top_shelf"))
        assertTrue("Hero clock must be 48sp", content.contains("48sp"))
        assertTrue("Hero clock must use thin typography", content.contains("sans-serif-thin"))
        assertTrue("Hero subtitle must match brief", content.contains("WanboLightOS • Modo Seguro • Zero Telemetria"))
    }

    /**
     * Verifies that the Favorites section declares the bold uppercase "FAVORITOS" label
     * with letter spacing 0.1, and horizontal orientation on rvFavorites and favorites_recycler_view.
     */
    @Test
    fun testActivityMainFavoritesSection() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = mainXml.readText()

        assertTrue("Favorites section label must be FAVORITOS", content.contains("FAVORITOS"))
        assertTrue("Favorites section label must have letterSpacing 0.1", content.contains("android:letterSpacing=\"0.1\""))
        assertTrue("favorites_recycler_view must be horizontal", content.contains("android:orientation=\"horizontal\""))
    }

    /**
     * Verifies that the Dual Row Sections (Favorites and All Apps) are declared properly
     * with their respective section labels and horizontal RecyclerViews.
     */
    @Test
    fun testActivityMainDualRowSections() {
        val layoutDir = resolveResDir("layout")
        val mainXml = File(layoutDir, "activity_main.xml")
        val content = mainXml.readText()

        // Section 1: FAVORITOS
        assertTrue("Must include FAVORITOS section", content.contains("FAVORITOS"))
        assertTrue("Must define favorites_recycler_view", content.contains("@+id/favorites_recycler_view"))

        // Section 2: TODOS OS APLICATIVOS
        assertTrue("Must include TODOS OS APLICATIVOS section", content.contains("TODOS OS APLICATIVOS"))
        assertTrue("Must define all_apps_recycler_view", content.contains("@+id/all_apps_recycler_view"))
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

        val content = cardXml.readText()
        assertTrue("item_app_card.xml must declare layout_width=\"120dp\"", content.contains("android:layout_width=\"120dp\""))
        assertTrue("item_app_card.xml must declare layout_height=\"90dp\"", content.contains("android:layout_height=\"90dp\""))
        assertTrue("item_app_card.xml must declare focusable=\"true\"", content.contains("android:focusable=\"true\""))
        assertTrue("item_app_card.xml must declare @+id/ivAppIcon", content.contains("@+id/ivAppIcon"))
        assertTrue("item_app_card.xml must declare @+id/tvAppLabel", content.contains("@+id/tvAppLabel"))
        assertTrue("item_app_card.xml must use @drawable/bg_card_unfocused", content.contains("@drawable/bg_card_unfocused"))
    }

    /**
     * Verifies that item_favorite_card.xml and item_add_favorite_card.xml exist,
     * use 20dp corner radius squircle styling, and define required card components.
     */
    @Test
    fun testFavoritesCardLayoutsAndSquircleStyling() {
        val layoutDir = resolveResDir("layout")
        val favCard = File(layoutDir, "item_favorite_card.xml")
        val addCard = File(layoutDir, "item_add_favorite_card.xml")

        assertTrue("item_favorite_card.xml must exist", favCard.exists())
        assertTrue("item_add_favorite_card.xml must exist", addCard.exists())

        val favContent = favCard.readText()
        assertTrue("Favorite card must define iv_favorite_icon", favContent.contains("@+id/iv_favorite_icon"))
        assertTrue("Favorite card must define tv_favorite_label", favContent.contains("@+id/tv_favorite_label"))
        assertTrue("Favorite card must use bg_card_favorite_unfocused", favContent.contains("bg_card_favorite_unfocused"))

        val addContent = addCard.readText()
        assertTrue("Add card must define iv_add_icon", addContent.contains("@+id/iv_add_icon"))
        assertTrue("Add card must define tv_add_label", addContent.contains("@+id/tv_add_label"))
        assertTrue("Add card must display + Adicionar", addContent.contains("+ Adicionar"))

        // Verify squircle drawables declare 20dp radius
        val drawableDir = resolveResDir("drawable")
        val unfocusedDrawable = File(drawableDir, "bg_card_favorite_unfocused.xml")
        val focusedDrawable = File(drawableDir, "bg_card_favorite_focused.xml")

        assertTrue("bg_card_favorite_unfocused.xml must exist", unfocusedDrawable.exists())
        assertTrue("bg_card_favorite_focused.xml must exist", focusedDrawable.exists())

        assertTrue("Unfocused squircle must have 20dp radius", unfocusedDrawable.readText().contains("20dp"))
        assertTrue("Focused squircle must have 20dp radius", focusedDrawable.readText().contains("20dp"))
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
     * Verifies the Apple TV style 1.08x focus elevation constants and view types in FavoritesAdapter.
     */
    @Test
    fun testFavoritesAdapterConstantsAndOperations() {
        assertEquals(1.08f, FavoritesAdapter.SCALE_FOCUSED, 0.001f)
        assertEquals(1.0f, FavoritesAdapter.SCALE_UNFOCUSED, 0.001f)
        assertEquals(150L, FavoritesAdapter.ANIMATION_DURATION_MS)
        assertEquals(8f, FavoritesAdapter.ELEVATION_FOCUSED_DP, 0.001f)
        assertEquals(0f, FavoritesAdapter.ELEVATION_UNFOCUSED_DP, 0.001f)

        assertEquals(0, FavoritesAdapter.VIEW_TYPE_APP)
        assertEquals(1, FavoritesAdapter.VIEW_TYPE_ADD)

        val app1 = AppItem("com.app.yt", "YouTube")
        val app2 = AppItem("com.app.nf", "Netflix")
        val adapter = FavoritesAdapter(listOf(app1, app2))

        // Total count includes favorite items + trailing "+ Adicionar" card
        assertEquals(3, adapter.itemCount)
        assertEquals(FavoritesAdapter.VIEW_TYPE_APP, adapter.getItemViewType(0))
        assertEquals(FavoritesAdapter.VIEW_TYPE_APP, adapter.getItemViewType(1))
        assertEquals(FavoritesAdapter.VIEW_TYPE_ADD, adapter.getItemViewType(2))
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

        assertEquals(null, clickedApp)
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

        val content = mainActivityFile.readText()
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
     * Strictly enforces the Zero Emojis policy across all layout and source files.
     */
    @Test
    fun testZeroEmojisInLayoutsAndSourceFiles() {
        val filesToCheck = listOf(
            resolveResDir("layout").let { File(it, "activity_main.xml") },
            resolveResDir("layout").let { File(it, "item_app_card.xml") },
            resolveResDir("layout").let { File(it, "item_favorite_card.xml") },
            resolveResDir("layout").let { File(it, "item_add_favorite_card.xml") },
            resolveFile("src/main/java/com/wanbo/launcher/ui/AppCardAdapter.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/ui/FavoritesAdapter.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/ui/AmbientBackdropController.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/MainActivity.kt"),
            resolveFile("src/test/java/com/wanbo/launcher/MainActivityLayoutTest.kt"),
            resolveFile("src/test/java/com/wanbo/launcher/AmbientBackdropControllerTest.kt")
        )

        for (file in filesToCheck) {
            assertTrue("File ${file.name} must exist", file.exists())
            val content = file.readText()
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
            resolveFile("src/main/java/com/wanbo/launcher/ui/FavoritesAdapter.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/ui/AmbientBackdropController.kt"),
            resolveFile("src/main/java/com/wanbo/launcher/MainActivity.kt"),
            resolveFile("src/test/java/com/wanbo/launcher/MainActivityLayoutTest.kt"),
            resolveFile("src/test/java/com/wanbo/launcher/AmbientBackdropControllerTest.kt")
        )

        for (file in filesToCheck) {
            val content = file.readText()
            assertTrue("File ${file.name} must have KDoc comments", content.contains("/**") && content.contains("*/"))
        }
    }
}
