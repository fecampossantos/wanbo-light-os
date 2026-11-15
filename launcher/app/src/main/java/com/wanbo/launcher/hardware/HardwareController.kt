package com.wanbo.launcher.hardware

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Controller responsible for managing direct 1-click hardware shortcuts
 * on the Wanbo T2 Max projector, including Keystone / Trapezoid calibration,
 * HDMI video input switching, and Android TV / projector settings.
 *
 * All intents are explicitly targeted and include [Intent.FLAG_ACTIVITY_NEW_TASK]
 * to ensure reliable launching from any UI or Application context.
 *
 * @param defaultContext Optional default [Context] for instance-based dispatch.
 */
class HardwareController(private val defaultContext: Context? = null) {

    companion object {
        /** Package identifier for the Wanbo trapezoid/keystone calibration application. */
        const val KEYSTONE_PACKAGE: String = "com.chijimlee.trapezoid"

        /** Main activity class name for the Wanbo trapezoid/keystone calibration application. */
        const val KEYSTONE_ACTIVITY: String = "com.chijimlee.trapezoid.MainActivity"

        /** Package identifier for the MediaTek TV input / HDMI switching application. */
        const val HDMI_PACKAGE: String = "com.mediatek.tvinput"

        /** Main activity class name for the MediaTek TV input / HDMI switching application. */
        const val HDMI_ACTIVITY: String = "com.mediatek.tvinput.TvInputActivity"

        /** Package identifier for the Android TV system settings application. */
        const val SETTINGS_PACKAGE: String = "com.android.tv.settings"

        /** Main activity class name for the Android TV system settings application. */
        const val SETTINGS_ACTIVITY: String = "com.android.tv.settings.MainActivity"

        /** Standard launch flags: ensures the activity starts in a new task outside launcher stack. */
        const val LAUNCH_FLAGS: Int = Intent.FLAG_ACTIVITY_NEW_TASK

        /** Toast error notification displayed when the Keystone calibration application is not found. */
        const val MSG_KEYSTONE_NOT_FOUND: String = "Keystone calibration app not found"

        /** Toast error notification displayed when the HDMI TV input application is not found. */
        const val MSG_HDMI_NOT_FOUND: String = "HDMI input app not found"

        /** Toast error notification displayed when the Settings application is not found. */
        const val MSG_SETTINGS_NOT_FOUND: String = "Settings app not found"

        /**
         * Constructs an explicit Intent targeting the specified package and activity class,
         * stamped with [Intent.FLAG_ACTIVITY_NEW_TASK].
         *
         * @param packageName Target application package identifier.
         * @param activityName Fully-qualified class name of the target activity.
         * @return Explicit Intent configured with the component and launch flags.
         */
        @JvmStatic
        fun createExplicitIntent(packageName: String, activityName: String): Intent {
            return Intent().apply {
                component = ComponentName(packageName, activityName)
                addFlags(LAUNCH_FLAGS)
            }
        }

        /**
         * Helper alias to construct an explicit Intent for a given package and activity name.
         *
         * @param packageName Target application package identifier.
         * @param activityName Fully-qualified class name of the target activity.
         * @return Explicit Intent configured with the component and launch flags.
         */
        @JvmStatic
        fun createIntent(packageName: String, activityName: String): Intent {
            return createExplicitIntent(packageName, activityName)
        }

        /**
         * Creates an explicit Intent configured for Keystone / Trapezoid calibration.
         * Sets target component to com.chijimlee.trapezoid.MainActivity and includes FLAG_ACTIVITY_NEW_TASK.
         *
         * @return Explicit Intent configured to launch the Keystone calibration activity.
         */
        @JvmStatic
        fun createKeystoneIntent(): Intent {
            return createExplicitIntent(KEYSTONE_PACKAGE, KEYSTONE_ACTIVITY)
        }

        /**
         * Creates an explicit Intent configured for HDMI video input switching.
         * Sets target component to com.mediatek.tvinput.TvInputActivity and includes FLAG_ACTIVITY_NEW_TASK.
         *
         * @return Explicit Intent configured to launch the HDMI input switching activity.
         */
        @JvmStatic
        fun createHdmiIntent(): Intent {
            return createExplicitIntent(HDMI_PACKAGE, HDMI_ACTIVITY)
        }

        /**
         * Creates an explicit Intent configured for Projector / Android TV settings.
         * Sets target component to com.android.tv.settings.MainActivity and includes FLAG_ACTIVITY_NEW_TASK.
         *
         * @return Explicit Intent configured to launch the Android TV settings activity.
         */
        @JvmStatic
        fun createSettingsIntent(): Intent {
            return createExplicitIntent(SETTINGS_PACKAGE, SETTINGS_ACTIVITY)
        }

        /**
         * Dispatches an Intent safely using provided launcher and fallback notifier lambdas,
         * catching [ActivityNotFoundException] and general exceptions without crashing.
         *
         * @param intent The Intent to dispatch.
         * @param launcher Function that executes the activity launch (e.g. context::startActivity).
         * @param notifier Function called to present error feedback on failure (e.g. Toast display).
         * @param errorMessage The user-facing error message passed to notifier on failure.
         * @return True if launched successfully without exception; false if an exception was caught.
         */
        @JvmStatic
        fun dispatchSafely(
            intent: Intent,
            launcher: (Intent) -> Unit,
            notifier: (String) -> Unit,
            errorMessage: String
        ): Boolean {
            return try {
                launcher(intent)
                true
            } catch (e: ActivityNotFoundException) {
                notifier(errorMessage)
                false
            } catch (e: Exception) {
                notifier(errorMessage)
                false
            }
        }

        /**
         * Safely launches an explicit intent using the provided Android Context.
         * Catches [ActivityNotFoundException] and general exceptions, displaying a Toast notification.
         *
         * @param context Context used to invoke startActivity and show Toast notifications.
         * @param intent The explicit Intent to dispatch.
         * @param errorMessage The user-facing Toast message to display on failure.
         * @return True if launched successfully, false if an error occurred.
         */
        @JvmStatic
        fun safeLaunch(context: Context, intent: Intent, errorMessage: String): Boolean {
            return try {
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                false
            } catch (e: Exception) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                false
            }
        }

        /**
         * Safely launches the Keystone / Trapezoid calibration application.
         * Attempts primary keystone activity first, then falls back to ProjectModeActivity
         * or FourPointActivity before displaying a Toast notification.
         *
         * @param context Context used to invoke startActivity and display Toast notifications.
         * @return True if an activity was launched successfully; false otherwise.
         */
        @JvmStatic
        fun launchKeystone(context: Context): Boolean {
            return try {
                context.startActivity(createKeystoneIntent())
                true
            } catch (e: ActivityNotFoundException) {
                try {
                    val projectModeIntent = createExplicitIntent(
                        "com.chijimlee.tyylauncher",
                        "com.chijimlee.tyylauncher.activity.ProjectModeActivity"
                    )
                    context.startActivity(projectModeIntent)
                    true
                } catch (e2: ActivityNotFoundException) {
                    try {
                        val fourPointIntent = createExplicitIntent(
                            "com.chijimlee.trapezoid",
                            "com.chijimlee.trapezoid.activity.FourPointActivity"
                        )
                        context.startActivity(fourPointIntent)
                        true
                    } catch (fallbackEx: Exception) {
                        Toast.makeText(context, MSG_KEYSTONE_NOT_FOUND, Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, MSG_KEYSTONE_NOT_FOUND, Toast.LENGTH_SHORT).show()
                false
            }
        }

        /**
         * Safely launches the HDMI TV input switching application.
         * Catches [ActivityNotFoundException] and notifies the user via Toast without crashing.
         *
         * @param context Context used to invoke startActivity and display Toast notifications.
         * @return True if the activity was launched successfully; false otherwise.
         */
        @JvmStatic
        fun launchHdmi(context: Context): Boolean {
            return safeLaunch(context, createHdmiIntent(), MSG_HDMI_NOT_FOUND)
        }

        /**
         * Switches the active video input source to HDMI on the projector.
         * Convenience alias for [launchHdmi] matching Top Bar hardware requirements.
         *
         * @param context Context used to dispatch the HDMI intent.
         * @return True if launched successfully; false otherwise.
         */
        @JvmStatic
        fun switchHdmiInput(context: Context): Boolean {
            return launchHdmi(context)
        }

        /**
         * Launches the Keystone / Trapezoid geometric calibration interface.
         * Convenience alias for [launchKeystone] matching Top Bar hardware requirements.
         *
         * @param context Context used to dispatch the Keystone intent.
         * @return True if launched successfully; false otherwise.
         */
        @JvmStatic
        fun launchKeystoneCalibration(context: Context): Boolean {
            return launchKeystone(context)
        }

        /**
         * Safely launches the Projector / Android TV settings application.
         * First attempts com.wanbo.settings, falling back to com.android.tv.settings.MainActivity,
         * and finally platform Settings.ACTION_SETTINGS before showing a Toast.
         *
         * @param context Context used to invoke startActivity and display Toast notifications.
         * @return True if the activity was launched successfully; false otherwise.
         */
        @JvmStatic
        fun launchSettings(context: Context): Boolean {
            val pm = context.packageManager
            val wanboIntent = pm?.getLeanbackLaunchIntentForPackage("com.wanbo.settings")
                ?: pm?.getLaunchIntentForPackage("com.wanbo.settings")
            if (wanboIntent != null) {
                wanboIntent.addFlags(LAUNCH_FLAGS)
                return try {
                    context.startActivity(wanboIntent)
                    true
                } catch (e: Exception) {
                    launchTvSettingsFallback(context)
                }
            }
            return launchTvSettingsFallback(context)
        }

        /**
         * Fallback dispatcher for Android TV settings when com.wanbo.settings is unavailable.
         *
         * @param context Context used to dispatch Android TV settings.
         * @return True if launched successfully; false otherwise.
         */
        @JvmStatic
        private fun launchTvSettingsFallback(context: Context): Boolean {
            return try {
                context.startActivity(createSettingsIntent())
                true
            } catch (e: ActivityNotFoundException) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(LAUNCH_FLAGS)
                    }
                    context.startActivity(fallbackIntent)
                    true
                } catch (fallbackEx: Exception) {
                    Toast.makeText(context, MSG_SETTINGS_NOT_FOUND, Toast.LENGTH_SHORT).show()
                    false
                }
            } catch (e: Exception) {
                Toast.makeText(context, MSG_SETTINGS_NOT_FOUND, Toast.LENGTH_SHORT).show()
                false
            }
        }

        /**
         * Applies the specified hardware optical mirror mode (0..3) directly via the MediaTek
         * TV Factory HIDL wrapper (MtkTvFApiDisplayWrapper and MtkTvFApiSystem) hosted in
         * com.chijimlee.tyylauncher, updating both the hardware scaler and eMMC FLIP_MODE variable.
         *
         * @param context Context used to create package context for com.chijimlee.tyylauncher.
         * @param modeIndex Target hardware mirror mode index (0..3).
         * @return True if the MediaTek HAL wrapper executed without exception; false otherwise.
         */
        @JvmStatic
        fun applyHardwareMirrorMode(context: Context, modeIndex: Int): Boolean {
            return try {
                val pkgCtx = context.createPackageContext(
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

                try {
                    val spClass = Class.forName("android.os.SystemProperties")
                    val setMethod = spClass.getMethod("set", String::class.java, String::class.java)
                    setMethod.invoke(null, "vendor.mstar.mirrormode", modeIndex.toString())
                    setMethod.invoke(null, "persist.sys.flip_mode", modeIndex.toString())
                } catch (_: Exception) {
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Reads the current eMMC FLIP_MODE environment variable via MtkTvFApiSystem.
         *
         * @param context Context used to access com.chijimlee.tyylauncher classes.
         * @return Current FLIP_MODE string, or empty string if unavailable.
         */
        @JvmStatic
        fun readHardwareFlipMode(context: Context): String {
            return try {
                val pkgCtx = context.createPackageContext(
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
         * Ensures the Wanbo T2 Max optical reflection compensation (mode 1) is applied
         * automatically on the first boot after a USB firmware flash (where mboot.bin resets
         * FLIP_MODE to 0). Preserves any subsequent user changes across normal reboots.
         *
         * @param context Context used to read SharedPreferences and invoke the MediaTek HAL.
         */
        @JvmStatic
        fun ensureDefaultOpticalOrientation(context: Context) {
            try {
                val prefs = context.getSharedPreferences("wanbo_optical_prefs", Context.MODE_PRIVATE) ?: return
                val alreadyInitialized = prefs.getBoolean("initial_optical_flip_applied", false)
                if (!alreadyInitialized) {
                    val currentFlip = readHardwareFlipMode(context)
                    if (currentFlip.isEmpty() || currentFlip == "0") {
                        val applied = applyHardwareMirrorMode(context, 1)
                        if (applied && readHardwareFlipMode(context) == "1") {
                            prefs.edit().putBoolean("initial_optical_flip_applied", true).apply()
                        }
                    } else {
                        prefs.edit().putBoolean("initial_optical_flip_applied", true).apply()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Safely launches Keystone calibration using the provided context or default instance context.
     *
     * @param context Optional override context. If null, uses the instance default context.
     * @return True if launched successfully, false otherwise.
     */
    @JvmName("instanceLaunchKeystone")
    fun launchKeystone(context: Context? = defaultContext): Boolean {
        val targetContext = context ?: defaultContext ?: return false
        return Companion.launchKeystone(targetContext)
    }

    /**
     * Safely launches HDMI input switching using the provided context or default instance context.
     *
     * @param context Optional override context. If null, uses the instance default context.
     * @return True if launched successfully, false otherwise.
     */
    @JvmName("instanceLaunchHdmi")
    fun launchHdmi(context: Context? = defaultContext): Boolean {
        val targetContext = context ?: defaultContext ?: return false
        return Companion.launchHdmi(targetContext)
    }

    /**
     * Safely launches Settings using the provided context or default instance context.
     *
     * @param context Optional override context. If null, uses the instance default context.
     * @return True if launched successfully, false otherwise.
     */
    @JvmName("instanceLaunchSettings")
    fun launchSettings(context: Context? = defaultContext): Boolean {
        val targetContext = context ?: defaultContext ?: return false
        return Companion.launchSettings(targetContext)
    }

    /**
     * Creates an explicit Intent configured for Keystone / Trapezoid calibration.
     *
     * @return Explicit Intent configured with ComponentName and FLAG_ACTIVITY_NEW_TASK.
     */
    @JvmName("instanceCreateKeystoneIntent")
    fun createKeystoneIntent(): Intent = Companion.createKeystoneIntent()

    /**
     * Creates an explicit Intent configured for HDMI input switching.
     *
     * @return Explicit Intent configured with ComponentName and FLAG_ACTIVITY_NEW_TASK.
     */
    @JvmName("instanceCreateHdmiIntent")
    fun createHdmiIntent(): Intent = Companion.createHdmiIntent()

    /**
     * Creates an explicit Intent configured for Android TV settings.
     *
     * @return Explicit Intent configured with ComponentName and FLAG_ACTIVITY_NEW_TASK.
     */
    @JvmName("instanceCreateSettingsIntent")
    fun createSettingsIntent(): Intent = Companion.createSettingsIntent()
}
