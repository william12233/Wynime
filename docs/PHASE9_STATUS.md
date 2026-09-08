# Phase 9 Implementation Status

This file records the current Phase 9 evidence boundary. The machine-readable sources of truth remain `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`SOURCE_IMPLEMENTED_NO_LIVE_ACCOUNT`

Bangumi domain models, state-bound memory-only authentication, official API parsing, SQLite collection/watched state, account-scoped calendar cache, appendable offline sync queue, bounded retry/backoff, manual mapping correction, conflict resolution and the connected Settings／Home／Library flows are implemented. No live Bangumi account or OAuth callback was used during validation. This status does not claim that Phase 9 has passed its live-account, Worker-deployment or action-level UI gates.

## Verified evidence

- Phase 9 targeted tests are covered by the current full suite: passed (212 tests total);
- official `/calendar`, `/v0/subjects/{subject_id}`, `/v0/episodes`, current-user collection and episode paths are covered by deterministic HTTP fixtures;
- OAuth state mismatch, HTTPS endpoint validation, token redaction and token exchange parsing are covered;
- collection status, watched episodes, remote revision and manual mapping persist through an in-memory SQLite database;
- the Bangumi calendar cache and last-refresh timestamp survive a store restart and are shown separately from the authenticated-session state;
- queued, failed, retryable, exhausted and conflict operations are covered, including prefer-remote and prefer-local resolution;
- `dart format --output=none --set-exit-if-changed --suppress-analytics lib test`: passed (149 files, 0 changes);
- `flutter analyze --fatal-infos`: passed after the Phase 9 implementation;
- `flutter test --suppress-analytics`: passed (212 tests, including the current fixed-size Goldens);
- Android debug build: passed; `build/app/outputs/flutter-apk/app-debug.apk` was produced by the isolated Gradle build;
- Windows debug and release builds: passed after restoring and verifying the pinned media-kit libmpv archive; the release bundle contains `wynime.exe`, `wynime_update.exe` and `flutter_windows.dll`;
- Bangumi broker `npm test`, TypeScript typecheck and Wrangler dry-run: passed; no production Worker deployment was performed;
- detailed plan DOCX was regenerated from the Markdown sources during the Phase 12 documentation pass and passed ZIP/OXML structural validation; page rendering was not available because LibreOffice/`soffice` is not installed on this host;
- no access token, client secret, cookie or live-account data was written to the repository or database fixtures.

## Remaining validation boundary

- no live OAuth login or real-account synchronization was attempted;
- HTTP fixture coverage is not a claim that a live account, rate limit or upstream availability is currently healthy;
- the fixed-size Golden comparisons are covered by the current test suite, but action-level Windows compact／medium／expanded／live-resize and Android phone／tablet flows remain unverified;
- a direct Windows `flutter run` attempt hit the host Visual Studio FileTracker `E_ACCESSDENIED` failure; this is separate from the successful debug/release builds and leaves native UI observation unavailable;
- no Windows or Android action-level UI evidence is available, so the UI result is `BLOCKED_UI_ENVIRONMENT`;
- page-level visual QA of the regenerated DOCX remains unavailable until a LibreOffice/`soffice` runtime is present.

The Phase 9 source and deterministic tests are present, but live OAuth/account
validation, action-level UI and Worker deployment remain separate external
boundaries. This record does not claim a live account or production Worker
deployment.
