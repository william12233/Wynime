# Wynime 1.0.4

Status: release-ready for the machine-verifiable release gates. The official
public release is produced only by the protected exact-SHA workflow after the
tag, `origin/main`, CI result, signing result, packaged assets and checksums
agree.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only; no
standalone setup.exe is published.

## Included boundary

- Existing Bangumi account and broker integration remains state-bound and
  server-secret-only.
- The production broker origin is configured through the GitHub Actions
  variable `WYNIME_BANGUMI_BROKER_ORIGIN`.
- The App's public Bangumi Client ID is aligned with the configured Worker;
  the phone and tablet OAuth-entry flows now reach the Bangumi login page.
- Android OAuth return uses the verified HTTPS App Link contract at
  `/oauth/callback` when the production Digital Asset Links relationship and
  Android OS association are both verified.
- Access and refresh credentials remain memory-only according to the Phase 9
  architecture; secrets, cookies, tokens and raw OAuth responses are not
  written to repository files, persistence or release evidence.
- Shared playback-session routing, source security boundaries, localization,
  native provenance locks and packaged license/source notices remain included.

## Validation boundary

The 1.0.4 candidate must rerun the analyzer, deterministic tests, broker
typecheck/tests, Android arm64 release checks, Windows x64 ZIP checks,
production `assetlinks.json` and Android App Link verification, and the
Bangumi OAuth account-flow evidence required by the release plan.

The current worktree has passed analyzer, deterministic Flutter tests (218),
broker typecheck and broker tests (9/9). A local arm64 Release APK build and
Windows x64 Release compile/install also pass; the local APK is intentionally
unsigned because protected CI owns production signing. After the host restart,
the fixed Android phone and tablet AVDs booted and launched the updated app;
both OAuth-entry flows reached the Bangumi login page. The user reported the
account completion and callback test as complete. Physical playback and
Windows native action-level UI evidence remain open and are disclosed below.

## External validation disclosure

- Physical Android playback: `HARDWARE_VALIDATION_PENDING`.
- Physical Windows playback: `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation:
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.
- Android phone/tablet Bangumi OAuth entry: `VERIFIED_ON_FIXED_AVDS`; the
  completed account callback test is user-reported external evidence.
- Windows debug launch and action-level interaction:
  `BLOCKED_UI_ENVIRONMENT` (third-party Debug-CRT link failure and native CUA
  surface unavailable; Release compile/liveness pass is recorded separately).
- Any manual or production OAuth state not directly evidenced remains
  unverified and is not represented as a pass.
