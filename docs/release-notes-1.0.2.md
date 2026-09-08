# Wynime 1.0.2

Status: release-ready for the machine-verifiable release gates. Live Bangumi
account validation, production Worker deployment and action-level UI evidence
remain explicitly disclosed external boundaries.

## Platforms

- Android multi-ABI APK
- Windows x64 ZIP (portable)

Windows is ZIP-only for this release; no standalone setup.exe is published.
The AAB remains an internal or Play Store handoff artifact and is not part of
the GitHub Release asset set.

## Included boundary

- Shared `PlaybackSession` routing with Android Media3 preference and the
  media-kit/libmpv fallback boundary.
- Responsive Home, Search, Library, Downloads, Sources and Settings pages,
  with the Windows ZIP updater helper and manual update flow.
- Bangumi Phase 9 source implementation: OAuth broker contracts, official
  `api.bgm.tv` client adapter, memory-only authentication, account-scoped
  Drift v3 state, calendar cache, local-first queue, retry and conflict UI.
- English, Traditional Chinese, Simplified Chinese and Japanese localization
  resources.
- Native provenance lock, exact archive/runtime/package hashes, applicable
  LGPLv3/GPLv3 texts, corresponding-source offer and third-party notices.

## Validation

- `dart format`, `flutter analyze --fatal-infos` and the full 212-test Flutter
  suite passed.
- Android debug/release and Windows debug/release builds passed.
- Windows native provenance verification passed for the pinned libmpv runtime.
- Bangumi broker tests, TypeScript typecheck and Wrangler dry-run passed; no
  production Worker deployment was performed.
- The Windows ZIP contains `wynime.exe`, `wynime_update.exe`, `version.txt`,
  Flutter runtime files and license/source documents, with a matching
  SHA-256 sidecar and no setup.exe.

## External validation disclosure

- Physical Android playback: `HARDWARE_VALIDATION_PENDING`.
- Physical Windows playback: `HARDWARE_VALIDATION_PENDING`.
- New Bangumi/update action-level flows: `BLOCKED_UI_ENVIRONMENT` because the
  audit host exposes no native application surface (`apps=[]`).
- Native Windows Computer Use action-level validation:
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` (`apps=[]`).
- No live Bangumi OAuth login or real-account synchronization was attempted.

These items are not represented as passes, and the package does not claim a
production Bangumi account or Worker deployment.
