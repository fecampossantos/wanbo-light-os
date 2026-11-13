package com.wanbo.settings.util

import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Data snapshot capturing memory metrics parsed from /proc/meminfo.
 *
 * @property totalRamMb Total physical RAM capacity in megabytes (~843 MB on Wanbo T2 Max).
 * @property freeRamMb Completely unused physical RAM in megabytes.
 * @property availableRamMb Estimated available memory for starting new applications without swapping in megabytes.
 * @property usedRamMb Active used physical RAM (totalRamMb - availableRamMb) in megabytes.
 * @property totalSwapMb Total swap space capacity (ZRAM compressed swap) in megabytes (~1024 MB).
 * @property freeSwapMb Unused swap memory in megabytes.
 * @property usedSwapMb Active compressed swap memory (totalSwapMb - freeSwapMb) in megabytes.
 * @property ramUsagePercent Percentage of physical RAM currently in use (0 to 100).
 * @property swapUsagePercent Percentage of ZRAM swap currently in use (0 to 100).
 */
data class MemInfoSnapshot(
    val totalRamMb: Long,
    val freeRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val totalSwapMb: Long,
    val freeSwapMb: Long,
    val usedSwapMb: Long,
    val ramUsagePercent: Int,
    val swapUsagePercent: Int
)

/**
 * Data snapshot capturing internal eMMC storage usage metrics.
 *
 * @property totalBytes Total storage partition capacity in bytes.
 * @property freeBytes Available free storage space in bytes.
 * @property usedBytes Storage space currently occupied in bytes.
 * @property usagePercent Storage utilization percentage between 0 and 100.
 * @property totalGbFormatted Human-readable total storage string (e.g. "8.00 GB").
 * @property usedGbFormatted Human-readable used storage string (e.g. "3.20 GB").
 * @property freeGbFormatted Human-readable free storage string (e.g. "4.80 GB").
 */
data class StorageSnapshot(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usagePercent: Int,
    val totalGbFormatted: String,
    val usedGbFormatted: String,
    val freeGbFormatted: String
)

/**
 * Utility responsible for parsing Linux /proc/meminfo text, calculating memory and ZRAM swap
 * utilization metrics, and computing storage space metrics for the Wanbo T2 Max Smart Projector.
 */
object MemInfoReader {

    /** Default total physical RAM in MB for Wanbo T2 Max (MediaTek MT5862). */
    const val DEFAULT_TOTAL_RAM_MB: Long = 843L

    /** Default free RAM in MB used for fallback scenarios. */
    const val DEFAULT_FREE_RAM_MB: Long = 250L

    /** Default available RAM in MB used for fallback scenarios. */
    const val DEFAULT_AVAILABLE_RAM_MB: Long = 431L

    /** Default used RAM in MB (843 - 431) used for fallback scenarios. */
    const val DEFAULT_USED_RAM_MB: Long = 412L

    /** Default total ZRAM swap in MB (~1024 MB). */
    const val DEFAULT_TOTAL_SWAP_MB: Long = 1024L

    /** Default free ZRAM swap in MB. */
    const val DEFAULT_FREE_SWAP_MB: Long = 814L

    /** Default used ZRAM swap in MB (1024 - 814). */
    const val DEFAULT_USED_SWAP_MB: Long = 210L

    /** Default RAM usage percentage (412 / 843 = ~49%). */
    const val DEFAULT_RAM_PERCENT: Int = 49

    /** Default swap usage percentage (210 / 1024 = ~20%). */
    const val DEFAULT_SWAP_PERCENT: Int = 20

    /** Default eMMC total internal storage capacity in bytes (8 GB). */
    const val DEFAULT_STORAGE_TOTAL_BYTES: Long = 8L * 1024L * 1024L * 1024L

    /** Default eMMC free internal storage capacity in bytes (4.8 GB). */
    const val DEFAULT_STORAGE_FREE_BYTES: Long = (4.8 * 1024.0 * 1024.0 * 1024.0).toLong()

    /**
     * Parses the string contents of /proc/meminfo and computes memory snapshots.
     *
     * @param content Raw string content of /proc/meminfo.
     * @return Calculated MemInfoSnapshot or fallback snapshot if content is invalid or missing.
     */
    fun parse(content: String?): MemInfoSnapshot {
        if (content.isNullOrBlank()) {
            return getFallbackSnapshot()
        }

        val entries = mutableMapOf<String, Long>()
        for (line in content.lineSequence()) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val valuePart = line.substring(colonIndex + 1).trim()
                val kbValue = valuePart.split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                if (kbValue != null) {
                    entries[key] = kbValue
                }
            }
        }

        val memTotalKb = entries["MemTotal"]
        if (memTotalKb == null || memTotalKb <= 0L) {
            return getFallbackSnapshot()
        }

        val memFreeKb = entries["MemFree"] ?: 0L
        val buffersKb = entries["Buffers"] ?: 0L
        val cachedKb = entries["Cached"] ?: 0L
        val memAvailableKb = entries["MemAvailable"] ?: (memFreeKb + buffersKb + cachedKb)

        val swapTotalKb = entries["SwapTotal"] ?: 0L
        val swapFreeKb = entries["SwapFree"] ?: 0L

        val totalRamMb = memTotalKb / 1024L
        val freeRamMb = memFreeKb / 1024L
        val availableRamMb = (memAvailableKb / 1024L).coerceIn(0L, totalRamMb)
        val usedRamMb = (totalRamMb - availableRamMb).coerceAtLeast(0L)

        val totalSwapMb = swapTotalKb / 1024L
        val freeSwapMb = (swapFreeKb / 1024L).coerceIn(0L, totalSwapMb)
        val usedSwapMb = (totalSwapMb - freeSwapMb).coerceAtLeast(0L)

        val ramUsagePercent = if (totalRamMb > 0L) {
            ((usedRamMb.toDouble() / totalRamMb.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val swapUsagePercent = if (totalSwapMb > 0L) {
            ((usedSwapMb.toDouble() / totalSwapMb.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        return MemInfoSnapshot(
            totalRamMb = totalRamMb,
            freeRamMb = freeRamMb,
            availableRamMb = availableRamMb,
            usedRamMb = usedRamMb,
            totalSwapMb = totalSwapMb,
            freeSwapMb = freeSwapMb,
            usedSwapMb = usedSwapMb,
            ramUsagePercent = ramUsagePercent,
            swapUsagePercent = swapUsagePercent
        )
    }

    /**
     * Reads /proc/meminfo from disk and returns the active memory snapshot.
     *
     * @param memInfoFile Target file path to read from, defaulting to /proc/meminfo.
     * @return Active MemInfoSnapshot or default fallback if reading fails.
     */
    fun readCurrent(memInfoFile: File = File("/proc/meminfo")): MemInfoSnapshot {
        return try {
            if (memInfoFile.exists() && memInfoFile.canRead()) {
                val content = memInfoFile.readText()
                parse(content)
            } else {
                getFallbackSnapshot()
            }
        } catch (_: Throwable) {
            getFallbackSnapshot()
        }
    }

    /**
     * Constructs a fallback MemInfoSnapshot populated with default Wanbo T2 Max hardware parameters.
     *
     * @return Default MemInfoSnapshot instance.
     */
    fun getFallbackSnapshot(): MemInfoSnapshot {
        return MemInfoSnapshot(
            totalRamMb = DEFAULT_TOTAL_RAM_MB,
            freeRamMb = DEFAULT_FREE_RAM_MB,
            availableRamMb = DEFAULT_AVAILABLE_RAM_MB,
            usedRamMb = DEFAULT_USED_RAM_MB,
            totalSwapMb = DEFAULT_TOTAL_SWAP_MB,
            freeSwapMb = DEFAULT_FREE_SWAP_MB,
            usedSwapMb = DEFAULT_USED_SWAP_MB,
            ramUsagePercent = DEFAULT_RAM_PERCENT,
            swapUsagePercent = DEFAULT_SWAP_PERCENT
        )
    }

    /**
     * Converts a raw byte count into a human-readable storage string (GB, MB, or KB).
     *
     * @param bytes Number of bytes to format.
     * @return Formatted human-readable string.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.2f GB", gb)
        }
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        if (mb >= 1.0) {
            return String.format(Locale.US, "%.1f MB", mb)
        }
        val kb = bytes.toDouble() / 1024.0
        return String.format(Locale.US, "%.0f KB", kb)
    }

    /**
     * Converts raw bytes into gigabytes as a double-precision scalar.
     *
     * @param bytes Number of bytes to convert.
     * @return Converted gigabyte value.
     */
    fun bytesToGigabytes(bytes: Long): Double {
        if (bytes <= 0L) return 0.0
        return bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    }

    /**
     * Converts raw bytes into megabytes as a rounded whole number.
     *
     * @param bytes Number of bytes to convert.
     * @return Converted megabyte value.
     */
    fun bytesToMegabytes(bytes: Long): Long {
        if (bytes <= 0L) return 0L
        return bytes / (1024L * 1024L)
    }

    /**
     * Computes the percentage of storage capacity occupied.
     *
     * @param usedBytes Occupied storage space in bytes.
     * @param totalBytes Total storage partition capacity in bytes.
     * @return Integer usage percentage between 0 and 100.
     */
    fun calculateUsagePercentage(usedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L || usedBytes <= 0L) return 0
        return ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    }

    /**
     * Computes a StorageSnapshot based on total and available storage bytes.
     *
     * @param totalBytes Total partition capacity in bytes.
     * @param freeBytes Unused partition space in bytes.
     * @return Constructed StorageSnapshot instance.
     */
    fun computeStorageSnapshot(totalBytes: Long, freeBytes: Long): StorageSnapshot {
        val safeTotal = totalBytes.coerceAtLeast(0L)
        val safeFree = freeBytes.coerceIn(0L, safeTotal)
        val usedBytes = (safeTotal - safeFree).coerceAtLeast(0L)
        val percent = calculateUsagePercentage(usedBytes, safeTotal)

        return StorageSnapshot(
            totalBytes = safeTotal,
            freeBytes = safeFree,
            usedBytes = usedBytes,
            usagePercent = percent,
            totalGbFormatted = formatBytes(safeTotal),
            usedGbFormatted = formatBytes(usedBytes),
            freeGbFormatted = formatBytes(safeFree)
        )
    }

    /**
     * Reads eMMC storage partition statistics via Android StatFs.
     *
     * @param path Target filesystem partition path, defaulting to /data.
     * @return Calculated StorageSnapshot or default fallback if query fails.
     */
    fun readStorageStats(path: String = "/data"): StorageSnapshot {
        return try {
            val stat = android.os.StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            computeStorageSnapshot(totalBytes, freeBytes)
        } catch (_: Throwable) {
            getFallbackStorageSnapshot()
        }
    }

    /**
     * Constructs a fallback StorageSnapshot representing default internal eMMC storage specs.
     *
     * @return Default StorageSnapshot instance.
     */
    fun getFallbackStorageSnapshot(): StorageSnapshot {
        return computeStorageSnapshot(DEFAULT_STORAGE_TOTAL_BYTES, DEFAULT_STORAGE_FREE_BYTES)
    }
}
