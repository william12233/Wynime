# Changelog

All notable Wynime changes are documented here. A version remains
`Unreleased` until its release gate, signing, provenance and publication
evidence are complete.

## 1.0.17 - Release-ready

### xifan source package refresh

- Published xifan source package `1.2.3` with the exact HTTPS
  `play.xfvod.pro:8088` media origin and matching registry SHA-256.
- Kept non-standard port admission exact, consent-bound and limited to the
  declared host; unlisted hosts and adjacent ports remain rejected.
- Ensured each live episode request acquires a fresh episode-specific media
  candidate instead of reusing a previous episode's result.

### Validation boundary

- Release validation remains tied to the exact candidate SHA, protected CI,
  external signing, native provenance, App Link association and the formal
  four-asset publication workflow.
- `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit unless first-party
  evidence closes them. No authenticated Bangumi, CAPTCHA, DRM or
  access-control-bypass claim is made.

## 1.0.16 - Release-ready

### Episode mapping, source compatibility and playback continuity

- Added typed episode mappings that preserve the raw source label, episode
  kind, season-relative or cumulative numbering, inferred offsets and mapping
  evidence instead of guessing from one ordinal alone.
- Added ordered-context correlation for cumulative source seasons, durable
  mapping persistence through the database v8 migration, and exact mapping
  restoration across controller reconstruction.
- Published xifan source package `1.2.2` with the current API host and hydrated
  result selector, and extended bounded document-capture time for slower
  Android WebViews.
- Added a bounded progressive-media retry that re-requests an oversized
  response with an initial Range while preserving the existing proxy and
  capability boundaries.
- Added Android playing-position events and updated the live harness to record
  position advancement without introducing a second playback lifecycle.

### Validation boundary

- Release validation is tied to the exact candidate SHA, protected CI,
  external signing, native provenance, App Link association and the formal
  four-asset publication workflow.
- `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit unless first-party
  evidence closes them. No authenticated Bangumi, CAPTCHA, DRM or
  access-control-bypass claim is made.

## 1.0.15 - Release-ready

### Source playback UX and compatibility continuation

- Kept rendered-document and WebView acquisition transient and offstage; normal
  playback now shows Wynime resolving/error states or the native player
  surface instead of source-site chrome.
- Added typed source-line selection with exact `SourceEpisodeIdentity`
  switching, old-session shutdown and bounded best-effort position restore.
- Preserved the xifan 1.2.1 declarative compatibility path, exact
  `bjdownload.pan.wo.cn:30443` admission, bounded `GET`/`Range` capture and
  the single `PlaybackSession` lifecycle.
- Added a localized HTTP 502 playback state with an explicit Wynime message,
  retry action and exact multi-line switch action; source-site chrome remains
  absent from the failure surface.

### Validation boundary

- Full deterministic Flutter suite: 815 tests passed; analyzer and
  `git diff --check` pass.
- Debug-only staged registry injection uses the same exact index/artifact
  loader and verifier as the fixed release registry. Android API-36 phone and
  tablet normal Sources UI both completed v1.1.0 → v1.2.1 update, fresh
  re-consent and restart persistence; live source search returned three
  xifan results on each device.
- The public Bangumi OAuth page was reached, but no account-holder consent was
  supplied. Authenticated Library/subject/episode/player evidence therefore
  remains pending. Standard non-elevated Windows builds reproduce the
  VS/FileTracker `E_ACCESSDENIED` environment failure; controlled elevated
  debug builds pass, while Windows UI remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`. No release action was performed.

## 1.0.12 - Release-ready

### Source Package System

- Added schema-v3 source packages with strict declarative live-operation
  bindings for subject details, episode links and playable sources.
- Added bounded memory-only cache policy, source health aggregation and
  secret-safe diagnostics without persisting cookies, tokens or full media
  URLs.
- Added the unsigned, allowlisted 稀飯動漫 (`xifan`) package with live detail,
  episode and iframe-playback declarations. Search remains explicitly
  `challengeRequired` when the provider presents an interactive challenge.
- Connected installed source packages through the subject-details pipeline so
  one bounded response produces normalized subject metadata and exact episode
  identities without introducing a provider-specific adapter.

### Validation boundary

- Full deterministic Flutter suite: 763 tests passed; analyzer, formatting
  and `git diff --check` pass on the release candidate.
- Live browser verification reached the xifan detail and episode pages,
  observed 13 episode links and three source lines, and observed the player
  iframe loading with a 23:40 duration.
- Physical Android and Windows playback remain `HARDWARE_VALIDATION_PENDING`;
  native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## 1.0.11 - Release-ready

### Bangumi synchronization reliability

- Fixed Bangumi collection and watched-episode mutations to send an explicit
  JSON content type, with stable secret-safe diagnostics for HTTP 415 and
  related transport failures.
- Made recoverable HTTP 415 queue entries retryable and coalesced stale
  blocked or active duplicates so a newer local intent cannot be overwritten
  by an older retry.
- Reconciled every mutation against the latest remote state before writing,
  preserving unrelated remote fields and surfacing same-target conflicts
  instead of silently overwriting them.
- Added localized Traditional-Chinese and translated sync error explanations
  while retaining the local intent until reconciliation completes.

### Validation boundary

- Full deterministic Flutter tests, focused Bangumi tests, analyzer and
  formatting checks pass on the release candidate worktree.
- Android phone/tablet emulator launch evidence passed; physical Android and
  Windows playback remain `HARDWARE_VALIDATION_PENDING`.
- Native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` in the current audit environment.

## 1.0.10 - Release-ready

### Animeko → Wynime 57-task convergence

- Completed TASK-001 through TASK-057 across Bangumi synchronization,
  watch-progress/resume behavior, bounded declarative source packages,
  deterministic fixture and live-source pipelines, package registry/lifecycle,
  source capture, playback handoff and Search presentation.
- Added detailed per-task scope, architecture guardrails, validation evidence
  and next-chat continuation rules in
  `docs/release-notes-1.0.10.md`.
- Preserved the one-website/one-package model, shared source executor,
  authoritative playback/session boundaries and secret-safe diagnostics.

### Distribution and validation boundary

- Android arm64-v8a APK and Windows x64 portable ZIP are the only public
  platform artifacts; each has a SHA-256 sidecar and Windows has no standalone
  `setup.exe`.
- Full deterministic Flutter tests pass 744/744; analyzer, formatting and
  diff checks are clean.
- Physical Android/Windows playback remains
  `HARDWARE_VALIDATION_PENDING`; native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## 1.0.9 - Release-ready

### Bangumi library and subject details

- Reworked the collection status navigation into responsive tabs with counts,
  poster cards, watch progress and explicit subject/episode actions.
- Reworked the subject detail page into a responsive media-first layout with
  progressive disclosure for summary, metadata, tags, characters, staff and
  related subjects.
- Preserved remote metadata while overlaying local-first collection status,
  cached artwork and watch progress during reconciliation.

### Update experience

- Started the automatic update check after application services initialize.
- Added real download progress for update installation, followed by explicit
  verifying and handoff states.
- Kept failed and manual-required installs truthful instead of showing a fake
  installed result.

### Validation boundary

- Full deterministic Flutter tests (261 tests), analyzer and formatting checks
  pass on the release worktree.
- Fixed phone/tablet emulator Library interactions and Windows debug launch
  evidence pass in the audit environment.
- Physical Android/Windows playback remains `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`.

## 1.0.8 - Release-ready

### Bangumi collection and subject details

- Rebuilt the Bangumi collection view with five status filters and live counts.
- Added an independent responsive subject detail route with typed rating, rank,
  public collection statistics, metadata, tags, episodes, characters, staff
  and related subjects.
- Kept episode selection separate from explicit local watched-state updates.
- Added cached-first detail loading, partial/fatal/retry states and stale
  request protection.
- Migrated the Drift cache additively from schema v4 to v5 without resetting
  existing collection or watched-episode state.

### Validation boundary

- Full deterministic tests and analyzer pass on the release worktree.
- Physical playback remains `HARDWARE_VALIDATION_PENDING`.
- Native Windows Computer Use action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when the audit surface exposes
  `apps=[]`.

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
