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
