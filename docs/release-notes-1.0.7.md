# Wynime 1.0.7

Status: release candidate. The protected exact-SHA workflow must rebuild,
sign, verify and publish the Android arm64-v8a APK and Windows x64 ZIP.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only; no
standalone setup.exe is published.

## Bangumi synchronization reliability

- Replaced retry exhaustion that permanently poisoned local operations with
  persistent `retryWaiting` and explicit `blocked` states.
- Added capped jittered exponential backoff, manual force retry, stable HTTP
  status diagnostics and recoverable authentication failures.
- Added pre-write remote reconciliation so already-satisfied changes do not
  issue duplicate writes, while independent remote fields merge safely.
- Added bounded post-write verification at 250ms, 500ms, 1s and 2s to recover
  from lost mutation responses and surface incompatible changes as conflicts.
- Explicit refresh imports external Bangumi collection and watched-episode
  changes; blocked and legacy failed operations never remain local-first.
- Upgraded the local operation schema to v4 and migrated valid legacy failed
  rows into retryable work while structurally invalid rows become blocked.

## Validation boundary

- Flutter analyzer and the full deterministic test suite (238 tests) pass on
  the release worktree.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`.
- No live Bangumi OAuth token, account synchronization or production upstream
  success is inferred from deterministic fixtures.
