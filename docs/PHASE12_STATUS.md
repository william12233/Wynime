# Phase 12 Implementation Status

This file records the final current-host audit boundary for performance, security, licensing, packaging and release. The machine-readable sources of truth remain `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`AUDIT_COMPLETE_RELEASE_BLOCKED`

The source-level security and packaging checks are complete for the current
candidate. The local Windows Flutter Release build now passes, but the
repository is not marked release-ready because Windows action-level rendering
is not observable through the available Computer Use surface, supported
hardware playback was not exercised, the bundled native binary
license/provenance closure is incomplete, and official external signing has
not yet been exercised. Standalone FFmpeg execution, remuxing and MKV
fallback are explicitly outside the 1.0.1 release boundary; their absence is
recorded as future scope rather than treated as a gate for this candidate. A
dedicated Wynime release keystore exists outside the repository and the four
required GitHub Actions secret names are confirmed; an official signed CI
artifact has not yet been produced because the release workflow remains
fail-closed on the release gate.

## Current-head automated evidence

- `dart format --output=none --set-exit-if-changed lib test`: passed, 124 files and zero changes;
- `flutter analyze --no-pub --fatal-infos --suppress-analytics`: passed, `No issues found!`;
- `flutter test`: passed, 170 tests including Android 360×800, Android 412×915, Windows 1024×768 and Windows 1440×900 Goldens;
- `flutter build apk --debug --no-pub`: passed;
- `flutter build apk --release --no-pub`: passed, unsigned inspection artifact;
- `flutter build appbundle --release --no-pub`: passed, unsigned inspection artifact;
- the release APK and AAB contain `assets/third_party/THIRD_PARTY_NOTICES.md`;
- `.github/scripts/build_release_assets.ps1 -SkipBuild -AllowBlockedInspection`: passed and produced versioned APK/ZIP sidecars;
- `flutter build windows --release --no-pub`: passed, `build/windows/x64/runner/Release/wynime.exe`, using the installed VS2026 toolchain and the repository-declared WebView2 package versions;
- a disposable local keystore smoke test passed through the same
  `WYNIME_RELEASE_*` Gradle environment and `apksigner verify`; the test
  keystore was removed and is not release-signing evidence;
- the permanent Wynime release keystore passed a local signed APK smoke test
  through the same `WYNIME_RELEASE_*` Gradle environment; `apksigner verify`
  reported APK Signature Scheme v2 `true` and the inspection APK SHA-256 was
  `2F4D9B79985D841271A152F4D9EB8A290A904561DD01992F5FF90D575E181612`;
  this is local signing evidence only, not an official GitHub CI artifact;
- the Windows CMake CMP0175 warning and third-party WebView2 compiler warnings are non-fatal dependency warnings.

## Runtime UI evidence

Operation: `wynime-release-0.1.0-20260906-7K4M`.

- Android phone AVD: `PASS_UI` for the exercised runtime path. `Pixel_API_36_Google_Play` on Android 16 / API 36, `1080x2400`, density `420`, launched the candidate debug APK; Search navigation, `Wynime` entry and submission, Home / Library / Downloads / Sources / Settings navigation, and the diagnostics `false → true → false` transition were exercised without an app crash.
- Android tablet AVD: `PASS_UI` for the exercised runtime path. `Pixel_Tablet_API_36_Google_Play` on Android 16 / API 36, `2560x1600`, density `320`, launched the candidate debug APK; the expanded navigation rail, Search query submission, Library → Watching, Downloads, Sources, Settings and the diagnostics `false → true → false` transition were exercised without an app crash.
- Windows desktop: `BLOCKED_UI_ENVIRONMENT`. The Release binary launched and remained responsive, but `mcp__cua_repl.getState()` exposed no native Windows app surface (`apps: []`); only the in-app browser was observable. No Windows compact/expanded/live-resize/mouse/keyboard action-level result is claimed.
- Required independent Sol review: `SOL_REVIEW_FAIL` for this operation. The complete finding is retained in the Sol conversation; the candidate must be pushed and re-reviewed against the final SHA after the remaining gates close.

Phone/tablet evidence was collected under the temporary operation directory
`%TEMP%\\codex-ui-verification\\wynime-release-0.1.0-20260906-7K4M` in the
`phone-f4f7af1` and `tablet-f4f7af1` subdirectories and the emulators were
stopped after each run.
The emulator results do not substitute for supported physical Android or
Windows hardware playback evidence, so the overall UI/release gate remains
blocked.

## Security and privacy implementation

- This 1.0.1 candidate contains no standalone download executor, FFmpeg
  runner, remuxer or MKV fallback path. Those capabilities remain explicitly
  out of scope and are not represented as release evidence.
- The Phase 6 playback boundary accepts only the authoritative
  `PlaybackSession`, numeric loopback capability URI and exact current-session
  track identity; platform errors are reduced to stable secret-safe codes.
- Android cleartext traffic is disabled except for the explicitly bounded
  loopback hosts required by the local proxy. No source logging call or
  telemetry backend was introduced.

## Android package audit

The latest audited candidate inspection APK is rebuilt from the final
candidate source tree after the release-evidence commit; it is
`build/app/outputs/flutter-apk/app-release.apk`, package
`io.github.william12233.wynime`, version `1.0.1` / versionCode `2`, min SDK
24, target/compile SDK 36, and ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`.
It remains an unsigned local inspection artifact, not a publishable release.
Declared runtime permissions are INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK and
the generated non-exported receiver permission. The APK is not debuggable.

The release build is intentionally unsigned when the external keystore properties are absent: `apksigner verify` reports `Missing META-INF/MANIFEST.MF`. The debug APK verifies only with the Android Debug certificate (SHA-256 `0a03c59eb4084a744e58b2ec2578d273e25c0b806ebb07f174c0b208d694b799`) and is not a release artifact. No signing secret is stored in the repository.

## External Android signing configuration

- dedicated Wynime keystore: stored outside the repository;
- required GitHub Actions secret names confirmed present: `WYNIME_RELEASE_KEYSTORE_BASE64`, `WYNIME_RELEASE_KEY_ALIAS`, `WYNIME_RELEASE_KEY_PASSWORD` and `WYNIME_RELEASE_STORE_PASSWORD`;
- secret values were not read or recorded by the audit;
- no official signed CI artifact is claimed until the checked-in workflow is available on GitHub and a tagged run verifies the alias, APK metadata, alignment and signature.

The exact APK, AAB, debug APK, Windows executable and portable ZIP SHA-256
values are recorded together in the final-SHA verification transcript after
the final evidence commit. Native linker and ZIP output can vary between
clean builds, so hashes from an earlier candidate or an earlier build run
must not be copied into this status document. The transcript also records
the helper output that verifies the packaged notice and version metadata.

The local release-preparation script produced `wynime-1.0.1.apk` and
`wynime-1.0.1.zip` under the ignored `build/release` directory; both sidecars
match. The ZIP contains the complete Flutter Release directory plus
`README.md`, `version.txt` and `THIRD_PARTY_NOTICES.md`; the APK, AAB and ZIP
all contain the candidate notice asset. The APK remains intentionally
unsigned and is therefore an inspection artifact, not a publishable release.

The release APK's packaged `libmpv.so` entries were read back from the ZIP and matched against the corresponding Gradle transform outputs: `arm64-v8a` 12,369,680 bytes / `ADF83FDE58A9F6751CE6E83B9B187F651425D53EC0E20DA04DEB8DFB4AA775E1`, `armeabi-v7a` 11,746,532 bytes / `8AA2B23D16D941A8D685B8EB995B1CB31677ED3E28C55C776954EB1618AB1A2E`, and `x86_64` 15,816,336 bytes / `F030E9A1A4D4664E89D7160A3D4528CD5875EE023FF6262E16E95AAC28FFAEC5`. This verifies local packaging identity; the input JAR digest match is recorded below. It does not close source, build-flag or license provenance.

## Native provenance and license boundary

The locked pub packages and downloaded native archives were inspected. Windows libmpv input is `mpv-dev-x86_64-20230924-git-652a1dd.7z` with MD5 `A832EF24B3A6FF97CD2560B5B9D04CD8` and SHA-256 `DCE982222D7A23E4A1C6F0FB6CC39F6E899A6714624B95EA49CFF6558EE97572`; ANGLE input `ANGLE.7z` has MD5 `E866F13E8D552348058AFAAFE869B1ED` and SHA-256 `CC5911BB15D596FD5A2B362613AD35B7093B427117269A7359054A65746A5F9A`. The package build scripts verify MD5 and download from their declared GitHub release URLs.

These hashes establish the selected archive bytes. The candidate now also
records the embedded Windows runtime identity, configure flags, PE dependency
list and redistributed DLL hashes in `docs/THIRD_PARTY_PROVENANCE.md`, and
ships the explicit notice asset in both platforms. The historical Windows
FFmpeg configure record and complete transitive license closure are still not
independently reviewed, so native provenance/license review remains a release
blocker.

The rebuilt APK's Flutter `assets/flutter_assets/NOTICES.Z` contains generated
Flutter/Dart/plugin notices and sections for dependencies such as `media_kit`,
ANGLE and SQLite, but a keyword scan found no dedicated `libmpv` or `FFmpeg`
native section. The separately packaged
`assets/third_party/THIRD_PARTY_NOTICES.md` is now verified in the APK/AAB and
Windows ZIP; it does not substitute for independent legal review.

The checkout has no root project `LICENSE` or `NOTICE` file. The application's own distribution license cannot be inferred from dependency notices and must be explicitly selected and approved before publication.

### Official upstream cross-check

- The official [`libmpv-android-video-build` v1.1.7 release](https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7) exposes these SHA-256 digests for the four ABI JARs: `default-arm64-v8a.jar` `4363dfa5d3d415b91c1f16f6fb90c3fe59a77dfd3f9b824d2b24b492d6b09df9`, `default-armeabi-v7a.jar` `8ead114fc5a43348d89dc0eb8f41823e549b15115c29f73ee26973f973620995`, `default-x86.jar` `94c13cb6188b774710e5e487afff6e500c4af504df74b2494d7b12cf9be8a66a`, and `default-x86_64.jar` `90268cd15f0766e07fb8e427388c621161177c9eb343c544f327bd63232bb236`. Its [`v1.1.7` dependency manifest](https://github.com/media-kit/libmpv-android-video-build/blob/v1.1.7/buildscripts/include/depinfo.sh) records FFmpeg 6.0, mpv commit `78d43740f52db817d98bcf24fb30a76ab6fa13ff` and the supporting dependency versions; the default flavor uses `--disable-gpl`, `--disable-nonfree` FFmpeg and `gpl=false` for libmpv. The upstream [`LICENSE`](https://github.com/media-kit/libmpv-android-video-build/blob/v1.1.7/LICENSE) is evidence for that source build repository, not a substitute for a complete transitive redistribution notice set for the APK.
- The current build output contains the original v1.1.7 ABI JARs. Their local SHA-256 values directly match the official release digests: `default-arm64-v8a.jar` `4363dfa5d3d415b91c1f16f6fb90c3fe59a77dfd3f9b824d2b24b492d6b09df9`, `default-armeabi-v7a.jar` `8ead114fc5a43348d89dc0eb8f41823e549b15115c29f73ee26973f973620995`, `default-x86.jar` `94c13cb6188b774710e5e487afff6e500c4af504df74b2494d7b12cf9be8a66a`, and `default-x86_64.jar` `90268cd15f0766e07fb8e427388c621161177c9eb343c544f327bd63232bb236`. This closes the Android upstream archive identity check only; it does not close the transitive license notice or native redistribution review.
- The official [Windows 2023-09-24 release](https://github.com/media-kit/libmpv-win32-video-build/releases/tag/2023-09-24) identifies the selected mpv development archive and links it to mpv commit `652a1dd90711839acdccc08004056d25514ef2d`; its release asset metadata did not provide a SHA-256 digest. The candidate's direct runtime probe now proves `mpv v0.36.0-403-g652a1dd907`, FFmpeg `n6.0`, `-Dgpl=false`, `-Dlibmpv=true`, static preference and ANGLE configuration for the actual DLL. The historical FFmpeg configure recipe and complete transitive notice set are still not independently proven.
- The official [ANGLE v1.0.1 release](https://github.com/alexmercerind/flutter-windows-ANGLE-OpenGL-ES/releases/tag/v1.0.1) identifies the selected archive, but the audited archive/repository listing did not yield a complete license bundle for redistribution. The local archive hashes remain the authoritative bytes actually inspected in this checkout.

This cross-check narrows the provenance gap but does not close it: exact historical Windows build inputs/flags, complete linked-library license notices and a reproducible redistribution record are still required before release packaging.

## Runtime and document boundaries

- `where.exe ffmpeg` finds no executable; standalone MP4 remux and MKV
  fallback are future scope explicitly excluded from 1.0.1 and therefore are
  not a release gate for this candidate;
- Phase 6 playback remains `prototype_not_hardware_validated`; deterministic fake-backed Android/Windows platform tests do not prove hardware decoding/rendering;
- Android phone and tablet action evidence passed individually, while Phase 11 overall UI remains `BLOCKED_UI_ENVIRONMENT` because the current Computer Use surface exposes no native Windows app (`apps: []`); no Windows action-level result is claimed;
- The 2026-09-07 operation-specific AVD rerun revalidated phone/tablet search focus, text entry, local submission, navigation and diagnostics toggle state, with empty crash-match files; the emulator results do not substitute for supported physical Android or Windows hardware playback;
- the detailed plan DOCX was structurally validated after regeneration (69,164 bytes, 544 non-empty paragraphs, with Phase 12 Gate and ADR-024 present in ZIP/OXML); visual page rendering could not run because LibreOffice/`soffice` is unavailable;
- no Git tag, GitHub Release, installer publication or `main` push was
  performed; the candidate branch has been pushed only for exact-SHA CI and
  independent review. Local/remote `main` integration remains gated.

## Release decision

`RELEASE_BLOCKED` until all of the following are supplied and independently
verified: native provenance/build flags/linked-license closure and an
approved Wynime distribution license, official external Android signing
evidence, observable Windows action-level UI, and supported Android/Windows
hardware playback. Standalone FFmpeg/remux/MKV execution is explicitly not a
1.0.1 gate because it is excluded from this release boundary. The current
source and deterministic test gates are complete and must not be reworded as
those runtime or legal passes.
