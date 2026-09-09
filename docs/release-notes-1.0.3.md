# Wynime 1.0.3

Status: release-ready for the machine-verifiable release gates. Live Bangumi
account validation, production Worker deployment and action-level UI evidence
remain explicitly disclosed external boundaries.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only for this
release; no standalone setup.exe is published.

## Included boundary

- Bangumi broker configuration uses one build-time HTTPS origin and fails
  closed when the origin is absent or invalid.
- Android App Link and callback validation derive from the same broker host as
  the Dart client.
- Responsive product pages show Bangumi as unavailable without opening a
  browser when the broker is not configured.
- Shared playback-session routing, source security boundaries, localization,
  native provenance locks and packaged license/source notices remain included.

## Validation

- Dart analyzer and the full deterministic Flutter test suite passed.
- Android arm64-v8a debug/release build contract passed; malformed broker
  origins are rejected by Gradle and no universal APK is produced.
- Bangumi broker tests, TypeScript typecheck and Wrangler dry-run passed; no
  production Worker deployment was performed.
- The Windows ZIP contains the portable runtime and required license/source
  documents, with a matching SHA-256 sidecar and no setup.exe.

## External validation disclosure

- Physical Android playback: `HARDWARE_VALIDATION_PENDING`.
- Physical Windows playback: `HARDWARE_VALIDATION_PENDING`.
- New Bangumi/update action-level flows: `BLOCKED_UI_ENVIRONMENT` because the
  audit host does not provide the required native application surfaces.
- Native Windows Computer Use action-level validation:
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.
- No live Bangumi OAuth login or real-account synchronization was attempted.

These items are not represented as passes, and the package does not claim a
production Bangumi account or Worker deployment.
