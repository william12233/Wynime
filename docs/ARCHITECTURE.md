# Wynime Architecture

## Layers

### Presentation
Flutter widgets, responsive shells, navigation, localization, player controls, source management, downloads, library and settings.

### Application
Use cases and coordinators. This layer owns source selection, playback session lifecycle, failover decisions, download orchestration, Bangumi sync orchestration and settings policy.

### Domain
Pure models and interfaces. No Flutter widgets, WebView, Media3, mpv, FFmpeg, HTTP client, Drift, HTML parser or database implementation details.

### Infrastructure
Database, network clients, source engines, HLS processing, download implementation, Bangumi client and platform bridges.

### Platform
Android Kotlin modules, Windows native modules and Flutter plugin adapters. Platform implementations must be hidden behind typed interfaces.

## Authoritative objects

- `PlaybackSession`: single handoff object for players and downloader.
- `AdRemovalPlan`: single ad decision and timeline mapping for a particular manifest fingerprint.
- `DownloadArtifactManifest`: single inventory for every file created by a download task.
- `DeleteJob`: persistent deletion transaction referencing the original artifact manifest.

## Dependency direction

```text
Presentation → Application → Domain
Infrastructure ─────────────→ Domain
Platform ───────────────────→ Domain
```

Domain must not import upper or outer layers. Architecture tests reject Flutter, Drift, HTML parser, WebView plugins, `dart:io` and `dart:ffi` imports from `lib/src/domain`.

## Package layout through Phase 3

```text
lib/
├─ main.dart
├─ l10n/
└─ src/
   ├─ app/
   ├─ presentation/
   ├─ design_system/
   ├─ domain/
   │  ├─ models/
   │  ├─ repositories/
   │  └─ services/
   ├─ infrastructure/
   │  ├─ database/
   │  ├─ repositories/
   │  ├─ source_rules/
   │  └─ web_capture/
   └─ platform/
      └─ web_capture/
```

- `app` owns application bootstrap and navigation destination definitions.
- `presentation` owns responsive shells and placeholder pages.
- `design_system` owns the single breakpoint classifier, spacing, radii, motion, dimensions and semantic themes.
- `domain` contains pure Dart models and typed interfaces only.
- `infrastructure/database` owns the Drift schema and generated database mapping.
- `infrastructure/repositories` implements Domain repository interfaces without exposing Drift records outside Infrastructure.
- `infrastructure/source_rules` owns strict package decoding and fixture-only declarative rule evaluation.
- `infrastructure/web_capture` owns plugin-independent event accumulation, budgets, classification and redaction.
- `platform/web_capture` is the only layer that imports the WebView plugin and maps plugin values into Domain capture models.

## Phase 1 persistence architecture

### Database baseline

Phase 1 uses Drift with SQLite schema version 1. Foreign-key enforcement is enabled whenever the database opens. The schema contains:

- `app_settings_rows`: singleton typed settings; telemetry defaults to disabled.
- `watch_history_rows`: exact source, line, subject, episode, playback position, duration, backend and timeline-map identity.
- `artifact_manifests`: authoritative manifest identity and download identity.
- `artifact_rows`: every registered physical artifact URI, kind and owning manifest.
- `delete_job_rows`: persistent deletion state machine referencing an existing artifact manifest.

### Watch-history identity

The authoritative watch-record identity is the tuple `(sourceId, lineId, subjectId, episodeId)`. Saving progress is transactional:

1. Find an existing row by the authoritative tuple.
2. Insert when absent.
3. Replace that row when present, even if the caller provides a newer `progressId`.

This prevents duplicate resume records and avoids relying on a primary-key-only upsert when the natural episode identity is also unique.

### Artifact-manifest transaction

`DownloadArtifactManifest` and all child artifacts are inserted in one database transaction. Artifact IDs are unique within their owning manifest, while absolute file URIs are unique across all manifests. Any child conflict rolls back the parent manifest insert, so deletion can never observe a partial inventory.

No repository reconstructs or guesses file paths. The database stores only file URIs explicitly supplied by the authoritative manifest.

### DeleteJob state machine

New jobs must begin in `pending`. Legal transitions are:

```text
pending → running → completed
                  ↘ failed → pending
```

- Entering `running` increments the attempt count.
- `failed` requires a non-empty failure code.
- `completed` is reachable only from `running`.
- Startup recovery converts interrupted `running` jobs to visible `failed` jobs using the `interrupted` failure code.
- Phase 1 does not delete physical files. Later deletion execution must consume the persisted manifest and retain canonical-containment and symlink/junction protections.

### Persistence privacy boundary

Phase 1 stores settings, source/line/episode identities, resume positions, registered local artifact URIs and deletion state. It does not store complete media URLs, cookies, authentication tokens or source-package executable code.

## Phase 2 source-rule architecture

### Source Package schema version 1

A package declares identity, semantic version, compatible Wynime version range, security policy, bounded declarative programs and optional Ed25519-shaped signature metadata. Unknown JSON keys and unsupported selector types fail closed.

Signature metadata is not cryptographic verification and never raises runtime authority. Signed and unsigned packages use identical allowlist, permission, consent and budget checks.

### URI and consent policy

- URI schemes are limited to HTTPS and explicitly consented HTTP.
- Schema version 1 permits only standard ports 443 and 80.
- User-info URIs, localhost, `.localhost`, `.local`, IPv4 literals and deceptive suffix hosts are rejected.
- Host matching uses exact equality or a dot-boundary subdomain rule.
- Adding a permission or domain, enabling subdomains or broadening any resource budget requires fresh consent.
- A package contains at most 32 domain rules and 32 programs; each program contains at most 64 fields.

### Declarative dialects

- HTML fixtures: bounded CSS subset using tag, id and class simple selectors with descendant or child combinators.
- JSON fixtures: `$`, property segments, non-negative array indexes and `[*]` only.
- Regex captures: short input and pattern budgets with no lookaround, backreference, alternation, braced quantifier, quantified wildcard, nested group or quantified group.
- XPath and executable Dart, JavaScript, WASM or native adapters are explicitly rejected in schema version 1.

### Evaluation budgets

Before and during evaluation, the engine enforces document bytes, redirect count, result count, selector matches, evaluation steps, regex pattern length and regex input length. Security exceptions retain their classification and are not converted into parser failures.

### Fixture-only boundary

Phase 2 accepts a supplied `SourceFixture` containing the intended URI, redirect chain and HTML or JSON body. The source-rule implementation imports no HTTP client, WebView, `dart:io`, `dart:ffi` or executable-code API. Real network and browser capture begin only in Phase 3 behind typed interfaces.

## Phase 3 WebView capture architecture

### Runtime and plugin boundary

Phase 3 uses `flutter_inappwebview` only under `lib/src/platform/web_capture`. Android resolves to the native Android WebView implementation and Windows resolves to the endorsed WebView2 implementation. `WebSourceBrowserPort` exposes runtime probing and scoped cookie operations without exposing plugin classes to Domain or Infrastructure.

A Windows machine without the WebView2 Runtime returns an explicit `webview2_runtime_missing` status. Runtime probe failures return `webview2_probe_failed`; neither state is converted into success.

### Capture request authority

`WebCaptureRequest` combines the Phase 2 `SourceSecurityPolicy` with:

- platform-default or explicitly permitted desktop user agent;
- bounded initial headers and cookies;
- event, candidate, header and cookie budgets;
- explicit media-request inspection permission.

Every initial URI, navigation, iframe, resource, XHR and fetch target is checked against the same allowlist. Disallowed navigations are cancelled, disallowed resource requests receive an empty 403 response, and disallowed XHR/fetch requests are aborted.

### Browser hardening

The platform surface enables JavaScript only because dynamic source pages require it, while enforcing these defaults:

- no file or content URI access;
- no file-URL cross-origin or universal access;
- no mixed content;
- no automatic JavaScript windows or multiple windows;
- no camera, microphone or geolocation permission;
- media playback requires a user gesture;
- no automatic source download handling.

Source packages cannot inject Dart, JavaScript, WASM or native executable adapters in Phase 3. The plugin's internal request-observation instrumentation is platform implementation detail and receives no package-supplied program.

### Capture output and privacy

`WebCaptureAccumulator` stores bounded events and deduplicated media candidates in memory only. Candidate classification recognizes HLS, DASH, common direct audio/video files and media segments using response content type or URL path.

Diagnostic output contains scheme, host, path-segment count, method and header names only. Cookie values, Authorization values, query strings, fragments and complete media URLs are not logged or persisted. Phase 3 does not create a `PlaybackSession`; Phase 4 must validate and transform a chosen candidate through its own Gate.

## Platform strategy

- Flutter owns shared UI and presentation state.
- Android playback starts with Media3; libmpv is a compatibility fallback.
- Windows playback starts with libmpv.
- Android dynamic page resolution uses Android WebView.
- Windows dynamic page resolution uses WebView2.
- The HLS sanitizer and local proxy sit before every playback backend.

## Source strategy

1. CMS fingerprint and generated rules.
2. Declarative selector rules.
3. WebView interception.
4. Site-specific adapter.

A source is not trusted merely because it is signed. Every source is constrained by permissions, domain allowlists and resource budgets.

## Failure policy

- HTTP authorization failures refresh the session.
- Manifest failures go through sanitizer diagnostics.
- Codec or renderer failures may trigger backend fallback.
- Deletion failures remain visible and recoverable.
- No layer may convert an unknown failure into success.
