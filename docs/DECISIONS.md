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
**Decision:** Phase 6 pins `media_kit 1.2.6`, `media_kit_video 2.0.1`, `media_kit_libs_android_video 1.3.8` and `media_kit_libs_windows_video 1.0.11`. A Wynime-owned Application router preserves one `PlaybackSession` and loopback proxy lease while selecting Android Media3 → libmpv → WebView or Windows libmpv → WebView. Each playback operation may perform at most one automatic fallback, only for decoder, renderer or unsupported failures.
**Reason:** media-kit provides a maintained Flutter-facing libmpv API and video surface without allowing third-party types to cross Domain or Application. Keeping routing above Platform avoids resolving a second URL or leaking source credentials when an engine changes.
**Safety:** libmpv receives only the bounded numeric-loopback capability URI with an empty header map. Authorization, expiry, network and manifest failures do not trigger engine fallback. Handoff preserves original-timeline position, play state, volume, rate, exact audio／subtitle IDs and `timelineMapIdentity`; stale generations and identity mismatches fail closed. Raw native errors are reduced to stable diagnostic codes. The media-kit Dart packages are MIT, but the exact libmpv／FFmpeg artifact may be GPL or a compliant LGPL build; release packaging remains blocked until binary provenance, build flags and linked-library licenses are verified. Real-device playback is not claimed from CI compilation alone.

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

## ADR-021 — Bangumi uses memory-only OAuth and revision-bound offline sync

**Status:** Accepted

**Decision:** Phase 9 uses Bangumi's official HTTPS API for `/calendar`, `/v0/subjects/{subject_id}`, `/v0/episodes`, current-user collection and current-user episode collection endpoints. OAuth authorization-code requests are state-bound and access tokens remain memory-only. Local collection status and watched episodes are updated immediately, while a Drift-backed `BangumiSyncOperation` records the exact mutation, base remote revision, retry state and conflict state.

**Reason:** Bangumi supplies schedule, subject, collection and episode-collection data, while Wynime must remain usable offline and must not confuse local playback progress with remote watched state. A revision-bound queue makes concurrent edits visible instead of silently overwriting them.

**Safety:** Only standard-port HTTPS Bangumi hosts are accepted; bearer tokens are sent in headers and never query parameters. Response size, episode page size, retries and exponential backoff are bounded. Authorization, malformed payload, exhausted retry and remote-revision failures remain visible. Conflict resolution either applies the fetched remote state or requeues the local mutation against the fetched revision. Tokens, client secrets, cookies and raw response text are excluded from persistence and diagnostics. No live OAuth account or upstream success is inferred from fixture tests.

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

**Safety:** Missing FFmpeg, unavailable hardware, unobservable Windows UI, absent external signing keys or incomplete native provenance remain explicit blockers. A successful process exit never bypasses artifact signature verification, canonical containment or manifest authority. Telemetry remains disabled by default, and no release/tag/publish action is implied by this decision.
