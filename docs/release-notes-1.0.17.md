# Wynime 1.0.17

Wynime `1.0.17+18` is the next protected exact-SHA release after `v1.0.16`.

## Distribution assets

- Android arm64-v8a APK: `wynime-1.0.17-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.17.zip`.
- Each asset is published with its `.sha256` sidecar.
- The release contains no universal Android APK and no installer outside the
  Windows ZIP contract.

## xifan source package refresh

- Published xifan package `1.2.3` with registry SHA-256
  `842E8E7C5193F01CD1F02D312D225DBDAA5A75C046DD85DC4B8C38BB6ACF6DC9`.
- Declared the exact HTTPS media origin `play.xfvod.pro:8088`.
- Exact-port policy, host equality, subdomain rejection, consent boundaries,
  redirect limits, resource budgets and secret-safe diagnostics remain
  enforced.
- Each live episode request acquires a fresh episode-specific media candidate;
  a prior episode's candidate is not reused.

## Validation boundary

- `flutter analyze --fatal-infos --no-pub`: passed with no issues.
- `flutter test --no-pub`: 820 tests passed.
- Dart format, `dart run build_runner build` and `git diff --check`: passed
  with no generated or formatting changes.
- The release candidate must pass protected exact-SHA CI, external signing,
  native provenance, production App Link association and the four-asset
  publication workflow.
- `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit: no authenticated
  Bangumi, sustained hardware playback, CAPTCHA, DRM or access-control-bypass
  claim is made.
