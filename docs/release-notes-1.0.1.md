# Wynime 1.0.1

Status: release candidate, not published.

This candidate uses application version `1.0.1` and Android build number `2`.
It packages the reviewed Phase 6 playback foundation and the responsive
Android／Windows product shell with localized product pages and fixed-size
golden coverage.

## Included boundary

- Shared `PlaybackSession` routing with Android Media3 preference and the
  media-kit/libmpv fallback boundary.
- Fail-closed track, timeline, generation and diagnostic contracts.
- Responsive Home, Search, Library, Downloads, Sources and Settings pages.
- English, Traditional Chinese, Simplified Chinese and Japanese localization
  resources.
- Release metadata, signing configuration boundaries and the exact four-asset
  GitHub Release workflow.

## Explicitly not included

Downloads, AES-128 download execution, FFmpeg/remuxing, Bangumi account or
synchronization work, DRM or paywall bypass, magnet/BT/seeding, and
source-provided executable adapters remain outside this release candidate.

## Verification boundary

Current-head deterministic analyzer, test, golden and local Android／Windows
build evidence exists, and the candidate branch must retain successful
first-party CI for the exact final SHA. The release remains blocked until
native provenance and linked-license closure, official signed CI artifacts,
observable Windows action-level UI, supported Android／Windows hardware
playback and the required independent review are complete. Standalone
FFmpeg/remux/MKV execution is explicitly future scope and is not a gate for
this 1.0.1 boundary.

No GitHub Release, tag or official release artifact is claimed by this file.
