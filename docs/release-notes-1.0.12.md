# Wynime 1.0.12

Status: release candidate for the protected exact-SHA publication workflow.
This release adds the Wynime Source Package System and the first live
declarative xifan package. It does not claim hardware playback or CAPTCHA
completion that was not exercised.

## Platforms and distribution

- Android arm64-v8a APK: `wynime-1.0.12-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.12.zip`.
- Each public artifact has its matching `.sha256` sidecar.
- Windows publication is ZIP-only. There is no standalone `setup.exe`; the
  ZIP contains `wynime_update.exe` for same-disk staged update, verification,
  backup, replacement, health-marker checking and rollback/manual-update
  fallback.
- No universal APK or release AAB is published.

## Source Package System

- Added strict schema-v3 source-package decoding and canonical encoding for
  bounded cache policy, public capability declarations and typed live
  operations.
- Added the live HTTP package runtime, operation-plan factory, subject-details
  coordinator and installed subject pipeline. Metadata and episode links are
  evaluated from one admitted bounded response, with exact source/line/subject
  /episode identity preserved.
- Added memory-only source cache controls with per-stage TTLs and bounded
  source health aggregation. Failed, disabled, consent-required and
  challenge outcomes remain typed and secret-safe.
- Added the unsigned xifan package with HTTPS allowlists for the detail,
  player and media hosts. Its detail, episode and iframe playback declarations
  are supported; search is explicitly `challengeRequired`.
- No source-provided Dart, JavaScript, WASM or native executable is executed.

## Validation evidence

- Full deterministic Flutter suite: 763 tests passed.
- `flutter analyze --no-pub` reports no issues; formatting and
  `git diff --check` pass.
- Live browser verification reached the xifan detail and episode pages,
  observed 13 episode links and three source lines, and saw the player iframe
  load with the episode title and `00:00 / 23:40` duration.
- The xifan search endpoint presented a verification challenge with an input,
  challenge image and submit control. No CAPTCHA was solved or bypassed.
- Protected CI remains the authority for release signing, APK metadata and
  App Link verification, Windows x64 packaging, four-asset checksums and
  immutable GitHub publication.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` in the current audit environment.

## Safety and compatibility boundary

- Source packages remain unsigned-capable but require lifecycle consent,
  declared HTTPS domains, network permission and bounded resource budgets.
- Telemetry remains disabled by default. Cookies, tokens, headers, response
  bodies and complete upstream media URLs are not persisted in release
  evidence or package metadata.
- Magnet, BitTorrent, seeding, DRM bypass, paywall bypass and access-control
  circumvention remain excluded.
