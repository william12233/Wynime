# Phase 12 Implementation Status

This file records the final current-host audit boundary for performance, security, licensing, packaging and release. The machine-readable sources of truth remain `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`AUDIT_COMPLETE_RELEASE_BLOCKED`

The source-level security and packaging checks are complete for the current head. The repository is not marked release-ready because the host has no FFmpeg executable, Windows Flutter rendering is not observable, supported hardware playback was not exercised, and the bundled native binary license/provenance closure is incomplete. A dedicated Wynime release keystore now exists outside the repository and the four required GitHub Actions secret names have been confirmed; an official signed CI artifact has not yet been produced because the release workflow is still only in the current worktree and the release gate remains blocked.

## Current-head automated evidence

- `dart format --output=none --set-exit-if-changed lib test`: passed, 168 files and zero changes;
- `flutter analyze --no-pub --fatal-infos --suppress-analytics`: passed, `No issues found!`;
- `flutter test --exclude-tags=golden`: passed, 216 tests;
- `flutter test`: passed, 220 tests including Android 360×800, Android 412×915, Windows 1024×768 and Windows 1440×900 Goldens;
- `flutter build apk --debug --no-pub`: passed;
- `flutter build apk --release --no-pub`: passed, unsigned inspection artifact;
- `flutter build appbundle --release --no-pub`: passed, unsigned inspection artifact;
- `zipalign -c -v 4 build/app/outputs/flutter-apk/app-release.apk`: passed;
- `flutter build windows --debug --no-pub`: passed;
- `flutter build windows --release --no-pub`: passed, `build/windows/x64/runner/Release/wynime.exe`;
- a disposable local keystore smoke test passed through the same
  `WYNIME_RELEASE_*` Gradle environment and `apksigner verify`; the test
  keystore was removed and is not release-signing evidence;
- the permanent Wynime release keystore passed a local signed APK smoke test
  through the same `WYNIME_RELEASE_*` Gradle environment; `apksigner verify`
  reported APK Signature Scheme v2 `true` and the inspection APK SHA-256 was
  `2F4D9B79985D841271A152F4D9EB8A290A904561DD01992F5FF90D575E181612`;
  this is local signing evidence only, not an official GitHub CI artifact;
- the existing Windows CMake CMP0175 warning and third-party WebView2 compiler warnings are non-fatal dependency warnings.

## Runtime UI evidence

Operation: `wynime-release-closure-20260903-9F2K`.

- Android phone AVD: `PASS_UI` for the exercised runtime path. `Pixel_API_36_Google_Play` on Android 16 / API 36, `1080x2400`, density `420`, launched successfully; Search navigation, query entry and submission, and Home / Library / Downloads / Sources / Settings navigation were exercised without an app crash.
- Android tablet AVD: `PASS_UI` for the exercised runtime path. `Pixel_Tablet_API_36_Google_Play` on Android 16 / API 36, `2560x1600`, density `320`, launched successfully; the expanded navigation rail, Search query path and all primary pages were exercised without an app crash.
- Windows desktop: `BLOCKED_UI_ENVIRONMENT`. The current app launched with the software-rendering diagnostic flag and exposed a normal Flutter VM widget/render tree, but the native client surface remained a uniformly white, non-observable window and UI Automation exposed only the root Flutter view. A separate default Flutter Windows smoke app showed the same white client surface under both software rendering and `--disable-gpu`, so no action-level Windows result is claimed and no production runner workaround was retained.
- Required independent Sol review: `BLOCKED_EXTERNAL_SOL_UI_STATE`. The required in-app Browser runtime exited during initialization with `windows sandbox failed: helper_unknown_error: setup refresh had errors`; no independent Sol verdict was recorded and no alternate browser was substituted.

Phone/tablet evidence was collected under the temporary operation directory
`%TEMP%\\codex-ui-verification\\wynime-release-closure-20260903-9F2K` and
cleaned after delivery and verification.
The emulator results do not substitute for supported physical Android or
Windows hardware playback evidence, so the overall UI/release gate remains
blocked.

## Security and privacy implementation

- `ProcessFfmpegRunner` accepts only local file URIs inside its configured download root, uses an argument vector with `runInShell: false`, bounds timeout and diagnostics, and rejects missing, linked, directory and outside-root paths before spawning;
- `LocalDownloadFileStore` and `LocalArtifactFileOperations` create parents only after nearest-ancestor and canonical containment checks, refuse linked parents and non-regular targets, and perform atomic temporary-file replacement or same-root promotion;
- deletion still consumes only the authoritative `DownloadArtifactManifest` and `DeleteJob`; orphan scanning remains report-only;
- `DownloadService` rejects oversized raw snapshots and persists only bounded structural HLS metadata, excluding full resource URIs, query strings, credentials, cookies and tokens;
- deterministic tests cover outside-root FFmpeg input, linked destination ancestry, linked download parent, atomic replacement, missing FFmpeg, manifest-only deletion, secret-safe diagnostics, cleartext restrictions, source package budgets and telemetry default-off;
- Android cleartext traffic is disabled except for the explicitly bounded loopback hosts required by the local proxy. No source code logging call or telemetry backend was introduced.

## Android package audit

The latest audited inspection APK predates the v1.0.1 candidate bump: it is
`build/app/outputs/flutter-apk/app-release.apk`, package
`io.github.william12233.wynime`, version `1.0.0` / versionCode `1`, min SDK
24, target/compile SDK 36, and ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`.
It is historical local inspection evidence, not a v1.0.1 release artifact.
Declared runtime permissions are INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK and
the generated non-exported receiver permission. The APK is not debuggable.

The release build is intentionally unsigned when the external keystore properties are absent: `apksigner verify` reports `Missing META-INF/MANIFEST.MF`. The debug APK verifies only with the Android Debug certificate (SHA-256 `0a03c59eb4084a744e58b2ec2578d273e25c0b806ebb07f174c0b208d694b799`) and is not a release artifact. No signing secret is stored in the repository.

## External Android signing configuration

- dedicated Wynime keystore: stored outside the repository;
- required GitHub Actions secret names confirmed present: `WYNIME_RELEASE_KEYSTORE_BASE64`, `WYNIME_RELEASE_KEY_ALIAS`, `WYNIME_RELEASE_KEY_PASSWORD` and `WYNIME_RELEASE_STORE_PASSWORD`;
- secret values were not read or recorded by the audit;
- no official signed CI artifact is claimed until the checked-in workflow is available on GitHub and a tagged run verifies the alias, APK metadata, alignment and signature.

Current artifact hashes:

- release APK SHA-256: `84FF853D392B19E377C83530E634D4C8F3B0EE88BC3B10CAF5E7156BCACE7D05`;
- release AAB SHA-256: `9280D7EEDA0F403895287AF4403C8426A75ECA9A37B3160219DDA218CF3849AE`;
- debug APK SHA-256: `55DDCC2B173457B50CD3123800943F96542679A32943136F38C124755A32AF4E` (test-only and debug-signed);
- Windows debug `wynime.exe` SHA-256: `6B448C3AB90E35F87CA6C6B279B7E28FE1D15CE4C4F840F0C6D5EEA6786698AB`;
- Windows release `wynime.exe` SHA-256: `0C523026DF5128EE9F6F3D9803EBBB96A829A18738360D593995AA8B90A96699`.

The local release-preparation script previously produced versioned 1.0.0
inspection assets under the ignored `build/release` directory. The multi-ABI APK is
`wynime-1.0.0.apk` with SHA-256
`84FF853D392B19E377C83530E634D4C8F3B0EE88BC3B10CAF5E7156BCACE7D05`; the
Windows x64 portable ZIP is `wynime-1.0.0.zip` with SHA-256
`1F9AB3FEE452D4B9EE136BC80EF879DC7D538CE2657A2D229C043360483E8A8C`.
Both sidecars match. The ZIP contains all 30 files from the Flutter Release
directory plus `README.md` and `version.txt` (32 file entries and 9 directory
entries); its entry set includes
`wynime.exe`, `flutter_windows.dll`, `libmpv-2.dll`, `sqlite3.dll` and
`data/flutter_assets/NOTICES.Z`. The APK remains intentionally unsigned and
is therefore an inspection artifact, not a publishable release.

The release APK's packaged `libmpv.so` entries were read back from the ZIP and matched against the corresponding Gradle transform outputs: `arm64-v8a` 12,369,680 bytes / `ADF83FDE58A9F6751CE6E83B9B187F651425D53EC0E20DA04DEB8DFB4AA775E1`, `armeabi-v7a` 11,746,532 bytes / `8AA2B23D16D941A8D685B8EB995B1CB31677ED3E28C55C776954EB1618AB1A2E`, and `x86_64` 15,816,336 bytes / `F030E9A1A4D4664E89D7160A3D4528CD5875EE023FF6262E16E95AAC28FFAEC5`. This verifies local packaging identity; the input JAR digest match is recorded below. It does not close source, build-flag or license provenance.

## Native provenance and license boundary

The locked pub packages and downloaded native archives were inspected. Windows libmpv input is `mpv-dev-x86_64-20230924-git-652a1dd.7z` with MD5 `A832EF24B3A6FF97CD2560B5B9D04CD8` and SHA-256 `DCE982222D7A23E4A1C6F0FB6CC39F6E899A6714624B95EA49CFF6558EE97572`; ANGLE input `ANGLE.7z` has MD5 `E866F13E8D552348058AFAAFE869B1ED` and SHA-256 `CC5911BB15D596FD5A2B362613AD35B7093B427117269A7359054A65746A5F9A`. The package build scripts verify MD5 and download from their declared GitHub release URLs.

These hashes establish the selected archive bytes, but the archives do not include a complete license bundle and the repository does not yet record exact libmpv/FFmpeg/ANGLE build flags, linked library versions or redistribution notices. Android native libraries are present in the APK for all three packaged ABIs, but the same closure is not established. Native provenance/license review therefore remains a release blocker.

The rebuilt APK's Flutter `assets/flutter_assets/NOTICES.Z` was decompressed successfully (1,417,567 bytes). It contains generated Flutter/Dart/plugin notices and sections for dependencies such as `media_kit`, ANGLE and SQLite, but a keyword scan found no `libmpv` or `FFmpeg` native notice section. The generated notice payload therefore does not substitute for the missing native redistribution record.

The checkout has no root project `LICENSE` or `NOTICE` file. The application's own distribution license cannot be inferred from dependency notices and must be explicitly selected and approved before publication.

### Official upstream cross-check

- The official [`libmpv-android-video-build` v1.1.7 release](https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7) exposes these SHA-256 digests for the four ABI JARs: `default-arm64-v8a.jar` `4363dfa5d3d415b91c1f16f6fb90c3fe59a77dfd3f9b824d2b24b492d6b09df9`, `default-armeabi-v7a.jar` `8ead114fc5a43348d89dc0eb8f41823e549b15115c29f73ee26973f973620995`, `default-x86.jar` `94c13cb6188b774710e5e487afff6e500c4af504df74b2494d7b12cf9be8a66a`, and `default-x86_64.jar` `90268cd15f0766e07fb8e427388c621161177c9eb343c544f327bd63232bb236`. Its [`v1.1.7` dependency manifest](https://github.com/media-kit/libmpv-android-video-build/blob/v1.1.7/buildscripts/include/depinfo.sh) records FFmpeg 6.0, mpv commit `78d43740f52db817d98bcf24fb30a76ab6fa13ff` and the supporting dependency versions; the default flavor uses `--disable-gpl`, `--disable-nonfree` FFmpeg and `gpl=false` for libmpv. The upstream [`LICENSE`](https://github.com/media-kit/libmpv-android-video-build/blob/v1.1.7/LICENSE) is evidence for that source build repository, not a substitute for a complete transitive redistribution notice set for the APK.
- The current build output contains the original v1.1.7 ABI JARs. Their local SHA-256 values directly match the official release digests: `default-arm64-v8a.jar` `4363dfa5d3d415b91c1f16f6fb90c3fe59a77dfd3f9b824d2b24b492d6b09df9`, `default-armeabi-v7a.jar` `8ead114fc5a43348d89dc0eb8f41823e549b15115c29f73ee26973f973620995`, `default-x86.jar` `94c13cb6188b774710e5e487afff6e500c4af504df74b2494d7b12cf9be8a66a`, and `default-x86_64.jar` `90268cd15f0766e07fb8e427388c621161177c9eb343c544f327bd63232bb236`. This closes the Android upstream archive identity check only; it does not close the transitive license notice or native redistribution review.
- The official [Windows 2023-09-24 release](https://github.com/media-kit/libmpv-win32-video-build/releases/tag/2023-09-24) identifies the selected mpv development archive and links it to mpv commit `652a1dd90711839acdccc08004056d25514ef2d`; its release asset metadata did not provide a SHA-256 digest. The audited release tag contains only the version marker, so the current-master [`mpv.cmake`](https://github.com/media-kit/libmpv-win32-video-build/blob/master/packages/mpv.cmake) and [`ffmpeg.cmake`](https://github.com/media-kit/libmpv-win32-video-build/blob/master/packages/ffmpeg.cmake) flags cannot be treated as proof of the historical binary's exact build configuration.
- The official [ANGLE v1.0.1 release](https://github.com/alexmercerind/flutter-windows-ANGLE-OpenGL-ES/releases/tag/v1.0.1) identifies the selected archive, but the audited archive/repository listing did not yield a complete license bundle for redistribution. The local archive hashes remain the authoritative bytes actually inspected in this checkout.

This cross-check narrows the provenance gap but does not close it: exact historical Windows build inputs/flags, complete linked-library license notices and a reproducible redistribution record are still required before release packaging.

## Runtime and document boundaries

- `where.exe ffmpeg` finds no executable; real MP4 remux, MKV fallback and media-fixture verification remain `UNVERIFIED_ENVIRONMENT`;
- Phase 6 playback remains `prototype_not_hardware_validated`; deterministic fake-backed Android/Windows platform tests do not prove hardware decoding/rendering;
- Android phone and tablet action evidence passed individually, while Phase 11 overall UI remains `BLOCKED_UI_ENVIRONMENT` because Windows normal and software-rendering launches showed a title bar with a uniformly white client surface and no observable interaction state; the independent default Flutter smoke app reproduced the same host behavior;
- The 2026-09-03 operation-specific AVD rerun revalidated phone search focus, text entry, local submission, settings navigation and scrolling, plus tablet navigation/settings/scrolling. Tablet text injection did not update the search field in that rerun, so no new tablet search-submit pass is claimed; the existing cross-platform status remains blocked by Windows observability and the other gates above;
- the detailed plan DOCX was structurally validated after regeneration (69,164 bytes, 544 non-empty paragraphs, with Phase 12 Gate and ADR-024 present in ZIP/OXML); visual page rendering could not run because LibreOffice/`soffice` is unavailable;
- no Git tag, GitHub Release, installer publication or push was performed.

## Release decision

`RELEASE_BLOCKED` until all of the following are supplied and independently verified: reviewed FFmpeg runtime and real remux fixture, native provenance/build flags/linked-license closure, official external Android signing evidence, observable Windows action-level UI, and supported Android/Windows hardware playback. The current source and deterministic test gates are complete and must not be reworded as those runtime or legal passes.
