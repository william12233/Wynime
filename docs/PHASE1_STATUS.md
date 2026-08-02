# Phase 1 Implementation Status

This file records the implementation baseline for Phase 1. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Locked toolchain

- Flutter: `3.44.8` stable
- Bundled Dart: `3.12.2`
- Drift: `2.34.3`
- drift_flutter: `0.3.1`
- drift_dev: `2.34.5`
- build_runner: `2.15.1`
- SQLite schema version: `1`
- CI Android Java: Temurin 17
- Target platforms: Android and Windows

`build_runner` is pinned to `2.15.1` because newer releases require analyzer/meta versions outside Flutter 3.44.8's compatible dependency set. The complete lockfile is generated and checked into the repository.

## Implemented Phase 1 surface

- Typed settings persistence with telemetry disabled by default
- Exact watch-history and resume-position persistence
- Transactional natural-identity replacement for source/line/subject/episode progress
- Atomic `DownloadArtifactManifest` and child artifact persistence
- Foreign-key-protected persistent `DeleteJob` records
- Explicit DeleteJob state machine and interrupted-job recovery
- In-memory SQLite tests for repositories, transactions, rollback and recovery
- Generated-code consistency, format, analyze, test, Android build and Windows build gates
- Long-lived DOCX snapshot workflow that follows `main` instead of the retired Phase 0 branch

## Explicitly absent

No live source parsing, source-package execution, WebView interception, media playback, downloads, physical file deletion, FFmpeg, mpv, Bangumi production authentication or Phase 2 rule-engine functionality is implemented in Phase 1.

## Validation state

The implementation and regression suite are complete. The final current-head CI run and read-only Reviewer verdict are recorded in PR #4 and Issue #3 before merge. Phase 2 must not begin until both are PASS.
