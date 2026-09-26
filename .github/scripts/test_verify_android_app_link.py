#!/usr/bin/env python3
"""Regression tests for Android App Link release-gate parsing."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("verify_android_app_link.py")
SPEC = importlib.util.spec_from_file_location("verify_android_app_link", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load verify_android_app_link.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ParseApkSignerOutputTest(unittest.TestCase):
    def test_accepts_current_apksigner_output_without_colon_after_signer(self) -> None:
        output = """\
Number of signers: 1
Signer #1 certificate SHA-256 digest: 32fa14329bda4ddd9c2f9d6be973ef70a4272b5630abbab714ff50f0f6254b53
"""

        self.assertEqual(
            MODULE.parse_apk_signer_output(output),
            "32:FA:14:32:9B:DA:4D:DD:9C:2F:9D:6B:E9:73:EF:70:"
            "A4:27:2B:56:30:AB:BA:B7:14:FF:50:F0:F6:25:4B:53",
        )

    def test_accepts_legacy_output_with_colon_after_signer(self) -> None:
        output = """\
Number of signers: 1
Signer #1: certificate SHA-256 digest: 32fa14329bda4ddd9c2f9d6be973ef70a4272b5630abbab714ff50f0f6254b53
"""

        self.assertEqual(
            MODULE.parse_apk_signer_output(output),
            "32:FA:14:32:9B:DA:4D:DD:9C:2F:9D:6B:E9:73:EF:70:"
            "A4:27:2B:56:30:AB:BA:B7:14:FF:50:F0:F6:25:4B:53",
        )


if __name__ == "__main__":
    unittest.main()
