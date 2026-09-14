# Wynime 1.0.10

Status: release candidate for the protected exact-SHA publication workflow.
This release records the completed Animeko → Wynime convergence sequence of
TASK-001 through TASK-057. The task list below describes the delivered
boundary; it does not claim an upstream provider, account, hardware playback
or native Windows action result that was not exercised.

## Platforms and distribution

- Android arm64-v8a APK: `wynime-1.0.10-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.10.zip`.
- Each public artifact has its matching `.sha256` sidecar.
- Windows publication is ZIP-only. There is no standalone `setup.exe`; the
  ZIP contains `wynime_update.exe` for same-disk staged update, verification,
  backup, replacement, health-marker checking and rollback/manual-update
  fallback.
- No universal APK or release AAB is published.

## Detailed 57-task convergence record

### TASK-001 — 收藏狀態同步與 HTTP 415 修復

Closed the collection-state mutation path from UI intent through local
persistence, durable queue, serializer, HTTP request and reconciliation. The
fix keeps rapid multi-item transitions correctly encoded, coalesces newer
intent, preserves truthful retry/error states and prevents an HTTP 415 from
being hidden as false success.

### TASK-002 — 觀看進度、集數觀看狀態與 Resume-on-open

Connected playback progress events to durable local watch progress, completion
policy, Bangumi watched intent, read-back synchronization, Continue Watching
and deterministic resume behavior. Local exact position remains device-local;
stale events, completion races, restart recovery, offline retry and account
isolation are bounded without inventing a second sync engine.

### TASK-003 — Bangumi authentication and API boundary

Established the typed Bangumi session, official HTTPS API and bounded parsing
boundary for calendar, subjects, episodes, collection and watched-episode
data. Access tokens remain memory-only, refresh material is protected by the
platform boundary, and malformed or unauthorized responses become safe typed
states.

### TASK-004 — OAuth callback and Android App Link recovery

Separated the provider callback `/oauth/callback` from the Android return path
`/oauth/app-callback`, preserved pending OAuth state across process recovery,
and kept denial/error redirects from re-entering the provider callback. The
App Link association is bound to the exact production package and signer
instead of a guessed or truncated fingerprint.

### TASK-005 — Bangumi sync, detail UI and update reliability

Closed the cumulative Bangumi reliability boundary: account-scoped local
state, retryWaiting/blocked reconciliation, pre-write and read-after-write
verification, conflict handling, responsive collection/subject presentation,
cached artwork/progress overlays and truthful update download/verify/handoff
states. Remote watched state never fabricates local playback position.

### TASK-006 — Bounded automatic source-builder proposals

Added observation-driven source proposal generation using only bounded HTML or
JSON samples and explicit expected fields. The builder emits the existing
declarative CSS/JSONPath dialect, replays the generated rules against supplied
observations and rejects XPath, executable code, guesses and over-budget data.

### TASK-007 — Declarative source-package lifecycle

Defined deterministic package install, update, enable and disable lifecycle
state. New or updated packages remain disabled and consent-pending until an
explicit decision; compatibility, version, domain, permission and resource
policy checks remain mandatory and signatures do not elevate authority.

### TASK-008 — Fixture source-package runtime

Connected enabled, consent-complete packages to the canonical bounded rule
executor for deterministic fixtures. Program lookup, status propagation,
partial/not-found handling, immutable records and secret-safe parse/security
failures are explicit; no source-provided Dart, JavaScript, WASM or native
executable is run.

### TASK-009 — Fixture search normalization

Added the explicit field-mapping boundary from generic fixture records to
immutable package-owned `SourceSearchResult` values. It validates identity and
shape, preserves source provenance and order, removes only deterministic
duplicates and redacts diagnostics.

### TASK-010 — Fixture episode normalization

Added the shared typed identity boundary for fixture episode rows. Explicit
subject/episode/source/line mapping, package provenance, full-identity
deduplication, bounded invalid-field handling and safe status propagation keep
episode identity from being guessed from display numbers.

### TASK-011 — Fixture playable-source normalization

Added package-policy validation for normalized playable candidates. Media and
page URIs must satisfy the package allowlist, supported kinds and exact
package/version/episode identity; duplicates and malformed fields fail closed
without creating a playback session.

### TASK-012 — Initial source-to-playback handoff contracts

Established the typed source playback handoff around deterministic route
selection, session-request construction and open-request construction. The
handoff preserves package, episode, source, ad-plan and event identity while
remaining separate from resolver, proxy, player and persistence side effects.

### TASK-013 — PlaybackCoordinator opener boundary

Added the coordinator opener that invokes the existing `PlaybackCoordinator`
only for a ready typed request. Rejected requests do not create resolver,
session, proxy or player work, and downstream playback failures remain typed
and secret-safe.

### TASK-014 — Fixture playback pipeline

Composed the fixture-only playable-source, route, session-request, open-request
and prepared-open stages with first-failure short-circuiting. The pipeline
does not perform live I/O, WebView capture or source-package mutation and
retains one existing playback lifecycle authority.

### TASK-015 — Durable source-package persistence

Added atomic schema-v1/v2 package snapshot persistence and restart restoration
through the existing Drift repository. Serialized mutations, corrupt-record
rejection, enable/consent preservation and failed-commit rollback prevent
partial lifecycle state.

### TASK-016 — Source registry index and package integrity

Added strict bounded registry-index decoding and exact package-byte integrity
verification. Package ID/version/path, SemVer, UTF-8, size and SHA-256
constraints are checked without turning registry transport or signatures into
runtime authority.

### TASK-017 — Atomic registry artifact catalog

Composed a complete registry index and exact artifact set into an immutable
catalog only after every package passes identity and digest verification.
Missing, extra, malformed or mismatched artifacts fail closed and raw package
bytes are not retained by the catalog.

### TASK-018 — Startup restoration and Sources foundation

Restored the persisted package snapshot before the Sources page is exposed and
added explicit lifecycle presentation states. Startup remains read-only and
does not implicitly fetch, install, enable or execute a package.

### TASK-019 — Fixed GitHub raw registry adapter

Connected the offline registry contract to a read-only fixed-host HTTPS
repository adapter. URL construction, fetch order, redirect refusal, response
and aggregate byte budgets, safe error mapping, concurrent-load sharing and
close races are bounded; authorization and cookie headers are absent.

### TASK-020 — Optional publisher-signature verification

Added canonical schema-v1/v2 package signature verification using an exact
package/key/signer-scoped trust resolver. Unsigned, malformed, mutated or
untrusted packages become bounded identity/integrity results; signature
verification never bypasses lifecycle consent or security policy.

### TASK-021 — Read-only registry controller and Sources catalog UI

Wired optional compile-time registry configuration through a single controller
with shared initialization/refresh, immutable loading/ready/failed state,
atomic replacement and disposal fencing. Sources compares verified catalog
entries with installed versions while keeping refresh, install and enablement
explicit.

### TASK-022 — Explicit package lifecycle consent UI

Added the application-facing lifecycle controller and Sources actions for
Install/Update staging, complete security-policy review, consent-gated Enable
and exact-version Disable. Mutations are serialized, stale/close-safe and do
not execute packages during staging.

### TASK-023 — Fixture source-search coordinator

Added deterministic multi-package search composition over explicit package,
program, fixture and mapping plans. Disabled, consent-pending and incompatible
packages short-circuit before runtime work; eligible results preserve source
order and source-local typed statuses.

### TASK-024 — Fixture episode coordinator

Added deterministic episode composition with staged-consent-aware preflight,
explicit episode identity and per-source status aggregation. Runtime,
normalizer, not-found, bounds and exception outcomes are typed and redacted.

### TASK-025 — Fixture playable-source coordinator

Added episode-bound playable-source aggregation with package policy and
candidate identity validation. It preserves caller order, rejects forged or
unsupported candidates, and reports partial, not-found and normalizer failure
without creating downstream playback side effects.

### TASK-026 — Fixture source-local playback route coordinator

Added deterministic route grouping and selection with exact package/version/
program/source-key preference. There is no implicit cross-package fallback or
cross-source identity synthesis; source-local failure and blocked states stay
visible.

### TASK-027 — Fixture playback session-request coordinator

Added the selected-route handoff to the existing session-request builder.
Non-selected routes stop before builder work, while the selected route retains
the exact package manifest, ad plan, source event sequence and typed rejection
semantics.

### TASK-028 — Fixture playback open-request coordinator

Added ready-session to `PlaybackOpenRequest` composition with exact resolver
request and bounded refresh/episode options. Non-ready session states and
invalid options never create an open request.

### TASK-029 — Fixture prepared-request opener

Added the final fixture prepared-request opener that passes only an exact ready
request to `PlaybackCoordinator`. Resolver/player cardinality and failure
propagation remain owned by the existing coordinator.

### TASK-030 — Complete fixture playback composition

Updated the fixture pipeline to expose the first failed typed stage across
normalization, route, session, open and prepared-open boundaries. Tests verify
exact forwarding, short-circuiting, exception conversion and diagnostic
redaction.

### TASK-031 — Generation-scoped live capture admission

Added typed live-capture request, port and coordinator contracts. Package,
program and policy provenance, URI/cookie/header/event/candidate budgets,
strict event order, candidate derivation and generation/close races are
validated before a capture result is accepted.

### TASK-032 — Cross-platform WebView capture port

Added the platform-owned Android WebView and Windows WebView2 capture port with
one keyed generation and one pending completion. Navigation, iframe, resource,
XHR/fetch, cookie and media observations use bounded typed mapping; duplicate,
late, replacement, close and platform exceptions fail safely.

### TASK-033 — Live source package admission

Added package-aware admission for live capture. Consent/re-consent, enabled
state, compatibility, declared program, exact security policy and capture
request identity are checked before any WebView work, with no package or
runtime authority duplication.

### TASK-034 — Installed-package live capture surface

Added the package-aware platform surface that mounts the lower WebView capture
only after application admission succeeds. It forwards one exact request and
maps no-build, unavailable, duplicate or late platform results to typed safe
states.

### TASK-035 — Live capture to playable-source provenance

Added shared snapshot validation and live playable-source composition. The
accepted request/result pair, first-observation candidate kind/URI/headers and
source-event sequence are revalidated exactly; omission, duplication,
reordering, forged kind, fragment mismatch and impossible budgets fail closed
without retaining invalid capture data.

### TASK-036 — Live capture source-local route

Added live route selection over accepted playable-source results. It preserves
the exact accepted source and capture-result references, rechecks package,
program, episode and policy identity, verifies event/candidate provenance and
supports only explicit first/preferred selection without implicit fallback.

### TASK-037 — Live capture session-request handoff

Added the live route-to-session-request boundary. Captured candidate headers,
event sequence, cookie snapshot and user-agent remain attached to the exact
resolver request, while non-selected or mismatched routes stop before session
resolution.

### TASK-038 — Live capture open-request handoff

Added live session-request to `PlaybackOpenRequest` composition with exact
request, proxy family, refresh bounds and optional episode duration forwarding.
Non-ready and invalid-option states remain typed and do not invoke resolver,
proxy or player work.

### TASK-039 — Live capture prepared opener

Added the live prepared-request opener that forwards one ready open request to
the existing `PlaybackCoordinator`. It adds no second lifecycle, session,
proxy, player, progress or generation authority.

### TASK-040 — Complete live capture playback pipeline

Composed live playable normalization, route selection, session request,
open-request and prepared-open stages in order with typed short-circuiting. A
ready path reaches exactly the existing playback coordinator; all downstream
options and stable playback failures remain exact and redacted.

### TASK-041 — Capture-to-playable plan coordinator

Added the explicit handoff that pairs package plan, ready admission, completed
capture, episode identity and complete candidate mappings into one accepted
playable-source plan. Any provenance, policy, identity or mapping mismatch is
rejected without leaking capture data.

### TASK-042 — Live capture playback entry point

Added the end-to-end package-capture entry boundary that feeds only a ready
TASK-041 plan into the accepted live playback pipeline. It verifies mapping,
option and downstream failure paths while retaining the existing session and
player lifecycle authority.

### TASK-043 — Bounded source HTTP GET boundary

Added explicit package-admitted `SourceHttpRequest`/`SourceHttpResponse`
contracts, one-shot execution and Dart I/O transport. Redirects, URI/headers,
response bytes, UTF-8, timeout, lifecycle and close races are bounded; only
safe text and typed diagnostics cross the boundary.

### TASK-044 — Live response declarative rule evaluation

Added the single live HTTP-to-declarative-runtime composition. Only a completed
package-admitted response enters one in-memory fixture evaluation; the result
is immutable and no response body, transport credential or raw exception is
promoted into application state.

### TASK-045 — Live source search composition

Added bounded asynchronous multi-plan live search through the existing runtime
and search normalizer. Query/count/duplicate gates, normalized identity checks,
generation-scoped stale suppression, close invalidation and source order are
explicit; no Search UI or provider adapter is added here.

### TASK-046 — Live episode composition

Added live HTTP episode aggregation through the existing runtime and episode
normalizer. It preserves caller order, exact episode identity and typed
partial/not-found/failure semantics with pre-I/O bounds and stale-response
protection.

### TASK-047 — Live playable-source composition

Added live HTTP playable-source aggregation through the existing runtime and
playable-source normalizer. It preserves package/episode/candidate provenance,
validates supported kinds and URI policy, and returns bounded typed partial or
failure results without selecting a route.

### TASK-048 — Live source-local playback route

Added live route composition over caller-ordered playable plans/results. It
revalidates package lifecycle, request policy, episode identity, supported kind,
allowlist and duplicate source keys before using the deterministic route
selector and preserving exact source provenance.

### TASK-049 — Live HTTP session-request composition

Added selected live route to the existing `PlaybackSessionResolutionRequest`
builder. Lifecycle and request identity are rechecked, non-selected routes
short-circuit, and ready output retains exact episode/page/media policy while
not duplicating resolver or session authority.

### TASK-050 — Live HTTP open-request composition

Added live session-request to the existing open-request contract with exact
proxy budget, loopback family, refresh settings and episode options. Typed
non-ready and invalid-option paths cannot produce an open request.

### TASK-051 — Live HTTP prepared open

Added the final live HTTP prepared opener to the existing `PlaybackCoordinator`.
Only ready requests reach resolver/proxy/player work; all non-ready lower-stage
states and stable playback errors remain truthful.

### TASK-052 — Complete live HTTP playback pipeline

Composed explicit live plans, runtime, normalization, source-local route,
session request, open request and prepared opener into one bounded pipeline.
It snapshots plans once, preserves caller order and exact identity, and
short-circuits the first typed failure without inventing capture or provider
data.

### TASK-053 — Schema-v2 live operation plan factory

Added schema-v2 bounded live `search`, `episode` and `playable-source`
declarations with strict v1 compatibility, canonical encoding/signature input,
field mappings and safe URI-template expansion. Plans are materialized only
after lifecycle, compatibility, program identity and request-policy admission.

### TASK-054 — Installed-package live search pipeline

Added one caller-ordered installed-package snapshot boundary with 32-package
and unique package/version limits. Eligible schema-v2 packages use TASK-053
plans and TASK-052 search composition; lifecycle, missing-operation, runtime,
normalizer, stale and close states remain typed.

### TASK-055 — Installed-package live episode pipeline

Added one exact installed-package/episode-target snapshot boundary with bounded
target count and package/version/subject/episode identity. It delegates to the
existing plan factory and live episode coordinator, preserving target order and
rejecting forged, duplicate, stale or lifecycle-invalid targets.

### TASK-056 — Installed-package live playback pipeline

Added exact installed episode-target to playable-plan composition and delegated
the ready plan to the existing TASK-052 live playback pipeline. Package,
program, episode, mapping, preference and playback options stay exact while
non-ready stages remain side-effect free.

### TASK-057 — Search presentation adapter

Connected `SearchPage` to exactly one installed-package live-search operation
through `SourceSearchPresentationController`. Queries are trimmed and bounded,
installed packages come from the existing lifecycle snapshot, normalized result
order and package/source provenance are visible, every typed state has
deterministic Traditional-Chinese/localized presentation, retry is bounded,
and result taps remain inert until a separately authorized navigation contract
exists. Loading, no-sources, stale-response and disposal behavior are covered
without closing the shared pipeline.

## Shared architecture and safety boundaries

- One website remains one declarative source-package file under the source
  registry model; the app does not hardcode provider-specific implementations.
- Source packages are data rules, not arbitrary Dart, JavaScript, WASM, native,
  shell or filesystem plugins. Domain remains pure Dart and platform code stays
  behind typed ports.
- Playback and any future download operation retain one authoritative
  `PlaybackSession`; route, session, proxy and player authority is not copied
  by source adapters.
- Bangumi watched state and Wynime exact playback milliseconds remain separate.
- Telemetry is disabled by default. Logs, persistence and UI do not expose
  tokens, cookies, secrets, raw upstream responses, complete media URLs or raw
  native exceptions.
- Magnet, BitTorrent, seeding, DRM bypass, paywall bypass and access-control
  circumvention remain excluded.

## Validation evidence

- Full deterministic Flutter suite after TASK-057: 117 suites, 744/744
  passed, 0 failed and 0 skipped.
- TASK-057 focused tests: 11/11 passed; combined Search focus: 20/20 passed;
  affected 13-suite set: 100/100 passed.
- Fatal Dart analyzer: no issues. Task-owned format: 7 files, 0 changes.
  Task-owned trailing-whitespace scan: no matches. `git diff --check` exited
  0 with known LF/CRLF conversion warnings only.
- Android arm64 debug build passed after controlled Gradle-environment
  recovery. The inspection APK contained arm64 Flutter and libmpv entries;
  this is not the protected release-signed APK.
- Windows debug build passed after a controlled serial retry in a writable
  environment; the existing CMake CMP0175 developer warning is non-fatal.
- The configured `Pixel_API_36_Google_Play` phone emulator was already open
  and responding (PID 17092). Per the user-specified validation boundary, no
  ADB install, app launch, tap/input, screenshot, tablet run or runtime UI
  interaction was performed.
- Independent browser-based GPT-5.6 Sol read-only review accepted TASK-045
  through TASK-057 with exact `SOL_REVIEW_PASS` verdicts. TASK-057 first
  returned `SOL_REVIEW_CHANGES_REQUIRED` for missing loading/no-sources test
  evidence; only those two deterministic tests were added, production code
  stayed unchanged, and the same task then returned `SOL_REVIEW_PASS`.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`.
- Native Windows action-level Computer Use remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` in the current audit environment.
- No live provider availability, live Bangumi account mutation, production
  source-registry rollout or authenticated playback is inferred from fixture,
  build or static evidence.

## Remaining changes for the next chat

1. Exercise the installed schema-v2 source packages against an explicitly
   authorized provider and supply live response/runtime evidence; no provider
   is bundled or hardcoded by this release.
2. Add the separately authorized episode/detail/playback UI route above the
   current inert Search results, preserving the one-package/one-website and
   shared-executor boundaries.
3. Repeat Android phone runtime and Windows action-level UI evidence when the
   user authorizes interaction and the environment can observe it; keep the
   current phone-only/open-only rule until then.
4. Obtain physical Android and Windows hardware playback evidence for the
   native engine route; keep `HARDWARE_VALIDATION_PENDING` until exercised.
5. Continue Bangumi live OAuth/account and production Worker/App Link checks
   only with an authorized test account and without exposing credentials.
6. Do not add fonts until the multilingual Android/Windows font review is
   explicitly approved.
7. Every future task must repeat: implement one task, run deterministic
   validation, submit the complete packet to browser-based GPT-5.6 Sol High
   in read-only mode, fix only evidence-backed findings, rerun, and continue
   only after exact `SOL_REVIEW_PASS`. This round ends after TASK-057; the next
   chat must select the next unfinished Gap Matrix item and must not assume
   TASK-058 without the next authorized task definition.
