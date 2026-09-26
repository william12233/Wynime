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

### Source Package schema versions 1 and 2

A package declares identity, semantic version, compatible Wynime version range, security policy, bounded declarative programs and optional Ed25519-shaped signature metadata. Unknown JSON keys and unsupported selector types fail closed. Schema version 1 retains its existing fixture-only meaning and does not accept live-operation metadata.

Schema version 2 adds a bounded `liveOperations` list. Each operation is one
closed `search`, `episode` or `playableSource` binding to an existing package
program, an explicit field mapping used by the existing normalizer and a fixed
HTTP(S) URI template. It adds no executable package code, selector dialect or
second normalization model. The typed
`SourceLiveOperationPlanFactory` expands only operation-specific input
placeholders into existing live plan types; it performs no I/O and does not
choose a source or start playback.

### Source Package schema version 3: subject details and public capability authority

Schema version 3 is a strict package format for source packages that expose a
subject-first live flow. It requires the exact `cache` TTL object and public
`capabilities` object, accepts at most four live operations, and replaces the
standalone v2 `episode` binding with one `subjectDetails` binding plus an
optional `playableSource` binding. A `subjectDetails` binding points to one
metadata program and one episode-link program. `SourceLiveSubjectCoordinator`
performs one admitted GET, evaluates both already-declared programs against the
same bounded in-memory response, and passes them to the existing typed
normalizer. It does not fetch an iframe, execute package JavaScript, infer
headers or create a playback session.

The v3 cache keys are exactly `searchTtlSeconds`,
`subjectMetadataTtlSeconds`, `episodeListTtlSeconds` and
`playbackResolutionTtlSeconds`. TTLs are whole seconds bounded to seven days;
the runtime cache is in-memory only, bounded to 256 entries globally and 64
entries per source, and stores only successful cacheable results. Playback
refresh invalidates only the playback-resolution key. The public capability
object has exactly `search`, `detail`, `episodes` and `playback` states; it is
declarative authority for availability presentation and does not grant network
or playback permissions.

The first v3 package is `xifan` (`sources/xifan.wynsrc.json`). The post-1.0.12
package revision uses the public Next site search route, subject detail and
episode links, and reads the declared video `src` through the existing source
normalizer. The package allowlist contains only its declared HTTPS hosts,
including the public Next page host, declared media hosts and the exact
provider redirect port required by the current public route. Challenge,
unavailable, disabled, consent and incompatible outcomes remain typed and
secret-safe; no source JavaScript is executed.

Signature metadata is not cryptographic verification and never raises runtime authority. Signed and unsigned packages use identical allowlist, permission, consent and budget checks. `SourcePackageSignatureVerifier` optionally verifies the canonical UTF-8 JSON representation with the signature field omitted using Ed25519 and a package/key/signer-scoped trusted-key resolver. Its result is bounded identity/integrity evidence only; unsigned, untrusted, mismatched, invalid or resolver-failed results cannot install, enable, activate or execute a package.

### URI and consent policy

- URI schemes are limited to HTTPS and explicitly consented HTTP.
- Source-package live-operation templates permit only standard ports 443 and
  80. A domain rule defaults to those standard ports, but may explicitly
  declare a bounded exact `ports` set for a provider HTTPS redirect. Port
  ranges, wildcards and implicit non-standard ports remain unsupported.
- User-info URIs, localhost, `.localhost`, `.local`, IPv4 literals and deceptive suffix hosts are rejected.
- Host matching uses exact equality or a dot-boundary subdomain rule.
- Adding a permission or domain, enabling subdomains or broadening any resource budget requires fresh consent.
- Adding, removing or changing a schema-v2 or schema-v3 live-operation binding,
  cache policy or public capability requires fresh consent; this metadata is
  part of the signed canonical package representation.
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

### Live source HTTP request boundary

`SourceHttpRequest` is the first live source transport contract. The current
boundary intentionally accepts only an explicit, allowlisted HTTPS GET with
no request body, bounded headers, a bounded timeout and no credential or
hop-by-hop headers. `SourceLiveHttpRequestCoordinator` checks consent,
re-consent, enabled state, Wynime compatibility, program identity and exact
semantic policy equality before exposing the request. It never infers a URI
from a package ID or executes package content.

`DartIoSourceHttpTransport` reuses the existing direct,
public-address-pinned upstream connection path, disables implicit redirect
trust through manual redirect handling, rechecks the same package allowlist
for every target, enforces the package redirect and response-byte budgets, and
decodes only bounded UTF-8 text. It requests identity content encoding at the
transport boundary so a compressed wire body is not mistaken for invalid
source text. Successful response bodies remain in memory for a future
declarative evaluator; response headers are not retained. Failed results retain
only safe status/reason metadata and, when available, redacted response
evidence consisting of status, final scheme/host/path, redirect count, content
type, byte count, body classification and encoding. `SourceLiveHttpRequestExecutor`
joins admission to exactly one transport call and short-circuits every
non-ready admission. Static HTTP remains a GET-only path; browser-rendered
fallback is a separate typed WebView boundary below.

## Phase 3 WebView capture architecture

### Runtime and plugin boundary

Phase 3 uses `flutter_inappwebview` only under `lib/src/platform/web_capture`. Android resolves to the native Android WebView implementation and Windows resolves to the endorsed WebView2 implementation. `WebSourceBrowserPort` exposes runtime probing and scoped cookie operations without exposing plugin classes to Domain or Infrastructure.

A Windows machine without the WebView2 Runtime returns an explicit `webview2_runtime_missing` status. Runtime probe failures return `webview2_probe_failed`; neither state is converted into success.

### Capture request authority

`WebCaptureRequest` combines the Phase 2 `SourceSecurityPolicy` with:

- platform-default or explicitly permitted desktop user agent;
- bounded initial headers and cookies;
- event, candidate, header, cookie and optional document budgets;
- load-stop, first-playable-candidate or bounded document-after-load completion;
- explicit media-request inspection permission.

Every initial URI, navigation, iframe, resource, XHR and fetch target is checked against the same allowlist. Disallowed navigations are cancelled, disallowed resource requests receive an empty 403 response, and disallowed XHR/fetch requests are aborted.

For a user-enabled package that explicitly declares `mediaRequestInspection`,
one episode acquisition may additionally request bounded runtime media-origin
admission. The WebView must begin on a package-allowed origin; only a
non-navigation HTTPS request observed in that exact generation can receive an
in-memory `RuntimeMediaOriginGrant`. Admission rejects credentials, local,
private, link-local, multicast, documentation and other special-purpose IP
ranges after bounded DNS resolution. The grant contains one exact scheme,
host and port, the acquisition identity, source event sequence and expiry. It
is neither written back to the package allowlist nor persisted.

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

`WebCaptureAccumulator` stores bounded events, an explicitly requested bounded
document and deduplicated media candidates in memory only. Candidate
classification recognizes HLS, DASH, common direct audio/video files and media
segments using response content type, URL path or bounded media-request
evidence such as destination, Accept and Range headers. A document is captured only
after the fixed platform `getHtml()` call for a request whose completion policy
is `documentAfterLoad`; source packages cannot supply script to obtain it.

Diagnostic output contains scheme, host, path-segment count, method and header names only. Cookie values, Authorization values, query strings, fragments and complete media URLs are not logged or persisted. Phase 3 does not create a `PlaybackSession`; Phase 4 must validate and transform a chosen candidate through its own Gate.

The selected candidate carries its exact runtime grant and captured request
context through snapshot validation, route selection and session resolution.
`PlaybackSession`, bounded probing and the loopback proxy revalidate the same
acquisition-bound grant for the candidate, redirects, HLS children and scoped
cookies. A later episode or replay creates a new acquisition identity and
cannot reuse an earlier grant.

### Live capture admission and generation

`SourceLiveCapturePort` is the typed boundary for a platform/WebView capture
implementation. `SourceLiveCaptureRequest` binds one bounded
`WebCaptureRequest` to the exact source package version and program. The
Application-layer `SourceLiveCaptureCoordinator` revalidates every returned
`WebCaptureSnapshot` against that request's URI policy, permissions, event
ordering, candidate provenance and resource budgets before exposing it to the
next boundary. Incomplete or malformed snapshots, platform failures and
budget exhaustion become stable redacted result codes; raw URLs, headers,
cookies and exceptions do not cross the result diagnostic boundary.

Each capture owns a monotonically increasing generation. A newer capture
supersedes an older one, and closing the coordinator invalidates all pending
generations; late platform completions are therefore ignored and cannot
replace the current accepted result. The port and coordinator perform no
network transport, package execution, persistence, playback/session
resolution or UI work. The platform implementation is connected only through
`InAppWebViewSourceLiveCapturePort` and the reusable
`InAppWebViewSourceLiveCapture` surface described below; actual source
eligibility, package execution and live availability remain separate
evidence-bound work.

### InAppWebView live capture bridge

`InAppWebViewSourceLiveCapturePort` is the only platform-owned bridge from the
existing `InAppWebViewCaptureView` to `SourceLiveCapturePort`. One port capture
owns one keyed generation and one completion. A new capture completes the old
one as superseded; closing the port completes pending work as closed. Runtime
unavailability, fatal bootstrap/final-URI/cookie failures and capture-surface
construction failures complete the port with stable redacted errors. Ordinary
policy-blocked resource, XHR, fetch or download events remain blocked and are
reported as security notices without turning the whole capture into false
success or an indefinite pending operation.

`InAppWebViewSourceLiveCapture` mounts that port's keyed view, starts one
`SourceLiveCaptureCoordinator` operation, delivers at most one typed result,
and closes both authorities before an updated request or widget disposal can
publish a late result. The injectable view builder is test-only composition;
the production builder is `InAppWebViewCaptureView`. This bridge does not
connect Search, source-package execution, live HTTP outside WebView, cookie
persistence, `PlaybackSession` resolution or player lifecycle.

### Installed-package live-capture admission

`SourceLiveCapturePackageCoordinator` is the application handoff from an
`InstalledSourcePackage` to the existing live-capture coordinator. It checks
consent/re-consent before disabled state, exact Wynime compatibility and
package-owned program identity, then requires the supplied
`WebCaptureRequest` to carry the exact installed package security policy and an
allowlisted initial URI. Non-ready package states never contact the platform
port. A ready request preserves the package ID, version and program ID in the
existing `SourceLiveCaptureRequest`, after which generation, snapshot
validation, stale-result handling and close remain owned by
`SourceLiveCaptureCoordinator`.

This admission boundary does not execute declarative source rules, fetch live
HTTP outside WebView, activate packages, persist capture data, connect Search
or resolve playback. It makes package eligibility explicit without treating a
mounted WebView or a captured snapshot as proof of live source availability.

`InAppWebViewInstalledSourceLiveCapture` is the package-aware platform surface
for that handoff. It accepts an explicit `SourceLiveCapturePackagePlan`, emits
the typed package-admission result, and mounts the lower-level WebView capture
surface only when the admission result is ready. Replacing the plan or
disposing the widget closes the package coordinator and platform port before
the next generation can publish a result. A non-ready admission therefore
cannot construct the capture view or contact the platform capture operation.

`SourceLiveCaptureSnapshotValidator` is the shared pure revalidation authority
for downstream consumers of a capture result. It binds a snapshot to the
exact capture request and repeats URI, permission, ordering,
candidate-provenance, candidate-kind and resource-budget checks, using the
same pure `WebCaptureCandidateClassifier` as the platform accumulator, so an
in-memory captured value cannot be treated as an unverified capability at a
downstream boundary. It reconstructs the accumulator's first-observation
candidate list from the classifier's kind plus exact normalized URI key,
rechecks the reconstructed unique-candidate budget, and requires the supplied
candidate list to match it in cardinality, insertion order, kind, normalized
URI, headers and source-event sequence. A later duplicate, omission,
duplication, reordering or impossible over-budget completed snapshot is
therefore rejected. Rejected validation retains only a bounded reason code.

`SourceLiveCapturePlayableSourceCoordinator` is the next pure Application
boundary after that surface. It consumes the exact admitted
`SourceLiveCaptureRequest` and `SourceLiveCaptureResult` pair, rechecks package
and episode identity plus the shared snapshot validator, and requires an
explicit source-key and label mapping for every captured candidate. Supported
HLS and direct audio/video candidates become
`SourceLiveCapturePlayableSource` values that retain the original
`WebMediaCandidate` (including its event sequence and ephemeral headers),
while the accepted capture snapshot retains cookies once for the later session
handoff. DASH and media-segment candidates are diagnosed and never become
playable outputs. Candidate provenance must point to the accumulator's first
classified observation for its kind and normalized URI; later duplicate
observations fail closed. Failed results retain neither candidate nor snapshot
data.
This boundary does not select a route, resolve a session, persist capture
state, perform source HTTP or start a player.

## Phase 4 playback architecture

### Authoritative session resolution

A selected `WebMediaCandidate` is converted once into a `PlaybackSession`. The resolver rejects segment-only and deferred DASH candidates, reuses the Phase 2 `SourceSecurityPolicy`, separates Referer／Origin／User-Agent from generic headers, and includes only cookies valid for the candidate host, path, security scheme and expiry. Refresh callbacks may replace expiring URLs and authority values but must preserve `sessionId` and the complete source／line／subject／episode identity.

### Loopback proxy capability model

`LoopbackPlaybackProxyService` binds only `127.0.0.1` or `::1` on an ephemeral port. Each lease creates an unguessable capability token and opaque resource IDs. The native player receives only the loopback URI; upstream media URLs, query tokens, cookies and authorization values remain inside the proxy session.

Every initial URI, redirect and HLS child URI must pass the same source allowlist. The production upstream client performs explicit IPv4 and IPv6 public-address DNS preflight, repeats validation inside the `HttpClient.connectionFactory`, and opens the socket directly to that validated numeric address. HTTPS then upgrades the connected socket with the original hostname for TLS validation. This rejects loopback, link-local, private, carrier-grade NAT, documentation, multicast, IPv4-embedded private and standard NAT64-private ranges without leaving a second hostname lookup between validation and connection. The proxy is therefore not a general URL forwarder.

### Forwarding and resource budgets

The proxy accepts only GET and HEAD plus one syntactically valid byte range. It forwards the authoritative session headers, scoped cookies, Referer, Origin and User-Agent under independent byte budgets. Redirect count, playlist bytes, total response bytes and registered HLS resources are bounded. When a progressive media response without an incoming Range explicitly declares a size above the response budget, the proxy cancels that response and retries once with a bounded initial Range; HLS playlists are never given this media fallback. Upstream Set-Cookie and redirect Location are never exposed downstream. Closing a lease invalidates its capability and cancels active body subscriptions; service shutdown closes all leases, the listener and the upstream client.

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

Drift schema version 4 stores local collection status and remote revision, watched episode rows, an account-scoped calendar cache with its last-refresh timestamp, manual local-to-Bangumi subject mappings and `BangumiSyncOperation` rows. Operations use `pending`, `retryWaiting`, `conflict` and `blocked`; the v1.0.6 `failed` state is migration-only. Each operation retains its target-field base value and the latest safe HTTP status diagnostic. The sync service updates local state immediately for offline use, then persists a coalesced queued operation. Automatic selection respects `nextAttemptAt`, while manual synchronization can force unfinished retry rows. Retryable network, timeout, provider, payload and 429／5xx failures remain retryable after any foreground attempt limit, with capped exponential backoff and jitter.

Every unfinished operation reads the applicable Bangumi state before mutation. If the target already matches, the operation completes without a write; an independent change to another target field is merged, while an incompatible target change becomes a visible conflict. Successful mutations are followed by a bounded read-after-write verification sequence before completion. Mutation calls always declare the JSON content type, and a local redacted diagnostic distinguishes request contract failures such as HTTP 415 without exposing identifiers or response bodies. A new local intent can reuse only a structurally valid blocked HTTP 415 row and rebases its target-field base values to the current cache, preventing a stale failed row from creating a duplicate or stale write. An explicit collection refresh fetches the complete paged membership snapshot and atomically removes absent cached rows, except for subjects with an active local-first collection intent. It also refreshes remote state for already-open detail subjects; unopened subjects fetch exact watched IDs when their detail route opens, avoiding an unbounded per-collection request fan-out. Blocked and legacy failed rows never participate in local-first overlays. A reauthentication-required session may still render the cached calendar, but the presentation labels it as cached rather than current.

## Phase 10 automatic source builder

Phase 10 receives only bounded `SourceBuilderObservation` fixtures and explicit field hints. The builder parses observations offline and searches for repeated structures that can be expressed in the Phase 2 declarative dialect. HTML output is restricted to generated tag/id/class CSS selectors; JSON output is restricted to generated property and wildcard JSONPath expressions. XPath, executable package content, arbitrary selectors and unverified guesses are never emitted.

The builder creates a candidate `SourcePackageManifest`, then evaluates it with the existing fixture rule evaluator against every observation before returning it. The proposal contains a deterministic fingerprint of package structure only; raw observation bodies, expected values, cookies, tokens and complete media URLs are not retained in the proposal or diagnostics. A missing example or mismatch produces a rejected proposal.

Security policy is copied from the previous package when present. An observed domain outside that policy can be included only through an explicit builder option and is marked for re-consent. HTTP requires an explicit opt-in and the `insecureHttp` permission; no permission is inferred from page content. Resource budgets remain bounded and any broader update requires re-consent.

Every successful result remains in `reviewRequired` state and `canActivate` is always false until `SourcePackageActivationService` receives the exact proposal ID, explicit user approval and any required re-consent. Signatures remain metadata and do not increase runtime authority.

`SourcePackageLiveOperation` is the only schema-v2 live authority. Its URI
template has a fixed public DNS authority and HTTP(S) scheme, rejects user-info,
fragments, undeclared non-standard ports and local/IP hosts, and allows placeholders only
in path/query text. Expansion percent-encodes each bounded input component and
the resulting URI is passed through the installed package's exact security
policy and `SourceLiveHttpRequestCoordinator` before any transport call.
Operation-specific mappings must reference fields in the selected program;
missing/duplicate operations, unknown programs, unsupported placeholders and
all malformed declarations fail at decode/manifest validation. Rejected
factory results retain only safe status/reason tokens, while the exact ready
package, policy, program and request provenance are carried by the existing
plan types.

`DeclarativeSourcePackageManager` is the deterministic lifecycle boundary for
 decoded schema-v1/v2 packages. It owns one in-memory record per `packageId`,
returns sorted immutable snapshots, and accepts only packages compatible with
the current Wynime version. A first install and every ordinary update remain
disabled and consent-pending; an update must be strictly newer and replaces
 the previous record atomically. A broader security policy or changed live
 operation authority is marked for re-consent, and a package-version guard
 rejects stale enable／disable actions
so an old UI snapshot cannot mutate a replacement package. Enabling clears
only the consent flags that were explicitly satisfied; disabling never deletes
the installed manifest. Builder proposals use a separate manager entry point
that maps the activation service's exact proposal-ID, approval and re-consent
checks into the same stable manager error boundary before enabling atomically.

This manager remains an in-memory, no-I/O lifecycle authority. It does not
discover GitHub folders, download packages, verify publisher signatures or
execute live source requests. `SourcePackageRepository` is the separate typed
durable-state boundary. `DriftSourcePackageRepository` stores the complete
 schema-v1/v2 manifest and consent/status flags in the v6 SQLite schema through one
transactional snapshot replacement. `PersistentSourcePackageManager` restores
the full snapshot only after decoding and revalidating every record, serializes
mutations, and restores the prior in-memory snapshot if a commit fails. The
repository does not grant registry trust or signature authority. At app
bootstrap, `SourcePackageStartupController` creates the persistent facade once,
awaits its durable snapshot load, and publishes idle／loading／ready／failed
state with a bounded diagnostic code. `ResponsiveAppShell` observes that
controller and passes its immutable installed snapshot to the Sources page.
Explicit Install／Update actions stage a validated manifest as disabled and
consent-pending; they never enable or execute it. Enable and Disable actions
use the exact package ID and version, serialize through the persistent manager,
and notify the shell only after durable replacement succeeds. Enabling is
preceded by a user-visible review of the manifest's allowlisted domains,
explicit permissions and resource budget; stale versions, repository failures
and close races remain typed and fail closed. The page never fetches artifacts
itself, verifies publisher signatures or treats a catalog response as
execution authority.

`SourceRegistryIndex` is the strict, offline schema-v1 contract for a future
source-package repository. It contains one bounded entry per package with an
opaque snapshot revision, a relative package path below a dedicated source
root, the package version and a normalized SHA-256 digest. The decoder rejects
unknown keys, duplicate IDs or paths, traversal/absolute paths, unsupported
versions and over-budget indexes. Its byte-oriented decoder rejects malformed
UTF-8 before parsing. `SourceRegistryPackageVerifier` hashes the supplied raw
package bytes, then strictly decodes and binds the manifest package ID and
exact version text to the entry before returning it; the String overload is
only a UTF-8 convenience wrapper. This is an integrity boundary only: it does
not select a GitHub repository or branch, access the filesystem or network,
verify Ed25519 publisher signatures, or activate a package; the ordinary
manager consent and security-policy checks remain authoritative.

`SourceRegistryArtifactCatalogLoader` is the bounded offline composition point
between that index and a future transport adapter. It accepts the raw index
bytes plus an exact full-relative-path-to-package-bytes map, rejects missing or
unindexed artifacts, verifies every package before returning an immutable
all-or-nothing catalog, and retains no raw artifact bytes. It does not install,
enable, persist, access the filesystem or network, or grant repository trust.

`GitHubSourceRegistryRepository` is the read-only transport adapter for one
configured owner, repository, ref and index path. It constructs only HTTPS
URLs on `raw.githubusercontent.com`, rejects traversal and unsafe repository
configuration, disables redirects and credentials, applies per-response and
total-package byte budgets, and fetches the decoded index's exact artifacts in
order. Concurrent loads share one in-flight snapshot and closing the adapter
prevents new work or late catalog composition. The adapter passes the raw bytes
through `SourceRegistryArtifactCatalogLoader` and never installs, persists,
enables or activates a package. It is not wired into startup or the Sources
page directly; the application composition root supplies it to
`SourceRegistryController`. It does not verify publisher signatures or grant a
verified digest repository trust.

`StagedSourceRegistryRepository` is the pre-release companion adapter for one
OS-assigned app-support directory. It reads only the configured relative index
and the exact artifact paths declared by that index, rejects symbolic-link or
canonical-root escapes, applies the same per-artifact and total-byte bounds,
and passes the exact bytes through `SourceRegistryArtifactCatalogLoader`.
It never creates or modifies staged files and never installs, persists,
enables or executes a package. The application composition root may select it
only when `kDebugMode` and the explicit compile-time staged flag are both true;
release composition always selects the fixed GitHub registry configuration and
cannot select the local staged root or any debug harness.

`SourcePackageSignatureVerifier` is the optional cryptographic boundary for
publisher identity evidence. It signs and verifies the canonical package JSON
without the recursive `signature` member, requires an Ed25519 public key from
an external resolver with exact package ID, key ID and signer ID matching, and
maps lookup, timeout, malformed-signature, payload and cryptographic failures
to bounded diagnostic codes. The resolver and verifier retain no package or
credential state, and a verified result does not alter registry integrity,
package allowlists, permissions, consent, lifecycle state or runtime
authority. The current startup, Sources UI and GitHub repository paths do not
implicitly invoke this optional verifier.

`SourceRegistryController` is the Application presentation boundary for an
optional compile-time configured registry repository (the fixed GitHub adapter
in release composition or the explicitly debug-only staged adapter). It owns one
immutable read-only catalog snapshot, exposes idle/loading/ready/failed states,
shares concurrent initialization or refresh calls, replaces snapshots only
after the complete catalog has passed index, artifact, identity and SHA-256
validation, and ignores late completion after disposal. `WynimeApp` starts the
optional read in the background and closes the controller with the app; the
Sources page observes it and shows the registry revision, verified artifact
identity and installed/update/not-installed comparison. Refresh is the only
remote UI action and only re-reads the configured snapshot. No catalog item
has an install, enable, activate or execute action, and the controller never
becomes package lifecycle or publisher-signature authority. Invalid or absent
compile-time configuration leaves the optional registry disconnected.

`SourcePackageRuntime` is the next typed boundary below the manager. Its
current `DeclarativeSourcePackageRuntime.executeFixture` operation accepts
only an enabled, consent-complete, Wynime-compatible installed package and an
exact program ID. It delegates to the canonical fixture rule engine, returns
immutable generic records and bounded redacted diagnostics, and classifies
disabled, consent-required, incompatible, available, not-found and failed
states without exposing parser or security exception text. This proves the
shared executor contract without claiming HTTP, GitHub registry, WebView,
source-specific hard-coding or normalized application UI integration.

`SourceLiveHttpPackageRuntime` is the narrow asynchronous composition above
the bounded live HTTP executor and the existing `SourcePackageRuntime`. It
passes only a completed, package-admitted `SourceHttpResponse` into one
in-memory `SourceFixture`, using the explicit request URI as the fixture's
initial URI and preserving the bounded redirect chain. It returns only the
existing immutable `SourceRuntimeResult`; response body, response metadata and
headers do not escape this composition. Admission and transport failures are
mapped to safe typed runtime statuses with no records, evaluator exceptions
are collapsed, and a result whose package/version/program identity does not
match the request plan fails closed. This establishes the first live-to-rule
composition without request inference, provider behavior, Search UI, WebView,
normalization, persistence, retry or playback authority.

`SourceLiveSearchCoordinator` is the next asynchronous application boundary.
It accepts a bounded list of explicit `SourceLiveSearchPlan` values, each
pairing one TASK-044 live request plan with one search field mapping. It runs
the plans in caller order, passes each typed runtime result through the
existing `SourceSearchNormalizer`, preserves per-source status and aggregates
only normalized results using the existing search result contract. Query text
is an aggregate input only; this boundary never edits or infers a request.
Plan-count and duplicate-identity limits are checked before I/O. A newer
search generation supersedes an older pending result, and `close` invalidates
pending/future results without becoming the lower transport's cancellation or
close authority. Invalid normalizer identity/shape, runtime exceptions and
non-available source states remain typed failures with no fabricated records.
When static execution returns `notFound`, an optional
`SourceLiveSearchDocumentFallback` may receive the same plan and return only a
typed `SourceRuntimeResult`. The production fallback builds an exact package
policy/request capture, uses the fixed native document bridge, and evaluates
the in-memory body through `SourceLiveDocumentPackageRuntime`; it is not
invoked for transport, parser or runtime failures. The coordinator still does
not own Search UI, provider behavior, registry, WebView, persistence, retry or
playback lifecycle.

`SourceInstalledLiveSearchPipeline` is the installed-package composition above
that coordinator. It snapshots the caller's installed-package authority once,
in caller order, before any live search transport and bounds the snapshot to 32
unique `(packageId, version)` identities. It passes every package through the
TASK-053 `SourceLiveOperationPlanFactory` with the exact query; only factory
ready plans enter one invocation of `SourceLiveSearchCoordinator`. Each factory
result remains visible as a typed per-package outcome, so disabled,
consent-pending, incompatible, undeclared or otherwise rejected packages cannot
be mistaken for successful sources while admitted packages continue searching.
The wrapper preserves the coordinator's exact normalized results and downstream
status, marks package-preflight coexistence as partial without changing the
downstream result, and returns a typed no-usable-sources result with zero
transport when no plan is admitted. It delegates generation, stale-response,
close and transport lifecycle to the existing coordinator and adds no retry,
timeout, persistence, provider or cross-source ranking authority. App bootstrap
owns the optional WebView fallback and SearchPage mounts its bounded capture
surface only while a fallback generation is pending.

`SourceLiveSubjectCoordinator` applies the same boundary to schema-v3 subject
details. It performs the static one-GET/two-program evaluation first; when a
typed metadata or episode result is `notFound`, an optional
`SourceLiveSubjectDocumentFallback` may capture one bounded rendered document
and return typed runtime results for both declared programs. The fallback
shares the exact package admission, policy, generation and document-runtime
rules, and the subject detail route mounts its platform capture surface only
while that generation is pending. Transport, challenge, parser and other
runtime failures do not trigger browser fallback, and neither coordinator
creates a second playback/session lifecycle.

`SourceSearchPresentationController` is the sole Presentation adapter above
`SourceInstalledLiveSearchPipeline`. It receives one application search
operation and the existing installed-package snapshot provider, submits one
trimmed query per user action, and projects only the pipeline's typed status,
normalized result order and package display provenance into immutable UI state.
It owns only a presentation request identity, so a newer query or disposed
Search surface cannot be overwritten by a late completion; it never closes the
shared pipeline. `SearchPage` renders deterministic idle, loading, available,
partial, not-found, no-source, no-usable-source, invalid-query and safe-failure
states with retry, while keeping request URIs, headers, cookies, tokens,
response bodies, diagnostics and raw exceptions out of the widget tree. The
application bootstrap constructs one live-search pipeline from the accepted
transport/runtime/factory/coordinator/normalizer chain and closes that shared
I/O owner with the app lifecycle. Presentation adds no package registry,
source fan-out, matching, ranking, network or playback authority.

`SourceInstalledLiveEpisodePipeline` is the corresponding composition from a
bounded caller-ordered iterable of exact `SourceInstalledLiveEpisodeTarget`
values. Each target retains one exact `InstalledSourcePackage` and complete
`SourceEpisodeIdentity`; the pipeline snapshots at most 32 collision-resistant
package/version/source/line/subject/episode identities before invoking the
TASK-053 `SourceLiveOperationPlanFactory`. Every snapped target retains its
typed factory outcome, while only exact ready `SourceLiveEpisodePlan` references
enter one existing `SourceLiveEpisodeCoordinator` invocation. Package-level
preflight rejection remains visible and may make an otherwise available or
not-found aggregate partial, but never turns a downstream failed or not-found
operation into success. The pipeline owns no identity synthesis, matching,
merge, ranking, retry, timeout, persistence, UI, provider or HTTP lifecycle;
generation, stale suppression, close invalidation, runtime and normalization
remain authoritative in `SourceLiveEpisodeCoordinator` and its lower layers.

`SourceInstalledLivePlaybackPipeline` is the corresponding composition from
the same bounded caller-ordered exact installed-package/episode targets into
the accepted `SourceLivePlaybackPipeline`. It snapshots at most 32
collision-resistant package/version/source/line/subject/episode identities
before invoking the TASK-053 `SourceLiveOperationPlanFactory` for
`playableSource`; every target retains its typed factory result, while only
exact ready `SourceLivePlayableSourcePlan` references enter one TASK-052
`openLive` invocation. The wrapper forwards all existing playback options
unchanged and retains the exact downstream playback result, including the
exact `PlaybackSession` on success or the typed stage failure on rejection.
Rejected package preflight remains visible and can make an opened aggregate
partial without invalidating the valid session. A caller-provided close
delegate invokes only the existing TASK-052/lower lifecycle owners; this
composition adds no generation, stale token, cancellation, timeout, retry,
fallback, session, proxy, player, transport, persistence, UI, provider or
identity-matching authority. Diagnostics expose only bounded package/version,
operation/program, status/count/stage and safe reason tokens.

`SourceLiveEpisodeCoordinator` is the corresponding asynchronous application
boundary for episode listings. It accepts a bounded list of explicit
`SourceLiveEpisodePlan` values, each pairing one live request plan with one
episode field mapping, executes them in caller order through
`SourceLiveHttpPackageRuntime`, and passes each result once through the
existing `SourceEpisodeNormalizer`. It preserves source-local typed states and
aggregates only normalized episodes through the existing episode result
contract. Plan-count and duplicate-identity limits are checked before I/O; a
newer listing supersedes an older pending result and `close` invalidates
pending/future results without taking ownership of lower transport lifecycle.
Invalid normalizer identity/shape and exceptions fail closed with no fabricated
episodes. This does not connect episode UI, Search UI, provider behavior,
registry, WebView, persistence, retry or playback.

`SourceSearchNormalizer` is the next typed boundary above the runtime. Its
fixture-only declarative implementation accepts an explicit subject-ID/title
field mapping and converts only available generic records into immutable
`SourceSearchResult` values. The package identity is authoritative, duplicate
subject IDs are removed deterministically, invalid or over-limit fields are
discarded with bounded generic diagnostics, and runtime states other than
available never expose records. This establishes the shared normalized search
contract without connecting Search UI, live HTTP, GitHub registry,
WebView, playback resolution or provider-specific code.

`SourceSearchCoordinator` composes explicit installed-package plans through
the fixture-only runtime and normalizer in caller order. It preflights
disabled, consent-pending and Wynime-incompatible packages without invoking
the runtime, preserves one typed normalization result per source, aggregates
only normalized rows and returns bounded available, partial, not-found,
no-sources or failed states with safe reason codes. It has no mutable async
state, so it cannot claim stale-response protection for a future live
transport; that generation/race boundary must be added with the asynchronous
source transport rather than inferred here. The coordinator performs no I/O,
does not mutate package lifecycle, and remains disconnected from Search UI,
HTTP, GitHub registry transport, WebView, playback or provider-specific code.

`SourceEpisodeNormalizer` follows the same boundary for episode listings. It
requires an explicit four-field mapping for line, subject, episode and title,
builds the existing `SourceEpisodeIdentity` with the package-owned source ID,
keeps valid rows in source order and removes duplicate full identities. Missing,
over-limit or control-character values are discarded with bounded generic
diagnostics; malformed package or program identities fail closed; and
non-available runtime states never expose episode records. This remains a pure
fixture transformation and does not resolve playback URLs or connect a source
adapter, coordinator, UI, network, registry or WebView.

`SourceEpisodeCoordinator` composes explicit episode-listing plans through the
fixture-only runtime and `SourceEpisodeNormalizer` in caller order. It
preflights consent/re-consent before disabled status, then rejects
Wynime-incompatible packages without invoking the runtime; eligible plans
produce one typed source result each, and only normalized `SourceEpisode`
identities are aggregated. Its bounded available, partial, not-found,
no-sources and failed states carry stable safe reason codes. The coordinator
has no mutable async state, so stale-response generation protection remains a
future asynchronous live-source boundary; it performs no I/O, persistence,
package mutation, WebView, playback or session work.

`SourcePlayableSourceNormalizer` is the next fixture-only boundary from a
playback-extraction runtime program to a normalized playable-source list. It
requires the exact package manifest, runtime package/version identity, the
typed episode identity and an explicit five-field mapping for source key,
label, candidate kind, media URI and page URI; every mapped field must also be
declared by the exact playback program. The package security policy validates
both HTTP(S) URIs; only HLS and direct audio/video candidates are
emitted, while DASH and media-segment records fail closed. Valid rows retain
source order and duplicate source keys keep the first row. The output carries
only ephemeral media/page URI values and no headers, cookies or playback
session; its diagnostics redact query strings and complete URLs. It performs
no I/O, persistence, WebView interaction, source-specific branching or
`PlaybackSession` creation. The existing playback-session resolver remains the
single authority that turns a later selected candidate into one
`PlaybackSession`.

`SourcePlayableSourceCoordinator` composes explicit playable-source plans
through the fixture-only runtime and normalizer in caller order. Each plan
binds one installed package, declared program, already-resolved
`SourceEpisodeIdentity`, fixture and five-field mapping; staged consent,
disabled status and Wynime incompatibility are rejected before runtime
execution. Only normalized candidates whose package/version, episode identity,
supported kind and package URI policy still match the plan are aggregated.
The coordinator returns bounded available, partial, not-found, no-sources or
failed states with safe reason codes. It has no mutable asynchronous state and
therefore does not claim stale-response protection; it performs no I/O,
persistence, WebView, route selection, playback or session work.

`SourceLivePlayableSourceCoordinator` is the asynchronous live counterpart for
playable-source listing. It accepts at most 32 explicit plans, each pairing a
package-admitted `SourceLiveHttpRequestPlan` with an already-resolved
`SourceEpisodeIdentity` and five-field playable mapping, then evaluates plans
sequentially through `SourceLiveHttpPackageRuntime` and the existing
`SourcePlayableSourceNormalizer`. It preserves per-source states and caller
order, validates package/version/program and candidate episode/kind/URI policy
identity again before aggregation, and returns the existing bounded
`SourcePlayableSourceCoordinatorResult` contract. Invalid explicit episode
identity, duplicate plan identity, normalizer identity/shape, candidate policy
or normalizer exceptions fail closed without candidates. A newer listing
supersedes an older pending result and `close` invalidates pending/future
results; the coordinator does not close or cancel the lower transport and
performs no route, session, player, persistence, WebView or provider work.

`SourceLivePlaybackRouteCoordinator` is the live HTTP route handoff from that
aggregate. It accepts the same bounded, caller-ordered
`SourceLivePlayableSourcePlan` list that produced the
`SourcePlayableSourceCoordinatorResult`, requires one matching normalized
result per plan, and rechecks package lifecycle, Wynime compatibility, declared
program, exact request policy, episode identity, supported HLS/direct
audio-video kind, package URI policy and the aggregate's flattened candidate
shape. It delegates the actual source choice to the existing
`SourcePlaybackRouteCoordinator`, so exact package/version/program/source-key
preference and no-fallback semantics remain single-sourced. A selected live
route retains the exact installed package and bounded GET request for the next
handoff, but no live response body, mapping, cookie or token. This coordinator
is pure and synchronous; it performs no source I/O, persistence, session
resolution, player work or asynchronous generation management.

`SourceLivePlaybackSessionRequestCoordinator` is the live HTTP handoff from a
selected `SourceLivePlaybackRoute` to the existing
`PlaybackSessionResolutionRequest`. It rechecks the installed package's
consent/re-consent, enabled lifecycle and Wynime compatibility gates, declared
program, exact package/version/episode identity and the package-admitted GET
request policy before delegating route conversion to the existing
`SourcePlaybackSessionRequestBuilder`. The explicit non-negative source-event
sequence is forwarded to that builder; because this path has no WebView event
or cookie authority, the resulting request must retain the exact live route
episode/page/media/kind fields with empty candidate headers, cookies and
user-agent. A ready builder result is rechecked for those exact fields and the
same `AdRemovalPlan`; non-selected routes, builder rejection, forged ready
requests and exceptions return bounded typed results. This coordinator is
pure and creates no session, invokes no resolver, performs no source I/O,
persistence, proxy, player or async lifecycle work.

`SourceLivePlaybackOpenRequestCoordinator` is the live HTTP handoff from a
ready `SourceLivePlaybackSessionRequestResult` to the existing
`PlaybackOpenRequest` contract. It preserves the exact resolver request and
caller-provided proxy budget, loopback family, refresh leeway, bounded
automatic-refresh count, optional episode duration and optional Bangumi target.
Non-ready live session results retain their live session, route and builder
statuses without creating an open request; invalid open options remain typed
failures. This coordinator is pure and synchronous, and performs no resolver,
session, proxy lease, player, source I/O, persistence, UI or asynchronous
lifecycle work.

`SourceLivePlaybackPreparedRequestOpener` is the final live HTTP handoff from
that typed open-request result to the existing `PlaybackCoordinator`. Its
concrete `PlaybackCoordinatorLivePlaybackPreparedRequestOpener` short-circuits
every non-ready result, retaining live session, route and builder statuses, and
passes only the exact ready `PlaybackOpenRequest` to `PlaybackCoordinator.open`.
The existing coordinator remains the sole authority for session resolution,
proxy exposure, player/progress lifecycle, operation serialization and stale
events; this opener adds no second lifecycle, retry, persistence, I/O or
platform bridge.

`SourceLivePlaybackPipeline` composes the explicit live HTTP path from a
bounded, caller-ordered snapshot of `SourceLivePlayableSourcePlan` values
through playable-source listing, live route selection, live session-request
building, open-request building and the prepared-request opener. It accepts
both complete and partial playable aggregates so a later valid source can be
selected after an earlier source failure, while exact package/version/program/
source-key preference remains the existing route authority's decision. The
caller supplies the non-negative source-event sequence required by the shared
request-builder contract; the pipeline does not invent WebView capture data.
Each non-ready boundary short-circuits with one bounded typed failure and no
resolver, proxy or player work. The pipeline owns no response persistence,
retry, session, proxy, player, progress or stale-operation state: live listing
supersession remains in `SourceLivePlayableSourceCoordinator` and playback
operation authority remains in `PlaybackCoordinator`.

`SourcePlaybackRouteCoordinator` composes the resulting source-local
normalization groups through the existing deterministic route selector. An
available group supplies its own exact package-owned episode identity to the
selector, so different package `sourceId` values are never synthesized into a
single cross-provider identity. With no preference it keeps the first
selected route in source order while preserving per-source selection results;
an explicit preference binds package, version, program and source key and
never falls back to another package. It returns only a typed route-selection
outcome and does not resolve a session, start a player, perform I/O or own
async generation state.

`DeterministicSourcePlaybackRouteSelector` is the Application-layer selection
boundary above that normalized list. It accepts only an available result whose
candidate identities all match the requested episode and package, selects an
exact preferred source key when one is provided, or otherwise selects the
first source in normalized order. A missing preference never silently falls
back, and a direct DASH/segment candidate or mixed identity fails closed. The
selector returns a typed `SourcePlaybackRoute` containing package/version and
program provenance, but does not create a `PlaybackSession`; the existing
resolver remains the only session authority.

`SourceLiveCapturePlaybackRouteCoordinator` is the separate live-only route
handoff from an accepted `SourceLiveCapturePlayableSourceResult`. It preflights
the installed package again, requires exact package/version/program and
completed-capture identity, and revalidates the complete snapshot candidate list
against the ordered capture events using the shared candidate classifier. Each
candidate's source-event sequence must resolve to an event whose classified kind,
exact classifier-normalized URI text, headers and sequence match; the
first-observation candidate list must also match the expected candidate at the
same index, in cardinality, order and fields. A candidate carrying its own
fragment or otherwise non-normalized URI fails closed. It then verifies each
selected candidate against its snapshot index, episode/page identity, supported
kind and package URI policy. With no preference it selects the first live source; an
explicit preference must match package, version, program and source key exactly
and never falls back. The returned route retains the exact
`SourceLiveCapturePlayableSource` and the same accepted capture result reference
so candidate headers, event sequence and the one cookie snapshot remain
available to the next handoff without duplicating secrets. This coordinator is
pure and performs no source I/O, persistence, session resolution or player
work; the live session-request gate below is the next explicit boundary.

`SourceLiveCapturePlaybackSessionRequestCoordinator` is the live-only handoff
from that selected route to the existing `PlaybackSessionResolutionRequest`.
The available playable result and live route retain the exact
`SourceLiveCaptureRequest`/capture-result pair. This coordinator rechecks the
installed package lifecycle, compatibility, program identity, request policy,
initial URI, accepted completed snapshot and candidate provenance through the
shared snapshot validator. It then passes the exact captured candidate, its
headers and source-event sequence, the one captured cookie snapshot (the
existing resolver filters it for the media URI) and any explicit capture
user-agent to the resolver request. An ad-removal plan must
describe the same episode. Non-selected routes and every mismatch return a
bounded typed failure without a resolver request. It creates no
`PlaybackSession`, proxy lease, player, persistence, source I/O or second
lifecycle; `PlaybackSessionResolver` and the existing `PlaybackCoordinator`
remain the only downstream authorities.

`SourceLiveCapturePlaybackOpenRequestCoordinator` is the separate live-only
handoff from a ready `SourceLiveCapturePlaybackSessionRequestResult` to the
existing `PlaybackOpenRequest`. It preserves the exact live resolver request
and applies the same bounded loopback family, refresh leeway, automatic
refresh limit, optional episode duration, Bangumi episode mapping and proxy
budget options as the fixture path. Non-ready live session results and invalid
options remain typed failures. This coordinator creates no
`PlaybackSession`, resolver invocation, proxy lease, player, persistence,
source I/O or asynchronous generation state; a later live prepared-request
handoff remains responsible for passing only a ready request to
`PlaybackCoordinator`.

`SourceLiveCapturePlaybackPreparedRequestOpener` is the live-only prepared
request port after that open-request boundary. Its concrete
`PlaybackCoordinatorLiveCapturePreparedRequestOpener` short-circuits every
non-ready live result and passes only the exact ready `PlaybackOpenRequest` to
the existing `PlaybackCoordinator.open`. It creates no second resolver,
session, proxy, player, progress, persistence, retry or generation authority;
downstream stable playback errors and stale-operation checks remain owned by
`PlaybackCoordinator`.

`SourceLiveCapturePlayableSourcePlanCoordinator` is the pure handoff from one
package-aware `SourceLiveCapturePackagePlan`/admission and its completed
`SourceLiveCaptureResult` to the existing
`SourceLiveCapturePlayableSourcePlan`. It requires the ready admission request
to retain the exact package-plan `WebCaptureRequest`, checks package/program
identity and lifecycle again, requires an exact capture identity and an
episode whose source belongs to the package, and requires one explicit mapping
for every captured candidate. A non-ready admission or capture is returned as
a bounded typed result without retaining capture data; identity, policy,
episode and mapping mismatches fail closed. The coordinator performs no
capture, WebView, source I/O, package execution, normalization, routing,
session, player or persistence work, and upstream capture-generation/stale
result authority remains with the package/WebView surface.

`SourceLiveCapturePlaybackPipeline` is the explicit live-only composition from
an accepted capture request/result plan through playable-source normalization,
route selection, session-request construction, open-request construction and
the prepared-request opener above. It stops at the first non-ready typed stage
before playback work and returns only that stage's bounded status and reason.
The pipeline retains no capture snapshot or request in its result and owns no
source capture, network, persistence, resolver, proxy, player, lifecycle,
progress or generation state. An accepted capture plan remains the input
boundary; package admission and WebView capture continue to be supplied by
their existing authorities.

`DeterministicSourcePlaybackSessionRequestBuilder` remains the fixture-path
Application boundary from a selected route to that resolver. It requires the exact package
manifest, package version and declared program, rechecks the package allowlist
for both media and page URIs, and requires the supplied `AdRemovalPlan` to
describe the same episode. It creates one `PlaybackSessionResolutionRequest`
with the selected candidate and an explicitly supplied non-negative source
event sequence; normalized source packages do not contribute headers or
cookies, so the request carries neither. A typed result reports every rejected
identity, policy or input case without raw exceptions. The builder does not
call the resolver, create a session, persist state, perform I/O or connect a
player; the existing resolver remains the sole session-construction authority.

`SourcePlaybackSessionRequestCoordinator` composes a selected
`SourcePlaybackRouteCoordinatorResult` through that existing builder. It
short-circuits every non-selected route, forwards the exact package manifest,
`AdRemovalPlan` and source event sequence unchanged, and exposes typed route,
builder-rejection or safe-failure outcomes. The builder remains authoritative
for package, episode, policy and sequence validation; this coordinator does not
revalidate by inventing a second contract, call the resolver, create a
`PlaybackSession`, persist state, perform I/O or start a player.

`SourcePlaybackOpenRequestCoordinator` is the next pure Application boundary.
It accepts only the ready result from `SourcePlaybackSessionRequestCoordinator`
and composes its existing typed resolution request with the bounded
`PlaybackOpenRequest` options: loopback family, refresh leeway, automatic
refresh limit, optional episode duration, Bangumi episode mapping and proxy
budget. Non-ready session results and invalid options remain typed, while the
`PlaybackOpenRequest` constructor remains the final option invariant. This
coordinator creates only an open request; it does not resolve a session, expose
a proxy lease, start a player, persist state, perform I/O or own async state.

`SourcePlaybackPreparedRequestOpener` is the typed downstream handoff port for
that prepared value; `PlaybackCoordinatorPreparedRequestOpener` is its concrete
execution implementation. It short-circuits every non-ready
`SourcePlaybackOpenRequestCoordinatorResult`; only a ready
`PlaybackOpenRequest` reaches the existing `PlaybackCoordinator.open`. The
existing coordinator therefore remains the sole authority for session
resolution, proxy lease exposure, player/progress lifecycle, operation
serialization and stale-event handling. This opener adds no second lifecycle,
retry policy, persistence, I/O or platform bridge.

`DeterministicSourcePlaybackOpenRequestBuilder` composes that validated
resolver request with the existing `PlaybackOpenRequest` coordinator options.
It passes the authoritative proxy budget, loopback family, refresh limit,
episode duration and optional explicit Bangumi episode mapping through without
creating a second progress, sync or session policy. Invalid coordinator
options or a rejected session request produce a typed result with no open
request. This is a pure Application composition boundary; only
`PlaybackCoordinator.open` may resolve the request, expose a proxy lease and
start a player operation.

`PlaybackCoordinatorSourceOpener` invokes that coordinator only after the
source open-request builder returns a ready request. A rejected build returns
a typed result without invoking resolver, proxy or player work; coordinator
failures remain under the coordinator's existing stable error boundary. The
opener owns no generation, proxy lease, session, progress, retry or
persistence state, so concurrent opens and stale events remain governed by the
existing `PlaybackCoordinator` lifecycle.

`SourcePlaybackFixturePipeline` is the fixture-only Application composition
from an installed package and explicit `SourceFixture` through the staged
playable-source coordinator, route coordinator, session-request coordinator,
open-request coordinator and prepared-request opener. It creates one explicit
playable-source plan, short-circuits on the first non-ready typed stage and
exposes only that stage's bounded status and reason code. A ready request is
passed to the existing `PlaybackCoordinator` through the prepared opener, so
session resolution, proxy exposure, player/progress lifecycle, operation
serialization and stale-event handling remain authoritative there. The
pipeline owns no source network, WebView, live capture, persistence or second
playback lifecycle.

The reusable platform capture surface is intentionally adjacent to this
fixture pipeline rather than inside it. An explicitly enabled package plan may
be provided to `InAppWebViewInstalledSourceLiveCapture`, which performs package
admission before mounting `InAppWebViewSourceLiveCapture`; its accepted result
can then pass through playable normalization and the separate live route
handoff, then the live session-request, live open-request and live
prepared-request handoffs above. The prepared handoff is the only point where
the existing `PlaybackCoordinator.open` may be called; no source package can
provide executable WebView adapters.

`SourceLiveCapturePlaybackEntryPoint` is the live-only Application entry from
one package plan, package admission, completed capture result, explicit
episode and candidate mappings to the existing playback lifecycle. It first
delegates exact request/result pairing to
`SourceLiveCapturePlayableSourcePlanCoordinator`, then passes only its ready
plan to `SourceLiveCapturePlaybackPipeline`; a plan rejection never reaches
normalization, routing, session resolution, proxy exposure or player work.
The entry result retains only one `PlaybackSession` on success or one typed
plan/pipeline rejection on failure, with no ready plan, open request, capture
snapshot, cookie, header, URL or raw exception in a rejection. It owns no
WebView, source I/O, package execution, persistence, retry, lifecycle,
progress or generation state.

## Phase 11 presentation architecture

`WynimeApp` owns the current in-memory `AppSettings` used by the presentation shell. Theme and interface-language changes are typed updates passed through `onSettingsChanged`; telemetry starts disabled and is not connected to a hidden reporting path. Persistence and backend wiring remain outside this presentation-only stage until their own approved boundaries are connected.

`ResponsiveAppShell` classifies the available logical width with the shared compact／medium／expanded breakpoints. Compact uses Material bottom navigation; medium and expanded use a NavigationRail with the same ordered `AppDestination` values. `buildWynimePage` maps each destination to a real product page and passes navigation callbacks only where a page has an explicit local action.

`WynimePageFrame` is the shared responsive page frame. It applies SafeArea, bounded scrolling, content max width, common spacing and page headers only where the window class has room. Home, Search, Library, Downloads, Sources and Settings render truthful empty, unavailable or review-required states. Search does not call a source service; it keeps a submitted query local until a future source package and application contract are explicitly connected. Sources restores the persisted package snapshot at startup and, when an optional registry is configured, shows its verified catalog; explicit Install／Update stages only a consent-pending manifest, while the security review dialog gates Enable and Disable remains reversible. No UI state represents a successful remote source response or execution merely because a page was opened.

Presentation tests cover compact navigation, local Search submission, Library filters, telemetry default-off and all four locale delegates. Fixed-size Goldens cover the four acceptance viewports. Real Android phone/tablet action evidence is required in addition to these tests; Windows launch/build evidence cannot substitute for observable mouse and keyboard interaction.

### Installed-source playback presentation boundary

The production route is `Bangumi detail -> episode -> typed source-line
selector -> transient background acquisition -> PlaybackSession -> native
Media3/libmpv surface`. `PlayerPage` must not render an installed source's
website as the playback page. A source WebView or rendered-document fallback
may be mounted only as a bounded, non-interactive, semantics-excluded
acquisition host and must be torn down after its typed capture result is
delivered. The visible resolving, failure and playing states belong to Wynime;
the native `surfaceHost` remains the only visible playback surface.

The line selector consumes only `SourceSubjectLine` values. Every selection is
carried through the exact `SourceEpisodeIdentity` (`sourceId`, `lineId`,
`subjectId`, `episodeId`); switching lines stops the current coordinator
session before opening the selected identity and may restore only a bounded
non-negative position. No line switch may infer a path, reuse a different
source identity or create a second playback lifecycle. The debug live harness
uses the same hidden acquisition-host rule but remains debug-only evidence and
does not substitute for production detail-page interaction.

## Phase 12 release and security audit boundary

Release evidence is split into source/deterministic, runtime, external-validation and engineering-provenance classes. A passing analyzer, test suite or platform build proves only the first class. APK metadata and signature inspection, Windows bundle inventory and native archive hashes are recorded separately; an unsigned release APK is a packaging artifact for inspection, not a publishable release. A release signing configuration may consume only explicitly supplied external keystore properties or environment variables and must never fall back to the Android debug key.

All local download and remux mutations are confined to one configured download root. Before creating missing parents, the filesystem adapter finds and validates the nearest existing ancestor, rejects link/junction traversal outside the root, rechecks canonical containment and accepts only regular files for writes, promotion and deletion. Delete jobs consume the persisted `DownloadArtifactManifest`; orphan discovery is report-only and never grants deletion authority. The FFmpeg adapter receives a bounded vector of local file arguments, starts with `runInShell: false`, caps process diagnostics and timeout, and fails closed for non-local, outside-root, linked or missing paths.

Persisted HLS recovery data is a structural redacted snapshot: playlist kind, bounded counts, durations, sequence values, segment flags, byte ranges and key/map presence are retained, while complete resource URIs, query strings, credentials, cookies and tokens are omitted. This preserves recovery identity without turning local persistence into an upstream-secret store. The same boundary applies to diagnostics and source-package proposals.

Phase 12 native engineering provenance remains a release prerequisite. The
locked media-kit packages and downloaded archive hashes must be joined to the
libmpv／FFmpeg／ANGLE build flags, linked dependency references, packaged
binary hashes and shipped notices; this record is maintained in
`docs/THIRD_PARTY_PROVENANCE.md`. Windows interaction and supported hardware
playback remain separate external-validation statuses:
`WINDOWS_CUA_VALIDATION_UNAVAILABLE` and
`prototype_not_hardware_validated` when unavailable. They must be recorded
truthfully but do not by themselves change the machine release status under
ADR-025.

Future Windows delivery is portable ZIP-only. The archive includes the main
executable and a separate `wynime_update.exe` helper, but no standalone setup
installer. `SoftwareUpdateInstaller` verifies the release version, checksum
and bounded ZIP contents before preparing a sibling handoff directory. It
then asks `DatabaseRecoveryPort` to drain the shared repository write gate and
create a SQLite-consistent `VACUUM INTO` snapshot. No application write is
admitted between that snapshot and helper handoff. If starting the helper
fails, the gate is reopened and all updater-owned temporary paths are removed;
the native helper owns replacement, startup health and rollback after it has
started.
