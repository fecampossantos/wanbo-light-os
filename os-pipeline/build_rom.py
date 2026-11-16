#!/usr/bin/env python3
"""
WanboLightOS Automated Firmware Unpack, Debloat, Injection & Repack Pipeline.
Target Hardware: Wanbo T2 Max Smart Projector (armeabi-v7a, Android 9 Pie, API 28).

Orchestrates the 9-step modification pipeline:
  Step 1: Validates existence of input stock firmware and compiled launcher APK.
  Step 2: Unpacks MStar/MediaTek firmware binary using mstar-bin-tool or partition extractors.
  Step 3: Converts sparse system.img to raw ext4 via simg2img.
  Step 4: Mounts/extracts system_raw.img.
  Step 5: Applies debloat-list.txt with robust glob/fnmatch wildcard expansion.
  Step 6: Verifies critical hardware safety blacklist is NEVER touched.
  Step 7: Injects compiled WanboLauncher.apk into priv-app/WanboLauncher/, WanboSettings.apk into priv-app/WanboSettings/, AptoideTV.apk into priv-app/AptoideTV/, and MaterialFiles.apk into app/MaterialFiles/.
  Step 8: Appends build.prop.patch to system/build.prop.
  Step 9: Re-converts to sparse image (img2simg) and re-packs MStar binary into flashable USB firmware.

Strict zero-emoji compliance is enforced throughout all outputs and logs.
"""

import argparse
import fnmatch
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


# Critical hardware driver and HAL safety blacklist.
# Any package, driver, daemon, or config containing these substrings must NEVER be touched.
CRITICAL_HARDWARE_BLACKLIST: List[str] = [
    "trapezoidserver",
    "trapezoid",
    "com.chijimlee.trapezoid",
    "tvinput",
    "com.mediatek.tvinput",
    "dtv_svc",
    "autopair",
    "com.mediatek.autopair",
    "loadingequalizer",
    "com.mediatek.loadingequalizer",
    "tyylauncher",
    "com.chijimlee.tyylauncher",
    "tvsettingsplus",
    "com.android.tv.settings",
    "kxtj2server",
    "hotkey.dispatcher",
]

DEFAULT_OUTPUT_BIN_NAME = "WanboLightOS_v1.0.bin"
DEFAULT_LAUNCHER_REL_PATH = "launcher/app/build/outputs/apk/release/app-release.apk"
DEFAULT_FALLBACK_LAUNCHER_PATHS = [
    "launcher/app/build/outputs/apk/debug/app-debug.apk",
    "launcher/build/outputs/apk/release/app-release.apk",
    "launcher/build/outputs/apk/debug/app-debug.apk",
    "build/WanboLauncher.apk",
    "launcher/WanboLauncher.apk",
    "WanboLauncher.apk",
]
DEFAULT_SETTINGS_REL_PATH = "settings/app/build/outputs/apk/release/app-release.apk"
DEFAULT_FALLBACK_SETTINGS_PATHS = [
    "settings/app/build/outputs/apk/debug/app-debug.apk",
    "settings/build/outputs/apk/release/app-release.apk",
    "settings/build/outputs/apk/debug/app-debug.apk",
    "build/WanboSettings.apk",
    "settings/WanboSettings.apk",
    "WanboSettings.apk",
]
DEFAULT_MATERIAL_FILES_REL_PATHS = [
    "os-pipeline/apks/MaterialFiles.apk",
    "tools/apks/MaterialFiles.apk",
    "apks/MaterialFiles.apk",
    "build/MaterialFiles.apk",
    "MaterialFiles.apk",
]
DEFAULT_APTOIDE_REL_PATHS = [
    "os-pipeline/apks/AptoideTV.apk",
    "tools/apks/AptoideTV.apk",
    "apks/AptoideTV.apk",
    "build/AptoideTV.apk",
    "AptoideTV.apk",
]


class CriticalHardwareSafetyViolation(RuntimeError):
    """Raised when an operation attempts to modify or delete a critical hardware HAL or driver."""
    pass


class PipelineStepError(RuntimeError):
    """Raised when a pipeline step fails during firmware modification."""
    pass


@dataclass
class BuildConfiguration:
    """Configuration options for firmware build pipeline execution."""
    input_fw: Path
    output_fw: Path
    launcher_apk: Optional[Path] = None
    settings_apk: Optional[Path] = None
    material_files_apk: Optional[Path] = None
    aptoide_apk: Optional[Path] = None
    debloat_list: Optional[Path] = None
    build_prop_patch: Optional[Path] = None
    work_dir: Optional[Path] = None
    dry_run: bool = False
    verbose: bool = False
    keep_work_dir: bool = False


def is_emoji_code_point(cp: int) -> bool:
    """
    Determines whether a given Unicode code point represents an emoji character.

    @param {int} cp - The Unicode code point integer.
    @returns {bool} True if the code point falls within known emoji ranges, False otherwise.
    """
    if 0x1F600 <= cp <= 0x1F64F:
        return True  # Emoticons
    if 0x1F300 <= cp <= 0x1F5FF:
        return True  # Misc Symbols and Pictographs
    if 0x1F680 <= cp <= 0x1F6FF:
        return True  # Transport and Map
    if 0x1F700 <= cp <= 0x1F77F:
        return True  # Alchemical Symbols
    if 0x1F780 <= cp <= 0x1F7FF:
        return True  # Geometric Shapes Extended
    if 0x1F800 <= cp <= 0x1F8FF:
        return True  # Supplemental Arrows-C
    if 0x1F900 <= cp <= 0x1F9FF:
        return True  # Supplemental Symbols and Pictographs
    if 0x1FA00 <= cp <= 0x1FA6F:
        return True  # Chess Symbols
    if 0x1FA70 <= cp <= 0x1FAFF:
        return True  # Symbols and Pictographs Extended-A
    if 0x2600 <= cp <= 0x26FF:
        return True  # Misc symbols
    if 0x2700 <= cp <= 0x27BF:
        return True  # Dingbats
    if 0xFE00 <= cp <= 0xFE0F:
        return True  # Variation Selectors
    if 0x1F1E6 <= cp <= 0x1F1FF:
        return True  # Flags
    return False


def contains_emoji(text: str) -> bool:
    """
    Scans a text string to check for any emoji characters.

    @param {str} text - The string content to inspect.
    @returns {bool} True if at least one emoji character is present, False otherwise.
    """
    for char in text:
        if is_emoji_code_point(ord(char)):
            return True
    return False


def get_repo_root() -> Path:
    """
    Resolves the root directory path of the git repository.

    @returns {Path} Path object pointing to the root of the repository.
    """
    current_dir = Path(__file__).resolve().parent
    return current_dir.parent


def log_info(msg: str, verbose: bool = False) -> None:
    """
    Outputs an informational log message without emojis.

    @param {str} msg - The log message text.
    @param {bool} verbose - Ignored flag, output is always printed.
    @returns {None}
    """
    print(f"[INFO] {msg}")


def log_debug(msg: str, verbose: bool = False) -> None:
    """
    Outputs a debug log message when verbose mode is enabled.

    @param {str} msg - The debug message text.
    @param {bool} verbose - True if verbose logging is enabled.
    @returns {None}
    """
    if verbose:
        print(f"[DEBUG] {msg}")


def log_warn(msg: str) -> None:
    """
    Outputs a warning log message without emojis.

    @param {str} msg - The warning message text.
    @returns {None}
    """
    print(f"[WARN] {msg}", file=sys.stderr)


def log_error(msg: str) -> None:
    """
    Outputs an error log message without emojis.

    @param {str} msg - The error message text.
    @returns {None}
    """
    print(f"[ERROR] {msg}", file=sys.stderr)


def is_blacklisted(candidate: str) -> bool:
    """
    Verifies if a candidate file or directory name matches any entry in the critical HAL blacklist.

    @param {str} candidate - Target file path or directory name.
    @returns {bool} True if candidate violates hardware preservation blacklist, False otherwise.
    """
    normalized = candidate.lower().replace("\\", "/")
    for keyword in CRITICAL_HARDWARE_BLACKLIST:
        if keyword in normalized:
            return True
    return False


def parse_debloat_list(debloat_file: Path) -> List[str]:
    """
    Reads debloat-list.txt and extracts clean, non-empty, non-comment patterns.
    Validates that no debloat pattern itself violates the critical hardware blacklist.

    @param {Path} debloat_file - Path to debloat-list.txt.
    @returns {List[str]} List of debloat relative path glob patterns.
    """
    if not debloat_file.exists():
        raise FileNotFoundError(f"Debloat specification file not found: {debloat_file}")

    content = debloat_file.read_text(encoding="utf-8")
    patterns: List[str] = []

    for line_num, line in enumerate(content.splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        if is_blacklisted(line):
            raise CriticalHardwareSafetyViolation(
                f"Debloat list line {line_num} contains blacklisted critical hardware keyword: '{line}'"
            )

        patterns.append(line.replace("\\", "/"))

    return patterns


def find_matching_debloat_targets(system_root: Path, patterns: List[str]) -> List[Path]:
    """
    Scans the extracted system directory and matches files and directories against debloat patterns using glob and fnmatch.
    Strictly verifies each matched item against the critical hardware safety blacklist.

    @param {Path} system_root - Root directory of the extracted system filesystem.
    @param {List[str]} patterns - List of debloat patterns from debloat-list.txt.
    @returns {List[Path]} List of absolute Path objects targeted for debloat deletion.
    """
    matched_paths: Set[Path] = set()

    # Build collection of candidate paths under system_root
    all_candidates: List[Path] = []
    for root, dirs, files in os.walk(system_root, topdown=True):
        root_path = Path(root)
        for d in dirs:
            all_candidates.append(root_path / d)
        for f in files:
            all_candidates.append(root_path / f)

    for candidate in all_candidates:
        rel_path = candidate.relative_to(system_root).as_posix()
        # Support both 'app/...' and 'system/app/...' matching conventions
        rel_path_with_system = f"system/{rel_path}" if not rel_path.startswith("system/") else rel_path
        rel_path_no_system = rel_path[len("system/"):] if rel_path.startswith("system/") else rel_path

        matched = False
        for pattern in patterns:
            norm_pattern = pattern.strip("/")
            pattern_no_system = norm_pattern[len("system/"):] if norm_pattern.startswith("system/") else norm_pattern
            pattern_with_system = f"system/{norm_pattern}" if not norm_pattern.startswith("system/") else norm_pattern

            if (
                fnmatch.fnmatch(rel_path, norm_pattern)
                or fnmatch.fnmatch(rel_path_with_system, pattern_with_system)
                or fnmatch.fnmatch(rel_path_no_system, pattern_no_system)
                or rel_path == norm_pattern
                or rel_path_with_system == pattern_with_system
                or rel_path_no_system == pattern_no_system
            ):
                matched = True
                break

        if matched:
            # Step 6: Verify candidate against hardware safety blacklist
            # Pass rel_path or candidate.name to avoid false positives from host filesystem path
            if is_blacklisted(rel_path) or is_blacklisted(candidate.name):
                raise CriticalHardwareSafetyViolation(
                    f"CRITICAL SAFETY VIOLATION: Candidate deletion target matches hardware blacklist: '{rel_path}'"
                )
            matched_paths.add(candidate)

    # Filter out child paths if parent directory is already marked for removal
    sorted_paths = sorted(matched_paths, key=lambda p: len(p.parts))
    final_targets: List[Path] = []
    for path in sorted_paths:
        if not any(parent in final_targets for parent in path.parents):
            final_targets.append(path)

    return sorted(final_targets)


def apply_debloat(system_root: Path, debloat_file: Path, dry_run: bool = False, verbose: bool = False) -> List[Path]:
    """
    Executes Step 5 and Step 6 of the pipeline: debloat list application with glob matching and safety verification.

    @param {Path} system_root - Extracted system partition directory.
    @param {Path} debloat_file - Path to debloat-list.txt.
    @param {bool} dry_run - True to simulate without deleting files.
    @param {bool} verbose - True to log detailed deletion targets.
    @returns {List[Path]} List of removed or simulated-removed file/directory paths.
    """
    patterns = parse_debloat_list(debloat_file)
    log_info(f"Loaded {len(patterns)} debloat patterns from {debloat_file.name}", verbose)

    targets = find_matching_debloat_targets(system_root, patterns)
    log_info(f"Identified {len(targets)} debloat targets for removal (Safety check: PASSED)", verbose)

    for target in targets:
        rel_str = target.relative_to(system_root).as_posix()
        if dry_run:
            log_debug(f"[DRY-RUN] Would remove: {rel_str}", verbose)
        else:
            log_debug(f"Removing: {rel_str}", verbose)
            if target.is_dir():
                shutil.rmtree(target, ignore_errors=False)
            elif target.exists():
                target.unlink()

    return targets


def find_launcher_apk(explicit_path: Optional[Path] = None, repo_root: Optional[Path] = None) -> Optional[Path]:
    """
    Locates the compiled Android TV Launcher APK from explicit path or standard build locations.

    @param {Optional[Path]} explicit_path - User-supplied path via CLI.
    @param {Optional[Path]} repo_root - Root directory of the repository.
    @returns {Optional[Path]} Path to existing launcher APK or None if not found.
    """
    if explicit_path:
        if explicit_path.exists():
            return explicit_path.resolve()
        return None

    root = repo_root or get_repo_root()

    primary = root / DEFAULT_LAUNCHER_REL_PATH
    if primary.exists():
        return primary.resolve()

    for fallback in DEFAULT_FALLBACK_LAUNCHER_PATHS:
        fallback_path = root / fallback
        if fallback_path.exists():
            return fallback_path.resolve()

    return None


def find_settings_apk(explicit_path: Optional[Path] = None, repo_root: Optional[Path] = None) -> Optional[Path]:
    """
    Locates the compiled WanboSettings APK from explicit path or standard build locations.

    @param {Optional[Path]} explicit_path - User-supplied path via CLI.
    @param {Optional[Path]} repo_root - Root directory of the repository.
    @returns {Optional[Path]} Path to existing settings APK or None if not found.
    """
    if explicit_path:
        if explicit_path.exists():
            return explicit_path.resolve()
        return None

    root = repo_root or get_repo_root()

    primary = root / DEFAULT_SETTINGS_REL_PATH
    if primary.exists():
        return primary.resolve()

    for fallback in DEFAULT_FALLBACK_SETTINGS_PATHS:
        fallback_path = root / fallback
        if fallback_path.exists():
            return fallback_path.resolve()

    return None


def find_material_files_apk(explicit_path: Optional[Path] = None, repo_root: Optional[Path] = None) -> Optional[Path]:
    """
    Locates the MaterialFiles APK from explicit path or standard storage locations.

    @param {Optional[Path]} explicit_path - User-supplied path via CLI.
    @param {Optional[Path]} repo_root - Root directory of the repository.
    @returns {Optional[Path]} Path to existing MaterialFiles APK or None if not found.
    """
    if explicit_path:
        if explicit_path.exists():
            return explicit_path.resolve()
        return None

    root = repo_root or get_repo_root()
    for rel_path in DEFAULT_MATERIAL_FILES_REL_PATHS:
        candidate = root / rel_path
        if candidate.exists():
            return candidate.resolve()

    return None


def find_aptoide_apk(explicit_path: Optional[Path] = None, repo_root: Optional[Path] = None) -> Optional[Path]:
    """
    Locates the AptoideTV APK from explicit path or standard storage locations.

    @param {Optional[Path]} explicit_path - User-supplied path via CLI.
    @param {Optional[Path]} repo_root - Root directory of the repository.
    @returns {Optional[Path]} Path to existing AptoideTV APK or None if not found.
    """
    if explicit_path:
        if explicit_path.exists():
            return explicit_path.resolve()
        return None

    root = repo_root or get_repo_root()
    for rel_path in DEFAULT_APTOIDE_REL_PATHS:
        candidate = root / rel_path
        if candidate.exists():
            return candidate.resolve()

    return None


def validate_prerequisites(config: BuildConfiguration) -> None:
    """
    Executes Step 1: Validates existence of input stock firmware and compiled launcher APK.

    @param {BuildConfiguration} config - Build configuration options.
    @returns {None}
    """
    log_info("Step 1/9: Validating build prerequisites...", config.verbose)

    if not config.dry_run and not config.input_fw.exists():
        raise FileNotFoundError(
            f"Input stock firmware binary not found: {config.input_fw}. "
            "Please specify a valid firmware file via --input-fw or positional argument."
        )

    if config.debloat_list and not config.debloat_list.exists():
        raise FileNotFoundError(f"Debloat specification file not found: {config.debloat_list}")

    if config.build_prop_patch and not config.build_prop_patch.exists():
        raise FileNotFoundError(f"build.prop patch file not found: {config.build_prop_patch}")

    resolved_launcher = find_launcher_apk(config.launcher_apk)
    if not config.dry_run and not resolved_launcher:
        raise FileNotFoundError(
            f"Compiled Wanbo Launcher APK not found at {config.launcher_apk or DEFAULT_LAUNCHER_REL_PATH}. "
            "Please build the launcher first via 'make launcher' or specify --launcher-apk."
        )

    if resolved_launcher:
        config.launcher_apk = resolved_launcher
        log_info(f"Validated launcher APK: {config.launcher_apk}", config.verbose)
    else:
        log_info("[DRY-RUN] Launcher APK validation simulated.", config.verbose)

    resolved_settings = find_settings_apk(config.settings_apk)
    if not config.dry_run and not resolved_settings:
        raise FileNotFoundError(
            f"Compiled Wanbo Settings APK not found at {config.settings_apk or DEFAULT_SETTINGS_REL_PATH}. "
            "Please build the settings app first via 'make settings' or specify --settings-apk."
        )

    if resolved_settings:
        config.settings_apk = resolved_settings
        log_info(f"Validated settings APK: {config.settings_apk}", config.verbose)
    else:
        log_info("[DRY-RUN] Settings APK validation simulated.", config.verbose)

    resolved_material = find_material_files_apk(config.material_files_apk)
    if resolved_material:
        config.material_files_apk = resolved_material
        log_info(f"Validated MaterialFiles APK: {config.material_files_apk}", config.verbose)
    else:
        log_warn("MaterialFiles APK not found at standard paths. MaterialFiles injection will be skipped.")

    resolved_aptoide = find_aptoide_apk(config.aptoide_apk)
    if resolved_aptoide:
        config.aptoide_apk = resolved_aptoide
        log_info(f"Validated AptoideTV APK: {config.aptoide_apk}", config.verbose)
    else:
        log_warn("AptoideTV APK not found at standard paths. AptoideTV injection will be skipped.")

    log_info("Step 1/9 complete: All prerequisites validated successfully.", config.verbose)


def find_mstar_tool(tool_name: str) -> Optional[Path]:
    """
    Discovers an MStar firmware utility script or binary in PATH or within local repository directories.

    @param {str} tool_name - Executable or script name (e.g. 'unpack.py' or 'pack.py').
    @returns {Optional[Path]} Path to discovered tool, or None if not found.
    """
    found = shutil.which(tool_name)
    if found:
        return Path(found)
    repo_root = get_repo_root()
    local_candidates = [
        repo_root / "os-pipeline" / "tools" / "mstar-bin-tool" / tool_name,
        repo_root / "tools" / "mstar-bin-tool" / tool_name,
        repo_root / "os-pipeline" / "tools" / tool_name,
        repo_root / "tools" / tool_name,
    ]
    for cand in local_candidates:
        if cand.exists() and cand.is_file():
            return cand
    return None


def unpack_mstar_binary(input_fw: Path, unpack_dir: Path, dry_run: bool = False, verbose: bool = False) -> Path:
    """
    Executes Step 2: Unpacks MStar/MediaTek firmware binary using mstar-bin-tool or partition extractors.

    @param {Path} input_fw - Path to input firmware binary.
    @param {Path} unpack_dir - Destination directory for unpacked partition images.
    @param {bool} dry_run - True to simulate unpack.
    @param {bool} verbose - True for detailed debug logging.
    @returns {Path} Path to directory containing unpacked partitions.
    """
    log_info("Step 2/9: Unpacking MStar/MediaTek firmware binary...", verbose)
    unpack_dir.mkdir(parents=True, exist_ok=True)

    if dry_run:
        log_info(f"[DRY-RUN] Simulated MStar unpack of {input_fw.name} into {unpack_dir}", verbose)
        dummy_sparse = unpack_dir / "system.img"
        if not dummy_sparse.exists():
            dummy_sparse.write_bytes(b"MOCK_SPARSE_IMAGE_DATA")
        return unpack_dir

    # Check for mstar-bin-tool unpack script or executable
    unpack_tool = find_mstar_tool("unpack.py") or shutil.which("mstar-bin-tool")
    if unpack_tool:
        tool_str = str(unpack_tool)
        if tool_str.lower().endswith(".py"):
            cmd = [sys.executable, tool_str, str(input_fw), str(unpack_dir)]
        else:
            cmd = [tool_str, str(input_fw), str(unpack_dir)]
        log_info(f"Running unpack command: {' '.join(cmd)}", verbose)
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise PipelineStepError(f"mstar-bin-tool unpack failed: {res.stderr}")
    else:
        # Fallback check for direct partition structure or custom extractor
        sparse_img = unpack_dir / "system.img"
        if not sparse_img.exists():
            raise PipelineStepError(
                f"No suitable MStar firmware unpacker tool found in PATH (unpack.py / mstar-bin-tool). "
                f"Cannot unpack {input_fw}."
            )

    log_info("Step 2/9 complete: Firmware unpacked successfully.", verbose)
    return unpack_dir


def convert_sparse_to_raw(sparse_img: Path, raw_img: Path, dry_run: bool = False, verbose: bool = False) -> Path:
    """
    Executes Step 3: Converts sparse system.img to raw ext4 image via simg2img.

    @param {Path} sparse_img - Path to input sparse system.img.
    @param {Path} raw_img - Path to output raw ext4 system_raw.img.
    @param {bool} dry_run - True to simulate conversion.
    @param {bool} verbose - True for detailed debug logging.
    @returns {Path} Path to converted raw ext4 image.
    """
    log_info("Step 3/9: Converting sparse system.img to raw ext4 image via simg2img...", verbose)

    if dry_run:
        log_info(f"[DRY-RUN] Simulated simg2img {sparse_img.name} -> {raw_img.name}", verbose)
        if not raw_img.exists():
            raw_img.write_bytes(b"MOCK_RAW_EXT4_DATA")
        return raw_img

    simg2img_tool = shutil.which("simg2img")
    if not simg2img_tool:
        raise PipelineStepError(
            "simg2img tool not found in PATH. Please install android-tools (simg2img) to proceed."
        )

    cmd = [simg2img_tool, str(sparse_img), str(raw_img)]
    log_info(f"Running: {' '.join(cmd)}", verbose)
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise PipelineStepError(f"simg2img conversion failed: {res.stderr}")

    log_info("Step 3/9 complete: Sparse image converted to raw ext4.", verbose)
    return raw_img


def extract_system_image(raw_img: Path, extract_dir: Path, dry_run: bool = False, verbose: bool = False) -> Path:
    """
    Executes Step 4: Mounts or extracts system_raw.img to directory.

    @param {Path} raw_img - Path to raw ext4 image.
    @param {Path} extract_dir - Directory where filesystem tree is mounted/extracted.
    @param {bool} dry_run - True to simulate extraction.
    @param {bool} verbose - True for debug logging.
    @returns {Path} Path to extracted filesystem root.
    """
    log_info("Step 4/9: Extracting system_raw.img filesystem...", verbose)
    extract_dir.mkdir(parents=True, exist_ok=True)

    if dry_run:
        log_info(f"[DRY-RUN] Simulated filesystem extraction of {raw_img.name} to {extract_dir}", verbose)
        # Create minimal synthetic mock directory structure for dry run testing
        (extract_dir / "app").mkdir(parents=True, exist_ok=True)
        (extract_dir / "priv-app").mkdir(parents=True, exist_ok=True)
        (extract_dir / "system").mkdir(parents=True, exist_ok=True)
        mock_build_prop = extract_dir / "system" / "build.prop"
        if not mock_build_prop.exists():
            mock_build_prop.write_text("ro.build.version.release=9\nro.product.model=Wanbo T2 Max\n", encoding="utf-8")
        return extract_dir

    # In production on Linux/macOS/Windows, use 7z or loop mount or e2tools
    p7zip = shutil.which("7z") or shutil.which("7za")
    if p7zip:
        cmd = [p7zip, "x", f"-o{extract_dir}", "-y", str(raw_img)]
        log_info(f"Running: {' '.join(cmd)}", verbose)
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise PipelineStepError(f"7z extraction of raw ext4 failed: {res.stderr}")
    else:
        raise PipelineStepError("No suitable ext4 extractor found (7z/mount). Cannot extract raw ext4 image.")

    log_info("Step 4/9 complete: Filesystem extracted successfully.", verbose)
    return extract_dir


def inject_packages(
    system_root: Path,
    launcher_apk: Optional[Path] = None,
    settings_apk: Optional[Path] = None,
    material_files_apk: Optional[Path] = None,
    aptoide_apk: Optional[Path] = None,
    dry_run: bool = False,
    verbose: bool = False,
) -> Dict[str, Path]:
    """
    Executes Step 7: Injects compiled applications into the system image:
      - WanboLauncher.apk into priv-app/WanboLauncher/ (0644, dir 0755)
      - WanboSettings.apk into priv-app/WanboSettings/ (0644, dir 0755)
      - AptoideTV.apk into priv-app/AptoideTV/ (0644, dir 0755)
      - MaterialFiles.apk into app/MaterialFiles/ (0644, dir 0755)

    @param {Path} system_root - Extracted system filesystem root directory.
    @param {Optional[Path]} launcher_apk - Path to compiled WanboLauncher APK.
    @param {Optional[Path]} settings_apk - Path to compiled WanboSettings APK.
    @param {Optional[Path]} material_files_apk - Path to MaterialFiles APK.
    @param {Optional[Path]} aptoide_apk - Path to AptoideTV APK.
    @param {bool} dry_run - True to simulate injection without file creation.
    @param {bool} verbose - True for detailed debug logging.
    @returns {Dict[str, Path]} Dictionary mapping injected component names to their final destination paths.
    """
    log_info("Step 7/9: Injecting WanboLauncher, WanboSettings, AptoideTV, and MaterialFiles APKs...", verbose)
    injected: Dict[str, Path] = {}

    # Check whether system root is mounted flat or with system/ subdirectory
    has_system_sub = (system_root / "system").is_dir()
    base_prefix = system_root / "system" if has_system_sub else system_root

    # 1. Inject WanboLauncher into priv-app/WanboLauncher/
    launcher_dir = base_prefix / "priv-app" / "WanboLauncher"
    launcher_dest = launcher_dir / "WanboLauncher.apk"

    if dry_run:
        log_info(f"[DRY-RUN] Simulated injection: WanboLauncher.apk -> {launcher_dest} (permissions 0644)", verbose)
        injected["WanboLauncher"] = launcher_dest
    else:
        launcher_dir.mkdir(parents=True, exist_ok=True)
        try:
            os.chmod(launcher_dir, 0o755)
        except OSError:
            pass

        if launcher_apk and launcher_apk.exists():
            shutil.copy2(launcher_apk, launcher_dest)
        else:
            # Fallback placeholder if simulated/stub
            launcher_dest.write_bytes(b"WANBO_LAUNCHER_MOCK_APK")

        try:
            os.chmod(launcher_dest, 0o644)
        except OSError:
            pass

        log_info(f"Injected WanboLauncher.apk to {launcher_dest} (mode 0644)", verbose)
        injected["WanboLauncher"] = launcher_dest

    # 2. Inject WanboSettings into priv-app/WanboSettings/
    settings_dir = base_prefix / "priv-app" / "WanboSettings"
    settings_dest = settings_dir / "WanboSettings.apk"

    if dry_run:
        log_info(f"[DRY-RUN] Simulated injection: WanboSettings.apk -> {settings_dest} (permissions 0644)", verbose)
        injected["WanboSettings"] = settings_dest
    else:
        settings_dir.mkdir(parents=True, exist_ok=True)
        try:
            os.chmod(settings_dir, 0o755)
        except OSError:
            pass

        if settings_apk and settings_apk.exists():
            shutil.copy2(settings_apk, settings_dest)
        else:
            # Fallback placeholder if simulated/stub
            settings_dest.write_bytes(b"WANBO_SETTINGS_MOCK_APK")

        try:
            os.chmod(settings_dest, 0o644)
        except OSError:
            pass

        log_info(f"Injected WanboSettings.apk to {settings_dest} (mode 0644)", verbose)
        injected["WanboSettings"] = settings_dest

    # 3. Inject AptoideTV into priv-app/AptoideTV/
    if aptoide_apk and (aptoide_apk.exists() or dry_run):
        aptoide_dir = base_prefix / "priv-app" / "AptoideTV"
        aptoide_dest = aptoide_dir / "AptoideTV.apk"

        if dry_run:
            log_info(f"[DRY-RUN] Simulated injection: AptoideTV.apk -> {aptoide_dest} (permissions 0644)", verbose)
            injected["AptoideTV"] = aptoide_dest
        else:
            aptoide_dir.mkdir(parents=True, exist_ok=True)
            try:
                os.chmod(aptoide_dir, 0o755)
            except OSError:
                pass

            shutil.copy2(aptoide_apk, aptoide_dest)
            try:
                os.chmod(aptoide_dest, 0o644)
            except OSError:
                pass

            log_info(f"Injected AptoideTV.apk to {aptoide_dest} (mode 0644)", verbose)
            injected["AptoideTV"] = aptoide_dest
    else:
        log_info("AptoideTV APK not present; skipping AptoideTV injection.", verbose)

    # 4. Inject MaterialFiles into app/MaterialFiles/
    if material_files_apk and (material_files_apk.exists() or dry_run):
        material_dir = base_prefix / "app" / "MaterialFiles"
        material_dest = material_dir / "MaterialFiles.apk"

        if dry_run:
            log_info(f"[DRY-RUN] Simulated injection: MaterialFiles.apk -> {material_dest} (permissions 0644)", verbose)
            injected["MaterialFiles"] = material_dest
        else:
            material_dir.mkdir(parents=True, exist_ok=True)
            try:
                os.chmod(material_dir, 0o755)
            except OSError:
                pass

            shutil.copy2(material_files_apk, material_dest)
            try:
                os.chmod(material_dest, 0o644)
            except OSError:
                pass

            log_info(f"Injected MaterialFiles.apk to {material_dest} (mode 0644)", verbose)
            injected["MaterialFiles"] = material_dest
    else:
        log_info("MaterialFiles APK not present; skipping MaterialFiles injection.", verbose)

    log_info("Step 7/9 complete: Packages injected successfully.", verbose)
    return injected


def patch_build_prop(
    system_root: Path,
    patch_file: Path,
    dry_run: bool = False,
    verbose: bool = False,
) -> Path:
    """
    Executes Step 8: Patches system/build.prop with patch entries.
    Replaces existing keys in-place to avoid write-once conflicts on ro.* properties,
    and appends newly introduced properties.

    @param {Path} system_root - Extracted system filesystem root directory.
    @param {Path} patch_file - Path to build.prop.patch.
    @param {bool} dry_run - True to simulate patching.
    @param {bool} verbose - True for detailed debug logging.
    @returns {Path} Path to patched build.prop file.
    """
    log_info("Step 8/9: Patching system/build.prop with low-RAM optimizations...", verbose)

    if not patch_file.exists():
        raise FileNotFoundError(f"Patch file not found: {patch_file}")

    patch_content = patch_file.read_text(encoding="utf-8")

    # Locate build.prop
    candidates = [
        system_root / "system" / "build.prop",
        system_root / "build.prop",
    ]
    target_build_prop: Optional[Path] = None
    for cand in candidates:
        if cand.exists():
            target_build_prop = cand
            break

    if not target_build_prop:
        # Default to system_root / system / build.prop
        target_build_prop = system_root / "system" / "build.prop"
        if not dry_run:
            target_build_prop.parent.mkdir(parents=True, exist_ok=True)
            target_build_prop.write_text("# Stock build.prop\n", encoding="utf-8")

    if dry_run:
        log_info(f"[DRY-RUN] Simulated patch of {patch_file.name} to {target_build_prop}", verbose)
        return target_build_prop

    existing_content = ""
    if target_build_prop.exists():
        existing_content = target_build_prop.read_text(encoding="utf-8")

    # Parse key-value mappings from patch file
    patch_props: Dict[str, str] = {}
    for line in patch_content.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key, val = stripped.split("=", 1)
            patch_props[key.strip()] = val.strip()

    # Process existing lines, replacing existing keys in-place
    new_lines: List[str] = []
    replaced_keys: Set[str] = set()

    for line in existing_content.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key, _ = stripped.split("=", 1)
            clean_key = key.strip()
            if clean_key in patch_props:
                if clean_key not in replaced_keys:
                    new_lines.append(f"{clean_key}={patch_props[clean_key]}")
                    replaced_keys.add(clean_key)
                continue
        new_lines.append(line)

    # Determine remaining patch lines to append (keys not already replaced in-place)
    remaining_lines: List[str] = []
    for line in patch_content.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key, _ = stripped.split("=", 1)
            if key.strip() in replaced_keys:
                continue
        remaining_lines.append(line)

    base_text = "\n".join(new_lines).rstrip()
    remaining_text = "\n".join(remaining_lines).strip()

    if remaining_text:
        separator = "\n\n" if base_text else ""
        final_content = (
            base_text
            + separator
            + "# ==============================================================================\n"
            + "# WanboLightOS Automated Injection Patch\n"
            + "# ==============================================================================\n"
            + remaining_text
            + "\n"
        )
    else:
        final_content = base_text + "\n"

    target_build_prop.write_text(final_content, encoding="utf-8")
    try:
        os.chmod(target_build_prop, 0o644)
    except OSError:
        pass

    log_info(f"Patched {patch_file.name} to {target_build_prop} (mode 0644)", verbose)
    log_info("Step 8/9 complete: build.prop patched successfully.", verbose)
    return target_build_prop


def repack_system_image(
    system_extract_dir: Path,
    system_raw_output: Path,
    unpacked_dir: Optional[Path] = None,
    dry_run: bool = False,
    verbose: bool = False,
) -> Path:
    """
    Executes raw ext4 filesystem repacking from extracted system directory tree.
    Rebuilds system_raw.img using make_ext4fs or mke2fs + e2fsdroid before sparse conversion.
    Preserves SELinux security contexts if file_contexts is present in the unpacked tree or system partition.

    @param {Path} system_extract_dir - Directory containing extracted and modified system files.
    @param {Path} system_raw_output - Destination path for rebuilt raw ext4 image.
    @param {Optional[Path]} unpacked_dir - Optional path to unpacked firmware tree for locating file_contexts.
    @param {bool} dry_run - True to simulate repacking without executing external host tools.
    @param {bool} verbose - True for detailed debug logging.
    @returns {Path} Path to repacked raw ext4 image.
    """
    log_info("Step 8b/9: Repacking extracted system filesystem into raw ext4 image...", verbose)

    if dry_run:
        log_info(
            f"[DRY-RUN] Simulated ext4 filesystem repack of {system_extract_dir} -> {system_raw_output.name}",
            verbose,
        )
        system_raw_output.write_bytes(b"MOCK_REPACKED_RAW_EXT4_DATA")
        return system_raw_output

    partition_size = 1073741824
    if system_raw_output.exists():
        existing_size = system_raw_output.stat().st_size
        if existing_size > 0:
            partition_size = existing_size

    # Discover SELinux file contexts file to preserve Android 9 security labels
    selinux_contexts_candidates = []
    if unpacked_dir:
        selinux_contexts_candidates.extend([
            unpacked_dir / "file_contexts.bin",
            unpacked_dir / "file_contexts",
            unpacked_dir / "system.file_contexts",
        ])
    selinux_contexts_candidates.extend([
        system_extract_dir / "etc" / "selinux" / "plat_file_contexts",
        system_extract_dir / "etc" / "selinux" / "file_contexts.bin",
        system_extract_dir / "file_contexts.bin",
    ])
    file_contexts_path = next((p for p in selinux_contexts_candidates if p.exists() and p.is_file()), None)
    if file_contexts_path:
        log_info(f"Using SELinux file contexts: {file_contexts_path}", verbose)

    make_ext4fs_tool = shutil.which("make_ext4fs")
    mke2fs_tool = shutil.which("mke2fs")
    e2fsdroid_tool = shutil.which("e2fsdroid")

    if make_ext4fs_tool:
        cmd = [
            make_ext4fs_tool,
            "-l",
            str(partition_size),
            "-a",
            "system",
        ]
        if file_contexts_path:
            cmd.extend(["-S", str(file_contexts_path)])
        cmd.extend([
            str(system_raw_output),
            str(system_extract_dir),
        ])
        log_info(f"Running make_ext4fs: {' '.join(cmd)}", verbose)
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise PipelineStepError(f"make_ext4fs repacking failed: {res.stderr}")
    elif mke2fs_tool and e2fsdroid_tool:
        blocks = partition_size // 4096
        cmd_mke2fs = [
            mke2fs_tool,
            "-t",
            "ext4",
            "-b",
            "4096",
            str(system_raw_output),
            str(blocks),
        ]
        log_info(f"Running mke2fs: {' '.join(cmd_mke2fs)}", verbose)
        res_mk = subprocess.run(cmd_mke2fs, capture_output=True, text=True)
        if res_mk.returncode != 0:
            raise PipelineStepError(f"mke2fs failed: {res_mk.stderr}")

        cmd_e2fs = [
            e2fsdroid_tool,
            "-e",
        ]
        if file_contexts_path:
            cmd_e2fs.extend(["-S", str(file_contexts_path)])
        cmd_e2fs.extend([
            "-a",
            "/system",
            "-f",
            str(system_extract_dir),
            str(system_raw_output),
        ])
        log_info(f"Running e2fsdroid: {' '.join(cmd_e2fs)}", verbose)
        res_e2fs = subprocess.run(cmd_e2fs, capture_output=True, text=True)
        if res_e2fs.returncode != 0:
            raise PipelineStepError(f"e2fsdroid failed: {res_e2fs.stderr}")
    else:
        raise PipelineStepError(
            "No suitable ext4 repacking tool found in PATH (make_ext4fs or mke2fs+e2fsdroid). "
            "Cannot repack raw ext4 system image."
        )

    log_info("Ext4 filesystem repacked successfully.", verbose)
    return system_raw_output


def convert_raw_to_sparse(raw_img: Path, sparse_img: Path, dry_run: bool = False, verbose: bool = False) -> Path:
    """
    Executes first part of Step 9: Re-converts raw ext4 filesystem to Android sparse image via img2simg.

    @param {Path} raw_img - Path to modified raw ext4 image.
    @param {Path} sparse_img - Path to output sparse system.img.
    @param {bool} dry_run - True to simulate conversion.
    @param {bool} verbose - True for debug logging.
    @returns {Path} Path to output sparse image.
    """
    log_info("Step 9a/9: Re-converting raw ext4 to sparse system.img via img2simg...", verbose)

    if dry_run:
        log_info(f"[DRY-RUN] Simulated img2simg {raw_img.name} -> {sparse_img.name}", verbose)
        sparse_img.write_bytes(b"MOCK_NEW_SPARSE_IMG")
        return sparse_img

    img2simg_tool = shutil.which("img2simg")
    if not img2simg_tool:
        raise PipelineStepError("img2simg tool not found in PATH. Cannot repack sparse image.")

    cmd = [img2simg_tool, str(raw_img), str(sparse_img)]
    log_info(f"Running: {' '.join(cmd)}", verbose)
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise PipelineStepError(f"img2simg conversion failed: {res.stderr}")

    return sparse_img


def repack_mstar_binary(unpacked_dir: Path, output_fw: Path, dry_run: bool = False, verbose: bool = False) -> Path:
    """
    Executes second part of Step 9: Re-packs MStar partition directory into final flashable USB firmware binary via pack.py.

    @param {Path} unpacked_dir - Directory containing modified partitions.
    @param {Path} output_fw - Destination path for final flashable USB firmware binary.
    @param {bool} dry_run - True to simulate repack.
    @param {bool} verbose - True for debug logging.
    @returns {Path} Path to generated firmware binary.
    """
    log_info(f"Step 9b/9: Repacking partitions into final firmware binary: {output_fw.name}...", verbose)
    output_fw.parent.mkdir(parents=True, exist_ok=True)

    if dry_run:
        log_info(f"[DRY-RUN] Simulated pack.py {unpacked_dir} -> {output_fw}", verbose)
        output_fw.write_bytes(b"MOCK_FLASHABLE_WANBO_LIGHT_OS_FIRMWARE_BINARY")
        return output_fw

    pack_tool = find_mstar_tool("pack.py") or shutil.which("mstar-bin-tool")
    if pack_tool:
        tool_str = str(pack_tool)
        if tool_str.lower().endswith(".py"):
            cmd = [sys.executable, tool_str, str(unpacked_dir), str(output_fw)]
        else:
            cmd = [tool_str, str(unpacked_dir), str(output_fw)]
        log_info(f"Running: {' '.join(cmd)}", verbose)
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise PipelineStepError(f"Firmware repacking failed: {res.stderr}")
    else:
        raise PipelineStepError(
            "pack.py / mstar-bin-tool repack utility not found in PATH. Cannot assemble final binary."
        )

    log_info(f"Step 9/9 complete: Output firmware created at {output_fw}", verbose)
    return output_fw


def run_pipeline(config: BuildConfiguration) -> int:
    """
    Executes the complete 9-step firmware modification pipeline.

    @param {BuildConfiguration} config - Complete build configuration.
    @returns {int} Exit code 0 on success, non-zero on failure.
    """
    log_info("Starting WanboLightOS Firmware Build Pipeline", config.verbose)
    log_info("Target Hardware: Wanbo T2 Max (armeabi-v7a, Android 9 Pie)", config.verbose)

    try:
        # Step 1: Validate prerequisites
        validate_prerequisites(config)

        # Setup working directory layout
        work_dir = config.work_dir
        work_dir.mkdir(parents=True, exist_ok=True)
        unpacked_dir = work_dir / "unpacked"
        system_sparse = unpacked_dir / "system.img"
        system_raw = work_dir / "system_raw.img"
        system_extract_dir = work_dir / "system_extracted"

        # Step 2: Unpack MStar binary
        unpack_mstar_binary(config.input_fw, unpacked_dir, dry_run=config.dry_run, verbose=config.verbose)

        # Step 3: Convert sparse system.img to raw ext4
        convert_sparse_to_raw(system_sparse, system_raw, dry_run=config.dry_run, verbose=config.verbose)

        # Step 4: Extract system_raw.img
        extract_system_image(system_raw, system_extract_dir, dry_run=config.dry_run, verbose=config.verbose)

        # Step 5 & 6: Debloat with glob expansion and critical safety verification
        log_info("Step 5/9 & 6/9: Applying debloat specification and enforcing HAL safety blacklist...", config.verbose)
        apply_debloat(system_extract_dir, config.debloat_list, dry_run=config.dry_run, verbose=config.verbose)

        # Step 7: Inject WanboLauncher, WanboSettings, AptoideTV, and MaterialFiles APKs
        inject_packages(
            system_extract_dir,
            launcher_apk=config.launcher_apk,
            settings_apk=config.settings_apk,
            material_files_apk=config.material_files_apk,
            aptoide_apk=config.aptoide_apk,
            dry_run=config.dry_run,
            verbose=config.verbose,
        )

        # Step 8: Patch build.prop
        patch_build_prop(system_extract_dir, config.build_prop_patch, dry_run=config.dry_run, verbose=config.verbose)

        # Repack modified filesystem into raw ext4 image before sparse conversion
        repack_system_image(system_extract_dir, system_raw, unpacked_dir=unpacked_dir, dry_run=config.dry_run, verbose=config.verbose)

        # Step 9: Re-convert to sparse and repack final binary
        new_sparse = unpacked_dir / "system.img"
        convert_raw_to_sparse(system_raw, new_sparse, dry_run=config.dry_run, verbose=config.verbose)
        repack_mstar_binary(unpacked_dir, config.output_fw, dry_run=config.dry_run, verbose=config.verbose)

        log_info(f"WanboLightOS build pipeline completed successfully: {config.output_fw}", config.verbose)

        if not config.keep_work_dir and not config.dry_run and work_dir.exists():
            log_info(f"Cleaning up temporary workspace: {work_dir}", config.verbose)
            shutil.rmtree(work_dir, ignore_errors=True)

        return 0

    except CriticalHardwareSafetyViolation as e:
        log_error(f"HAL PRESERVATION ERROR: {e}")
        return 2
    except PipelineStepError as e:
        log_error(f"PIPELINE STEP FAILED: {e}")
        return 3
    except Exception as e:
        log_error(f"FATAL ERROR: {e}")
        return 1


def create_argument_parser() -> argparse.ArgumentParser:
    """
    Configures and returns the command line argument parser for build_rom.py.

    @returns {argparse.ArgumentParser} Configured ArgumentParser instance.
    """
    repo_root = get_repo_root()
    default_debloat = repo_root / "os-pipeline" / "patches" / "debloat-list.txt"
    default_build_prop = repo_root / "os-pipeline" / "patches" / "build.prop.patch"
    default_launcher = repo_root / DEFAULT_LAUNCHER_REL_PATH
    default_settings = repo_root / DEFAULT_SETTINGS_REL_PATH
    default_work_dir = repo_root / "build" / "rom_work"

    parser = argparse.ArgumentParser(
        description="WanboLightOS Automated Firmware Unpack, Debloat, Injection & Repack Pipeline."
    )
    # Positional or optional flags for input/output firmware
    parser.add_argument(
        "pos_input_fw",
        nargs="?",
        default=None,
        help="Path to stock input firmware binary (e.g. stock_firmware.bin).",
    )
    parser.add_argument(
        "pos_output_fw",
        nargs="?",
        default=None,
        help=f"Path to destination flashable firmware binary (default: {DEFAULT_OUTPUT_BIN_NAME}).",
    )
    parser.add_argument(
        "-i", "--input-fw",
        dest="flag_input_fw",
        default=None,
        help="Explicit flag for stock input firmware binary path.",
    )
    parser.add_argument(
        "-o", "--output-fw",
        dest="flag_output_fw",
        default=None,
        help=f"Explicit flag for destination firmware binary path (default: {DEFAULT_OUTPUT_BIN_NAME}).",
    )
    parser.add_argument(
        "--launcher-apk",
        default=None,
        help=f"Path to compiled WanboLauncher APK (default: autodetected from {default_launcher}).",
    )
    parser.add_argument(
        "--settings-apk",
        default=None,
        help=f"Path to compiled WanboSettings APK (default: autodetected from {default_settings}).",
    )
    parser.add_argument(
        "--material-files-apk",
        default=None,
        help="Path to MaterialFiles APK (optional, default: autodetected from os-pipeline/apks/MaterialFiles.apk).",
    )
    parser.add_argument(
        "--aptoide-apk",
        default=None,
        help="Path to AptoideTV APK (optional, default: autodetected from os-pipeline/apks/AptoideTV.apk).",
    )
    parser.add_argument(
        "--debloat-list",
        default=str(default_debloat),
        help=f"Path to debloat specification list (default: {default_debloat}).",
    )
    parser.add_argument(
        "--build-prop-patch",
        default=str(default_build_prop),
        help=f"Path to build.prop patch file (default: {default_build_prop}).",
    )
    parser.add_argument(
        "--work-dir",
        default=str(default_work_dir),
        help=f"Temporary working directory for extraction (default: {default_work_dir}).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Perform dry-run simulation of all steps without invoking external system binaries or deleting files.",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable detailed debug logging.",
    )
    parser.add_argument(
        "--keep-work-dir",
        action="store_true",
        help="Preserve intermediate extracted workspace directory after pipeline completes.",
    )

    return parser


def parse_args_to_config(argv: Optional[List[str]] = None) -> BuildConfiguration:
    """
    Parses CLI arguments and resolves them into a structured BuildConfiguration.

    @param {Optional[List[str]]} argv - Command line argument strings.
    @returns {BuildConfiguration} Fully resolved build configuration.
    """
    parser = create_argument_parser()
    args = parser.parse_args(argv)

    input_fw_str = args.flag_input_fw or args.pos_input_fw or "stock_firmware.bin"
    output_fw_str = args.flag_output_fw or args.pos_output_fw or DEFAULT_OUTPUT_BIN_NAME

    launcher_apk_path = Path(args.launcher_apk).resolve() if args.launcher_apk else None
    settings_apk_path = Path(args.settings_apk).resolve() if args.settings_apk else None
    material_files_apk_path = Path(args.material_files_apk).resolve() if args.material_files_apk else None
    aptoide_apk_path = Path(args.aptoide_apk).resolve() if args.aptoide_apk else None

    return BuildConfiguration(
        input_fw=Path(input_fw_str).resolve(),
        output_fw=Path(output_fw_str).resolve(),
        launcher_apk=launcher_apk_path,
        settings_apk=settings_apk_path,
        material_files_apk=material_files_apk_path,
        aptoide_apk=aptoide_apk_path,
        debloat_list=Path(args.debloat_list).resolve(),
        build_prop_patch=Path(args.build_prop_patch).resolve(),
        work_dir=Path(args.work_dir).resolve(),
        dry_run=args.dry_run,
        verbose=args.verbose,
        keep_work_dir=args.keep_work_dir,
    )


def main(argv: Optional[List[str]] = None) -> int:
    """
    Main entry point for command-line execution of build_rom.py.

    @param {Optional[List[str]]} argv - Command-line arguments.
    @returns {int} Exit code: 0 on success, non-zero on failure.
    """
    config = parse_args_to_config(argv)
    return run_pipeline(config)


if __name__ == "__main__":
    sys.exit(main())
