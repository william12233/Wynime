# Phase 12 Implementation Status

This file records the machine-verifiable release boundary for performance,
security, licensing, packaging and publication. The machine-readable sources
of truth remain `AGENTS.md`, `docs/PROJECT_PLAN.md`,
`docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`RELEASE_READY`

The v1.0.4 release boundary is ready for exact-SHA CI and protected
publication. The final candidate SHA must still be frozen and revalidated by
the main, signing and release workflows; no unexecuted hardware or Computer
Use result is represented as a pass.

Approved Wynime distribution license: yes

The root `LICENSE` is the repository owner's MIT license for Wynime-owned
source code only. It does not change the terms of any third-party dependency,
native binary, codec or system runtime.

## Prior v1.0.3 machine release baseline

- Current-head `dart format --output=none --set-exit-if-changed lib test`:
  pass.
- `flutter analyze --fatal-infos`: pass with no analyzer issues.
- Full deterministic Flutter test suite and the repository's fixed-size
  golden tests: pass.
- Android debug and arm64-v8a Android release APK builds: pass. No universal
  APK or release AAB is published.
- Official Android signing uses the configured Wynime
  `WYNIME_RELEASE_*` keystore only; `apksigner verify`, APK alignment,
  version metadata and ABI checks are required and recorded by CI.
- Windows x64 Flutter Release build: pass on the declared toolchain.
- Engineering native provenance, archive identity, packaged-binary hashes,
  the applicable Android and Windows LGPLv3 text, the accompanying GPLv3
  Combined Work text,
  corresponding-source/relink offer and shipped notices:
  `CLOSED_RELEASE_PROVENANCE`.
- Android APK and Windows x64 portable ZIP each have a matching SHA-256
  sidecar.
- No signing secret, keystore or password is stored in the repository or
  release artifacts.
- The prior versioned release notes described the actual v1.0.3 boundary.
- The release workflow requires the tag, trigger SHA, `origin/main`,
  successful exact-SHA phase-0 CI and publication checkout to agree.

The protected candidate-signing workflow remains useful as exact-SHA signing
evidence before tagging. The final release workflow independently rebuilds and
verifies the signed APK and all public assets.

## 1.0.4 candidate status

- Version authority is `1.0.4+5`; the expected arm64 split APK versionCode is
  `2005`.
- GitHub repository variable `WYNIME_BANGUMI_BROKER_ORIGIN` is configured with
  the production Worker origin.
- The Worker `ANDROID_CERT_SHA256` configuration is deployed and the public
  `assetlinks.json` now contains the exact production package and certificate
  fingerprint.
- Flutter analyzer, the full deterministic test suite (218 tests), broker
  typecheck and broker tests (9/9) pass on this candidate worktree.
- Local Android arm64-v8a Release APK build passes with versionCode `2005` and
  versionName `1.0.4`; it is unsigned locally and is not the protected CI
  signing result.
- Local Windows x64 Release compile/install passes and the Release process
  stays responsive. The Windows Debug launch still fails in third-party
  Debug-CRT linking, while the native Computer Use surface exposes `apps=[]`.
- After the host restart, `WHPX` reports installed and usable. Both fixed
  Android 16 / API 36 AVDs booted, accepted the Debug APK, and launched
  Wynime. The phone and tablet Home screens and Bangumi OAuth entry flow were
  captured. The user subsequently reported completing the account callback
  test; this remains external manual evidence and is not substituted for
  physical playback evidence.
- The App's public Bangumi client ID was aligned with the Worker configuration.
  Both AVD OAuth entry flows reached the Bangumi login page instead of the
  Worker `invalid_oauth_request` response.

## External validation disclosure

The following items are non-blocking QA and are intentionally not upgraded to
release gates:

- Android physical hardware playback:
  `HARDWARE_VALIDATION_PENDING`.
- Windows physical hardware playback:
  `HARDWARE_VALIDATION_PENDING`.
- Windows native Computer Use action-level validation:
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` (`apps=[]`) in the audit environment.
- Additional manual exploratory UI testing: `PENDING`.

The fixed Android phone and tablet emulator action paths were exercised for
the earlier v1.0.1 shell and recorded as deterministic UI evidence. They do
not substitute for physical hardware playback or action-level validation of
the new Bangumi/update flows. The unavailable Windows Computer Use surface is
recorded as an environment limitation and does not claim compact, expanded,
live resize, mouse or keyboard actions passed.

## Runtime and UI evidence

Operation: `Wynime-Bangumi-1.0.4-20260909-185900-cf7a`.

- Current local Android arm64-v8a Release APK: metadata and ABI checks pass
  (`versionCode=2005`, `versionName=1.0.4`, `arm64-v8a`); local
  `apksigner verify` correctly reports it is unsigned. Protected CI signing
  remains required.
- Current local Windows x64 Release binary: compile/install and an 8-second
  process liveness check pass. Native action-level UI could not be observed
  because the Computer Use session exposed only the in-app browser and
  `apps=[]`.
- Current fixed Android phone/tablet AVD launch: pass after the host restart;
  both AVDs booted and launched Wynime. Home and OAuth-entry screenshots are
  retained. User-reported account authorization and OS callback testing
  completed; physical playback remains pending.

- Android phone AVD `Pixel_API_36_Google_Play`: Android 16 / API 36,
  1080×2400, density 420. The updated Debug APK launched, the Home screen was
  captured, and the Bangumi sign-in action reached the Bangumi login page.
- Android tablet AVD `Pixel_Tablet_API_36_Google_Play`: Android 16 / API 36,
  2560×1600, density 320. The updated Debug APK launched, the expanded Home
  screen was captured, and the Bangumi sign-in action reached the Bangumi
  login page.
- Windows Release binary: build completed and the process launched
  responsively. Native action-level UI could not be observed because the
  fresh Computer Use session exposed only the in-app browser and
  `apps=[]`.

Evidence is retained outside the repository under the operation-specific
verification directory and is not used to claim physical hardware playback.

## Native engineering provenance

The locked dependency mechanism and candidate package contents were inspected
for every native component that enters the release distributions:

- `media_kit 1.2.6`, `media_kit_video 2.0.1`,
  `media_kit_libs_android_video 1.3.8` and
  `media_kit_libs_windows_video 1.0.12` from the pinned media-kit commit
  `e9abf3b9114fdb565b13a4c194d776c70e416e7d`;
- `flutter_inappwebview 6.2.0-beta.3`,
  `flutter_inappwebview_android 1.2.0-beta.3` and
  `flutter_inappwebview_windows 0.7.0-beta.3`;
- `sqlite3_flutter_libs 0.6.0+eol`;
- Android default ABI JARs from media-kit libmpv build release `v1.1.7`,
  with FFmpeg 6.0, mpv revision
  `78d43740f52db817d98bcf24fb30a76ab6fa13ff`, package-declared MD5 values
  and candidate SHA-256 values;
- Windows `mpv-dev-x86_64-20241021-git-0f78584.7z` from build source commit
  `8ddbe5472465950b87853789f7173f2eedc5586a` and `ANGLE.7z` v1.0.1, with
  package-declared MD5 values, local archive SHA-256 values, runtime
  version/configuration and packaged DLL hashes;
- Flutter/Dart runtime, WebView2 loader, SQLite, ANGLE/Vulkan/SwiftShader,
  zlib, media-kit plugin DLLs and all other native files in the Windows
  Release directory.

The selected Android default flavor disables GPL/nonfree FFmpeg options and
enables FFmpeg's version-3 licensing option according to the pinned upstream
`buildscripts/flavors/default.sh` record; it is covered by the complete
`COPYING.LGPLv3` text. LGPLv3 Section 4(b) also requires the accompanying
`COPYING.GPLv3` text; its exact upstream blob and SHA-256 are locked in the
native provenance record and both license files are packaged and checked. The
Windows release DLL runtime reports mpv
`v0.39.0-179-g0f78584518`, FFmpeg `N-117622-g8d940a07d` from exact commit
`8d940a07d19023a98689f353e4425a14688547e9`, `-Dgpl=false`, `-Dlibmpv=true`,
`-Dprefer_static=True` and static FFmpeg linkage. The exact Windows FFmpeg
policy is pinned to `packages/ffmpeg.cmake` blob
`ffbcbfc34882110acf2c271bdae994570bd62c39`, requiring
`--disable-gpl --disable-nonfree --enable-version3 --enable-static
--disable-shared` and rejecting both forbidden enable flags. The Windows
`--enable-version3` path is covered by the complete `COPYING.LGPLv3` text
and the accompanying `COPYING.GPLv3` text required by LGPLv3 Section 4(b).
The machine-readable lock and runtime verifier close this provenance chain;
no alternate FFmpeg or native package was substituted to satisfy the gate.

The exact upstream references, archive hashes, packaged hashes, runtime
probe, dependency versions and notice mapping are maintained in
`docs/THIRD_PARTY_PROVENANCE.md`. The same candidate notice material is
verified in the Android APK and Windows distributions by release automation.
The distributions carry `COPYING.LGPLv3` for both Android's pinned
`--enable-version3` native media and Windows' `--enable-version3` native
media, together with `THIRD_PARTY_SOURCE_OFFER.md`. The latter identifies the exact
corresponding source and relink procedure for the statically incorporated
FFmpeg/libmpv components.
No independent legal opinion was obtained; this is a disclosure, not a
machine release blocker under ADR-025.

## Package boundary

The Windows portable ZIP includes the complete Flutter Release directory plus
`README.md`, `LICENSE`, `THIRD_PARTY_NOTICES.md`, `COPYING.GPLv3`,
`COPYING.LGPLv3`,
`THIRD_PARTY_SOURCE_OFFER.md`,
`WINDOWS_LIBMPV_BUILD.lock.json`, `RELEASE_NOTES.md` and version-only
`version.txt`.

The historical `installer/windows/wynime.iss` file is retained as reference
material only. It is not invoked by the current release workflow. Windows
publication is ZIP-only and the ZIP includes the complete Flutter Release
bundle, the portable `wynime_update.exe` helper, version file, license texts,
corresponding-source offer, native provenance lock, notices and release notes.

Standalone FFmpeg execution, remuxing, MKV fallback, DRM/paywall bypass,
magnet/BT/seeding and source-provided executable adapters remain outside this
1.0.4 candidate boundary. Bangumi source integration and production Worker
configuration are included, while live account validation and platform runtime
evidence remain open until directly exercised.

## Security and privacy

- Playback uses the authoritative `PlaybackSession`, numeric loopback
  capability URI and exact current-session track identity.
- Platform errors are reduced to stable secret-safe diagnostic codes.
- Android cleartext traffic remains disabled except for bounded loopback
  hosts used by the local proxy.
- Telemetry is disabled by default.
- No signing secret, cookie, token or complete upstream media URL is persisted
  in release evidence or package metadata.

## Release decision

The machine-verifiable status is `RELEASE_READY`. External
validation disclosures remain visible and must be preserved in release notes;
they must not be rewritten as passes or used to imply hardware playback.
Publication still requires the exact annotated tag, exact-SHA CI, signed APK
verification, ZIP build, four-asset checksum verification and the protected
GitHub release environment.
