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

## Phase 4 scope

Allowed:
- Hardened authoritative `PlaybackSession` models and refresh invariants
- Controlled media-candidate to `PlaybackSession` resolution
- Loopback-only IPv4 or IPv6 HTTP proxy with per-session capability paths
- Source-allowlist enforcement, DNS public-address preflight and bounded redirects
- Bounded forwarding of approved headers, cookies, Referer, Origin and User-Agent
- HLS master/media playlist URI rewriting without ad decisions or timeline edits
- GET, HEAD, one-range forwarding, lease cancellation and service shutdown
- Android Media3 1.10.1 behind typed Flutter platform interfaces
- Pure-Dart playback state and failure classification
- Explicit Windows unsupported backend while preserving Windows buildability
- Android and Windows build gates, proxy integration tests and architecture tests

Required defaults:
- Proxy listeners bind only to numeric loopback addresses and ephemeral ports
- Every exposed session receives an unguessable capability token and opaque resource IDs
- Unknown tokens, undeclared authorities, non-public DNS results and excess budgets fail closed
- Upstream redirects and HLS child resources reuse the Phase 2 `SourceSecurityPolicy`
- Set-Cookie, upstream Location, Authorization, Cookie, tokens and full upstream URLs are never returned to players or diagnostics
- Media3 accepts only a validated loopback proxy URI, never a raw upstream URI
- HTTP 401／403 and explicit expiry request `PlaybackSession` refresh instead of decoder fallback
- Playback events remain bound to the current session identity

Not allowed yet:
- Manifest sanitization, ad detection, `AdRemovalPlan` generation or timeline rewriting
- Source-provided Dart, JavaScript, WASM or native executable adapters
- DRM, paywall, login or access-control bypass
- mpv, FFmpeg, real downloads, Bangumi authentication or Phase 5 work
