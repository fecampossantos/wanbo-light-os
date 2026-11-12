#!/usr/bin/env python3
"""
Test Suite for WanboLightOS End-User Documentation and Release Guide.
Validates existence, technical section completeness, license terms,
and zero-emoji compliance across all markdown guides and legal files.
"""

import os
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
        return True  # Misc symbols (e.g. warning, gears, sparkles)
    if 0x2700 <= cp <= 0x27BF:
        return True  # Dingbats (e.g. checkmarks, crosses)
    if 0xFE00 <= cp <= 0xFE0F:
        return True  # Variation Selectors
    if 0x1F1E6 <= cp <= 0x1F1FF:
        return True  # Regional indicator symbols / Flags
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


def find_emoji_characters(text: str) -> list:
    """
    Finds all emoji characters and their code points in a text string.

    @param {str} text - The string content to inspect.
    @returns {list} List of tuples containing (character, hex_codepoint).
    """
    violations = []
    for char in text:
        cp = ord(char)
        if is_emoji_code_point(cp):
            violations.append((char, hex(cp)))
    return violations


class TestDocs(unittest.TestCase):
    """
    Test cases for WanboLightOS documentation, flash guide, build instructions,
    and open-source license agreement.
    """

    @classmethod
    def setUpClass(cls):
        """
        Locates the project root directory relative to the test file.

        @returns {None}
        """
        # test_docs.py is in docs/tests/
        current_file = Path(__file__).resolve()
        cls.repo_root = current_file.parent.parent.parent
        cls.docs_dir = cls.repo_root / "docs"

    def test_readme_exists_and_content(self):
        """
        Verifies README.md exists, has zero emojis, and contains all required sections.

        @returns {None}
        """
        readme_path = self.repo_root / "README.md"
        self.assertTrue(readme_path.is_file(), "README.md must exist in repository root")
        
        content = readme_path.read_text(encoding="utf-8")
        self.assertGreater(len(content), 100, "README.md must not be empty")

        # Zero emojis
        emojis = find_emoji_characters(content)
        self.assertEqual(len(emojis), 0, f"README.md contains emojis: {emojis}")

        # Target Hardware
        self.assertIn("Wanbo T2 Max", content)
        self.assertIn("armeabi-v7a", content)
        self.assertIn("Android 9", content)
        self.assertIn("API", content)

        # Architectural Highlights
        self.assertIn("WanboLightOS", content)
        self.assertIn("tvOS", content)
        self.assertIn("WanboLauncher", content)
        self.assertIn("os-pipeline", content)

        # Core Guarantees
        self.assertIn("Zero Emojis", content)
        self.assertIn("Zero Telemetry", content)
        self.assertIn("android.permission.INTERNET", content)
        self.assertIn("350 MB", content)
        self.assertIn("Hardware Preservation", content)

        # Hardware components preserved
        self.assertIn("keystone", content.lower())
        self.assertIn("hdmi", content.lower())
        self.assertIn("bluetooth", content.lower())
        self.assertIn("equalizer", content.lower())

        # Directory map & Quick start links
        self.assertIn("how-to-flash.md", content)
        self.assertIn("how-to-build.md", content)
        self.assertIn("LICENSE", content)

    def test_how_to_flash_exists_and_content(self):
        """
        Verifies docs/how-to-flash.md exists, has zero emojis, and details the 7-step USB procedure.

        @returns {None}
        """
        flash_path = self.docs_dir / "how-to-flash.md"
        self.assertTrue(flash_path.is_file(), "docs/how-to-flash.md must exist")

        content = flash_path.read_text(encoding="utf-8")
        self.assertGreater(len(content), 200, "how-to-flash.md must not be empty")

        # Zero emojis
        emojis = find_emoji_characters(content)
        self.assertEqual(len(emojis), 0, f"how-to-flash.md contains emojis: {emojis}")

        # The 7 USB flashing steps from brief
        self.assertIn("FAT32", content)
        self.assertIn("WanboLightOS_v1.0.bin", content)
        self.assertIn("Power off", content)
        self.assertIn("rear USB port", content)
        self.assertIn("Power button", content)
        self.assertIn("5 seconds", content)
        self.assertIn("MStar", content)
        self.assertIn("2-3 minutes", content)
        self.assertIn("reboot", content.lower())

        # Safety & troubleshooting
        self.assertIn("MBR", content)
        self.assertIn("Troubleshooting", content)

    def test_how_to_build_exists_and_content(self):
        """
        Verifies docs/how-to-build.md exists, has zero emojis, and details build prerequisites and targets.

        @returns {None}
        """
        build_path = self.docs_dir / "how-to-build.md"
        self.assertTrue(build_path.is_file(), "docs/how-to-build.md must exist")

        content = build_path.read_text(encoding="utf-8")
        self.assertGreater(len(content), 200, "how-to-build.md must not be empty")

        # Zero emojis
        emojis = find_emoji_characters(content)
        self.assertEqual(len(emojis), 0, f"how-to-build.md contains emojis: {emojis}")

        # Prerequisites
        self.assertIn("Python 3", content)
        self.assertIn("JDK 17", content)
        self.assertIn("Android SDK", content)
        self.assertIn("MStar", content)

        # Make targets
        self.assertIn("make launcher", content)
        self.assertIn("make rom", content)
        self.assertIn("make test", content)
        self.assertIn("make clean", content)

        # Pipeline explanation
        self.assertIn("build_rom.py", content)
        self.assertIn("debloat-list.txt", content)
        self.assertIn("build.prop.patch", content)
        self.assertIn("CRITICAL_HARDWARE_BLACKLIST", content)

    def test_license_exists_and_content(self):
        """
        Verifies LICENSE exists, has zero emojis, and contains GNU General Public License v3.0.

        @returns {None}
        """
        license_path = self.repo_root / "LICENSE"
        self.assertTrue(license_path.is_file(), "LICENSE file must exist in repository root")

        content = license_path.read_text(encoding="utf-8")
        self.assertGreater(len(content), 500, "LICENSE file must contain full license text")

        # Zero emojis
        emojis = find_emoji_characters(content)
        self.assertEqual(len(emojis), 0, f"LICENSE contains emojis: {emojis}")

        # GPL v3 text verification
        self.assertIn("GNU GENERAL PUBLIC LICENSE", content)
        self.assertIn("Version 3, 29 June 2007", content)
        self.assertIn("Free Software Foundation", content)

    def test_all_documentation_files_zero_emojis(self):
        """
        Recursively scans all markdown, text, and test files in docs/ and root to verify zero emojis.

        @returns {None}
        """
        files_to_check = [
            self.repo_root / "README.md",
            self.repo_root / "LICENSE",
            self.docs_dir / "how-to-flash.md",
            self.docs_dir / "how-to-build.md",
            self.docs_dir / "tests" / "test_docs.py",
        ]

        for file_path in files_to_check:
            self.assertTrue(file_path.exists(), f"File {file_path.name} must exist")
            content = file_path.read_text(encoding="utf-8")
            emojis = find_emoji_characters(content)
            self.assertEqual(len(emojis), 0, f"File {file_path.name} contains emojis: {emojis}")


if __name__ == "__main__":
    unittest.main()
