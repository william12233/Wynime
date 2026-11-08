#!/usr/bin/env python3

import json
import os
import shutil
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path


API_URL = "https://api.github.com/repos/wixtoolset/wix3/releases/tags/wix3112rtm"
ASSET_NAME = "wix311-binaries.zip"
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}


def request_with_retries(request: urllib.request.Request):
    last_error = None
    for attempt in range(1, 6):
        try:
            return urllib.request.urlopen(request, timeout=120)
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code not in RETRYABLE_STATUS_CODES or attempt == 5:
                raise
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            if attempt == 5:
                raise
        time.sleep(attempt * 2)
    raise RuntimeError(f"WiX request failed: {last_error}")


def github_headers(accept: str) -> dict[str, str]:
    headers = {
        "Accept": accept,
        "User-Agent": "Wynime-CI",
    }
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def download_asset(asset_url: str, destination: Path) -> None:
    request = urllib.request.Request(
        asset_url,
        headers=github_headers("application/octet-stream"),
    )
    with request_with_retries(request) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def main() -> None:
    runner_temp = os.environ.get("RUNNER_TEMP")
    github_env = os.environ.get("GITHUB_ENV")
    if not runner_temp or not github_env:
        print("RUNNER_TEMP and GITHUB_ENV are required.", file=sys.stderr)
        sys.exit(1)

    metadata_request = urllib.request.Request(
        API_URL,
        headers=github_headers("application/vnd.github+json"),
    )
    with request_with_retries(metadata_request) as response:
        release = json.load(response)

    asset = next(
        (candidate for candidate in release.get("assets", []) if candidate.get("name") == ASSET_NAME),
        None,
    )
    if not asset or not asset.get("url"):
        raise RuntimeError(f"GitHub release does not contain {ASSET_NAME}.")

    archive_path = Path(runner_temp) / ASSET_NAME
    wix_path = Path(runner_temp) / "wix311"
    staging_path = Path(tempfile.mkdtemp(prefix="wix311-", dir=runner_temp))

    try:
        for attempt in range(1, 4):
            try:
                download_asset(asset["url"], archive_path)
                expected_size = asset.get("size")
                if expected_size and archive_path.stat().st_size != expected_size:
                    raise RuntimeError(
                        f"Downloaded {archive_path.stat().st_size} bytes, expected {expected_size}."
                    )
                break
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(attempt * 2)

        with zipfile.ZipFile(archive_path) as archive:
            staging_root = staging_path.resolve()
            for member in archive.infolist():
                target = (staging_path / member.filename).resolve()
                if staging_root != target and staging_root not in target.parents:
                    raise RuntimeError(f"Unsafe WiX archive entry: {member.filename}")
            archive.extractall(staging_path)

        if not (staging_path / "candle.exe").is_file():
            raise RuntimeError("Downloaded WiX archive does not contain candle.exe at its root.")

        if wix_path.exists():
            shutil.rmtree(wix_path)
        staging_path.replace(wix_path)
        with Path(github_env).open("a", encoding="utf-8") as output:
            output.write(f"WIX_PATH={wix_path}\n")
        print(f"WiX prepared at {wix_path}")
    finally:
        if staging_path.exists():
            shutil.rmtree(staging_path)
        if archive_path.exists():
            archive_path.unlink()


if __name__ == "__main__":
    main()
