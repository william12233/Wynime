# Phase 8 Implementation Status

This file records the current Phase 8 evidence boundary. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`SOURCE_VERIFIED_RUNTIME_UNAVAILABLE`

The Domain remux contract, bounded MP4-to-MKV fallback policy, artifact verification contract, append-only manifest inventory, DeleteJob lifecycle and report-only orphan scanner are implemented and tested. The root-confined read-only inventory, link/junction-aware atomic artifact adapter, regular-file download store and no-shell process runner are also implemented and tested against isolated fixtures. The installed environment does not provide an `ffmpeg` executable, so a real media remux run remains environment-unavailable rather than being reported as a pass.

## Verified evidence

- `dart format --output=none --set-exit-if-changed lib test`: passed with zero changes;
- `flutter analyze --fatal-infos`: passed;
- `flutter test --exclude-tags=golden`: passed (216 tests);
- `flutter test`: passed (220 tests, including all four fixed-size Goldens);
- `flutter build apk --debug`: passed;
- `flutter build windows --debug`: passed (CMP0175 is an existing plugin CMake developer warning);
- remux fallback and non-fallback failure tests: passed;
- manifest append-only and `listAll` SQLite tests: passed;
- deletion state-machine, link fail-closed and orphan-report tests: passed;
- root-confined local inspect, atomic move, safe delete and invalid-container verification tests: passed using an isolated temporary fixture;
- missing-FFmpeg, non-file-input and outside-root rejection tests: passed;
- download writes, replacements and linked-parent rejection tests: passed;
- persisted HLS manifest snapshots contain structural metadata only and exclude URLs, queries and credentials;
- no existing user artifact was deleted or moved during these checks.

## Remaining environment limitation

- `where.exe ffmpeg` finds no executable on the current host;
- therefore actual FFmpeg media-fixture remux output is `UNVERIFIED_ENVIRONMENT`, while the invocation boundary, fallback policy, output verification and file mutation boundaries are covered by deterministic tests;
- a future host with the reviewed FFmpeg binary must run the isolated MP4/MKV fixture before release packaging.

Phase 8 source and deterministic validation is complete for this host. This is not a hardware/media-runtime or release pass; the limitation must remain visible in the final release audit.
