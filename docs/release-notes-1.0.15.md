# Wynime 1.0.15

Status: release candidate for the protected exact-SHA publication workflow.
The candidate is based on the approved `main` commit and publishes only after
the signing, packaging, provenance, checksum and immutable-reference gates
complete.

## Platforms and distribution

- Android arm64-v8a APK: `wynime-1.0.15-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.15.zip`.
- Each public artifact has its matching `.sha256` sidecar.
- Windows publication is ZIP-only and includes `wynime_update.exe`; no
  standalone setup installer or universal Android APK is published.

## Source playback continuation

- Kept rendered-document and WebView acquisition transient, non-interactive
  and offstage; visible playback remains Wynime's resolving/error state or the
  native Media3/libmpv surface.
- Added typed source-line selection using the exact
  `SourceEpisodeIdentity`, old-session shutdown and bounded best-effort
  position restoration.
- Updated the declarative xifan package to `1.2.1`, including the exact
  `bjdownload.pan.wo.cn:30443` HTTPS authority, bounded document/media-request
  capture and the existing single `PlaybackSession` lifecycle.
- Added localized HTTP 502 playback messaging with retry and exact line-switch
  actions. Source-site chrome is not used as the playback error surface.
- Added a debug-only staged registry adapter that reuses the verified index,
  artifact, package identity and SHA-256 boundaries. Release composition stays
  on the fixed GitHub registry.

## Validation boundary

- Current deterministic suite: 815 Flutter tests passed; analyzer, formatting
  and `git diff --check` passed.
- Protected Phase 0 CI remains authoritative for current-head generated-code,
  analyzer, golden, Android debug and Windows debug gates.
- Protected release CI remains authoritative for the externally signed APK,
  Windows x64 ZIP, native provenance, App Link association, four-asset set,
  checksums and GitHub publication.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.
- Authenticated Bangumi access and sustained hardware playback are not claimed.

## Safety and compatibility boundary

- Source packages remain declarative, allowlisted and resource-bounded.
- Cookies, tokens, credentials, response bodies and complete upstream media
  URLs are not persisted in release evidence or package metadata.
- Telemetry remains disabled by default.
- Magnet, BitTorrent, seeding, DRM bypass, paywall bypass, CAPTCHA solving and
  access-control circumvention remain excluded.
