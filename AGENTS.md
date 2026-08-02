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
- Do not claim success without running the relevant build, analyze, unit, integration, or golden tests.
- Keep Android and Windows UI visually aligned through shared design tokens and fixed-size screenshot tests.
- Do not add bundled font files until the multilingual font review is explicitly approved.
- Keep telemetry disabled by default and redact secrets, cookies, tokens, and full media URLs from logs and persistence.

## Change boundaries

- One phase or one narrowly scoped feature per PR.
- Do not silently expand scope.
- Architecture changes require updating `docs/DECISIONS.md`.
- Public interfaces require tests before implementation dependencies are added.
- Platform-specific code must stay behind typed interfaces.
- Domain must remain pure Dart and must not import Flutter, Drift, HTML parsers, `dart:io`, or `dart:ffi`.

## Phase 2 scope

Allowed:
- Source Package schema version 1 and strict JSON decoding
- Package version and Wynime compatibility validation
- Domain allowlists, declared permissions, resource budgets and re-consent decisions
- Optional signature metadata that never increases runtime authority
- CSS selector evaluation over supplied HTML fixtures
- Restricted JSONPath evaluation over supplied JSON fixtures
- Restricted regular-expression capture over bounded strings
- Fixture-only security, budget and regression tests

Not allowed yet:
- Live HTTP, XHR or fetch
- Android WebView or Windows WebView2
- Cookie jars, login sessions or media-request interception
- Site-specific executable adapters
- Arbitrary Dart, JavaScript, WASM or native code execution
- Real playback, downloads, FFmpeg, mpv or Bangumi authentication
- Phase 3 WebView integration work
