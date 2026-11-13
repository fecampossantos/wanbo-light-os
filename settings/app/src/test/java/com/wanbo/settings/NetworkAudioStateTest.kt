package com.wanbo.settings

import com.wanbo.settings.panes.AudioOutputMode
import com.wanbo.settings.panes.AudioStateHelper
import com.wanbo.settings.panes.BluetoothCategory
import com.wanbo.settings.panes.BluetoothStateHelper
import com.wanbo.settings.panes.SoundProfile
import com.wanbo.settings.panes.WifiStateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests validating pure logic calculations for Network (Wi-Fi), Bluetooth, and Audio panes:
 * - IPv4 dot-decimal formatting from 32-bit integers
 * - Wi-Fi signal level discrete step mapping and RSSI percentage conversions
 * - Audio volume step (0-15) to percentage (0-100%) conversions and profile metadata
 * - Bluetooth device categorization (Audio sink, Input peripheral, Miscellaneous)
 * - Strict Zero Emojis compliance across hardware category files
 */
class NetworkAudioStateTest {

    /**
     * Verifies that 32-bit integer representations of IPv4 addresses
     * format accurately into standard dot-decimal notation.
     */
    @Test
    fun testIpAddressFormattingFromInt() {
        // Standard Android WifiInfo little-endian test case: 0x6401a8c0 -> 192.168.1.100
        val testIp = 0x6401a8c0
        val formatted = WifiStateHelper.formatIpAddress(testIp)
        assertEquals("192.168.1.100", formatted)

        // Zero IP represents disconnected or unassigned address
        assertEquals("0.0.0.0", WifiStateHelper.formatIpAddress(0))

        // Gateway: 192.168.0.1 (0x0100a8c0)
        assertEquals("192.168.0.1", WifiStateHelper.formatIpAddress(0x0100a8c0))

        // Class B Private: 172.16.1.1 (0x010110ac)
        assertEquals("172.16.1.1", WifiStateHelper.formatIpAddress(0x010110ac))

        // Class A Private: 10.0.0.1 (0x0100000a)
        assertEquals("10.0.0.1", WifiStateHelper.formatIpAddress(0x0100000a))

        // Broadcast: 255.255.255.255
        assertEquals("255.255.255.255", WifiStateHelper.formatIpAddress(0xFFFFFFFF.toInt()))
    }

    /**
     * Verifies Wi-Fi RSSI to 5-level bar mapping (0 to 4) and percentage calculations.
     */
    @Test
    fun testWifiSignalLevelAndRssiCalculations() {
        // Upper bound: >= -55 dBm corresponds to level 4 (Excellent)
        assertEquals(4, WifiStateHelper.calculateSignalLevel(-50, 5))
        assertEquals(4, WifiStateHelper.calculateSignalLevel(-55, 5))
        assertEquals(100, WifiStateHelper.rssiToPercentage(-50))
        assertEquals("Excelente", WifiStateHelper.getSignalStrengthLabel(4))

        // Good range: -60 dBm
        val levelGood = WifiStateHelper.calculateSignalLevel(-60, 5)
        assertTrue("Level for -60 dBm should be 3 or 4", levelGood >= 3)
        assertEquals("Bom", WifiStateHelper.getSignalStrengthLabel(3))

        // Mid range: -75 dBm
        val levelMid = WifiStateHelper.calculateSignalLevel(-75, 5)
        assertEquals(2, levelMid)
        assertEquals("Razoavel", WifiStateHelper.getSignalStrengthLabel(2))
        assertEquals(50, WifiStateHelper.rssiToPercentage(-75))

        // Weak range: -85 dBm
        val levelWeak = WifiStateHelper.calculateSignalLevel(-85, 5)
        assertEquals(1, levelWeak)
        assertEquals("Fraco", WifiStateHelper.getSignalStrengthLabel(1))

        // Bottom bound: <= -100 dBm corresponds to level 0 (Very Weak)
        assertEquals(0, WifiStateHelper.calculateSignalLevel(-100, 5))
        assertEquals(0, WifiStateHelper.calculateSignalLevel(-105, 5))
        assertEquals(0, WifiStateHelper.rssiToPercentage(-100))
        assertEquals(0, WifiStateHelper.rssiToPercentage(-120))
        assertEquals("Muito Fraco", WifiStateHelper.getSignalStrengthLabel(0))

        // Clamping check for excessively strong signals
        assertEquals(4, WifiStateHelper.calculateSignalLevel(-20, 5))
        assertEquals(100, WifiStateHelper.rssiToPercentage(-20))
    }

    /**
     * Verifies SSID sanitization (removing quotes) and encryption capabilities detection.
     */
    @Test
    fun testWifiSsidSanitizationAndEncryption() {
        // Quoted SSIDs returned by Android WifiInfo
        assertEquals("Wanbo_Home_5G", WifiStateHelper.sanitizeSsid("\"Wanbo_Home_5G\""))
        assertEquals("Guest_Network", WifiStateHelper.sanitizeSsid("Guest_Network"))
        assertEquals("", WifiStateHelper.sanitizeSsid("\"\""))

        // Null and placeholder SSIDs
        assertEquals("Desconectado", WifiStateHelper.sanitizeSsid(null))
        assertEquals("Desconectado", WifiStateHelper.sanitizeSsid("<unknown ssid>"))
        assertEquals("Desconectado", WifiStateHelper.sanitizeSsid("0x"))
        assertEquals("Rede Pessoal", WifiStateHelper.sanitizeSsid(null, fallback = "Rede Pessoal"))

        // Encryption detection
        assertTrue(WifiStateHelper.isEncrypted("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertTrue(WifiStateHelper.isEncrypted("[WEP][ESS]"))
        assertTrue(WifiStateHelper.isEncrypted("[WPA-EAP-CCMP]"))
        assertFalse(WifiStateHelper.isEncrypted("[ESS]"))
        assertFalse(WifiStateHelper.isEncrypted(""))
        assertFalse(WifiStateHelper.isEncrypted(null))

        // Labels
        assertEquals("Protegida", WifiStateHelper.getSecurityLabel("[WPA2-PSK-CCMP]"))
        assertEquals("Aberta", WifiStateHelper.getSecurityLabel("[ESS]"))
    }

    /**
     * Verifies volume step percentage conversion (0-15 to 0-100%) and reverse index mapping.
     */
    @Test
    fun testAudioVolumeStepPercentageConversion() {
        val maxVolume = 15

        // Zero volume
        assertEquals(0, AudioStateHelper.volumeToPercentage(0, maxVolume))
        assertEquals(0, AudioStateHelper.percentageToVolume(0, maxVolume))

        // Max volume
        assertEquals(100, AudioStateHelper.volumeToPercentage(15, maxVolume))
        assertEquals(15, AudioStateHelper.percentageToVolume(100, maxVolume))

        // Mid steps
        // 7/15 = 46.666% -> rounded to 47%
        assertEquals(47, AudioStateHelper.volumeToPercentage(7, maxVolume))
        // 8/15 = 53.333% -> rounded to 53%
        assertEquals(53, AudioStateHelper.volumeToPercentage(8, maxVolume))

        // Reverse conversions
        assertEquals(7, AudioStateHelper.percentageToVolume(47, maxVolume))
        assertEquals(8, AudioStateHelper.percentageToVolume(50, maxVolume))
        assertEquals(8, AudioStateHelper.percentageToVolume(53, maxVolume))

        // Boundary clamping
        assertEquals(0, AudioStateHelper.volumeToPercentage(-5, maxVolume))
        assertEquals(100, AudioStateHelper.volumeToPercentage(25, maxVolume))
        assertEquals(0, AudioStateHelper.percentageToVolume(-10, maxVolume))
        assertEquals(15, AudioStateHelper.percentageToVolume(120, maxVolume))

        // Formatting
        assertEquals("75%", AudioStateHelper.formatVolumePercentage(75))
        assertEquals("100%", AudioStateHelper.formatVolumePercentage(100))
        assertEquals("0%", AudioStateHelper.formatVolumePercentage(0))
    }

    /**
     * Verifies audio output routing targets and equalizer acoustic profiles.
     */
    @Test
    fun testAudioOutputModesAndSoundProfiles() {
        // Output routing modes
        assertEquals(3, AudioOutputMode.values().size)
        assertEquals("internal_speaker", AudioOutputMode.INTERNAL_SPEAKER.id)
        assertEquals("Alto-falante Interno", AudioOutputMode.INTERNAL_SPEAKER.displayName)
        assertEquals("bluetooth", AudioOutputMode.BLUETOOTH.id)
        assertEquals("Bluetooth Audio", AudioOutputMode.BLUETOOTH.displayName)
        assertEquals("aux_p2", AudioOutputMode.AUX_P2.id)
        assertEquals("Saida Auxiliar P2 / Fone", AudioOutputMode.AUX_P2.displayName)

        // Output mode resolution
        assertEquals(AudioOutputMode.BLUETOOTH, AudioStateHelper.getOutputModeById("bluetooth"))
        assertEquals(AudioOutputMode.AUX_P2, AudioStateHelper.getOutputModeById("aux_p2"))
        assertEquals(AudioOutputMode.INTERNAL_SPEAKER, AudioStateHelper.getOutputModeById("internal_speaker"))
        assertEquals(AudioOutputMode.INTERNAL_SPEAKER, AudioStateHelper.getOutputModeById("invalid_id"))

        // Sound profiles
        assertEquals(4, SoundProfile.values().size)
        assertEquals("Padrao", SoundProfile.STANDARD.displayName)
        assertEquals("Cinema", SoundProfile.CINEMA.displayName)
        assertEquals("Musica", SoundProfile.MUSIC.displayName)
        assertEquals("Voz", SoundProfile.VOICE.displayName)

        // Profile resolution
        assertEquals(SoundProfile.CINEMA, AudioStateHelper.getSoundProfileById("cinema"))
        assertEquals(SoundProfile.MUSIC, AudioStateHelper.getSoundProfileById("music"))
        assertEquals(SoundProfile.VOICE, AudioStateHelper.getSoundProfileById("voice"))
        assertEquals(SoundProfile.STANDARD, AudioStateHelper.getSoundProfileById("standard"))
        assertEquals(SoundProfile.STANDARD, AudioStateHelper.getSoundProfileById(null))
    }

    /**
     * Verifies Bluetooth device classification across audio sinks, input peripherals, and others.
     */
    @Test
    fun testBluetoothDeviceClassification() {
        // 1. Audio Sink devices
        val audioMajor = BluetoothStateHelper.classifyDevice(BluetoothStateHelper.MAJOR_AUDIO_VIDEO)
        assertEquals(BluetoothCategory.AUDIO, audioMajor)
        assertTrue(BluetoothStateHelper.isAudioDevice(BluetoothStateHelper.MAJOR_AUDIO_VIDEO))

        val headphones = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_AUDIO_VIDEO,
            BluetoothStateHelper.DEVICE_AUDIO_HEADPHONES
        )
        assertEquals(BluetoothCategory.AUDIO, headphones)

        val soundbar = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_AUDIO_VIDEO,
            BluetoothStateHelper.DEVICE_AUDIO_LOUDSPEAKER
        )
        assertEquals(BluetoothCategory.AUDIO, soundbar)

        // Name-based audio detection
        val jblSpeaker = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_UNCATEGORIZED,
            deviceName = "JBL Soundbar Cinema"
        )
        assertEquals(BluetoothCategory.AUDIO, jblSpeaker)

        val bluetoothEarbuds = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_UNCATEGORIZED,
            deviceName = "Galaxy Buds Pro"
        )
        assertEquals(BluetoothCategory.AUDIO, bluetoothEarbuds)

        // 2. Input / Remote Peripherals
        val peripheralMajor = BluetoothStateHelper.classifyDevice(BluetoothStateHelper.MAJOR_PERIPHERAL)
        assertEquals(BluetoothCategory.REMOTE, peripheralMajor)
        assertTrue(BluetoothStateHelper.isRemoteDevice(BluetoothStateHelper.MAJOR_PERIPHERAL))

        val keyboard = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_PERIPHERAL,
            BluetoothStateHelper.DEVICE_PERIPHERAL_KEYBOARD
        )
        assertEquals(BluetoothCategory.REMOTE, keyboard)

        val mouse = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_PERIPHERAL,
            BluetoothStateHelper.DEVICE_PERIPHERAL_POINTING
        )
        assertEquals(BluetoothCategory.REMOTE, mouse)

        val remoteControl = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_PERIPHERAL,
            BluetoothStateHelper.DEVICE_PERIPHERAL_REMOTE
        )
        assertEquals(BluetoothCategory.REMOTE, remoteControl)

        // Name-based remote/accessory detection
        val wanboRemote = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_UNCATEGORIZED,
            deviceName = "Wanbo Remote Controller"
        )
        assertEquals(BluetoothCategory.REMOTE, wanboRemote)

        val gamepad = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_UNCATEGORIZED,
            deviceName = "Bluetooth Gamepad X3"
        )
        assertEquals(BluetoothCategory.REMOTE, gamepad)

        // 3. Uncategorized / Miscellaneous
        val phone = BluetoothStateHelper.classifyDevice(
            0x0200, // PHONE
            deviceName = "Pixel 7 Pro"
        )
        assertEquals(BluetoothCategory.OTHER, phone)

        val generic = BluetoothStateHelper.classifyDevice(
            BluetoothStateHelper.MAJOR_UNCATEGORIZED,
            deviceName = "Unknown Hardware"
        )
        assertEquals(BluetoothCategory.OTHER, generic)

        // Category labels
        assertEquals("Audio", BluetoothStateHelper.getCategoryLabel(BluetoothCategory.AUDIO))
        assertEquals("Controle Remoto", BluetoothStateHelper.getCategoryLabel(BluetoothCategory.REMOTE))
        assertEquals("Outros", BluetoothStateHelper.getCategoryLabel(BluetoothCategory.OTHER))
    }

    /**
     * Verifies strict Zero Emojis compliance across all newly implemented hardware pane files.
     */
    @Test
    fun testZeroEmojiComplianceAcrossHardwarePaneFiles() {
        val candidates = listOf(
            File("src"),
            File("app/src"),
            File("settings/app/src"),
            File("C:/Users/fecam/wanbo-light-os/settings/app/src")
        )
        val srcRoot = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not resolve src directory from candidates: $candidates")

        val filesToCheck = mutableListOf<File>()

        fun scan(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { scan(it) }
            } else if (file.isFile) {
                val name = file.name.lowercase()
                if (name.endsWith(".kt") || name.endsWith(".xml")) {
                    filesToCheck.add(file)
                }
            }
        }
        scan(srcRoot)

        assertTrue("Must scan at least 15 source and layout files", filesToCheck.size >= 15)

        for (file in filesToCheck) {
            val content = file.readText()
            var i = 0
            while (i < content.length) {
                val cp = content.codePointAt(i)
                val isEmoji = cp in 0x1F300..0x1F5FF ||
                    cp in 0x1F600..0x1F64F ||
                    cp in 0x1F680..0x1F6FF ||
                    cp in 0x1F700..0x1F77F ||
                    cp in 0x1F780..0x1F7FF ||
                    cp in 0x1F800..0x1F8FF ||
                    cp in 0x1F900..0x1F9FF ||
                    cp in 0x1FA00..0x1FA6F ||
                    cp in 0x1FA70..0x1FAFF ||
                    cp in 0x2600..0x26FF ||
                    cp in 0x2700..0x27BF ||
                    cp in 0xFE00..0xFE0F ||
                    cp in 0x1F1E6..0x1F1FF

                assertFalse(
                    "File ${file.name} contains emoji codepoint 0x${Integer.toHexString(cp)} violating Zero Emojis policy",
                    isEmoji
                )
                i += Character.charCount(cp)
            }
        }
    }
}
