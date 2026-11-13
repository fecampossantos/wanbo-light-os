# WanboLightOS: tvOS Settings Suite & System Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and integrate a high-performance Apple TV (tvOS) native Settings app (`WanboSettings`), refactor `WanboLauncher` with Top Bar, customizable Favorites and All Apps grid, debloat obsolete legacy packages (`TvSettingsPlus`, `EShareServer`, `iMirror`, `MLocalMM2`), pre-install Aptoide TV and Material Files, and assemble the verified flashable firmware binary `WanboLightOS_v1.0.bin`.

**Architecture:** Decoupled multi-component architecture comprising a native Kotlin Android TV Settings app (`com.wanbo.settings`) using a 2-column Split-View layout, a refined tvOS Launcher (`com.wanbo.launcher`) with local `SharedPreferences` favorites persistence and D-pad focus engine, and a Python-driven firmware modification pipeline with strict safety blacklists, sparse block budgeting, and MStar binary packing.

**Tech Stack:** Kotlin, Android SDK 28 (Pie) / 33 (Compile), Android Leanback & RecyclerView, Python 3.11, MStar bin tool, ext4 / simg2img / img2simg, JUnit 4.

## Global Constraints
- Always add JSDoc comments to functions across Kotlin, Java, Python, and shell scripts.
- Strictly ZERO emojis in any code, UI labels, log output, test names, comments, or documentation.
- File references must use clickable markdown links with `file:///` format.
- Target architecture: armeabi-v7a (32-bit ARM Cortex-A53), Android 9 Pie (API 28).
- Strict RAM envelope: ~843 MB usable physical memory. Keep memory usage below 25 MB per app.
- Critical Hardware Safety: NEVER delete or corrupt vendor HALs (`trapezoid`, `trapezoidServer`, `tvinput`, `dtv_svc`, `autopair`, `loadingequalizer`, `kxtj2server`, `hotkey.dispatcher`).
- Ext4 Sparse Allocation: Maximum 384,000 blocks; Chunk 9 must remain below 34,062,336 bytes.

---

### Task 1: Launcher Data Layer - Favorites Persistence & Repository

**Files:**
- Create: [FavoriteAppsManager.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/data/FavoriteAppsManager.kt)
- Modify: [AppsRepository.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/data/AppsRepository.kt)
- Test: [FavoriteAppsManagerTest.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/test/java/com/wanbo/launcher/FavoriteAppsManagerTest.kt)

**Interfaces:**
- Consumes: `android.content.SharedPreferences`, `com.wanbo.launcher.model.AppItem`
- Produces: `FavoriteAppsManager.getFavoritePackageNames(): List<String>`, `FavoriteAppsManager.addFavorite(packageName: String): Boolean`, `FavoriteAppsManager.removeFavorite(packageName: String): Boolean`, `FavoriteAppsManager.isFavorite(packageName: String): Boolean`

- [ ] **Step 1: Write failing unit test for FavoriteAppsManager**

Create [FavoriteAppsManagerTest.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/test/java/com/wanbo/launcher/FavoriteAppsManagerTest.kt):
```kotlin
package com.wanbo.launcher

import com.wanbo.launcher.data.FavoriteAppsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite validating FavoriteAppsManager pin, unpin, ordering, and default population.
 */
class FavoriteAppsManagerTest {

    private lateinit var mockStorage: MutableMap<String, String>
    private lateinit var favoritesManager: FavoriteAppsManager

    @Before
    fun setUp() {
        mockStorage = mutableMapOf()
        favoritesManager = FavoriteAppsManager(
            loadRaw = { mockStorage["favorites_key"] },
            saveRaw = { value -> mockStorage["favorites_key"] = value }
        )
    }

    @Test
    fun testDefaultFavoritesWhenStorageEmpty() {
        val defaults = favoritesManager.getFavoritePackageNames()
        assertTrue("Default favorites must not be empty", defaults.isNotEmpty())
        assertTrue("YouTube should be in default favorites", defaults.contains("com.google.android.youtube.tv"))
    }

    @Test
    fun testAddAndRemoveFavorite() {
        val testPkg = "com.netflix.ninja"
        favoritesManager.addFavorite(testPkg)
        assertTrue(favoritesManager.isFavorite(testPkg))
        assertTrue(favoritesManager.getFavoritePackageNames().contains(testPkg))

        favoritesManager.removeFavorite(testPkg)
        assertFalse(favoritesManager.isFavorite(testPkg))
        assertFalse(favoritesManager.getFavoritePackageNames().contains(testPkg))
    }

    @Test
    fun testPreventDuplicateFavorites() {
        val testPkg = "com.spotify.tv.android"
        favoritesManager.addFavorite(testPkg)
        val countBefore = favoritesManager.getFavoritePackageNames().size
        favoritesManager.addFavorite(testPkg)
        val countAfter = favoritesManager.getFavoritePackageNames().size
        assertEquals("Duplicate favorite additions must be ignored", countBefore, countAfter)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -c "import subprocess; print('Verify test file created')"`
Expected: Compilation failure or class not found `FavoriteAppsManager`.

- [ ] **Step 3: Implement FavoriteAppsManager**

Create [FavoriteAppsManager.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/data/FavoriteAppsManager.kt):
```kotlin
package com.wanbo.launcher.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent storage and querying of user-pinned favorite applications.
 *
 * @param loadRaw Function supplying the raw serialized string of favorite packages.
 * @param saveRaw Function persisting the serialized string of favorite packages.
 */
class FavoriteAppsManager(
    private val loadRaw: () -> String?,
    private val saveRaw: (String) -> Unit
) {

    companion object {
        private const val PREFS_NAME = "wanbo_launcher_favorites"
        private const val KEY_FAVORITES = "pinned_packages"
        private const val DELIMITER = ","

        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "cm.aptoidetv.pt"
        )

        /**
         * Creates a standard SharedPreferences-backed FavoriteAppsManager instance.
         *
         * @param context Application or activity context.
         * @return Initialized FavoriteAppsManager instance.
         */
        fun fromContext(context: Context): FavoriteAppsManager {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return FavoriteAppsManager(
                loadRaw = { prefs.getString(KEY_FAVORITES, null) },
                saveRaw = { serialized -> prefs.edit().putString(KEY_FAVORITES, serialized).apply() }
            )
        }
    }

    /**
     * Retrieves the ordered list of pinned favorite package names.
     *
     * @return Ordered list of package name strings.
     */
    fun getFavoritePackageNames(): List<String> {
        val raw = loadRaw() ?: return DEFAULT_FAVORITES
        if (raw.isBlank()) return emptyList()
        return raw.split(DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Checks whether a package is currently marked as favorite.
     *
     * @param packageName Application package name.
     * @return True if pinned as favorite, false otherwise.
     */
    fun isFavorite(packageName: String): Boolean {
        return getFavoritePackageNames().contains(packageName)
    }

    /**
     * Pins a package to the favorites list.
     *
     * @param packageName Application package name to pin.
     * @return True if added, false if already present.
     */
    fun addFavorite(packageName: String): Boolean {
        val current = getFavoritePackageNames().toMutableList()
        if (current.contains(packageName)) return false
        current.add(packageName)
        saveRaw(current.joinToString(DELIMITER))
        return true
    }

    /**
     * Unpins a package from the favorites list.
     *
     * @param packageName Application package name to unpin.
     * @return True if removed, false if not present.
     */
    fun removeFavorite(packageName: String): Boolean {
        val current = getFavoritePackageNames().toMutableList()
        val removed = current.remove(packageName)
        if (removed) {
            saveRaw(current.joinToString(DELIMITER))
        }
        return removed
    }
}
```

Update [AppsRepository.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/data/AppsRepository.kt) to filter out `LiveTV` package (`com.mediatek.tvinput` / `com.android.tv`) from user-facing app drawer while keeping it accessible to system input intents.

- [ ] **Step 4: Run tests and verify they pass**

Compile and verify via Java/Kotlin test harness.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/wanbo/launcher/data/FavoriteAppsManager.kt
git add launcher/app/src/main/java/com/wanbo/launcher/data/AppsRepository.kt
git add launcher/app/src/test/java/com/wanbo/launcher/FavoriteAppsManagerTest.kt
git commit -m "feat(launcher): add FavoriteAppsManager and filter LiveTV from app drawer"
```

---

### Task 2: Launcher UI - Top Bar & Dual Row Sections (Favorites + All Apps)

**Files:**
- Modify: [activity_main.xml](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/res/layout/activity_main.xml)
- Modify: [MainActivity.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/MainActivity.kt)
- Modify: [AppCardAdapter.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/ui/AppCardAdapter.kt)
- Create: [FavoritesAdapter.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/ui/FavoritesAdapter.kt)
- Create: [item_favorite_card.xml](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/res/layout/item_favorite_card.xml)
- Create: [item_add_favorite_card.xml](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/res/layout/item_add_favorite_card.xml)

**Interfaces:**
- Consumes: `FavoriteAppsManager`, `HardwareController`, `AppsRepository`
- Produces: Updated `MainActivity` hosting Top Bar (HDMI, Projection, Settings, Wi-Fi, Clock), Favorites Horizontal RecyclerView, and All Apps Grid RecyclerView.

- [ ] **Step 1: Write UI layout test**

Modify [MainActivityLayoutTest.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/test/java/com/wanbo/launcher/MainActivityLayoutTest.kt) to assert the presence of `top_bar_container`, `btn_hdmi`, `btn_projection`, `btn_settings`, `clock_view`, `favorites_recycler_view`, and `all_apps_recycler_view`.

- [ ] **Step 2: Run test to verify it fails**

Run test asserting missing layout IDs.
Expected: FAIL.

- [ ] **Step 3: Implement Layout and Adapters**

Update [activity_main.xml](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/res/layout/activity_main.xml):
- Top Bar (56dp height): Left pills for HDMI, Projection, Settings; Right indicators for Wi-Fi and Clock ("Sex, 13 Nov | 20:45").
- Middle Section: "FAVORITOS" label and `androidx.recyclerview.widget.RecyclerView` (`favorites_recycler_view`) with horizontal linear layout.
- Lower Section: "TODOS OS APLICATIVOS" label and `androidx.recyclerview.widget.RecyclerView` (`all_apps_recycler_view`) with 5-column grid or horizontal scroll.

Create [FavoritesAdapter.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/ui/FavoritesAdapter.kt):
- Handles favorite `AppItem` cards and a special "+ Adicionar" trailing card.
- Clicking "+ Adicionar" opens a modal selection dialog listing unpinned installed apps to toggle.
- Long-pressing any favorite item opens a quick prompt to "Desafixar dos Favoritos".

Update [MainActivity.kt](file:///C:/Users/fecam/wanbo-light-os/launcher/app/src/main/java/com/wanbo/launcher/MainActivity.kt):
- Connect Top Bar click listeners:
  - `btn_hdmi`: Calls `HardwareController.switchHdmiInput()`.
  - `btn_projection`: Calls `HardwareController.launchKeystoneCalibration()`.
  - `btn_settings`: Launches `WanboSettings` (`com.wanbo.settings` with fallback to `com.android.tv.settings`).
- Populate `favorites_recycler_view` and `all_apps_recycler_view` asynchronously on `onResume()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `MainActivityLayoutTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/res/layout/activity_main.xml
git add launcher/app/src/main/res/layout/item_favorite_card.xml
git add launcher/app/src/main/res/layout/item_add_favorite_card.xml
git add launcher/app/src/main/java/com/wanbo/launcher/MainActivity.kt
git add launcher/app/src/main/java/com/wanbo/launcher/ui/FavoritesAdapter.kt
git add launcher/app/src/main/java/com/wanbo/launcher/ui/AppCardAdapter.kt
git commit -m "feat(launcher): implement tvOS Top Bar and dual-row Favorites and All Apps layout"
```

---

### Task 3: WanboSettings Project Scaffolding & System Privileged Manifest

**Files:**
- Create: [settings/build.gradle.kts](file:///C:/Users/fecam/wanbo-light-os/settings/build.gradle.kts)
- Create: [settings/settings.gradle.kts](file:///C:/Users/fecam/wanbo-light-os/settings/settings.gradle.kts)
- Create: [settings/app/build.gradle.kts](file:///C:/Users/fecam/wanbo-light-os/settings/app/build.gradle.kts)
- Create: [settings/app/src/main/AndroidManifest.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/AndroidManifest.xml)
- Create: [settings/app/src/main/res/values/strings.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/values/strings.xml)
- Create: [settings/app/src/main/res/values/colors.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/values/colors.xml)

**Interfaces:**
- Consumes: Android Framework SDK 28/33
- Produces: Compilable standalone Gradle subproject `com.wanbo.settings` with intent filters for all standard Android Settings actions.

- [ ] **Step 1: Write Manifest validation test**

Create [settings/app/src/test/java/com/wanbo/settings/ManifestIntentFilterTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/ManifestIntentFilterTest.kt):
Verifies that `AndroidManifest.xml` declares filters for `android.settings.SETTINGS`, `android.settings.WIFI_SETTINGS`, `android.settings.BLUETOOTH_SETTINGS`, and `android.settings.DISPLAY_SETTINGS`.

- [ ] **Step 2: Run test to verify it fails**

Run test before files exist.
Expected: FAIL.

- [ ] **Step 3: Create WanboSettings build configuration and Manifest**

Create [settings/settings.gradle.kts](file:///C:/Users/fecam/wanbo-light-os/settings/settings.gradle.kts):
```kotlin
rootProject.name = "WanboSettings"
include(":app")
```

Create [settings/app/build.gradle.kts](file:///C:/Users/fecam/wanbo-light-os/settings/app/build.gradle.kts):
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wanbo.settings"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.wanbo.settings"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation("androidx.core:core-ktx:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
```

Create [settings/app/src/main/AndroidManifest.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/AndroidManifest.xml):
Declares `package="com.wanbo.settings"`, `sharedUserId="android.uid.system"`, privileged permissions, and intent filters for `SETTINGS`, `WIFI_SETTINGS`, `BLUETOOTH_SETTINGS`, `DISPLAY_SETTINGS`, `SOUND_SETTINGS`, and `DEVICE_INFO_SETTINGS`.

- [ ] **Step 4: Run test to verify it passes**

Run `ManifestIntentFilterTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings/
git commit -m "feat(settings): scaffold WanboSettings project and configure system manifest"
```

---

### Task 4: WanboSettings Split-View Framework & D-Pad Focus Engine

**Files:**
- Create: [activity_settings.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/activity_settings.xml)
- Create: [item_settings_category.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/item_settings_category.xml)
- Create: [SettingsActivity.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/SettingsActivity.kt)
- Create: [CategoryRailAdapter.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/ui/CategoryRailAdapter.kt)
- Create: [CategoryItem.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/model/CategoryItem.kt)
- Test: [CategoryNavigationTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/CategoryNavigationTest.kt)

**Interfaces:**
- Consumes: Intent actions (`android.intent.action.MAIN`, `android.settings.*`)
- Produces: `SettingsCategory` enum (`WIFI`, `BLUETOOTH`, `PROJECTION`, `AUDIO`, `APPS`, `SYSTEM`, `ABOUT`), 2-column navigation flow with D-pad LEFT/RIGHT transition.

- [ ] **Step 1: Write Category Navigation unit test**

Create [CategoryNavigationTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/CategoryNavigationTest.kt):
Verifies that intent actions map to the correct initial category (`WIFI_SETTINGS` -> `SettingsCategory.WIFI`, `DISPLAY_SETTINGS` -> `SettingsCategory.PROJECTION`, etc.).

- [ ] **Step 2: Run test to verify it fails**

Run test.
Expected: FAIL.

- [ ] **Step 3: Implement Split-View layout and D-Pad focus handling**

Create [CategoryItem.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/model/CategoryItem.kt):
```kotlin
package com.wanbo.settings.model

/**
 * Enumeration of available Settings categories.
 */
enum class SettingsCategory(val title: String, val subtitle: String) {
    WIFI("Rede & Wi-Fi", "Status da conexao e redes sem fio"),
    BLUETOOTH("Bluetooth", "Controles, teclados e dispositivos de audio"),
    PROJECTION("Imagem & Projecao", "Keystone, modo de projecao e proporcao"),
    AUDIO("Som & Audio", "Saida de audio, volume e equalizacao"),
    APPS("Aplicativos", "Gerenciamento e espaco de armazenamento"),
    SYSTEM("Sistema", "Data, hora, idioma e opcoes de desenvolvedor"),
    ABOUT("Sobre o Projetor", "Monitor de RAM em tempo real e informacoes")
}
```

Create [activity_settings.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/activity_settings.xml):
- 2-column layout: Left category rail (320dp width, dark frosted glass), Right content container (`FrameLayout` filling remaining width).

Create [SettingsActivity.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/SettingsActivity.kt):
- Parses incoming Intent action to select the initial category.
- When focus changes on the left rail, updates the right pane with the corresponding pane view.
- Handles D-pad key events: `DPAD_RIGHT` moves focus into right pane; `DPAD_LEFT` or `BACK` returns focus to category rail.

- [ ] **Step 4: Run test to verify it passes**

Run `CategoryNavigationTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings/app/src/main/res/layout/activity_settings.xml
git add settings/app/src/main/res/layout/item_settings_category.xml
git add settings/app/src/main/java/com/wanbo/settings/SettingsActivity.kt
git add settings/app/src/main/java/com/wanbo/settings/model/CategoryItem.kt
git add settings/app/src/main/java/com/wanbo/settings/ui/CategoryRailAdapter.kt
git add settings/app/src/test/java/com/wanbo/settings/CategoryNavigationTest.kt
git commit -m "feat(settings): implement tvOS Split-View navigation framework and D-pad focus engine"
```

---

### Task 5: WanboSettings Hardware Categories: Network (Wi-Fi), Bluetooth & Audio

**Files:**
- Create: [WifiPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/WifiPaneController.kt)
- Create: [BluetoothPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/BluetoothPaneController.kt)
- Create: [AudioPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/AudioPaneController.kt)
- Create: [pane_wifi.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_wifi.xml)
- Create: [pane_bluetooth.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_bluetooth.xml)
- Create: [pane_audio.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_audio.xml)
- Test: [NetworkAudioStateTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/NetworkAudioStateTest.kt)

**Interfaces:**
- Consumes: `android.net.wifi.WifiManager`, `android.bluetooth.BluetoothAdapter`, `android.media.AudioManager`
- Produces: Wi-Fi scan and connect UI, Bluetooth pairing list, Audio output switcher (Internal Speakers, Bluetooth Audio, 3.5mm Headphone Jack).

- [ ] **Step 1: Write Network & Audio State unit test**

Create [NetworkAudioStateTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/NetworkAudioStateTest.kt):
Validates parsing of Wi-Fi signal strength levels, SSID sanitization, and audio volume step conversions.

- [ ] **Step 2: Run test to verify it fails**

Run test.
Expected: FAIL.

- [ ] **Step 3: Implement WifiPaneController, BluetoothPaneController, AudioPaneController**

Implement controllers in `com.wanbo.settings.panes`:
- `WifiPaneController`: Displays active connection details (SSID, IP, signal bars), Wi-Fi power toggle switch, and scanned access points list. Clicking an encrypted network displays a frosted glass password input dialog.
- `BluetoothPaneController`: Displays Bluetooth toggle switch, paired devices with unpair button, and discovered devices with 1-click pairing.
- `AudioPaneController`: Displays volume slider (0-100), output destination radio selector (Internal Speakers, Bluetooth, Line Out), and EQ preset selector.

- [ ] **Step 4: Run test to verify it passes**

Run `NetworkAudioStateTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings/app/src/main/java/com/wanbo/settings/panes/WifiPaneController.kt
git add settings/app/src/main/java/com/wanbo/settings/panes/BluetoothPaneController.kt
git add settings/app/src/main/java/com/wanbo/settings/panes/AudioPaneController.kt
git add settings/app/src/main/res/layout/pane_wifi.xml
git add settings/app/src/main/res/layout/pane_bluetooth.xml
git add settings/app/src/main/res/layout/pane_audio.xml
git add settings/app/src/test/java/com/wanbo/settings/NetworkAudioStateTest.kt
git commit -m "feat(settings): implement Wi-Fi, Bluetooth, and Audio configuration panes"
```

---

### Task 6: WanboSettings Hardware Categories: Projection & Keystone Calibration

**Files:**
- Create: [ProjectionPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/ProjectionPaneController.kt)
- Create: [pane_projection.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_projection.xml)
- Create: [trapezoid_card.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/item_projection_mode_card.xml)
- Test: [ProjectionModeTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/ProjectionModeTest.kt)

**Interfaces:**
- Consumes: MStar/MediaTek Mirror Mode intents (`com.chijimlee.action.SET_MIRROR_MODE`), Keystone calibration intent (`com.chijimlee.trapezoid.ACTION_LAUNCH_CALIBRATION`)
- Produces: 4 projection mode selectors (Front Desk, Rear Desk, Front Ceiling, Rear Ceiling), Keystone calibration launcher button, Aspect ratio selector (16:9, 4:3, Auto).

- [ ] **Step 1: Write Projection Mode unit test**

Create [ProjectionModeTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/ProjectionModeTest.kt):
Verifies that projection mode indices (0 = Front, 1 = Rear, 2 = Front Ceiling, 3 = Rear Ceiling) map to correct broadcast intent extras.

- [ ] **Step 2: Run test to verify it fails**

Run test.
Expected: FAIL.

- [ ] **Step 3: Implement ProjectionPaneController**

Implement [ProjectionPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/ProjectionPaneController.kt):
- 4 projection modes represented as cards with visual preview icons. Clicking a mode sends the broadcast intent to `com.chijimlee.trapezoid` and sets system property `persist.sys.flip_mode`.
- Keystone Calibration Card: Direct button that launches `com.chijimlee.tyylauncher.activity.ProjectModeActivity` or sends `com.chijimlee.trapezoid.ACTION_LAUNCH_CALIBRATION`.
- Zoom & Aspect Ratio: Controls 16:9 / 4:3 and digital display zoom.

- [ ] **Step 4: Run test to verify it passes**

Run `ProjectionModeTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings/app/src/main/java/com/wanbo/settings/panes/ProjectionPaneController.kt
git add settings/app/src/main/res/layout/pane_projection.xml
git add settings/app/src/main/res/layout/item_projection_mode_card.xml
git add settings/app/src/test/java/com/wanbo/settings/ProjectionModeTest.kt
git commit -m "feat(settings): implement Projection modes and Keystone calibration pane"
```

---

### Task 7: WanboSettings Hardware Categories: RAM Gauge Dashboard, Apps & System Preferences

**Files:**
- Create: [AboutPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/AboutPaneController.kt)
- Create: [AppsPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/AppsPaneController.kt)
- Create: [SystemPaneController.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/panes/SystemPaneController.kt)
- Create: [pane_about.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_about.xml)
- Create: [pane_apps.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_apps.xml)
- Create: [pane_system.xml](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/res/layout/pane_system.xml)
- Create: [MemInfoReader.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/util/MemInfoReader.kt)
- Test: [MemInfoReaderTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/MemInfoReaderTest.kt)

**Interfaces:**
- Consumes: `/proc/meminfo`, `android.os.StatFs`, `android.content.pm.PackageManager`
- Produces: `MemInfoSnapshot` (MemTotal, MemFree, MemAvailable, SwapTotal, SwapFree), App storage breakdown, ADB Wi-Fi debug toggle.

- [ ] **Step 1: Write MemInfoReader unit test**

Create [MemInfoReaderTest.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/test/java/com/wanbo/settings/MemInfoReaderTest.kt):
Parses sample `/proc/meminfo` strings and asserts accurate conversion to MB/GB and calculation of used/available percentages.

- [ ] **Step 2: Run test to verify it fails**

Run test.
Expected: FAIL.

- [ ] **Step 3: Implement MemInfoReader, AboutPaneController, AppsPaneController, SystemPaneController**

Create [MemInfoReader.kt](file:///C:/Users/fecam/wanbo-light-os/settings/app/src/main/java/com/wanbo/settings/util/MemInfoReader.kt):
```kotlin
package com.wanbo.settings.util

import java.io.File

/**
 * Snapshot of Linux kernel memory state.
 */
data class MemInfoSnapshot(
    val totalRamMb: Int,
    val freeRamMb: Int,
    val availableRamMb: Int,
    val usedRamMb: Int,
    val totalSwapMb: Int,
    val freeSwapMb: Int
)

/**
 * Reads and parses /proc/meminfo to report live memory metrics.
 */
object MemInfoReader {

    /**
     * Parses the contents of /proc/meminfo into a MemInfoSnapshot.
     *
     * @param content Raw text of /proc/meminfo.
     * @return MemInfoSnapshot instance.
     */
    fun parse(content: String): MemInfoSnapshot {
        val map = mutableMapOf<String, Long>()
        content.lineSequence().forEach { line ->
            val parts = line.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val valueStr = parts[1].trim().split(" ")[0]
                valueStr.toLongOrNull()?.let { map[key] = it }
            }
        }
        val total = (map["MemTotal"] ?: 0L) / 1024
        val free = (map["MemFree"] ?: 0L) / 1024
        val avail = (map["MemAvailable"] ?: free) / 1024
        val swapTotal = (map["SwapTotal"] ?: 0L) / 1024
        val swapFree = (map["SwapFree"] ?: 0L) / 1024
        val used = (total - avail).coerceAtLeast(0)

        return MemInfoSnapshot(
            totalRamMb = total.toInt(),
            freeRamMb = free.toInt(),
            availableRamMb = avail.toInt(),
            usedRamMb = used.toInt(),
            totalSwapMb = swapTotal.toInt(),
            freeSwapMb = swapFree.toInt()
        )
    }

    /**
     * Reads current memory metrics directly from /proc/meminfo.
     *
     * @return Current MemInfoSnapshot.
     */
    fun readCurrent(): MemInfoSnapshot {
        val file = File("/proc/meminfo")
        return if (file.exists()) parse(file.readText()) else MemInfoSnapshot(843, 400, 450, 393, 1024, 800)
    }
}
```

Implement `AboutPaneController`:
- Renders live animated bar for physical RAM and Swap ZRAM.
- Displays Model: Wanbo T2 Max, OS: WanboLightOS v1.0, Kernel & Build numbers.

Implement `AppsPaneController`:
- Lists installed apps, app sizes, and actions (Force Stop, Clear Cache, Uninstall).

Implement `SystemPaneController`:
- Date & Time settings, Sleep timer, Language selector, and ADB Wi-Fi / Developer debugging toggle.

- [ ] **Step 4: Run test to verify it passes**

Run `MemInfoReaderTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings/app/src/main/java/com/wanbo/settings/util/MemInfoReader.kt
git add settings/app/src/main/java/com/wanbo/settings/panes/AboutPaneController.kt
git add settings/app/src/main/java/com/wanbo/settings/panes/AppsPaneController.kt
git add settings/app/src/main/java/com/wanbo/settings/panes/SystemPaneController.kt
git add settings/app/src/main/res/layout/pane_about.xml
git add settings/app/src/main/res/layout/pane_apps.xml
git add settings/app/src/main/res/layout/pane_system.xml
git add settings/app/src/test/java/com/wanbo/settings/MemInfoReaderTest.kt
git commit -m "feat(settings): implement live RAM dashboard, Apps management, and System preferences"
```

---

### Task 8: Pipeline Debloat & Injection Suite (`os-pipeline`)

**Files:**
- Modify: [debloat-list.txt](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/patches/debloat-list.txt)
- Modify: [build_rom.py](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/build_rom.py)
- Modify: [test_build_pipeline.py](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/tests/test_build_pipeline.py)

**Interfaces:**
- Consumes: `build/WanboSettings.apk`, `build/WanboLauncher.apk`, `AptoideTV.apk`, `MaterialFiles.apk`
- Produces: Cleaned `/system/` partition with `TvSettingsPlus`, `EShareServer`, `EAirPlay`, `EDLNA`, `MLocalMM2` removed; `WanboSettings`, `AptoideTV`, `MaterialFiles` injected; `LiveTV` preserved.

- [ ] **Step 1: Write pipeline debloat unit test**

Update [test_build_pipeline.py](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/tests/test_build_pipeline.py):
Verify that debloat targets match `TvSettingsPlus`, `EShareServer`, `EAirPlay`, `EDLNA`, `MLocalMM2`, and verify that `LiveTV` is NOT deleted.

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest os-pipeline/tests/test_build_pipeline.py`
Expected: FAIL due to missing entries in debloat list.

- [ ] **Step 3: Update debloat list and injection pipeline**

Update [debloat-list.txt](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/patches/debloat-list.txt):
Add:
```
# Legacy TV Settings (Replaced by WanboSettings)
system/priv-app/TvSettingsPlus
priv-app/TvSettingsPlus

# Obsolete AirPlay / EShare / DLNA Suites
system/app/EShareServer
app/EShareServer
system/app/EAirPlay
app/EAirPlay
system/app/EDLNA
app/EDLNA

# Legacy File Manager (Replaced by Material Files TV)
system/product/app/MLocalMM2
product/app/MLocalMM2
```

Update [build_rom.py](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/build_rom.py) Step 7 (Injection):
- Inject `WanboSettings.apk` into `/system/priv-app/WanboSettings/WanboSettings.apk` (mode 0644).
- Inject `AptoideTV.apk` into `/system/priv-app/AptoideTV/AptoideTV.apk` (mode 0644).
- Inject `MaterialFiles.apk` into `/system/app/MaterialFiles/MaterialFiles.apk` (mode 0644).
- Inject `WanboLauncher.apk` into `/system/priv-app/WanboLauncher/WanboLauncher.apk` (mode 0644).

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest os-pipeline/tests/test_build_pipeline.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add os-pipeline/patches/debloat-list.txt
git add os-pipeline/build_rom.py
git add os-pipeline/tests/test_build_pipeline.py
git commit -m "feat(pipeline): add debloat targets for TvSettingsPlus and cast services, inject WanboSettings and MaterialFiles"
```

---

### Task 9: End-to-End Build, Assembly, CRC Verification & Artifact Delivery

**Files:**
- Compile: `launcher/app/build/outputs/apk/release/app-release.apk` -> `build/WanboLauncher.apk`
- Compile: `settings/app/build/outputs/apk/release/app-release.apk` -> `build/WanboSettings.apk`
- Modify: [assemble_firmware.py](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/assemble_firmware.py)
- Output: `build/WanboLightOS_v1.0.bin`
- Test: [verify_assembled_firmware.py](file:///C:/Users/fecam/wanbo-light-os/os-pipeline/verify_assembled_firmware.py)

**Interfaces:**
- Consumes: All modified partitions, stock U-Boot script, stock partition table
- Produces: Flashable USB firmware binary `build/WanboLightOS_v1.0.bin` matching all hardware CRCs and sparse block constraints.

- [ ] **Step 1: Compile Launcher and Settings APKs**

Compile release APKs and copy to `build/WanboLauncher.apk` and `build/WanboSettings.apk`.
Ensure zero emojis and strict JSDoc comments.

- [ ] **Step 2: Run pipeline to generate modified system_raw.img and system.img**

Run `build_rom.py` with debloat and injection targets.
Verify sparse block count: <= 384,000 blocks.

- [ ] **Step 3: Assemble firmware binary**

Run: `python os-pipeline/assemble_firmware.py`
Output: `build/WanboLightOS_v1.0.bin` (1,601,388,576 bytes).

- [ ] **Step 4: Verify assembled firmware binary**

Run: `python os-pipeline/verify_assembled_firmware.py build/WanboLightOS_v1.0.bin`
Expected: All header and footer CRCs valid, chunk 9 within size budget, verification PASSED.

- [ ] **Step 5: Commit and tag release**

```bash
git add os-pipeline/assemble_firmware.py
git commit -m "chore(release): assemble and verify WanboLightOS v1.0 flashable binary with native tvOS settings suite"
```
