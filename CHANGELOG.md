# Changelog

All notable Wynime changes are documented here. A version remains
`Unreleased` until its release gate, signing, provenance and publication
evidence are complete.

## 1.0.7 - Unreleased

### Bangumi synchronization reliability

- Replaced terminal retry exhaustion with persistent `retryWaiting` and
  explicit `blocked` states; legacy v1.0.6 `failed` rows migrate safely.
- Added capped jittered backoff, manual force retry, HTTP status diagnostics,
  single-flight synchronization and operation coalescing.
- Added pre-write reconciliation, post-write remote verification, lost-response
  recovery, independent-field merging and explicit conflict handling.
- Explicit collection refresh now imports external Bangumi changes without
  allowing blocked or legacy failed operations to override remote state.

### Release boundary

- Upgraded the Bangumi operation schema to v4 and added deterministic coverage
  for migration, retry, conflict, remote pull and blocked-operation behavior.

## 1.0.6 - Unreleased

### Android Bangumi OAuth App Link

- Corrected the production Android App Link certificate fingerprint from the
  published v1.0.5 APK signer instead of truncating or guessing the malformed
  value.
- Added strict fingerprint validation and release gates for APK signer,
  package identity and production `assetlinks.json` association.
- Deployed the existing Bangumi broker with direct health, association and
  callback verification.

## 1.0.5 - Unreleased

### Bangumi OAuth callback

- Separated Bangumi's provider callback at `/oauth/callback` from the final
  Android App Link return path at `/oauth/app-callback`.
- Prevented the provider denial/error redirect from re-entering the provider
  callback and producing `oauth_state_invalid`.
- Kept the production Android package and signing fingerprint aligned with
  the Worker `assetlinks.json` configuration.

## 1.0.3 - Release-ready

### Bangumi broker and Android packaging

- Removed the nonexistent hardcoded Bangumi broker host and replaced it with
  one build-time HTTPS origin contract.
- Builds without a valid broker origin fail closed and show Bangumi as
  unavailable instead of opening a dead web page.
- The Android release artifact is now an arm64-v8a APK only; no universal APK
  is published.

### Release status

The machine-verifiable release gates and native provenance checks are closed
for the immutable candidate. Live Bangumi account/OAuth validation,
production Worker deployment and action-level UI validation remain separately
disclosed external boundaries. See `docs/PHASE12_STATUS.md` and
`docs/release-notes-1.0.3.md`.

## 1.0.2 - Release-ready

### Windows distribution and updates

- Changed future Windows publication to a portable ZIP plus SHA-256 sidecar;
  no standalone setup.exe is published.
- Added the portable `wynime_update.exe` helper and Settings manual update
  flow with bounded ZIP validation, same-disk replacement, health marking and
  rollback/manual-update-required handling.

### Bangumi Phase 9 source implementation

- Added memory-only OAuth session contracts, the Cloudflare OAuth broker,
  official `api.bgm.tv` client adapter and bounded error/pagination handling.
- Added account-scoped Drift v3 collections, episodes, calendar cache,
  local-first queued synchronization, retry and visible conflict resolution.
- Connected Bangumi state to Settings, Home, Library and subject detail flows;
  live-account and production Worker validation remain separately disclosed.

### Release status

The machine-verifiable release gates and native provenance checks are closed
for the immutable candidate. Live Bangumi account/OAuth validation and
action-level UI validation remain disclosed external boundaries. See
`docs/PHASE12_STATUS.md` and `docs/release-notes-1.0.2.md`.

## 1.0.1 - Release-ready

### Release candidate scope

- Consolidated the Phase 6 playback engine router, Media3 boundary and
  media-kit/libmpv boundary behind the shared playback session contract.
- Included the responsive product shell, localized product pages and fixed-size
  UI golden coverage for the Android and Windows targets.
- Kept downloads, remuxing, Bangumi synchronization and other higher-phase
  services outside this release candidate.

### Release status

The machine-verifiable Phase 12 release gates are closed. Physical hardware
playback and native Windows Computer Use validation remain explicitly
disclosed non-blocking QA. The immutable GitHub Release workflow is the only
publication path. See `docs/PHASE12_STATUS.md` and
`docs/release-notes-1.0.1.md`.

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
