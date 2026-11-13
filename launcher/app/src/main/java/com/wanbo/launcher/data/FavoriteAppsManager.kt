package com.wanbo.launcher.data

import android.content.Context

/**
 * Manages user favorite application package names on the projector.
 * Persists pinned packages to SharedPreferences and provides default favorites
 * when no user preferences have been stored yet.
 * Supports injected load/save lambdas for pure JVM unit testing without Robolectric.
 */
class FavoriteAppsManager(
    private val loadRaw: (() -> Set<String>?)? = null,
    private val saveRaw: ((Set<String>) -> Unit)? = null
) {

    private var inMemoryStorage: MutableSet<String>? = null

    /**
     * Secondary constructor for direct Context initialization.
     *
     * @param context Application or UI context used to access SharedPreferences.
     */
    constructor(context: Context) : this(
        loadRaw = {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getStringSet(KEY_FAVORITES, null)
        },
        saveRaw = { set ->
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
        }
    )

    /**
     * Retrieves the list of favorite application package names.
     * If no favorites have been saved yet, returns the default list.
     *
     * @return List of favorite package names.
     */
    fun getFavoritePackageNames(): List<String> {
        val raw = if (loadRaw != null) {
            loadRaw.invoke()
        } else {
            inMemoryStorage
        }
        return raw?.filter { it.isNotBlank() } ?: DEFAULT_FAVORITES
    }

    /**
     * Checks if a given package name is in the favorites list.
     *
     * @param packageName Application package identifier to check.
     * @return True if the package is pinned as a favorite; false otherwise.
     */
    fun isFavorite(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return getFavoritePackageNames().contains(packageName.trim())
    }

    /**
     * Adds an application package name to favorites.
     * Rejects blank strings or duplicates.
     *
     * @param packageName Application package identifier to add.
     * @return True if the package was successfully added; false if blank or duplicate.
     */
    fun addFavorite(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) {
            return false
        }

        val currentFavorites = getFavoritePackageNames()
        if (currentFavorites.contains(trimmed)) {
            return false
        }

        val updatedSet = LinkedHashSet(currentFavorites)
        updatedSet.add(trimmed)
        persistFavorites(updatedSet)
        return true
    }

    /**
     * Removes an application package name from favorites.
     * Rejects blank strings or non-existent favorites.
     *
     * @param packageName Application package identifier to remove.
     * @return True if the package was removed; false if blank or not in favorites.
     */
    fun removeFavorite(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) {
            return false
        }

        val currentFavorites = getFavoritePackageNames()
        if (!currentFavorites.contains(trimmed)) {
            return false
        }

        val updatedSet = LinkedHashSet(currentFavorites)
        updatedSet.remove(trimmed)
        persistFavorites(updatedSet)
        return true
    }

    /**
     * Persists the updated favorites set using saveRaw lambda or in-memory fallback.
     *
     * @param favorites The set of package names to persist.
     */
    private fun persistFavorites(favorites: Set<String>) {
        if (saveRaw != null) {
            saveRaw.invoke(favorites)
        } else {
            inMemoryStorage = LinkedHashSet(favorites)
        }
    }

    companion object {
        const val PREFS_NAME: String = "wanbo_launcher_favorites"
        const val KEY_FAVORITES: String = "favorite_packages"

        /**
         * Default list of pinned applications for Wanbo T2 Max projector:
         * YouTube TV, Netflix TV, Prime Video, Disney+, and Aptoide TV.
         */
        val DEFAULT_FAVORITES: List<String> = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "cm.aptoidetv.pt"
        )

        /**
         * Factory method to create a FavoriteAppsManager backed by SharedPreferences.
         *
         * @param context Application or UI context used to access SharedPreferences.
         * @return An initialized FavoriteAppsManager instance.
         */
        @JvmStatic
        fun fromContext(context: Context): FavoriteAppsManager {
            return FavoriteAppsManager(context)
        }
    }
}
