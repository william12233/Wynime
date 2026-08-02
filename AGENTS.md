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

## Phase 6 scope

Allowed:
- Windows libmpv playback through the reviewed `media_kit` wrapper and platform-specific native package
- Android libmpv compatibility prototype while Media3 remains the default engine
- Wynime-owned typed playback controls for play, pause, seek, volume, rate, audio track and subtitle track
- Backend availability probes and a single application-layer engine router
- One bounded automatic fallback per playback operation for decoder, renderer or unsupported failures only
- Engine handoff using the same `PlaybackSession`, loopback proxy lease, capability URI, `AdRemovalPlan` and timeline-map identity
- Preservation of original-timeline position, play state, volume, rate, selected audio and subtitle track
- Generation-scoped event sequencing so stale engine events cannot mutate the current operation
- Platform-only media-kit facade and video surface with deterministic fake-backed tests
- Android／Windows compilation, analyzer, unit, architecture and current-head CI evidence

Required defaults:
- Android preference is Media3 → libmpv → WebView; Windows preference is libmpv → WebView
- libmpv receives only the numeric-loopback capability URI and no upstream URL, Header, Cookie, Referer, Origin or token
- Automatic fallback never runs for authorization, expiry, network or manifest failures
- Track restoration requires an exact current-session track identifier; missing tracks fail closed
- Timeline identity mismatch, unavailable runtime, stale events and repeated fallback fail closed
- Raw native errors are collapsed into stable secret-safe diagnostic codes
- media-kit wrapper and native binary provenance／license must be reviewed before release packaging
- Real-device hardware playback remains `prototype_not_hardware_validated` until exercised on supported Android and Windows hardware

Not allowed yet:
- Real downloads, FFmpeg execution, AES-128 download decryption, remuxing or artifact creation
- DRM, paywall, login or access-control bypass
- Content-recognition or machine-learning ad classification
- Source-provided Dart, JavaScript, WASM or native executable adapters
- Bangumi authentication or Phase 7 work
