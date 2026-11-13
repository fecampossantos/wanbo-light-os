package com.wanbo.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wanbo.launcher.R
import com.wanbo.launcher.model.AppItem

/**
 * RecyclerView adapter responsible for rendering application floating cards in an
 * Apple TV tvOS style dock. Supports smooth 1.08x focus elevation animation,
 * background state switching, and click dispatching.
 *
 * @param initialApps Initial list of [AppItem] instances to display.
 * @param onAppClick Callback invoked when an application card is clicked or selected.
 */
class AppCardAdapter(
    initialApps: List<AppItem> = emptyList(),
    private val onAppClick: (AppItem) -> Unit = {}
) : RecyclerView.Adapter<AppCardAdapter.AppCardViewHolder>() {

    companion object {
        /** Scale factor applied when the card gains D-pad focus (tvOS elevation effect). */
        const val SCALE_FOCUSED: Float = 1.08f

        /** Default scale factor when the card does not have focus. */
        const val SCALE_UNFOCUSED: Float = 1.0f

        /** Duration of the focus scale and elevation transition in milliseconds. */
        const val ANIMATION_DURATION_MS: Long = 150L

        /** Elevation (translationZ) in density-independent pixels when focused. */
        const val ELEVATION_FOCUSED_DP: Float = 8f

        /** Elevation (translationZ) in density-independent pixels when unfocused. */
        const val ELEVATION_UNFOCUSED_DP: Float = 0f
    }

    private var apps: List<AppItem> = initialApps.toList()

    /**
     * Updates the underlying application dataset and refreshes the adapter.
     *
     * @param newApps New list of [AppItem] instances to display.
     */
    fun submitList(newApps: List<AppItem>) {
        this.apps = newApps.toList()
        try {
            notifyDataSetChanged()
        } catch (_: Exception) {
            // Safe fallback during headless JVM unit testing when observer is unattached
        }
    }

    /**
     * Retrieves the current immutable list of applications bound to the adapter.
     *
     * @return Current list of [AppItem] instances.
     */
    fun getApps(): List<AppItem> = apps

    /**
     * Inflates the tvOS card item layout [R.layout.item_app_card] and constructs the ViewHolder.
     *
     * @param parent The parent ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A newly created [AppCardViewHolder].
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        return AppCardViewHolder(view)
    }

    /**
     * Binds an [AppItem] to the specified [AppCardViewHolder], setting up drawables,
     * labels, click listeners, and focus animation listeners.
     *
     * @param holder The [AppCardViewHolder] to update.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: AppCardViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app, onAppClick)
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     * @return The total item count.
     */
    override fun getItemCount(): Int = apps.size

    /**
     * Applies the tvOS floating card focus animation to the target view.
     * Animates scaleX, scaleY, and translationZ, and updates the background drawable.
     *
     * @param view The target card view to animate.
     * @param hasFocus True if the card currently has focus; false otherwise.
     */
    fun applyFocusAnimation(view: View, hasFocus: Boolean) {
        val targetScale = if (hasFocus) SCALE_FOCUSED else SCALE_UNFOCUSED
        val density = view.resources.displayMetrics.density
        val targetElevation = (if (hasFocus) ELEVATION_FOCUSED_DP else ELEVATION_UNFOCUSED_DP) * density

        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationZ(targetElevation)
            .setDuration(ANIMATION_DURATION_MS)
            .start()

        view.setBackgroundResource(
            if (hasFocus) R.drawable.bg_card_focused else R.drawable.bg_card_unfocused
        )
    }

    /**
     * ViewHolder caching views for an individual floating application card.
     *
     * @param itemView The root view inflated from [R.layout.item_app_card].
     */
    inner class AppCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvAppLabel: TextView = itemView.findViewById(R.id.tvAppLabel)

        /**
         * Binds data from [AppItem] to views, resets animation states, and attaches listeners.
         *
         * @param app The [AppItem] model containing application metadata.
         * @param clickListener Callback invoked when the card is clicked.
         */
        fun bind(app: AppItem, clickListener: (AppItem) -> Unit) {
            tvAppLabel.text = app.label
            val displayDrawable = app.banner ?: app.icon
            ivAppIcon.setImageDrawable(displayDrawable)

            // Synchronize view properties with current focus state to prevent recycling artifacts
            val isFocused = itemView.hasFocus()
            val density = itemView.resources.displayMetrics.density
            itemView.scaleX = if (isFocused) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.scaleY = if (isFocused) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.translationZ = (if (isFocused) ELEVATION_FOCUSED_DP else ELEVATION_UNFOCUSED_DP) * density
            itemView.setBackgroundResource(
                if (isFocused) R.drawable.bg_card_focused else R.drawable.bg_card_unfocused
            )

            itemView.setOnClickListener {
                clickListener(app)
            }

            itemView.setOnFocusChangeListener { v, hasFocus ->
                applyFocusAnimation(v, hasFocus)
            }
        }
    }
}
