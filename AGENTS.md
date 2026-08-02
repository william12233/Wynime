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

## Phase 3 scope

Allowed:
- Android native WebView and Windows WebView2 behind typed interfaces
- Explicit runtime availability reporting
- Desktop user-agent policy guarded by declared permission
- Cookie import, export, and scoped clearing guarded by declared permission
- Navigation, iframe, resource, XHR, and fetch request observation
- Allowlist enforcement and bounded in-memory media-candidate capture
- Redacted diagnostics and deterministic replay tests
- Android and Windows build gates

Required defaults:
- File and content URI access disabled
- File-URL cross-origin and universal access disabled
- Mixed content blocked
- Camera, microphone, geolocation, and new-window requests denied
- Media playback requires a user gesture
- Captured cookies, tokens, Authorization values, and full URLs must not be logged or persisted
- A missing WebView2 Runtime returns an explicit unavailable status

Not allowed yet:
- Source-provided Dart, JavaScript, WASM, or native executable adapters
- DRM, paywall, login, or access-control bypass
- `PlaybackSession` resolution or refresh callbacks
- Local HLS proxy, sanitizer, ad detection, or timeline mapping
- Media3, mpv, playback backend selection, or playback error recovery
- Real downloads, FFmpeg, Bangumi authentication, or Phase 4 work
