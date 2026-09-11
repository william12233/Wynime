# Wynime 1.0.9

Status: release candidate. The protected exact-SHA workflow must rebuild,
sign, verify and publish the Android arm64-v8a APK and Windows x64 ZIP.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only; no
standalone setup.exe is published.

## Bangumi library and subject details

- Reworked the collection status navigation into responsive tabs with live
  counts, poster cards, watch progress and explicit subject/episode actions.
- Reworked subject details into a responsive media-first layout with
  progressive disclosure for summary, metadata, tags, characters, staff and
  related subjects.
- Preserved remote metadata while overlaying local-first collection status,
  cached artwork and watch progress during reconciliation.

## Update experience

- Started the automatic update check after application services initialize.
- Added real download progress for update installation and explicit verifying
  and handoff states.
- Kept failed and manual-required installs truthful instead of showing a fake
  installed result.

## Validation boundary

- `dart format --output=none --set-exit-if-changed lib test` passes.
- `flutter analyze --suppress-analytics` passes with no issues.
- The full deterministic Flutter test suite passes (261 tests).
- Fixed phone/tablet emulator Library interactions pass, including the
  Library status tabs and On hold flow.
- Windows debug packaging and `flutter run -d windows --debug` pass; the
  runner bundle includes the required media-kit video plugin DLLs.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`; launch evidence does not replace interaction evidence.
- A live Bangumi account, OAuth collection sync and populated production
  detail flow are not inferred from the unconfigured local debug build.

