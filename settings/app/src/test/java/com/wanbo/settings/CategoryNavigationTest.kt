package com.wanbo.settings

import com.wanbo.settings.model.CategoryItem
import com.wanbo.settings.model.SettingsCategory
import com.wanbo.settings.model.fromIntentAction
import com.wanbo.settings.ui.DpadFocusEngine
import com.wanbo.settings.ui.FocusPane
import com.wanbo.settings.ui.NavigationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating tvOS Split-View navigation, category metadata,
 * intent action resolution, D-pad focus transitions, and Zero Emojis compliance.
 */
class CategoryNavigationTest {

    /**
     * Verifies that all 11 required system intent actions declared in AndroidManifest
     * map accurately to their designated SettingsCategory constants.
     */
    @Test
    fun testAllElevenIntentActionMappings() {
        val testCases = mapOf(
            "android.intent.action.MAIN" to SettingsCategory.WIFI,
            "android.settings.SETTINGS" to SettingsCategory.WIFI,
            "android.settings.WIFI_SETTINGS" to SettingsCategory.WIFI,
            "android.settings.NETWORK_OPERATOR_SETTINGS" to SettingsCategory.WIFI,
            "android.settings.BLUETOOTH_SETTINGS" to SettingsCategory.BLUETOOTH,
            "android.settings.DISPLAY_SETTINGS" to SettingsCategory.PROJECTION,
            "android.settings.SOUND_SETTINGS" to SettingsCategory.AUDIO,
            "android.settings.APPLICATION_SETTINGS" to SettingsCategory.APPS,
            "android.settings.MANAGE_APPLICATIONS_SETTINGS" to SettingsCategory.APPS,
            "android.settings.DATE_SETTINGS" to SettingsCategory.SYSTEM,
            "android.settings.DEVICE_INFO_SETTINGS" to SettingsCategory.ABOUT
        )

        assertEquals("Must test exactly 11 declared intent actions", 11, testCases.size)

        for ((action, expectedCategory) in testCases) {
            val resolvedCompanion = SettingsCategory.fromIntentAction(action)
            val resolvedTopLevel = fromIntentAction(action)

            assertEquals(
                "Action $action must resolve to $expectedCategory via companion",
                expectedCategory,
                resolvedCompanion
            )
            assertEquals(
                "Action $action must resolve to $expectedCategory via top-level helper",
                expectedCategory,
                resolvedTopLevel
            )
        }

        assertEquals(
            "Null intent action must default to SettingsCategory.WIFI",
            SettingsCategory.WIFI,
            SettingsCategory.fromIntentAction(null)
        )
        assertEquals(
            "Unknown intent action must default to SettingsCategory.WIFI",
            SettingsCategory.WIFI,
            SettingsCategory.fromIntentAction("android.settings.UNKNOWN_NONEXISTENT_SETTING")
        )
    }

    /**
     * Verifies exact title and subtitle properties of all seven settings categories.
     */
    @Test
    fun testAllSevenCategoryTitlesAndSubtitles() {
        val categories = SettingsCategory.values()
        assertEquals("Must define exactly 7 categories", 7, categories.size)

        val expectedMetadata = mapOf(
            SettingsCategory.WIFI to Pair("Rede & Wi-Fi", "Status da conexao e redes sem fio"),
            SettingsCategory.BLUETOOTH to Pair("Bluetooth", "Controles, teclados e dispositivos de audio"),
            SettingsCategory.PROJECTION to Pair("Imagem & Projecao", "Keystone, modo de projecao e proporcao"),
            SettingsCategory.AUDIO to Pair("Som & Audio", "Saida de audio, volume e equalizacao"),
            SettingsCategory.APPS to Pair("Aplicativos", "Gerenciamento e espaco de armazenamento"),
            SettingsCategory.SYSTEM to Pair("Sistema", "Data, hora, idioma e opcoes de desenvolvedor"),
            SettingsCategory.ABOUT to Pair("Sobre o Projetor", "Monitor de RAM em tempo real e informacoes")
        )

        for (category in categories) {
            val expected = expectedMetadata[category]
                ?: throw AssertionError("Missing expected metadata definition for category: $category")

            assertEquals(
                "Title mismatch for ${category.name}",
                expected.first,
                category.title
            )
            assertEquals(
                "Subtitle mismatch for ${category.name}",
                expected.second,
                category.subtitle
            )
        }
    }

    /**
     * Verifies CategoryItem model construction and property defaults.
     */
    @Test
    fun testCategoryItemModelDefaults() {
        val item = CategoryItem(SettingsCategory.PROJECTION)
        assertEquals(SettingsCategory.PROJECTION, item.category)
        assertEquals("Imagem & Projecao", item.title)
        assertEquals("Keystone, modo de projecao e proporcao", item.subtitle)
        assertFalse(item.isSelected)

        item.isSelected = true
        assertTrue(item.isSelected)
    }

    /**
     * Verifies D-pad navigation actions when focus is located in the left category rail.
     */
    @Test
    fun testDpadNavigationFromLeftRail() {
        val engine = DpadFocusEngine(SettingsCategory.WIFI)
        assertEquals(FocusPane.LEFT_RAIL, engine.currentPane)
        assertEquals(0, engine.selectedPosition)

        // DPAD_RIGHT transfers focus to right pane
        val actionRight = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_DPAD_RIGHT, isActionDown = true)
        assertEquals(NavigationAction.FOCUS_RIGHT_PANE, actionRight)
        assertEquals(FocusPane.RIGHT_CONTENT, engine.currentPane)

        // Reset to left rail
        engine.setFocusPane(FocusPane.LEFT_RAIL)

        // DPAD_CENTER transfers focus to right pane
        val actionCenter = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_DPAD_CENTER, isActionDown = true)
        assertEquals(NavigationAction.FOCUS_RIGHT_PANE, actionCenter)
        assertEquals(FocusPane.RIGHT_CONTENT, engine.currentPane)

        // Reset to left rail
        engine.setFocusPane(FocusPane.LEFT_RAIL)

        // ENTER transfers focus to right pane
        val actionEnter = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_ENTER, isActionDown = true)
        assertEquals(NavigationAction.FOCUS_RIGHT_PANE, actionEnter)
        assertEquals(FocusPane.RIGHT_CONTENT, engine.currentPane)

        // Reset to left rail
        engine.setFocusPane(FocusPane.LEFT_RAIL)

        // DPAD_DOWN navigates category down
        val actionDown = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_DPAD_DOWN, isActionDown = true)
        assertEquals(NavigationAction.NAVIGATE_CATEGORY_DOWN, actionDown)
        assertEquals(1, engine.selectedPosition)
        assertEquals(SettingsCategory.BLUETOOTH, engine.selectedCategory)

        // DPAD_UP navigates category up
        val actionUp = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_DPAD_UP, isActionDown = true)
        assertEquals(NavigationAction.NAVIGATE_CATEGORY_UP, actionUp)
        assertEquals(0, engine.selectedPosition)
        assertEquals(SettingsCategory.WIFI, engine.selectedCategory)

        // BACK while in left rail exits activity
        val actionBack = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_BACK, isActionDown = true)
        assertEquals(NavigationAction.EXIT_ACTIVITY, actionBack)
    }

    /**
     * Verifies D-pad navigation actions when focus is located in the right content pane.
     */
    @Test
    fun testDpadNavigationFromRightContentPane() {
        val engine = DpadFocusEngine(SettingsCategory.PROJECTION)
        engine.setFocusPane(FocusPane.RIGHT_CONTENT)

        // DPAD_LEFT with no internal left focus moves focus back to left rail
        val actionLeft = engine.handleKeyEvent(
            DpadFocusEngine.KEYCODE_DPAD_LEFT,
            isActionDown = true,
            canNavigateLeftInsidePane = false
        )
        assertEquals(NavigationAction.FOCUS_LEFT_RAIL, actionLeft)
        assertEquals(FocusPane.LEFT_RAIL, engine.currentPane)

        // Return focus to right content pane
        engine.setFocusPane(FocusPane.RIGHT_CONTENT)

        // DPAD_LEFT with internal focus stays in right pane
        val actionInternal = engine.handleKeyEvent(
            DpadFocusEngine.KEYCODE_DPAD_LEFT,
            isActionDown = true,
            canNavigateLeftInsidePane = true
        )
        assertEquals(NavigationAction.INTERNAL_PANE_NAVIGATION, actionInternal)
        assertEquals(FocusPane.RIGHT_CONTENT, engine.currentPane)

        // BACK while in right content pane returns focus to left rail
        val actionBack = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_BACK, isActionDown = true)
        assertEquals(NavigationAction.FOCUS_LEFT_RAIL, actionBack)
        assertEquals(FocusPane.LEFT_RAIL, engine.currentPane)
    }

    /**
     * Verifies boundary clamping when navigating past the top or bottom of the category rail.
     */
    @Test
    fun testDpadIndexClamping() {
        val engine = DpadFocusEngine(SettingsCategory.WIFI)
        assertEquals(0, engine.selectedPosition)

        // Navigating UP from first position stays at 0
        val clampedTop = engine.computeTargetIndex(0, 7, isUp = true)
        assertEquals(0, clampedTop)

        // Navigating DOWN from last position stays at 6
        val clampedBottom = engine.computeTargetIndex(6, 7, isUp = false)
        assertEquals(6, clampedBottom)

        // Zero count edge case returns 0
        val zeroCount = engine.computeTargetIndex(0, 0, isUp = true)
        assertEquals(0, zeroCount)
    }

    /**
     * Verifies that key release events (ACTION_UP) are ignored by the focus engine.
     */
    @Test
    fun testKeyReleaseIgnored() {
        val engine = DpadFocusEngine()
        val action = engine.handleKeyEvent(DpadFocusEngine.KEYCODE_DPAD_RIGHT, isActionDown = false)
        assertEquals(NavigationAction.IGNORED, action)
    }

    /**
     * Verifies strict Zero Emojis compliance across all source and test files in settings module.
     */
    @Test
    fun testZeroEmojiComplianceAcrossNavigationFiles() {
        val candidates = listOf(
            File("src"),
            File("app/src"),
            File("settings/app/src"),
            File("C:/Users/fecam/wanbo-light-os/settings/app/src")
        )
        val srcRoot = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not resolve src directory from candidates: $candidates")

        val filesToCheck = mutableListOf<File>()
        fun scan(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { scan(it) }
            } else if (file.isFile) {
                filesToCheck.add(file)
            }
        }
        scan(srcRoot)

        assertTrue("Must scan navigation source files", filesToCheck.size >= 8)

        for (file in filesToCheck) {
            val content = file.readText()
            var i = 0
            while (i < content.length) {
                val cp = content.codePointAt(i)
                val isEmoji = cp in 0x1F300..0x1F5FF ||
                    cp in 0x1F600..0x1F64F ||
                    cp in 0x1F680..0x1F6FF ||
                    cp in 0x1F700..0x1F77F ||
                    cp in 0x1F780..0x1F7FF ||
                    cp in 0x1F800..0x1F8FF ||
                    cp in 0x1F900..0x1F9FF ||
                    cp in 0x1FA00..0x1FA6F ||
                    cp in 0x1FA70..0x1FAFF ||
                    cp in 0x2600..0x26FF ||
                    cp in 0x2700..0x27BF ||
                    cp in 0xFE00..0xFE0F ||
                    cp in 0x1F1E6..0x1F1FF

                assertFalse(
                    "File ${file.name} contains emoji codepoint 0x${Integer.toHexString(cp)} violating Zero Emojis policy",
                    isEmoji
                )
                i += Character.charCount(cp)
            }
        }
    }
}
