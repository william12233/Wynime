# Wynime 1.0.5

Status: release candidate. The protected exact-SHA workflow must rebuild,
sign, verify and publish the Android arm64-v8a APK and Windows x64 ZIP.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only; no
standalone setup.exe is published.

## Included boundary

- Bangumi's provider callback remains `https://<worker-host>/oauth/callback`.
- The final Android verified App Link return path is
  `https://<worker-host>/oauth/app-callback`.
- The production Worker now keeps those two paths separate, so an OAuth
  denial or exchange error cannot loop back into provider-state validation.
- The Android manifest and native callback handler accept only the verified
  `/oauth/app-callback` path.
- Access and refresh credentials remain memory-only; secrets, cookies, tokens
  and raw OAuth responses are not written to repository files or release
  evidence.

## Validation boundary

- Flutter analyzer and deterministic tests must pass on the final candidate.
- The broker typecheck and tests must pass on the final candidate.
- The production Worker health, App Link association and denial callback are
  checked against the deployed origin.
- Protected CI owns release signing, APK verification and Windows ZIP
  packaging.
- Physical Android playback and physical Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`.
