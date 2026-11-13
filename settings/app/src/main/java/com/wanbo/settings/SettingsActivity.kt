package com.wanbo.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Main entry-point activity for WanboSettings.
 * Handles system settings navigation and action intents for projector management.
 */
class SettingsActivity : AppCompatActivity() {

    /**
     * Initializes the settings activity and sets up the base window appearance.
     *
     * @param savedInstanceState Previously saved instance state bundle, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }
}
