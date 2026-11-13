package com.wanbo.settings.panes

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wanbo.settings.R
import java.util.Locale
import java.util.TimeZone

/**
 * Controller managing the "Sistema" settings pane.
 * Configures device name, clock formatting (24h/12h), regional time zone,
 * UI language preferences, sleep inactivity timers, and developer debugging switches.
 *
 * @param activity Host AppCompatActivity context.
 * @param container Parent container into which the pane layout is attached.
 */
class SystemPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null

    // UI Widgets: Device Name
    private lateinit var cardDeviceName: View
    private lateinit var textDeviceNameValue: TextView
    private lateinit var badgeDeviceName: TextView

    // UI Widgets: Date & Time
    private lateinit var cardTimeFormat: View
    private lateinit var badgeTimeFormat: TextView
    private lateinit var cardTimeZone: View
    private lateinit var textTimeZoneValue: TextView
    private lateinit var cardDateTimeAdvanced: View

    // UI Widgets: Language
    private lateinit var cardLanguage: View
    private lateinit var textLanguageSelected: TextView
    private lateinit var badgeLanguage: TextView

    // UI Widgets: Sleep Timer
    private lateinit var cardSleepTimer: View
    private lateinit var textSleepTimerValue: TextView
    private lateinit var badgeSleepTimer: TextView

    // UI Widgets: Developer Options
    private lateinit var cardAdbWifi: View
    private lateinit var badgeAdbWifi: TextView
    private lateinit var cardUsbDebugging: View
    private lateinit var badgeUsbDebugging: TextView

    private val prefs by lazy {
        activity.getSharedPreferences("wanbo_system_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_KEY_DEVICE_NAME = "key_device_name"
        private const val DEFAULT_DEVICE_NAME = "Wanbo-Projetor"

        private const val PREF_KEY_24H_FORMAT = "key_24h_format"
        private const val PREF_KEY_LANGUAGE_INDEX = "key_language_index"
        private const val PREF_KEY_SLEEP_TIMER_MINUTES = "key_sleep_timer_minutes"
        private const val PREF_KEY_ADB_WIFI = "key_adb_wifi"
        private const val PREF_KEY_USB_DEBUG = "key_usb_debug"

        /** Supported system UI language titles. */
        val SUPPORTED_LANGUAGES = arrayOf(
            "Portugues (Brasil)",
            "English (United States)",
            "Espanol (Latinoamerica)"
        )

        /** Sleep timer selectable options in minutes. */
        val SLEEP_TIMER_OPTIONS = intArrayOf(0, 15, 30, 60, 120)

        /** Sleep timer human-readable labels. */
        val SLEEP_TIMER_LABELS = arrayOf(
            "Desativado",
            "15 minutos",
            "30 minutos",
            "60 minutos (1 hora)",
            "120 minutos (2 horas)"
        )
    }

    /**
     * Inflates the System pane layout, binds child widgets, and synchronizes settings states.
     */
    fun attach() {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_system, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        setupListeners()
        refreshAllPreferences()
    }

    /**
     * Cleans up views and detaches the controller.
     */
    fun detach() {
        rootView = null
    }

    /**
     * Binds UI widget elements from the inflated pane layout.
     *
     * @param root Inflated pane view root.
     */
    private fun bindViews(root: View) {
        cardDeviceName = root.findViewById(R.id.card_device_name)
        textDeviceNameValue = root.findViewById(R.id.text_device_name_value)
        badgeDeviceName = root.findViewById(R.id.badge_device_name)

        cardTimeFormat = root.findViewById(R.id.card_time_format)
        badgeTimeFormat = root.findViewById(R.id.badge_time_format)
        cardTimeZone = root.findViewById(R.id.card_time_zone)
        textTimeZoneValue = root.findViewById(R.id.text_time_zone_value)
        cardDateTimeAdvanced = root.findViewById(R.id.card_date_time_advanced)

        cardLanguage = root.findViewById(R.id.card_language)
        textLanguageSelected = root.findViewById(R.id.text_language_selected)
        badgeLanguage = root.findViewById(R.id.badge_language)

        cardSleepTimer = root.findViewById(R.id.card_sleep_timer)
        textSleepTimerValue = root.findViewById(R.id.text_sleep_timer_value)
        badgeSleepTimer = root.findViewById(R.id.badge_sleep_timer)

        cardAdbWifi = root.findViewById(R.id.card_adb_wifi)
        badgeAdbWifi = root.findViewById(R.id.badge_adb_wifi)
        cardUsbDebugging = root.findViewById(R.id.card_usb_debugging)
        badgeUsbDebugging = root.findViewById(R.id.badge_usb_debugging)
    }

    /**
     * Applies tvOS scale elevation animations across interactive cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardDeviceName)
        applyFocusAnimation(cardTimeFormat)
        applyFocusAnimation(cardTimeZone)
        applyFocusAnimation(cardDateTimeAdvanced)
        applyFocusAnimation(cardLanguage)
        applyFocusAnimation(cardSleepTimer)
        applyFocusAnimation(cardAdbWifi)
        applyFocusAnimation(cardUsbDebugging)
    }

    /**
     * Configures click listeners across all system preferences cards.
     */
    private fun setupListeners() {
        cardDeviceName.setOnClickListener {
            showDeviceNameDialog()
        }

        cardTimeFormat.setOnClickListener {
            toggleTimeFormat()
        }

        cardTimeZone.setOnClickListener {
            openAndroidDateSettings()
        }

        cardDateTimeAdvanced.setOnClickListener {
            openAndroidDateSettings()
        }

        cardLanguage.setOnClickListener {
            showLanguageDialog()
        }

        cardSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }

        cardAdbWifi.setOnClickListener {
            toggleAdbWifi()
        }

        cardUsbDebugging.setOnClickListener {
            toggleUsbDebugging()
        }
    }

    /**
     * Refreshes and synchronizes all system UI widgets with current hardware and preference states.
     */
    fun refreshAllPreferences() {
        refreshDeviceName()
        refreshTimeFormat()
        refreshTimeZone()
        refreshLanguage()
        refreshSleepTimer()
        refreshDeveloperOptions()
    }

    /**
     * Reads saved projector device name and updates the header text.
     */
    private fun refreshDeviceName() {
        val name = prefs.getString(PREF_KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME
        textDeviceNameValue.text = name
    }

    /**
     * Opens an input dialog allowing the user to rename the projector device.
     */
    private fun showDeviceNameDialog() {
        val currentName = prefs.getString(PREF_KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME
        val editText = EditText(activity).apply {
            setText(currentName)
            setSelection(currentName.length)
            setTextColor(ContextCompat.getColor(activity, R.color.white))
            setBackgroundResource(R.drawable.bg_pane_card_selector)
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(activity)
            .setTitle("Nome do Projetor")
            .setMessage("Digite a nova identificacao para redes e Bluetooth:")
            .setView(editText)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString(PREF_KEY_DEVICE_NAME, newName).apply()
                    refreshDeviceName()
                    Toast.makeText(activity, "Nome atualizado: $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Synchronizes 24-hour clock formatting status from Settings or SharedPreferences.
     */
    private fun refreshTimeFormat() {
        val is24h = try {
            val systemVal = Settings.System.getString(activity.contentResolver, Settings.System.TIME_12_24)
            if (systemVal != null) systemVal == "24" else prefs.getBoolean(PREF_KEY_24H_FORMAT, true)
        } catch (_: Exception) {
            prefs.getBoolean(PREF_KEY_24H_FORMAT, true)
        }

        if (is24h) {
            badgeTimeFormat.text = "24h"
            badgeTimeFormat.setBackgroundResource(R.drawable.bg_badge_active)
            badgeTimeFormat.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
        } else {
            badgeTimeFormat.text = "12h"
            badgeTimeFormat.setBackgroundResource(R.drawable.bg_badge_inactive)
            badgeTimeFormat.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
        }
    }

    /**
     * Toggles clock display between 24-hour and 12-hour format.
     */
    private fun toggleTimeFormat() {
        val current = prefs.getBoolean(PREF_KEY_24H_FORMAT, true)
        val newTarget = !current
        prefs.edit().putBoolean(PREF_KEY_24H_FORMAT, newTarget).apply()

        try {
            Settings.System.putString(
                activity.contentResolver,
                Settings.System.TIME_12_24,
                if (newTarget) "24" else "12"
            )
        } catch (_: Exception) {
        }

        refreshTimeFormat()
        val formatLabel = if (newTarget) "Formato 24 horas ativado" else "Formato 12 horas (AM/PM) ativado"
        Toast.makeText(activity, formatLabel, Toast.LENGTH_SHORT).show()
    }

    /**
     * Reads current regional timezone and updates the display card.
     */
    private fun refreshTimeZone() {
        val tz = TimeZone.getDefault()
        val id = tz.id
        val display = tz.getDisplayName(false, TimeZone.SHORT, Locale.getDefault())
        textTimeZoneValue.text = "$id ($display)"
    }

    /**
     * Launches Android system Date & Time settings.
     */
    private fun openAndroidDateSettings() {
        try {
            val intent = Intent(Settings.ACTION_DATE_SETTINGS)
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Nao foi possivel abrir ajustes de data do sistema", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Reads currently selected language index and synchronizes display text.
     */
    private fun refreshLanguage() {
        val index = prefs.getInt(PREF_KEY_LANGUAGE_INDEX, 0).coerceIn(0, SUPPORTED_LANGUAGES.lastIndex)
        textLanguageSelected.text = SUPPORTED_LANGUAGES[index]
    }

    /**
     * Displays a dialog allowing user selection from supported language profiles.
     */
    private fun showLanguageDialog() {
        val currentIndex = prefs.getInt(PREF_KEY_LANGUAGE_INDEX, 0).coerceIn(0, SUPPORTED_LANGUAGES.lastIndex)

        AlertDialog.Builder(activity)
            .setTitle("Selecionar Idioma")
            .setSingleChoiceItems(SUPPORTED_LANGUAGES, currentIndex) { dialog, which ->
                prefs.edit().putInt(PREF_KEY_LANGUAGE_INDEX, which).apply()
                refreshLanguage()
                Toast.makeText(activity, "Idioma selecionado: ${SUPPORTED_LANGUAGES[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Reads sleep inactivity timer setting and updates UI label and badge.
     */
    private fun refreshSleepTimer() {
        val minutes = prefs.getInt(PREF_KEY_SLEEP_TIMER_MINUTES, 0)
        val index = SLEEP_TIMER_OPTIONS.indexOf(minutes).let { if (it >= 0) it else 0 }

        textSleepTimerValue.text = SLEEP_TIMER_LABELS[index]
        if (minutes > 0) {
            badgeSleepTimer.text = "${minutes}m"
            badgeSleepTimer.setBackgroundResource(R.drawable.bg_badge_active)
            badgeSleepTimer.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
        } else {
            badgeSleepTimer.text = "Desativado"
            badgeSleepTimer.setBackgroundResource(R.drawable.bg_badge_inactive)
            badgeSleepTimer.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
        }
    }

    /**
     * Displays a dialog allowing user selection of sleep inactivity timer durations.
     */
    private fun showSleepTimerDialog() {
        val currentMinutes = prefs.getInt(PREF_KEY_SLEEP_TIMER_MINUTES, 0)
        val currentIndex = SLEEP_TIMER_OPTIONS.indexOf(currentMinutes).let { if (it >= 0) it else 0 }

        AlertDialog.Builder(activity)
            .setTitle("Temporizador de Desligamento")
            .setSingleChoiceItems(SLEEP_TIMER_LABELS, currentIndex) { dialog, which ->
                val chosenMinutes = SLEEP_TIMER_OPTIONS[which]
                prefs.edit().putInt(PREF_KEY_SLEEP_TIMER_MINUTES, chosenMinutes).apply()
                refreshSleepTimer()
                Toast.makeText(activity, "Temporizador: ${SLEEP_TIMER_LABELS[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Reads developer switches (ADB Wi-Fi, USB debugging) and updates badges.
     */
    private fun refreshDeveloperOptions() {
        // ADB Wi-Fi
        val adbWifiEnabled = prefs.getBoolean(PREF_KEY_ADB_WIFI, false)
        if (adbWifiEnabled) {
            badgeAdbWifi.text = "Porta 5555"
            badgeAdbWifi.setBackgroundResource(R.drawable.bg_badge_active)
            badgeAdbWifi.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
        } else {
            badgeAdbWifi.text = "Desativado"
            badgeAdbWifi.setBackgroundResource(R.drawable.bg_badge_inactive)
            badgeAdbWifi.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
        }

        // USB Debugging
        val usbDebugEnabled = try {
            Settings.Global.getInt(activity.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            prefs.getBoolean(PREF_KEY_USB_DEBUG, false)
        }

        if (usbDebugEnabled) {
            badgeUsbDebugging.text = "Ativado"
            badgeUsbDebugging.setBackgroundResource(R.drawable.bg_badge_active)
            badgeUsbDebugging.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
        } else {
            badgeUsbDebugging.text = "Desativado"
            badgeUsbDebugging.setBackgroundResource(R.drawable.bg_badge_inactive)
            badgeUsbDebugging.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
        }
    }

    /**
     * Toggles ADB daemon over TCP/IP on port 5555.
     */
    private fun toggleAdbWifi() {
        val current = prefs.getBoolean(PREF_KEY_ADB_WIFI, false)
        val newTarget = !current
        prefs.edit().putBoolean(PREF_KEY_ADB_WIFI, newTarget).apply()

        try {
            val portValue = if (newTarget) "5555" else "-1"
            Runtime.getRuntime().exec(arrayOf("setprop", "service.adb.tcp.port", portValue))
            Runtime.getRuntime().exec(arrayOf("setprop", "ctl.restart", "adbd"))
        } catch (_: Exception) {
        }

        refreshDeveloperOptions()
        val msg = if (newTarget) "ADB Wi-Fi ativado na porta 5555" else "ADB Wi-Fi desativado"
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Toggles Android USB debugging state in Settings.Global.
     */
    private fun toggleUsbDebugging() {
        val current = try {
            Settings.Global.getInt(activity.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            prefs.getBoolean(PREF_KEY_USB_DEBUG, false)
        }
        val newTarget = !current

        try {
            Settings.Global.putInt(activity.contentResolver, Settings.Global.ADB_ENABLED, if (newTarget) 1 else 0)
        } catch (_: Exception) {
        }
        prefs.edit().putBoolean(PREF_KEY_USB_DEBUG, newTarget).apply()

        refreshDeveloperOptions()
        val msg = if (newTarget) "Depuracao USB ativada" else "Depuracao USB desativada"
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
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
