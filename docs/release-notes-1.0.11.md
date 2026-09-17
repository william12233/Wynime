# Wynime 1.0.11

Status: release candidate for the protected exact-SHA publication workflow.
This release repairs the Bangumi synchronization retry and reconciliation
path. It does not claim a live provider mutation or hardware result that was
not exercised.

## Platforms and distribution

- Android arm64-v8a APK: `wynime-1.0.11-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.11.zip`.
- Each public artifact has its matching `.sha256` sidecar.
- Windows publication is ZIP-only. There is no standalone `setup.exe`; the
  ZIP contains `wynime_update.exe` for same-disk staged update, verification,
  backup, replacement, health-marker checking and rollback/manual-update
  fallback.
- No universal APK or release AAB is published.

## Bangumi synchronization repair

- Bangumi collection and watched-episode mutation requests now explicitly use
  `application/json`, matching the official v0 API contract and preventing
  an unsupported request media type from being retried unchanged.
- HTTP 415 responses now produce stable, secret-safe diagnostics without
  persisting tokens, account identifiers or raw upstream response bodies.
- Recoverable HTTP 415 rows can be retried. Legacy blocked rows and active
  duplicates are coalesced per account and target, keeping the newest intent
  and deleting only exact queue rows inside an account/write-authorized
  transaction.
- Every pending mutation reads the latest remote target first. Already-applied
  changes complete without another write; unrelated remote changes are
  merged; same-target changes stop as conflicts rather than overwriting the
  remote state.
- The settings and Bangumi pages present localized explanations instead of
  exposing the internal `http_415` code directly.

## Validation evidence

- Full deterministic Flutter suite: 750/750 passed, 0 failed and 0 skipped.
- Bangumi-focused synchronization, transport, local-store and presentation
  tests: 49 passed.
- `flutter analyze`: no issues; formatting and `git diff --check` pass.
- Android debug APK build passed and the fresh APK installed/launched on the
  fixed Android 16/API 36 phone and tablet emulators.
- The local Windows build environment currently returns the Visual Studio
  FileTracker `E_ACCESSDENIED` failure; the protected release workflow must
  perform the Windows x64 Release build and packaging on its runner.
- Protected CI remains responsible for release signing, APK/App Link
  verification, Windows packaging, checksums and the exact four public assets.
- Independent browser-based GPT-5.6 Sol review returned
  `SOL_REVIEW_PASS` for operation
  `wynime-bangumi-sync-20260917-01a0`.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` in the current audit environment.
- No live authenticated Bangumi mutation is inferred from deterministic tests,
  emulator launch evidence or build output.

## Safety and compatibility boundary

- Bangumi local intent is retained until it is reconciled, completed or
  explicitly marked as a conflict; a stale local queue row cannot silently
  overwrite a newer remote same-target change.
- Telemetry remains disabled by default. Raw tokens, cookies, upstream
  response bodies and complete media URLs are not written to logs or release
  evidence.
- Magnet, BitTorrent, seeding, DRM bypass, paywall bypass and access-control
  circumvention remain excluded.
