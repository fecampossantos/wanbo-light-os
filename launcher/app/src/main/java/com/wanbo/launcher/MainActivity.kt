package com.wanbo.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanbo.launcher.data.AppsRepository
import com.wanbo.launcher.data.FavoriteAppsManager
import com.wanbo.launcher.hardware.HardwareController
import com.wanbo.launcher.model.AppItem
import com.wanbo.launcher.ui.AmbientBackdropController
import com.wanbo.launcher.ui.AppCardAdapter
import com.wanbo.launcher.ui.FavoritesAdapter

/**
 * Main activity for the WanboLightOS custom projector launcher.
 * Implements Apple TV / tvOS inspired dual-row floating dock UI (Favorites and All Apps),
 * dynamic ambient glow backdrop, 1-click hardware shortcuts (HDMI, Keystone/Projection, Settings),
 * and high-performance Leanback application launching.
 * Strictly enforces Zero Emojis compliance across all code, UI, and log output.
 */
class MainActivity : Activity() {

    /** Repository instance used to query installed applications. */
    var appsRepository: AppsRepository = AppsRepository()

    /** Manager instance governing pinned user favorites persistence. */
    var favoriteAppsManager: FavoriteAppsManager = FavoriteAppsManager()

    /** Adapter instance managing pinned application cards and + Adicionar card in Favorites row. */
    var favoritesAdapter: FavoritesAdapter = FavoritesAdapter()

    /** Adapter instance managing all installed applications in the All Apps row. */
    var appAdapter: AppCardAdapter = AppCardAdapter()

    /** Controller managing dynamic atmospheric gradient glow on launcher backdrop. */
    var ambientBackdropController: AmbientBackdropController? = null

    /** Background executor service for off-loading package queries and icon decoding. */
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Main thread handler for posting UI updates. */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Initializes the activity, sets up the layout, ambient backdrop, hardware shortcuts,
     * and dual-row RecyclerView docks.
     *
     * @param savedInstanceState Saved state bundle if recreating activity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        favoriteAppsManager = FavoriteAppsManager.fromContext(this)
        appsRepository = AppsRepository(this, favoriteAppsManager = favoriteAppsManager)

        setupAmbientBackdrop()
        setupHardwareShortcuts()
        setupRecyclerViews()
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
     * Initializes the [AmbientBackdropController] bound to ambient_backdrop_view.
     */
    fun setupAmbientBackdrop() {
        val backdropView = findViewById<View>(R.id.ambient_backdrop_view)
        ambientBackdropController = AmbientBackdropController(backdropView)
    }

    /**
     * Binds Top Bar hardware quick action buttons (HDMI, Keystone, Settings)
     * to [HardwareController] dispatchers.
     */
    fun setupHardwareShortcuts() {
        // HDMI input quick button (primary: btn_hdmi, fallback: btnHdmi)
        val btnHdmi = findViewById<View>(R.id.btn_hdmi) ?: findViewById<View>(R.id.btnHdmi)
        btnHdmi?.setOnClickListener {
            HardwareController.switchHdmiInput(this)
            HardwareController.launchHdmi(this)
        }

        // Keystone / Projection quick button (primary: btn_projection, fallback: btnKeystone)
        val btnKeystone = findViewById<View>(R.id.btn_projection) ?: findViewById<View>(R.id.btnKeystone)
        btnKeystone?.setOnClickListener {
            HardwareController.launchKeystoneCalibration(this)
            HardwareController.launchKeystone(this)
        }

        // System Settings quick button (primary: btn_settings, fallback: btnSettings)
        val btnSettings = findViewById<View>(R.id.btn_settings) ?: findViewById<View>(R.id.btnSettings)
        btnSettings?.setOnClickListener {
            HardwareController.launchSettings(this)
        }
    }

    /**
     * Configures the dual-row RecyclerView docks:
     * 1. Section 1: Favorites row (FavoritesAdapter with squircle cards and + Adicionar).
     * 2. Section 2: All Apps row (AppCardAdapter with all installed apps).
     */
    fun setupRecyclerViews() {
        // Section 1: Favorites row
        val rvFavorites = findViewById<RecyclerView>(R.id.favorites_recycler_view)
            ?: findViewById<RecyclerView>(R.id.rvFavorites)
        rvFavorites?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        favoritesAdapter = FavoritesAdapter(
            initialFavorites = emptyList(),
            onAppClick = { selectedApp -> launchApp(selectedApp) },
            onAppLongClick = { selectedApp -> showUnpinDialog(selectedApp); true },
            onAddClick = { showAddFavoriteDialog() },
            onFocusChange = { focusedApp, hasFocus ->
                if (hasFocus) {
                    ambientBackdropController?.updateAmbient(focusedApp)
                }
            }
        )
        rvFavorites?.adapter = favoritesAdapter

        // Section 2: All Apps row
        val rvAllApps = findViewById<RecyclerView>(R.id.all_apps_recycler_view)
        rvAllApps?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appAdapter = AppCardAdapter(
            initialApps = emptyList(),
            onAppClick = { selectedApp -> launchApp(selectedApp) },
            onFocusChanged = { focusedApp, hasFocus ->
                if (hasFocus) {
                    ambientBackdropController?.updateAmbient(focusedApp)
                }
            }
        )
        rvAllApps?.adapter = appAdapter
    }

    /**
     * Backward compatibility helper for legacy test suites invoking setupRecyclerView.
     */
    fun setupRecyclerView() {
        setupRecyclerViews()
    }

    /**
     * Loads installed launchable applications from [AppsRepository] asynchronously
     * on a background worker thread and populates both Favorites and All Apps rows.
     */
    fun loadApps() {
        backgroundExecutor.execute {
            val allApps = appsRepository.filterExcluded(appsRepository.getLaunchableApps())
            val favPackageNames = favoriteAppsManager.getFavoritePackageNames().toSet()
            val favoriteApps = allApps.filter { favPackageNames.contains(it.packageName) }

            mainHandler.post {
                favoritesAdapter.submitList(favoriteApps)
                appAdapter.submitList(allApps)
            }
        }
    }

    /**
     * Displays a dialog allowing the user to select and pin an unpinned application
     * to the Favorites row.
     */
    fun showAddFavoriteDialog() {
        val allApps = appsRepository.filterExcluded(appsRepository.getLaunchableApps())
        val favPackageNames = favoriteAppsManager.getFavoritePackageNames().toSet()
        val unpinnedApps = allApps.filter { !favPackageNames.contains(it.packageName) }

        if (unpinnedApps.isEmpty()) {
            Toast.makeText(this, "Todos os aplicativos ja estao nos favoritos", Toast.LENGTH_SHORT).show()
            return
        }

        val appLabels = unpinnedApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Adicionar aos Favoritos")
            .setItems(appLabels) { _, which ->
                val selected = unpinnedApps[which]
                favoriteAppsManager.addFavorite(selected.packageName)
                loadApps()
                Toast.makeText(this, "${selected.label} adicionado aos favoritos", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Displays a confirmation dialog to unpin a favorite application.
     *
     * @param app The favorite application to unpin.
     */
    fun showUnpinDialog(app: AppItem) {
        AlertDialog.Builder(this)
            .setTitle("Remover dos Favoritos")
            .setMessage("Deseja remover ${app.label} dos favoritos?")
            .setPositiveButton("Remover") { _, _ ->
                favoriteAppsManager.removeFavorite(app.packageName)
                loadApps()
                Toast.makeText(this, "${app.label} removido dos favoritos", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
