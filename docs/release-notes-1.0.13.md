# Wynime 1.0.13

Status: release candidate for the protected exact-SHA publication workflow.
This release completes the source-to-playback integration boundary while
preserving truthful runtime and hardware-validation disclosures.

## Platforms and distribution

- Android arm64-v8a APK: `wynime-1.0.13-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.13.zip`.
- Each public artifact has its matching `.sha256` sidecar.
- Windows publication is ZIP-only. There is no standalone `setup.exe`; the
  ZIP contains `wynime_update.exe` for same-disk staged update, verification,
  backup, replacement, health-marker checking and rollback/manual-update
  fallback.
- No universal APK or release AAB is published.

## Source-to-playback integration

- Added the fixed production source registry authority and schema-v1 index.
- Added serialized package removal with transactional cleanup of source
  subject and episode mappings.
- Added package provenance revisions and schema-v7 persistence for separate
  Bangumi subject, source subject, Bangumi episode and source episode
  identities.
- Added exact normalized subject matching with explicit ambiguity and no-result
  states, plus conservative main-story episode ordinal correlation.
- Connected Bangumi detail-page episode actions to the existing single
  PlaybackSession／PlaybackCoordinator and PlayerPage path.
- Android Media3 PlayerView binds the existing MainActivity ExoPlayer; Windows
  retains the existing mpv surface.

## xifan 1.1.0 source package

- Added a formal declarative search operation using the public Next search
  route, resolving the previous first-time subject-mapping blocker.
- Subject details, episode links and playable video-source extraction use
  declared bounded programs and HTTPS allowlists only.
- The package remains unsigned-capable but requires lifecycle consent, declared
  permissions, bounded resource budgets and strict artifact integrity checks.
- No CAPTCHA solving, DRM bypass, paywall bypass, access-control circumvention,
  source-provided executable code or dynamic adapter is included.

## Validation evidence

- Full deterministic Flutter suite: 772 tests passed.
- Targeted source identity, live-operation, search-pipeline, subject-pipeline,
  decoder and xifan fixture suite: 36 tests passed.
- `flutter analyze --fatal-infos --no-pub` reports no issues; formatting and
  `git diff --check` pass.
- Android debug APK and Windows debug build pass locally. Protected CI remains
  authoritative for release signing, APK metadata, App Link verification,
  Windows x64 packaging, four-asset checksums and immutable GitHub publication.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## Safety and compatibility boundary

- Source packages remain declarative and bounded. Cookies, tokens, headers,
  response bodies and complete upstream media URLs are not persisted in release
  evidence or package metadata.
- Telemetry remains disabled by default.
- Magnet, BitTorrent, seeding, DRM bypass, paywall bypass and access-control
  circumvention remain excluded.
