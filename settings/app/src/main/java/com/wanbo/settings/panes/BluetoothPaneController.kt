package com.wanbo.settings.panes

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wanbo.settings.R

/**
 * High-level categories for classified Bluetooth accessories.
 *
 * @property displayName Portuguese description displayed on UI badges.
 */
enum class BluetoothCategory(val displayName: String) {
    AUDIO("Audio"),
    REMOTE("Controle Remoto"),
    OTHER("Outros")
}

/**
 * Pure logic helper for categorizing Bluetooth accessories and classifying devices
 * into audio outputs, remote controls / peripherals, and miscellaneous hardware.
 */
object BluetoothStateHelper {

    /** Bluetooth major device class: Audio / Video peripherals. */
    const val MAJOR_AUDIO_VIDEO = 0x0400

    /** Bluetooth major device class: Peripheral devices (HID, remotes, keyboards). */
    const val MAJOR_PERIPHERAL = 0x0500

    /** Bluetooth major device class: Uncategorized hardware. */
    const val MAJOR_UNCATEGORIZED = 0x1F00

    /** Specific device class: Headphones. */
    const val DEVICE_AUDIO_HEADPHONES = 0x0418

    /** Specific device class: Loudspeaker / Soundbar. */
    const val DEVICE_AUDIO_LOUDSPEAKER = 0x0414

    /** Specific device class: Wearable headset with mic. */
    const val DEVICE_AUDIO_WEARABLE_HEADSET = 0x0404

    /** Specific device class: Keyboard. */
    const val DEVICE_PERIPHERAL_KEYBOARD = 0x0540

    /** Specific device class: Pointing device (Mouse, Trackpad). */
    const val DEVICE_PERIPHERAL_POINTING = 0x0580

    /** Specific device class: Keyboard and pointing combo. */
    const val DEVICE_PERIPHERAL_COMBO = 0x05C0

    /** Specific device class: Remote control. */
    const val DEVICE_PERIPHERAL_REMOTE = 0x0584

    /**
     * Classifies a Bluetooth device based on its major class, specific class, and advertised name.
     *
     * @param majorClass Major device class integer.
     * @param deviceClass Specific device class integer.
     * @param deviceName Optional advertised Bluetooth device name.
     * @return Resolved BluetoothCategory constant.
     */
    fun classifyDevice(
        majorClass: Int,
        deviceClass: Int = 0,
        deviceName: String? = null
    ): BluetoothCategory {
        val nameLower = deviceName?.lowercase() ?: ""

        // Check device name keywords first for projector accessories
        if (nameLower.contains("remote") ||
            nameLower.contains("controle") ||
            nameLower.contains("wanbo") ||
            nameLower.contains("keyboard") ||
            nameLower.contains("teclado") ||
            nameLower.contains("mouse") ||
            nameLower.contains("gamepad")) {
            return BluetoothCategory.REMOTE
        }

        if (nameLower.contains("headphone") ||
            nameLower.contains("fone") ||
            nameLower.contains("speaker") ||
            nameLower.contains("soundbar") ||
            nameLower.contains("audio") ||
            nameLower.contains("jbl") ||
            nameLower.contains("buds")) {
            return BluetoothCategory.AUDIO
        }

        // Check major class
        if (majorClass == MAJOR_AUDIO_VIDEO) {
            return BluetoothCategory.AUDIO
        }
        if (majorClass == MAJOR_PERIPHERAL) {
            return BluetoothCategory.REMOTE
        }

        // Check specific class
        if (deviceClass == DEVICE_AUDIO_HEADPHONES ||
            deviceClass == DEVICE_AUDIO_LOUDSPEAKER ||
            deviceClass == DEVICE_AUDIO_WEARABLE_HEADSET) {
            return BluetoothCategory.AUDIO
        }
        if (deviceClass == DEVICE_PERIPHERAL_KEYBOARD ||
            deviceClass == DEVICE_PERIPHERAL_POINTING ||
            deviceClass == DEVICE_PERIPHERAL_COMBO ||
            deviceClass == DEVICE_PERIPHERAL_REMOTE) {
            return BluetoothCategory.REMOTE
        }

        return BluetoothCategory.OTHER
    }

    /**
     * Returns Portuguese label for given category.
     *
     * @param category Target BluetoothCategory.
     * @return Display name in Portuguese.
     */
    fun getCategoryLabel(category: BluetoothCategory): String {
        return category.displayName
    }

    /**
     * Checks whether device represents an audio playback sink (speaker, headphone, soundbar).
     *
     * @param majorClass Major device class integer.
     * @param deviceClass Specific device class integer.
     * @param deviceName Optional advertised Bluetooth device name.
     * @return True if classified as audio device.
     */
    fun isAudioDevice(majorClass: Int, deviceClass: Int = 0, deviceName: String? = null): Boolean {
        return classifyDevice(majorClass, deviceClass, deviceName) == BluetoothCategory.AUDIO
    }

    /**
     * Checks whether device represents an input or remote peripheral.
     *
     * @param majorClass Major device class integer.
     * @param deviceClass Specific device class integer.
     * @param deviceName Optional advertised Bluetooth device name.
     * @return True if classified as remote control or input peripheral.
     */
    fun isRemoteDevice(majorClass: Int, deviceClass: Int = 0, deviceName: String? = null): Boolean {
        return classifyDevice(majorClass, deviceClass, deviceName) == BluetoothCategory.REMOTE
    }
}

/**
 * Pane controller managing Bluetooth adapter power, paired accessories (Wanbo remote, soundbars, headphones),
 * nearby device discovery, and pairing lifecycle.
 *
 * @param activity Host AppCompatActivity.
 * @param container Target container ViewGroup into which the pane layout is inflated.
 */
class BluetoothPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private val discoveredDevices = LinkedHashMap<String, BluetoothDevice>()

    // UI Widgets
    private lateinit var cardBluetoothPower: View
    private lateinit var bluetoothPowerState: TextView

    private lateinit var cardBluetoothScan: View
    private lateinit var bluetoothScanState: TextView

    private lateinit var bluetoothEmptyPairedText: TextView
    private lateinit var bluetoothPairedContainer: LinearLayout

    private lateinit var bluetoothScanningIndicator: TextView
    private lateinit var bluetoothDiscoveredContainer: LinearLayout
    private lateinit var bluetoothEmptyDiscoveredText: TextView

    /**
     * Inflates the Bluetooth layout, binds UI views, registers broadcast listeners,
     * and performs initial status reflection.
     */
    fun attach() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_bluetooth, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        setupListeners()
        registerBluetoothReceiver()
        refreshBluetoothState()
    }

    /**
     * Detaches the controller, cancels discovery, unregisters listeners, and releases references.
     */
    fun detach() {
        bluetoothAdapter?.let {
            if (it.isDiscovering) {
                try {
                    it.cancelDiscovery()
                } catch (_: Exception) {
                }
            }
        }
        unregisterBluetoothReceiver()
        discoveredDevices.clear()
        rootView = null
    }

    /**
     * Binds view elements from the inflated pane layout.
     *
     * @param root Inflated root view.
     */
    private fun bindViews(root: View) {
        cardBluetoothPower = root.findViewById(R.id.card_bluetooth_power)
        bluetoothPowerState = root.findViewById(R.id.bluetooth_power_state)

        cardBluetoothScan = root.findViewById(R.id.card_bluetooth_scan)
        bluetoothScanState = root.findViewById(R.id.bluetooth_scan_state)

        bluetoothEmptyPairedText = root.findViewById(R.id.bluetooth_empty_paired_text)
        bluetoothPairedContainer = root.findViewById(R.id.bluetooth_paired_container)

        bluetoothScanningIndicator = root.findViewById(R.id.bluetooth_scanning_indicator)
        bluetoothDiscoveredContainer = root.findViewById(R.id.bluetooth_discovered_container)
        bluetoothEmptyDiscoveredText = root.findViewById(R.id.bluetooth_empty_discovered_text)
    }

    /**
     * Configures tvOS smooth scale and elevation animations for focusable cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardBluetoothPower)
        applyFocusAnimation(cardBluetoothScan)
    }

    /**
     * Attaches interactive click listeners for Bluetooth power toggling and discovery scanning.
     */
    private fun setupListeners() {
        cardBluetoothPower.setOnClickListener {
            toggleBluetoothPower()
        }

        cardBluetoothScan.setOnClickListener {
            toggleDiscovery()
        }
    }

    /**
     * Toggles Bluetooth adapter power state.
     */
    private fun toggleBluetoothPower() {
        val adapter = bluetoothAdapter ?: run {
            Toast.makeText(activity, "Bluetooth nao disponivel no dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (adapter.isEnabled) {
                adapter.disable()
                bluetoothPowerState.text = "Desativando..."
            } else {
                adapter.enable()
                bluetoothPowerState.text = "Ativando..."
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Erro ao alterar estado do Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggles nearby Bluetooth accessory discovery scan.
     */
    private fun toggleDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Toast.makeText(activity, "Ative o Bluetooth para buscar dispositivos", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
                bluetoothScanState.text = "Buscar"
                bluetoothScanningIndicator.visibility = View.GONE
            } else {
                discoveredDevices.clear()
                bluetoothDiscoveredContainer.removeAllViews()
                bluetoothEmptyDiscoveredText.visibility = View.GONE
                bluetoothScanningIndicator.visibility = View.VISIBLE
                bluetoothScanState.text = "Parar"
                adapter.startDiscovery()
            }
        } catch (e: Exception) {
            bluetoothScanningIndicator.visibility = View.GONE
            bluetoothScanState.text = "Buscar"
        }
    }

    /**
     * Refreshes the full Bluetooth state: power status, paired accessories, and discovery state.
     */
    fun refreshBluetoothState() {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            bluetoothPowerState.text = "Indisponivel"
            bluetoothPowerState.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
            bluetoothEmptyPairedText.visibility = View.VISIBLE
            bluetoothEmptyPairedText.text = "Hardware Bluetooth nao encontrado"
            return
        }

        if (adapter.isEnabled) {
            bluetoothPowerState.text = "Ativado"
            bluetoothPowerState.setTextColor(ContextCompat.getColor(activity, R.color.cyan_highlight))
            renderPairedDevices()

            if (adapter.isDiscovering) {
                bluetoothScanState.text = "Parar"
                bluetoothScanningIndicator.visibility = View.VISIBLE
            } else {
                bluetoothScanState.text = "Buscar"
                bluetoothScanningIndicator.visibility = View.GONE
            }
        } else {
            bluetoothPowerState.text = "Desativado"
            bluetoothPowerState.setTextColor(ContextCompat.getColor(activity, R.color.cool_slate))
            bluetoothPairedContainer.removeAllViews()
            bluetoothEmptyPairedText.visibility = View.VISIBLE
            bluetoothEmptyPairedText.text = "Bluetooth desativado"
            bluetoothDiscoveredContainer.removeAllViews()
            bluetoothScanningIndicator.visibility = View.GONE
            bluetoothEmptyDiscoveredText.visibility = View.VISIBLE
            bluetoothEmptyDiscoveredText.text = "Ative o Bluetooth para ver acessorios"
        }
    }

    /**
     * Renders bonded Bluetooth devices into the paired accessories container.
     */
    private fun renderPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        bluetoothPairedContainer.removeAllViews()

        val bonded = try {
            adapter.bondedDevices
        } catch (e: Exception) {
            null
        }

        if (bonded.isNullOrEmpty()) {
            bluetoothEmptyPairedText.visibility = View.VISIBLE
            bluetoothEmptyPairedText.text = "Nenhum dispositivo Bluetooth pareado"
            return
        }

        bluetoothEmptyPairedText.visibility = View.GONE
        val inflater = LayoutInflater.from(activity)

        for (device in bonded) {
            val itemView = inflater.inflate(R.layout.item_bluetooth_device, bluetoothPairedContainer, false)
            val textName = itemView.findViewById<TextView>(R.id.text_device_name)
            val textCategory = itemView.findViewById<TextView>(R.id.text_device_category)
            val textAddress = itemView.findViewById<TextView>(R.id.text_device_address)
            val textAction = itemView.findViewById<TextView>(R.id.text_device_action)

            val name = device.name ?: "Dispositivo Bluetooth"
            val btClass = device.bluetoothClass
            val majorClass = btClass?.majorDeviceClass ?: BluetoothStateHelper.MAJOR_UNCATEGORIZED
            val deviceClass = btClass?.deviceClass ?: 0
            val category = BluetoothStateHelper.classifyDevice(majorClass, deviceClass, name)

            textName.text = name
            textCategory.text = "[${category.displayName}]"
            textAddress.text = device.address
            textAction.text = "Opcoes"

            applyFocusAnimation(itemView)

            itemView.setOnClickListener {
                showPairedDeviceDialog(device)
            }

            bluetoothPairedContainer.addView(itemView)
        }
    }

    /**
     * Renders newly discovered Bluetooth devices into the nearby accessories container.
     */
    private fun renderDiscoveredDevices() {
        bluetoothDiscoveredContainer.removeAllViews()

        if (discoveredDevices.isEmpty()) {
            bluetoothEmptyDiscoveredText.visibility = View.VISIBLE
            bluetoothEmptyDiscoveredText.text = "Procurando dispositivos proximos..."
            return
        }

        bluetoothEmptyDiscoveredText.visibility = View.GONE
        val inflater = LayoutInflater.from(activity)

        for (device in discoveredDevices.values) {
            val itemView = inflater.inflate(R.layout.item_bluetooth_device, bluetoothDiscoveredContainer, false)
            val textName = itemView.findViewById<TextView>(R.id.text_device_name)
            val textCategory = itemView.findViewById<TextView>(R.id.text_device_category)
            val textAddress = itemView.findViewById<TextView>(R.id.text_device_address)
            val textAction = itemView.findViewById<TextView>(R.id.text_device_action)

            val name = device.name ?: "Dispositivo Desconhecido"
            val btClass = device.bluetoothClass
            val majorClass = btClass?.majorDeviceClass ?: BluetoothStateHelper.MAJOR_UNCATEGORIZED
            val deviceClass = btClass?.deviceClass ?: 0
            val category = BluetoothStateHelper.classifyDevice(majorClass, deviceClass, name)

            textName.text = name
            textCategory.text = "[${category.displayName}]"
            textAddress.text = device.address
            textAction.text = "Parear"
            textAction.setTextColor(ContextCompat.getColor(activity, R.color.cyan_highlight))

            applyFocusAnimation(itemView)

            itemView.setOnClickListener {
                initiatePairing(device)
            }

            bluetoothDiscoveredContainer.addView(itemView)
        }
    }

    /**
     * Displays a tvOS styled options modal for a paired Bluetooth accessory with Disconnect and Unpair actions.
     *
     * @param device Target paired BluetoothDevice.
     */
    private fun showPairedDeviceDialog(device: BluetoothDevice) {
        val name = device.name ?: "Dispositivo"
        val options = arrayOf("Desconectar", "Desparear (Esquecer)", "Cancelar")

        AlertDialog.Builder(activity)
            .setTitle(name)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        Toast.makeText(activity, "Desconectando $name...", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        unpairDevice(device)
                    }
                    2 -> {
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    /**
     * Removes the bond relationship (unpairs) for the target Bluetooth device using reflection.
     *
     * @param device Target paired BluetoothDevice.
     */
    private fun unpairDevice(device: BluetoothDevice) {
        try {
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val result = removeBondMethod.invoke(device) as? Boolean
            if (result == true) {
                Toast.makeText(activity, "Dispositivo despareado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Solicitacao de despareamento enviada", Toast.LENGTH_SHORT).show()
            }
            renderPairedDevices()
        } catch (e: Exception) {
            Toast.makeText(activity, "Falha ao desparear dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Initiates Bluetooth pairing bond creation with target discovered device.
     *
     * @param device Target discovered BluetoothDevice.
     */
    private fun initiatePairing(device: BluetoothDevice) {
        try {
            val success = device.createBond()
            val name = device.name ?: device.address
            if (success) {
                Toast.makeText(activity, "Pareando com $name...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Iniciando conexao com $name...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Falha ao iniciar pareamento", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Registers system broadcast receiver listening for Bluetooth state transitions and device discovery.
     */
    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver != null) return

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        refreshBluetoothState()
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        bluetoothScanState.text = "Parar"
                        bluetoothScanningIndicator.visibility = View.VISIBLE
                        bluetoothEmptyDiscoveredText.visibility = View.GONE
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        bluetoothScanState.text = "Buscar"
                        bluetoothScanningIndicator.visibility = View.GONE
                        if (discoveredDevices.isEmpty()) {
                            bluetoothEmptyDiscoveredText.visibility = View.VISIBLE
                            bluetoothEmptyDiscoveredText.text = "Nenhum dispositivo encontrado no escaneamento"
                        }
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && device.bondState != BluetoothDevice.BOND_BONDED) {
                            discoveredDevices[device.address] = device
                            renderDiscoveredDevices()
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        renderPairedDevices()
                        renderDiscoveredDevices()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        activity.registerReceiver(bluetoothReceiver, filter)
    }

    /**
     * Safely unregisters the Bluetooth broadcast receiver if currently active.
     */
    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            bluetoothReceiver = null
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
