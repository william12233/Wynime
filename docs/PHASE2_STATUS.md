# Phase 2 Implementation Status

This file records the implementation baseline for Phase 2. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Locked toolchain

- Flutter: `3.44.8` stable
- Bundled Dart: `3.12.2`
- `html`: `0.15.6`
- `pub_semver`: `2.2.0`
- Existing Drift／SQLite toolchain remains unchanged from Phase 1
- Target platforms: Android and Windows

## Implemented Phase 2 surface

- Strict Source Package schema version 1 JSON decoding
- Semantic package version and Wynime compatibility constraints
- Domain allowlists, permissions, standard-port enforcement and re-consent decisions
- Local/private/IP target rejection
- Resource budgets for documents, redirects, records, selector matches, evaluation steps and regex
- Optional Ed25519-shaped signature metadata with no authority elevation
- Bounded CSS selector dialect over supplied HTML fixtures
- Restricted JSONPath dialect over supplied JSON fixtures
- Bounded regex capture dialect
- Required-field diagnostics and per-record rejection
- Fixture, malicious-input, architecture-boundary and all prior regression tests

## Explicitly absent

No live HTTP, XHR, fetch, cookie jar, login session, Android WebView, Windows WebView2, media interception, site-specific executable adapter, arbitrary source code execution, playback, download, FFmpeg, mpv, Bangumi authentication or Phase 3 implementation exists in Phase 2.

## Validation state

Implementation and security regression coverage are complete. The final current-head CI run and independent read-only Reviewer verdict are recorded in PR #6 and Issue #5 before merge. Phase 3 begins only after both are PASS.
