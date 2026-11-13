package com.wanbo.settings.panes

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wanbo.settings.R
import kotlin.math.roundToInt

/**
 * Enumeration representing available audio output destinations for Wanbo projector.
 *
 * @property id Unique persistent identifier.
 * @property displayName Portuguese title.
 * @property description Explanatory subtitle.
 */
enum class AudioOutputMode(
    val id: String,
    val displayName: String,
    val description: String
) {
    INTERNAL_SPEAKER(
        "internal_speaker",
        "Alto-falante Interno",
        "Alto-falante embutido do projetor"
    ),
    BLUETOOTH(
        "bluetooth",
        "Bluetooth Audio",
        "Caixa de som ou soundbar sem fio pareada"
    ),
    AUX_P2(
        "aux_p2",
        "Saida Auxiliar P2 / Fone",
        "Conector analogico de 3.5mm"
    )
}

/**
 * Enumeration representing predefined equalizer acoustic calibration profiles.
 *
 * @property id Unique persistent identifier.
 * @property displayName Portuguese title.
 * @property description Explanatory acoustic profile characteristics.
 */
enum class SoundProfile(
    val id: String,
    val displayName: String,
    val description: String
) {
    STANDARD(
        "standard",
        "Padrao",
        "Resposta de frequencia equilibrada para qualquer conteudo"
    ),
    CINEMA(
        "cinema",
        "Cinema",
        "Graves encorpados e imersao sonora para filmes e series"
    ),
    MUSIC(
        "music",
        "Musica",
        "Fidelidade acustica, agudos nitidos e separacao estereo"
    ),
    VOICE(
        "voice",
        "Voz",
        "Realce de frequencias vocais para noticias e podcasts"
    )
}

/**
 * Pure logic helper for volume step calculations, percentage conversions,
 * and audio routing configurations without Android framework dependencies.
 */
object AudioStateHelper {

    /** Default maximum volume step index for STREAM_MUSIC in Android. */
    const val DEFAULT_MAX_VOLUME = 15

    /**
     * Converts a stream volume index step (e.g. 0 to 15) to a percentage (0 to 100%).
     *
     * @param currentVolume Current stream volume index.
     * @param maxVolume Maximum stream volume index.
     * @return Clamped percentage integer between 0 and 100.
     */
    fun volumeToPercentage(currentVolume: Int, maxVolume: Int = DEFAULT_MAX_VOLUME): Int {
        if (maxVolume <= 0) return 0
        val clamped = currentVolume.coerceIn(0, maxVolume)
        return ((clamped.toFloat() / maxVolume.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * Converts a volume percentage (0 to 100%) back to a discrete stream volume index step (0 to maxVolume).
     *
     * @param percentage Integer percentage from 0 to 100.
     * @param maxVolume Maximum stream volume index.
     * @return Discrete stream volume index.
     */
    fun percentageToVolume(percentage: Int, maxVolume: Int = DEFAULT_MAX_VOLUME): Int {
        if (maxVolume <= 0) return 0
        val clamped = percentage.coerceIn(0, 100)
        return ((clamped.toFloat() / 100f) * maxVolume.toFloat()).roundToInt().coerceIn(0, maxVolume)
    }

    /**
     * Formats the volume percentage integer as a human-readable string.
     *
     * @param percentage Integer percentage value.
     * @return Formatted percentage string (e.g. "75%").
     */
    fun formatVolumePercentage(percentage: Int): String {
        return "${percentage.coerceIn(0, 100)}%"
    }

    /**
     * Resolves SoundProfile enum from persistent identifier string. Defaults to STANDARD.
     *
     * @param id Identifier string.
     * @return Resolved SoundProfile constant.
     */
    fun getSoundProfileById(id: String?): SoundProfile {
        return SoundProfile.values().firstOrNull { it.id == id } ?: SoundProfile.STANDARD
    }

    /**
     * Resolves AudioOutputMode enum from persistent identifier string. Defaults to INTERNAL_SPEAKER.
     *
     * @param id Identifier string.
     * @return Resolved AudioOutputMode constant.
     */
    fun getOutputModeById(id: String?): AudioOutputMode {
        return AudioOutputMode.values().firstOrNull { it.id == id } ?: AudioOutputMode.INTERNAL_SPEAKER
    }
}

/**
 * Pane controller managing master volume control, audio output routing
 * (Internal Speaker, Bluetooth Audio, Aux P2), and equalizer sound profile calibration.
 *
 * @param activity Host AppCompatActivity.
 * @param container Target container ViewGroup into which the pane layout is inflated.
 */
class AudioPaneController(
    private val activity: Activity,
    private val container: ViewGroup
) {

    private var rootView: View? = null
    private var audioManager: AudioManager? = null
    private var audioReceiver: BroadcastReceiver? = null

    // UI Widgets: Volume
    private lateinit var cardMasterVolume: View
    private lateinit var textVolumePercentage: TextView
    private lateinit var progressMasterVolume: ProgressBar
    private lateinit var btnVolumeDown: View
    private lateinit var btnVolumeUp: View

    // UI Widgets: Audio Output
    private lateinit var cardOutputSpeaker: View
    private lateinit var badgeOutputSpeaker: TextView
    private lateinit var cardOutputBluetooth: View
    private lateinit var badgeOutputBluetooth: TextView
    private lateinit var cardOutputAux: View
    private lateinit var badgeOutputAux: TextView

    // UI Widgets: Sound Profiles
    private lateinit var cardProfileStandard: View
    private lateinit var badgeProfileStandard: TextView
    private lateinit var cardProfileCinema: View
    private lateinit var badgeProfileCinema: TextView
    private lateinit var cardProfileMusic: View
    private lateinit var badgeProfileMusic: TextView
    private lateinit var cardProfileVoice: View
    private lateinit var badgeProfileVoice: TextView

    private val prefs by lazy {
        activity.getSharedPreferences("wanbo_audio_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_KEY_OUTPUT_MODE = "key_audio_output_mode"
        private const val PREF_KEY_SOUND_PROFILE = "key_sound_profile"
        private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }

    /**
     * Inflates the Audio layout, binds UI views, configures D-pad key listeners,
     * registers volume change broadcast receiver, and restores active selections.
     */
    fun attach() {
        audioManager = activity.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.pane_audio, container, false)
        rootView = root
        container.removeAllViews()
        container.addView(root)

        bindViews(root)
        setupFocusEffects()
        setupListeners()
        registerAudioReceiver()
        refreshAudioState()
    }

    /**
     * Detaches the controller, unregisters broadcast listeners, and cleans up references.
     */
    fun detach() {
        unregisterAudioReceiver()
        rootView = null
    }

    /**
     * Binds view elements from the inflated pane layout.
     *
     * @param root Inflated root view.
     */
    private fun bindViews(root: View) {
        cardMasterVolume = root.findViewById(R.id.card_master_volume)
        textVolumePercentage = root.findViewById(R.id.text_volume_percentage)
        progressMasterVolume = root.findViewById(R.id.progress_master_volume)
        btnVolumeDown = root.findViewById(R.id.btn_volume_down)
        btnVolumeUp = root.findViewById(R.id.btn_volume_up)

        cardOutputSpeaker = root.findViewById(R.id.card_output_speaker)
        badgeOutputSpeaker = root.findViewById(R.id.badge_output_speaker)
        cardOutputBluetooth = root.findViewById(R.id.card_output_bluetooth)
        badgeOutputBluetooth = root.findViewById(R.id.badge_output_bluetooth)
        cardOutputAux = root.findViewById(R.id.card_output_aux)
        badgeOutputAux = root.findViewById(R.id.badge_output_aux)

        cardProfileStandard = root.findViewById(R.id.card_profile_standard)
        badgeProfileStandard = root.findViewById(R.id.badge_profile_standard)
        cardProfileCinema = root.findViewById(R.id.card_profile_cinema)
        badgeProfileCinema = root.findViewById(R.id.badge_profile_cinema)
        cardProfileMusic = root.findViewById(R.id.card_profile_music)
        badgeProfileMusic = root.findViewById(R.id.badge_profile_music)
        cardProfileVoice = root.findViewById(R.id.card_profile_voice)
        badgeProfileVoice = root.findViewById(R.id.badge_profile_voice)
    }

    /**
     * Configures tvOS smooth scale and elevation animations for focusable cards.
     */
    private fun setupFocusEffects() {
        applyFocusAnimation(cardMasterVolume)
        applyFocusAnimation(btnVolumeDown)
        applyFocusAnimation(btnVolumeUp)

        applyFocusAnimation(cardOutputSpeaker)
        applyFocusAnimation(cardOutputBluetooth)
        applyFocusAnimation(cardOutputAux)

        applyFocusAnimation(cardProfileStandard)
        applyFocusAnimation(cardProfileCinema)
        applyFocusAnimation(cardProfileMusic)
        applyFocusAnimation(cardProfileVoice)
    }

    /**
     * Configures interactive D-pad key adjustments and click listeners across volume, routing, and equalizer cards.
     */
    private fun setupListeners() {
        // D-pad LEFT/RIGHT volume adjustment when focused on master volume card
        cardMasterVolume.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        adjustVolume(-1)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        adjustVolume(1)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnVolumeDown.setOnClickListener {
            adjustVolume(-1)
        }

        btnVolumeUp.setOnClickListener {
            adjustVolume(1)
        }

        cardOutputSpeaker.setOnClickListener {
            setOutputMode(AudioOutputMode.INTERNAL_SPEAKER)
        }
        cardOutputBluetooth.setOnClickListener {
            setOutputMode(AudioOutputMode.BLUETOOTH)
        }
        cardOutputAux.setOnClickListener {
            setOutputMode(AudioOutputMode.AUX_P2)
        }

        cardProfileStandard.setOnClickListener {
            setSoundProfile(SoundProfile.STANDARD)
        }
        cardProfileCinema.setOnClickListener {
            setSoundProfile(SoundProfile.CINEMA)
        }
        cardProfileMusic.setOnClickListener {
            setSoundProfile(SoundProfile.MUSIC)
        }
        cardProfileVoice.setOnClickListener {
            setSoundProfile(SoundProfile.VOICE)
        }
    }

    /**
     * Refreshes volume level from AudioManager and restores selected output routing and profile badges.
     */
    fun refreshAudioState() {
        refreshVolumeDisplay()

        val savedOutput = AudioStateHelper.getOutputModeById(prefs.getString(PREF_KEY_OUTPUT_MODE, null))
        renderOutputModeSelection(savedOutput)

        val savedProfile = AudioStateHelper.getSoundProfileById(prefs.getString(PREF_KEY_SOUND_PROFILE, null))
        renderSoundProfileSelection(savedProfile)
    }

    /**
     * Queries stream volume from AudioManager and synchronizes percentage text and progress bar.
     */
    private fun refreshVolumeDisplay() {
        val manager = audioManager
        val maxVolume = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: AudioStateHelper.DEFAULT_MAX_VOLUME
        val currentVolume = manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVolume / 2)
        val percentage = AudioStateHelper.volumeToPercentage(currentVolume, maxVolume)

        textVolumePercentage.text = AudioStateHelper.formatVolumePercentage(percentage)
        progressMasterVolume.progress = percentage
    }

    /**
     * Adjusts the stream volume up or down by the specified step delta.
     *
     * @param delta Signed volume delta index (e.g. -1 or +1).
     */
    private fun adjustVolume(delta: Int) {
        val manager = audioManager ?: return
        try {
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val target = (current + delta).coerceIn(0, max)
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            refreshVolumeDisplay()
        } catch (e: Exception) {
            Toast.makeText(activity, "Erro ao ajustar volume", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Activates target audio output mode and updates UI badges.
     *
     * @param mode Selected AudioOutputMode.
     */
    private fun setOutputMode(mode: AudioOutputMode) {
        prefs.edit().putString(PREF_KEY_OUTPUT_MODE, mode.id).apply()
        renderOutputModeSelection(mode)
        Toast.makeText(activity, "Saida selecionada: ${mode.displayName}", Toast.LENGTH_SHORT).show()
    }

    /**
     * Updates visual active/inactive badges across the three output routing cards.
     *
     * @param activeMode Current active AudioOutputMode.
     */
    private fun renderOutputModeSelection(activeMode: AudioOutputMode) {
        updateBadge(badgeOutputSpeaker, activeMode == AudioOutputMode.INTERNAL_SPEAKER, "Ativo", "Inativo")
        updateBadge(badgeOutputBluetooth, activeMode == AudioOutputMode.BLUETOOTH, "Ativo", "Inativo")
        updateBadge(badgeOutputAux, activeMode == AudioOutputMode.AUX_P2, "Ativo", "Inativo")
    }

    /**
     * Activates target equalizer acoustic profile and updates UI badges.
     *
     * @param profile Selected SoundProfile.
     */
    private fun setSoundProfile(profile: SoundProfile) {
        prefs.edit().putString(PREF_KEY_SOUND_PROFILE, profile.id).apply()
        renderSoundProfileSelection(profile)
        Toast.makeText(activity, "Perfil aplicado: ${profile.displayName}", Toast.LENGTH_SHORT).show()
    }

    /**
     * Updates visual selected/inactive badges across the four sound profile cards.
     *
     * @param activeProfile Current active SoundProfile.
     */
    private fun renderSoundProfileSelection(activeProfile: SoundProfile) {
        updateBadge(badgeProfileStandard, activeProfile == SoundProfile.STANDARD, "Selecionado", "Inativo")
        updateBadge(badgeProfileCinema, activeProfile == SoundProfile.CINEMA, "Selecionado", "Inativo")
        updateBadge(badgeProfileMusic, activeProfile == SoundProfile.MUSIC, "Selecionado", "Inativo")
        updateBadge(badgeProfileVoice, activeProfile == SoundProfile.VOICE, "Selecionado", "Inativo")
    }

    /**
     * Configures background drawable and typography colors for status badges.
     *
     * @param badge Target TextView badge widget.
     * @param isActive True if selected/active.
     * @param activeText Text label when active.
     * @param inactiveText Text label when inactive.
     */
    private fun updateBadge(badge: TextView, isActive: Boolean, activeText: String, inactiveText: String) {
        if (isActive) {
            badge.text = activeText
            badge.setBackgroundResource(R.drawable.bg_badge_active)
            badge.setTextColor(ContextCompat.getColor(activity, R.color.status_connected))
        } else {
            badge.text = inactiveText
            badge.setBackgroundResource(R.drawable.bg_badge_inactive)
            badge.setTextColor(ContextCompat.getColor(activity, R.color.status_disconnected))
        }
    }

    /**
     * Registers system broadcast receiver listening for volume adjustment events.
     */
    private fun registerAudioReceiver() {
        if (audioReceiver != null) return

        audioReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_VOLUME_CHANGED) {
                    refreshVolumeDisplay()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
        }
        try {
            activity.registerReceiver(audioReceiver, filter)
        } catch (_: Exception) {
        }
    }

    /**
     * Safely unregisters the audio volume change receiver.
     */
    private fun unregisterAudioReceiver() {
        audioReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            audioReceiver = null
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
