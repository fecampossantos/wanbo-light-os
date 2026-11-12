package com.wanbo.launcher.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed launchable application on the Wanbo TV projector.
 *
 * @property packageName Unique application package identifier (e.g. "org.jellyfin.androidtv")
 * @property label User-facing display title (e.g. "Jellyfin")
 * @property icon Application icon drawable
 * @property banner Leanback 16:9 banner drawable (if available)
 * @property isFavorite Whether the user pinned this app to the Apple TV floating dock
 */
data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val banner: Drawable? = null,
    var isFavorite: Boolean = false
)
