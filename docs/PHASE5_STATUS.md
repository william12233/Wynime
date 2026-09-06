# Phase 5 Implementation Status

This file records the current Phase 5 evidence boundary. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`SOURCE_VERIFIED_CURRENT_HEAD`

The bounded HLS parser, canonical fingerprint, evidence-bound ad plan, sanitizer, timeline map and proxy integration are present. The Phase 11 Golden reconciliation and current-head verification have closed the former baseline mismatch; the remaining runtime boundaries are recorded by the later phase status files.

## Implemented surface

- strict master/media HLS parsing with syntax, semantic and resource-budget rejection;
- canonical SHA-256 structural fingerprints with volatile authorization values masked;
- CUE and bounded ad DATERANGE evidence for safe planning;
- independent-signal limits, first/last-group protection and removal-ratio limits for heuristic modes;
- VOD-only sanitization preserving key, map, byte-range and program-date-time context;
- exact bidirectional `AdTimelineMap` generation;
- shared `AdRemovalPlan` and fingerprint verification at the proxy boundary;
- fail-closed handling for live/event, LL-HLS, delta, unsupported encryption and ambiguous metadata.

## Current verification evidence

- `dart format --output=none --set-exit-if-changed lib test`: passed on the current worktree;
- `flutter analyze --fatal-infos`: passed on the current worktree;
- targeted HLS parser, fingerprint, planner, sanitizer, timeline and proxy tests: passed in the existing Phase 5 evidence set;
- `flutter test --exclude-tags=golden`: passed (216 tests);
- `flutter test`: passed (220 tests, including all four fixed-size Goldens);
- Android debug and Windows debug builds: passed on the current head.

## Gate boundary

The four fixed-size Golden viewports were deliberately reconciled during Phase 11 and passed in an independent repository-wide rerun. This closes the Phase 5 deterministic Golden boundary; it does not establish live-source, hardware playback, FFmpeg or release-license evidence.

Phase 5 does not claim live source, hardware playback, real downloads, FFmpeg, Bangumi or content-recognition validation.
