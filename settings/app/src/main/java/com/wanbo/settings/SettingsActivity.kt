package com.wanbo.settings

import android.content.Intent
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanbo.settings.model.SettingsCategory
import com.wanbo.settings.panes.AboutPaneController
import com.wanbo.settings.panes.AppsPaneController
import com.wanbo.settings.panes.AudioPaneController
import com.wanbo.settings.panes.BluetoothPaneController
import com.wanbo.settings.panes.ProjectionPaneController
import com.wanbo.settings.panes.SystemPaneController
import com.wanbo.settings.panes.WifiPaneController
import com.wanbo.settings.ui.CategoryRailAdapter
import com.wanbo.settings.ui.DpadFocusEngine
import com.wanbo.settings.ui.FocusPane
import com.wanbo.settings.ui.NavigationAction

/**
 * Main entry-point activity for WanboSettings implementing tvOS Split-View navigation.
 * Coordinates the left vertical category rail and right content pane using a deterministic
 * D-pad focus engine.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var categoryRailContainer: View
    private lateinit var categoryRecyclerView: RecyclerView
    private lateinit var contentPaneContainer: ViewGroup
    private lateinit var contentPaneTitle: TextView
    private lateinit var contentPaneSubtitle: TextView
    private lateinit var contentPaneBody: FrameLayout

    /** Rail adapter managing category item views. */
    lateinit var adapter: CategoryRailAdapter
        private set

    /** Deterministic D-pad navigation state engine. */
    lateinit var focusEngine: DpadFocusEngine
        private set

    private var currentMountedCategory: SettingsCategory? = null
    private var wifiController: WifiPaneController? = null
    private var bluetoothController: BluetoothPaneController? = null
    private var audioController: AudioPaneController? = null
    private var projectionController: ProjectionPaneController? = null
    private var appsController: AppsPaneController? = null
    private var systemController: SystemPaneController? = null
    private var aboutController: AboutPaneController? = null

    /**
     * Initializes the Split-View layout, instantiates the focus engine, and resolves the starting category.
     *
     * @param savedInstanceState Previously saved instance state bundle, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindViews()
        setupCategoryRail()
        setupFocusListeners()

        val initialCategory = SettingsCategory.fromIntentAction(intent?.action)
        selectAndDisplayCategory(initialCategory, requestFocus = true)
    }

    /**
     * Binds layout widgets from activity_settings.xml.
     */
    private fun bindViews() {
        categoryRailContainer = findViewById(R.id.category_rail_container)
        categoryRecyclerView = findViewById(R.id.category_recycler_view)
        contentPaneContainer = findViewById(R.id.content_pane_container)
        contentPaneTitle = findViewById(R.id.content_pane_title)
        contentPaneSubtitle = findViewById(R.id.content_pane_subtitle)
        contentPaneBody = findViewById(R.id.content_pane_body)
    }

    /**
     * Sets up the category navigation rail with a vertical LinearLayoutManager and tvOS item adapter.
     */
    private fun setupCategoryRail() {
        categoryRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        categoryRecyclerView.setHasFixedSize(true)

        val initialCategory = SettingsCategory.fromIntentAction(intent?.action)
        focusEngine = DpadFocusEngine(initialCategory)

        adapter = CategoryRailAdapter(
            onCategoryFocused = { category, position ->
                focusEngine.selectPosition(position)
                updateContentPane(category)
            },
            onCategorySelected = { category, position ->
                focusEngine.selectPosition(position)
                updateContentPane(category)
                focusContentPane()
            }
        )
        categoryRecyclerView.adapter = adapter
    }

    /**
     * Installs global focus listeners to maintain synchronized highlight states across rail and content panes.
     */
    private fun setupFocusListeners() {
        contentPaneContainer.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            val isNowInRightPane = isViewDescendantOf(newFocus, contentPaneContainer)
            val wasInRightPane = isViewDescendantOf(oldFocus, contentPaneContainer)

            if (isNowInRightPane && !wasInRightPane) {
                focusEngine.setFocusPane(FocusPane.RIGHT_CONTENT)
                adapter.setRightPaneFocused(true)
            } else if (!isNowInRightPane && wasInRightPane) {
                focusEngine.setFocusPane(FocusPane.LEFT_RAIL)
                adapter.setRightPaneFocused(false)
            }
        }
    }

    /**
     * Handles singleTask intent re-delivery, updating the active category accordingly.
     *
     * @param intent Incoming replacement Intent.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetCategory = SettingsCategory.fromIntentAction(intent?.action)
        selectAndDisplayCategory(targetCategory, requestFocus = true)
    }

    /**
     * Selects target category, updates right content pane, and optionally places focus on the category rail item.
     *
     * @param category The SettingsCategory to activate.
     * @param requestFocus True to focus the rail item immediately.
     */
    fun selectAndDisplayCategory(category: SettingsCategory, requestFocus: Boolean = false) {
        focusEngine.selectCategory(category)
        adapter.setSelectedCategory(category)
        updateContentPane(category)

        val targetPosition = adapter.items.indexOf(category).coerceAtLeast(0)
        categoryRecyclerView.scrollToPosition(targetPosition)

        if (requestFocus) {
            focusEngine.setFocusPane(FocusPane.LEFT_RAIL)
            adapter.setRightPaneFocused(false)
            categoryRecyclerView.post {
                val vh = categoryRecyclerView.findViewHolderForAdapterPosition(targetPosition)
                vh?.itemView?.requestFocus()
            }
        }
    }

    /**
     * Updates the right content pane header and displays the corresponding pane controller.
     *
     * @param category Active category to present.
     */
    private fun updateContentPane(category: SettingsCategory) {
        contentPaneTitle.text = category.title
        contentPaneSubtitle.text = category.subtitle
        displayCategory(category)
    }

    /**
     * Dynamically inflates and mounts the corresponding pane controller into content_pane_body.
     *
     * @param category The target SettingsCategory.
     */
    fun displayCategory(category: SettingsCategory) {
        if (currentMountedCategory == category) return
        currentMountedCategory = category

        detachActiveControllers()
        contentPaneBody.removeAllViews()

        when (category) {
            SettingsCategory.WIFI -> {
                val controller = WifiPaneController(this, contentPaneBody)
                controller.attach()
                wifiController = controller
            }
            SettingsCategory.BLUETOOTH -> {
                val controller = BluetoothPaneController(this, contentPaneBody)
                controller.attach()
                bluetoothController = controller
            }
            SettingsCategory.PROJECTION -> {
                val controller = ProjectionPaneController(this, contentPaneBody)
                controller.attach()
                projectionController = controller
            }
            SettingsCategory.AUDIO -> {
                val controller = AudioPaneController(this, contentPaneBody)
                controller.attach()
                audioController = controller
            }
            SettingsCategory.APPS -> {
                val controller = AppsPaneController(this, contentPaneBody)
                controller.attach()
                appsController = controller
            }
            SettingsCategory.SYSTEM -> {
                val controller = SystemPaneController(this, contentPaneBody)
                controller.attach()
                systemController = controller
            }
            SettingsCategory.ABOUT -> {
                val controller = AboutPaneController(this, contentPaneBody)
                controller.attach()
                aboutController = controller
            }
        }
    }

    /**
     * Detaches any currently mounted pane controllers and releases system listeners.
     */
    private fun detachActiveControllers() {
        wifiController?.detach()
        wifiController = null
        bluetoothController?.detach()
        bluetoothController = null
        audioController?.detach()
        audioController = null
        projectionController?.detach()
        projectionController = null
        appsController?.detach()
        appsController = null
        systemController?.detach()
        systemController = null
        aboutController?.detach()
        aboutController = null
    }

    /**
     * Cleans up pane controllers on activity destruction.
     */
    override fun onDestroy() {
        detachActiveControllers()
        super.onDestroy()
    }

    /**
     * Moves focus from category rail to the first available focusable child in the right content pane.
     */
    fun focusContentPane() {
        focusEngine.setFocusPane(FocusPane.RIGHT_CONTENT)
        adapter.setRightPaneFocused(true)

        val firstFocusableInBody = findFirstFocusable(contentPaneBody)
        val focusTarget = firstFocusableInBody
            ?: (if (contentPaneBody.isFocusable) contentPaneBody else findFirstFocusable(contentPaneContainer) ?: contentPaneContainer)

        focusTarget.requestFocus()
    }

    /**
     * Returns focus from the right content pane back to the currently selected category item in the rail.
     */
    fun focusSelectedRailItem() {
        focusEngine.setFocusPane(FocusPane.LEFT_RAIL)
        adapter.setRightPaneFocused(false)

        val targetPosition = adapter.selectedPosition
        val vh = categoryRecyclerView.findViewHolderForAdapterPosition(targetPosition)
        if (vh != null) {
            vh.itemView.requestFocus()
        } else {
            categoryRecyclerView.scrollToPosition(targetPosition)
            categoryRecyclerView.post {
                val holder = categoryRecyclerView.findViewHolderForAdapterPosition(targetPosition)
                holder?.itemView?.requestFocus()
            }
        }
    }

    /**
     * Intercepts D-pad navigation key events to maintain tvOS Split-View focus containment rules.
     *
     * @param event The key event being dispatched.
     * @return True if event was consumed, false to proceed with standard dispatch.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentFocusView = currentFocus
            val isInRightPane = isViewDescendantOf(currentFocusView, contentPaneContainer)
            focusEngine.setFocusPane(if (isInRightPane) FocusPane.RIGHT_CONTENT else FocusPane.LEFT_RAIL)

            var canNavigateLeftInsidePane = false
            if (isInRightPane && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentFocusView != null) {
                // Allow interactive horizontal slider cards (e.g. Master Volume, Screen Zoom) to handle DPAD_LEFT directly
                if (currentFocusView.id == R.id.card_master_volume || currentFocusView.id == R.id.card_screen_zoom) {
                    if (super.dispatchKeyEvent(event)) {
                        return true
                    }
                }

                val nextFocus = FocusFinder.getInstance().findNextFocus(
                    contentPaneContainer,
                    currentFocusView,
                    View.FOCUS_LEFT
                )
                canNavigateLeftInsidePane = (nextFocus != null && nextFocus != currentFocusView)
            }

            val action = focusEngine.handleKeyEvent(
                keyCode = event.keyCode,
                isActionDown = true,
                canNavigateLeftInsidePane = canNavigateLeftInsidePane
            )

            when (action) {
                NavigationAction.FOCUS_RIGHT_PANE -> {
                    focusContentPane()
                    return true
                }

                NavigationAction.FOCUS_LEFT_RAIL -> {
                    focusSelectedRailItem()
                    return true
                }

                NavigationAction.EXIT_ACTIVITY -> {
                    finish()
                    return true
                }

                NavigationAction.NAVIGATE_CATEGORY_UP,
                NavigationAction.NAVIGATE_CATEGORY_DOWN -> {
                    return super.dispatchKeyEvent(event)
                }

                NavigationAction.INTERNAL_PANE_NAVIGATION,
                NavigationAction.UNHANDLED,
                NavigationAction.IGNORED -> {
                    return super.dispatchKeyEvent(event)
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Intercepts BACK key when invoked via system navigation or remote back button.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentFocusView = currentFocus
        if (isViewDescendantOf(currentFocusView, contentPaneContainer)) {
            focusSelectedRailItem()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Traverses view hierarchy to locate the first focusable child widget.
     *
     * @param root The parent ViewGroup to search.
     * @return First focusable View found, or null if none.
     */
    private fun findFirstFocusable(root: ViewGroup): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.isFocusable && child.visibility == View.VISIBLE) {
                return child
            }
            if (child is ViewGroup) {
                val nested = findFirstFocusable(child)
                if (nested != null) return nested
            }
        }
        return null
    }

    /**
     * Determines whether candidate view is equal to or a child of target parent view.
     *
     * @param child Candidate view to inspect.
     * @param parent Target parent view.
     * @return True if child is identical to parent or has parent in its hierarchy.
     */
    private fun isViewDescendantOf(child: View?, parent: View): Boolean {
        if (child == null) return false
        if (child === parent) return true
        var current: ViewParent? = child.parent
        while (current != null) {
            if (current === parent) return true
            current = current.parent
        }
        return false
    }
}
