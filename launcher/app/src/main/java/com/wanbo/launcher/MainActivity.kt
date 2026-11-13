package com.wanbo.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanbo.launcher.data.AppsRepository
import com.wanbo.launcher.hardware.HardwareController
import com.wanbo.launcher.model.AppItem
import com.wanbo.launcher.ui.AppCardAdapter

/**
 * Main activity for the WanboLightOS custom projector launcher.
 * Implements Apple TV / tvOS inspired floating dock UI, 1-click hardware shortcuts
 * (HDMI, Keystone, Settings), and high-performance Leanback application launching.
 */
class MainActivity : Activity() {

    /** Repository instance used to query installed applications. */
    var appsRepository: AppsRepository = AppsRepository()

    /** Adapter instance managing floating application cards in the favorites dock. */
    var appAdapter: AppCardAdapter = AppCardAdapter()

    /** Background executor service for off-loading package queries and icon decoding. */
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Main thread handler for posting UI updates. */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Initializes the activity, sets up the layout, hardware shortcuts, and favorites dock.
     *
     * @param savedInstanceState Saved state bundle if recreating activity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appsRepository = AppsRepository(this)
        setupHardwareShortcuts()
        setupRecyclerView()
        loadApps()
    }

    /**
     * Refreshes installed applications when returning to the launcher home screen.
     */
    override fun onResume() {
        super.onResume()
        loadApps()
    }

    /**
     * Cleans up background thread executor when activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }

    /**
     * Binds Top Bar hardware quick action buttons (HDMI, Keystone, Settings)
     * to [HardwareController] dispatchers.
     */
    fun setupHardwareShortcuts() {
        findViewById<View>(R.id.btnHdmi)?.setOnClickListener {
            HardwareController.launchHdmi(this)
        }

        findViewById<View>(R.id.btnKeystone)?.setOnClickListener {
            HardwareController.launchKeystone(this)
        }

        findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            HardwareController.launchSettings(this)
        }
    }

    /**
     * Sets up the horizontal RecyclerView dock with a horizontal LinearLayoutManager
     * and attaches [AppCardAdapter] with launch handling.
     */
    fun setupRecyclerView() {
        val rvFavorites = findViewById<RecyclerView>(R.id.rvFavorites)
        rvFavorites?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appAdapter = AppCardAdapter(emptyList()) { selectedApp ->
            launchApp(selectedApp)
        }
        rvFavorites?.adapter = appAdapter
    }

    /**
     * Loads installed launchable applications from [AppsRepository] asynchronously
     * on a background worker thread and posts the resolved list back to the UI thread.
     */
    fun loadApps() {
        backgroundExecutor.execute {
            val apps = appsRepository.getLaunchableApps()
            mainHandler.post {
                appAdapter.submitList(apps)
            }
        }
    }

    /**
     * Launches the target application on the projector.
     * Queries Leanback launcher intent first, falling back to standard launcher intent.
     * Applies [Intent.FLAG_ACTIVITY_NEW_TASK] and catches launch exceptions defensively.
     *
     * @param app The [AppItem] application to launch.
     * @return True if launch intent was found and dispatched; false otherwise.
     */
    fun launchApp(app: AppItem): Boolean {
        val pm = packageManager ?: return false
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(launchIntent)
                true
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            Toast.makeText(this, "Application not found: ${app.label}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
