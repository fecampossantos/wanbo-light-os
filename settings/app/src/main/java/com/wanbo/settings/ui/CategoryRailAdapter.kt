package com.wanbo.settings.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wanbo.settings.R
import com.wanbo.settings.model.SettingsCategory

/**
 * Adapter responsible for rendering settings categories inside the vertical navigation rail.
 * Applies tvOS card aesthetics, scale transitions, and visual highlight states for D-pad navigation.
 *
 * @param items List of SettingsCategory items to display.
 * @param onCategoryFocused Callback triggered when a category item gains D-pad focus.
 * @param onCategorySelected Callback triggered when a category item is clicked or confirmed via D-pad.
 */
class CategoryRailAdapter(
    val items: List<SettingsCategory> = SettingsCategory.values().toList(),
    private val onCategoryFocused: (category: SettingsCategory, position: Int) -> Unit,
    private val onCategorySelected: (category: SettingsCategory, position: Int) -> Unit
) : RecyclerView.Adapter<CategoryRailAdapter.CategoryViewHolder>() {

    /** Current selected category index. */
    var selectedPosition: Int = 0
        private set

    /** Indicates whether user focus is currently located in the right content pane. */
    var isRightPaneFocused: Boolean = false
        private set

    /**
     * ViewHolder caching references to category view widgets.
     *
     * @param itemView Root view for the category item.
     */
    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: View = itemView.findViewById(R.id.category_item_root)
        val title: TextView = itemView.findViewById(R.id.category_title)
        val subtitle: TextView = itemView.findViewById(R.id.category_subtitle)
    }

    /**
     * Inflates the category item layout for tvOS Split-View rail.
     *
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return Newly created CategoryViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_settings_category, parent, false)
        return CategoryViewHolder(view)
    }

    /**
     * Returns total number of settings categories.
     *
     * @return Count of items in the list.
     */
    override fun getItemCount(): Int = items.size

    /**
     * Binds category metadata and focus listeners to the view holder.
     *
     * @param holder The CategoryViewHolder to bind.
     * @param position Zero-based index of the item.
     */
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = items[position]
        holder.title.text = category.title
        holder.subtitle.text = category.subtitle

        applyVisualState(holder, position)

        holder.root.setOnFocusChangeListener { view, hasFocus ->
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener

            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                view.setBackgroundResource(R.drawable.bg_category_item_focused)
                holder.title.setTextColor(ContextCompat.getColor(view.context, R.color.white))
                holder.subtitle.setTextColor(ContextCompat.getColor(view.context, R.color.white))
                view.elevation = 4f

                val previousSelected = selectedPosition
                selectedPosition = currentPos
                if (previousSelected != currentPos && previousSelected in items.indices) {
                    notifyItemChanged(previousSelected)
                }

                onCategoryFocused(category, currentPos)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                view.elevation = 0f

                if (currentPos == selectedPosition && isRightPaneFocused) {
                    view.setBackgroundResource(R.drawable.bg_category_item_selected)
                    holder.title.setTextColor(ContextCompat.getColor(view.context, R.color.cyan_highlight))
                    holder.subtitle.setTextColor(ContextCompat.getColor(view.context, R.color.cool_slate))
                } else {
                    view.setBackgroundResource(R.drawable.bg_category_item_normal)
                    holder.title.setTextColor(ContextCompat.getColor(view.context, R.color.white))
                    holder.subtitle.setTextColor(ContextCompat.getColor(view.context, R.color.cool_slate))
                }
            }
        }

        holder.root.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onCategorySelected(items[currentPos], currentPos)
            }
        }
    }

    /**
     * Configures the visual background, elevation, and typography colors based on current focus and selection state.
     *
     * @param holder The target CategoryViewHolder.
     * @param position Zero-based index of the item.
     */
    private fun applyVisualState(holder: CategoryViewHolder, position: Int) {
        val context = holder.root.context
        val hasFocus = holder.root.hasFocus()

        if (hasFocus) {
            holder.root.scaleX = 1.05f
            holder.root.scaleY = 1.05f
            holder.root.elevation = 4f
            holder.root.setBackgroundResource(R.drawable.bg_category_item_focused)
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.white))
            holder.subtitle.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else if (position == selectedPosition && isRightPaneFocused) {
            holder.root.scaleX = 1.0f
            holder.root.scaleY = 1.0f
            holder.root.elevation = 0f
            holder.root.setBackgroundResource(R.drawable.bg_category_item_selected)
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.cyan_highlight))
            holder.subtitle.setTextColor(ContextCompat.getColor(context, R.color.cool_slate))
        } else {
            holder.root.scaleX = 1.0f
            holder.root.scaleY = 1.0f
            holder.root.elevation = 0f
            holder.root.setBackgroundResource(R.drawable.bg_category_item_normal)
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.white))
            holder.subtitle.setTextColor(ContextCompat.getColor(context, R.color.cool_slate))
        }
    }

    /**
     * Updates the selection state to match the given category and refreshes affected items.
     *
     * @param category Target SettingsCategory to select.
     */
    fun setSelectedCategory(category: SettingsCategory) {
        val index = items.indexOf(category)
        if (index >= 0) {
            setSelectedPosition(index)
        }
    }

    /**
     * Updates the selection index directly and refreshes affected items.
     *
     * @param position Zero-based index within the list.
     */
    fun setSelectedPosition(position: Int) {
        if (position in items.indices && position != selectedPosition) {
            val previous = selectedPosition
            selectedPosition = position
            notifyItemChanged(previous)
            notifyItemChanged(position)
        }
    }

    /**
     * Sets whether user focus is in the right content pane, updating the selected item highlight.
     *
     * @param isFocused True if focus is inside the right content pane.
     */
    fun setRightPaneFocused(isFocused: Boolean) {
        if (isRightPaneFocused != isFocused) {
            isRightPaneFocused = isFocused
            if (selectedPosition in items.indices) {
                notifyItemChanged(selectedPosition)
            }
        }
    }

    /**
     * Returns the currently selected category instance.
     *
     * @return Selected SettingsCategory.
     */
    fun getSelectedCategory(): SettingsCategory {
        return items[selectedPosition]
    }
}
