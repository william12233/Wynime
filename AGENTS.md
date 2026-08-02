# Wynime Agent Instructions

Before changing code, read in this order:

1. `docs/PROJECT_PLAN.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DECISIONS.md`
4. Relevant feature docs and tests

`docs/Wynime_完整應用計畫與技術規格_v0.3.docx` is the detailed human-review snapshot. It is useful for full-plan review, but it is not the machine-readable source of truth. If the DOCX conflicts with Markdown or an accepted architecture decision, Markdown and `docs/DECISIONS.md` win. Update Markdown first, then regenerate the DOCX through the repository workflow.

## Working rules

- Do not implement magnet, BT, seeding, DRM bypass, paywall bypass, or access-control circumvention.
- Preserve the approved architecture: Flutter UI with platform-native media and WebView integrations.
- Playback and download must share the same `PlaybackSession`.
- Ad removal must use one authoritative `AdRemovalPlan` keyed by source, line, episode, and manifest fingerprint.
- Download and deletion must share one authoritative `DownloadArtifactManifest`; deletion must never reconstruct or guess paths.
- Unsigned source packages are allowed, but sandboxing, domain allowlists, declared permissions, and resource limits are mandatory.
- Do not claim success without running the relevant build, analyze, unit, integration, or golden tests. When real hardware or an external runtime is unavailable, record that limitation and use current-head compilation, static analysis, deterministic replay tests, and independent read-only code review as the acceptance evidence.
- Keep Android and Windows UI visually aligned through shared design tokens and fixed-size screenshot tests.
- Do not add bundled font files until the multilingual font review is explicitly approved.
- Keep telemetry disabled by default and redact secrets, cookies, tokens, and full media URLs from logs and persistence.

## Change boundaries

- One phase or one narrowly scoped feature per PR.
- Do not silently expand scope.
- Architecture changes require updating `docs/DECISIONS.md`.
- Public interfaces require tests before implementation dependencies are added.
- Platform-specific code must stay behind typed interfaces.
- Domain must remain pure Dart and must not import Flutter, Drift, HTML parsers, WebView plugins, `dart:io`, or `dart:ffi`.

## Phase 5 scope

Allowed:
- Strict, bounded HLS master and media playlist parsing into immutable typed models
- Canonical SHA-256 manifest fingerprints that mask volatile authorization values while retaining structural identity
- Explicit ad evidence from bounded CUE markers and bounded `EXT-X-DATERANGE` records
- Conservative structural evidence using authority, path, duration and interior discontinuity-group signals
- `off`, `safe`, `smart` and `aggressive` planning modes with one authoritative `AdRemovalPlan`
- Exact original-to-sanitized and sanitized-to-original timeline mapping
- Complete-VOD manifest sanitization that preserves effective key, map, byte-range, gap and program-date-time context
- Discontinuity and media-sequence repair after approved segment removal
- Playback-proxy integration before opaque loopback URI rewriting
- Deterministic fixture, malicious-input, timeline, proxy and architecture tests

Required defaults:
- Every plan is keyed by complete source／line／subject／episode identity and the exact canonical manifest fingerprint
- An active plan must match the current parsed manifest before any transformation
- `EXT-X-DISCONTINUITY` alone never authorizes removal
- Safe mode removes only explicitly bounded CUE or ad-DATERANGE segments
- Smart and aggressive modes require at least two independent signals, never heuristically remove first or last groups, and obey a bounded removal ratio
- Planning and sanitization refuse to remove every segment
- Sanitization accepts only complete VOD media playlists; live, event, low-latency, I-frame-only and ambiguous semantics fail closed
- Phase 5 accepts only identity `AES-128` key semantics; SAMPLE-AES, DRM key formats and access-control bypass remain rejected
- Cookies, tokens, complete upstream URLs, SCTE payloads and unredacted manifests never enter diagnostics or persistence

Not allowed yet:
- Content-recognition or machine-learning ad classification
- Source-provided Dart, JavaScript, WASM or native executable adapters
- DRM, paywall, login or access-control bypass
- mpv, FFmpeg, real downloads, AES-128 download execution, Bangumi authentication or Phase 6 work
