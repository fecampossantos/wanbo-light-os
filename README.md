# WanboLightOS

> Lightweight, privacy-first custom operating system and tvOS-inspired launcher for the Wanbo T2 Max Smart Projector.

WanboLightOS is an open-source, debloated firmware project engineered specifically for the Wanbo T2 Max smart projector (MediaTek / MStar platform, `armeabi-v7a`, Android 9 Pie, API level 28). It replaces intrusive manufacturer bloatware, telemetry daemons, and sluggish stock launchers with an ultra-responsive, privacy-respecting environment designed for 1080p home theater projection.

---

## Architectural Highlights

WanboLightOS combines a native Android TV launcher with an automated firmware modification pipeline:

- **tvOS-Inspired Leanback Launcher (`WanboLauncher`)**
  - High-performance, native Android application built with Kotlin and Leanback design principles.
  - Floating card interface with dynamic 1.08x focus elevation and smooth 150ms spring transitions.
  - Direct 1-click hardware shortcuts: HDMI input switching, motorized keystone/trapezoid calibration, and native system settings.
  - Dark mode aesthetic optimized for high-contrast projection in both dark and ambient lighting environments.
  - Hero header with clean typography, digital clock, and subtle projector status indicators.

- **Automated OS Re-assembly Pipeline (`os-pipeline`)**
  - Python-orchestrated 9-step unpack, debloat, inject, patch, and repack pipeline for MStar firmware binaries.
  - Safe ext4 sparse image unpacking (`simg2img`) and filesystem manipulation with rollback awareness.
  - Wildcard-capable debloat engine targeting aggressive advertising frameworks and fake maintenance utilities.
  - Automated APK injection into `/system/priv-app/` and `/system/app/` with strict Linux permissions (`0644` files, `0755` directories).
  - Cross-platform build support with CLI flags, POSIX bash wrapper (`build-rom.sh`), and standard `Makefile` targets.

---

## Core Guarantees

### 1. Zero Emojis Policy
WanboLightOS strictly enforces a zero-emoji standard across all source code, XML layouts, strings, scripts, commit messages, and documentation. The interface delivers a clean, professional, distraction-free home cinema experience without cartoonish glyphs or rendering inconsistencies across system fonts.

### 2. Zero Telemetry & Privacy Preservation
The stock Wanbo firmware constantly runs background telemetry daemons, ad-serving frameworks (Zeasn, Whale TV), and analytics services that communicate with remote tracking servers. 

WanboLightOS eliminates this completely:
- The `WanboLauncher` application omits the `android.permission.INTERNET` permission entirely from its `AndroidManifest.xml`.
- The launcher cannot open network sockets, transmit telemetry, communicate with external hosts, or serve advertisements.
- Stock telemetry frameworks, analytics providers, and background advertising daemons are completely eradicated from the system partition during ROM assembly.

### 3. ~350 MB RAM Savings
The Wanbo T2 Max operates within a constrained physical memory envelope of 1 GB RAM (approximately 843 MB usable by the Android runtime). In the stock firmware, bloated background services and heavy webview launchers consume over 600 MB at idle, leaving less than 150 MB of free memory. This leads to frequent out-of-memory (OOM) app crashes, stuttering playback, and dropped frames during 1080p streaming.

By purging unnecessary background services, fake cleaner daemons, factory test suites, and default ad launchers, WanboLightOS recovers approximately 350 MB of RAM. Available free RAM increases from ~150 MB to over 500 MB, giving streaming applications (SmartTube, Kodi, Netflix, VLC) ample memory headroom for smooth 1080p 60fps video decoding and responsive multitasking.

### 4. 100% Hardware Preservation
Many generic custom ROMs or aggressive debloat scripts brick essential projector hardware functions. WanboLightOS implements a strict hardware preservation blacklist (`CRITICAL_HARDWARE_BLACKLIST`) preventing any removal or corruption of proprietary vendor HALs and drivers:

- **Motorized Optical Keystone & Focus:** Fully preserves `/vendor/bin/trapezoidServer` and `com.chijimlee.trapezoid`.
- **HDMI & AV Video Inputs:** Fully preserves `dtv_svc` and `com.mediatek.tvinput` for low-latency external console and PC connectivity.
- **Bluetooth Remote Control:** Fully preserves `kxtj2server`, `com.mediatek.autopair`, and `com.mediatek.hotkey.dispatcher` for seamless voice remote pairing and physical button mappings.
- **DSP Audio Equalizer:** Fully preserves `com.mediatek.loadingequalizer` for internal dual-speaker acoustic tuning.
- **Native Android TV Settings:** Fully preserves `com.android.tv.settings` for complete Wi-Fi, Bluetooth, display, and sound configuration.

---

## Hardware Specifications & Compatibility

| Parameter | Specification |
|:---|:---|
| **Device Model** | Wanbo T2 Max (Smart Projector) |
| **System Architecture** | `armeabi-v7a` (32-bit ARMv7) |
| **SoC Platform** | MediaTek / MStar Semiconductor |
| **Operating System** | Android 9 Pie (API Level 28) |
| **System RAM** | 1 GB physical (~843 MB usable) |
| **Internal Storage** | 16 GB eMMC flash |
| **Native Resolution** | 1920 x 1080 (Full HD 1080p) |
| **Connectivity** | Wi-Fi 802.11 b/g/n, Bluetooth 4.0, HDMI, USB 2.0 |

---

## Repository Directory Map

```text
wanbo-light-os/
|-- Makefile                          Root orchestration Makefile (help, launcher, test, rom, clean)
|-- LICENSE                           GNU General Public License v3.0 (GPL-3.0)
|-- README.md                         Project overview, guarantees, and quick start guide
|-- docs/
|   |-- how-to-flash.md               End-user USB thumb drive flashing instructions
|   |-- how-to-build.md               Developer guide for compiling APK and assembling ROM
|   `-- tests/
|       `-- test_docs.py              Automated documentation & zero-emoji verification test suite
|-- launcher/                         Android TV Leanback Launcher Source Code
|   |-- build.gradle.kts              Root Gradle build configuration
|   |-- settings.gradle.kts           Project settings
|   `-- app/
|       |-- build.gradle.kts          App-level Gradle build configuration
|       `-- src/
|           |-- main/
|           |   |-- AndroidManifest.xml Zero network permission manifest
|           |   |-- java/com/wanbo/launcher/
|           |   |   |-- MainActivity.kt Core Leanback TV launcher activity
|           |   |   |-- data/           AppItem model, color palette, AppsRepository
|           |   |   |-- hardware/       HardwareController (HDMI, Keystone, Settings)
|           |   |   `-- ui/             AppCardAdapter, focus elevation animations
|           |   `-- res/
|           |       |-- drawable/       Vector drawables, button & card backgrounds
|           |       |-- layout/         activity_main.xml, item_app_card.xml
|           |       `-- values/         colors.xml, strings.xml, styles.xml
|           `-- test/
|               `-- java/com/wanbo/launcher/  Unit tests for launcher components
`-- os-pipeline/                      Automated ROM Assembly Pipeline
    |-- build_rom.py                  Automated 9-step unpack, debloat, inject & repack orchestrator
    |-- build-rom.sh                  POSIX bash wrapper script
    |-- patches/
    |   |-- debloat-list.txt          Filesystem purge specification (bloatware removal)
    |   `-- build.prop.patch          1GB RAM tuning parameters and Dalvik heap limits
    `-- tests/
        |-- test_patches.py           Debloat specification & build.prop unit tests
        `-- test_build_pipeline.py    Pipeline orchestrator & HAL blacklist unit tests
```

---

## Quick Start

### For End-Users: Flash via USB
If you have a compiled `WanboLightOS_v1.0.bin` release image and wish to install it on your Wanbo T2 Max projector:
- Read the complete step-by-step instructions in the [USB Flashing Guide](docs/how-to-flash.md).
- Flashing requires a standard FAT32-formatted USB flash drive (4 GB to 32 GB) and takes approximately 2 to 3 minutes.

### For Developers: Compile and Build
If you wish to compile the launcher APK from source code, customize debloat rules, or assemble your own custom ROM binary:
- Read the comprehensive instructions in the [Build Guide](docs/how-to-build.md).
- Prerequisites: Python 3, JDK 17, Android SDK / build-tools, and MStar firmware utilities.
- Quick commands:
  ```bash
  # Compile the launcher APK
  make launcher

  # Run all automated verification tests
  make test

  # Assemble the custom ROM image
  make rom INPUT_FW=stock_firmware.bin OUTPUT_FW=WanboLightOS_v1.0.bin
  ```

---

## License

WanboLightOS is open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for the complete license terms and conditions.
