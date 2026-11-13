#!/usr/bin/env python3
"""
Test Suite for WanboLightOS Firmware Unpack, Debloat, Injection & Repack Pipeline.
Validates CLI arguments, glob/fnmatch debloat expansion, HAL preservation blacklist,
APK injection, build.prop patching, dry-run end-to-end execution, and zero emojis compliance.
"""

import os
import shutil
import sys
import tempfile
import unittest
import subprocess
from pathlib import Path
from typing import List
from unittest.mock import MagicMock, patch

# Ensure os-pipeline module is accessible
PIPELINE_DIR = Path(__file__).resolve().parent.parent
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

import build_rom  # noqa: E402
from build_rom import (  # noqa: E402
    CRITICAL_HARDWARE_BLACKLIST,
    BuildConfiguration,
    CriticalHardwareSafetyViolation,
    PipelineStepError,
    apply_debloat,
    contains_emoji,
    convert_raw_to_sparse,
    convert_sparse_to_raw,
    create_argument_parser,
    extract_system_image,
    find_launcher_apk,
    find_matching_debloat_targets,
    find_material_files_apk,
    find_mstar_tool,
    find_settings_apk,
    get_repo_root,
    inject_packages,
    is_blacklisted,
    is_emoji_code_point,
    parse_args_to_config,
    parse_debloat_list,
    patch_build_prop,
    repack_mstar_binary,
    repack_system_image,
    run_pipeline,
    unpack_mstar_binary,
    validate_prerequisites,
)


class TestBuildPipeline(unittest.TestCase):
    """
    Unit tests for WanboLightOS build_rom.py pipeline orchestrator.
    """

    def setUp(self) -> None:
        """
        Creates temporary directory fixtures before each test method runs.

        @returns {None}
        """
        self.temp_dir = Path(tempfile.mkdtemp(prefix="wanbo_test_pipeline_"))
        self.repo_root = get_repo_root()
        self.patches_dir = self.repo_root / "os-pipeline" / "patches"
        self.debloat_file = self.patches_dir / "debloat-list.txt"
        self.build_prop_file = self.patches_dir / "build.prop.patch"

    def tearDown(self) -> None:
        """
        Cleans up temporary directory fixtures after each test method runs.

        @returns {None}
        """
        if self.temp_dir.exists():
            shutil.rmtree(self.temp_dir, ignore_errors=True)

    # ==========================================================================
    # Group A: CLI Argument Parsing
    # ==========================================================================

    def test_cli_defaults(self) -> None:
        """
        Validates default argument parsing values when no arguments are provided.

        @returns {None}
        """
        config = parse_args_to_config([])
        self.assertEqual(config.input_fw.name, "stock_firmware.bin")
        self.assertEqual(config.output_fw.name, "WanboLightOS_v1.0.bin")
        self.assertFalse(config.dry_run)
        self.assertFalse(config.verbose)
        self.assertFalse(config.keep_work_dir)
        self.assertIsNone(config.launcher_apk)
        self.assertIsNone(config.settings_apk)
        self.assertIsNone(config.material_files_apk)
        self.assertIsNone(config.aptoide_apk)
        self.assertEqual(config.debloat_list, self.debloat_file.resolve())
        self.assertEqual(config.build_prop_patch, self.build_prop_file.resolve())

    def test_cli_positional_arguments(self) -> None:
        """
        Validates parsing of positional input and output firmware binary paths.

        @returns {None}
        """
        config = parse_args_to_config(["custom_stock.bin", "custom_wanbo.bin"])
        self.assertEqual(config.input_fw.name, "custom_stock.bin")
        self.assertEqual(config.output_fw.name, "custom_wanbo.bin")

    def test_cli_custom_flags(self) -> None:
        """
        Validates explicit command-line flags for all pipeline options.

        @returns {None}
        """
        dummy_launcher = self.temp_dir / "WanboLauncher.apk"
        dummy_launcher.write_bytes(b"LAUNCHER_APK")
        dummy_settings = self.temp_dir / "WanboSettings.apk"
        dummy_settings.write_bytes(b"SETTINGS_APK")
        dummy_material = self.temp_dir / "MaterialFiles.apk"
        dummy_material.write_bytes(b"MATERIAL_APK")
        dummy_aptoide = self.temp_dir / "AptoideTV.apk"
        dummy_aptoide.write_bytes(b"APTOIDE_APK")
        custom_work = self.temp_dir / "custom_work"

        argv = [
            "-i", "input_fw_flag.bin",
            "-o", "output_fw_flag.bin",
            "--launcher-apk", str(dummy_launcher),
            "--settings-apk", str(dummy_settings),
            "--material-files-apk", str(dummy_material),
            "--aptoide-apk", str(dummy_aptoide),
            "--work-dir", str(custom_work),
            "--dry-run",
            "-v",
            "--keep-work-dir",
        ]
        config = parse_args_to_config(argv)

        self.assertEqual(config.input_fw.name, "input_fw_flag.bin")
        self.assertEqual(config.output_fw.name, "output_fw_flag.bin")
        self.assertEqual(config.launcher_apk, dummy_launcher.resolve())
        self.assertEqual(config.settings_apk, dummy_settings.resolve())
        self.assertEqual(config.material_files_apk, dummy_material.resolve())
        self.assertEqual(config.aptoide_apk, dummy_aptoide.resolve())
        self.assertEqual(config.work_dir, custom_work.resolve())
        self.assertTrue(config.dry_run)
        self.assertTrue(config.verbose)
        self.assertTrue(config.keep_work_dir)

    def test_cli_help_flag(self) -> None:
        """
        Validates that argument parser generates non-empty help text without emojis.

        @returns {None}
        """
        parser = create_argument_parser()
        help_text = parser.format_help()
        self.assertGreater(len(help_text), 100)
        self.assertFalse(contains_emoji(help_text), "Help output must adhere to Zero Emojis policy")

    # ==========================================================================
    # Group B: Debloat Glob & Fnmatch Expansion
    # ==========================================================================

    def test_glob_expansion_exact_and_wildcard_patterns(self) -> None:
        """
        Validates glob and fnmatch expansion on simulated system filesystem trees.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_test"
        system_root.mkdir(parents=True, exist_ok=True)

        # Create target bloatware directories and files
        whale_dir = system_root / "system" / "app" / "TV_OPEN_LAUNCHER_WHALE-zvt_mtk9255_p_prod"
        whale_dir.mkdir(parents=True, exist_ok=True)
        (whale_dir / "base.apk").write_bytes(b"MOCK_BLOAT")

        zeasn_store = system_root / "system" / "priv-app" / "ZeasnAppStore"
        zeasn_store.mkdir(parents=True, exist_ok=True)
        (zeasn_store / "store.apk").write_bytes(b"MOCK_BLOAT")

        demo_dir = system_root / "system" / "app" / "fusion.android.tv.demo"
        demo_dir.mkdir(parents=True, exist_ok=True)

        memory_clean = system_root / "system" / "app" / "MemoryClean"
        memory_clean.mkdir(parents=True, exist_ok=True)

        print_spooler = system_root / "system" / "app" / "PrintSpooler"
        print_spooler.mkdir(parents=True, exist_ok=True)

        # Create legitimate system apps that MUST NOT be touched
        calc_dir = system_root / "system" / "app" / "ExactCalculator"
        calc_dir.mkdir(parents=True, exist_ok=True)
        (calc_dir / "calc.apk").write_bytes(b"CALC_APK")

        bt_dir = system_root / "system" / "app" / "Bluetooth"
        bt_dir.mkdir(parents=True, exist_ok=True)

        # Use official debloat-list.txt
        patterns = parse_debloat_list(self.debloat_file)
        targets = find_matching_debloat_targets(system_root, patterns)

        target_names = [t.name for t in targets]

        # Verify bloatware was captured
        self.assertIn("TV_OPEN_LAUNCHER_WHALE-zvt_mtk9255_p_prod", target_names)
        self.assertIn("ZeasnAppStore", target_names)
        self.assertIn("fusion.android.tv.demo", target_names)
        self.assertIn("MemoryClean", target_names)
        self.assertIn("PrintSpooler", target_names)

        # Verify legitimate applications are preserved
        self.assertNotIn("ExactCalculator", target_names)
        self.assertNotIn("Bluetooth", target_names)

    def test_glob_expansion_both_system_and_flat_layouts(self) -> None:
        """
        Validates debloat matching whether extracted root has /system prefix or is flat.

        @returns {None}
        """
        # Flat layout (e.g. root contains app/ and priv-app/ directly)
        flat_root = self.temp_dir / "flat_root"
        flat_root.mkdir(parents=True, exist_ok=True)
        (flat_root / "app" / "MemoryClean").mkdir(parents=True, exist_ok=True)
        (flat_root / "priv-app" / "ZeasnService").mkdir(parents=True, exist_ok=True)

        patterns = parse_debloat_list(self.debloat_file)
        targets = find_matching_debloat_targets(flat_root, patterns)
        target_names = [t.name for t in targets]

        self.assertIn("MemoryClean", target_names)
        self.assertIn("ZeasnService", target_names)

    def test_debloat_targets_include_legacy_settings_and_cast_and_media(self) -> None:
        """
        Validates that TvSettingsPlus, com.android.tv.settings, EShareServer, EAirPlay,
        EDLNA, and MLocalMM2 are successfully matched by debloat rules in both standard
        and flat filesystem layouts.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_legacy_debloat"
        system_root.mkdir(parents=True, exist_ok=True)

        # Standard layout targets
        (system_root / "system" / "priv-app" / "TvSettingsPlus").mkdir(parents=True, exist_ok=True)
        (system_root / "system" / "priv-app" / "com.android.tv.settings").mkdir(parents=True, exist_ok=True)
        (system_root / "system" / "app" / "EShareServer").mkdir(parents=True, exist_ok=True)
        (system_root / "system" / "app" / "EAirPlay").mkdir(parents=True, exist_ok=True)
        (system_root / "system" / "app" / "EDLNA").mkdir(parents=True, exist_ok=True)
        (system_root / "system" / "product" / "app" / "MLocalMM2").mkdir(parents=True, exist_ok=True)

        patterns = parse_debloat_list(self.debloat_file)
        targets = find_matching_debloat_targets(system_root, patterns)
        target_names = [t.name for t in targets]

        self.assertIn("TvSettingsPlus", target_names)
        self.assertIn("com.android.tv.settings", target_names)
        self.assertIn("EShareServer", target_names)
        self.assertIn("EAirPlay", target_names)
        self.assertIn("EDLNA", target_names)
        self.assertIn("MLocalMM2", target_names)

        # Flat layout targets
        flat_root = self.temp_dir / "sys_legacy_flat"
        flat_root.mkdir(parents=True, exist_ok=True)
        (flat_root / "priv-app" / "TvSettingsPlus").mkdir(parents=True, exist_ok=True)
        (flat_root / "priv-app" / "com.android.tv.settings").mkdir(parents=True, exist_ok=True)
        (flat_root / "app" / "EShareServer").mkdir(parents=True, exist_ok=True)
        (flat_root / "product" / "app" / "MLocalMM2").mkdir(parents=True, exist_ok=True)

        targets_flat = find_matching_debloat_targets(flat_root, patterns)
        target_names_flat = [t.name for t in targets_flat]
        self.assertIn("TvSettingsPlus", target_names_flat)
        self.assertIn("com.android.tv.settings", target_names_flat)
        self.assertIn("EShareServer", target_names_flat)
        self.assertIn("MLocalMM2", target_names_flat)

    def test_debloat_rules_preserve_livetv_for_hdmi_switching(self) -> None:
        """
        Validates that LiveTV (system/priv-app/LiveTV) is NOT matched by any debloat rule,
        preserving hardware HDMI input switching functionality.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_livetv"
        system_root.mkdir(parents=True, exist_ok=True)

        livetv_dir = system_root / "system" / "priv-app" / "LiveTV"
        livetv_dir.mkdir(parents=True, exist_ok=True)
        livetv_apk = livetv_dir / "LiveTV.apk"
        livetv_apk.write_bytes(b"HARDWARE_LIVETV_APK")

        patterns = parse_debloat_list(self.debloat_file)
        targets = find_matching_debloat_targets(system_root, patterns)
        target_names = [t.name for t in targets]

        self.assertNotIn("LiveTV", target_names)
        self.assertNotIn("LiveTV.apk", target_names)

        # Verify LiveTV itself is not in parsed debloat patterns
        for pattern in patterns:
            norm = pattern.strip("/")
            self.assertNotEqual(norm, "system/priv-app/LiveTV")
            self.assertNotEqual(norm, "priv-app/LiveTV")
            self.assertNotEqual(norm, "system/priv-app/LiveTV*")
            self.assertNotEqual(norm, "priv-app/LiveTV*")

    # ==========================================================================
    # Group C: HAL Preservation Safety Blacklist
    # ==========================================================================

    def test_is_blacklisted_detects_all_critical_hardware_components(self) -> None:
        """
        Validates that is_blacklisted detects all critical hardware HALs, drivers, and services.

        @returns {None}
        """
        test_cases = [
            "system/priv-app/trapezoidServer",
            "app/com.chijimlee.trapezoid",
            "vendor/bin/trapezoid",
            "priv-app/tvinput",
            "app/com.mediatek.tvinput",
            "system/bin/dtv_svc",
            "app/autopair",
            "priv-app/com.mediatek.autopair",
            "app/loadingequalizer",
            "priv-app/com.mediatek.loadingequalizer",
            "system/app/TyyLauncher",
            "priv-app/com.chijimlee.tyylauncher",
            "vendor/bin/kxtj2server",
            "app/com.mediatek.hotkey.dispatcher",
        ]

        for case in test_cases:
            self.assertTrue(
                is_blacklisted(case),
                f"Critical hardware component must be blacklisted: {case}",
            )

    def test_is_blacklisted_allows_regular_bloat_and_purged_targets(self) -> None:
        """
        Validates that is_blacklisted returns False for bloatware packages and purged legacy services.

        @returns {None}
        """
        bloat_cases = [
            "system/app/ZeasnStore",
            "system/app/TV_OPEN_LAUNCHER_WHALE",
            "system/app/MemoryClean",
            "system/app/CleanCacheUtil",
            "system/app/PrintSpooler",
            "system/priv-app/TvSettingsPlus",
            "system/priv-app/com.android.tv.settings",
            "system/app/EShareServer",
            "system/app/EAirPlay",
            "system/app/EDLNA",
            "system/product/app/MLocalMM2",
        ]
        for case in bloat_cases:
            self.assertFalse(is_blacklisted(case), f"Bloatware must not trigger blacklist: {case}")

    def test_parse_debloat_list_rejects_blacklisted_entry(self) -> None:
        """
        Validates that parse_debloat_list raises CriticalHardwareSafetyViolation if debloat list includes critical HAL.

        @returns {None}
        """
        poisoned_debloat = self.temp_dir / "poisoned_debloat.txt"
        poisoned_debloat.write_text("system/app/Zeasn*\nsystem/priv-app/trapezoidServer\n", encoding="utf-8")

        with self.assertRaises(CriticalHardwareSafetyViolation):
            parse_debloat_list(poisoned_debloat)

    def test_find_matching_debloat_targets_aborts_on_blacklist_match(self) -> None:
        """
        Validates that find_matching_debloat_targets raises CriticalHardwareSafetyViolation if candidate matches HAL.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_safety"
        system_root.mkdir(parents=True, exist_ok=True)
        # Create a critical hardware driver directory
        trapezoid_dir = system_root / "system" / "priv-app" / "trapezoidServer"
        trapezoid_dir.mkdir(parents=True, exist_ok=True)

        # Deliberately use overly broad wildcard pattern
        dangerous_patterns = ["system/priv-app/*"]

        with self.assertRaises(CriticalHardwareSafetyViolation):
            find_matching_debloat_targets(system_root, dangerous_patterns)

    def test_apply_debloat_removes_files_safely(self) -> None:
        """
        Validates that apply_debloat physically deletes bloatware in non-dry-run mode while preserving others.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_apply"
        bloat_dir = system_root / "system" / "app" / "MemoryClean"
        bloat_dir.mkdir(parents=True, exist_ok=True)
        (bloat_dir / "clean.apk").write_bytes(b"BLOAT")

        legit_dir = system_root / "system" / "app" / "ExactCalculator"
        legit_dir.mkdir(parents=True, exist_ok=True)
        (legit_dir / "calc.apk").write_bytes(b"CALC")

        custom_debloat = self.temp_dir / "custom_debloat.txt"
        custom_debloat.write_text("system/app/MemoryClean\n", encoding="utf-8")

        removed = apply_debloat(system_root, custom_debloat, dry_run=False)
        self.assertEqual(len(removed), 1)
        self.assertFalse(bloat_dir.exists(), "Bloatware directory must be removed")
        self.assertTrue(legit_dir.exists(), "Legitimate directory must be preserved")

    # ==========================================================================
    # Group D: APK Injection & build.prop Patching
    # ==========================================================================

    def test_inject_packages_wanbo_launcher(self) -> None:
        """
        Validates injection of WanboLauncher into priv-app/WanboLauncher/WanboLauncher.apk.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_inject"
        system_root.mkdir(parents=True, exist_ok=True)

        dummy_launcher = self.temp_dir / "WanboLauncher.apk"
        dummy_launcher.write_bytes(b"WANBO_LAUNCHER_BINARY_DATA")

        injected = inject_packages(system_root, launcher_apk=dummy_launcher, aptoide_apk=None, dry_run=False)

        self.assertIn("WanboLauncher", injected)
        target_path = injected["WanboLauncher"]
        self.assertTrue(target_path.exists())
        self.assertEqual(target_path.name, "WanboLauncher.apk")
        self.assertEqual(target_path.parent.name, "WanboLauncher")
        self.assertEqual(target_path.read_bytes(), b"WANBO_LAUNCHER_BINARY_DATA")

    def test_inject_packages_aptoide_tv(self) -> None:
        """
        Validates injection of AptoideTV into priv-app/AptoideTV/AptoideTV.apk (0644).

        @returns {None}
        """
        system_root = self.temp_dir / "sys_inject_aptoide"
        system_root.mkdir(parents=True, exist_ok=True)

        dummy_launcher = self.temp_dir / "WanboLauncher.apk"
        dummy_launcher.write_bytes(b"WANBO_LAUNCHER")
        dummy_aptoide = self.temp_dir / "AptoideTV.apk"
        dummy_aptoide.write_bytes(b"APTOIDE_TV_BINARY_DATA")

        injected = inject_packages(
            system_root,
            launcher_apk=dummy_launcher,
            aptoide_apk=dummy_aptoide,
            dry_run=False,
        )

        self.assertIn("AptoideTV", injected)
        aptoide_dest = injected["AptoideTV"]
        self.assertTrue(aptoide_dest.exists())
        self.assertEqual(aptoide_dest.name, "AptoideTV.apk")
        self.assertEqual(aptoide_dest.parent.name, "AptoideTV")
        self.assertEqual(aptoide_dest.parent.parent.name, "priv-app")
        self.assertEqual(aptoide_dest.read_bytes(), b"APTOIDE_TV_BINARY_DATA")

    def test_inject_packages_wanbo_settings(self) -> None:
        """
        Validates injection of WanboSettings into priv-app/WanboSettings/WanboSettings.apk (0644).

        @returns {None}
        """
        system_root = self.temp_dir / "sys_inject_settings"
        system_root.mkdir(parents=True, exist_ok=True)

        dummy_launcher = self.temp_dir / "WanboLauncher.apk"
        dummy_launcher.write_bytes(b"WANBO_LAUNCHER")
        dummy_settings = self.temp_dir / "WanboSettings.apk"
        dummy_settings.write_bytes(b"WANBO_SETTINGS_BINARY_DATA")

        injected = inject_packages(
            system_root,
            launcher_apk=dummy_launcher,
            settings_apk=dummy_settings,
            dry_run=False,
        )

        self.assertIn("WanboSettings", injected)
        settings_dest = injected["WanboSettings"]
        self.assertTrue(settings_dest.exists())
        self.assertEqual(settings_dest.name, "WanboSettings.apk")
        self.assertEqual(settings_dest.parent.name, "WanboSettings")
        self.assertEqual(settings_dest.parent.parent.name, "priv-app")
        self.assertEqual(settings_dest.read_bytes(), b"WANBO_SETTINGS_BINARY_DATA")

    def test_inject_packages_material_files(self) -> None:
        """
        Validates injection of MaterialFiles into app/MaterialFiles/MaterialFiles.apk (0644).

        @returns {None}
        """
        system_root = self.temp_dir / "sys_inject_material"
        system_root.mkdir(parents=True, exist_ok=True)

        dummy_launcher = self.temp_dir / "WanboLauncher.apk"
        dummy_launcher.write_bytes(b"WANBO_LAUNCHER")
        dummy_material = self.temp_dir / "MaterialFiles.apk"
        dummy_material.write_bytes(b"MATERIAL_FILES_BINARY_DATA")

        injected = inject_packages(
            system_root,
            launcher_apk=dummy_launcher,
            material_files_apk=dummy_material,
            dry_run=False,
        )

        self.assertIn("MaterialFiles", injected)
        material_dest = injected["MaterialFiles"]
        self.assertTrue(material_dest.exists())
        self.assertEqual(material_dest.name, "MaterialFiles.apk")
        self.assertEqual(material_dest.parent.name, "MaterialFiles")
        self.assertEqual(material_dest.parent.parent.name, "app")
        self.assertEqual(material_dest.read_bytes(), b"MATERIAL_FILES_BINARY_DATA")

    def test_inject_packages_all_four_components(self) -> None:
        """
        Validates simultaneous injection of WanboLauncher, WanboSettings, AptoideTV, and MaterialFiles.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_inject_all"
        system_root.mkdir(parents=True, exist_ok=True)

        dummy_launcher = self.temp_dir / "WanboLauncher.apk"
        dummy_launcher.write_bytes(b"LAUNCHER_DATA")
        dummy_settings = self.temp_dir / "WanboSettings.apk"
        dummy_settings.write_bytes(b"SETTINGS_DATA")
        dummy_aptoide = self.temp_dir / "AptoideTV.apk"
        dummy_aptoide.write_bytes(b"APTOIDE_DATA")
        dummy_material = self.temp_dir / "MaterialFiles.apk"
        dummy_material.write_bytes(b"MATERIAL_DATA")

        injected = inject_packages(
            system_root,
            launcher_apk=dummy_launcher,
            settings_apk=dummy_settings,
            material_files_apk=dummy_material,
            aptoide_apk=dummy_aptoide,
            dry_run=False,
        )

        self.assertEqual(len(injected), 4)
        self.assertIn("WanboLauncher", injected)
        self.assertIn("WanboSettings", injected)
        self.assertIn("AptoideTV", injected)
        self.assertIn("MaterialFiles", injected)

        self.assertEqual(injected["WanboLauncher"].parent.parent.name, "priv-app")
        self.assertEqual(injected["WanboSettings"].parent.parent.name, "priv-app")
        self.assertEqual(injected["AptoideTV"].parent.parent.name, "priv-app")
        self.assertEqual(injected["MaterialFiles"].parent.parent.name, "app")

    def test_find_settings_apk_resolution(self) -> None:
        """
        Validates resolution of WanboSettings APK from explicit path and standard build locations.

        @returns {None}
        """
        dummy_settings = self.temp_dir / "CustomSettings.apk"
        dummy_settings.write_bytes(b"SETTINGS")

        # Explicit path resolution
        resolved = find_settings_apk(dummy_settings)
        self.assertEqual(resolved, dummy_settings.resolve())

        # Non-existent explicit path
        non_existent = self.temp_dir / "non_existent.apk"
        self.assertIsNone(find_settings_apk(non_existent))

    def test_find_material_files_apk_resolution(self) -> None:
        """
        Validates resolution of MaterialFiles APK from explicit path and standard storage locations.

        @returns {None}
        """
        dummy_material = self.temp_dir / "CustomMaterial.apk"
        dummy_material.write_bytes(b"MATERIAL")

        # Explicit path resolution
        resolved = find_material_files_apk(dummy_material)
        self.assertEqual(resolved, dummy_material.resolve())

        # Non-existent explicit path
        non_existent = self.temp_dir / "non_existent.apk"
        self.assertIsNone(find_material_files_apk(non_existent))

    def test_patch_build_prop_appends_cleanly(self) -> None:
        """
        Validates that patch_build_prop appends build.prop.patch and retains existing properties.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_prop"
        prop_dir = system_root / "system"
        prop_dir.mkdir(parents=True, exist_ok=True)
        build_prop = prop_dir / "build.prop"
        build_prop.write_text("ro.product.model=Wanbo T2 Max\nro.build.version.release=9\n", encoding="utf-8")

        patched_path = patch_build_prop(system_root, self.build_prop_file, dry_run=False)
        self.assertEqual(patched_path, build_prop)

        content = build_prop.read_text(encoding="utf-8")
        # Verify original properties retained
        self.assertIn("ro.product.model=Wanbo T2 Max", content)
        # Verify low-RAM properties appended
        self.assertIn("ro.config.low_ram=true", content)
        self.assertIn("dalvik.vm.heapgrowthlimit=64m", content)
        self.assertIn("ro.zram.mark_idle_delay_mins=60", content)

    # ==========================================================================
    # Group E: Dry-Run Mode & Pipeline Step Execution
    # ==========================================================================

    def test_validate_prerequisites_missing_input_fw_non_dry_run(self) -> None:
        """
        Validates that validate_prerequisites raises FileNotFoundError when input firmware binary is missing.

        @returns {None}
        """
        non_existent_fw = self.temp_dir / "does_not_exist.bin"
        config = BuildConfiguration(
            input_fw=non_existent_fw,
            output_fw=self.temp_dir / "out.bin",
            launcher_apk=None,
            aptoide_apk=None,
            debloat_list=self.debloat_file,
            build_prop_patch=self.build_prop_file,
            work_dir=self.temp_dir / "work",
            dry_run=False,
        )
        with self.assertRaises(FileNotFoundError):
            validate_prerequisites(config)

    def test_pipeline_dry_run_end_to_end(self) -> None:
        """
        Validates complete 9-step simulated execution in dry-run mode.

        @returns {None}
        """
        work_dir = self.temp_dir / "dry_run_work"
        output_fw = self.temp_dir / "WanboLightOS_v1.0.bin"
        dummy_input = self.temp_dir / "mock_stock.bin"
        dummy_input.write_bytes(b"MOCK_FIRMWARE_BINARY")

        config = BuildConfiguration(
            input_fw=dummy_input,
            output_fw=output_fw,
            launcher_apk=None,
            aptoide_apk=None,
            debloat_list=self.debloat_file,
            build_prop_patch=self.build_prop_file,
            work_dir=work_dir,
            dry_run=True,
            verbose=True,
            keep_work_dir=True,
        )

        exit_code = run_pipeline(config)
        self.assertEqual(exit_code, 0, "Pipeline dry-run must succeed with exit code 0")
        self.assertTrue(output_fw.exists(), "Dry-run output binary must be generated")

    def test_pipeline_catches_hal_violation_returns_code_2(self) -> None:
        """
        Validates that pipeline returns exit code 2 when a HAL safety violation occurs.

        @returns {None}
        """
        poisoned_debloat = self.temp_dir / "poisoned_debloat.txt"
        poisoned_debloat.write_text("system/priv-app/trapezoidServer\n", encoding="utf-8")

        work_dir = self.temp_dir / "safety_work"
        dummy_input = self.temp_dir / "stock.bin"
        dummy_input.write_bytes(b"MOCK_STOCK")

        config = BuildConfiguration(
            input_fw=dummy_input,
            output_fw=self.temp_dir / "out.bin",
            launcher_apk=None,
            aptoide_apk=None,
            debloat_list=poisoned_debloat,
            build_prop_patch=self.build_prop_file,
            work_dir=work_dir,
            dry_run=True,
        )

        exit_code = run_pipeline(config)
        self.assertEqual(exit_code, 2, "Pipeline must exit with code 2 on HAL preservation violation")

    # ==========================================================================
    # Group F: Zero Emojis Compliance
    # ==========================================================================

    def test_build_rom_py_zero_emojis(self) -> None:
        """
        Validates that build_rom.py contains zero emojis.

        @returns {None}
        """
        script_path = self.repo_root / "os-pipeline" / "build_rom.py"
        self.assertTrue(script_path.exists())
        text = script_path.read_text(encoding="utf-8")
        self.assertFalse(contains_emoji(text), "build_rom.py must adhere to Zero Emojis policy")

    def test_build_rom_sh_zero_emojis(self) -> None:
        """
        Validates that build-rom.sh contains zero emojis.

        @returns {None}
        """
        script_path = self.repo_root / "os-pipeline" / "build-rom.sh"
        self.assertTrue(script_path.exists())
        text = script_path.read_text(encoding="utf-8")
        self.assertFalse(contains_emoji(text), "build-rom.sh must adhere to Zero Emojis policy")

    def test_makefile_zero_emojis(self) -> None:
        """
        Validates that Makefile contains zero emojis.

        @returns {None}
        """
        makefile_path = self.repo_root / "Makefile"
        self.assertTrue(makefile_path.exists())
        text = makefile_path.read_text(encoding="utf-8")
        self.assertFalse(contains_emoji(text), "Makefile must adhere to Zero Emojis policy")

    def test_test_build_pipeline_zero_emojis(self) -> None:
        """
        Validates that test_build_pipeline.py contains zero emojis.

        @returns {None}
        """
        test_path = Path(__file__).resolve()
        text = test_path.read_text(encoding="utf-8")
        self.assertFalse(contains_emoji(text), "test_build_pipeline.py must adhere to Zero Emojis policy")

    def test_all_pipeline_directory_files_zero_emojis(self) -> None:
        """
        Recursively validates that all files in os-pipeline adhere to Zero Emojis policy.

        @returns {None}
        """
        pipeline_dir = self.repo_root / "os-pipeline"
        for root, _, files in os.walk(pipeline_dir):
            for file_name in files:
                file_path = Path(root) / file_name
                try:
                    text = file_path.read_text(encoding="utf-8")
                    self.assertFalse(
                        contains_emoji(text),
                        f"File {file_path.relative_to(self.repo_root)} violates Zero Emojis policy!",
                    )
                except UnicodeDecodeError:
                    pass  # Non-text binary file

    # ==========================================================================
    # Group G: Review Findings Fixes Verification
    # ==========================================================================

    def test_repack_system_image_dry_run(self) -> None:
        """
        Validates that repack_system_image executes properly in dry-run mode and outputs simulated raw image.

        @returns {None}
        """
        extract_dir = self.temp_dir / "extract_dir"
        extract_dir.mkdir(parents=True, exist_ok=True)
        (extract_dir / "app").mkdir(parents=True, exist_ok=True)

        raw_output = self.temp_dir / "system_raw.img"
        res = repack_system_image(extract_dir, raw_output, dry_run=True, verbose=True)

        self.assertEqual(res, raw_output)
        self.assertTrue(raw_output.exists())
        self.assertEqual(raw_output.read_bytes(), b"MOCK_REPACKED_RAW_EXT4_DATA")

    def test_build_rom_sh_syntax_and_jsdoc(self) -> None:
        """
        Validates that build-rom.sh contains valid bash syntax with all JSDoc lines prefixed with #.

        @returns {None}
        """
        script_path = self.repo_root / "os-pipeline" / "build-rom.sh"
        self.assertTrue(script_path.exists())
        lines = script_path.read_text(encoding="utf-8").splitlines()

        for idx, line in enumerate(lines, start=1):
            stripped = line.strip()
            self.assertFalse(
                stripped.startswith("/**") or stripped.startswith("*/") or (stripped.startswith("*") and not stripped.startswith("*=")),
                f"Line {idx} in build-rom.sh contains un-prefixed JSDoc comment: {line}",
            )

        bash_tool = shutil.which("bash")
        if bash_tool:
            rel_path = "os-pipeline/build-rom.sh"
            res_syntax = subprocess.run([bash_tool, "-n", rel_path], cwd=str(self.repo_root), capture_output=True, text=True)
            self.assertEqual(res_syntax.returncode, 0, f"bash -n syntax check failed: {res_syntax.stderr}")

            res_help = subprocess.run([bash_tool, rel_path, "-h"], cwd=str(self.repo_root), capture_output=True, text=True)
            self.assertEqual(res_help.returncode, 0, f"bash -h failed: {res_help.stderr}")
            self.assertIn("Usage:", res_help.stdout)

    def test_mstar_tools_invoke_via_sys_executable_for_python(self) -> None:
        """
        Validates that unpack_mstar_binary and repack_mstar_binary invoke python tools via sys.executable.

        @returns {None}
        """
        input_fw = self.temp_dir / "stock.bin"
        input_fw.write_bytes(b"DATA")
        unpack_dir = self.temp_dir / "unpack"
        unpack_dir.mkdir(parents=True, exist_ok=True)
        output_fw = self.temp_dir / "out.bin"

        with patch("shutil.which", return_value="C:/tools/unpack.py"), \
             patch("subprocess.run") as mock_run:
            mock_run.return_value = MagicMock(returncode=0, stderr="", stdout="")
            unpack_mstar_binary(input_fw, unpack_dir, dry_run=False, verbose=False)
            mock_run.assert_called_once()
            cmd_called = mock_run.call_args[0][0]
            self.assertEqual(cmd_called[0], sys.executable)
            self.assertEqual(Path(cmd_called[1]), Path("C:/tools/unpack.py"))

        with patch("shutil.which", return_value="/usr/bin/pack.py"), \
             patch("subprocess.run") as mock_run_pack:
            mock_run_pack.return_value = MagicMock(returncode=0, stderr="", stdout="")
            repack_mstar_binary(unpack_dir, output_fw, dry_run=False, verbose=False)
            mock_run_pack.assert_called_once()
            cmd_called_pack = mock_run_pack.call_args[0][0]
            self.assertEqual(cmd_called_pack[0], sys.executable)
            self.assertEqual(Path(cmd_called_pack[1]), Path("/usr/bin/pack.py"))

    def test_find_matching_debloat_targets_ignores_host_directory_blacklist_substrings(self) -> None:
        """
        Validates that host directory path containing blacklist keyword does not cause false positives.

        @returns {None}
        """
        host_dir_with_blacklist = self.temp_dir / "host_trapezoid_workspace" / "sys_extract"
        host_dir_with_blacklist.mkdir(parents=True, exist_ok=True)

        clean_cache_dir = host_dir_with_blacklist / "system" / "app" / "CleanCacheUtil"
        clean_cache_dir.mkdir(parents=True, exist_ok=True)
        (clean_cache_dir / "clean.apk").write_bytes(b"BLOAT")

        patterns = ["system/app/CleanCacheUtil*"]
        targets = find_matching_debloat_targets(host_dir_with_blacklist, patterns)
        self.assertEqual(len(targets), 1)
        self.assertEqual(targets[0].name, "CleanCacheUtil")

        bad_dir = host_dir_with_blacklist / "system" / "priv-app" / "trapezoidServer"
        bad_dir.mkdir(parents=True, exist_ok=True)
        dangerous_patterns = ["system/priv-app/*"]
        with self.assertRaises(CriticalHardwareSafetyViolation):
            find_matching_debloat_targets(host_dir_with_blacklist, dangerous_patterns)

    def test_patch_build_prop_replaces_existing_keys_in_place(self) -> None:
        """
        Validates that patch_build_prop replaces existing properties in-place without duplicating keys.

        @returns {None}
        """
        system_root = self.temp_dir / "sys_prop_replace"
        prop_dir = system_root / "system"
        prop_dir.mkdir(parents=True, exist_ok=True)
        build_prop = prop_dir / "build.prop"

        original_content = (
            "# Stock properties\n"
            "ro.product.model=Wanbo T2 Max\n"
            "ro.config.low_ram=false\n"
            "dalvik.vm.heapgrowthlimit=128m\n"
            "ro.build.version.release=9\n"
        )
        build_prop.write_text(original_content, encoding="utf-8")

        patch_build_prop(system_root, self.build_prop_file, dry_run=False)
        content = build_prop.read_text(encoding="utf-8")

        self.assertIn("ro.config.low_ram=true", content)
        self.assertNotIn("ro.config.low_ram=false", content)
        self.assertEqual(content.count("ro.config.low_ram="), 1)

        self.assertIn("dalvik.vm.heapgrowthlimit=64m", content)
        self.assertNotIn("dalvik.vm.heapgrowthlimit=128m", content)
        self.assertEqual(content.count("dalvik.vm.heapgrowthlimit="), 1)

        self.assertIn("ro.product.model=Wanbo T2 Max", content)
        self.assertIn("ro.build.version.release=9", content)
        self.assertIn("ro.zram.mark_idle_delay_mins=60", content)

    def test_makefile_has_all_help_target(self) -> None:
        """
        Validates that Makefile defines the 'all: help' target.

        @returns {None}
        """
        makefile_path = self.repo_root / "Makefile"
        self.assertTrue(makefile_path.exists())
        text = makefile_path.read_text(encoding="utf-8")
        self.assertIn("all: help", text)

    def test_repack_system_image_includes_selinux_contexts_flag(self) -> None:
        """
        Validates that repack_system_image appends -S flag when file_contexts is present.

        @returns {None}
        """
        sys_extract = self.temp_dir / "selinux_extract"
        sys_extract.mkdir(parents=True, exist_ok=True)
        unpacked_dir = self.temp_dir / "selinux_unpacked"
        unpacked_dir.mkdir(parents=True, exist_ok=True)
        file_ctx = unpacked_dir / "file_contexts.bin"
        file_ctx.write_bytes(b"SELINUX_CONTEXTS")
        sys_raw = self.temp_dir / "sys_raw.img"

        with patch("shutil.which") as mock_which, \
             patch("subprocess.run") as mock_run:
            mock_which.side_effect = lambda tool: "/usr/bin/make_ext4fs" if tool == "make_ext4fs" else None
            mock_run.return_value = MagicMock(returncode=0, stderr="", stdout="")

            repack_system_image(sys_extract, sys_raw, unpacked_dir=unpacked_dir, dry_run=False)
            mock_run.assert_called_once()
            cmd_called = mock_run.call_args[0][0]
            self.assertIn("-S", cmd_called)
            self.assertIn(str(file_ctx), cmd_called)

    def test_find_mstar_tool_discovers_repo_local_script(self) -> None:
        """
        Validates that find_mstar_tool discovers unpack.py inside repository tools directory.

        @returns {None}
        """
        tools_dir = self.repo_root / "os-pipeline" / "tools" / "mstar-bin-tool"
        tools_dir.mkdir(parents=True, exist_ok=True)
        dummy_unpack = tools_dir / "unpack.py"
        dummy_unpack.write_text("# dummy", encoding="utf-8")
        try:
            with patch("shutil.which", return_value=None):
                discovered = find_mstar_tool("unpack.py")
                self.assertIsNotNone(discovered)
                self.assertEqual(discovered.resolve(), dummy_unpack.resolve())
        finally:
            if dummy_unpack.exists():
                dummy_unpack.unlink()


def main() -> int:
    """
    Main entry point for standalone execution of test_build_pipeline.py.

    @returns {int} Exit code: 0 if all tests pass, 1 otherwise.
    """
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(TestBuildPipeline)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(main())
