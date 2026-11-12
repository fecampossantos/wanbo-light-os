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
         * Catches [ActivityNotFoundException] and notifies the user via Toast without crashing.
         *
         * @param context Context used to invoke startActivity and display Toast notifications.
         * @return True if the activity was launched successfully; false otherwise.
         */
        @JvmStatic
        fun launchKeystone(context: Context): Boolean {
            return safeLaunch(context, createKeystoneIntent(), MSG_KEYSTONE_NOT_FOUND)
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
         * Safely launches the Projector / Android TV settings application.
         * First attempts com.android.tv.settings.MainActivity. If not found, falls back
         * defensively to platform Settings.ACTION_SETTINGS before showing a Toast.
         *
         * @param context Context used to invoke startActivity and display Toast notifications.
         * @return True if the activity was launched successfully; false otherwise.
         */
        @JvmStatic
        fun launchSettings(context: Context): Boolean {
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
    }

    /**
     * Safely launches Keystone calibration using the provided context or default instance context.
     *
     * @param context Optional override context. If null, uses the instance default context.
     * @return True if launched successfully, false otherwise.
     */
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
    fun launchSettings(context: Context? = defaultContext): Boolean {
        val targetContext = context ?: defaultContext ?: return false
        return Companion.launchSettings(targetContext)
    }

    /**
     * Creates an explicit Intent configured for Keystone / Trapezoid calibration.
     *
     * @return Explicit Intent configured with ComponentName and FLAG_ACTIVITY_NEW_TASK.
     */
    fun createKeystoneIntent(): Intent = Companion.createKeystoneIntent()

    /**
     * Creates an explicit Intent configured for HDMI input switching.
     *
     * @return Explicit Intent configured with ComponentName and FLAG_ACTIVITY_NEW_TASK.
     */
    fun createHdmiIntent(): Intent = Companion.createHdmiIntent()

    /**
     * Creates an explicit Intent configured for Android TV settings.
     *
     * @return Explicit Intent configured with ComponentName and FLAG_ACTIVITY_NEW_TASK.
     */
    fun createSettingsIntent(): Intent = Companion.createSettingsIntent()
}
