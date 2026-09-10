#!/usr/bin/env python3
"""Verify an Android release signer against Wynime's App Link metadata."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any


COMPACT_SHA256 = re.compile(r"^[0-9A-Fa-f]{64}$")
COLON_SHA256 = re.compile(r"^(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}$")
PACKAGE_NAME = re.compile(r"^[A-Za-z0-9_.]+$")
REQUIRED_RELATION = "delegate_permission/common.handle_all_urls"
ANSI_ESCAPE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]|\x1b[@-_]")


def fail(message: str) -> None:
    print(f"Android App Link gate failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def normalize_fingerprint(value: Any, label: str) -> str:
    if not isinstance(value, str):
        fail(f"{label} is not a string")
    if COMPACT_SHA256.fullmatch(value):
        compact = value
    elif COLON_SHA256.fullmatch(value):
        compact = value.replace(":", "")
    else:
        fail(f"{label} is not a 64-hex or 32-byte colon fingerprint")
    return ":".join(compact[index : index + 2] for index in range(0, 64, 2)).upper()


def run_tool(command: list[str], label: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        fail(f"{label} returned exit code {result.returncode}")
    output = f"{result.stdout}\n{result.stderr}"
    return ANSI_ESCAPE.sub("", output).replace("\r\n", "\n").replace("\r", "\n")


def load_config(path: Path) -> tuple[str, str]:
    try:
        with path.open("rb") as config_file:
            document = tomllib.load(config_file)
    except (OSError, tomllib.TOMLDecodeError) as error:
        fail(f"cannot read {path}: {error}")
    variables = document.get("vars")
    if not isinstance(variables, dict):
        fail("wrangler.toml has no [vars] table")
    package_name = variables.get("ANDROID_PACKAGE_NAME")
    if not isinstance(package_name, str) or not PACKAGE_NAME.fullmatch(package_name):
        fail("ANDROID_PACKAGE_NAME is missing or malformed")
    fingerprint = normalize_fingerprint(
        variables.get("ANDROID_CERT_SHA256"), "wrangler.toml ANDROID_CERT_SHA256"
    )
    return package_name, fingerprint


def read_apk_signer(apksigner: Path, apk: Path) -> str:
    output = run_tool(
        [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
        "apksigner",
    )
    signer_counts = re.findall(r"^\s*Number of signers:\s*(\d+)\s*$", output, re.MULTILINE)
    if signer_counts != ["1"]:
        fail("APK must report exactly one signer")
    digest_lines = re.findall(
        r"(?:Signer\s+#\d+|V\d+(?:\.\d+)?\s+Signer):\s*"
        r"certificate\s+SHA-256\s+digest:\s*"
        r"((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}|[0-9A-Fa-f]{64})",
        output,
        re.IGNORECASE,
    )
    if len(digest_lines) != 1:
        fail("APK must report exactly one signer SHA-256 digest")
    return normalize_fingerprint(digest_lines[0], "APK signer SHA-256 digest")


def read_apk_package(aapt2: Path, apk: Path) -> str:
    output = run_tool([str(aapt2), "dump", "badging", str(apk)], "aapt2")
    package_names = re.findall(r"^package:\s+name='([^']+)'", output, re.MULTILINE)
    if len(package_names) != 1:
        fail("APK must report exactly one package name")
    return package_names[0]


def verify_assetlinks(path: Path, package_name: str, signer: str) -> None:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        fail(f"assetlinks.json is not valid UTF-8 JSON: {error}")
    if not isinstance(document, list):
        fail("assetlinks.json top level must be an array")

    matching_statement = False
    for index, statement in enumerate(document):
        if not isinstance(statement, dict):
            fail(f"assetlinks statement {index} is not an object")
        target = statement.get("target")
        if not isinstance(target, dict):
            fail(f"assetlinks statement {index} has no target object")
        if target.get("namespace") != "android_app":
            continue
        fingerprints = target.get("sha256_cert_fingerprints")
        if not isinstance(fingerprints, list) or not fingerprints:
            fail(f"assetlinks statement {index} has no certificate fingerprints")
        normalized = [
            normalize_fingerprint(value, f"assetlinks statement {index} fingerprint")
            for value in fingerprints
        ]
        relations = statement.get("relation")
        if not isinstance(relations, list) or not all(
            isinstance(relation, str) for relation in relations
        ):
            fail(f"assetlinks statement {index} has malformed relation")
        if (
            target.get("package_name") == package_name
            and REQUIRED_RELATION in relations
            and signer in normalized
        ):
            matching_statement = True

    if not matching_statement:
        fail("live assetlinks.json has no matching package/relation/signer statement")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--assetlinks", type=Path)
    arguments = parser.parse_args()

    for path in (arguments.apk, arguments.apksigner, arguments.aapt2, arguments.config):
        if not path.is_file():
            fail(f"required path is missing: {path}")
    if arguments.assetlinks is not None and not arguments.assetlinks.is_file():
        fail(f"assetlinks file is missing: {arguments.assetlinks}")

    package_name, configured_signer = load_config(arguments.config)
    apk_signer = read_apk_signer(arguments.apksigner, arguments.apk)
    if apk_signer != configured_signer:
        fail("APK signer does not match wrangler.toml production association metadata")
    apk_package = read_apk_package(arguments.aapt2, arguments.apk)
    if apk_package != package_name:
        fail("APK package does not match wrangler.toml production association metadata")
    if arguments.assetlinks is not None:
        verify_assetlinks(arguments.assetlinks, package_name, apk_signer)

    if arguments.assetlinks is None:
        print("ANDROID_APP_LINK_SIGNER_GATE_PASS")
    else:
        print("ANDROID_APP_LINK_LIVE_ASSOCIATION_GATE_PASS")


if __name__ == "__main__":
    main()
