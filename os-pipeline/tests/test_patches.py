#!/usr/bin/env python3
"""
Test Suite for WanboLightOS Debloat Specification and build.prop Patch.
Validates debloat-list.txt and build.prop.patch for correctness, safety,
hardware HAL preservation, low-RAM tuning parameters, and zero-emoji compliance.
"""

import os
import sys
import unittest
from pathlib import Path


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
    Resolves the path to the repository root directory.

    @returns {Path} Resolved Path object pointing to the repository root.
    """
    current_dir = Path(__file__).resolve().parent
    return current_dir.parent.parent


class TestPatches(unittest.TestCase):
    """
    Automated test cases for debloat-list.txt and build.prop.patch.
    """

    def setUp(self) -> None:
        """
        Sets up test paths before each test method runs.

        @returns {None}
        """
        self.repo_root = get_repo_root()
        self.patches_dir = self.repo_root / "os-pipeline" / "patches"
        self.debloat_file = self.patches_dir / "debloat-list.txt"
        self.build_prop_file = self.patches_dir / "build.prop.patch"

    def test_debloat_list_exists_and_readable(self) -> None:
        """
        Validates that debloat-list.txt exists and is non-empty.

        @returns {None}
        """
        self.assertTrue(
            self.debloat_file.exists(),
            f"debloat-list.txt must exist at {self.debloat_file}",
        )
        content = self.debloat_file.read_text(encoding="utf-8")
        self.assertGreater(
            len(content.strip()),
            0,
            "debloat-list.txt must not be empty",
        )

    def test_debloat_list_zero_emojis(self) -> None:
        """
        Validates that debloat-list.txt contains zero emojis.

        @returns {None}
        """
        content = self.debloat_file.read_text(encoding="utf-8")
        self.assertFalse(
            contains_emoji(content),
            "debloat-list.txt must adhere to the Zero Emojis policy",
        )

    def test_debloat_list_contains_required_purges(self) -> None:
        """
        Validates that debloat-list.txt specifies all required purge targets.

        @returns {None}
        """
        content = self.debloat_file.read_text(encoding="utf-8")
        lines = [line.strip() for line in content.splitlines() if line.strip() and not line.strip().startswith("#")]

        # 1. Zeasn / Whale advertising suite
        has_whale = any("TV_OPEN_LAUNCHER_WHALE" in line for line in lines)
        has_zeasn = any("Zeasn" in line or "com.zeasn" in line for line in lines)
        self.assertTrue(has_whale, "debloat-list.txt must include TV_OPEN_LAUNCHER_WHALE")
        self.assertTrue(has_zeasn, "debloat-list.txt must include Zeasn packages")

        # 2. Demos and test suites
        has_demo = any("fusion.android.tv.demo" in line for line in lines)
        has_mstar_tests = any("com.mstar.android.media.tests" in line for line in lines)
        has_factory_menu = any("mediatek.factorymenu.ui" in line or "MtkFactoryMenu" in line for line in lines)
        self.assertTrue(has_demo, "debloat-list.txt must include fusion.android.tv.demo")
        self.assertTrue(has_mstar_tests, "debloat-list.txt must include com.mstar.android.media.tests")
        self.assertTrue(has_factory_menu, "debloat-list.txt must include mediatek.factorymenu.ui / MtkFactoryMenu")

        # 3. Fake memory cleaners
        has_memory_clean = any("MemoryClean" in line or "com.memoryclean" in line for line in lines)
        has_clean_cache = any("CleanCacheUtil" in line or "com.chijimlee.cleancacheutil" in line for line in lines)
        self.assertTrue(has_memory_clean, "debloat-list.txt must include MemoryClean")
        self.assertTrue(has_clean_cache, "debloat-list.txt must include CleanCacheUtil")

        # 4. Unneeded projector daemons
        has_print = any("PrintSpooler" in line for line in lines)
        has_browser = any("Browser" in line for line in lines)
        has_contacts_sync = any("GoogleContactsSyncAdapter" in line for line in lines)
        has_calendar = any("CalendarProvider" in line for line in lines)
        self.assertTrue(has_print, "debloat-list.txt must include PrintSpooler")
        self.assertTrue(has_browser, "debloat-list.txt must include Browser")
        self.assertTrue(has_contacts_sync, "debloat-list.txt must include GoogleContactsSyncAdapter")
        self.assertTrue(has_calendar, "debloat-list.txt must include CalendarProvider")

    def test_debloat_list_preserves_critical_hal_and_drivers(self) -> None:
        """
        Validates that debloat-list.txt NEVER includes critical HALs, drivers, or system settings.

        @returns {None}
        """
        content = self.debloat_file.read_text(encoding="utf-8")
        raw_lines = [line.strip() for line in content.splitlines() if line.strip() and not line.strip().startswith("#")]

        # Blacklist of critical packages and drivers that must NEVER be removed
        critical_keywords = [
            "trapezoidServer",
            "trapezoid",
            "com.chijimlee.trapezoid",
            "tvinput",
            "com.mediatek.tvinput",
            "dtv_svc",
            "autopair",
            "com.mediatek.autopair",
            "loadingequalizer",
            "com.mediatek.loadingequalizer",
            "tv.settings",
            "com.android.tv.settings",
            "kxtj2server",
            "hotkey.dispatcher",
        ]

        for line in raw_lines:
            for keyword in critical_keywords:
                self.assertNotIn(
                    keyword.lower(),
                    line.lower(),
                    f"CRITICAL SAFETY VIOLATION: debloat-list.txt must NEVER purge '{keyword}'! Found line: '{line}'",
                )

    def test_build_prop_patch_exists_and_readable(self) -> None:
        """
        Validates that build.prop.patch exists and is non-empty.

        @returns {None}
        """
        self.assertTrue(
            self.build_prop_file.exists(),
            f"build.prop.patch must exist at {self.build_prop_file}",
        )
        content = self.build_prop_file.read_text(encoding="utf-8")
        self.assertGreater(
            len(content.strip()),
            0,
            "build.prop.patch must not be empty",
        )

    def test_build_prop_patch_zero_emojis(self) -> None:
        """
        Validates that build.prop.patch contains zero emojis.

        @returns {None}
        """
        content = self.build_prop_file.read_text(encoding="utf-8")
        self.assertFalse(
            contains_emoji(content),
            "build.prop.patch must adhere to the Zero Emojis policy",
        )

    def test_build_prop_low_ram_properties(self) -> None:
        """
        Validates that build.prop.patch contains low memory configuration properties.

        @returns {None}
        """
        content = self.build_prop_file.read_text(encoding="utf-8")
        props = {}
        for line in content.splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()

        self.assertIn("ro.config.low_ram", props, "build.prop.patch must declare ro.config.low_ram")
        self.assertEqual(props["ro.config.low_ram"], "true", "ro.config.low_ram must be 'true'")

        self.assertIn("ro.lmk.low", props, "build.prop.patch must declare ro.lmk.low")
        self.assertEqual(props["ro.lmk.low"], "1001", "ro.lmk.low must be '1001'")

        self.assertIn("ro.lmk.medium", props, "build.prop.patch must declare ro.lmk.medium")
        self.assertEqual(props["ro.lmk.medium"], "800", "ro.lmk.medium must be '800'")

        self.assertIn("ro.lmk.critical", props, "build.prop.patch must declare ro.lmk.critical")
        self.assertEqual(props["ro.lmk.critical"], "0", "ro.lmk.critical must be '0'")

    def test_build_prop_dalvik_heap_properties(self) -> None:
        """
        Validates that build.prop.patch contains Dalvik VM heap tuning for 843 MB RAM.

        @returns {None}
        """
        content = self.build_prop_file.read_text(encoding="utf-8")
        props = {}
        for line in content.splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()

        expected_heap = {
            "dalvik.vm.heapstartsize": "8m",
            "dalvik.vm.heapgrowthlimit": "64m",
            "dalvik.vm.heapsize": "128m",
            "dalvik.vm.heaptargetutilization": "0.75",
            "dalvik.vm.heapminfree": "512k",
            "dalvik.vm.heapmaxfree": "2m",
        }

        for prop_name, expected_val in expected_heap.items():
            self.assertIn(prop_name, props, f"build.prop.patch must declare {prop_name}")
            self.assertEqual(
                props[prop_name],
                expected_val,
                f"{prop_name} must be '{expected_val}', got '{props.get(prop_name)}'",
            )

    def test_build_prop_gpu_and_zram_properties(self) -> None:
        """
        Validates that build.prop.patch contains GPU hardware acceleration and ZRAM tuning.

        @returns {None}
        """
        content = self.build_prop_file.read_text(encoding="utf-8")
        props = {}
        for line in content.splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()

        self.assertIn("debug.sf.hw", props, "build.prop.patch must declare debug.sf.hw")
        self.assertEqual(props["debug.sf.hw"], "1", "debug.sf.hw must be '1'")

        self.assertIn("video.accelerate.hw", props, "build.prop.patch must declare video.accelerate.hw")
        self.assertEqual(props["video.accelerate.hw"], "1", "video.accelerate.hw must be '1'")

        self.assertIn("ro.hwui.renderer", props, "build.prop.patch must declare ro.hwui.renderer")
        self.assertEqual(props["ro.hwui.renderer"], "opengl", "ro.hwui.renderer must be 'opengl'")

        self.assertIn("ro.zram.mark_idle_delay_mins", props, "build.prop.patch must declare ro.zram.mark_idle_delay_mins")
        self.assertEqual(props["ro.zram.mark_idle_delay_mins"], "60", "ro.zram.mark_idle_delay_mins must be '60'")

    def test_all_pipeline_files_zero_emojis(self) -> None:
        """
        Validates that all files under os-pipeline adhere to the Zero Emojis policy.

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
                    pass  # Skip non-text binary files if any


def main() -> int:
    """
    Main entry point for standalone execution of test_patches.py.

    @returns {int} Exit code: 0 for all tests passed, 1 otherwise.
    """
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(TestPatches)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(main())
