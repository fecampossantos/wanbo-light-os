package com.wanbo.settings.panes

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wanbo.settings.R

/**
 * Enumeration of hardware projection modes supported by the Wanbo optical engine.
 *
 * @property modeIndex Hardware index passed to mirror_mode extra and persist.sys.flip_mode.
 * @property title Portuguese human-readable title.
 * @property subtitle Explanatory description of projection orientation.
 */
enum class ProjectionMode(
    val modeIndex: Int,
    val title: String,
    val subtitle: String
) {
    FRONT(
        0,
        "Projecao Frontal",
        "Mesa / Padrao"
    ),
    REAR(
        1,
        "Projecao Traseira",
        "Mesa Traseira / Inversao Horizontal"
    ),
    FRONT_CEILING(
        2,
        "Projecao Frontal Teto",
        "Teto Frontal / Inversao Vertical"
    ),
    REAR_CEILING(
        3,
        "Projecao Traseira Teto",
        "Teto Traseiro / Inversao H + V"
    );

    companion object {
        /**
         * Resolves ProjectionMode by its numeric hardware mode index.
         *
         * @param index Numeric mode index (0..3).
         * @return Matching ProjectionMode or FRONT if index is unmapped.
         */
        fun fromIndex(index: Int): ProjectionMode {
            return values().firstOrNull { it.modeIndex == index } ?: FRONT
        }
    }
}

/**
 * Enumeration of display aspect ratio configurations.
 *
 * @property id Unique string identifier.
 * @property title Portuguese human-readable title.
 * @property subtitle Explanatory description of aspect ratio format.
 */
enum class AspectRatioMode(
    val id: String,
    val title: String,
    val subtitle: String
) {
    ASPECT_16_9(
        "16_9",
        "16:9 Widescreen",
        "Formato panoramico padrao para filmes e series"
    ),
    ASPECT_4_3(
        "4_3",
        "4:3 Padrao",
        "Formato tradicional de transmissao e apresentacoes"
    ),
    ASPECT_AUTO(
        "auto",
        "Automatico",
        "Ajuste de proporcao conforme a resolucao da fonte"
    );

    companion object {
        /**
         * Resolves AspectRatioMode by its string identifier.
         *
         * @param id Identifier string.
         * @return Matching AspectRatioMode or ASPECT_16_9 as fallback.
         */
        fun fromId(id: String?): AspectRatioMode {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ASPECT_16_9
        }
    }
}

/**
 * Lightweight descriptor for broadcast intent validation in pure JVM unit tests.
 *
 * @property action Broadcast action string.
 * @property extras Map containing key-value intent extras.
 */
data class BroadcastIntentDescriptor(
    val action: String,
    val extras: Map<String, Any>
)

/**
 * Lightweight descriptor for Keystone calibration activity launch intents.
 *
 * @property packageName Target package identifier.
 * @property className Target activity class name.
 * @property action Optional intent action string.
 * @property flags Intent launch flags.
 */
data class KeystoneIntentDescriptor(
    val packageName: String?,
    val className: String?,
    val action: String?,
    val flags: Int
)

/**
 * Pure logic helper for projection modes, broadcast actions, intent descriptors,
 * system property handling, and screen zoom calculations without framework dependencies.
 */
object ProjectionStateHelper {

    /** Broadcast action for dispatching mirror mode changes to MStar/MediaTek display server. */
    const val ACTION_SET_MIRROR_MODE = "com.chijimlee.action.SET_MIRROR_MODE"

    /** Extra parameter key for mirror mode index. */
    const val EXTRA_MIRROR_MODE = "mirror_mode"

    /** Extra parameter key for flip mode index. */
    const val EXTRA_FLIP_MODE = "flip_mode"

    /** System property storing active flip mode index. */
    const val PROP_FLIP_MODE = "persist.sys.flip_mode"

    /** Alternative system property storing active mirror mode index. */
    const val PROP_MIRROR_MODE = "persist.sys.mirror_mode"

    /** SharedPreferences file name for projection preferences. */
    const val PREFS_NAME = "wanbo_projection_prefs"

    /** Preference key for persisted projection mode index. */
    const val PREF_KEY_PROJECTION_MODE = "key_projection_mode"

    /** Preference key for persisted aspect ratio. */
    const val PREF_KEY_ASPECT_RATIO = "key_aspect_ratio"

    /** Preference key for persisted screen zoom percentage. */
    const val PREF_KEY_SCREEN_ZOOM = "key_screen_zoom"

    /** Primary Keystone calibration activity package name. */
    const val KEYSTONE_PRIMARY_PACKAGE = "com.chijimlee.tyylauncher"

    /** Primary Keystone calibration activity class name. */
    const val KEYSTONE_PRIMARY_ACTIVITY = "com.chijimlee.tyylauncher.activity.ProjectModeActivity"

    /** Fallback Keystone calibration activity package name. */
    const val KEYSTONE_FALLBACK_PACKAGE = "com.chijimlee.trapezoid"

    /** Fallback Keystone calibration activity class name. */
    const val KEYSTONE_FALLBACK_ACTIVITY = "com.chijimlee.trapezoid.activity.FourPointActivity"

    /** Fallback broadcast / intent action for launching Keystone calibration. */
    const val ACTION_LAUNCH_CALIBRATION = "com.chijimlee.trapezoid.ACTION_LAUNCH_CALIBRATION"

    /** Standard launch flags: starts the activity in a fresh task. */
    const val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK

    /** Minimum zoom percentage supported by display scaler. */
    const val MIN_ZOOM_PERCENTAGE = 80

    /** Maximum zoom percentage supported by display scaler. */
    const val MAX_ZOOM_PERCENTAGE = 100

    /** Default initial zoom percentage. */
    const val DEFAULT_ZOOM_PERCENTAGE = 100

    /**
     * Creates a testable descriptor of the SET_MIRROR_MODE broadcast intent.
     *
     * @param modeIndex Target projection mode index (0..3).
     * @return BroadcastIntentDescriptor containing action and extras map.
     */
    fun getMirrorModeBroadcastDescriptor(modeIndex: Int): BroadcastIntentDescriptor {
        return BroadcastIntentDescriptor(
            action = ACTION_SET_MIRROR_MODE,
            extras = mapOf(
                EXTRA_MIRROR_MODE to modeIndex,
                EXTRA_FLIP_MODE to modeIndex
            )
        )
    }

    /**
     * Creates an Android broadcast Intent configured with SET_MIRROR_MODE action and extras.
     *
     * @param modeIndex Target projection mode index (0..3).
     * @return Intent ready to be broadcast to the vendor display HAL.
     */
    fun createMirrorModeBroadcastIntent(modeIndex: Int): Intent {
        return Intent(ACTION_SET_MIRROR_MODE).apply {
            putExtra(EXTRA_MIRROR_MODE, modeIndex)
            putExtra(EXTRA_FLIP_MODE, modeIndex)
        }
    }

    /**
     * Creates a testable descriptor for the primary Keystone calibration launch intent.
     *
     * @return KeystoneIntentDescriptor specifying tyylauncher ProjectModeActivity.
     */
    fun getPrimaryKeystoneDescriptor(): KeystoneIntentDescriptor {
        return KeystoneIntentDescriptor(
            packageName = KEYSTONE_PRIMARY_PACKAGE,
            className = KEYSTONE_PRIMARY_ACTIVITY,
            action = null,
            flags = LAUNCH_FLAGS
        )
    }

    /**
     * Creates a testable descriptor for the fallback Keystone calibration launch intent.
     *
     * @return KeystoneIntentDescriptor specifying trapezoid FourPointActivity.
     */
    fun getFallbackKeystoneDescriptor(): KeystoneIntentDescriptor {
        return KeystoneIntentDescriptor(
            packageName = KEYSTONE_FALLBACK_PACKAGE,
            className = KEYSTONE_FALLBACK_ACTIVITY,
            action = null,
            flags = LAUNCH_FLAGS
        )
    }

    /**
     * Creates a testable descriptor for the action-based Keystone calibration launch intent.
     *
     * @return KeystoneIntentDescriptor specifying ACTION_LAUNCH_CALIBRATION.
     */
    fun getActionKeystoneDescriptor(): KeystoneIntentDescriptor {
        return KeystoneIntentDescriptor(
            packageName = null,
            className = null,
            action = ACTION_LAUNCH_CALIBRATION,
            flags = LAUNCH_FLAGS
        )
    }

    /**
     * Creates an explicit Intent configured for the primary Keystone calibration activity.
     *
     * @return Configured explicit Intent with FLAG_ACTIVITY_NEW_TASK.
     */
    fun createPrimaryKeystoneIntent(): Intent {
        return Intent().apply {
            component = ComponentName(KEYSTONE_PRIMARY_PACKAGE, KEYSTONE_PRIMARY_ACTIVITY)
            addFlags(LAUNCH_FLAGS)
        }
    }

    /**
     * Creates an explicit Intent configured for the fallback Keystone calibration activity.
     *
     * @return Configured explicit Intent with FLAG_ACTIVITY_NEW_TASK.
     */
    fun createFallbackKeystoneIntent(): Intent {
        return Intent().apply {
            component = ComponentName(KEYSTONE_FALLBACK_PACKAGE, KEYSTONE_FALLBACK_ACTIVITY)
            addFlags(LAUNCH_FLAGS)
        }
    }

    /**
     * Creates an implicit Intent configured for Keystone calibration via action string.
     *
     * @return Configured implicit Intent with FLAG_ACTIVITY_NEW_TASK.
     */
    fun createActionKeystoneIntent(): Intent {
        return Intent(ACTION_LAUNCH_CALIBRATION).apply {
            addFlags(LAUNCH_FLAGS)
        }
    }

    /**
     * Resolves the human-readable display title for a projection mode index.
     *
     * @param modeIndex Numeric mode index (0..3).
     * @return Portuguese display title.
     */
    fun getModeTitle(modeIndex: Int): String {
        return ProjectionMode.fromIndex(modeIndex).title
    }

    /**
     * Resolves the explanatory subtitle for a projection mode index.
     *
     * @param modeIndex Numeric mode index (0..3).
     * @return Portuguese explanatory subtitle.
     */
    fun getModeSubtitle(modeIndex: Int): String {
        return ProjectionMode.fromIndex(modeIndex).subtitle
    }

    /**
     * Clamps a screen zoom percentage between [MIN_ZOOM_PERCENTAGE] and [MAX_ZOOM_PERCENTAGE].
     *
     * @param zoom Input zoom percentage integer.
     * @return Clamped zoom integer (80..100).
     */
    fun clampZoomPercentage(zoom: Int): Int {
        return zoom.coerceIn(MIN_ZOOM_PERCENTAGE, MAX_ZOOM_PERCENTAGE)
    }

    /**
     * Formats an integer zoom percentage into a display string (e.g. "100%").
     *
     * @param zoom Zoom percentage integer.
     * @return Formatted percentage string.
     */
    fun formatZoomPercentage(zoom: Int): String {
        return "${clampZoomPercentage(zoom)}%"
    }

    /**
     * Validates whether a given projection mode index is within the valid hardware range (0..3).
     *
     * @param modeIndex Mode index to check.
     * @return True if valid, false otherwise.
     */
    fun isValidModeIndex(modeIndex: Int): Boolean {
        return modeIndex in 0..3
    }

    /**
     * Safely reads a system property string via reflection.
     *
     * @param propName System property key name.
     * @param defaultValue Fallback value if property cannot be read.
     * @return Property value or defaultValue on failure.
     */
    fun getSystemProperty(propName: String, defaultValue: String = ""): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, propName, defaultValue) as? String) ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    /**
     * Safely sets a system property string via reflection.
     *
     * @param propName System property key name.
     * @param value Property value to assign.
     * @return True if invocation succeeded without exception, false otherwise.
     */
    fun setSystemProperty(propName: String, value: String): Boolean {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val setMethod = systemPropertiesClass.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, propName, value)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Controller for the Projection & Keystone Calibration settings pane.
 * Manages 4-way optical projection modes, Keystone trapezoid hub launching,
 * display aspect ratio selection, and digital screen zoom calibration.
 *
 * @param activity Host AppCompatActivity.
 * @param container Target container ViewGroup into which the pane layout is inflated.
 */
class ProjectionPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null

    // UI Widgets: Keystone Hub
    private lateinit var cardKeystoneCalibration: View
    private lateinit var badgeKeystoneLaunch: TextView

    // UI Widgets: Projection Modes
    private lateinit var cardProjectionFront: View
    private lateinit var badgeProjectionFront: TextView
    private lateinit var cardProjectionRear: View
    private lateinit var badgeProjectionRear: TextView
    private lateinit var cardProjectionFrontCeiling: View
    private lateinit var badgeProjectionFrontCeiling: TextView
    private lateinit var cardProjectionRearCeiling: View
    private lateinit var badgeProjectionRearCeiling: TextView

    // UI Widgets: Aspect Ratio
    private lateinit var cardAspect169: View
    private lateinit var badgeAspect169: TextView
    private lateinit var cardAspect43: View
    private lateinit var badgeAspect43: TextView
    private lateinit var cardAspectAuto: View
    private lateinit var badgeAspectAuto: TextView

    // UI Widgets: Screen Zoom
    private lateinit var cardScreenZoom: View
    private lateinit var textZoomPercentage: TextView
    private lateinit var progressScreenZoom: ProgressBar
    private lateinit var btnZoomOut: View
    private lateinit var btnZoomIn: View

    /** Active projection mode index (0..3). */
    var currentProjectionMode: Int = 0
        private set

    /** Active aspect ratio configuration. */
    var currentAspectRatio: AspectRatioMode = AspectRatioMode.ASPECT_16_9
        private set

    /** Active screen zoom percentage (80..100). */
    var currentZoom: Int = ProjectionStateHelper.DEFAULT_ZOOM_PERCENTAGE
        private set

    private val prefs by lazy {
        activity.getSharedPreferences(ProjectionStateHelper.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Inflates the Projection pane layout, binds UI views, configures D-pad key listeners,
     * and initializes active hardware states.
     */
    fun attach() {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_projection, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        setupListeners()
        refreshProjectionState()
    }

    /**
     * Detaches the controller and cleans up references.
     */
    fun detach() {
        rootView = null
    }

    /**
     * Binds view elements from the inflated pane layout.
     *
     * @param root Inflated root view.
     */
    private fun bindViews(root: View) {
        cardKeystoneCalibration = root.findViewById(R.id.card_keystone_calibration)
        badgeKeystoneLaunch = root.findViewById(R.id.badge_keystone_launch)

        cardProjectionFront = root.findViewById(R.id.card_projection_front)
        badgeProjectionFront = root.findViewById(R.id.badge_projection_front)
        cardProjectionRear = root.findViewById(R.id.card_projection_rear)
        badgeProjectionRear = root.findViewById(R.id.badge_projection_rear)
        cardProjectionFrontCeiling = root.findViewById(R.id.card_projection_front_ceiling)
        badgeProjectionFrontCeiling = root.findViewById(R.id.badge_projection_front_ceiling)
        cardProjectionRearCeiling = root.findViewById(R.id.card_projection_rear_ceiling)
        badgeProjectionRearCeiling = root.findViewById(R.id.badge_projection_rear_ceiling)

        cardAspect169 = root.findViewById(R.id.card_aspect_16_9)
        badgeAspect169 = root.findViewById(R.id.badge_aspect_16_9)
        cardAspect43 = root.findViewById(R.id.card_aspect_4_3)
        badgeAspect43 = root.findViewById(R.id.badge_aspect_4_3)
        cardAspectAuto = root.findViewById(R.id.card_aspect_auto)
        badgeAspectAuto = root.findViewById(R.id.badge_aspect_auto)

        cardScreenZoom = root.findViewById(R.id.card_screen_zoom)
        textZoomPercentage = root.findViewById(R.id.text_zoom_percentage)
        progressScreenZoom = root.findViewById(R.id.progress_screen_zoom)
        btnZoomOut = root.findViewById(R.id.btn_zoom_out)
        btnZoomIn = root.findViewById(R.id.btn_zoom_in)
    }

    /**
     * Configures tvOS smooth scale animations for all interactive cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardKeystoneCalibration)

        applyFocusAnimation(cardProjectionFront)
        applyFocusAnimation(cardProjectionRear)
        applyFocusAnimation(cardProjectionFrontCeiling)
        applyFocusAnimation(cardProjectionRearCeiling)

        applyFocusAnimation(cardAspect169)
        applyFocusAnimation(cardAspect43)
        applyFocusAnimation(cardAspectAuto)

        applyFocusAnimation(cardScreenZoom)
        applyFocusAnimation(btnZoomOut)
        applyFocusAnimation(btnZoomIn)
    }

    /**
     * Configures click listeners and D-pad key handlers across all cards and controls.
     */
    private fun setupListeners() {
        // Keystone Calibration Card Click
        cardKeystoneCalibration.setOnClickListener {
            launchKeystoneCalibration()
        }

        // 4-Way Projection Mode Cards
        cardProjectionFront.setOnClickListener {
            setProjectionMode(0)
        }
        cardProjectionRear.setOnClickListener {
            setProjectionMode(1)
        }
        cardProjectionFrontCeiling.setOnClickListener {
            setProjectionMode(2)
        }
        cardProjectionRearCeiling.setOnClickListener {
            setProjectionMode(3)
        }

        // Aspect Ratio Cards
        cardAspect169.setOnClickListener {
            setAspectRatio(AspectRatioMode.ASPECT_16_9)
        }
        cardAspect43.setOnClickListener {
            setAspectRatio(AspectRatioMode.ASPECT_4_3)
        }
        cardAspectAuto.setOnClickListener {
            setAspectRatio(AspectRatioMode.ASPECT_AUTO)
        }

        // Screen Zoom interactive slider card
        cardScreenZoom.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        adjustZoom(-5)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        adjustZoom(5)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnZoomOut.setOnClickListener {
            adjustZoom(-5)
        }
        btnZoomIn.setOnClickListener {
            adjustZoom(5)
        }
    }

    /**
     * Reads current hardware and persisted configurations to initialize the UI state.
     */
    fun refreshProjectionState() {
        // 1. Resolve current Projection Mode from MediaTek eMMC env, system property, or persisted preference
        val emmcFlip = readMtkEmmcFlipMode()
        val mstarProp = ProjectionStateHelper.getSystemProperty("vendor.mstar.mirrormode")
        val propValue = ProjectionStateHelper.getSystemProperty(ProjectionStateHelper.PROP_FLIP_MODE)
        val initialMode = emmcFlip.toIntOrNull()
            ?: mstarProp.toIntOrNull()
            ?: propValue.toIntOrNull()
            ?: prefs.getInt(ProjectionStateHelper.PREF_KEY_PROJECTION_MODE, 0)
        currentProjectionMode = if (ProjectionStateHelper.isValidModeIndex(initialMode)) initialMode else 0
        updateProjectionModeUi(currentProjectionMode)

        // 2. Resolve Aspect Ratio
        val savedAspectId = prefs.getString(ProjectionStateHelper.PREF_KEY_ASPECT_RATIO, AspectRatioMode.ASPECT_16_9.id)
        currentAspectRatio = AspectRatioMode.fromId(savedAspectId)
        updateAspectRatioUi(currentAspectRatio)

        // 3. Resolve Screen Zoom
        val savedZoom = prefs.getInt(ProjectionStateHelper.PREF_KEY_SCREEN_ZOOM, ProjectionStateHelper.DEFAULT_ZOOM_PERCENTAGE)
        currentZoom = ProjectionStateHelper.clampZoomPercentage(savedZoom)
        updateZoomUi(currentZoom)
    }

    /**
     * Activates a new projection mode index, dispatches hardware broadcast,
     * invokes the MediaTek TV Factory Display HAL wrapper, updates system properties,
     * saves preference, and updates UI badges.
     *
     * @param modeIndex Target projection mode index (0..3).
     */
    fun setProjectionMode(modeIndex: Int) {
        if (!ProjectionStateHelper.isValidModeIndex(modeIndex)) return
        currentProjectionMode = modeIndex

        // Invoke real MediaTek TV Factory Display HAL and eMMC environment setter
        applyMtkHardwareMirrorMode(modeIndex)

        // Dispatch broadcast to MStar/MediaTek HAL
        val intent = ProjectionStateHelper.createMirrorModeBroadcastIntent(modeIndex)
        try {
            activity.sendBroadcast(intent)
        } catch (_: Exception) {
        }

        // Update system properties
        ProjectionStateHelper.setSystemProperty("vendor.mstar.mirrormode", modeIndex.toString())
        ProjectionStateHelper.setSystemProperty(ProjectionStateHelper.PROP_FLIP_MODE, modeIndex.toString())
        ProjectionStateHelper.setSystemProperty(ProjectionStateHelper.PROP_MIRROR_MODE, modeIndex.toString())

        // Persist preference
        prefs.edit().putInt(ProjectionStateHelper.PREF_KEY_PROJECTION_MODE, modeIndex).apply()

        // Update visual state
        updateProjectionModeUi(modeIndex)
    }

    /**
     * Invokes MtkTvFApiDisplayWrapper.setMirrorMode and MtkTvFApiSystem.setEmmcEnvVar
     * using the com.chijimlee.tyylauncher package classLoader so the hardware scaler
     * flips the display immediately and persists across reboots.
     *
     * @param modeIndex Target projection mode index (0..3).
     * @return True if the MediaTek HAL invocation succeeded; false otherwise.
     */
    private fun applyMtkHardwareMirrorMode(modeIndex: Int): Boolean {
        return try {
            val pkgCtx = activity.createPackageContext(
                "com.chijimlee.tyylauncher",
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val cl = pkgCtx.classLoader

            val sysClass = cl.loadClass("com.mediatek.twoworlds.factory.MtkTvFApiSystem")
            val sysInstance = sysClass.getMethod("getInstance").invoke(null)
            sysClass.getMethod("setEmmcEnvVar", String::class.java, String::class.java)
                .invoke(sysInstance, "FLIP_MODE", modeIndex.toString())

            val dispClass = cl.loadClass("com.mediatek.twoworlds.factory.clientwrapper.MtkTvFApiDisplayWrapper")
            val dispInstance = dispClass.getMethod("getInstance").invoke(null)
            dispClass.getMethod("setMirrorMode", Int::class.javaPrimitiveType)
                .invoke(dispInstance, modeIndex)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads the active FLIP_MODE variable from the MediaTek eMMC environment via MtkTvFApiSystem.
     *
     * @return Current FLIP_MODE value string or empty string if unavailable.
     */
    private fun readMtkEmmcFlipMode(): String {
        return try {
            val pkgCtx = activity.createPackageContext(
                "com.chijimlee.tyylauncher",
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val cl = pkgCtx.classLoader
            val sysClass = cl.loadClass("com.mediatek.twoworlds.factory.MtkTvFApiSystem")
            val sysInstance = sysClass.getMethod("getInstance").invoke(null)
            (sysClass.getMethod("getEmmcEnvVar", String::class.java)
                .invoke(sysInstance, "FLIP_MODE") as? String)?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Updates visual selection badges across all 4 projection mode cards.
     *
     * @param selectedMode Active projection mode index.
     */
    private fun updateProjectionModeUi(selectedMode: Int) {
        updateBadge(badgeProjectionFront, selectedMode == 0)
        updateBadge(badgeProjectionRear, selectedMode == 1)
        updateBadge(badgeProjectionFrontCeiling, selectedMode == 2)
        updateBadge(badgeProjectionRearCeiling, selectedMode == 3)
    }

    /**
     * Activates an aspect ratio mode, persists preference, and updates UI badges.
     *
     * @param mode Target AspectRatioMode.
     */
    fun setAspectRatio(mode: AspectRatioMode) {
        currentAspectRatio = mode
        prefs.edit().putString(ProjectionStateHelper.PREF_KEY_ASPECT_RATIO, mode.id).apply()
        updateAspectRatioUi(mode)
    }

    /**
     * Updates visual selection badges across aspect ratio cards.
     *
     * @param selectedMode Active AspectRatioMode.
     */
    private fun updateAspectRatioUi(selectedMode: AspectRatioMode) {
        updateBadge(badgeAspect169, selectedMode == AspectRatioMode.ASPECT_16_9)
        updateBadge(badgeAspect43, selectedMode == AspectRatioMode.ASPECT_4_3)
        updateBadge(badgeAspectAuto, selectedMode == AspectRatioMode.ASPECT_AUTO)
    }

    /**
     * Increments or decrements the screen zoom percentage by a specified delta.
     *
     * @param delta Integer offset to apply (e.g. -5 or +5).
     */
    fun adjustZoom(delta: Int) {
        setZoom(currentZoom + delta)
    }

    /**
     * Sets screen zoom to an explicit percentage, clamps bounds (80..100),
     * persists preference, and updates UI progress bar and text.
     *
     * @param zoom Target zoom percentage.
     */
    fun setZoom(zoom: Int) {
        val clamped = ProjectionStateHelper.clampZoomPercentage(zoom)
        currentZoom = clamped
        prefs.edit().putInt(ProjectionStateHelper.PREF_KEY_SCREEN_ZOOM, clamped).apply()
        updateZoomUi(clamped)
    }

    /**
     * Updates visual elements of the screen zoom card.
     *
     * @param zoom Active zoom percentage integer.
     */
    private fun updateZoomUi(zoom: Int) {
        textZoomPercentage.text = ProjectionStateHelper.formatZoomPercentage(zoom)
        progressScreenZoom.progress = zoom
    }

    /**
     * Updates a status badge TextView between active and inactive states.
     *
     * @param badge Target TextView badge.
     * @param isActive True if card is currently active/selected.
     */
    private fun updateBadge(badge: TextView, isActive: Boolean) {
        if (isActive) {
            badge.text = "Ativo"
            badge.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
            badge.setBackgroundResource(R.drawable.bg_badge_active)
        } else {
            badge.text = "Selecionar"
            badge.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
            badge.setBackgroundResource(R.drawable.bg_badge_inactive)
        }
    }

    /**
     * Safely launches the hardware Keystone trapezoid calibration activity with fallback chain.
     *
     * @return True if an intent launched successfully without throwing ActivityNotFoundException.
     */
    fun launchKeystoneCalibration(): Boolean {
        // 1. Primary Component: tyylauncher ProjectModeActivity
        try {
            activity.startActivity(ProjectionStateHelper.createPrimaryKeystoneIntent())
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }

        // 2. Fallback Component: trapezoid FourPointActivity
        try {
            activity.startActivity(ProjectionStateHelper.createFallbackKeystoneIntent())
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }

        // 3. Fallback Action: ACTION_LAUNCH_CALIBRATION
        try {
            activity.startActivity(ProjectionStateHelper.createActionKeystoneIntent())
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }

        Toast.makeText(
            activity,
            "Aplicativo de calibracao trapezoidal nao encontrado",
            Toast.LENGTH_SHORT
        ).show()
        return false
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
