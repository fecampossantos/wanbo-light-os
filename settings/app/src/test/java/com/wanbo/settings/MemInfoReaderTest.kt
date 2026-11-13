package com.wanbo.settings

import com.wanbo.settings.panes.AppsPaneController
import com.wanbo.settings.util.MemInfoReader
import com.wanbo.settings.util.MemInfoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating Linux /proc/meminfo parsing, memory and swap utilization formulas,
 * storage snapshot computation, fallback specifications, protected HAL security filtering,
 * and strict Zero Emojis compliance across Task 7 files.
 */
class MemInfoReaderTest {

    /**
     * Verifies that a realistic /proc/meminfo text dump from the Wanbo T2 Max projector
     * parses into the expected physical RAM and ZRAM swap metrics.
     */
    @Test
    fun testParseRealWanboMeminfoOutput() {
        val sampleMeminfo = """
            MemTotal:         863232 kB
            MemFree:          123456 kB
            MemAvailable:     441344 kB
            Buffers:           14288 kB
            Cached:           297408 kB
            SwapCached:            0 kB
            Active:           312456 kB
            Inactive:         221344 kB
            SwapTotal:       1048576 kB
            SwapFree:         838860 kB
            Dirty:                48 kB
            Writeback:             0 kB
            AnonPages:        221344 kB
            Mapped:            86320 kB
            Shmem:              2048 kB
        """.trimIndent()

        val snapshot = MemInfoReader.parse(sampleMeminfo)

        // 863232 kB / 1024 = 843 MB Total RAM
        assertEquals(843L, snapshot.totalRamMb)
        // 123456 kB / 1024 = 120 MB Free RAM
        assertEquals(120L, snapshot.freeRamMb)
        // 441344 kB / 1024 = 431 MB Available RAM
        assertEquals(431L, snapshot.availableRamMb)
        // Used RAM = Total - Available = 843 - 431 = 412 MB
        assertEquals(412L, snapshot.usedRamMb)
        // RAM percentage = (412 / 843) * 100 = 48.87% -> 49%
        assertEquals(49, snapshot.ramUsagePercent)

        // Swap: 1048576 kB / 1024 = 1024 MB Total Swap
        assertEquals(1024L, snapshot.totalSwapMb)
        // SwapFree: 838860 kB / 1024 = 819 MB Free Swap
        assertEquals(819L, snapshot.freeSwapMb)
        // Used Swap = Total - Free = 1024 - 819 = 205 MB
        assertEquals(205L, snapshot.usedSwapMb)
        // Swap percentage = (205 / 1024) * 100 = 20.01% -> 20%
        assertEquals(20, snapshot.swapUsagePercent)
    }

    /**
     * Verifies parsing behavior when MemAvailable is omitted in legacy kernel dumps
     * by estimating available RAM from MemFree, Buffers, and Cached.
     */
    @Test
    fun testParseWithoutMemAvailableFallback() {
        val legacyMeminfo = """
            MemTotal:         863232 kB
            MemFree:          123456 kB
            Buffers:           20480 kB
            Cached:           297408 kB
            SwapTotal:       1048576 kB
            SwapFree:         838860 kB
        """.trimIndent()

        val snapshot = MemInfoReader.parse(legacyMeminfo)

        // Total = 843 MB
        assertEquals(843L, snapshot.totalRamMb)
        // Estimated available = (123456 + 20480 + 297408) / 1024 = 441344 / 1024 = 431 MB
        assertEquals(431L, snapshot.availableRamMb)
        assertEquals(412L, snapshot.usedRamMb)
        assertEquals(49, snapshot.ramUsagePercent)
    }

    /**
     * Verifies that blank, null, or malformed inputs cleanly return default Wanbo specifications
     * without throwing runtime exceptions.
     */
    @Test
    fun testFallbackOnMalformedOrEmptyContent() {
        val emptySnapshot = MemInfoReader.parse("")
        assertEquals(MemInfoReader.DEFAULT_TOTAL_RAM_MB, emptySnapshot.totalRamMb)
        assertEquals(MemInfoReader.DEFAULT_AVAILABLE_RAM_MB, emptySnapshot.availableRamMb)
        assertEquals(MemInfoReader.DEFAULT_USED_RAM_MB, emptySnapshot.usedRamMb)
        assertEquals(MemInfoReader.DEFAULT_TOTAL_SWAP_MB, emptySnapshot.totalSwapMb)
        assertEquals(MemInfoReader.DEFAULT_USED_SWAP_MB, emptySnapshot.usedSwapMb)
        assertEquals(MemInfoReader.DEFAULT_RAM_PERCENT, emptySnapshot.ramUsagePercent)
        assertEquals(MemInfoReader.DEFAULT_SWAP_PERCENT, emptySnapshot.swapUsagePercent)

        val nullSnapshot = MemInfoReader.parse(null)
        assertEquals(MemInfoReader.DEFAULT_TOTAL_RAM_MB, nullSnapshot.totalRamMb)

        val malformedSnapshot = MemInfoReader.parse("InvalidHeader: not_a_number\nOtherKey: nothing")
        assertEquals(MemInfoReader.DEFAULT_TOTAL_RAM_MB, malformedSnapshot.totalRamMb)
    }

    /**
     * Verifies readCurrent behavior when target file does not exist on host filesystem.
     */
    @Test
    fun testReadCurrentMissingFileReturnsFallback() {
        val nonExistentFile = File("target/non_existent_proc_meminfo_dummy")
        val snapshot = MemInfoReader.readCurrent(nonExistentFile)
        assertNotNull(snapshot)
        assertEquals(MemInfoReader.DEFAULT_TOTAL_RAM_MB, snapshot.totalRamMb)
        assertEquals(MemInfoReader.DEFAULT_USED_RAM_MB, snapshot.usedRamMb)
    }

    /**
     * Verifies zero-swap edge case when swap is completely unconfigured or disabled.
     */
    @Test
    fun testZeroSwapDisabledCase() {
        val zeroSwapMeminfo = """
            MemTotal:         863232 kB
            MemFree:          431616 kB
            MemAvailable:     431616 kB
            SwapTotal:             0 kB
            SwapFree:              0 kB
        """.trimIndent()

        val snapshot = MemInfoReader.parse(zeroSwapMeminfo)
        assertEquals(843L, snapshot.totalRamMb)
        assertEquals(0L, snapshot.totalSwapMb)
        assertEquals(0L, snapshot.freeSwapMb)
        assertEquals(0L, snapshot.usedSwapMb)
        assertEquals(0, snapshot.swapUsagePercent)
    }

    /**
     * Verifies byte formatting calculations across gigabytes, megabytes, and kilobytes.
     */
    @Test
    fun testStorageFormattingHelpers() {
        // Zero and negative bytes
        assertEquals("0 MB", MemInfoReader.formatBytes(0L))
        assertEquals("0 MB", MemInfoReader.formatBytes(-100L))

        // Kilobytes (< 1 MB)
        assertEquals("500 KB", MemInfoReader.formatBytes(500L * 1024L))

        // Megabytes (< 1 GB)
        assertEquals("25.0 MB", MemInfoReader.formatBytes(25L * 1024L * 1024L))
        assertEquals("512.0 MB", MemInfoReader.formatBytes(512L * 1024L * 1024L))

        // Gigabytes (>= 1 GB)
        assertEquals("1.00 GB", MemInfoReader.formatBytes(1L * 1024L * 1024L * 1024L))
        assertEquals("8.00 GB", MemInfoReader.formatBytes(8L * 1024L * 1024L * 1024L))
        assertEquals("4.80 GB", MemInfoReader.formatBytes((4.8 * 1024.0 * 1024.0 * 1024.0).toLong()))

        // Scalar conversions
        assertEquals(8.0, MemInfoReader.bytesToGigabytes(8L * 1024L * 1024L * 1024L), 0.001)
        assertEquals(512L, MemInfoReader.bytesToMegabytes(512L * 1024L * 1024L))
    }

    /**
     * Verifies percentage usage computation and boundary clamping.
     */
    @Test
    fun testStorageUsagePercentageCalculations() {
        val totalBytes = 8L * 1024L * 1024L * 1024L // 8 GB
        val halfBytes = 4L * 1024L * 1024L * 1024L // 4 GB

        assertEquals(50, MemInfoReader.calculateUsagePercentage(halfBytes, totalBytes))
        assertEquals(0, MemInfoReader.calculateUsagePercentage(0L, totalBytes))
        assertEquals(100, MemInfoReader.calculateUsagePercentage(totalBytes, totalBytes))
        assertEquals(0, MemInfoReader.calculateUsagePercentage(halfBytes, 0L))
    }

    /**
     * Verifies complete StorageSnapshot construction and string properties.
     */
    @Test
    fun testComputeStorageSnapshot() {
        val totalBytes = 8L * 1024L * 1024L * 1024L
        val freeBytes = (4.8 * 1024.0 * 1024.0 * 1024.0).toLong()
        val snapshot = MemInfoReader.computeStorageSnapshot(totalBytes, freeBytes)

        assertEquals(totalBytes, snapshot.totalBytes)
        assertEquals(freeBytes, snapshot.freeBytes)
        assertEquals(totalBytes - freeBytes, snapshot.usedBytes)
        assertEquals(40, snapshot.usagePercent)
        assertEquals("8.00 GB", snapshot.totalGbFormatted)
        assertEquals("4.80 GB", snapshot.freeGbFormatted)
        assertEquals("3.20 GB", snapshot.usedGbFormatted)
    }

    /**
     * Verifies that critical vendor HAL service packages are accurately identified
     * as protected components to prevent tampering or uninstallation.
     */
    @Test
    fun testProtectedHalSecurityFilter() {
        val protectedPackages = listOf(
            "com.mstar.android.trapezoid",
            "com.mstar.android.trapezoidServer",
            "com.mstar.tvinput",
            "com.mediatek.tvinput",
            "com.mstar.android.dtv_svc",
            "com.mstar.android.autopair",
            "com.mstar.android.loadingequalizer",
            "com.mstar.android.kxtj2server",
            "com.mstar.android.hotkey.dispatcher",
            "com.mstar.tv.service"
        )

        for (pkg in protectedPackages) {
            assertTrue("Package $pkg must be classified as protected HAL", AppsPaneController.isProtectedHal(pkg))
        }

        // Null and blank packages must be defensively protected
        assertTrue(AppsPaneController.isProtectedHal(null))
        assertTrue(AppsPaneController.isProtectedHal(""))
        assertTrue(AppsPaneController.isProtectedHal("   "))

        // Standard user and third-party apps must NOT be protected
        val allowedApps = listOf(
            "com.google.android.youtube",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "org.videolan.vlc",
            "com.spotify.tv.android"
        )

        for (pkg in allowedApps) {
            assertFalse("Package $pkg must not be protected", AppsPaneController.isProtectedHal(pkg))
        }
    }

    /**
     * Verifies strict Zero Emojis compliance across all Task 7 files.
     */
    @Test
    fun testZeroEmojiComplianceAcrossTask7Files() {
        val candidates = listOf(
            File("src"),
            File("app/src"),
            File("settings/app/src"),
            File("C:/Users/fecam/wanbo-light-os/settings/app/src")
        )
        val srcRoot = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate src directory from candidates: $candidates")

        val targetFilenames = setOf(
            "MemInfoReader.kt",
            "AboutPaneController.kt",
            "AppsPaneController.kt",
            "SystemPaneController.kt",
            "pane_about.xml",
            "pane_apps.xml",
            "pane_system.xml",
            "item_app_management.xml",
            "SettingsActivity.kt",
            "MemInfoReaderTest.kt"
        )

        val filesToCheck = mutableListOf<File>()
        fun scan(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    scan(child)
                } else if (child.isFile && targetFilenames.contains(child.name)) {
                    filesToCheck.add(child)
                }
            }
        }
        scan(srcRoot)

        assertEquals("Must locate and verify all 10 target files for Task 7", 10, filesToCheck.size)

        for (file in filesToCheck) {
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
                    "File ${file.name} contains emoji character 0x${Integer.toHexString(cp)} violating Zero Emojis policy",
                    isEmoji
                )
                i += Character.charCount(cp)
            }
        }
    }
}
