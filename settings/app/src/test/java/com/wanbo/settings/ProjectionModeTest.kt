package com.wanbo.settings

import android.content.Intent
import com.wanbo.settings.panes.AspectRatioMode
import com.wanbo.settings.panes.ProjectionMode
import com.wanbo.settings.panes.ProjectionStateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating Projection and Keystone calibration logic:
 * - 4-way projection mode mapping (indices 0..3, titles, subtitles)
 * - Broadcast intent action, mirror_mode and flip_mode extras
 * - Keystone calibration primary, fallback components, and action strings
 * - Aspect ratio options and id-to-enum resolution
 * - Screen zoom range clamping (80% to 100%) and formatting
 * - Layout resource existence and structure verification
 * - Strict Zero Emojis compliance across projection source and layout files
 */
class ProjectionModeTest {

    /**
     * Resolves a relative path to a File across varying Gradle working directories.
     *
     * @param relativePath Relative path from the settings/app module directory.
     * @return Resolved File instance.
     */
    private fun resolveFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("settings/app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    /**
     * Verifies that all 4 hardware projection modes (0..3) map accurately
     * to expected indices, Portuguese titles, and subtitles.
     */
    @Test
    fun testAllFourProjectionModeMappings() {
        val modes = ProjectionMode.values()
        assertEquals("Must support exactly 4 projection modes", 4, modes.size)

        // Mode 0: Front (Desktop / Normal)
        val modeFront = ProjectionMode.fromIndex(0)
        assertEquals(ProjectionMode.FRONT, modeFront)
        assertEquals(0, modeFront.modeIndex)
        assertEquals("Projecao Frontal", modeFront.title)
        assertEquals("Mesa / Padrao", modeFront.subtitle)

        // Mode 1: Rear (Rear Desktop / Horizontal Flip)
        val modeRear = ProjectionMode.fromIndex(1)
        assertEquals(ProjectionMode.REAR, modeRear)
        assertEquals(1, modeRear.modeIndex)
        assertEquals("Projecao Traseira", modeRear.title)
        assertEquals("Mesa Traseira / Inversao Horizontal", modeRear.subtitle)

        // Mode 2: Front Ceiling (Ceiling Front / Vertical Flip)
        val modeFrontCeiling = ProjectionMode.fromIndex(2)
        assertEquals(ProjectionMode.FRONT_CEILING, modeFrontCeiling)
        assertEquals(2, modeFrontCeiling.modeIndex)
        assertEquals("Projecao Frontal Teto", modeFrontCeiling.title)
        assertEquals("Teto Frontal / Inversao Vertical", modeFrontCeiling.subtitle)

        // Mode 3: Rear Ceiling (Ceiling Rear / HV Flip)
        val modeRearCeiling = ProjectionMode.fromIndex(3)
        assertEquals(ProjectionMode.REAR_CEILING, modeRearCeiling)
        assertEquals(3, modeRearCeiling.modeIndex)
        assertEquals("Projecao Traseira Teto", modeRearCeiling.title)
        assertEquals("Teto Traseiro / Inversao H + V", modeRearCeiling.subtitle)

        // Out-of-bounds fallback
        assertEquals(ProjectionMode.FRONT, ProjectionMode.fromIndex(-1))
        assertEquals(ProjectionMode.FRONT, ProjectionMode.fromIndex(4))
        assertEquals(ProjectionMode.FRONT, ProjectionMode.fromIndex(99))
    }

    /**
     * Verifies that broadcast intent descriptors for SET_MIRROR_MODE contain
     * the exact action and both mirror_mode and flip_mode extras.
     */
    @Test
    fun testMirrorModeBroadcastDescriptors() {
        assertEquals("com.chijimlee.action.SET_MIRROR_MODE", ProjectionStateHelper.ACTION_SET_MIRROR_MODE)
        assertEquals("mirror_mode", ProjectionStateHelper.EXTRA_MIRROR_MODE)
        assertEquals("flip_mode", ProjectionStateHelper.EXTRA_FLIP_MODE)
        assertEquals("persist.sys.flip_mode", ProjectionStateHelper.PROP_FLIP_MODE)
        assertEquals("persist.sys.mirror_mode", ProjectionStateHelper.PROP_MIRROR_MODE)

        for (modeIndex in 0..3) {
            val descriptor = ProjectionStateHelper.getMirrorModeBroadcastDescriptor(modeIndex)
            assertEquals(ProjectionStateHelper.ACTION_SET_MIRROR_MODE, descriptor.action)
            assertEquals(modeIndex, descriptor.extras[ProjectionStateHelper.EXTRA_MIRROR_MODE])
            assertEquals(modeIndex, descriptor.extras[ProjectionStateHelper.EXTRA_FLIP_MODE])
        }
    }

    /**
     * Verifies helper functions for mode titles, subtitles, and validity checks.
     */
    @Test
    fun testProjectionStateHelperMethods() {
        assertEquals("Projecao Frontal", ProjectionStateHelper.getModeTitle(0))
        assertEquals("Projecao Traseira", ProjectionStateHelper.getModeTitle(1))
        assertEquals("Projecao Frontal Teto", ProjectionStateHelper.getModeTitle(2))
        assertEquals("Projecao Traseira Teto", ProjectionStateHelper.getModeTitle(3))

        assertEquals("Mesa / Padrao", ProjectionStateHelper.getModeSubtitle(0))
        assertEquals("Mesa Traseira / Inversao Horizontal", ProjectionStateHelper.getModeSubtitle(1))
        assertEquals("Teto Frontal / Inversao Vertical", ProjectionStateHelper.getModeSubtitle(2))
        assertEquals("Teto Traseiro / Inversao H + V", ProjectionStateHelper.getModeSubtitle(3))

        assertTrue(ProjectionStateHelper.isValidModeIndex(0))
        assertTrue(ProjectionStateHelper.isValidModeIndex(1))
        assertTrue(ProjectionStateHelper.isValidModeIndex(2))
        assertTrue(ProjectionStateHelper.isValidModeIndex(3))
        assertFalse(ProjectionStateHelper.isValidModeIndex(-1))
        assertFalse(ProjectionStateHelper.isValidModeIndex(4))
        assertFalse(ProjectionStateHelper.isValidModeIndex(10))
    }

    /**
     * Verifies Keystone calibration component names, fallback activities, and action strings.
     */
    @Test
    fun testKeystoneCalibrationComponentsAndIntents() {
        // Primary Keystone Component: tyylauncher ProjectModeActivity
        assertEquals("com.chijimlee.tyylauncher", ProjectionStateHelper.KEYSTONE_PRIMARY_PACKAGE)
        assertEquals(
            "com.chijimlee.tyylauncher.activity.ProjectModeActivity",
            ProjectionStateHelper.KEYSTONE_PRIMARY_ACTIVITY
        )

        // Fallback Keystone Component: trapezoid FourPointActivity
        assertEquals("com.chijimlee.trapezoid", ProjectionStateHelper.KEYSTONE_FALLBACK_PACKAGE)
        assertEquals(
            "com.chijimlee.trapezoid.activity.FourPointActivity",
            ProjectionStateHelper.KEYSTONE_FALLBACK_ACTIVITY
        )

        // Fallback Keystone Action
        assertEquals(
            "com.chijimlee.trapezoid.ACTION_LAUNCH_CALIBRATION",
            ProjectionStateHelper.ACTION_LAUNCH_CALIBRATION
        )

        // Test Primary Descriptor
        val primaryDesc = ProjectionStateHelper.getPrimaryKeystoneDescriptor()
        assertEquals(ProjectionStateHelper.KEYSTONE_PRIMARY_PACKAGE, primaryDesc.packageName)
        assertEquals(ProjectionStateHelper.KEYSTONE_PRIMARY_ACTIVITY, primaryDesc.className)
        assertNull(primaryDesc.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, primaryDesc.flags)

        // Test Fallback Descriptor
        val fallbackDesc = ProjectionStateHelper.getFallbackKeystoneDescriptor()
        assertEquals(ProjectionStateHelper.KEYSTONE_FALLBACK_PACKAGE, fallbackDesc.packageName)
        assertEquals(ProjectionStateHelper.KEYSTONE_FALLBACK_ACTIVITY, fallbackDesc.className)
        assertNull(fallbackDesc.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, fallbackDesc.flags)

        // Test Action Descriptor
        val actionDesc = ProjectionStateHelper.getActionKeystoneDescriptor()
        assertNull(actionDesc.packageName)
        assertNull(actionDesc.className)
        assertEquals(ProjectionStateHelper.ACTION_LAUNCH_CALIBRATION, actionDesc.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, actionDesc.flags)
    }

    /**
     * Verifies AspectRatioMode configurations, IDs, titles, and resolution helper.
     */
    @Test
    fun testAspectRatioModesAndResolution() {
        val aspectModes = AspectRatioMode.values()
        assertEquals("Must support 3 aspect ratio modes", 3, aspectModes.size)

        assertEquals("16_9", AspectRatioMode.ASPECT_16_9.id)
        assertEquals("16:9 Widescreen", AspectRatioMode.ASPECT_16_9.title)

        assertEquals("4_3", AspectRatioMode.ASPECT_4_3.id)
        assertEquals("4:3 Padrao", AspectRatioMode.ASPECT_4_3.title)

        assertEquals("auto", AspectRatioMode.ASPECT_AUTO.id)
        assertEquals("Automatico", AspectRatioMode.ASPECT_AUTO.title)

        // ID Resolution
        assertEquals(AspectRatioMode.ASPECT_16_9, AspectRatioMode.fromId("16_9"))
        assertEquals(AspectRatioMode.ASPECT_4_3, AspectRatioMode.fromId("4_3"))
        assertEquals(AspectRatioMode.ASPECT_AUTO, AspectRatioMode.fromId("auto"))
        assertEquals(AspectRatioMode.ASPECT_AUTO, AspectRatioMode.fromId("AUTO"))

        // Fallback for null or unknown
        assertEquals(AspectRatioMode.ASPECT_16_9, AspectRatioMode.fromId(null))
        assertEquals(AspectRatioMode.ASPECT_16_9, AspectRatioMode.fromId("unknown_ratio"))
    }

    /**
     * Verifies screen zoom clamping within 80% to 100% and formatting.
     */
    @Test
    fun testScreenZoomClampingAndFormatting() {
        assertEquals(80, ProjectionStateHelper.MIN_ZOOM_PERCENTAGE)
        assertEquals(100, ProjectionStateHelper.MAX_ZOOM_PERCENTAGE)
        assertEquals(100, ProjectionStateHelper.DEFAULT_ZOOM_PERCENTAGE)

        // Clamping tests
        assertEquals(100, ProjectionStateHelper.clampZoomPercentage(100))
        assertEquals(100, ProjectionStateHelper.clampZoomPercentage(105))
        assertEquals(100, ProjectionStateHelper.clampZoomPercentage(200))
        assertEquals(80, ProjectionStateHelper.clampZoomPercentage(80))
        assertEquals(80, ProjectionStateHelper.clampZoomPercentage(75))
        assertEquals(80, ProjectionStateHelper.clampZoomPercentage(0))
        assertEquals(80, ProjectionStateHelper.clampZoomPercentage(-50))
        assertEquals(95, ProjectionStateHelper.clampZoomPercentage(95))
        assertEquals(85, ProjectionStateHelper.clampZoomPercentage(85))

        // Formatting tests
        assertEquals("100%", ProjectionStateHelper.formatZoomPercentage(100))
        assertEquals("95%", ProjectionStateHelper.formatZoomPercentage(95))
        assertEquals("85%", ProjectionStateHelper.formatZoomPercentage(85))
        assertEquals("80%", ProjectionStateHelper.formatZoomPercentage(80))
        assertEquals("80%", ProjectionStateHelper.formatZoomPercentage(60))
        assertEquals("100%", ProjectionStateHelper.formatZoomPercentage(120))
    }

    /**
     * Verifies that pane_projection.xml and item_projection_mode_card.xml
     * layout files exist and define the expected widget IDs and focus targets.
     */
    @Test
    fun testProjectionLayoutFilesStructure() {
        val paneFile = resolveFile("src/main/res/layout/pane_projection.xml")
        assertTrue("pane_projection.xml must exist", paneFile.exists())
        val paneContent = paneFile.readText()

        // Keystone hub card
        assertTrue(paneContent.contains("card_keystone_calibration"))
        assertTrue(paneContent.contains("badge_keystone_launch"))

        // 4 projection mode cards
        assertTrue(paneContent.contains("card_projection_front"))
        assertTrue(paneContent.contains("card_projection_rear"))
        assertTrue(paneContent.contains("card_projection_front_ceiling"))
        assertTrue(paneContent.contains("card_projection_rear_ceiling"))

        // Badges
        assertTrue(paneContent.contains("badge_projection_front"))
        assertTrue(paneContent.contains("badge_projection_rear"))
        assertTrue(paneContent.contains("badge_projection_front_ceiling"))
        assertTrue(paneContent.contains("badge_projection_rear_ceiling"))

        // Aspect ratio cards
        assertTrue(paneContent.contains("card_aspect_16_9"))
        assertTrue(paneContent.contains("card_aspect_4_3"))
        assertTrue(paneContent.contains("card_aspect_auto"))

        // Screen zoom controls
        assertTrue(paneContent.contains("card_screen_zoom"))
        assertTrue(paneContent.contains("text_zoom_percentage"))
        assertTrue(paneContent.contains("progress_screen_zoom"))
        assertTrue(paneContent.contains("btn_zoom_out"))
        assertTrue(paneContent.contains("btn_zoom_in"))

        // Card selector background
        assertTrue(paneContent.contains("@drawable/bg_pane_card_selector"))

        // Item card layout verification
        val itemFile = resolveFile("src/main/res/layout/item_projection_mode_card.xml")
        assertTrue("item_projection_mode_card.xml must exist", itemFile.exists())
        val itemContent = itemFile.readText()
        assertTrue(itemContent.contains("card_projection_mode_root"))
        assertTrue(itemContent.contains("text_projection_mode_title"))
        assertTrue(itemContent.contains("text_projection_mode_subtitle"))
        assertTrue(itemContent.contains("badge_projection_mode_status"))
    }

    /**
     * Verifies that SettingsActivity mounts ProjectionPaneController when SettingsCategory.PROJECTION is active.
     */
    @Test
    fun testSettingsActivityMountsProjectionPane() {
        val activityFile = resolveFile("src/main/java/com/wanbo/settings/SettingsActivity.kt")
        assertTrue("SettingsActivity.kt must exist", activityFile.exists())
        val content = activityFile.readText()

        assertTrue(content.contains("SettingsCategory.PROJECTION"))
        assertTrue(content.contains("ProjectionPaneController"))
        assertTrue(content.contains("projectionController"))
    }

    /**
     * Verifies strict Zero Emojis compliance across all projection-related files.
     */
    @Test
    fun testZeroEmojisInProjectionFiles() {
        val targetFiles = listOf(
            resolveFile("src/main/java/com/wanbo/settings/panes/ProjectionPaneController.kt"),
            resolveFile("src/main/res/layout/pane_projection.xml"),
            resolveFile("src/main/res/layout/item_projection_mode_card.xml"),
            resolveFile("src/test/java/com/wanbo/settings/ProjectionModeTest.kt")
        )

        for (file in targetFiles) {
            assertTrue("File ${file.name} must exist for emoji check", file.exists())
            val text = file.readText()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
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
