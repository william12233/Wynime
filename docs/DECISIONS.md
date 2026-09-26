# Wynime Architecture Decisions

## ADR-001 — Flutter for shared UI

**Status:** Accepted
**Decision:** Use Flutter for Android and Windows UI. Use responsive layout classes instead of stretching one widget tree across every screen size.
**Reason:** High visual consistency and lower cross-platform UI duplication while retaining native media integrations.

## ADR-002 — Native media backends

**Status:** Accepted
**Decision:** Android uses Media3 by default and libmpv as fallback. Windows uses libmpv. WebView playback is the final fallback.
**Reason:** One cross-platform player backend is not sufficiently reliable for all lifecycle, codec and stream edge cases.

## ADR-003 — Shared PlaybackSession

**Status:** Accepted
**Decision:** Players and downloader consume the same resolved session, including URL, headers, cookies, referer, origin, expiry, refresh callback, tracks and timeline map.
**Reason:** Prevents download-success/playback-failure inconsistencies caused by separate resolution paths.

## ADR-004 — Local HLS sanitizer and proxy

**Status:** Accepted
**Decision:** Parse and sanitize HLS before playback or download. Expose the cleaned manifest through a local proxy.
**Reason:** Enables stable seeking, consistent ad removal, shared headers and one timeline across engines.

## ADR-005 — Ad plan identity

**Status:** Accepted
**Decision:** An ad-removal plan is keyed by source, line, subject, episode and manifest fingerprint.
**Reason:** Ads can differ between sources, lines, quality variants and different versions of the same source.

## ADR-006 — Authoritative artifact manifest

**Status:** Accepted
**Decision:** Every created file is recorded when created. Deletion only consumes that manifest and never reconstructs paths.
**Reason:** Guarantees downloads and deletions refer to the same physical artifacts and supports crash recovery.

## ADR-007 — Optional source signatures

**Status:** Accepted
**Decision:** Unsigned source packages may be installed. Signatures only establish author identity. Domain allowlists, permissions, sandboxing and resource limits remain mandatory.
**Reason:** Avoids excluding high-quality independent sources while preserving runtime security boundaries.

## ADR-008 — No magnet or BitTorrent

**Status:** Accepted
**Decision:** Do not include magnet, BitTorrent, seeding or upload features.
**Reason:** Product priority is web-stream playback, ad-free HLS download and reliable local file management.

## ADR-009 — Bangumi synchronization

**Status:** Accepted
**Decision:** Bangumi provides schedule metadata, collection state and watched episodes. Exact playback position stays local to Wynime.
**Reason:** Keeps synchronization aligned with Bangumi capabilities while preserving source-specific playback state.

## ADR-010 — Font remains unselected

**Status:** Proposed
**Decision:** Do not bundle or lock a font until Noto Sans CJK Full Variable, Source Han Sans Variable and Sarasa Gothic UI pass Android and Windows mixed-language screenshot tests.
**Reason:** The baseline must visually match Traditional Chinese, Simplified Chinese, Japanese and English without obvious fallback seams.

## ADR-011 — Rust requires a gate

**Status:** Proposed
**Decision:** Adopt Rust for HLS/download internals only after Android and Windows FFI, cancellation, crash recovery, memory and stress prototypes pass.
**Fallback:** Dart orchestration with Kotlin and Windows-native implementations.

## ADR-012 — Source rule schema v1 uses bounded declarative dialects

**Status:** Accepted
**Decision:** Phase 2 source schema version 1 supports CSS selectors, a restricted JSONPath subset and restricted regular-expression captures. XPath and executable source code are rejected explicitly rather than partially or silently supported.
**Reason:** CSS and JSONPath cover fixture-based extraction without introducing an unsafe interpreter. Dart regular expressions are permitted only with strict pattern/input budgets and syntax restrictions. XPath requires a dedicated safe subset parser and remains deferred.

## ADR-013 — Source signatures never increase authority

**Status:** Accepted
**Decision:** Signature metadata may identify an author, but signed and unsigned packages are subject to identical domain allowlists, permissions, user-consent rules and resource budgets.
**Reason:** Identity and runtime authority are separate security properties; trusting an author must not bypass sandbox controls.

## ADR-014 — One typed WebView plugin boundary for Android and Windows

**Status:** Accepted
**Decision:** Phase 3 pins `flutter_inappwebview 6.2.0-beta.3` and permits it only under `lib/src/platform/web_capture`. Android uses `flutter_inappwebview_android 1.2.0-beta.3`; Windows uses the endorsed `flutter_inappwebview_windows 0.7.0-beta.3` WebView2 implementation. Domain and plugin-independent Infrastructure depend only on Wynime-owned typed models and ports.
**Reason:** The official `webview_flutter` family does not provide a Windows implementation, while maintaining separate Android and Windows APIs would duplicate security policy and interception mapping. The latest stable `6.1.5` was rejected after its Android `1.1.3` package failed under Flutter-generated AGP `9.0.1`; the selected prerelease explicitly contains the upstream AGP 9 fix and supports the locked Flutter 3.44.8／Dart 3.12.2 toolchain.
**Safety:** Download handling, HTTP authentication, invalid server trust, camera, microphone, geolocation, new windows, file access and mixed content fail closed. Plugin types cannot cross into Domain, and a signed source package receives no additional WebView authority.

## ADR-015 — Capability-scoped loopback proxy and Media3 1.10.1

**Status:** Accepted
**Decision:** Phase 4 resolves one authoritative `PlaybackSession`, exposes every player resource through a numeric-loopback-only HTTP proxy with a per-session unguessable capability path, and pins Android `media3-exoplayer` plus `media3-exoplayer-hls` to `1.10.1`. The player never receives a raw upstream media URI or source credentials.
**Reason:** Centralizing URI, Header, Cookie, Referer, Origin, expiry and refresh authority avoids player-specific resolution drift. Capability paths and strict source allowlists prevent the local service from becoming an open proxy, while HLS URI rewriting allows every child request to reuse the same policy and credentials. Media3 `1.10.1` was the newest stable version verified against the locked toolchain when Phase 4 began.
**Safety:** The listener binds only `127.0.0.1` or `::1`; redirects and child resources remain allowlisted; IPv4 and IPv6 DNS results must all be public; every new upstream socket is connected directly to an address validated inside the connection factory while HTTPS preserves the original hostname for certificate checks; request and response resources are bounded; Set-Cookie and Location are not forwarded; lease close cancels active body subscriptions; Media3 accepts only loopback endpoints. Phase 4 does not sanitize manifests, detect ads or rewrite timelines.

## ADR-016 — Canonical HLS fingerprints bind every ad decision

**Status:** Accepted
**Decision:** Phase 5 parses a bounded immutable HLS model and computes one SHA-256 structural fingerprint before planning or sanitization. Volatile authorization query values are masked, while sequence numbers, durations, discontinuities, byte ranges, effective key／map context, date ranges and resource structure remain fingerprint inputs. Every active `AdRemovalPlan` must match that fingerprint exactly.
**Reason:** A refreshed token must not invalidate an otherwise identical stream, but an ad plan must never be reused after the manifest structure changes. Hash-only identity also prevents credentials or complete manifests from entering persistence and diagnostics.
**Safety:** Mixed playlist kinds, duplicate singleton tags, malformed or unsupported semantics and resource-budget excesses fail closed before hashing. The parser accepts only HTTP(S) resources without user info or fragments.

## ADR-017 — Phase 5 sanitization is evidence-bound and VOD-only

**Status:** Accepted
**Decision:** Safe mode removes only segments covered by explicit HLS CUE markers or a bounded ad `EXT-X-DATERANGE`. Smart and aggressive modes require at least two independent structural signals, cannot heuristically target first or last discontinuity groups, and obey a configured maximum removal ratio. Sanitization accepts only complete VOD media playlists and produces one exact bidirectional `AdTimelineMap`.
**Reason:** `EXT-X-DISCONTINUITY`, a short segment or a different host can all occur in legitimate content. Combining independent evidence, preserving exact segment identity and limiting heuristics reduces false positives while keeping deterministic replay and seeking.
**Safety:** Planning refuses to remove every segment. Live／event, LL-HLS, delta updates, I-frame-only, ambiguous date-range semantics, SAMPLE-AES and non-identity key formats are rejected. DRM and access-control bypass remain explicitly out of scope.

## ADR-018 — media-kit wraps libmpv behind a capability-only engine router

**Status:** Accepted
**Decision:** Phase 6 pins `media_kit 1.2.6`, `media_kit_video 2.0.1`, `media_kit_libs_android_video 1.3.8` and `media_kit_libs_windows_video 1.0.12` from media-kit commit `e9abf3b9114fdb565b13a4c194d776c70e416e7d`. Its Windows native archive is pinned through the machine-readable provenance lock to the 20241021 build source and runtime identities. A Wynime-owned Application router preserves one `PlaybackSession` and loopback proxy lease while selecting Android Media3 → libmpv → WebView or Windows libmpv → WebView. Each playback operation may perform at most one automatic fallback, only for decoder, renderer or unsupported failures.
**Reason:** media-kit provides a maintained Flutter-facing libmpv API and video surface without allowing third-party types to cross Domain or Application. Keeping routing above Platform avoids resolving a second URL or leaking source credentials when an engine changes.
**Safety:** libmpv receives only the bounded numeric-loopback capability URI with an empty header map. Authorization, expiry, network and manifest failures do not trigger engine fallback. Handoff preserves original-timeline position, play state, volume, rate, exact audio／subtitle IDs and `timelineMapIdentity`; stale generations and identity mismatches fail closed. Raw native errors are reduced to stable diagnostic codes. The media-kit Dart packages are MIT, but the exact libmpv／FFmpeg artifact may be GPL or a compliant LGPL build; release packaging requires the engineering provenance, build-flag and linked-library license evidence recorded by Phase 12. Real-device playback is not claimed from CI compilation alone.

## ADR-019 — Phase 6 Application boundary owns track and error authority

**Status:** Accepted

**Decision:** `PlaybackEngineRouter` validates every backend event track ID and public track selection against the active `PlaybackSession`. Only exact, current-session tracks without an external URI may enter shared playback state. Platform and Application layers normalize synchronous open/control failures and asynchronous event-stream failures into a bounded `PlaybackOperationException` carrying only a typed `PlaybackFailure`.

**Reason:** Backend-specific validation is defense-in-depth, but the shared router must remain safe when a new backend, test double or malformed native event bypasses an individual implementation. A single error boundary prevents native messages, URLs, credentials and stack traces from becoming Application-visible diagnostics while preserving fallback and refresh classification.

**Safety:** Foreign, missing, stale, ambiguous and external-URI track identities fail closed. Raw exception text and stack traces are never emitted through the coordinator error stream or persisted; tests inject URL/query/cookie/token-shaped messages to verify stable redaction. This decision does not authorize DRM, paywall, access-control bypass, downloads or Phase 7 work.

## ADR-020 — Download progress and artifact lifecycle are append-only and fail closed

**Status:** Accepted

**Decision:** Phase 7 persists one `DownloadJob` per manifest identity and checkpoints segment completion after every atomic file write. Phase 8 extends the same `DownloadArtifactManifest` only through append-only registration. Remuxing receives local manifest artifacts through a typed runner and must verify the selected container before promotion. Deletion receives a persisted `DeleteJob` and deletes only the exact manifest URIs after canonical containment and link checks. Orphan scanning is report-only.

**Reason:** Crash recovery, remux retries and deletion must operate on durable identities rather than reconstructing URLs or paths from mutable source metadata. Append-only registration preserves provenance while allowing a later remux output to join a Phase 7 manifest.

**Safety:** Download refreshes are bounded and preserve `timelineMapIdentity`; only identity AES-128 is accepted. No cookies, tokens, upstream URLs or full manifests enter job persistence or diagnostics. FFmpeg arguments are supplied as an argument vector rather than a shell command, and raw process output is reduced to stable codes. A missing, linked, outside-root or unverifiable artifact never becomes a successful deletion or completed remux.

## ADR-021 — Bangumi uses encrypted refresh sessions and revision-bound offline sync

**Status:** Accepted

**Decision:** Phase 9 uses Bangumi's official HTTPS API for `/calendar`, `/v0/subjects/{subject_id}`, `/v0/episodes`, current-user collection and current-user episode collection endpoints. OAuth authorization-code requests are state-bound. The short-lived CSRF state may be stored by the platform handoff bridge so an Android process restart can finish the browser return. Access tokens remain memory-only; Android stores only the refresh token, encrypted with an Android Keystore AES-GCM key, so startup can silently refresh the session. A rotated refresh token replaces the previous ciphertext. Local collection status and watched episodes are updated immediately, while a Drift-backed `BangumiSyncOperation` records the exact mutation, base remote revision, target-field base value, retry state, diagnostic status code and conflict or blocked state.

**Reason:** Bangumi supplies schedule, subject, collection and episode-collection data, while Wynime must remain usable offline and must not confuse local playback progress with remote watched state. A revision-bound queue makes concurrent edits visible instead of silently overwriting them.

**Safety:** Only standard-port HTTPS Bangumi hosts are accepted; bearer tokens are sent in headers and never query parameters. The broker keeps the Bangumi provider callback at `/oauth/callback` separate from the Android verified App Link return path at `/oauth/app-callback`, so the final app state cannot be parsed as a second provider state. Response size, episode page size, retries and capped jittered exponential backoff are bounded. Every unfinished local operation reads remote state before mutation and verifies the desired state after mutation. Mutation requests explicitly send `Content-Type: application/json`; a local diagnostic records only the mutation kind, redacted route shape, method, status, bounded reason code, route class and allowlisted content-type class. It never records identifiers, hosts, query strings, authorization, cookies, body or raw response text, and is not remote telemetry. Network, timeout, provider, payload and 429／5xx failures remain in `retryWaiting` after foreground limits; only structurally impossible operations enter `blocked`, and v1.0.6 `failed` rows are migrated. A blocked HTTP 415 row is recoverable after the request contract is corrected; a new local intent may reuse that row and rebase its target-field base revision from the current remote cache without reviving other blocked rows. Explicit refresh imports remote-only changes, safely merges independent fields and records incompatible target changes as conflicts. Conflict resolution either applies the fetched remote state or requeues the local mutation against the fetched revision. The platform handoff store contains only one bounded, one-time OAuth state and its creation time, and the client clears it on mismatch, denial, ticket failure or successful redeem; it is not a token store. The Android session store contains only encrypted refresh-session ciphertext and IV; it never stores access tokens, client secrets, cookies or raw response text. Sign-out, provider rejection, account mismatch and invalid ciphertext clear the stored refresh session. A provider can still revoke or expire a refresh token, so this is automatic reauthentication rather than an unbreakable permanent login. No live OAuth account or upstream success is inferred from fixture tests.

Implementation note: An explicit collection refresh uses an atomic full snapshot. It imports remote-only changes, removes absent membership rows, preserves active local-first collection intent, and refreshes remote state for already-open detail subjects. Unopened detail routes fetch exact watched IDs on demand, so a large collection cannot trigger an unbounded N+1 refresh.

## ADR-022 — Automatic source building is a reviewed declarative proposal

**Status:** Accepted

**Decision:** Phase 10 accepts only bounded source observations with explicit expected field samples. It may infer repeated HTML tag/id/class selectors or the existing restricted JSONPath subset, but it may not execute source-provided code or emit XPath. The generated package is verified by the existing fixture evaluator before it is returned as a proposal.

**Reason:** Automatic source discovery reduces the cost of adding ordinary declarative sources while keeping the runtime interpreter small, deterministic and reviewable. Expected samples provide an evidence boundary so a selector cannot be accepted merely because it parses.

**Safety:** A generated proposal is always review-required and cannot activate itself. Activation is bound to a deterministic proposal ID and explicit user approval. New domains, insecure HTTP and broader budgets are never silently inherited; they require explicit builder options and fresh consent or re-consent. The builder never persists observation bodies, expected values, cookies, tokens or complete media URLs, and source signatures do not add authority.

## ADR-023 — Phase 11 uses explicit presentation state and truthful empty states

**Status:** Accepted

**Decision:** The Phase 11 shell maps all six destinations to explicit product pages backed by typed local presentation state. Compact, medium and expanded layouts share destination order and design tokens but use navigation patterns appropriate to their window class. Missing source packages, Bangumi connectivity, watch history, downloads and generated proposals are rendered as unavailable, empty or review-required states rather than mocked success data.

**Reason:** A visual shell is only useful when it communicates the real product boundary. Keeping Search local until an enabled source exists, keeping telemetry off by default and exposing artifact／engine constraints prevents UI affordances from implying capabilities that the domain and infrastructure have not yet authorized.

**Safety:** The presentation layer does not import WebView, network clients, source execution, playback resolution or download mutation paths. Theme and language changes remain typed and in-memory for this phase. Android action-level evidence and fixed Goldens are necessary but not sufficient for the overall cross-platform gate; if Windows rendering or required interactions cannot be observed, the result remains `BLOCKED_UI_ENVIRONMENT` and is never upgraded from build or launch success.

## ADR-024 — Phase 12 uses evidence-gated release packaging and privacy-preserving local artifacts

**Status:** Accepted

**Decision:** Release packaging is gated by separate current-head, runtime, hardware and native-provenance evidence. Android release signing may use only an explicitly configured external keystore; when no such configuration exists, the release artifact remains unsigned and is not publishable. The exact media-kit/libmpv/FFmpeg/ANGLE inputs, build flags, linked libraries and redistributed licenses must be recorded before release. Local download and remux mutations must validate lexical and canonical containment under the configured root, reject link/junction traversal, use regular files only and let deletion consume only the persisted `DownloadArtifactManifest`. FFmpeg runs through a bounded no-shell argument vector. Persisted HLS recovery data is reduced to structural metadata and never stores full URLs, queries, credentials or tokens.

**Reason:** Compilation and fixture tests cannot prove a native binary's legal closure, real decoder behavior, hardware rendering or safe filesystem behavior against hostile links. Separating evidence classes prevents a convenient build result from being mistaken for a release or runtime pass, while the redacted snapshot retains enough bounded structure for recovery without retaining upstream secrets.

**Safety:** Missing FFmpeg where the release boundary requires it, absent external signing keys, failed artifact verification or incomplete native engineering provenance remain explicit blockers. Unavailable hardware and unobservable Windows UI remain explicit disclosures and do not become fabricated passes. A successful process exit never bypasses artifact signature verification, canonical containment or manifest authority. Telemetry remains disabled by default.

## ADR-025 — Phase 12 separates hard release gates from external validation

**Status:** Accepted

**Decision:** Phase 12 separates machine-verifiable release integrity gates
from external validation that may be unavailable in the current audit
environment. Exact-SHA source integration, CI, tests, analyzer, Android
release build and signing, APK metadata/alignment, Windows release build,
engineering native provenance, packaged notices, checksums, release notes,
tag identity and immutable release assets remain hard gates. Physical Android
and Windows playback, native Windows Computer Use actions and additional
manual exploratory UI checks are recorded as
`HARDWARE_VALIDATION_PENDING` or `WINDOWS_CUA_VALIDATION_UNAVAILABLE` when
unavailable, and do not by themselves prevent `RELEASE_READY`.

**Reason:** The repository acceptance policy requires truthful current-head
compilation, static analysis, deterministic replay and independent
read-only review when real hardware or an external runtime is unavailable.
Making unavailable external observation a mandatory publication gate would
conflict with that policy without improving the machine-verifiable integrity
checks.

**Safety:** Unavailable validation is never rewritten as a pass. A real
license incompatibility, missing legally required notice/source offering,
unverified binary identity, signing failure, failed test/build, secret
exposure or SHA mismatch remains a hard blocker. `LICENSE` applies only to
Wynime-owned source code; every third-party component retains its own
upstream terms and notice references. Release automation remains exact-SHA,
non-force and protected by the GitHub release environment.

## ADR-026 — Windows portable updates are ZIP-only and database-quiesced

**Status:** Accepted

**Decision:** Future Windows releases publish only the portable ZIP and its
SHA-256 sidecar; the ZIP contains `wynime.exe`, `wynime_update.exe`,
`version.txt`, the Flutter runtime and required notices. The Settings update
flow prepares all staging paths first, then creates a SQLite-consistent
recovery point through a shared database write gate. The native helper starts
only after the gate has drained and blocked all application writes, waits for
the parent to exit, and owns backup, replacement, health-marker and rollback.

**Reason:** A portable ZIP keeps installation reversible without requiring a
setup executable or elevation. Quiescing every repository write closes the
window in which a live Drift connection could commit data after the recovery
snapshot but before replacement, while still allowing a failed process start
to resume the running application safely.

**Safety:** SHA-256 is an integrity check rather than a signature. A failed
pre-handoff start resumes writes, deletes the recovery snapshot and removes
all updater-owned staging. A successful helper handoff leaves the database
closed by process termination, and the helper restores the prior install and
database snapshot on failed startup. Historical releases retain their
original assets and installer evidence.

## ADR-027 — Declarative source package lifecycle is explicit and in-memory

**Status:** Accepted

**Decision:** Phase 10 uses one deterministic `SourcePackageManager` record per
`packageId`. Strictly decoded, compatible packages may be installed, but a
new install or ordinary update is disabled until explicit consent. Updates
must be strictly newer, replace the prior record atomically, and become
disabled again for review. A broader domain／permission／budget policy
requires re-consent. Enable and disable operations bind to the current
package version so stale UI actions cannot mutate a replacement. Builder
proposals use the exact proposal ID, explicit approval and re-consent gate
before an atomic enabled install; activation errors cross the manager as
stable codes.

**Reason:** Source packages are replaceable data rules rather than executable
plugins. Separating decode, lifecycle consent and future registry persistence
keeps the current implementation deterministic while allowing a later
registry adapter without coupling source code to GitHub or platform APIs.

**Safety:** The manager has no filesystem, network, WebView, player, shell or
source-code execution capability and keeps immutable sorted snapshots. It does
not claim persistent restart recovery, live registry availability, checksum／
publisher-signature verification or live source search. Those are separate
future boundaries and cannot weaken the existing allowlist, permission,
resource-budget, proposal or re-consent requirements.

## ADR-028 — Source runtime starts as a fixture-only canonical executor

**Status:** Accepted

**Decision:** Source packages execute through one typed
`SourcePackageRuntime` contract. The first implementation accepts only an
enabled, consent-complete, compatible installed package, resolves an exact
program ID, and evaluates a supplied fixture through the canonical
`SourceFixtureRuleEngine`. It returns immutable generic field records and
bounded, secret-safe diagnostics with explicit availability, not-found,
disabled, consent-required, incompatible and failed states.

**Reason:** The package manager establishes lifecycle authority, but a shared
runtime contract is required before future source coordinators can consume
packages without provider-specific branches. Keeping the first executor
fixture-only makes the contract deterministic and testable while the live
HTTP/WebView boundary remains separately authorized.

**Safety:** Runtime preflight rejects disabled, consent-pending and
incompatible packages before evaluation. Security and evaluation exceptions
are converted to stable codes and generic messages; raw parser text, fixture
bodies, credentials and complete media URLs are never returned. No runtime
operation performs I/O, executes source code, mutates the package manager or
creates a playback session.

## ADR-029 — Normalize fixture search records through an explicit typed mapping

**Status:** Accepted

**Decision:** The first normalized source boundary is a fixture-only
`SourceSearchNormalizer`. It accepts the generic records and status from
`SourcePackageRuntime` plus an explicit subject-ID/title field mapping, then
returns immutable `SourceSearchResult` values. Source identity comes from the
installed package identity, not a record field. Valid rows retain input order;
duplicate subject IDs keep the first row; invalid, missing or over-limit values
are dropped with bounded generic diagnostics. Runtime states that are not
available propagate without exposing any records.

**Reason:** A generic evaluator record is not yet a safe application search
contract. An explicit mapping makes package-declared field semantics visible
and testable while allowing every website to use the same normalizer and
avoiding provider-specific branches. Keeping this slice fixture-only provides
deterministic evidence before a future live coordinator is authorized.

**Safety:** The normalizer rejects malformed package identities, bounds subject
IDs and titles, rejects control characters, redacts runtime diagnostic text and
never maps arbitrary record values into source identity or playback URLs. It
does not perform I/O, access Search UI, persist data, invoke WebView or create
a playback session.

## ADR-030 — Normalize fixture episode records through the shared identity model

**Status:** Accepted

**Decision:** Episode listings use a fixture-only
`SourceEpisodeNormalizer` with an explicit mapping for line ID, subject ID,
episode ID and title. The normalizer creates the existing
`SourceEpisodeIdentity` using the source package identity, preserves valid row
order, keeps the first occurrence of a duplicate full identity, and returns
immutable typed `SourceEpisode` values. Missing, invalid or over-limit fields
are row-scoped diagnostics; if no row survives, the result is failed rather
than a false empty success. Runtime availability states are propagated without
returning records.

**Reason:** Search output alone cannot identify a playable episode. Reusing one
source/line/subject/episode identity model keeps future progress and playback
boundaries consistent while the explicit mapping lets every declarative source
package share the same executor and normalizer.

**Safety:** Episode IDs, line IDs, subject IDs and titles are bounded and reject
C0, DEL and C1 control characters. Package identity is validated independently
and record-provided source IDs are ignored. Diagnostics are generic and bounded;
the implementation performs no I/O, persistence, live source request, WebView
interaction or playback-session creation.

## ADR-031 — Normalize fixture playable sources through package policy

**Status:** Accepted

**Decision:** Playback extraction uses a fixture-only
`SourcePlayableSourceNormalizer` with an explicit mapping for source key,
label, candidate kind, media URI and page URI. The normalizer requires the
exact package manifest and rejects runtime results whose package ID or version
does not match that manifest, requires the program to exist and requires every
mapped field to be declared by that program. It binds the supplied typed
episode identity to each output, accepts only HLS and direct audio/video
candidates, validates both URIs through the package `SourceSecurityPolicy`,
preserves source order and keeps the first occurrence of a duplicate source
key. Non-available runtime states propagate without returning records; if no
record survives, the result is failed rather than a false playable success.

**Reason:** A normalized episode is not yet a safe player input. A dedicated
playable-source contract lets every declarative website describe playback
variants through the shared executor while keeping the existing
`PlaybackSessionResolver` as the only session authority. Requiring the package
manifest at this boundary prevents a runtime result, episode identity or
allowlist from being mixed across source-package versions.

**Safety:** Source keys, labels and URI text are bounded and reject C0, DEL and
C1 controls. Malformed, user-info-bearing, non-HTTP(S) or allowlist-external
URIs are discarded with stable generic diagnostics. DASH and media-segment
records are not presented as playable outputs because the current resolver
defers or rejects them. URI query strings, cookies, headers and complete URLs
are never included in diagnostics or persistence; the normalizer performs no
I/O, persistence, WebView interaction or playback-session creation. The
package-specific source policy remains authoritative and no provider/site is
hard-coded in the shared executor.

## ADR-032 — Select one normalized source without implicit fallback

**Status:** Accepted

**Decision:** The Application layer uses one deterministic
`SourcePlaybackRouteSelector` for a normalized playable-source result. For an
available result it requires every candidate to retain the requested
`SourceEpisodeIdentity` and package identity, selects an exact preferred
source key when present, and otherwise selects the first candidate in source
order. An absent preferred key returns `preferredSourceNotFound` rather than
silently switching sources. Non-available runtime states propagate without a
route, and unsupported direct candidates or invalid normalized identities fail
closed. The returned `SourcePlaybackRoute` preserves package, version and
program provenance but is not a `PlaybackSession`.

**Reason:** Source selection is application policy, not a provider-specific
branch and not session construction. An explicit no-fallback result lets the
UI or a future coordinator distinguish a requested source failure from an
automatic source choice, while the existing session resolver remains the one
authority for the final playback handoff.

**Safety:** Selection is synchronous and has no I/O, persistence, WebView,
player or retry side effects. It validates all candidates before selecting one
so a malformed mixed-episode list cannot be partially trusted. Route and
selection result invariants reject missing routes, inconsistent package
identity and false selected states; no URI, cookie, header or raw error text
is added to the selection result.

## ADR-033 — Bridge a selected source to the authoritative session resolver

**Status:** Accepted

**Decision:** The Application layer uses one deterministic
`SourcePlaybackSessionRequestBuilder` to convert a selected
`SourcePlaybackRoute` into a `PlaybackSessionResolutionRequest`. The builder
requires an exact package manifest identity and declared program, revalidates
the package `SourceSecurityPolicy` against both the media and page URI, binds
the supplied `AdRemovalPlan` to the route episode, and accepts only a
non-negative caller-provided source event sequence. It maps the normalized
candidate into `WebMediaCandidate` with empty headers; cookies and other
capture authority are not invented by the package route. The typed result
returns a request only in `ready` state and otherwise returns a stable failure
status and code.

**Reason:** The source-package path and the existing capture path must converge
at one session authority. Rechecking package/version/program, episode plan and
URI policy at the handoff prevents a stale route or mismatched package from
reaching the resolver, while keeping source extraction independent from
player/session construction.

**Safety:** Request construction is synchronous and has no I/O, persistence,
WebView, player, retry or resolver side effect. It never copies source-package
headers or cookies into the request and does not log or persist media/page URI
values. A missing or mismatched identity, disallowed URI, unsupported kind or
invalid sequence produces no request; only the existing
`PlaybackSessionResolver` may create the authoritative `PlaybackSession`.

## ADR-034 — Compose source handoff options without bypassing the coordinator

**Status:** Accepted

**Decision:** The Application layer uses one
`SourcePlaybackOpenRequestBuilder` to compose a ready
`PlaybackOpenRequest` from the validated session request and the existing
coordinator options. It preserves the caller's `PlaybackProxyBudget`, loopback
address family, refresh leeway, automatic-refresh bound, episode duration and
explicit `BangumiEpisodeTarget` without creating parallel progress or sync
state. Invalid options or a rejected session request return a typed result with
no open request.

**Reason:** Source-package playback must enter the established
`PlaybackCoordinator.open` lifecycle so one coordinator owns the resolver,
proxy lease, player operation and progress binding. A small composition
boundary avoids duplicating those authorities or silently dropping resume and
Bangumi mapping inputs.

**Safety:** Composition is synchronous and has no resolver, coordinator,
player, proxy, network, WebView, persistence or retry side effect. The builder
validates option bounds before delegation and propagates only bounded stable
reason codes; it never logs or persists the underlying request, URI, cookie,
header or token values. The existing coordinator remains the only production
entry point that resolves a session or starts playback.

## ADR-035 — Enter source playback through the existing coordinator lifecycle

**Status:** Accepted

**Decision:** The Application layer uses one
`PlaybackCoordinatorSourceOpener` to delegate a validated source route to
`SourcePlaybackOpenRequestBuilder`, and calls `PlaybackCoordinator.open` only
when the builder returns a ready request. A rejected request returns a typed
`requestRejected` result with the bounded builder status and reason code; a
coordinator failure remains an exception from the coordinator's existing error
boundary.

**Reason:** The source-package path must join the established playback
lifecycle without creating a second session, proxy, player, generation or
progress authority. A narrow opener makes the integration point explicit while
keeping source-package validation and coordinator lifecycle responsibilities
separate.

**Safety:** The opener is synchronous until the single coordinator call and
has no independent I/O, retry, persistence or state machine. It never invokes
the coordinator for a rejected request, never catches a coordinator failure as
success, and exposes only bounded status metadata in its result diagnostics.
Concurrent opens, stale events, proxy cleanup and progress binding remain
owned by `PlaybackCoordinator`.

## ADR-036 — Compose the source playback path only for deterministic fixtures

**Status:** Accepted

**Decision:** The Application layer may use one
`SourcePlaybackFixturePipeline` to compose an explicit installed source package
and `SourceFixture` through `SourcePackageRuntime`,
`SourcePlayableSourceNormalizer`, `SourcePlaybackRouteSelector` and
`SourcePlaybackCoordinatorOpener`. The pipeline stops at the first non-available
typed stage result and returns only the stage, enum status and bounded reason
code. It does not add a live source adapter, HTTP/WebView capture, source
registry, UI route or alternative coordinator entry point.

**Reason:** The existing source contracts need one deterministic integration
proof before any future live provider work is considered. Keeping the
composition fixture-only makes the order, short-circuit behavior and
coordinator handoff testable without implying live availability or adding a
second playback lifecycle.

**Safety:** Runtime, normalization and route failures cannot invoke later
stages. A selected route is delegated to the existing coordinator opener, so
session resolution, proxy exposure, player lifecycle, progress binding,
generation checks and cleanup remain authoritative there. The pipeline does
not catch coordinator exceptions as success, persist fixture data, perform
network/WebView I/O or log source URIs, headers, cookies or tokens.

## ADR-037 — Persist source-package lifecycle state as an atomic snapshot

**Status:** Accepted

**Decision:** The in-memory `DeclarativeSourcePackageManager` remains the sole
package lifecycle authority, while `SourcePackageRepository` provides a
separate durable-state port. `DriftSourcePackageRepository` stores the complete
schema-v1 manifest plus status, consent and re-consent flags in the v6 SQLite
schema and replaces the snapshot in one transaction. `PersistentSourcePackageManager`
must load before mutation, revalidate every decoded package against the current
Wynime version and package-state invariants, serialize mutations, and restore
the last in-memory snapshot when a commit fails.

**Reason:** Package enablement and security consent must survive process death
without creating a second lifecycle authority or allowing a partially written
state to become executable after restart.

**Safety:** Persistence is limited to validated package declarations and
consent/status metadata. It performs no registry discovery, network access,
source execution or publisher-signature verification. Corrupt or inconsistent
records fail closed with bounded codes; an enabled record cannot carry pending
consent flags; and a failed replacement cannot leave the wrapper's memory
ahead of the committed snapshot. UI startup wiring, GitHub registry trust and
checksum/signature verification remain outside this task.

## ADR-038 — Keep registry discovery and package integrity as an offline contract

**Status:** Accepted

**Decision:** Define one strict schema-v1 `SourceRegistryIndex` containing a
bounded source root, opaque snapshot revision and one `SourceRegistryEntry` per
package. Each entry binds a lower-case package ID, semantic version, safe
relative package path and normalized SHA-256 digest. The index decoder rejects
unknown keys, duplicate IDs or paths, traversal/absolute paths, malformed
metadata and over-budget input. `SourceRegistryPackageVerifier` checks the
exact UTF-8 package bytes against that digest and then requires the decoded
manifest ID and exact version text to match the entry. Byte-oriented callers
are verified against their supplied raw bytes before strict UTF-8 decoding;
the String API is only a UTF-8 convenience wrapper.

**Reason:** A future repository adapter needs a deterministic, reviewable
mapping from one source website package to one immutable package location and
content digest before it can offer package updates. Keeping the index and
integrity check pure makes those invariants testable without introducing a
network client, filesystem traversal or a second package lifecycle authority.

**Safety:** The index is metadata, not repository trust. The current boundary
does not choose a GitHub repository or branch, resolve paths on disk, perform
HTTP/WebView I/O, verify Ed25519 publisher signatures or activate a package.
A verified digest does not bypass the existing package decoder, security
allowlist, resource budgets, consent/re-consent gates or persistent manager.
Future adapters remain responsible for separately authorized repository and
canonical-containment checks.

## ADR-039 — Compose registry artifacts atomically before lifecycle use

**Status:** Accepted

**Decision:** A future registry transport must first pass its raw index bytes
and exact full-relative-path package bytes through
`SourceRegistryArtifactCatalogLoader`. The loader requires exactly the indexed
artifact set, verifies every package against the same index snapshot and
returns an immutable all-or-nothing catalog. It retains decoded manifests but
not raw package bytes, and it does not install, activate, persist, access the
filesystem or network, or grant repository or publisher trust.

**Reason:** Keeping snapshot composition separate from transport and package
lifecycle prevents a missing, extra, stale or tampered artifact from becoming
a partial source-package state. The byte-oriented verifier remains the sole
integrity and identity check.

**Safety:** Missing, unexpected, malformed, oversized, integrity-failing and
metadata-mismatched artifacts produce stable non-secret codes. Catalog
construction has no side effects; consent, security-policy checks, persistence
and activation remain owned by the existing package manager boundaries.

## ADR-040 — Restore source-package state before exposing the Sources page

**Status:** Accepted

**Decision:** Application bootstrap creates one
`SourcePackageStartupController` around the existing
`PersistentSourcePackageManager`. The controller initializes at most once,
awaits the durable repository snapshot, maps repository and manager failures to
bounded diagnostic codes, and publishes only read-only installed and enabled
snapshots. `ResponsiveAppShell` observes the controller and `SourcesPage`
renders loading, failure, empty or installed-package states. Each installed
record displays its package ID, version and one of enabled, review-required,
re-consent-required or disabled; the page provides no install, enable, activate,
fetch or verify action.

**Reason:** Persisted source lifecycle state must be visible after restart so
the product does not present a false empty Sources page, while startup must not
turn the presentation layer into a package lifecycle or registry authority.

**Safety:** Startup performs only manager construction and durable snapshot
loading. It does not contact a registry, execute a source, verify publisher
signatures, mutate package state or expose raw storage errors. Disposal prevents
late completion notifications from updating the shell. Registry transport,
artifact integrity, consent and activation remain separate boundaries.

## ADR-041 — Fetch registry artifacts through a fixed GitHub raw adapter

**Status:** Accepted

**Decision:** `GitHubSourceRegistryRepository` is a read-only adapter for one
explicit owner, repository, ref and relative JSON index path. It builds only
HTTPS requests to `raw.githubusercontent.com`, rejects unsafe path and ref
segments, sends no authorization or cookie data, does not follow redirects,
and bounds every response plus the total package snapshot. It fetches the
index first, decodes its exact entries, fetches each indexed artifact
sequentially, and passes all bytes to `SourceRegistryArtifactCatalogLoader` for
all-or-nothing integrity and identity verification. Concurrent callers share
one in-flight load; closing the adapter rejects new work and prevents a late
load from composing a catalog.

**Reason:** The offline registry and exact-byte verifier need one concrete
repository boundary before a future package-management flow can consume a
remote snapshot. Keeping transport, snapshot composition and lifecycle
authority separate preserves deterministic testing and prevents a network
response from becoming an implicit installation or activation.

**Safety:** HTTP status, timeout, response-size, network, malformed-index,
missing-artifact and integrity failures cross the adapter as bounded
secret-safe diagnostic codes. The adapter is not connected to application
startup or the Sources UI, does not persist or execute packages, and does not
verify Ed25519 publisher signatures; SHA-256 remains an integrity check rather
than repository or author trust.

## ADR-042 — Verify optional publisher signatures without granting authority

**Status:** Accepted

**Decision:** `SourcePackageSignatureVerifier` may verify schema-v1 optional
Ed25519 signature metadata over the deterministic UTF-8 JSON package payload
with the recursive `signature` member omitted. Trusted public-key material is
supplied only by a separate `SourcePackageSignatureKeyResolver`, and the
resolver request and returned key must match the package ID, key ID and signer
ID exactly. Key lookup is bounded by a positive timeout. Unsigned packages
return an explicit unsigned result; untrusted keys, signer mismatches,
malformed or cryptographically invalid signatures, timeouts and resolver or
payload failures return stable bounded status/code pairs.

**Reason:** Publisher identity and package-byte integrity are useful separate
signals, but neither should become an implicit installation, consent or source
execution decision. Keeping canonical encoding, trusted-key lookup and
cryptographic verification behind typed boundaries makes the behavior
deterministic and testable without making a package self-authorize.

**Safety:** The verifier uses no package-provided key, credential, URL or
executable content and never logs or returns raw resolver/crypto errors. The
result is identity/integrity evidence only; signed and unsigned packages keep
the same allowlists, permissions, budgets, consent gates and lifecycle rules.
It has no mutable operation state, and a late resolver completion cannot
mutate package state. Startup, the Sources UI and the GitHub adapter do not
implicitly invoke it; future callers must separately decide how to present
or store this non-authoritative evidence.

## ADR-043 — Wire the optional registry catalog as read-only presentation state

**Status:** Accepted

**Decision:** `SourceRegistryController` is the sole Application-layer
coordinator for an optional compile-time configured
`GitHubSourceRegistryRepository`. It starts one background catalog read,
shares concurrent initialization and refresh calls, exposes only immutable
idle/loading/ready/failed state and bounded diagnostics, and replaces a
snapshot only after the complete `SourceRegistryArtifactCatalog` succeeds.
`WynimeApp` owns its lifecycle, `ResponsiveAppShell` observes it, and
`SourcesPage` presents registry revision, verified artifact identity and
installed/update/not-installed comparisons. The only registry UI mutation is
an explicit refresh that re-reads the catalog; no item can install, enable,
activate or execute a package.

**Reason:** A validated remote candidate list must be visible in the product
without turning transport responses into package lifecycle authority or
implying that a package is executable. A single controller also makes
single-flight, atomic replacement, disposal and stale-completion behavior
deterministic for the shell and tests.

**Safety:** Missing or invalid compile-time configuration leaves the optional
registry disconnected. The controller does not persist registry bytes, invoke
publisher-signature verification, mutate package state or execute source
rules. The fixed-host repository and artifact catalog remain responsible for
transport bounds, exact artifact sets, package identity and SHA-256 integrity;
the existing package manager remains responsible for consent, lifecycle and
runtime authority. No live registry snapshot or platform UI interaction is
claimed solely from widget or deterministic tests.

## ADR-044 — Require explicit source-package lifecycle consent

**Status:** Accepted

**Decision:** `SourcePackageStartupController` owns the application-facing
source-package lifecycle operations after its persistent snapshot is ready.
An explicit registry Install／Update action stores the exact validated
manifest in disabled, consent-pending state; it never enables or executes a
package. Enable and Disable require the exact package ID and version and are
serialized with durable snapshot replacement. Before enablement, Sources shows
the package identity plus its allowlisted domains, explicit permissions and
bounded resource budget, and only a positive user decision passes approval
and re-consent to the existing manager.

**Reason:** A verified registry candidate is useful only when a user can make
a visible, reversible lifecycle decision. Keeping staging, consent and
enablement separate preserves the existing package manager as the sole
lifecycle authority while allowing the Sources page to close the gap between
read-only discovery and an actually configured source.

**Safety:** No operation is implicit at startup or registry refresh. The
controller serializes mutations, rejects work before initialization or after
close, preserves the manager's atomic rollback on repository failure and
rejects stale package versions. UI failures expose one bounded generic message;
raw repository, signature, URL, cookie and token data never reaches the
presentation layer. Enabling does not invoke source runtime, WebView, network
requests or playback, and publisher signatures remain optional non-authoritative
evidence.

## ADR-045 — Compose fixture source search through an explicit coordinator

**Status:** Accepted

**Decision:** `SourceSearchCoordinator` is the pure Application-layer
composition boundary for the current fixture-only search path. Each request
must provide an explicit installed package, program ID, fixture and
subject-ID/title mapping. The coordinator evaluates plans in caller order,
preflights disabled, consent-pending and Wynime-incompatible packages without
calling the runtime, preserves one immutable normalization result per plan,
and aggregates only normalized rows. It returns bounded aggregate states for
available, partial, not-found, no-sources and failed outcomes with stable
reason codes.

**Reason:** The runtime and normalizer contracts are useful independently, but
without a typed composition boundary a future Search UI or live transport
could accidentally infer mappings, hide source-local failures or treat an
empty response as a successful search. One synchronous fixture coordinator
provides deterministic multi-source ordering and explicit failure semantics
before any network or UI wiring is authorized.

**Safety:** Query text, plan count, source count and aggregate rows are
bounded; duplicate plan identities are rejected; invalid requests fail before
runtime execution; runtime and normalizer exceptions become generic typed
diagnostics; and result diagnostics expose only counts and safe tokens. The
coordinator has no I/O, persistence, package lifecycle mutation, WebView,
source-code execution, playback or session capability. It owns no mutable
async generation, so stale-response protection is intentionally deferred to
the future asynchronous live-source boundary and cannot be claimed from this
fixture task.

## ADR-046 — Compose fixture episode listings through an explicit coordinator

**Status:** Accepted

**Decision:** `SourceEpisodeCoordinator` is the pure Application-layer
composition boundary for fixture-only episode listings. Each plan must
provide an explicit installed package, program ID, fixture and four-field
episode mapping. Plans are evaluated in caller order; consent/re-consent is
preflighted before disabled status, Wynime-incompatible packages are rejected
without runtime execution, one immutable normalization result is retained per
source, and only normalized `SourceEpisode` identities are aggregated. The
coordinator returns bounded available, partial, not-found, no-sources and
failed states with stable reason codes.

**Reason:** A normalized episode identity is the bridge from a source result
to later playback and watched-state work. Composing it explicitly prevents a
future caller from guessing line, subject or episode IDs, hiding one source's
failure behind another source's rows, or treating blocked sources as an empty
successful listing.

**Safety:** Plan count, source count and aggregate episode count are bounded;
duplicate plan identities are rejected; disabled, staged-consent and
incompatible packages never reach the runtime; runtime and normalizer
exceptions become generic typed diagnostics; and result diagnostics expose
only counts and safe tokens. The coordinator has no I/O, persistence, package
lifecycle mutation, WebView, source-code execution, playback or session
capability. It owns no mutable async generation, so stale-response protection
is deferred to a future asynchronous live-source boundary.

## ADR-047 — Compose fixture playable sources through an explicit coordinator

**Status:** Accepted

**Decision:** `SourcePlayableSourceCoordinator` is the pure Application-layer
composition boundary for fixture-only playable-source extraction. Each plan
must provide an explicit installed package, program ID, fixture, resolved
`SourceEpisodeIdentity` and five-field playable mapping. Plans are evaluated in
caller order; invalid episode/package identity, consent-pending, disabled and
Wynime-incompatible packages are rejected before runtime execution. One
immutable normalization result is retained per source, and only candidates
whose package/version, episode identity, supported kind and package URI policy
match the plan are aggregated. The coordinator returns bounded available,
partial, not-found, no-sources and failed states with stable reason codes.

**Reason:** A playable candidate is the last source-package value before
application route selection. Composing it explicitly prevents a future caller
from guessing the episode mapping, mixing package versions, exposing a
provider-returned identity or treating a blocked extraction as an empty
successful source list. The existing route selector and session resolver stay
as separate authorities.

**Safety:** Plan count, source count and candidate fields remain bounded by the
existing manifest, runtime and normalizer contracts; duplicate plan identities
are rejected; staged consent, disabled and incompatible packages never reach
the runtime; runtime and normalizer exceptions become generic typed
diagnostics; invalid normalizer result shapes and forged candidate identities
fail closed; and aggregate diagnostics expose only counts and safe tokens. The
coordinator has no I/O, persistence, package lifecycle mutation, WebView,
source-code execution, route selection, playback or session capability. It
owns no mutable async generation, so stale-response protection remains a
future asynchronous live-source boundary.

## ADR-048 — Compose source-local playback routes without cross-source identity synthesis

**Status:** Accepted

**Decision:** `SourcePlaybackRouteCoordinator` is the pure Application-layer
composition boundary from a `SourcePlayableSourceCoordinatorResult` to one
typed `SourcePlaybackRoute`. It evaluates source-local normalization results in
caller order and passes each available group's own normalized episode identity
to the existing `SourcePlaybackRouteSelector`. Without a preference, the
first selected route wins while source-local selection outcomes are retained.
With a preference, package ID, exact package version, program ID and source key
must match; a missing preferred source returns `preferredSourceNotFound` and
never falls back to another package. Blocked, not-found and failed source
states remain typed when no route can be selected.

**Reason:** `SourceEpisodeIdentity.sourceId` intentionally identifies the
source package. Treating two providers' identities as one episode would make a
route appear valid for the wrong source context. A dedicated composition
boundary can select across already verified source groups while preserving
that provenance and keeping the existing selector's single-source checks
authoritative.

**Safety:** The preference validates bounded package/program/source-key
identity; source groups and selection results remain immutable and bounded;
selector exceptions become a generic safe failure; and diagnostics expose
only counts and safe reason codes. The coordinator does not infer mappings,
modify packages, perform I/O, access WebView, resolve a `PlaybackSession`,
start a player, persist state or own asynchronous generation/stale-response
logic.

## ADR-050 — Compose a ready session request into a playback open request

**Status:** Accepted

**Decision:** `SourcePlaybackOpenRequestCoordinator` is the pure
Application-layer boundary after session-request composition. It accepts only
a ready `SourcePlaybackSessionRequestCoordinatorResult`, preserves its exact
`PlaybackSessionResolutionRequest`, validates the existing bounded open options
and creates one `PlaybackOpenRequest`. Non-ready session results are returned as
a typed `sessionRequestNotReady` outcome; invalid refresh leeway, automatic
refresh count and episode duration remain typed option failures.

**Reason:** The resolver request and the coordinator's open options have
different authorities. Keeping their handoff explicit prevents route or session
identity from being reconstructed while allowing the existing
`PlaybackOpenRequest` constructor to remain the source of truth for option
invariants before `PlaybackCoordinator.open` is reached.

**Safety:** The boundary never resolves a session, exposes a proxy capability,
starts a player, persists state, performs I/O, accesses WebView or owns
asynchronous generation. Diagnostics contain only status, request presence,
typed upstream statuses and bounded safe reason tokens; raw media URIs,
episode identities and exceptions do not cross the result boundary.

## ADR-051 — Open only a ready prepared request through PlaybackCoordinator

**Status:** Accepted

**Decision:** `SourcePlaybackPreparedRequestOpener` is the typed pure handoff
port from `SourcePlaybackOpenRequestCoordinatorResult` to the existing
`PlaybackCoordinator`. `PlaybackCoordinatorPreparedRequestOpener` is the
production implementation. It returns a typed request-not-ready result for
every non-ready prepared request and passes the exact `PlaybackOpenRequest` to
`PlaybackCoordinator.open` only when the upstream status is ready.

**Reason:** Request construction and playback execution have separate
authorities. A prepared-request opener makes the final admission check explicit
without reconstructing route or resolver data, while preserving the existing
coordinator's session/proxy/player lifecycle and stable downstream errors.

**Safety:** Non-ready requests cannot create a session or touch the coordinator.
The opener owns no resolver, proxy lease, player, progress store, retry policy,
persistence or asynchronous generation; downstream `PlaybackOperationException`
failures remain truthful and are not converted into successful results.

## ADR-052 — Compose the fixture source path through the staged playback boundaries

**Status:** Accepted

**Decision:** `SourcePlaybackFixturePipeline` is the fixture-only composition
boundary from one explicit `SourcePlayableSourcePlan` through
`SourcePlayableSourceCoordinator`, `SourcePlaybackRouteCoordinator`,
`SourcePlaybackSessionRequestCoordinator`,
`SourcePlaybackOpenRequestCoordinator` and
`PlaybackCoordinatorPreparedRequestOpener`. It stops at the first non-ready
typed stage and passes only a ready request to the existing
`PlaybackCoordinator` lifecycle.

**Reason:** The individual source/playback contracts are useful only when
their authority-preserving handoffs are exercised together. One deterministic
fixture pipeline proves the order and short-circuit behavior without treating
fixture data as live availability or reconstructing a session, proxy or player
outside the existing coordinator.

**Safety:** The pipeline creates no source I/O, WebView capture, persistence,
retry or independent generation state. Package identity, URI policy,
episode/ad-plan identity and bounded open options remain validated by their
existing typed boundaries. Diagnostics retain only the first failed stage,
enum status and safe reason token; source URIs, responses, cookies, headers and
raw exceptions are not exposed. Downstream stable playback exceptions remain
truthful and are not converted into a false pipeline success.

## ADR-049 — Compose selected routes into the existing playback resolver request

**Status:** Accepted

**Decision:** `SourcePlaybackSessionRequestCoordinator` is the pure
Application-layer boundary after route selection. It accepts a
`SourcePlaybackRouteCoordinatorResult`, short-circuits every non-selected
state, and passes a selected route with the exact `SourcePackageManifest`,
`AdRemovalPlan` and source event sequence to the existing
`SourcePlaybackSessionRequestBuilder`. Ready, builder-rejected and safe-failed
outcomes remain typed; the existing builder remains the authority for exact
package/version/program, episode, URI-policy and non-negative sequence checks.

**Reason:** Route selection and resolver-request construction have separate
contracts. A small composition boundary makes their handoff explicit without
duplicating identity or security validation, while keeping the route result's
source-local provenance and the resolver's existing request shape intact.

**Safety:** Non-selected routes never reach the builder; builder exceptions are
collapsed to a bounded diagnostic token; and result diagnostics expose only
status, presence and safe enum/token fields. The coordinator does not resolve a
`PlaybackSession`, create a session, expose a proxy lease, persist state,
perform I/O, access WebView, start a player or own asynchronous generation and
stale-response state.

## ADR-053 — Admit live capture through a generation-scoped typed boundary

**Status:** Accepted

**Decision:** `SourceLiveCapturePort` is the typed platform/WebView port for a
future bounded capture operation. `SourceLiveCaptureCoordinator` binds each
request to the exact source package ID, version and program, revalidates the
returned `WebCaptureSnapshot` against the request's allowlist, permissions,
ordering and budgets, and exposes only a completed accepted snapshot or a
stable redacted outcome. A newer capture increments the generation and
supersedes older work; `close()` invalidates all pending generations.

**Reason:** The existing WebView accumulator proves bounded observation but
does not provide an Application-owned asynchronous admission or stale-result
boundary. Adding that boundary now makes future live wiring unable to publish
an unvalidated or late snapshot, without turning fixture contracts into live
source execution or duplicating playback/session authority.

**Safety:** Incomplete, over-budget, disallowed, out-of-order or
candidate/cookie-inconsistent snapshots fail closed. Platform exceptions are
collapsed to `capture_failed`, and superseded or closed completions retain no
snapshot. The coordinator owns only in-memory capture result state; it does
not perform network I/O, execute source package code, persist cookies or
snapshots, access Flutter/WebView, resolve playback or create a
`PlaybackSession`. Live source availability and UI integration remain outside
this decision.

## ADR-054 — Bridge live capture through one keyed WebView session

**Status:** Accepted

**Decision:** The platform implementation of `SourceLiveCapturePort` is
`InAppWebViewSourceLiveCapturePort`. It owns one pending completion and one
monotonic generation, while `buildView()` mounts the existing
`InAppWebViewCaptureView` under a generation key. The reusable
`InAppWebViewSourceLiveCapture` widget creates one
`SourceLiveCaptureCoordinator` per mounted request, reports at most one typed
result, and closes the coordinator and port before request replacement or
widget disposal. The lower-level WebView exposes a separate fatal-capture
callback for bootstrap, final-URI and cookie failures; individual policy
blocks remain security notifications and do not manufacture a completed
snapshot.

**Reason:** TASK-031 established the Application admission and stale-result
contract, but the existing WebView view had only a callback and no typed
completion owner. A small platform session bridge makes the callback usable by
that port without introducing a second source executor, playback lifecycle or
Search-specific integration.

**Safety:** Runtime-unavailable, fatal platform and view-construction failures
complete the port with bounded error codes. Duplicate, superseded and late
callbacks cannot complete the current operation. The bridge retains no
snapshot after close, does not persist cookies, does not execute package
code, and forwards no upstream source authority outside the existing typed
capture models. Live source eligibility, HTTP transport, package activation,
playback resolution and product-route integration remain separate tasks.

## ADR-055 — Admit live capture only from an enabled package

**Status:** Accepted

**Decision:** `SourceLiveCapturePackageCoordinator` is the application
boundary from one `InstalledSourcePackage` and explicit program/request plan
to the existing `SourceLiveCaptureCoordinator`. It checks consent and
re-consent before disabled state, exact Wynime compatibility and package-owned
program identity. The supplied `WebCaptureRequest` must use the exact
installed package `SourceSecurityPolicy` and an allowlisted initial URI. Only
a ready plan is converted to `SourceLiveCaptureRequest`; all other outcomes
remain bounded package-admission diagnostics and never contact the platform
port.

**Reason:** TASK-032 provides a safe keyed WebView port, but a caller could
otherwise mount it with a package that is disabled, awaiting consent,
incompatible or using a broader policy. A small preflight keeps package
lifecycle authority in `SourcePackageManager`, keeps generation and snapshot
validation in `SourceLiveCaptureCoordinator`, and makes the next live-source
handoff explicit without creating a second executor.

**Safety:** The coordinator does not execute source rules, perform live HTTP,
persist cookies or snapshots, mutate package lifecycle, access Search, resolve
playback or create a `PlaybackSession`. Package preflight failures do not
reach the port; platform failures remain under the existing typed capture
result boundary; and package IDs, versions, program IDs and reason codes are
bounded by their existing model contracts.

## ADR-056 — Mount live capture only after package admission

**Status:** Accepted

**Decision:** `InAppWebViewInstalledSourceLiveCapture` is the package-aware
platform surface for live capture. It accepts an explicit
`SourceLiveCapturePackagePlan`, emits its typed admission result, and mounts
the lower-level `InAppWebViewSourceLiveCapture` surface only for a ready
admission. It owns no package lifecycle mutation; it composes the existing
`SourceLiveCapturePackageCoordinator`, `InAppWebViewSourceLiveCapturePort` and
generation-scoped capture coordinator. A plan replacement or widget disposal
closes the current authorities before a new plan can publish a result.

**Reason:** TASK-033 made package eligibility explicit, but a platform caller
could still bypass that boundary by mounting the lower-level request-based
surface directly. Making the installed-package surface the high-level handoff
prevents disabled, consent-pending, incompatible or broader-policy requests
from reaching a WebView while preserving the existing capture authorities.

**Safety:** Non-ready admission emits only a bounded package result and does
not build the capture view or start a port capture. Ready captures preserve the
exact admitted request, package provenance and existing stale-result/close
semantics. This surface does not execute source rules, perform HTTP outside
WebView, persist cookies or snapshots, connect Search, resolve playback or
create a `PlaybackSession`; mounting it is not evidence of live source
availability.

## ADR-057 — Preserve accepted live-capture provenance through playable normalization

**Status:** Accepted

**Decision:** `SourceLiveCaptureSnapshotValidator` is the shared pure
revalidation authority for downstream consumers of a
`SourceLiveCaptureResult`. `SourceLiveCapturePlayableSourceCoordinator` accepts
the exact `SourceLiveCaptureRequest` and result pair, rechecks installed
package lifecycle, compatibility, program identity, episode ownership and
semantic security-policy equality, then invokes the shared validator before
normalizing candidates. Every captured candidate requires one explicit
candidate-index/source-key/label mapping. Supported HLS and direct
audio/video candidates become `SourceLiveCapturePlayableSource` values that
retain the exact `WebMediaCandidate`; the accepted capture snapshot retains
the cookie set once for a later session handoff. Unsupported DASH and
media-segment candidates are diagnosed and never emitted. The validator and
`WebCaptureAccumulator` use the same pure `WebCaptureCandidateClassifier`, so
candidate kind cannot be forged between capture admission and consumption. The
validator also rebuilds the accumulator's deterministic first-observation
candidate list from the classifier's kind plus exact normalized URI key,
rechecks its unique-candidate budget, and requires candidate cardinality,
insertion order, kind, normalized URI, headers and source-event sequence to
match the reconstructed list. A repeated later event, omission, duplication,
reordering or impossible over-budget completed snapshot therefore cannot
provide a candidate's authority.

**Reason:** The package-aware WebView surface now produces an accepted capture,
but the existing fixture normalizer intentionally has no headers, cookies or
event provenance. A narrow live-only handoff keeps that fixture contract
unchanged while preventing the live path from dropping the authorities needed
by the existing playback resolver. Explicit metadata avoids deriving source
identity, labels or page identity from untrusted media URLs.

**Safety:** Package, request, result and episode mismatches fail closed. A
forged completed snapshot is revalidated for allowlist, permission, event
ordering, candidate derivation, candidate kind, first-observation provenance,
candidate-list completeness and all capture budgets before any playable value
is returned. Non-available
and all-invalid outcomes retain no snapshot;
diagnostics expose only bounded codes, counts, schemes, hosts and header names.
The coordinator performs no source HTTP, package execution, persistence, route
selection, session resolution or player work. The existing fixture route and
session gates remain separate until a later explicitly scoped live handoff.

## ADR-058 — Preserve accepted live capture through source-local route selection

**Status:** Accepted

**Decision:** `SourceLiveCapturePlaybackRouteCoordinator` is the pure live-only
route boundary from an accepted `SourceLiveCapturePlayableSourceResult` to one
source-local route. It rechecks installed-package consent, enabled state,
compatibility and program identity, then requires exact package/version/program
identity between the playable result and its accepted captured snapshot. The
complete snapshot candidate list is revalidated against the ordered capture
events using the shared classifier: every source-event sequence must resolve to
an event with the same classified kind, exact classifier-normalized URI text
(including no candidate-owned fragment), headers and sequence, and the
first-observation candidate list must match the expected candidate at the same
index, in cardinality, order and fields. Every playable candidate is then checked against its snapshot index and
exact kind, URI, headers and source-event sequence; its package-owned episode,
final page URI, supported candidate kind and media/page allowlist are checked
before selection. Without a preference, the first source is selected. A
preference must match package, version, program and source key exactly; no
implicit or cross-package fallback is allowed.

The selected route retains the exact `SourceLiveCapturePlayableSource` and the
same accepted `SourceLiveCaptureResult` reference. Candidate headers and event
sequence therefore remain available for the next session handoff, while cookies
remain only in the one accepted capture snapshot and are not copied into source
rows or diagnostics. Non-available playable states map to typed route states;
invalid capture/provenance returns a bounded failure and no route.

**Reason:** TASK-035 preserves live candidate authority, but the existing
fixture route intentionally strips headers, cookies and capture provenance.
This separate handoff connects only the already accepted live value to a
source-local route and leaves fixture routing unchanged. Retaining the capture
result reference avoids losing the cookie snapshot when the selected route is
passed onward, without creating a second secret-bearing representation.

**Safety:** This boundary performs no source HTTP, package execution, WebView
access, persistence, `PlaybackSession` resolution, player work or asynchronous
generation. Exact event/candidate provenance rejects missing or forged event
links, URI/header/kind mismatches, wrong indexes, duplicate indexes, duplicate
source keys, unsupported DASH/segment kinds, disallowed media/page URIs and
mismatched episode/page identities. Result and route diagnostics expose only
bounded identities, counts, schemes, hosts and header names; cookie values and
media/header values are never echoed.

## ADR-059 — Preserve live capture through the resolver-request handoff

**Status:** Accepted

**Decision:** `SourceLiveCapturePlayableSourceResult` retains the exact
`SourceLiveCaptureRequest` together with its accepted capture result when the
result is available. `SourceLiveCapturePlaybackRoute` carries that same pair
and the selected `SourceLiveCapturePlayableSource`. The pure
`SourceLiveCapturePlaybackSessionRequestCoordinator` accepts only a selected
live route from an enabled, consent-complete and compatible installed package,
revalidates the request policy and completed snapshot with
`SourceLiveCaptureSnapshotValidator`, rechecks the selected candidate against
the validated snapshot and package allowlist, and constructs one
`PlaybackSessionResolutionRequest`. It uses the candidate's exact headers and
source-event sequence, passes the single captured cookie snapshot for the
existing resolver to URI-filter and an explicit capture user-agent when
present, and requires the supplied
`AdRemovalPlan` to match the route episode.

**Reason:** TASK-036 preserved the accepted live candidate and capture result
through source-local selection, but the fixture session builder intentionally
strips headers, cookies and live provenance. Carrying the exact request/result
pair and using a separate live handoff prevents the live path from silently
falling back to the fixture contract or reconstructing credentials from a
media URL. The existing resolver remains the sole session-construction
authority.

**Safety:** Consent, disabled, incompatible, route, package, version, program,
request-policy, initial-URI, snapshot, candidate-index, candidate provenance,
episode/page, supported-kind, package allowlist and ad-plan mismatches fail
closed with bounded diagnostics and no request. Revalidation repeats the full
snapshot event/candidate/cookie budget and first-observation checks. The
handoff retains no second cookie/header store, performs no source I/O, package
execution, persistence, proxy or player work, and does not alter the existing
fixture session coordinator. Cookie values, raw media URLs and raw exceptions
never enter diagnostics.

## ADR-060 — Compose the accepted live resolver request into an open request

**Status:** Accepted

**Decision:** `SourceLiveCapturePlaybackOpenRequestCoordinator` is the pure
live-only handoff from a ready
`SourceLiveCapturePlaybackSessionRequestResult` to one existing
`PlaybackOpenRequest`. It preserves the exact
`PlaybackSessionResolutionRequest` reference and forwards the authoritative
proxy budget, loopback address family, refresh leeway, bounded automatic
refresh count, optional episode duration and optional Bangumi episode mapping.
Non-ready live session states are returned as typed `sessionRequestNotReady`
results with their live session and route statuses retained; invalid open
options are typed failures and never produce an open request.

**Reason:** TASK-037 preserves the accepted live capture through the existing
resolver-request contract, while the fixture open coordinator intentionally
accepts only fixture session status types. A separate live boundary prevents
the live path from silently entering the fixture result contract or dropping
the captured candidate, headers, cookies and user-agent already retained by
the exact resolver request. The existing `PlaybackCoordinator` remains the
sole authority for resolving and opening a session.

**Safety:** A ready result requires the live session status and route status to
both be ready/selected, and a non-ready result cannot carry an open request.
Status-specific upstream invariants reject fabricated route/session
combinations and all diagnostic values are bounded safe tokens. The
coordinator performs no resolver invocation, `PlaybackSession` creation,
proxy lease, player work, source I/O, persistence or asynchronous generation;
it also emits no raw request, cookie, header, URL or exception data.

## ADR-061 — Open only a ready live request through PlaybackCoordinator

**Status:** Accepted

**Decision:** `SourceLiveCapturePlaybackPreparedRequestOpener` is the typed
live-only prepared-request port after ADR-060. Its concrete
`PlaybackCoordinatorLiveCapturePreparedRequestOpener` returns a bounded typed
not-ready result for every non-ready
`SourceLiveCapturePlaybackOpenRequestCoordinatorResult` and passes only the
exact ready `PlaybackOpenRequest` to the existing `PlaybackCoordinator.open`.
An opened result contains only the returned `PlaybackSession`; a rejected
result retains the live open/session/route statuses and a bounded reason code.

**Reason:** The live path now reaches the same open-request contract as the
fixture path, but its result statuses and capture provenance are intentionally
separate. A live-specific prepared port makes the final admission explicit
without converting live state into the fixture result model or adding a second
playback lifecycle.

**Safety:** Non-ready results never invoke the resolver, proxy or player. The
opener owns no session, proxy lease, progress, retry, persistence or generation
state and does not catch or rewrite the existing coordinator's stable playback
errors. Result invariants reject opened/rejected mixtures, ready upstream
statuses in a rejection, impossible live route/session combinations and unsafe
diagnostic tokens. Diagnostics expose no request, cookie, header, URL or raw
exception data; stale-operation and concurrent-open behavior remains governed
by `PlaybackCoordinator`.

## ADR-062 — Compose the accepted live capture playback chain explicitly

**Status:** Accepted

**Decision:** `SourceLiveCapturePlaybackPipeline` is the live-only application
composition from one `SourceLiveCapturePlayableSourcePlan` through the existing
live playable-source, route, session-request, open-request and prepared-request
boundaries. It short-circuits at the first non-ready typed stage and returns a
bounded `SourceLiveCapturePlaybackPipelineResult`; only a ready result reaches
`SourceLiveCapturePlaybackPreparedRequestOpener`.

**Reason:** The live path had each handoff independently implemented but no
single composition boundary that proved their ordering and common execution
path. A pipeline makes the accepted-capture-to-player sequence testable while
keeping capture admission, WebView generation, session lifecycle and player
ownership in their established authorities.

**Safety:** The pipeline never performs source I/O or package execution and
does not retain request, snapshot, cookie, header, URL or raw exception data in
its result. Its result contains one opened session or one typed stage failure;
`PlaybackCoordinator` remains the sole resolver, proxy, player, lifecycle,
progress and stale-operation authority. Downstream stable playback errors are
propagated unchanged.

## ADR-063 — Pair accepted package capture with an explicit playable plan

**Status:** Accepted

**Decision:** `SourceLiveCapturePlayableSourcePlanCoordinator` is the pure
Application handoff from one `SourceLiveCapturePackagePlan`, its ready package
admission and one `SourceLiveCaptureResult` to the existing
`SourceLiveCapturePlayableSourcePlan`. A ready admission must carry the exact
`WebCaptureRequest` from the package plan; the capture result must match the
admitted package/version/program; the episode must belong to that package; and
one explicit candidate mapping must cover every captured candidate. The
result exposes either one plan or one bounded package/capture/plan failure.

**Reason:** The package-aware WebView surface already emits typed admission and
capture callbacks, while the live playback pipeline previously began only
after a caller manually reconstructed a playable-source plan. Making this
pairing explicit prevents a caller from substituting a different request,
capture identity, episode or incomplete candidate mapping before the existing
live normalizer receives the value.

**Safety:** Non-ready admission and capture outcomes never produce a plan or
retain capture data in the result. Package lifecycle, program and request
policy are checked again; mismatched request/result identity, package state,
episode source or candidate mappings fail closed with bounded reason codes.
The coordinator performs no source I/O, WebView access, package execution,
normalization, routing, session resolution, persistence or player work; capture
generation and late-result handling remain owned by the existing package and
WebView authorities. The resulting plan retains the exact request/result pair
only in memory for the next already-reviewed live normalization boundary.

## ADR-064 — Join accepted live capture planning with the playback pipeline

**Status:** Accepted

**Decision:** `SourceLiveCapturePlaybackEntryPoint` is the pure live
Application entry that first invokes
`SourceLiveCapturePlayableSourcePlanCoordinator` with one package plan,
admission, capture result, episode and explicit candidate mappings, then
invokes `SourceLiveCapturePlaybackPipeline` only for a ready plan. It returns
one bounded entry result: a successful result contains only the
`PlaybackSession`, while a rejection contains either the typed plan result or
the typed pipeline result.

**Reason:** TASK-040 and TASK-041 each made their adjacent live handoff
explicit, but callers still had to compose package-aware capture planning and
playback manually. One entry boundary proves that a failed admission,
capture, episode or mapping cannot accidentally reach normalization or the
existing playback lifecycle, while the accepted path uses the already-tested
ordering and single coordinator authority.

**Safety:** The entry point performs no WebView access, source I/O, package
execution, persistence, retry or asynchronous generation management. A plan
rejection cannot invoke the pipeline; a pipeline rejection cannot retain the
ready plan or open request in the entry result. The existing
`PlaybackCoordinator` remains the sole resolver, session, proxy, player,
lifecycle, progress and stale-operation authority, and downstream stable
playback errors propagate unchanged. Diagnostics expose only bounded status,
stage and safe reason values, never capture snapshots, cookies, headers, URLs
or raw exception text.

## ADR-065 — Establish a bounded live source HTTP GET boundary

**Status:** Accepted

**Decision:** `SourceLiveHttpRequestCoordinator` admits only an explicit
`SourceHttpRequest` paired with an enabled, consent-complete, Wynime-compatible
source program and an exactly matching semantic `SourceSecurityPolicy`.
`SourceLiveHttpRequestExecutor` passes a ready request to
`SourceHttpTransport` once; `DartIoSourceHttpTransport` follows only bounded
allowlisted redirects and returns a bounded UTF-8 `SourceHttpResponse` on a
2xx result. It reuses the existing direct public-address-pinned upstream
client, and no failed result retains response data.

**Reason:** The source-package path had fixture evaluation and WebView capture
boundaries, but no typed live HTTP request/response handoff. Introducing the
smallest idempotent GET contract makes live transport testable without
guessing provider behavior or coupling the package executor to `dart:io`.

**Safety:** Request construction rejects fragments, disallowed URIs,
credential/hop-by-hop headers, bodies and unbounded headers/timeouts. Redirect
targets are checked against the same package policy and redirect budget.
Response bytes are capped before UTF-8 exposure, non-2xx bodies are discarded
without entering the result, and raw transport exceptions collapse to stable
safe codes. The boundary adds no POST, source-rule execution, provider
adapter, Search UI, persistence, retry, WebView or playback authority.

## ADR-066 — Compose one bounded live response with the declarative runtime

**Status:** Accepted

**Decision:** `SourceLiveHttpPackageRuntime` composes a completed
`SourceLiveHttpRequestExecutor` result with the existing
`SourcePackageRuntime` by constructing exactly one in-memory `SourceFixture`.
The fixture uses the explicit request URI as its initial URI, carries the
bounded redirect chain and body, and is evaluated once. Only the immutable
`SourceRuntimeResult` crosses the boundary. Admission or transport failures
become safe typed runtime failures, evaluator exceptions are collapsed, and
package ID, exact version and program ID are checked before evaluated records
are returned.

**Reason:** TASK-043 established a safe live HTTP response boundary, while
the declarative evaluator already provided the package-rule and budget
authority. This smallest composition proves that a live response can enter
the existing rule path without inventing request construction, provider
adapters or a second evaluator.

**Safety:** Non-completed executions never invoke the evaluator and expose no
request or response data. A completed response is not persisted or logged by
this boundary; headers and content type are not copied into the fixture. Any
unexpected executor/evaluator exception, missing response or identity mismatch
fails closed with zero records and a bounded diagnostic. The composition adds
no request inference, POST, source-provided executable code, normalization,
Search UI, WebView, persistence, retry or playback authority.

## ADR-067 — Compose bounded live source search with the existing normalizer

**Status:** Accepted

**Decision:** `SourceLiveSearchCoordinator` accepts at most 32 explicit
`SourceLiveSearchPlan` values. Each plan carries a package-admitted
`SourceLiveHttpRequestPlan` and an explicit `SourceSearchFieldMapping`. The
coordinator executes plans in caller order through
`SourceLiveHttpPackageRuntime`, invokes the existing `SourceSearchNormalizer`
once per completed source result, preserves source-local typed states and
aggregates only normalized `SourceSearchResult` values through the existing
`SourceSearchCoordinatorResult` contract. Query text is not used to construct
or alter a request.

**Reason:** TASK-044 proved the live response can safely enter the declarative
rule runtime. This task connects that result to the already-reviewed search
normalization and multi-source aggregation boundary without adding a provider
adapter or Search UI coupling.

**Safety:** Plan count and duplicate package/version/program identities are
rejected before network I/O. Admission and transport failures remain
per-source typed states; runtime/normalizer exceptions, identity mismatch and
invalid normalized shapes fail closed without records. A monotonic generation
invalidates older pending searches when a newer call starts, and `close`
invalidates pending/future calls. The coordinator does not cancel or close the
lower transport, so it cannot create a second I/O lifecycle authority. It adds
no request inference, persistence, retry, WebView, playback, provider or
source-provided executable code.

## ADR-068 — Compose bounded live episode listings with the existing normalizer

**Status:** Accepted

**Decision:** `SourceLiveEpisodeCoordinator` accepts at most 32 explicit
`SourceLiveEpisodePlan` values. Each plan carries a package-admitted
`SourceLiveHttpRequestPlan` and an explicit `SourceEpisodeFieldMapping`. The
coordinator executes plans in caller order through
`SourceLiveHttpPackageRuntime`, invokes the existing
`SourceEpisodeNormalizer` once per completed source result, preserves
source-local typed states and aggregates only normalized `SourceEpisode`
values through the existing `SourceEpisodeCoordinatorResult` contract.

**Reason:** TASK-045 connected the live response to deterministic search
normalization. This task carries the same safe response-to-rule boundary into
episode listings without deriving episode identity, adding provider adapters or
coupling the result to episode UI.

**Safety:** Plan count and duplicate package/version/program identities are
rejected before network I/O. Admission and transport failures remain typed
per-source states; runtime/normalizer exceptions, identity mismatch and
invalid normalized shapes fail closed without episodes. A monotonic generation
invalidates older pending listings when a newer call starts, and `close`
invalidates pending/future calls. The coordinator does not cancel or close the
lower transport and adds no request inference, persistence, retry, WebView,
playback, provider or source-provided executable code.

## ADR-069 — Compose bounded live playable-source listings with the existing normalizer

**Status:** Accepted

**Decision:** `SourceLivePlayableSourceCoordinator` accepts at most 32 explicit
`SourceLivePlayableSourcePlan` values. Each plan carries a package-admitted
`SourceLiveHttpRequestPlan`, an already-resolved `SourceEpisodeIdentity` and an
explicit `SourcePlayableSourceFieldMapping`. The coordinator executes plans in
caller order through `SourceLiveHttpPackageRuntime`, invokes the existing
`SourcePlayableSourceNormalizer` once per completed source result, preserves
source-local typed states and aggregates only validated normalized candidates
through the existing `SourcePlayableSourceCoordinatorResult` contract.

**Reason:** TASK-046 connected live responses to deterministic episode
normalization. This task carries the same bounded response-to-rule boundary
into playable-source extraction while keeping episode identity explicit and
leaving route selection and the playback-session resolver authoritative.

**Safety:** Plan count and package/version/program/episode identity duplicates
are rejected before network I/O. Invalid episode identity, admission or
transport failure, runtime/normalizer exception, normalized identity/shape,
candidate kind or package URI-policy mismatch fails closed without playable
candidates. A monotonic generation invalidates older pending listings when a
newer call starts, and `close` invalidates pending/future calls. The coordinator
does not cancel or close the lower transport and adds no request inference,
persistence, retry, WebView, route, session, playback, provider or
source-provided executable code.

## ADR-070 — Compose live playable sources with the source-local route

**Status:** Accepted

**Decision:** `SourceLivePlaybackRouteCoordinator` accepts a bounded,
caller-ordered list of `SourceLivePlayableSourcePlan` values together with the
matching `SourcePlayableSourceCoordinatorResult`. It revalidates plan/result
alignment, package lifecycle, Wynime compatibility, declared program, exact
GET request policy, episode identity, normalized candidate shape, supported
candidate kind, package URI policy and the flattened aggregate before
delegating source selection to the existing `SourcePlaybackRouteCoordinator`.
The selected `SourceLivePlaybackRoute` retains the exact installed package and
bounded request provenance for the next handoff; it does not retain the live
response body, mapping, cookies or tokens.

**Reason:** TASK-047 now produces typed live playable candidates, but the
fixture route coordinator alone does not retain the package-admission/request
provenance needed by a live HTTP handoff. This is the smallest composition that
connects the accepted live result to the existing deterministic route selector
without creating a second selection policy or cross-source identity.

**Safety:** Duplicate and over-bound plan inputs, stale or reordered result
groups, package/request/candidate mismatches, invalid aggregate shape and
unsupported or disallowed candidates fail closed with bounded reason codes.
Preference matching remains exact and never falls back across packages. The
coordinator performs no source I/O, persistence, retry, session resolution,
player work or asynchronous generation management; the existing route
selector and downstream playback authorities remain unchanged.

## ADR-071 — Compose the live HTTP route with the session request contract

**Status:** Accepted

**Decision:** `SourceLivePlaybackSessionRequestCoordinator` accepts only a
selected `SourceLivePlaybackRouteResult` and an explicit `AdRemovalPlan` plus
non-negative source-event sequence. It rechecks the installed package's
consent/re-consent, enabled lifecycle, Wynime compatibility, declared program,
package/version/episode identity and exact package-admitted GET request before
delegating to the existing `SourcePlaybackSessionRequestBuilder`. A ready
result must preserve the live route's exact episode, page URI, media URI and
supported kind, use the same ad-removal plan and policy, and contain no
candidate headers, cookies, user-agent, refresh callback or track data because
the live HTTP path has no WebView capture authority. Builder rejections and
exceptions remain bounded typed results.

**Reason:** TASK-048 establishes a source-local live route with the exact
installed package and request provenance, but it intentionally stops before
the resolver request. This separate composition connects that accepted route
to the existing session-request builder without reimplementing selection or
using the capture-specific session model that carries WebView event and cookie
authority.

**Safety:** Non-selected routes never invoke the builder. Lifecycle, identity,
request-policy, supported-kind, candidate URI, source-event-sequence and
ad-removal mismatches fail closed without a request. A forged ready builder
output is rejected unless every live route field and the empty credential/data
boundary matches exactly. The coordinator performs no source I/O, persistence,
resolver invocation, `PlaybackSession` creation, proxy or player work, and it
owns no asynchronous generation or retry state. Diagnostics expose only
bounded statuses and safe reason codes.

## ADR-072 — Compose the live HTTP session request into an open request

**Status:** Accepted

**Decision:** `SourceLivePlaybackOpenRequestCoordinator` accepts a
`SourceLivePlaybackSessionRequestResult` and creates one existing
`PlaybackOpenRequest` only when the live session result is ready. It preserves
the exact `PlaybackSessionResolutionRequest` reference and forwards the
caller-provided proxy budget, loopback address family, refresh leeway, bounded
automatic-refresh count, optional episode duration and optional Bangumi target.
Every non-ready live session state is returned as a typed
`sessionRequestNotReady` result retaining its session, route and builder
statuses. Invalid open options and construction failures produce no request.

**Reason:** TASK-049 establishes the live HTTP route-to-session-request
boundary, but the existing fixture open coordinator accepts a different result
type. This separate live handoff reaches the shared `PlaybackOpenRequest`
contract without converting live state into the fixture model or starting
resolver/session/player work.

**Safety:** Ready output requires ready live session, selected route and ready
builder status; non-ready output cannot contain an open request. The upstream
live status/route/builder combination is rechecked before it crosses the
boundary, with route-not-selected requiring no builder status, request
rejection requiring a non-ready builder status, and lifecycle blocks requiring
no route or builder status. Option failures retain only ready status metadata.
The coordinator performs no resolver invocation, `PlaybackSession` creation,
proxy lease, player work, source I/O, persistence or asynchronous generation;
diagnostics contain only bounded statuses and reason tokens.

## ADR-073 — Open only a ready live HTTP request through PlaybackCoordinator

**Status:** Accepted

**Decision:** `SourceLivePlaybackPreparedRequestOpener` is the typed
live-HTTP prepared-request port after ADR-072. Its concrete
`PlaybackCoordinatorLivePlaybackPreparedRequestOpener` returns a bounded typed
`requestNotReady` result for every non-ready
`SourceLivePlaybackOpenRequestCoordinatorResult` and passes only the exact
ready `PlaybackOpenRequest` to the existing `PlaybackCoordinator.open`. An
opened result contains only the returned `PlaybackSession`; a rejected result
retains the live open, session, route and builder statuses plus a bounded reason
code.

**Reason:** TASK-050 reaches the shared open-request contract, but the live
HTTP result types must remain distinct from fixture and WebView-capture result
types. A live-specific prepared port makes the final admission explicit
without dropping live state or adding a second playback lifecycle.

**Safety:** Non-ready results never invoke the resolver, proxy or player. The
opener owns no session, proxy lease, progress, retry, persistence or generation
state and does not catch or rewrite the existing coordinator's stable playback
errors. Result invariants reject opened/rejected mixtures, ready upstream
statuses in a rejection, impossible live route/session/builder combinations and
unsafe diagnostic tokens. Diagnostics expose no request, cookie, header, URL or
raw exception data; stale-operation and concurrent-open behavior remains
governed by `PlaybackCoordinator`.

## ADR-074 — Compose the complete live HTTP playback pipeline

**Status:** Accepted

**Decision:** `SourceLivePlaybackPipeline` accepts a bounded snapshot of
caller-ordered `SourceLivePlayableSourcePlan` values, the explicit
`AdRemovalPlan`, a non-negative source-event sequence, a `PlaybackProxyBudget`
and an optional exact `SourcePlaybackRoutePreference`. It invokes the accepted
live playable-source coordinator, accepts only `available` or `partial`
aggregates, then passes the same snapshot through
`SourceLivePlaybackRouteCoordinator`,
`SourceLivePlaybackSessionRequestCoordinator`,
`SourceLivePlaybackOpenRequestCoordinator` and
`SourceLivePlaybackPreparedRequestOpener` in that order. Only the final ready
result can produce an opened session.

**Reason:** TASK-051 completed the final live HTTP handoff, but callers still
had to compose five independent typed boundaries and could accidentally reuse
a one-shot or mutated plan iterable between listing and route validation. This
is the smallest end-to-end composition that joins the accepted live path while
preserving each existing authority and the live/capture type separation.

**Safety:** The pipeline snapshots at most 32 plans before I/O, rejects an
over-bound or throwing iterable as a playable-stage failure, and forwards the
exact snapshot to both listing and route validation. Partial aggregates remain
eligible only when the route coordinator can select a valid source; no implicit
cross-package fallback is introduced. One non-ready typed status and a bounded
reason code identify the first failed stage. A negative source-event sequence
is not normalized or fabricated; the existing live session-request builder
rejects it at the session stage. The pipeline performs no persistence, retry,
resolver, proxy or player work of its own; live listing generation/close
semantics remain in the playable coordinator and session/proxy/player/stale
operation authority remains in `PlaybackCoordinator`. Diagnostics expose no
request, response, URI, cookie, header, token or raw exception data.

## ADR-075 — Materialize live plans only from schema-v2 package declarations

**Status:** Accepted

**Decision:** Schema-v1 source packages remain fixture-only and reject
`liveOperations`. Schema-v2 may declare at most one bounded binding for each of
`search`, `episode` and `playableSource`. A binding references an existing
package `SourceRuleProgram`, one existing normalizer field mapping and a fixed
HTTP(S) URI template. The template permits only an explicit operation-specific
placeholder set in path/query text; values are bounded and percent-encoded as
individual URI components. `SourceLiveOperationPlanFactory` expands the
declaration into the existing `SourceLiveSearchPlan`, `SourceLiveEpisodePlan`
or `SourceLivePlayableSourcePlan` and sends the resulting request through the
existing `SourceLiveHttpRequestCoordinator` before any I/O. It never selects a
source, normalizes records, creates a session or starts playback.

**Reason:** TASK-052 completed the live HTTP playback pipeline, but its plans
still had to be supplied by application code. Package-declared operations close
that composition gap without adding provider-specific adapters, fake data or a
parallel request/normalization model.

**Safety:** The decoder is strict by schema version and bounds operation count,
template length, placeholder names and mapping fields. Manifest validation
rejects duplicate/missing program references, mapping fields outside the
program, unsupported placeholders and malformed URI authority. The factory
rechecks package lifecycle, consent/re-consent, Wynime compatibility, operation
uniqueness, program existence, input identity ownership and exact package
policy. Only ready results retain the exact installed package, policy, program
and request provenance; rejected results retain safe reason tokens only. Live
operation metadata is part of the canonical encoder/signature payload and any
schema or binding change requires re-consent. Existing v1 fixture behavior and
all source-choice, playback, persistence and transport authorities remain
unchanged.

## ADR-076 — Compose installed packages through one live-search pipeline

**Status:** Accepted

**Decision:** `SourceInstalledLiveSearchPipeline` snapshots the caller-provided
installed-package authority once, in order, with a maximum of 32 unique
`(packageId, version)` identities. It sends the same query to the accepted
`SourceLiveOperationPlanFactory` for every snapshot item, retains every typed
factory result in order, and passes only ready `SourceLiveSearchPlan` references
to one invocation of the existing `SourceLiveSearchCoordinator`. The exact
coordinator result remains available to callers; package-level preflight
rejections remain explicit and make a successful downstream aggregate an outer
partial result. If no package is admitted, the pipeline returns a typed
no-usable-sources result without invoking live transport.

**Reason:** TASK-053 made one installed package able to declare and materialize
an exact live search plan, but Search still had no application boundary for the
current installed-package set. This is the smallest fan-out composition that
connects the package authority to the accepted live-search coordinator without
moving URI templates, lifecycle checks, mapping, HTTP admission, normalization
or aggregation into a second implementation.

**Safety:** Snapshot iteration, duplicate identity and the 33rd-package bound
fail before a source request. Factory failures are retained as bounded typed
per-package results and cannot suppress other ready packages. The pipeline
owns no generation, stale-response, timeout, retry, transport, response,
persistence or UI state; `SourceLiveSearchCoordinator` remains the authority
for async generation and close invalidation. Diagnostics expose only bounded
package/version identities, typed statuses, counts, operation/program IDs and
safe reason tokens, never query text, expanded URLs, response bodies, headers,
cookies, tokens or raw exceptions. Cross-package merge, ranking, matching and
provider hardcoding remain outside this decision.

## ADR-077 — Compose exact installed episode targets through one live pipeline

**Status:** Accepted

**Decision:** `SourceInstalledLiveEpisodePipeline` snapshots a caller-provided
iterable of exact `SourceInstalledLiveEpisodeTarget` values once, in caller
order, with a maximum of 32 deterministic identities. Each identity binds the
installed package ID and version to the complete source, line, subject and
episode identity. The pipeline passes every snapped target unchanged through
`SourceLiveOperationPlanFactory.buildEpisodePlan`, retains each typed factory
result, and passes only exact ready `SourceLiveEpisodePlan` references to one
existing `SourceLiveEpisodeCoordinator.listEpisodes` invocation. If no target
is ready, it returns a typed no-usable-sources result without coordinator or
transport work. Package preflight rejections remain visible and can make an
otherwise available or not-found aggregate partial; downstream failed, stale,
closed, no-source and not-found semantics are never fabricated as success.

**Reason:** TASK-054 connected installed packages to live search, while the
existing episode coordinator and TASK-053 factory already supplied the
episode-listing and package-declaration authorities. This is the smallest
composition that closes the installed exact-episode-target gap without adding
provider-specific matching, a second plan type, or a second asynchronous
lifecycle.

**Safety:** Iterator failure, duplicate target identity and the 33rd target are
rejected before factory, coordinator or live transport work. Ready results
retain the exact installed package, package policy, program, request, mapping,
plan reference and caller target; rejected results retain only bounded status
and safe reason data. The pipeline owns no generation, stale-response,
timeout, retry, persistence, normalization, HTTP or UI state. Diagnostics omit
episode identity values, request URIs, response bodies, headers, cookies,
tokens and raw exceptions. Cross-source matching, episode-number
reconciliation, merge/ranking, UI wiring, WebView, authentication, downloads
and release work remain outside this decision.

## ADR-078 — Compose installed playback targets through the existing live pipeline

**Status:** Accepted

**Decision:** `SourceInstalledLivePlaybackPipeline` snapshots a caller-provided
iterable of exact `SourceInstalledLiveEpisodeTarget` values once, in caller
order, with a maximum of 32 deterministic package/version/source/line/subject/
episode identities. Each target is passed unchanged to
`SourceLiveOperationPlanFactory.buildPlayableSourcePlan`; every typed factory
result remains visible, and only exact ready
`SourceLivePlayableSourcePlan` references enter one existing
`SourceLivePlaybackPipeline.openLive` invocation. All playback options are
forwarded unchanged. The wrapper retains the exact downstream result and
exposes its exact session on success. A valid opened target may coexist with
rejected alternatives as an explicitly partial outer result; no target is
allowed to fabricate success when the downstream result is not opened.

**Reason:** TASK-054 and TASK-055 connect installed packages to live search and
episode resolution, while TASK-053 can already materialize playable-source
plans and TASK-052 already owns playable-source normalization, route selection,
session request, open request and the `PlaybackCoordinator` lifecycle. This is
the smallest remaining composition for selected installed episode playback
and avoids a duplicate playable-source or session authority.

**Safety:** Snapshot failure, duplicate identity and the 33rd target return
before factory or live I/O. Consent, re-consent, enabled state, compatibility,
URI-template, HTTP and `SourceSecurityPolicy` admission remain with TASK-053;
route, session, proxy, player, stale and close behavior remain with TASK-052
and its lower authorities. A caller-provided close delegate only forwards to
those existing owners. Factory exceptions and unexpected downstream
exceptions become bounded safe per-target or wrapper failures. Diagnostics
omit episode identity, URI, response, headers, cookies, tokens, proxy
credentials, media payload and raw exceptions. Search/episode UI, provider
adapters, registry, matching, ranking, WebView, authentication, persistence,
downloads, player redesign and release work remain outside this decision.

## ADR-079 — Keep live-source Search as a presentation adapter

**Status:** Accepted

**Decision:** `SearchPage` receives exactly one
`SourceInstalledLiveSearchPipeline` application boundary and the existing
`SourcePackageStartupController.installedPackages` snapshot provider. A small
`SourceSearchPresentationController` submits one bounded trimmed query per
user action, retains only a presentation request identity, and projects the
pipeline's typed aggregate status, normalized result order and package display
provenance into deterministic UI state. Search results remain source-local and
are not matched, ranked, deduplicated or opened into an episode or playback
route.

**Reason:** TASK-056 completed the installed-source backend path, but the real
Search surface still showed a static placeholder. This is the smallest
presentation integration that exposes accepted live search while preserving
TASK-053 package admission, TASK-054 search composition, the existing package
lifecycle authority and the lower network/runtime coordinators.

**Safety:** Presentation creates no HTTP runtime, package factory, normalizer,
coordinator or transport and performs no per-package fan-out. It snapshots
through the existing application provider, maps invalid input and unexpected
failures to bounded safe states, and prevents stale or disposed completions
from mutating visible state. It renders only ordinary normalized titles and
source/package provenance; request URIs, headers, cookies, tokens, response
bodies, package internals, diagnostics and raw exceptions remain outside the
widget tree. Search-surface disposal never closes the shared application
pipeline; app bootstrap owns its lifecycle. Provider-specific adapters,
matching, ranking, episode navigation, playback, WebView, authentication,
persistence, downloads and release work remain outside this decision.

## ADR-080 — Schema-v3 source packages use one subject-details response path

**Status:** Accepted

**Decision:** Schema-v3 source packages require exact cache-policy and public-
capability objects, allow at most four live bindings, and replace the v2
standalone `episode` binding with a typed `subjectDetails` binding. The binding
references one metadata program, one episode-link program and a fixed HTTPS
URI template. `SourceLiveSubjectCoordinator` performs one admitted GET and
evaluates both declared programs against that same bounded in-memory response;
`SourceInstalledLiveSubjectPipeline` composes exact installed subject targets
without selecting sources or adding another network authority. The v3 runtime
supports only declarative literal fields in addition to the existing bounded
selector, attribute, text and regex dialects. The first package is the
unsigned, allowlisted `xifan` package; its search capability is declared as
`challengeRequired`, while detail, episodes and playback are declared
`supported`.

The post-1.0.12 `xifan` package revision updates only the provider-declared
public routes: its search capability is now `supported` through the public
Next search route, and its subject, episode-link and playback bindings target
the corresponding Next pages and declared video element. The package remains
unsigned, allowlisted and declarative; this revision does not add a browser
challenge handler or a provider-specific adapter.

**Reason:** A subject-first provider needs metadata and episode links from one
detail document, while package-declared capabilities and cache policy must be
explicit, versioned and consent-bound. Keeping the response fan-out inside the
existing live HTTP package runtime proves the xifan flow without introducing a
provider-specific adapter, iframe fetch, arbitrary script execution or a
second playback/session path.

**Safety:** The decoder is strict for schema v3 and preserves schema-v1/v2
wire and dialect behavior. Cache TTLs are whole seconds bounded to seven days;
the cache is memory-only with global/per-source limits and stores only
successful cacheable values. Playback refresh invalidates only its own cache
stage. Health tracking stores only bounded per-source aggregate counts and safe
diagnostic codes; disabled, consent-required and incompatible outcomes do not
mutate health. Subject details, challenge, unavailable and normalization
failures return typed redacted results. xifan permits only its declared HTTPS
hosts and package network permission; no raw upstream URL, cookie, token,
header, response body or source-provided executable code crosses the package
boundary. Real browser challenge handling, expiring media semantics, device
playback and UI source selection remain outside this implementation gate.

## ADR-081 — Use a bounded rendered-document fallback for hydrated source pages

**Status:** Accepted

**Decision:** Static source search and subject details remain the first path
and continue to use the GET-only `SourceLiveHttpRequestExecutor`. When search
returns typed `notFound`, `SourceLiveSearchCoordinator` may invoke one typed
`SourceLiveSearchDocumentFallback`. When schema-v3 subject metadata or
episode-link evaluation returns typed `notFound`,
`SourceLiveSubjectCoordinator` may invoke one typed
`SourceLiveSubjectDocumentFallback` with the same admitted plan. The
production fallbacks construct a `WebCaptureRequest` with the exact installed
package policy, platform-default user agent, bounded event/header/cookie/
document budgets and `documentAfterLoad` completion. The subject fallback
evaluates both declared programs against that one captured document.
`InAppWebViewCaptureView` obtains the rendered document only through the fixed
native `getHtml()` call after load; `SourceLiveDocumentPackageRuntime` then
evaluates it through the existing declarative `SourcePackageRuntime` and
returns only typed runtime results.

**Reason:** The live xifan search and subject routes return valid HTML
application shells whose result anchors, title and episode links appear after
browser hydration. Treating those static shells as parser failures cannot
produce the user's real search/detail flow, while adding a source-specific
POST/RPC client would violate the GET boundary and create a provider adapter.
A bounded rendered-document handoff closes the smallest compatibility gap
while preserving the existing rule, normalizer, subject/search aggregation and
single-session authorities.

**Safety:** Both fallbacks run only for typed `notFound`, never for transport,
parser, challenge or other runtime failures, and are generation-scoped so a
newer operation supersedes older WebView work. AJAX/fetch interception remains
enabled for document captures; every navigation, resource, XHR and fetch target
is checked against the package allowlist. The xifan next-runtime package
declares the observed `api.xifandm.net` HTTPS host explicitly. Source packages
cannot inject JavaScript or read arbitrary browser state. Captured document
text, headers, cookies and response data remain in memory only; diagnostics
retain only bounded status, counts and safe reason codes. The fallback creates
no second HTTP transport, evaluator, playback session, player lifecycle or
persistence path.

## ADR-082 — Declare exact non-standard HTTPS ports explicitly

**Status:** Accepted

**Decision:** `SourceDomainRule` keeps standard HTTPS/HTTP ports as its default
authority. A rule may optionally declare a bounded set of exact ports. The
source-package decoder and encoder carry the optional `ports` array, policy
semantic equality and re-consent checks include it, and the declarative source
builder may add an exact observed port only through its existing explicit
domain-admission path. Port ranges, wildcard ports and implicit non-standard
ports remain rejected.

**Reason:** The current public xifan playback route redirects from its declared
media host to `https://bjdownload.pan.wo.cn:30443`. Treating that redirect as a
generic standard-port request made the typed live path reject a real provider
route before the provider could return its response. An exact port declaration
closes that compatibility gap without broadening the source package into an
open proxy.

**Safety:** An exact port is part of the package authority and therefore the
canonical package identity, semantic policy comparison and re-consent boundary.
Host equality, dot-boundary subdomain matching, HTTPS requirements, public DNS
preflight, redirect budgets and bounded response handling remain unchanged.
The xifan continuation package is therefore versioned as `1.2.2`
with an updated registry hash. An earlier Android replay reached that declared
endpoint but received a typed HTTP 403 with zero captured cookies. The later
bounded harness observed HTTP 206 `video/mp4` and an initial Media3 handoff;
sustained duration playback remains unclaimed and no access-control bypass is
attempted.

## ADR-083 — Keep source acquisition offstage and line identity exact in playback UX

**Status:** Accepted for `1.0.15+16`; carried into the `1.0.16+17` continuation

**Decision:** The visible installed-source playback route is owned by Wynime:
detail page, exact episode and source-line selection, transient background
acquisition, the existing `PlaybackSession`／`PlaybackCoordinator` lifecycle,
and the native Media3 or libmpv surface. A rendered-document or WebView
fallback may be mounted only in a bounded 1x1, non-interactive,
semantics-excluded host while acquisition is pending. Once a typed capture
result is delivered, the host and its capture port are closed; the result is
passed to the existing playback route and never rendered as a source website.
The line selector accepts only typed `SourceSubjectLine` records. A line
change stops the old session, resolves the exact `SourceEpisodeIdentity` for
the selected `lineId`, and restores only a bounded position when safe.

**Reason:** The previous fallback view occupied the player surface and made a
source's website chrome visible, which violated the product's Animeko-style
playback contract and obscured the distinction between acquisition and
playback. Keeping the source surface offstage fixes that presentation defect
without adding a provider adapter, second player lifecycle, or URL inference.

**Safety:** The hidden host cannot receive pointer or semantics interaction;
generation checks, typed identity validation and the existing package policy,
request budgets, redirect rules and secret-safe diagnostics remain in force.
The player surface still receives only the capability URI through the existing
session/proxy boundary. The debug harness follows the same hidden-host rule but
is not production evidence. `source_line_selector_test.dart` and
`source_playback_ux_dependency_test.dart` guard the selector contract and the
absence of a visible source-page dependency.

## ADR-084 — Use the same verified registry boundary for debug admission

**Status:** Accepted for `1.0.15+16`; carried into the `1.0.16+17` continuation

**Decision:** A debug build may opt into a read-only staged registry rooted at
the app-support `source-registry` directory only when both `kDebugMode` and
the compile-time `WYNIME_SOURCE_REGISTRY_USE_STAGED` define are true. The
staged repository reads the exact index and package bytes, applies canonical
root/path and regular-file checks, and delegates decoding, byte budgets,
artifact inventory, SHA-256 verification and package identity checks to the
same registry components used by the fixed GitHub repository. It exposes no
write or lifecycle mutation operation. Release composition always selects the
fixed GitHub registry and cannot select the staged path.

**Reason:** Production-path admission needs real Sources UI, install/update,
fresh permission consent and persistence evidence for a dirty unreleased
package without making local files or a test harness part of the production
registry. Reusing the exact catalog/verifier boundary keeps that evidence
representative while preventing a debug convenience from becoming a release
override.

**Safety:** The staged root is canonicalized and all relative paths are
bounded; symlinked or non-regular artifacts are rejected. The staging helper
verifies the index-declared digest before placing bytes in the app's private
support directory. The runtime remains declarative, and registry reads do not
install, enable, bypass consent or create a second playback lifecycle.

## ADR-085 — Version xifan 1.2.3 for exact media-origin and fresh episode resolution

**Status:** Accepted for `1.0.17+18`

**Decision:** The xifan source package is revised to `1.2.3` and declares the
provider media origin `https://play.xfvod.pro:8088` as one exact HTTPS domain
rule with the exact non-standard port `8088`. The package registry index stores
the matching SHA-256 for the canonical package bytes. Each live episode
operation must issue a fresh episode-specific request and return only the
candidate correlated to that episode; it must not reuse a previous operation's
media candidate.

**Reason:** The provider's current playable media route uses a non-standard
port, while episode resolution is operation-specific. A precise package rule
closes the declared route without broadening network authority, and a fresh
request prevents stale media from one episode being presented for another.

**Safety:** Exact host, HTTPS scheme, port, DNS preflight, redirect budget,
response limits, permission and consent checks remain authoritative. Adjacent
ports, unlisted subdomains and standard-port variants are rejected. The
package remains unsigned and declarative; no source JavaScript, credential,
cookie, token, CAPTCHA, DRM or access-control bypass is introduced. The
existing shared `PlaybackSession` and capability-URI boundary remain the only
playback authorities.

## ADR-086 — Admit captured dynamic media origins as acquisition capabilities

**Status:** Accepted

**Decision:** Static HTTP remains the fast path, but a playable result with no
usable normalized candidate may enter the existing bounded WebView path when
the enabled package declares `mediaRequestInspection`. During one capture,
the platform may admit a third-party media request outside the package's
durable domain rules only after observing it as a non-navigation HTTPS request
in that generation and resolving every returned address as public. The
resulting `RuntimeMediaOriginGrant` authorizes one exact scheme, host and port,
one acquisition identity and one source event sequence. The grant and captured
request context are carried by the candidate into the existing
`PlaybackSession`, probe and loopback proxy; they are never added to package
policy or persisted. When one capture observes multiple candidates, the
shared classifier ranks explicit media Content-Type, supported media suffix,
browser media destination and Accept evidence ahead of a Range-only signal;
event sequence and normalized URI are deterministic tie-breakers. Images,
fonts, stylesheets and script requests carrying contradictory evidence are not
admitted as Range-only media.

**Reason:** Dynamic players commonly obtain an episode-specific CDN endpoint
through JavaScript, XHR, fetch or the browser media stack. Requiring the final
endpoint to be known when the declarative package is authored makes a durable
allowlist double as episode resolution and fails whenever the provider changes
CDN host, port or URL shape. An event-derived capability preserves the package
as the authority to start acquisition without pretending the transient media
origin is permanent source authority.

**Safety:** Runtime admission requires explicit package permission and a fresh
acquisition ID. Main-frame navigation, iframe admission, HTTP, embedded URL
credentials, local/private/special-purpose addresses, stale events and forged
candidate provenance fail closed. Snapshot, route, resolver, media probe and
proxy boundaries revalidate the same exact grant; redirect and HLS child
resources cannot escape it. Cookies are filtered to the exact candidate host,
all values remain in memory, and diagnostics expose only bounded shapes and
presence. A→B→A therefore creates three independent grants; process restart
removes every transient grant. Media3's native first-frame callback is exposed
as typed evidence separately from ready/playing state and position progress.

## ADR-087 — Separate protected candidate signing from the real-device release gate

**Status:** Accepted

**Decision:** The protected release-candidate signing workflow may build an
exact-`origin/main` inspection APK while Phase 12 is either `RELEASE_READY` or
`BLOCKED_REAL_ANDROID_E2E`. This exception applies only to the workflow that
uploads signed inspection artifacts and creates no tag or release. Publication
workflows continue to require `RELEASE_READY`; this operation must not enter
that state until the signed APK passes the complete real-device playback gate.

**Reason:** Production App Link and Bangumi callback verification require the
production-associated signer, while that verification is itself required
before release readiness. Requiring release readiness before producing the
inspection APK creates a circular gate and encourages invalid debug-signer
substitution.

**Safety:** Candidate checkout remains bound to the exact lowercase SHA at
both `HEAD` and `origin/main`, signing secrets remain inside the protected
`release` Environment, signer/alignment/metadata checks remain mandatory, and
the workflow still cannot tag or publish. A signed candidate is evidence only;
without all real-device checks, Phase 12 remains
`BLOCKED_REAL_ANDROID_E2E`.

## ADR-088 — Bound xifan rendered documents at one MiB

**Status:** Accepted

**Decision:** The xifan source package is revised to `1.2.4` and raises
`maxDocumentBytes` from 256 KiB to 1 MiB. Search and subject WebView document
capture use the declared package limit with an application ceiling of 1 MiB.
The registry stores the exact canonical 1.2.4 package digest, and the broader
resource budget requires fresh user consent before the package can be enabled.

**Reason:** A live public subject request exceeded 256 KiB before declarative
evaluation, so the installed production path could fail before episode
selection even though the rendered page and dynamic playback acquisition were
otherwise valid. One MiB admits the observed page without making document
capture unbounded or provider-specific native code.

**Safety:** The package's record, selector, evaluation, regex, redirect and
timeout limits remain unchanged. Captured documents stay in memory, are
evaluated only by the declarative runtime and are never logged. The application
ceiling prevents future source packages from turning this change into an
unbounded WebView bridge; larger pages continue to fail closed.
