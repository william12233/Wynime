# Wynime 1.0.8

Status: release candidate. The protected exact-SHA workflow must rebuild,
sign, verify and publish the Android arm64-v8a APK and Windows x64 ZIP.

## Platforms

- Android arm64-v8a APK
- Windows x64 ZIP (portable)

No universal APK or release AAB is published. Windows is ZIP-only; no
standalone setup.exe is published.

## Bangumi collection and subject details

- Rebuilt the collection page with Bangumi's five statuses: dropped, wish,
  watching, on hold and completed, including counts derived from loaded state.
- Replaced the inline detail card with an independent responsive subject detail
  route with Back and Home navigation.
- Added typed Bangumi subject rating, rank, public collection statistics,
  infobox, metadata tags, tags, episodes, characters with supplied actors,
  staff credits and related subjects.
- Kept selecting an episode separate from explicitly marking it watched; no
  playback, source selection, comments, reviews or discussion UI was added.
- Added cached-first rendering, local-first collection/watch state, partial and
  fatal section handling, retry actions and stale-request generation guards.
- Added an additive Drift v4 to v5 migration and detail-cache round trips.

## Validation boundary

- `flutter analyze` passes with no issues.
- The full deterministic Flutter test suite passes (245 tests).
- Windows x64 Release build and Android Debug phone/tablet launches were
  exercised in the audit environment.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`; launch screenshots do not replace interaction evidence.
- A live Bangumi account, OAuth collection sync and populated production detail
  flow are not inferred from fixtures or the unconfigured local build.
