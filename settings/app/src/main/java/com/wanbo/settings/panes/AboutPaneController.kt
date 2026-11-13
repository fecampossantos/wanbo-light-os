package com.wanbo.settings.panes

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.wanbo.settings.R
import com.wanbo.settings.util.MemInfoReader

/**
 * Controller managing the "Sobre o Projetor" settings pane.
 * Renders hardware specifications, internal eMMC storage usage, and a live
 * real-time memory monitor dashboard tracking physical RAM and ZRAM swap compression.
 *
 * @param activity Host AppCompatActivity context.
 * @param container Parent container into which the pane layout is attached.
 */
class AboutPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isRefreshingActive = false

    // UI Widgets: Hardware Specs
    private lateinit var cardHardwareSpecs: View
    private lateinit var textModelName: TextView
    private lateinit var textOsVersion: TextView
    private lateinit var textSocHardware: TextView
    private lateinit var textArchitecture: TextView
    private lateinit var textKernelBuild: TextView

    // UI Widgets: RAM Dashboard
    private lateinit var cardRamDashboard: View
    private lateinit var textRamUsedSummary: TextView
    private lateinit var textRamPercent: TextView
    private lateinit var progressRamUsage: ProgressBar
    private lateinit var textRamDetail: TextView
    private lateinit var textSwapUsedSummary: TextView
    private lateinit var textSwapPercent: TextView
    private lateinit var progressSwapUsage: ProgressBar
    private lateinit var textSwapDetail: TextView

    // UI Widgets: Storage Card
    private lateinit var cardStorageSpecs: View
    private lateinit var textStorageSummary: TextView
    private lateinit var textStorageFree: TextView
    private lateinit var textStoragePercent: TextView
    private lateinit var progressStorageUsage: ProgressBar

    // UI Widgets: Refresh Action
    private lateinit var btnRefreshRam: View

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isRefreshingActive) {
                refreshMemoryAndStorage()
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    companion object {
        /** Interval in milliseconds between consecutive live RAM readings. */
        const val REFRESH_INTERVAL_MS: Long = 3000L
    }

    /**
     * Inflates the About pane layout, binds child widgets, initializes hardware specifications,
     * performs initial memory diagnostic readings, and registers periodic updates.
     */
    fun attach() {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_about, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        setupListeners()
        populateHardwareSpecs()
        refreshMemoryAndStorage()

        isRefreshingActive = true
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    /**
     * Halts periodic memory updates, detaches layout, and releases widget references.
     */
    fun detach() {
        isRefreshingActive = false
        refreshHandler.removeCallbacks(refreshRunnable)
        rootView = null
    }

    /**
     * Binds UI widget elements from the inflated pane layout.
     *
     * @param root Inflated pane view root.
     */
    private fun bindViews(root: View) {
        cardHardwareSpecs = root.findViewById(R.id.card_hardware_specs)
        textModelName = root.findViewById(R.id.text_model_name)
        textOsVersion = root.findViewById(R.id.text_os_version)
        textSocHardware = root.findViewById(R.id.text_soc_hardware)
        textArchitecture = root.findViewById(R.id.text_architecture)
        textKernelBuild = root.findViewById(R.id.text_kernel_build)

        cardRamDashboard = root.findViewById(R.id.card_ram_dashboard)
        textRamUsedSummary = root.findViewById(R.id.text_ram_used_summary)
        textRamPercent = root.findViewById(R.id.text_ram_percent)
        progressRamUsage = root.findViewById(R.id.progress_ram_usage)
        textRamDetail = root.findViewById(R.id.text_ram_detail)
        textSwapUsedSummary = root.findViewById(R.id.text_swap_used_summary)
        textSwapPercent = root.findViewById(R.id.text_swap_percent)
        progressSwapUsage = root.findViewById(R.id.progress_swap_usage)
        textSwapDetail = root.findViewById(R.id.text_swap_detail)

        cardStorageSpecs = root.findViewById(R.id.card_storage_specs)
        textStorageSummary = root.findViewById(R.id.text_storage_summary)
        textStorageFree = root.findViewById(R.id.text_storage_free)
        textStoragePercent = root.findViewById(R.id.text_storage_percent)
        progressStorageUsage = root.findViewById(R.id.progress_storage_usage)

        btnRefreshRam = root.findViewById(R.id.btn_refresh_ram)
    }

    /**
     * Applies tvOS scale elevation animations across interactive cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardRamDashboard)
        applyFocusAnimation(cardStorageSpecs)
        applyFocusAnimation(cardHardwareSpecs)
        applyFocusAnimation(btnRefreshRam)
    }

    /**
     * Configures click listeners for manual refresh triggers.
     */
    private fun setupListeners() {
        btnRefreshRam.setOnClickListener {
            refreshMemoryAndStorage()
        }
    }

    /**
     * Populates static hardware and firmware specifications from Android system properties.
     */
    private fun populateHardwareSpecs() {
        val model = Build.MODEL.takeIf { !it.isNullOrBlank() } ?: "Wanbo T2 Max Smart Projector"
        textModelName.text = model

        val osDisplay = "WanboLightOS v1.0 (Android 9 Pie, API 28)"
        textOsVersion.text = osDisplay

        val soc = Build.HARDWARE.takeIf { !it.isNullOrBlank() } ?: "MediaTek MT5862 (4x ARM Cortex-A53)"
        textSocHardware.text = soc

        val arch = Build.CPU_ABI.takeIf { !it.isNullOrBlank() } ?: "armeabi-v7a (32-bit ARM)"
        textArchitecture.text = arch

        val kernelVersion = System.getProperty("os.version") ?: "4.9.118+"
        val buildId = Build.DISPLAY.takeIf { !it.isNullOrBlank() } ?: "WanboLightOS-20220616"
        textKernelBuild.text = "Linux $kernelVersion / $buildId"
    }

    /**
     * Queries current physical RAM, ZRAM swap, and eMMC storage metrics to update UI meters.
     */
    fun refreshMemoryAndStorage() {
        // Query live memory metrics
        val mem = MemInfoReader.readCurrent()
        textRamUsedSummary.text = "${mem.usedRamMb} MB / ${mem.totalRamMb} MB Usados"
        textRamPercent.text = "${mem.ramUsagePercent}%"
        progressRamUsage.progress = mem.ramUsagePercent
        textRamDetail.text = "Disponivel: ${mem.availableRamMb} MB | Livre: ${mem.freeRamMb} MB"

        textSwapUsedSummary.text = "${mem.usedSwapMb} MB / ${mem.totalSwapMb} MB Usados"
        textSwapPercent.text = "${mem.swapUsagePercent}%"
        progressSwapUsage.progress = mem.swapUsagePercent
        textSwapDetail.text = "Swap Livre: ${mem.freeSwapMb} MB | Compressao Ativa LZ4"

        // Query storage metrics
        val storage = MemInfoReader.readStorageStats()
        textStorageSummary.text = "${storage.usedGbFormatted} / ${storage.totalGbFormatted} Usados"
        textStorageFree.text = "Livre: ${storage.freeGbFormatted}"
        textStoragePercent.text = "${storage.usagePercent}%"
        progressStorageUsage.progress = storage.usagePercent
    }

    /**
     * Applies tvOS focus scale transition to target card view.
     *
     * @param view Focusable card view.
     */
    private fun applyFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start()
                v.elevation = 4f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                v.elevation = 0f
            }
        }
    }
}
