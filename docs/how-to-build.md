# WanboLightOS Build Guide

This technical guide provides comprehensive instructions for developers and system builders to compile the Wanbo TV launcher APK from source, run automated verification tests, and execute the firmware assembly pipeline to generate flashable ROM binaries for the Wanbo T2 Max smart projector.

---

## Target Hardware Specifications

- **Device:** Wanbo T2 Max Smart Projector
- **Architecture:** `armeabi-v7a` (32-bit ARMv7)
- **SoC Platform:** MediaTek / MStar Semiconductor (e.g. MT9255 / MStar 6A358 family)
- **Android Runtime:** Android 9 Pie (API Level 28)
- **RAM Envelope:** 1 GB physical memory (~843 MB usable)
- **Storage:** 16 GB eMMC flash

---

## Prerequisites & Development Environment

Building WanboLightOS requires the following development tools and runtime environments:

### 1. Java Development Kit (JDK 17)
- **Requirement:** OpenJDK 17 or Oracle JDK 17.
- **Verification:** Run `java -version` and `javac -version` to verify JDK 17 is active.
- **Installation:**
  - Ubuntu/Debian: `sudo apt-get install openjdk-17-jdk`
  - macOS: `brew install openjdk@17`
  - Windows: Download OpenJDK 17 installer from Adoptium Temurin or Microsoft OpenJDK.

### 2. Android SDK & Build Tools
- **Android SDK Platform:** API Level 28 (`platforms;android-28`).
- **Android SDK Build-Tools:** Version 28.0.3 or higher (`build-tools;28.0.3`).
- **Environment Variable:** Ensure `ANDROID_HOME` or `ANDROID_SDK_ROOT` points to your installed Android SDK directory.

### 3. Python 3 Runtime
- **Requirement:** Python 3.8 or higher.
- **Verification:** Run `python3 --version` (or `python --version` on Windows).
- Standard Python libraries utilized: `argparse`, `pathlib`, `shutil`, `subprocess`, `unittest`, `fnmatch`, `glob`.

### 4. MStar & Android Firmware Utilities
The ROM re-assembly pipeline requires standard Android image and MStar firmware manipulation binaries in your system `PATH`:
- **`simg2img`:** Converts Android sparse image format to raw ext4 images.
- **`img2simg`:** Re-converts raw ext4 images back to Android sparse format.
- **`make_ext4fs`** or **`mke2fs` + `e2fsdroid`:** Creates ext4 filesystem images from a directory tree.
- **`mstar-bin-tool`** (or `unpack.py` and `pack.py`): Extracts and repacks MStar/MediaTek firmware headers and partition tables.

---

## Repository Structure & Overview

```text
wanbo-light-os/
|-- Makefile                 Root build orchestration Makefile
|-- LICENSE                  GNU General Public License v3.0 (GPL-3.0)
|-- README.md                Project overview and architecture
|-- docs/                    Documentation guides and test suites
|-- launcher/                Android TV Leanback Launcher (Kotlin, Gradle)
`-- os-pipeline/             Firmware Debloat, Patching & Assembly Pipeline
```

---

## Step 1: Compiling the Wanbo TV Launcher APK

The `WanboLauncher` application is a lightweight, zero-telemetry Android TV Leanback launcher designed to replace the stock Whale / Zeasn TV interface.

### Build Target: `make launcher`
To compile the production release APK, execute:

```bash
make launcher
```

Alternatively, invoke Gradle directly from the `launcher` directory:

```bash
cd launcher
./gradlew assembleRelease
cd ..
```

*(On Windows PowerShell, run `.\gradlew.bat assembleRelease` inside `launcher/`)*

### Compilation Output
Upon successful compilation, the resulting APK is located at:
`launcher/app/build/outputs/apk/release/app-release-unsigned.apk` (or `WanboLauncher.apk`).

The build pipeline automatically scans standard build directories (`launcher/app/build/outputs/apk/release`, `launcher/app/build/outputs/apk/debug`, etc.) to locate the latest compiled APK.

---

## Step 2: Running Automated Verification Tests

WanboLightOS includes an automated test suite verifying debloat rules, low-RAM property patches, HAL preservation guarantees, pipeline execution logic, documentation completeness, and zero-emoji compliance.

### Test Target: `make test`
Run all automated test suites by executing:

```bash
make test
```

Behind the scenes, this executes Python's standard `unittest` test runner across all test modules:

```bash
python3 -m unittest discover -s os-pipeline/tests -p "test_*.py" -v
python3 -m unittest discover -s docs/tests -p "test_*.py" -v
```

### Test Coverage Highlights
- **Patches Suite (`os-pipeline/tests/test_patches.py`):**
  - Confirms complete removal of Zeasn, Whale TV, fake cleaners, and factory demos.
  - Verifies zero deletions of critical hardware packages (`trapezoid`, `tvinput`, `autopair`, `loadingequalizer`, `tv.settings`).
  - Confirms low-RAM parameters (`ro.config.low_ram=true`, Dalvik 843 MB heap limits, GPU acceleration, ZRAM).
- **Pipeline Suite (`os-pipeline/tests/test_build_pipeline.py`):**
  - Validates CLI argument parsing and flags.
  - Verifies glob/wildcard debloat pattern expansion.
  - Verifies `CriticalHardwareSafetyViolation` exceptions if any HAL blacklist item is matched.
  - Tests simulated end-to-end execution via `--dry-run`.
- **Documentation Suite (`docs/tests/test_docs.py`):**
  - Validates existence of `README.md`, `LICENSE`, `how-to-flash.md`, `how-to-build.md`.
  - Verifies 100% Zero Emojis compliance across all documentation and source files.

---

## Step 3: Assembling the Custom ROM Binary

The ROM assembly orchestrator (`os-pipeline/build_rom.py`) takes an official Wanbo T2 Max stock firmware binary, unpacks the partitions, strips out bloatware, enforces hardware safety, injects the custom launcher, applies low-memory configuration, and repacks a clean, flashable USB image.

### Build Target: `make rom`

```bash
make rom INPUT_FW=stock_firmware.bin OUTPUT_FW=WanboLightOS_v1.0.bin
```

### Direct Script Invocation
You can also invoke the Python pipeline or Bash wrapper directly with custom CLI flags:

```bash
# Using the POSIX Bash wrapper
./os-pipeline/build-rom.sh stock_firmware.bin WanboLightOS_v1.0.bin

# Using Python 3 directly
python3 os-pipeline/build_rom.py stock_firmware.bin WanboLightOS_v1.0.bin --verbose
```

### Dry-Run Simulation Mode
To simulate the complete pipeline without physical firmware binaries or external image tools:

```bash
python3 os-pipeline/build_rom.py dummy_stock.bin WanboLightOS_v1.0.bin --dry-run --verbose
```

---

## The 9-Step ROM Assembly Pipeline Explained

The automated pipeline performs the following nine operations in sequence:

1. **Step 1: Prerequisite Validation**
   - Validates existence of input stock firmware binary.
   - Discovers compiled `WanboLauncher.apk`.
   - Loads `debloat-list.txt` and `build.prop.patch`.
2. **Step 2: MStar Firmware Unpack**
   - Unpacks the MStar firmware package using `mstar-bin-tool` or `unpack.py`.
   - Extracts partition table and individual partition images (`boot.img`, `recovery.img`, `system.img`, `vendor.img`).
3. **Step 3: Sparse Image Conversion**
   - Converts the sparse Android `system.img` to an uncompressed raw ext4 filesystem image using `simg2img`.
4. **Step 4: Filesystem Extraction**
   - Extracts or mounts the raw ext4 filesystem tree into a working directory (`work_dir/system_extract`).
5. **Step 5: Debloat Application**
   - Parses `debloat-list.txt` with support for exact paths and wildcard glob patterns (`*`).
   - Resolves target paths across both flat (`app/...`) and system-as-root (`system/app/...`) directory layouts.
6. **Step 6: Critical HAL Safety Blacklist Enforcement**
   - Checks candidate deletions against `CRITICAL_HARDWARE_BLACKLIST`.
   - Guaranteed protection for: `trapezoid`, `trapezoidServer`, `tvinput`, `dtv_svc`, `autopair`, `loadingequalizer`, `tv.settings`, `kxtj2server`, `hotkey.dispatcher`.
   - Aborts immediately with `CriticalHardwareSafetyViolation` (exit code 2) if any safety violation is detected.
7. **Step 7: APK Package Injection**
   - Injects `WanboLauncher.apk` into `/system/priv-app/WanboLauncher/WanboLauncher.apk` (directory mode `0755`, file mode `0644`).
   - Injects `AptoideTV.apk` into `/system/app/AptoideTV/AptoideTV.apk` (directory mode `0755`, file mode `0644`).
8. **Step 8: `build.prop` Low-Memory Patch Application**
   - Replaces existing configuration keys in-place and appends newly declared properties to `/system/build.prop` (mode `0644`).
   - Enforces low-RAM profile (`ro.config.low_ram=true`), custom LMK thresholds, Dalvik 843 MB heap limits, hardware OpenGL rendering, and ZRAM idle swap.
9. **Step 9: Repack & Sparse Conversion**
   - Repacks the modified filesystem tree into raw ext4 (`make_ext4fs` / `mke2fs`).
   - Converts the raw image to Android sparse format using `img2simg`.
   - Assembles all partition binaries into the final MStar flashable firmware binary: `WanboLightOS_v1.0.bin`.

---

## Customization Options

### Modifying the Debloat Specification
Edit `os-pipeline/patches/debloat-list.txt` to add or remove bloatware package entries. Each line specifies a file or directory path relative to the system root. Comment lines starting with `#` are ignored. Wildcards (`*`) are expanded automatically.

### Adjusting Low-RAM and Dalvik VM Parameters
Edit `os-pipeline/patches/build.prop.patch` to fine-tune memory limits or graphics parameters. For example, to adjust Dalvik heap growth limit for heavier applications, modify:

```properties
dalvik.vm.heapgrowthlimit=80m
```

---

## Cleaning Build Artifacts

To remove temporary build directories, Gradle outputs, and intermediate extraction workspaces:

```bash
make clean
```
