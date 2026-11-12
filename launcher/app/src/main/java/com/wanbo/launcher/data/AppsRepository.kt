package com.wanbo.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.wanbo.launcher.model.AppItem
import java.util.Locale

/**
 * Repository responsible for discovering installed applications on the projector,
 * querying both Leanback TV launchers and standard Android launchers for sideloaded apps.
 * Filters out the launcher itself and hidden services, ensuring deduplication and sorting.
 */
class AppsRepository(
    private val context: Context? = null,
    private val packageManager: PackageManager? = context?.packageManager
) {

    /**
     * Retrieves all launchable applications, sorting favorites first by default.
     * Primary entry point used by the launcher UI and ViewModels.
     *
     * @return List of launchable AppItem instances.
     */
    fun getLaunchableApps(): List<AppItem> {
        return getInstalledApps()
    }

    /**
     * Queries PackageManager for installed Leanback TV applications and sideloaded
     * launcher applications, excluding the launcher's own package and deduplicating.
     *
     * @param favoritesFirst Whether pinned favorite apps should appear at the top.
     * @return List of launchable AppItem instances.
     */
    fun getInstalledApps(favoritesFirst: Boolean = true): List<AppItem> {
        val pm = packageManager ?: context?.packageManager ?: return emptyList()
        val ownPackage = context?.packageName ?: ""
        return getInstalledApps(pm, ownPackage, favoritesFirst)
    }

    /**
     * Queries the provided PackageManager for installed TV applications (Leanback launcher category)
     * and sideloaded applications (standard launcher category), filtering out the specified own package name.
     *
     * @param pm The Android PackageManager instance to query.
     * @param ownPackageName The launcher package name to filter out.
     * @param favoritesFirst Whether pinned favorite apps should appear at the top.
     * @return List of launchable AppItem instances.
     */
    fun getInstalledApps(
        pm: PackageManager,
        ownPackageName: String = context?.packageName ?: "",
        favoritesFirst: Boolean = true
    ): List<AppItem> {
        // 1. Query native Leanback TV apps (Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackResolves = pm.queryIntentActivities(leanbackIntent, 0) ?: emptyList()

        // 2. Query standard launcher apps for sideloaded applications (Intent.CATEGORY_LAUNCHER)
        val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val standardResolves = pm.queryIntentActivities(standardIntent, 0) ?: emptyList()

        val appList = mutableListOf<AppItem>()
        val seenPackages = mutableSetOf<String>()

        fun processResolveInfo(info: ResolveInfo) {
            val activityInfo = info.activityInfo ?: return
            val pkg = activityInfo.packageName ?: return

            // Exclude empty packages, launcher self package, and already processed apps
            if (pkg.isBlank() || pkg == ownPackageName || seenPackages.contains(pkg)) {
                return
            }

            seenPackages.add(pkg)

            val label = info.loadLabel(pm)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: activityInfo.loadLabel(pm)?.toString()?.trim()
                ?: pkg

            val icon = info.loadIcon(pm) ?: activityInfo.loadIcon(pm)
            val banner = activityInfo.loadBanner(pm) ?: info.loadBanner(pm)

            appList.add(
                AppItem(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    banner = banner,
                    isFavorite = false
                )
            )
        }

        // Process Leanback apps first to capture Leanback banners
        leanbackResolves.forEach { processResolveInfo(it) }
        // Fallback to standard apps for sideloaded applications
        standardResolves.forEach { processResolveInfo(it) }

        return sortApps(appList, favoritesFirst)
    }

    /**
     * Filters out the launcher package itself and invalid blank packages from an app list.
     *
     * @param apps The list of AppItem instances.
     * @param selfPackageName The launcher's own package name to exclude.
     * @return Filtered list of AppItem instances.
     */
    fun filterSelf(apps: List<AppItem>, selfPackageName: String): List<AppItem> {
        return apps.filter { it.packageName.isNotBlank() && it.packageName != selfPackageName }
    }

    /**
     * Deduplicates a list of AppItem instances based on packageName, preserving insertion order.
     *
     * @param apps The list of AppItem instances.
     * @return Deduplicated list of AppItem instances.
     */
    fun deduplicate(apps: List<AppItem>): List<AppItem> {
        val seen = mutableSetOf<String>()
        return apps.filter { seen.add(it.packageName) }
    }

    /**
     * Sorts application items: favorites first (if requested), followed by case-insensitive
     * alphabetical order by label.
     *
     * @param apps The list of AppItem instances.
     * @param favoritesFirst Whether favorite apps appear first.
     * @return Sorted list of AppItem instances.
     */
    fun sortApps(apps: List<AppItem>, favoritesFirst: Boolean = true): List<AppItem> {
        return apps.sortedWith(
            compareBy<AppItem> { if (favoritesFirst && it.isFavorite) 0 else 1 }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
    }
}
