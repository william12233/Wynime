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

## Package layout through Phase 6

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
   │  ├─ web_capture/
   │  └─ playback/
   └─ platform/
      ├─ web_capture/
      └─ playback/
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
- `infrastructure/playback` owns candidate resolution, the loopback HTTP proxy, bounded upstream I/O and HLS URI rewriting.
- `platform/playback` is the only Dart layer that imports Flutter platform channels for native player integration.

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

## Phase 4 playback architecture

### Authoritative session resolution

A selected `WebMediaCandidate` is converted once into a `PlaybackSession`. The resolver rejects segment-only and deferred DASH candidates, reuses the Phase 2 `SourceSecurityPolicy`, separates Referer／Origin／User-Agent from generic headers, and includes only cookies valid for the candidate host, path, security scheme and expiry. Refresh callbacks may replace expiring URLs and authority values but must preserve `sessionId` and the complete source／line／subject／episode identity.

### Loopback proxy capability model

`LoopbackPlaybackProxyService` binds only `127.0.0.1` or `::1` on an ephemeral port. Each lease creates an unguessable capability token and opaque resource IDs. The native player receives only the loopback URI; upstream media URLs, query tokens, cookies and authorization values remain inside the proxy session.

Every initial URI, redirect and HLS child URI must pass the same source allowlist. The production upstream client performs explicit IPv4 and IPv6 public-address DNS preflight, repeats validation inside the `HttpClient.connectionFactory`, and opens the socket directly to that validated numeric address. HTTPS then upgrades the connected socket with the original hostname for TLS validation. This rejects loopback, link-local, private, carrier-grade NAT, documentation, multicast, IPv4-embedded private and standard NAT64-private ranges without leaving a second hostname lookup between validation and connection. The proxy is therefore not a general URL forwarder.

### Forwarding and resource budgets

The proxy accepts only GET and HEAD plus one syntactically valid byte range. It forwards the authoritative session headers, scoped cookies, Referer, Origin and User-Agent under independent byte budgets. Redirect count, playlist bytes, total response bytes and registered HLS resources are bounded. Upstream Set-Cookie and redirect Location are never exposed downstream. Closing a lease invalidates its capability and cancels active body subscriptions; service shutdown closes all leases, the listener and the upstream client.

### HLS rewrite boundary

Phase 4 validates `#EXTM3U` and rewrites standalone media lines and `URI=` attributes to opaque loopback resources. It does not remove segments, infer advertisements, alter discontinuities, generate an `AdRemovalPlan` or rewrite media time. Those decisions remain Phase 5.

### Playback lifecycle coordinator

`PlaybackCoordinator` is the Application-layer owner of one playback operation. It resolves a session, refreshes it before exposure when expiry is near, creates exactly one proxy lease, hands the loopback URI to the selected backend, and releases both player and lease on stop, replacement or failure. Authorization or explicit-expiry events may trigger a bounded refresh cycle that preserves session and episode identity; superseded operations cannot reopen an old lease.

### Android Media3 boundary

Android pins Media3 ExoPlayer and the HLS module to `1.10.1`. Flutter communicates through Wynime-owned MethodChannel and EventChannel messages under `platform/playback`; Media3 types never enter Domain. The native bridge accepts only numeric loopback HTTP URIs, exposes open／pause／seek／close, emits monotonic state and failure events bound to the active session, and reports HTTP status when available. Pure Dart classification routes 401／403 and explicit expiry to session refresh rather than decoder fallback.

The Android manifest requests network access but keeps cleartext traffic disabled globally. A Network Security Config permits cleartext only for numeric loopback `127.0.0.1` and `::1`, matching the proxy listener and native URI validation instead of allowing arbitrary external HTTP.

Windows remains buildable through an explicit unsupported Phase 4 backend. Windows mpv begins only in Phase 6.

## Phase 5 HLS sanitization architecture

### Strict typed parser

Phase 5 parses HLS text into immutable master or media models before any ad decision. Character, line, URI, attribute, rendition, date-range, variant and segment budgets are enforced before unbounded accumulation. Duplicate singleton tags, mixed master／media semantics, unsafe URI forms, malformed byte ranges, ambiguous segment-scoped tags and content after `EXT-X-ENDLIST` fail closed.

The sanitizer boundary deliberately excludes live／event playlists, low-latency HLS, delta updates, I-frame-only media, `END-ON-NEXT` date ranges and other semantics that cannot yet be rewritten deterministically. Encryption parsing accepts only identity `AES-128`; SAMPLE-AES and non-identity key formats remain outside scope and are not interpreted as authorization to bypass DRM.

### Canonical fingerprint

`HlsManifestFingerprinter` hashes a canonical structural representation with SHA-256. It retains playlist kind, sequence numbers, durations, discontinuities, byte ranges, effective key／map context, date ranges, program dates, variants and resource structure. Volatile token, signature, credential and expiry query values are replaced by bounded placeholders before hashing, so ordinary URL renewal does not invalidate an otherwise identical manifest while structural changes do.

The fingerprint contains no recoverable manifest or credential data. Every active `AdRemovalPlan` must match the current fingerprint before the proxy exposes transformed bytes.

### Evidence and planning policy

The authoritative planner distinguishes explicit CUE evidence from bounded ad `EXT-X-DATERANGE` evidence. Safe mode removes only segments covered by those explicit signals. Smart and aggressive modes may additionally use authority changes, suspicious path signatures, short interior discontinuity groups and duration outliers, but require at least two independent evidence kinds.

Heuristics never target the first or last discontinuity group and cannot exceed the configured share of the original duration. A discontinuity by itself is never evidence. Any result that would remove the complete playlist fails closed.

### Sanitizer and timeline map

`HlsManifestSanitizer` consumes the parsed media playlist, exact fingerprint and authoritative plan. Every removal is re-bound to media sequence, segment index, original start and duration before output. The sanitizer preserves the effective `EXT-X-KEY`, `EXT-X-MAP`, byte range, gap and program-date-time context of retained segments; recalculates target duration, media sequence and discontinuity sequence; and emits a complete VOD playlist.

`AdTimelineMap` covers the original duration exactly once with kept and removed intervals. Playback progress can therefore map monotonically in both directions across removed gaps. The proxy applies sanitization before Phase 4 opaque loopback URI rewriting, so players still receive only capability-scoped loopback resources.

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

## Phase 6 native playback routing

`PlaybackCoordinator` continues to own exactly one resolved `PlaybackSession` and one loopback `PlaybackProxyLease`. It receives a Wynime-owned `PlaybackEngineRouter` as its `PlayerBackend`; therefore switching Media3／libmpv never asks the resolver or proxy to create a second session. The same capability URI, ad-removal plan and `timelineMapIdentity` cross every engine handoff.

The router lives in Application and depends only on Domain ports. Platform implementations remain under `lib/src/platform/playback`:

- `Media3PlayerBackend` maps the Android MethodChannel and EventChannel to the typed port.
- `MpvPlayerBackend` maps a testable media-kit facade to the same typed port.
- `ProductionMediaKitFacade` is the only layer that imports `media_kit` and `media_kit_video`.
- `MpvPlayerSurface` is the only UI adapter that consumes the media-kit `VideoController`.
- `PlayerBackendFactory` supplies Android order Media3 → libmpv → WebView and Windows order libmpv → WebView.

The native player never receives the upstream media URI or source credentials. Both Media3 and libmpv accept only a bounded numeric-loopback capability endpoint. `MpvPlayerBackend` supplies an empty HTTP-header map to media-kit. Upstream authorization remains exclusively inside the proxy.

Before a switch, the router snapshots position in the original timeline, play state, volume, rate, audio selection, subtitle selection and timeline identity. The new backend opens the same session, then receives the mapped sanitized seek and remaining controls. Events emitted while handoff is pending cannot overwrite that snapshot. Event subscriptions are scoped by operation and backend generation so old-engine events are discarded.

Automatic fallback is limited to one attempt per operation and only decoder, renderer or unsupported failures. Authorization／expiry returns to the existing session-refresh path; network and manifest failures remain visible without engine hopping. The Application router independently validates every event and selection against the current `PlaybackSession`: exact track identifiers must exist, external-URI tracks are rejected, and foreign／stale／ambiguous IDs fail closed. Production media-kit selection also matches the native track ID exactly.

Synchronous controls, open／handoff failures and asynchronous native events cross a bounded stable error boundary. Raw native/platform messages and stack traces never enter Application diagnostics or persistence; only typed failure codes and non-sensitive metadata may cross. The Dart wrapper is MIT-licensed, but release suitability depends on the exact bundled libmpv and linked FFmpeg build. Phase 6 records the dependency versions and keeps release licensing as an explicit provenance gate. Compilation and deterministic fake-backed tests do not establish real hardware playback; without Android and Windows device evidence the result remains `prototype_not_hardware_validated`.

## Phase 7 download and Phase 8 artifact lifecycle

`DownloadService` consumes the same resolved `PlaybackSession`, source security policy, manifest fingerprint and `AdRemovalPlan` used by playback. It accepts only complete VOD playlists, persists a bounded `DownloadJob` after each segment, stores only explicitly registered local artifact URIs and refreshes the session at most within the request limit. Identity AES-128 decryption is the only supported download encryption path; unsupported or ambiguous encryption fails closed.

The persisted `DownloadArtifactManifest` is append-only after creation. `addArtifacts` may register a planned remux temporary or final output only when its artifact ID and URI are new; existing inventory entries cannot be changed. `listAll` is used by orphan reporting and never authorizes deletion by itself.

Phase 8 separates read-only inventory from mutation. `ArtifactFileInspector` performs lexical containment, canonical-parent containment and link/type inspection under the configured download root. `ArtifactFileOperations` adds deletion only for a safe regular file, while `ArtifactMover` is a separate port for same-root atomic promotion. `ArtifactDeletionService` consumes the persisted `DeleteJob` and the manifest returned by the repository; it never derives a path from an episode or filename. Every deletion is re-inspected and remains a visible failed job if verification fails.

`RemuxService` registers both temporary and final output artifacts before invoking a `RemuxRunner`. The runner receives only local file URIs, a manifest ID, a timeline-map identity and a container choice. A successful process is not sufficient: `ArtifactVerifier` must accept the expected MP4 or Matroska signature and bounded size before atomic promotion. MP4 fallback is limited to an explicit unsupported-container or invalid-MP4 result; input, authorization, network and generic failures do not trigger MKV fallback. Orphan scanning is read-only and reports both unregistered physical files and registered files that are missing.

## Phase 9 Bangumi synchronization

Phase 9 keeps Bangumi metadata and collection state behind pure Domain models and typed ports. `BangumiSubject`, `BangumiEpisode` and `BangumiScheduleEntry` represent bounded calendar and subject data; schedule entries preserve the API weekday when no concrete episode air date is supplied. `BangumiRemoteState` carries the collection status, watched episode IDs and an opaque remote revision used for optimistic conflict detection.

`BangumiAuthenticationService` creates a state-bound OAuth authorization request, accepts only standard-port HTTPS authorization and redirect URIs without user-info or fragments, and stores the resulting `BangumiAuthSession` only in memory. The token exchange is a typed port; token, client secret and cookies are never written to Drift or diagnostics. Expired sessions fail closed as `auth_required`.

`BangumiClient` exposes daily calendar, subject, paged episode, remote-state and mutation operations. The production adapter is limited to the official `api.bgm.tv`/`api.bgm38.tv` HTTPS hosts, uses `Authorization: Bearer` headers rather than query tokens, bounds response bodies and maps HTTP/network/payload failures to stable codes. Collection status maps to the official values 1 wish, 2 completed, 3 watching, 4 on-hold and 5 dropped; an episode is watched only for collection type 2. Mutations use the official current-user `-` paths and perform a bounded remote-revision preflight when the queued operation has a base revision.

Drift schema version 3 stores local collection status and remote revision, watched episode rows, manual local-to-Bangumi subject mappings and `BangumiSyncOperation` rows. The sync service updates local state immediately for offline use, then persists a queued operation. Queue recovery selects only queued or due retryable failures below the configured attempt limit. Authorization, malformed payload, unsupported HTTP and exhausted retry failures remain visible rather than being silently retried forever. Remote revision conflicts remain visible and can be resolved by applying the remote state or by requeueing the local mutation against the newly fetched revision.

## Phase 10 automatic source builder

Phase 10 receives only bounded `SourceBuilderObservation` fixtures and explicit field hints. The builder parses observations offline and searches for repeated structures that can be expressed in the Phase 2 declarative dialect. HTML output is restricted to generated tag/id/class CSS selectors; JSON output is restricted to generated property and wildcard JSONPath expressions. XPath, executable package content, arbitrary selectors and unverified guesses are never emitted.

The builder creates a candidate `SourcePackageManifest`, then evaluates it with the existing fixture rule evaluator against every observation before returning it. The proposal contains a deterministic fingerprint of package structure only; raw observation bodies, expected values, cookies, tokens and complete media URLs are not retained in the proposal or diagnostics. A missing example or mismatch produces a rejected proposal.

Security policy is copied from the previous package when present. An observed domain outside that policy can be included only through an explicit builder option and is marked for re-consent. HTTP requires an explicit opt-in and the `insecureHttp` permission; no permission is inferred from page content. Resource budgets remain bounded and any broader update requires re-consent.

Every successful result remains in `reviewRequired` state and `canActivate` is always false until `SourcePackageActivationService` receives the exact proposal ID, explicit user approval and any required re-consent. Signatures remain metadata and do not increase runtime authority.

## Phase 11 presentation architecture

`WynimeApp` owns the current in-memory `AppSettings` used by the presentation shell. Theme and interface-language changes are typed updates passed through `onSettingsChanged`; telemetry starts disabled and is not connected to a hidden reporting path. Persistence and backend wiring remain outside this presentation-only stage until their own approved boundaries are connected.

`ResponsiveAppShell` classifies the available logical width with the shared compact／medium／expanded breakpoints. Compact uses Material bottom navigation; medium and expanded use a NavigationRail with the same ordered `AppDestination` values. `buildWynimePage` maps each destination to a real product page and passes navigation callbacks only where a page has an explicit local action.

`WynimePageFrame` is the shared responsive page frame. It applies SafeArea, bounded scrolling, content max width, common spacing and page headers only where the window class has room. Home, Search, Library, Downloads, Sources and Settings render truthful empty, unavailable or review-required states. Search does not call a source service; it keeps a submitted query local until a future source package and application contract are explicitly connected. Source review remains disabled without a proposal, and no UI state represents a successful remote response merely because a page was opened.

Presentation tests cover compact navigation, local Search submission, Library filters, telemetry default-off and all four locale delegates. Fixed-size Goldens cover the four acceptance viewports. Real Android phone/tablet action evidence is required in addition to these tests; Windows launch/build evidence cannot substitute for observable mouse and keyboard interaction.

## Phase 12 release and security audit boundary

Release evidence is split into source/deterministic, runtime, hardware and legal/provenance classes. A passing analyzer, test suite or platform build proves only the first class. APK metadata and signature inspection, Windows bundle inventory and native archive hashes are recorded separately; an unsigned release APK is a packaging artifact for inspection, not a publishable release. A release signing configuration may consume only explicitly supplied external keystore properties or environment variables and must never fall back to the Android debug key.

All local download and remux mutations are confined to one configured download root. Before creating missing parents, the filesystem adapter finds and validates the nearest existing ancestor, rejects link/junction traversal outside the root, rechecks canonical containment and accepts only regular files for writes, promotion and deletion. Delete jobs consume the persisted `DownloadArtifactManifest`; orphan discovery is report-only and never grants deletion authority. The FFmpeg adapter receives a bounded vector of local file arguments, starts with `runInShell: false`, caps process diagnostics and timeout, and fails closed for non-local, outside-root, linked or missing paths.

Persisted HLS recovery data is a structural redacted snapshot: playlist kind, bounded counts, durations, sequence values, segment flags, byte ranges and key/map presence are retained, while complete resource URIs, query strings, credentials, cookies and tokens are omitted. This preserves recovery identity without turning local persistence into an upstream-secret store. The same boundary applies to diagnostics and source-package proposals.

Phase 12 native provenance remains a release prerequisite. The locked media-kit packages and downloaded archive hashes are evidence of the selected inputs, not proof of the libmpv／FFmpeg／ANGLE build flags, linked dependency closure or redistributed licenses. Until those records are supplied and reviewed, native packaging remains `RELEASE_BLOCKED`; until Windows interaction and supported hardware playback are exercised, runtime status remains `BLOCKED_UI_ENVIRONMENT` and `prototype_not_hardware_validated` respectively.
