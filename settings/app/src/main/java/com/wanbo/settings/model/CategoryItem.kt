package com.wanbo.settings.model

/**
 * Enumeration representing all high-level settings categories for WanboSettings.
 *
 * @property title Human-readable category title displayed on the navigation rail.
 * @property subtitle Explanatory category subtitle describing available configurations.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String
) {
    WIFI(
        "Rede & Wi-Fi",
        "Status da conexao e redes sem fio"
    ),
    BLUETOOTH(
        "Bluetooth",
        "Controles, teclados e dispositivos de audio"
    ),
    PROJECTION(
        "Imagem & Projecao",
        "Keystone, modo de projecao e proporcao"
    ),
    AUDIO(
        "Som & Audio",
        "Saida de audio, volume e equalizacao"
    ),
    APPS(
        "Aplicativos",
        "Gerenciamento e espaco de armazenamento"
    ),
    SYSTEM(
        "Sistema",
        "Data, hora, idioma e opcoes de desenvolvedor"
    ),
    ABOUT(
        "Sobre o Projetor",
        "Monitor de RAM em tempo real e informacoes"
    );

    companion object {
        /** Intent action constant for Wi-Fi settings. */
        const val ACTION_WIFI_SETTINGS = "android.settings.WIFI_SETTINGS"

        /** Intent action constant for Network Operator settings. */
        const val ACTION_NETWORK_OPERATOR_SETTINGS = "android.settings.NETWORK_OPERATOR_SETTINGS"

        /** Intent action constant for standard Bluetooth settings. */
        const val ACTION_BLUETOOTH_SETTINGS = "android.settings.BLUETOOTH_SETTINGS"

        /** Intent action constant for Display and Projection settings. */
        const val ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS"

        /** Intent action constant for Sound and Audio settings. */
        const val ACTION_SOUND_SETTINGS = "android.settings.SOUND_SETTINGS"

        /** Intent action constant for Application management settings. */
        const val ACTION_APPLICATION_SETTINGS = "android.settings.APPLICATION_SETTINGS"

        /** Intent action constant for Detailed Application management settings. */
        const val ACTION_MANAGE_APPLICATIONS_SETTINGS = "android.settings.MANAGE_APPLICATIONS_SETTINGS"

        /** Intent action constant for Date and Time settings. */
        const val ACTION_DATE_SETTINGS = "android.settings.DATE_SETTINGS"

        /** Intent action constant for Device Info and Hardware Status settings. */
        const val ACTION_DEVICE_INFO_SETTINGS = "android.settings.DEVICE_INFO_SETTINGS"

        /** Standard system main action. */
        const val ACTION_MAIN = "android.intent.action.MAIN"

        /** Standard root settings action. */
        const val ACTION_SETTINGS = "android.settings.SETTINGS"

        /**
         * Resolves the target SettingsCategory from an incoming Android Intent action string.
         * Defaults to SettingsCategory.WIFI for unhandled, empty, or null actions.
         *
         * @param action Intent action string received by the activity.
         * @return Resolved SettingsCategory enum constant.
         */
        fun fromIntentAction(action: String?): SettingsCategory {
            return when (action) {
                ACTION_WIFI_SETTINGS,
                ACTION_NETWORK_OPERATOR_SETTINGS,
                ACTION_MAIN,
                ACTION_SETTINGS -> WIFI

                ACTION_BLUETOOTH_SETTINGS -> BLUETOOTH

                ACTION_DISPLAY_SETTINGS -> PROJECTION

                ACTION_SOUND_SETTINGS -> AUDIO

                ACTION_APPLICATION_SETTINGS,
                ACTION_MANAGE_APPLICATIONS_SETTINGS -> APPS

                ACTION_DATE_SETTINGS -> SYSTEM

                ACTION_DEVICE_INFO_SETTINGS -> ABOUT

                else -> WIFI
            }
        }
    }
}

/**
 * Standalone helper function delegating to SettingsCategory.fromIntentAction for convenience.
 *
 * @param action Intent action string received by the activity.
 * @return Resolved SettingsCategory enum constant.
 */
fun fromIntentAction(action: String?): SettingsCategory {
    return SettingsCategory.fromIntentAction(action)
}

/**
 * Data model representing a category item rendered inside the navigation rail.
 *
 * @property category The underlying SettingsCategory enum constant.
 * @property title Category title displayed in the rail.
 * @property subtitle Category description displayed in the rail.
 * @property isSelected Flag indicating whether this category is currently selected.
 */
data class CategoryItem(
    val category: SettingsCategory,
    val title: String = category.title,
    val subtitle: String = category.subtitle,
    var isSelected: Boolean = false
)
