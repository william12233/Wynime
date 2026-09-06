# Phase 9 Implementation Status

This file records the current Phase 9 evidence boundary. The machine-readable sources of truth remain `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`SOURCE_VERIFIED_NO_LIVE_ACCOUNT`

Bangumi domain models, state-bound memory-only authentication, official API parsing, SQLite collection/watched state, appendable offline sync queue, bounded retry/backoff, manual mapping correction and conflict resolution are implemented. No live Bangumi account or OAuth callback was used during validation.

## Verified evidence

- Phase 9 targeted tests: passed (20 tests);
- official `/calendar`, `/v0/subjects/{subject_id}`, `/v0/episodes`, current-user collection and episode paths are covered by deterministic HTTP fixtures;
- OAuth state mismatch, HTTPS endpoint validation, token redaction and token exchange parsing are covered;
- collection status, watched episodes, remote revision and manual mapping persist through an in-memory SQLite database;
- queued, failed, retryable, exhausted and conflict operations are covered, including prefer-remote and prefer-local resolution;
- `dart format --output=none --set-exit-if-changed lib test`: passed (162 files, 0 changes);
- `flutter analyze --fatal-infos`: passed after the Phase 9 implementation;
- `flutter test --exclude-tags=golden`: passed (216 tests);
- `flutter test`: passed (220 tests, including all four fixed-size Goldens);
- `flutter build apk --debug`: passed;
- `flutter build windows --debug`: passed (the existing third-party CMake CMP0175 developer warning remains non-fatal);
- detailed plan DOCX was regenerated from the Markdown sources during the Phase 12 documentation pass and passed ZIP/OXML structural validation; page rendering was not available because LibreOffice/`soffice` is not installed on this host;
- no access token, client secret, cookie or live-account data was written to the repository or database fixtures.

## Remaining validation boundary

- no live OAuth login or real-account synchronization was attempted;
- HTTP fixture coverage is not a claim that a live account, rate limit or upstream availability is currently healthy;
- the four Golden viewport comparisons are now covered by the Phase 11 baseline and independent rerun;
- page-level visual QA of the regenerated DOCX remains unavailable until a LibreOffice/`soffice` runtime is present.

Phase 9 source and deterministic validation is complete for the current host. Production release readiness still depends on the later UI, security, provenance and packaging phases.
