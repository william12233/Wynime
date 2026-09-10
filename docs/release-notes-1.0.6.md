# Wynime 1.0.6

Status: release candidate. The protected exact-SHA workflow must rebuild,
sign, verify and publish the Android arm64-v8a APK and Windows x64 ZIP.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only; no
standalone setup.exe is published.

## Android Bangumi OAuth App Link

- Repaired the production association using the exact SHA-256 signer from the
  published v1.0.5 APK; no signing key rotation or fingerprint guessing was
  used.
- The Worker now accepts only an exact 64-hex or 32-byte colon fingerprint,
  emits canonical uppercase association metadata and fails closed on malformed
  configured values.
- The provider callback remains `/oauth/callback`; the final Android App Link
  return path remains `/oauth/app-callback`.
- The existing production Worker was deployed and verified with direct
  health, association and callback probes. No real OAuth token or account was
  used.

## Validation boundary

- Flutter analyzer, deterministic tests, broker typecheck and broker tests
  pass on the release worktree.
- The release verifier was executed in Python 3.11 against the published
  APK/build-tools path and its negative cases; protected CI remains the final
  signer, packaging and Windows ZIP authority.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`.
- Physical Android App Link dispatch and real-account OAuth remain external
  validation boundaries and are not claimed as passed.
