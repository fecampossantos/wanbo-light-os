package com.wanbo.settings.ui

import com.wanbo.settings.model.SettingsCategory

/**
 * Identifies which pane in the tvOS Split-View layout currently holds user focus.
 */
enum class FocusPane {
    /** The 340dp left vertical category navigation rail. */
    LEFT_RAIL,

    /** The dynamic right content display area. */
    RIGHT_CONTENT
}

/**
 * Resulting navigation action determined by DpadFocusEngine evaluation.
 */
enum class NavigationAction {
    /** Transition focus from left rail to first focusable child in right content pane. */
    FOCUS_RIGHT_PANE,

    /** Return focus from right content pane to the currently selected item in left rail. */
    FOCUS_LEFT_RAIL,

    /** Exit the settings activity because focus was already on the left rail when BACK was pressed. */
    EXIT_ACTIVITY,

    /** Select the preceding category in the vertical rail. */
    NAVIGATE_CATEGORY_UP,

    /** Select the succeeding category in the vertical rail. */
    NAVIGATE_CATEGORY_DOWN,

    /** Event consumed or delegated to internal widgets within right pane. */
    INTERNAL_PANE_NAVIGATION,

    /** Key event was ignored (e.g. key up). */
    IGNORED,

    /** Key event not handled by Split-View focus engine. */
    UNHANDLED
}

/**
 * Deterministic D-pad focus engine managing transitions and focus containment
 * between the left category rail and right content pane in WanboSettings.
 *
 * @param initialCategory The starting selected category, defaults to WIFI.
 */
class DpadFocusEngine(
    initialCategory: SettingsCategory = SettingsCategory.WIFI
) {
    companion object {
        /** Key code for D-pad Up. */
        const val KEYCODE_DPAD_UP = 19

        /** Key code for D-pad Down. */
        const val KEYCODE_DPAD_DOWN = 20

        /** Key code for D-pad Left. */
        const val KEYCODE_DPAD_LEFT = 21

        /** Key code for D-pad Right. */
        const val KEYCODE_DPAD_RIGHT = 22

        /** Key code for D-pad Center. */
        const val KEYCODE_DPAD_CENTER = 23

        /** Key code for Hardware Back button. */
        const val KEYCODE_BACK = 4

        /** Key code for Enter key on keyboard / remote. */
        const val KEYCODE_ENTER = 66
    }

    private val categories: List<SettingsCategory> = SettingsCategory.values().toList()

    /** Current pane holding user focus. */
    var currentPane: FocusPane = FocusPane.LEFT_RAIL
        private set

    /** Currently selected category index in the rail. */
    var selectedPosition: Int = categories.indexOf(initialCategory).coerceAtLeast(0)
        private set

    /** Currently selected SettingsCategory. */
    val selectedCategory: SettingsCategory
        get() = categories[selectedPosition]

    /**
     * Updates the selected category by instance.
     *
     * @param category The SettingsCategory to select.
     */
    fun selectCategory(category: SettingsCategory) {
        val index = categories.indexOf(category)
        if (index >= 0) {
            selectedPosition = index
        }
    }

    /**
     * Updates the selected category index directly.
     *
     * @param position Zero-based index within the category list.
     */
    fun selectPosition(position: Int) {
        if (position in categories.indices) {
            selectedPosition = position
        }
    }

    /**
     * Updates the current focus pane location.
     *
     * @param pane The target FocusPane.
     */
    fun setFocusPane(pane: FocusPane) {
        currentPane = pane
    }

    /**
     * Computes the new category position when navigating Up or Down in the rail.
     * Clamps within valid bounds [0, totalCount - 1].
     *
     * @param currentIndex Current zero-based index.
     * @param totalCount Total items in the category list.
     * @param isUp True for Up navigation, false for Down.
     * @return New index clamped within bounds.
     */
    fun computeTargetIndex(currentIndex: Int, totalCount: Int, isUp: Boolean): Int {
        if (totalCount <= 0) return 0
        val delta = if (isUp) -1 else 1
        return (currentIndex + delta).coerceIn(0, totalCount - 1)
    }

    /**
     * Evaluates a D-pad or remote key press event and produces the corresponding NavigationAction.
     *
     * @param keyCode Key code constant matching Android KeyEvent.
     * @param isActionDown True if key action is ACTION_DOWN, false for ACTION_UP.
     * @param canNavigateLeftInsidePane True if right content pane has an internal focusable child to the left.
     * @return Resulting NavigationAction.
     */
    fun handleKeyEvent(
        keyCode: Int,
        isActionDown: Boolean,
        canNavigateLeftInsidePane: Boolean = false
    ): NavigationAction {
        if (!isActionDown) {
            return NavigationAction.IGNORED
        }

        return when (currentPane) {
            FocusPane.LEFT_RAIL -> handleLeftRailKey(keyCode)
            FocusPane.RIGHT_CONTENT -> handleRightContentKey(keyCode, canNavigateLeftInsidePane)
        }
    }

    /**
     * Handles key event while focus is in the left category rail.
     *
     * @param keyCode Pressed key code.
     * @return NavigationAction to perform.
     */
    private fun handleLeftRailKey(keyCode: Int): NavigationAction {
        return when (keyCode) {
            KEYCODE_DPAD_RIGHT,
            KEYCODE_DPAD_CENTER,
            KEYCODE_ENTER -> {
                currentPane = FocusPane.RIGHT_CONTENT
                NavigationAction.FOCUS_RIGHT_PANE
            }

            KEYCODE_DPAD_UP -> {
                selectedPosition = computeTargetIndex(selectedPosition, categories.size, isUp = true)
                NavigationAction.NAVIGATE_CATEGORY_UP
            }

            KEYCODE_DPAD_DOWN -> {
                selectedPosition = computeTargetIndex(selectedPosition, categories.size, isUp = false)
                NavigationAction.NAVIGATE_CATEGORY_DOWN
            }

            KEYCODE_BACK -> {
                NavigationAction.EXIT_ACTIVITY
            }

            else -> NavigationAction.UNHANDLED
        }
    }

    /**
     * Handles key event while focus is in the right content pane.
     *
     * @param keyCode Pressed key code.
     * @param canNavigateLeftInsidePane True if an internal child view can receive left focus.
     * @return NavigationAction to perform.
     */
    private fun handleRightContentKey(
        keyCode: Int,
        canNavigateLeftInsidePane: Boolean
    ): NavigationAction {
        return when (keyCode) {
            KEYCODE_DPAD_LEFT -> {
                if (canNavigateLeftInsidePane) {
                    NavigationAction.INTERNAL_PANE_NAVIGATION
                } else {
                    currentPane = FocusPane.LEFT_RAIL
                    NavigationAction.FOCUS_LEFT_RAIL
                }
            }

            KEYCODE_BACK -> {
                currentPane = FocusPane.LEFT_RAIL
                NavigationAction.FOCUS_LEFT_RAIL
            }

            else -> NavigationAction.INTERNAL_PANE_NAVIGATION
        }
    }
}
