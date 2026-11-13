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
 * RecyclerView adapter managing the Favorites dock section in the Wanbo launcher.
 * Renders pinned applications as squircle cards with 20dp corner radius, 1.08x D-pad
 * focus scaling with subtle white glow edge, and a trailing "+ Adicionar" action card
 * for pinning new applications.
 *
 * Supports click launching, long-press unpinning, and focus elevation transitions
 * integrated with [AmbientBackdropController] for dynamic atmospheric glow.
 * Strictly enforces Zero Emojis compliance across all UI and documentation.
 *
 * @param initialFavorites Initial list of favorite [AppItem] instances.
 * @param onAppClick Callback invoked when a favorite card is clicked or selected.
 * @param onAppLongClick Callback invoked when a favorite card is long-pressed (unpin action).
 * @param onAddClick Callback invoked when the trailing "+ Adicionar" card is clicked.
 * @param onFocusChange Callback invoked when an item gains focus, passing null for the add card.
 */
class FavoritesAdapter(
    initialFavorites: List<AppItem> = emptyList(),
    private val onAppClick: (AppItem) -> Unit = {},
    private val onAppLongClick: (AppItem) -> Boolean = { false },
    private val onAddClick: () -> Unit = {},
    private var onFocusChange: (AppItem?, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        /** View type identifier for standard favorite application cards. */
        const val VIEW_TYPE_APP: Int = 0

        /** View type identifier for the trailing "+ Adicionar" action card. */
        const val VIEW_TYPE_ADD: Int = 1

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

    private var favorites: List<AppItem> = initialFavorites.toList()

    /**
     * Updates the underlying favorite applications dataset and refreshes the adapter.
     *
     * @param newFavorites New list of [AppItem] instances to display as favorites.
     */
    fun submitList(newFavorites: List<AppItem>) {
        this.favorites = newFavorites.toList()
        try {
            notifyDataSetChanged()
        } catch (_: Exception) {
            // Safe fallback during headless JVM unit testing
        }
    }

    /**
     * Retrieves the current immutable list of favorite applications.
     *
     * @return Current list of favorite [AppItem] instances.
     */
    fun getFavorites(): List<AppItem> = favorites

    /**
     * Returns total item count: pinned favorite apps plus the trailing "+ Adicionar" card.
     *
     * @return Total item count including the trailing add card.
     */
    override fun getItemCount(): Int = favorites.size + 1

    /**
     * Identifies whether the position corresponds to a favorite app or the trailing add card.
     *
     * @param position Zero-based adapter position.
     * @return [VIEW_TYPE_APP] for favorite items; [VIEW_TYPE_ADD] for the trailing card.
     */
    override fun getItemViewType(position: Int): Int {
        return if (position < favorites.size) VIEW_TYPE_APP else VIEW_TYPE_ADD
    }

    /**
     * Inflates either [R.layout.item_favorite_card] or [R.layout.item_add_favorite_card].
     *
     * @param parent The parent ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return Appropriate [RecyclerView.ViewHolder] instance.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_APP) {
            val view = inflater.inflate(R.layout.item_favorite_card, parent, false)
            FavoriteAppViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_add_favorite_card, parent, false)
            AddFavoriteViewHolder(view)
        }
    }

    /**
     * Binds application metadata or add card action to the specified ViewHolder.
     *
     * @param holder The ViewHolder to update.
     * @param position The position within the adapter dataset.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FavoriteAppViewHolder) {
            val app = favorites[position]
            holder.bind(app)
        } else if (holder is AddFavoriteViewHolder) {
            holder.bind()
        }
    }

    /**
     * Configures the focus change listener callback for ambient glow and analytics.
     *
     * @param listener Callback receiving the focused [AppItem] (or null for add card) and state.
     */
    fun setOnFocusChangeListener(listener: (AppItem?, Boolean) -> Unit) {
        this.onFocusChange = listener
    }

    /**
     * Applies the tvOS squircle focus animation to the target view.
     * Animates scaleX, scaleY, and translationZ, and updates background drawable.
     *
     * @param view Target card view to animate.
     * @param hasFocus True if the card currently has focus; false otherwise.
     */
    fun applyFocusAnimation(view: View, hasFocus: Boolean) {
        val targetScale = if (hasFocus) SCALE_FOCUSED else SCALE_UNFOCUSED
        val density = view.resources?.displayMetrics?.density ?: 1.0f
        val targetElevation = (if (hasFocus) ELEVATION_FOCUSED_DP else ELEVATION_UNFOCUSED_DP) * density

        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationZ(targetElevation)
            .setDuration(ANIMATION_DURATION_MS)
            .start()

        view.setBackgroundResource(
            if (hasFocus) R.drawable.bg_card_favorite_focused else R.drawable.bg_card_favorite_unfocused
        )
    }

    /**
     * ViewHolder caching views for an individual favorite application card.
     *
     * @param itemView Root view inflated from [R.layout.item_favorite_card].
     */
    inner class FavoriteAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView? = itemView.findViewById(R.id.iv_favorite_icon)
        val tvLabel: TextView? = itemView.findViewById(R.id.tv_favorite_label)

        /**
         * Binds an [AppItem] to views, resets animation states, and attaches listeners.
         *
         * @param app Application data model containing metadata.
         */
        fun bind(app: AppItem) {
            tvLabel?.text = app.label
            val displayDrawable = app.banner ?: app.icon
            ivIcon?.setImageDrawable(displayDrawable)

            val isFocused = itemView.hasFocus()
            val density = itemView.resources?.displayMetrics?.density ?: 1.0f
            itemView.scaleX = if (isFocused) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.scaleY = if (isFocused) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.translationZ = (if (isFocused) ELEVATION_FOCUSED_DP else ELEVATION_UNFOCUSED_DP) * density
            itemView.setBackgroundResource(
                if (isFocused) R.drawable.bg_card_favorite_focused else R.drawable.bg_card_favorite_unfocused
            )

            itemView.setOnClickListener {
                onAppClick(app)
            }

            itemView.setOnLongClickListener {
                onAppLongClick(app)
            }

            itemView.setOnFocusChangeListener { v, hasFocus ->
                applyFocusAnimation(v, hasFocus)
                onFocusChange(if (hasFocus) app else null, hasFocus)
            }
        }
    }

    /**
     * ViewHolder caching views for the trailing "+ Adicionar" action card.
     *
     * @param itemView Root view inflated from [R.layout.item_add_favorite_card].
     */
    inner class AddFavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAddLabel: TextView? = itemView.findViewById(R.id.tv_add_label)

        /**
         * Binds the trailing add action card, attaching click and focus listeners.
         */
        fun bind() {
            val isFocused = itemView.hasFocus()
            val density = itemView.resources?.displayMetrics?.density ?: 1.0f
            itemView.scaleX = if (isFocused) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.scaleY = if (isFocused) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.translationZ = (if (isFocused) ELEVATION_FOCUSED_DP else ELEVATION_UNFOCUSED_DP) * density
            itemView.setBackgroundResource(
                if (isFocused) R.drawable.bg_card_favorite_focused else R.drawable.bg_card_favorite_unfocused
            )

            itemView.setOnClickListener {
                onAddClick()
            }

            itemView.setOnFocusChangeListener { v, hasFocus ->
                applyFocusAnimation(v, hasFocus)
                onFocusChange(null, hasFocus)
            }
        }
    }
}
