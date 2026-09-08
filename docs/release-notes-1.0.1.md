# Wynime 1.0.1

Status: release-ready; publication is produced only by the immutable
GitHub Actions release workflow after all hard gates pass.

## Platforms

- Android multi-ABI APK
- Windows x64 installer
- Windows x64 portable ZIP

The Android AAB is retained as an internal or Play Store handoff artifact and
is not part of the GitHub Release asset set.

## Included boundary

- Shared `PlaybackSession` routing with Android Media3 preference and the
  media-kit/libmpv fallback boundary.
- Fail-closed track, timeline, generation and diagnostic contracts.
- Responsive Home, Search, Library, Downloads, Sources and Settings pages.
- English, Traditional Chinese, Simplified Chinese and Japanese localization
  resources.
- Engineering native provenance, exact archive/binary hashes, the complete
  applicable Android and Windows LGPLv3 text plus the accompanying GPLv3
  Combined Work text,
  corresponding-source/relink offer and packaged third-party notices.
- A signed Android APK, an unsigned Windows x64 installer and a portable
  Windows x64 ZIP, each with a SHA-256 sidecar.

## Validation

- Automated format, analyzer, deterministic tests, golden tests and CI
  builds: required hard gates.
- Android APK signing and `apksigner verify`: required hard gates.
- Emulator action-level UI evidence: passed where exercised.
- Physical Android hardware playback: `HARDWARE_VALIDATION_PENDING`.
- Physical Windows hardware playback: `HARDWARE_VALIDATION_PENDING`.
- Windows native Computer Use action-level validation:
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` (`apps=[]`) in the audit environment.

The pending or unavailable external validation items are disclosed
non-blocking QA, not claimed as passes.

## Windows installer

The installer is built with the `ISCC.exe` available on the `windows-2025`
runner. It installs the complete Flutter Windows Release bundle, creates a
Start Menu shortcut, offers an unchecked optional desktop shortcut, includes
`LICENSE`, `COPYING.GPLv3`, `COPYING.LGPLv3`, `THIRD_PARTY_SOURCE_OFFER.md`,
`THIRD_PARTY_NOTICES.md`, `README.md` and `RELEASE_NOTES.md`, and supports
complete uninstall without deleting Wynime user data.

No Authenticode certificate is configured for this release. The installer is
intentionally unsigned and Windows may display a SmartScreen warning. No
code-signing evidence is implied.

## Not included

Downloads, AES-128 download execution, FFmpeg/remuxing, Bangumi account or
synchronization work, DRM or paywall bypass, magnet/BT/seeding, and
source-provided executable adapters remain outside this release boundary.
