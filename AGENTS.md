# Wynime Agent Instructions

Before changing code, read in this order:

1. `docs/PROJECT_PLAN.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DECISIONS.md`
4. Relevant feature docs and tests

`docs/Wynime_完整應用計畫與技術規格_v0.3.docx` is a human-review snapshot. Markdown and accepted decisions are authoritative.

## Working rules

- Do not implement magnet, BT, seeding, DRM bypass, paywall bypass, or access-control circumvention.
- Preserve Flutter UI with platform-native media and WebView integrations.
- Playback and download must share the same `PlaybackSession`.
- Ad removal must use one authoritative `AdRemovalPlan` keyed by source, line, episode, and manifest fingerprint.
- Download and deletion must share one authoritative `DownloadArtifactManifest`; deletion must never reconstruct or guess paths.
- Do not claim success without relevant build, analyze, unit, integration, or golden tests.
- Keep telemetry disabled by default and redact secrets, cookies, tokens, and full media URLs from logs and persistence.

## Change boundaries

- One phase or one narrowly scoped feature per PR.
- Do not silently expand scope.
- Architecture changes require updating `docs/DECISIONS.md`.
- Public interfaces require tests before implementation dependencies are added.
- Platform-specific code must stay behind typed interfaces.
- Domain must remain pure Dart and must not import Flutter, Drift, `dart:io`, or `dart:ffi`.

## Phase 1 scope

Allowed:
- Drift and SQLite schema version 1
- Typed settings persistence
- Watch history and resume-position persistence
- Atomic `DownloadArtifactManifest` and artifact inventory persistence
- Persistent `DeleteJob` state machine and interrupted-job recovery
- In-memory persistence tests and Android/Windows build gates

Not allowed yet:
- Live website scraping or source execution
- Real media playback or player dependencies
- Real downloads or physical file deletion
- FFmpeg or mpv binaries
- Bangumi production authentication
- Phase 2 source-rule engine work
