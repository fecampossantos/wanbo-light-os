package com.wanbo.settings.panes

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wanbo.settings.R

/**
 * Pure logic helper for Wi-Fi state calculations, IP address formatting,
 * signal strength mapping, and network encryption detection without Android framework dependencies.
 */
object WifiStateHelper {

    /**
     * Formats a 32-bit integer IPv4 address (little-endian as delivered by Android WifiInfo)
     * into standard dot-decimal notation (e.g. 192.168.1.100).
     *
     * @param ipAddress 32-bit integer representation of IPv4 address.
     * @return Formatted IP string or "0.0.0.0" if zero.
     */
    fun formatIpAddress(ipAddress: Int): String {
        if (ipAddress == 0) return "0.0.0.0"
        val byte0 = ipAddress and 0xFF
        val byte1 = (ipAddress shr 8) and 0xFF
        val byte2 = (ipAddress shr 16) and 0xFF
        val byte3 = (ipAddress shr 24) and 0xFF
        return "$byte0.$byte1.$byte2.$byte3"
    }

    /**
     * Calculates the signal level (from 0 to numLevels - 1) based on raw RSSI (dBm).
     * Follows standard Android Wi-Fi signal thresholds (-100 dBm to -55 dBm).
     *
     * @param rssi Received signal strength indicator in dBm.
     * @param numLevels Total number of discrete levels (default 5, yielding 0 to 4).
     * @return Discrete signal level clamped between 0 and numLevels - 1.
     */
    fun calculateSignalLevel(rssi: Int, numLevels: Int = 5): Int {
        val minRssi = -100
        val maxRssi = -55
        return when {
            rssi <= minRssi -> 0
            rssi >= maxRssi -> numLevels - 1
            else -> {
                val inputRange = (maxRssi - minRssi).toFloat()
                val outputRange = (numLevels - 1).toFloat()
                ((rssi - minRssi).toFloat() * outputRange / inputRange).toInt().coerceIn(0, numLevels - 1)
            }
        }
    }

    /**
     * Converts a raw RSSI reading (in dBm) into a percentage scale (0 to 100%).
     *
     * @param rssi Received signal strength indicator in dBm.
     * @return Percentage clamped between 0 and 100.
     */
    fun rssiToPercentage(rssi: Int): Int {
        val minRssi = -100
        val maxRssi = -50
        return when {
            rssi <= minRssi -> 0
            rssi >= maxRssi -> 100
            else -> (((rssi - minRssi) * 100) / (maxRssi - minRssi)).coerceIn(0, 100)
        }
    }

    /**
     * Returns a human-readable signal quality description in Portuguese.
     *
     * @param level Signal level from 0 to 4.
     * @return Descriptive label for the signal quality.
     */
    fun getSignalStrengthLabel(level: Int): String {
        return when (level) {
            4 -> "Excelente"
            3 -> "Bom"
            2 -> "Razoavel"
            1 -> "Fraco"
            else -> "Muito Fraco"
        }
    }

    /**
     * Sanitizes raw SSID strings returned by Android WifiInfo or ScanResult by removing surrounding quotes
     * and converting invalid/placeholder values to a fallback label.
     *
     * @param rawSsid Raw SSID string.
     * @param fallback Default string if null, empty, or placeholder.
     * @return Sanitized clean SSID string.
     */
    fun sanitizeSsid(rawSsid: String?, fallback: String = "Desconectado"): String {
        if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>" || rawSsid == "0x") {
            return fallback
        }
        val trimmed = rawSsid.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    /**
     * Evaluates Wi-Fi AP capabilities string to determine if network requires encryption credentials.
     *
     * @param capabilities Raw capabilities string from ScanResult (e.g. "[WPA2-PSK-CCMP][ESS]").
     * @return True if encrypted with WPA, WEP, EAP, or PSK; false if open.
     */
    fun isEncrypted(capabilities: String?): Boolean {
        if (capabilities.isNullOrBlank()) return false
        val caps = capabilities.uppercase()
        return caps.contains("WPA") || caps.contains("WEP") || caps.contains("EAP") || caps.contains("PSK")
    }

    /**
     * Returns security classification label in Portuguese for display.
     *
     * @param capabilities Raw capabilities string from ScanResult.
     * @return "Protegida" or "Aberta".
     */
    fun getSecurityLabel(capabilities: String?): String {
        return if (isEncrypted(capabilities)) "Protegida" else "Aberta"
    }
}

/**
 * Pane controller managing Wi-Fi state, active connection display, power toggling,
 * scanned network discovery, and password authentication for WanboSettings.
 *
 * @param activity Host AppCompatActivity.
 * @param container Target container ViewGroup into which the pane layout is inflated.
 */
class WifiPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null
    private var wifiManager: WifiManager? = null
    private var wifiReceiver: BroadcastReceiver? = null
    private val scanPollHandler = Handler(Looper.getMainLooper())

    companion object {
        /**
         * Ensures Android 9 Pie Wi-Fi scan prerequisites are satisfied by enabling
         * High Accuracy Location Mode in Settings.Secure, disabling scan throttling in
         * Settings.Global, and ensuring location permissions are granted.
         *
         * @param activity Host Activity context.
         */
        @JvmStatic
        fun ensureWifiScanPrerequisites(activity: Activity) {
            try {
                Settings.Secure.putInt(
                    activity.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                )
            } catch (_: Exception) {
            }
            try {
                Settings.Global.putInt(activity.contentResolver, "wifi_scan_always_enabled", 1)
                Settings.Global.putInt(activity.contentResolver, "wifi_scan_throttle_enabled", 0)
            } catch (_: Exception) {
            }
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    val fineGranted = activity.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted = activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!fineGranted || !coarseGranted) {
                        activity.requestPermissions(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            1001
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // UI Widgets
    private lateinit var cardWifiStatus: View
    private lateinit var wifiStatusBadge: TextView
    private lateinit var wifiStatusSsid: TextView
    private lateinit var wifiStatusIp: TextView
    private lateinit var wifiStatusSignal: TextView

    private lateinit var cardWifiPower: View
    private lateinit var wifiPowerState: TextView

    private lateinit var cardWifiRescan: View
    private lateinit var wifiRescanText: TextView
    private lateinit var wifiScanningIndicator: TextView
    private lateinit var wifiNetworksContainer: LinearLayout
    private lateinit var wifiEmptyNetworksText: TextView

    /**
     * Inflates the Wi-Fi layout, binds UI views, registers system broadcast listeners,
     * and performs initial Wi-Fi status queries.
     */
    fun attach() {
        ensureWifiScanPrerequisites(activity)
        wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_wifi, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        setupListeners()
        registerWifiReceiver()
        refreshWifiState()
        if (wifiManager?.isWifiEnabled == true) {
            triggerNetworkScan()
        }
    }

    /**
     * Detaches the controller, unregisters broadcast listeners, and cleans up references.
     */
    fun detach() {
        scanPollHandler.removeCallbacksAndMessages(null)
        unregisterWifiReceiver()
        rootView = null
    }

    /**
     * Binds view elements from the inflated pane layout.
     *
     * @param root Inflated root view.
     */
    private fun bindViews(root: View) {
        cardWifiStatus = root.findViewById(R.id.card_wifi_status)
        wifiStatusBadge = root.findViewById(R.id.wifi_status_badge)
        wifiStatusSsid = root.findViewById(R.id.wifi_status_ssid)
        wifiStatusIp = root.findViewById(R.id.wifi_status_ip)
        wifiStatusSignal = root.findViewById(R.id.wifi_status_signal)

        cardWifiPower = root.findViewById(R.id.card_wifi_power)
        wifiPowerState = root.findViewById(R.id.wifi_power_state)

        cardWifiRescan = root.findViewById(R.id.card_wifi_rescan)
        wifiRescanText = root.findViewById(R.id.wifi_rescan_text)
        wifiScanningIndicator = root.findViewById(R.id.wifi_scanning_indicator)
        wifiNetworksContainer = root.findViewById(R.id.wifi_networks_container)
        wifiEmptyNetworksText = root.findViewById(R.id.wifi_empty_networks_text)
    }

    /**
     * Configures tvOS smooth scale and elevation animations for focusable cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardWifiStatus)
        applyFocusAnimation(cardWifiPower)
        applyFocusAnimation(cardWifiRescan)
    }

    /**
     * Attaches interactive click listeners for Wi-Fi power toggling and manual network scans.
     */
    private fun setupListeners() {
        cardWifiPower.setOnClickListener {
            toggleWifiPower()
        }

        cardWifiRescan.setOnClickListener {
            triggerNetworkScan()
        }
    }

    /**
     * Toggles the Wi-Fi adapter power state using WifiManager.
     */
    private fun toggleWifiPower() {
        val manager = wifiManager ?: return
        val currentEnabled = manager.isWifiEnabled
        val newTarget = !currentEnabled
        try {
            manager.isWifiEnabled = newTarget
            wifiPowerState.text = if (newTarget) "Ativando..." else "Desativando..."
        } catch (e: Exception) {
            Toast.makeText(activity, "Erro ao alterar estado do Wi-Fi", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Initiates an asynchronous Wi-Fi scan and updates UI indicator.
     */
    private fun triggerNetworkScan() {
        ensureWifiScanPrerequisites(activity)
        val manager = wifiManager ?: return
        if (!manager.isWifiEnabled) {
            Toast.makeText(activity, "Ative o Wi-Fi para buscar redes", Toast.LENGTH_SHORT).show()
            return
        }
        wifiScanningIndicator.visibility = View.VISIBLE
        wifiEmptyNetworksText.visibility = View.GONE
        try {
            manager.startScan()
        } catch (e: Exception) {
            wifiScanningIndicator.visibility = View.GONE
        }
        scanPollHandler.removeCallbacksAndMessages(null)
        for (delayMs in listOf(1200L, 2500L, 4500L)) {
            scanPollHandler.postDelayed({
                if (rootView != null && manager.isWifiEnabled) {
                    val currentResults = try {
                        manager.scanResults
                    } catch (_: Exception) {
                        null
                    }
                    if (!currentResults.isNullOrEmpty() || delayMs == 4500L) {
                        updateScannedNetworks(currentResults)
                    }
                }
            }, delayMs)
        }
    }

    /**
     * Refreshes the full Wi-Fi status: active connection parameters, power state, and scanned networks.
     */
    fun refreshWifiState() {
        val manager = wifiManager ?: return
        val isEnabled = manager.isWifiEnabled

        if (isEnabled) {
            wifiPowerState.text = "Ativado"
            wifiPowerState.setTextColor(ContextCompat.getColor(activity, R.color.cyan_highlight))
            updateActiveConnection(manager.connectionInfo)
            updateScannedNetworks(manager.scanResults)
        } else {
            wifiPowerState.text = "Desativado"
            wifiPowerState.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
            renderDisconnectedState()
            wifiNetworksContainer.removeAllViews()
            wifiScanningIndicator.visibility = View.GONE
            wifiEmptyNetworksText.visibility = View.VISIBLE
            wifiEmptyNetworksText.text = "Wi-Fi desativado"
        }
    }

    /**
     * Updates active Wi-Fi connection card with SSID, IP, signal level, and status badge.
     *
     * @param info Active WifiInfo connection metadata.
     */
    private fun updateActiveConnection(info: WifiInfo?) {
        val ssid = WifiStateHelper.sanitizeSsid(info?.ssid, fallback = "Desconectado")
        val ipInt = info?.ipAddress ?: 0
        val isConnected = info != null && ipInt != 0 && ssid != "Desconectado"

        if (isConnected) {
            val ipFormatted = WifiStateHelper.formatIpAddress(ipInt)
            val level = WifiStateHelper.calculateSignalLevel(info?.rssi ?: -100)
            val pct = WifiStateHelper.rssiToPercentage(info?.rssi ?: -100)
            val levelLabel = WifiStateHelper.getSignalStrengthLabel(level)

            wifiStatusBadge.text = "Conectado"
            wifiStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
            wifiStatusBadge.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))

            wifiStatusSsid.text = ssid
            wifiStatusIp.text = "IP: $ipFormatted"
            wifiStatusSignal.text = "Sinal: $level/4 ($levelLabel, $pct%)"
        } else {
            renderDisconnectedState()
        }
    }

    /**
     * Resets active connection card to disconnected placeholder state.
     */
    private fun renderDisconnectedState() {
        wifiStatusBadge.text = "Desconectado"
        wifiStatusBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
        wifiStatusBadge.setTextColor(ContextCompat.getColor(activity, R.color.status_disconnected))

        wifiStatusSsid.text = "Nenhuma rede conectada"
        wifiStatusIp.text = "IP: Indisponivel"
        wifiStatusSignal.text = "Sinal: --"
    }

    /**
     * Populates scanned access points into the scrollable networks container.
     *
     * @param results List of ScanResult instances returned by the Wi-Fi subsystem.
     */
    private fun updateScannedNetworks(results: List<ScanResult>?) {
        wifiScanningIndicator.visibility = View.GONE
        wifiNetworksContainer.removeAllViews()

        if (results.isNullOrEmpty()) {
            wifiEmptyNetworksText.visibility = View.VISIBLE
            wifiEmptyNetworksText.text = "Nenhuma rede encontrada por perto"
            return
        }

        wifiEmptyNetworksText.visibility = View.GONE

        // Group by SSID to avoid duplicates and select the strongest signal
        val uniqueNetworks = results
            .filter { !it.SSID.isNullOrBlank() }
            .groupBy { it.SSID.trim() }
            .mapValues { (_, group) -> group.maxByOrNull { it.level } ?: group.first() }
            .values
            .sortedByDescending { it.level }

        val inflater = LayoutInflater.from(activity)

        for (ap in uniqueNetworks) {
            val itemView = inflater.inflate(R.layout.item_wifi_network, wifiNetworksContainer, false)
            val textSsid = itemView.findViewById<TextView>(R.id.text_network_ssid)
            val textSecurity = itemView.findViewById<TextView>(R.id.text_network_security)
            val textSignal = itemView.findViewById<TextView>(R.id.text_network_signal)

            val cleanSsid = ap.SSID.trim()
            val isEncrypted = WifiStateHelper.isEncrypted(ap.capabilities)
            val securityLabel = WifiStateHelper.getSecurityLabel(ap.capabilities)
            val level = WifiStateHelper.calculateSignalLevel(ap.level)

            textSsid.text = cleanSsid
            textSecurity.text = securityLabel
            textSignal.text = "Sinal: $level/4"

            applyFocusAnimation(itemView)

            itemView.setOnClickListener {
                if (isEncrypted) {
                    showPasswordDialog(cleanSsid, ap.capabilities)
                } else {
                    connectToOpenNetwork(cleanSsid)
                }
            }

            wifiNetworksContainer.addView(itemView)
        }
    }

    /**
     * Connects directly to an open (unencrypted) Wi-Fi access point.
     *
     * @param ssid Target access point SSID.
     */
    private fun connectToOpenNetwork(ssid: String) {
        val manager = wifiManager ?: return
        try {
            val quotedSsid = "\"$ssid\""
            val config = WifiConfiguration().apply {
                SSID = quotedSsid
                hiddenSSID = false
                status = WifiConfiguration.Status.ENABLED
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            var netId = manager.addNetwork(config)
            if (netId == -1) {
                val existing = try {
                    manager.configuredNetworks?.firstOrNull { it.SSID == quotedSsid || it.SSID == ssid }
                } catch (_: Exception) {
                    null
                }
                if (existing != null) {
                    config.networkId = existing.networkId
                    netId = manager.updateNetwork(config).let { if (it != -1) it else existing.networkId }
                }
            }
            if (netId != -1) {
                manager.disconnect()
                manager.enableNetwork(netId, true)
                manager.saveConfiguration()
                manager.reconnect()
                Toast.makeText(activity, "Conectando a $ssid...", Toast.LENGTH_SHORT).show()
                scheduleConnectionRefresh()
            } else {
                Toast.makeText(activity, "Falha ao adicionar rede $ssid", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Falha ao conectar a $ssid", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Displays a tvOS styled modal dialog prompting for Wi-Fi WPA/WEP credentials.
     *
     * @param ssid Target access point SSID.
     * @param capabilities Capabilities string indicating security cipher.
     */
    private fun showPasswordDialog(ssid: String, capabilities: String) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_wifi_password, null)
        val textTitle = dialogView.findViewById<TextView>(R.id.dialog_wifi_title)
        val textSecurity = dialogView.findViewById<TextView>(R.id.dialog_wifi_security)
        val editPassword = dialogView.findViewById<EditText>(R.id.edit_wifi_password)

        textTitle.text = "Conectar a $ssid"
        textSecurity.text = "Rede protegida (${WifiStateHelper.getSecurityLabel(capabilities)})"

        AlertDialog.Builder(activity)
            .setView(dialogView)
            .setPositiveButton("Conectar") { _, _ ->
                val password = editPassword.text.toString().trim()
                if (password.isNotEmpty()) {
                    connectToEncryptedNetwork(ssid, password, capabilities)
                } else {
                    Toast.makeText(activity, "Senha nao pode ser vazia", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Connects to a WPA/WPA2/WEP encrypted network with supplied password credentials.
     *
     * @param ssid Target access point SSID.
     * @param password Wi-Fi password.
     * @param capabilities Raw AP security capabilities string.
     */
    private fun connectToEncryptedNetwork(ssid: String, password: String, capabilities: String = "WPA2") {
        val manager = wifiManager ?: return
        try {
            val quotedSsid = "\"$ssid\""
            val capsUpper = capabilities.uppercase()
            val config = WifiConfiguration().apply {
                SSID = quotedSsid
                hiddenSSID = false
                status = WifiConfiguration.Status.ENABLED
                if (capsUpper.contains("WEP") && !capsUpper.contains("WPA")) {
                    wepKeys[0] = "\"$password\""
                    wepTxKeyIndex = 0
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                } else {
                    preSharedKey = "\"$password\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                    allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                    allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                    allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                    allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                }
            }
            var netId = manager.addNetwork(config)
            if (netId == -1) {
                val existing = try {
                    manager.configuredNetworks?.firstOrNull { it.SSID == quotedSsid || it.SSID == ssid }
                } catch (_: Exception) {
                    null
                }
                if (existing != null) {
                    config.networkId = existing.networkId
                    netId = manager.updateNetwork(config).let { if (it != -1) it else existing.networkId }
                }
            }
            if (netId != -1) {
                manager.disconnect()
                manager.enableNetwork(netId, true)
                manager.saveConfiguration()
                manager.reconnect()
                Toast.makeText(activity, "Conectando a $ssid...", Toast.LENGTH_SHORT).show()
                scheduleConnectionRefresh()
            } else {
                Toast.makeText(activity, "Falha ao adicionar rede $ssid", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Falha ao configurar rede $ssid", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Schedules delayed refreshes of the active Wi-Fi connection card after initiating a connection.
     */
    private fun scheduleConnectionRefresh() {
        for (delayMs in listOf(2000L, 4500L, 7000L)) {
            scanPollHandler.postDelayed({
                val manager = wifiManager
                if (rootView != null && manager != null && manager.isWifiEnabled) {
                    updateActiveConnection(manager.connectionInfo)
                }
            }, delayMs)
        }
    }

    /**
     * Registers system broadcast receiver listening for Wi-Fi state changes and scan results.
     */
    private fun registerWifiReceiver() {
        if (wifiReceiver != null) return

        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        refreshWifiState()
                    }
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        val manager = wifiManager ?: return
                        if (manager.isWifiEnabled) {
                            updateScannedNetworks(manager.scanResults)
                        }
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                        val manager = wifiManager ?: return
                        if (networkInfo != null && networkInfo.isConnected) {
                            updateActiveConnection(manager.connectionInfo)
                        } else {
                            renderDisconnectedState()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        activity.registerReceiver(wifiReceiver, filter)
    }

    /**
     * Safely unregisters the Wi-Fi broadcast receiver if currently active.
     */
    private fun unregisterWifiReceiver() {
        wifiReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            wifiReceiver = null
        }
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
