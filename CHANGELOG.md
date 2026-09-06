# Changelog

All notable Wynime changes are documented here. A version remains
`Unreleased` until its release gate, signing, provenance and runtime evidence
are complete.

## 1.0.1 - Unreleased

### Release candidate scope

- Consolidated the Phase 6 playback engine router, Media3 boundary and
  media-kit/libmpv boundary behind the shared playback session contract.
- Included the responsive product shell, localized product pages and fixed-size
  UI golden coverage for the Android and Windows targets.
- Kept downloads, remuxing, Bangumi synchronization and other higher-phase
  services outside this release candidate.

### Release status

This candidate remains unpublished until the Phase 12 release blockers are
closed and the exact signed CI artifacts are independently verified. See
`docs/PHASE12_STATUS.md` and `docs/release-notes-1.0.1.md`.

## 1.0.0 - Unreleased

### Product foundation

- Added the Android and Windows responsive product shell for Home, Search,
  Library, Downloads, Sources and Settings.
- Added truthful unavailable, empty and review-required states instead of
  presenting unconnected source, Bangumi or download data as successful data.
- Added four generated locales: Traditional Chinese, Simplified Chinese,
  Japanese and English.

### Sources and playback

- Added bounded declarative source-package validation, domain and permission
  policy, resource budgets and fixture-backed proposal evaluation.
- Added the shared `PlaybackSession` contract, Android Media3 boundary and
  Windows/libmpv media-kit boundary with one application-level engine router.
- Added generation fencing, exact track authority, timeline identity checks,
  bounded fallback and secret-safe playback diagnostics.

### Downloads and local artifacts

- Added bounded download-job, HLS recovery and AES-128 domain contracts.
- Added root-confined artifact creation, atomic promotion, manifest-authorized
  deletion and report-only orphan scanning.
- Added the MP4 remux/MKV fallback boundary without enabling unsafe shell or
  outside-root file access.

### Bangumi and validation

- Added typed Bangumi models, local mapping and queued synchronization
  contracts with deterministic fixtures.
- Added fixed-size Golden coverage and current-head analyzer, test and Android
  / Windows build validation.

### Release status

This version is not yet publishable. See `docs/PHASE12_STATUS.md` for the
current release blockers and `docs/release-notes-1.0.0.md` for the candidate
release boundary.
