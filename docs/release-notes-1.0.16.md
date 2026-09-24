# Wynime 1.0.16

Wynime `1.0.16+17` is the next protected exact-SHA release after `v1.0.15`.

## Distribution assets

- Android arm64-v8a APK: `wynime-1.0.16-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.16.zip`.
- Each asset is published with its `.sha256` sidecar.
- The release contains no universal Android APK and no installer outside the
  Windows ZIP contract.

## Episode mapping and playback continuity

- Added typed episode mappings that retain the raw source label, source episode
  kind, numbering mode, inferred offset and evidence records.
- Ordered Bangumi/source context can infer cumulative season numbering without
  treating a coincidental source ordinal as an exact match.
- Persisted mapping metadata through the database v8 migration and restored it
  across controller reconstruction with source identity isolation.
- Added a bounded retry for oversized progressive media responses using an
  initial Range request; HLS playlist handling remains unchanged.
- Android Media3 emits bounded playing-position updates, and the live harness
  records position advancement while preserving the single playback lifecycle.

## xifan source package

- Published xifan package `1.2.2` so the updated API host and hydrated search
  selector are a real package update from `1.2.1`.
- Updated the exact registry SHA-256, fixture selector and bounded Android
  document-capture window.
- Source package permissions, domain allowlists, redirect limits, resource
  budgets, consent and secret-safe diagnostics remain enforced.

## Validation boundary

- `flutter analyze --fatal-infos --no-pub`: passed with no issues.
- `flutter test --no-pub`: 818 tests passed.
- Dart format and `git diff --check`: passed.
- API-36 Android phone and tablet emulator UI: Sources page, xifan `1.2.2`
  update, bounded permission review, consent, enabled state, and post-restart
  enabled persistence passed; log summaries contained no app crash, ANR, or
  Flutter error.
- Protected exact-SHA CI, external signing, native provenance, production App
  Link association and the four-asset release workflow remain required release
  authorities.
- `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit: the Windows Debug
  executable built and launched, but the current Computer Use runtime exposed
  no native app/window inventory for action-level UI evidence. No
  authenticated Bangumi, sustained hardware playback, CAPTCHA, DRM or
  access-control-bypass claim is made.
