package com.wanbo.settings.panes

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wanbo.settings.R
import com.wanbo.settings.util.MemInfoReader
import java.io.File
import java.util.Locale

/**
 * Data model representing an installed application on the projector for management purposes.
 *
 * @property packageName Unique Android application package name.
 * @property label User-facing application display name.
 * @property icon Cached application launcher drawable icon.
 * @property sizeBytes Storage footprint of the APK file in bytes.
 * @property isSystemApp Flag indicating whether the application is bundled in the system partition.
 * @property formattedSize Preformatted human-readable storage string (e.g. "24.5 MB").
 */
data class ManagedAppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val sizeBytes: Long,
    val isSystemApp: Boolean,
    val formattedSize: String = MemInfoReader.formatBytes(sizeBytes)
)

/**
 * Controller managing the "Aplicativos" settings pane.
 * Renders internal eMMC partition utilization and provides management actions
 * (force stop, cache management, uninstallation) for non-HAL applications.
 *
 * @param activity Host AppCompatActivity context.
 * @param container Parent container into which the pane layout is attached.
 */
class AppsPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null

    // UI Widgets: Storage Overview
    private lateinit var cardStorageOverview: View
    private lateinit var textAppsStorageSummary: TextView
    private lateinit var textAppsStorageFree: TextView
    private lateinit var textAppsStoragePercent: TextView
    private lateinit var progressAppsStorage: ProgressBar

    // UI Widgets: App List
    private lateinit var appsListContainer: LinearLayout
    private lateinit var textAppsEmpty: TextView

    companion object {
        /**
         * Protected vendor HAL identifiers that must NEVER be killed, corrupted, or uninstalled.
         */
        val PROTECTED_HAL_KEYWORDS: Set<String> = setOf(
            "trapezoid",
            "trapezoidserver",
            "tvinput",
            "dtv_svc",
            "autopair",
            "loadingequalizer",
            "kxtj2server",
            "hotkey.dispatcher",
            "mstar"
        )

        /**
         * Determines whether a package name belongs to a critical vendor HAL component.
         *
         * @param packageName Package identifier to evaluate.
         * @return True if the package corresponds to a protected hardware service; false otherwise.
         */
        fun isProtectedHal(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return true
            val lower = packageName.lowercase(Locale.ROOT)
            return PROTECTED_HAL_KEYWORDS.any { lower.contains(it) }
        }
    }

    /**
     * Inflates the Apps pane layout, binds views, queries storage statistics,
     * and populates the installed applications list.
     */
    fun attach() {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_apps, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        refreshStorageOverview()
        populateInstalledApps()
    }

    /**
     * Cleans up views and detaches the controller.
     */
    fun detach() {
        rootView = null
    }

    /**
     * Binds UI widget elements from the inflated pane layout.
     *
     * @param root Inflated pane view root.
     */
    private fun bindViews(root: View) {
        cardStorageOverview = root.findViewById(R.id.card_storage_overview)
        textAppsStorageSummary = root.findViewById(R.id.text_apps_storage_summary)
        textAppsStorageFree = root.findViewById(R.id.text_apps_storage_free)
        textAppsStoragePercent = root.findViewById(R.id.text_apps_storage_percent)
        progressAppsStorage = root.findViewById(R.id.progress_apps_storage)

        appsListContainer = root.findViewById(R.id.apps_list_container)
        textAppsEmpty = root.findViewById(R.id.text_apps_empty)
    }

    /**
     * Applies tvOS scale elevation animations across interactive cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardStorageOverview)
    }

    /**
     * Queries internal eMMC storage partition and updates the summary card.
     */
    fun refreshStorageOverview() {
        val storage = MemInfoReader.readStorageStats()
        textAppsStorageSummary.text = "${storage.usedGbFormatted} / ${storage.totalGbFormatted} Usados"
        textAppsStorageFree.text = "Espaco livre: ${storage.freeGbFormatted}"
        textAppsStoragePercent.text = "${storage.usagePercent}%"
        progressAppsStorage.progress = storage.usagePercent
    }

    /**
     * Discovers installed applications on the system, filters out protected hardware HALs,
     * sorts them with user apps first, and inflates them into the list container.
     */
    fun populateInstalledApps() {
        appsListContainer.removeAllViews()
        val apps = queryEligibleApps()

        if (apps.isEmpty()) {
            textAppsEmpty.visibility = View.VISIBLE
            return
        }

        textAppsEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(activity)

        for (app in apps) {
            val itemView = inflater.inflate(R.layout.item_app_management, appsListContainer, false)
            val iconView = itemView.findViewById<ImageView>(R.id.app_icon)
            val nameView = itemView.findViewById<TextView>(R.id.app_name)
            val packageView = itemView.findViewById<TextView>(R.id.app_package_name)
            val sizeView = itemView.findViewById<TextView>(R.id.app_size)
            val badgeView = itemView.findViewById<TextView>(R.id.app_badge)

            if (app.icon != null) {
                iconView.setImageDrawable(app.icon)
            } else {
                iconView.setImageResource(R.drawable.ic_settings)
            }

            nameView.text = app.label
            packageView.text = app.packageName
            sizeView.text = app.formattedSize

            if (app.isSystemApp) {
                badgeView.text = "Sistema"
                badgeView.setBackgroundResource(R.drawable.bg_badge_inactive)
                badgeView.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
            } else {
                badgeView.text = "Usuario"
                badgeView.setBackgroundResource(R.drawable.bg_badge_active)
                badgeView.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
            }

            applyFocusAnimation(itemView)

            itemView.setOnClickListener {
                showAppManagementDialog(app)
            }

            appsListContainer.addView(itemView)
        }
    }

    /**
     * Discovers all installed applications excluding critical vendor HALs and self.
     *
     * @return List of eligible ManagedAppItem instances sorted by user priority and label.
     */
    fun queryEligibleApps(): List<ManagedAppItem> {
        val pm = activity.packageManager
        val installed = try {
            pm.getInstalledApplications(0)
        } catch (_: Exception) {
            emptyList<ApplicationInfo>()
        }

        val resultList = mutableListOf<ManagedAppItem>()
        val ownPackage = activity.packageName

        for (appInfo in installed) {
            val pkg = appInfo.packageName ?: continue
            if (pkg.isBlank() || isProtectedHal(pkg) || pkg == ownPackage) {
                continue
            }

            val label = try {
                appInfo.loadLabel(pm).toString().trim().takeIf { it.isNotEmpty() } ?: pkg
            } catch (_: Exception) {
                pkg
            }

            val icon = try {
                appInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }

            val sizeBytes = try {
                val apkFile = File(appInfo.sourceDir)
                if (apkFile.exists()) apkFile.length() else 0L
            } catch (_: Exception) {
                0L
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            resultList.add(
                ManagedAppItem(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    sizeBytes = sizeBytes,
                    isSystemApp = isSystem
                )
            )
        }

        // Sort: User sideloaded apps first, followed alphabetically by label
        return resultList.sortedWith(
            compareBy<ManagedAppItem> { if (it.isSystemApp) 1 else 0 }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
    }

    /**
     * Displays a tvOS management dialog offering actions to force stop, manage cache, or uninstall.
     *
     * @param app Target ManagedAppItem to configure.
     */
    private fun showAppManagementDialog(app: ManagedAppItem) {
        val options = arrayOf(
            "Forcar Parada",
            "Limpar Cache / Gerenciar",
            if (app.isSystemApp) "Desinstalar (Bloqueado para Sistema)" else "Desinstalar Aplicativo"
        )

        AlertDialog.Builder(activity)
            .setTitle(app.label)
            .setMessage("Pacote: ${app.packageName}\nTamanho: ${app.formattedSize}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> executeForceStop(app)
                    1 -> openAppDetails(app)
                    2 -> executeUninstall(app)
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    /**
     * Terminates background execution processes for target package.
     *
     * @param app Target application item.
     */
    private fun executeForceStop(app: ManagedAppItem) {
        if (isProtectedHal(app.packageName)) {
            Toast.makeText(activity, "Componentes criticos de hardware nao podem ser finalizados", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(app.packageName)
            Runtime.getRuntime().exec(arrayOf("am", "force-stop", app.packageName))
            Toast.makeText(activity, "Aplicativo finalizado: ${app.label}", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Erro ao finalizar aplicativo", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Navigates to system application detail settings to inspect permissions and storage cache.
     *
     * @param app Target application item.
     */
    private fun openAppDetails(app: ManagedAppItem) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Cache de ${app.label} limpo", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Triggers package uninstallation flow for user-installed applications.
     *
     * @param app Target application item.
     */
    private fun executeUninstall(app: ManagedAppItem) {
        if (app.isSystemApp) {
            Toast.makeText(activity, "Aplicativos do sistema nao podem ser desinstalados", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            activity.startActivity(uninstallIntent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Erro ao iniciar desinstalacao", Toast.LENGTH_SHORT).show()
        }
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
