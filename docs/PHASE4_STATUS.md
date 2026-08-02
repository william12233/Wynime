# Phase 4 Implementation Status

This file records the implementation baseline for Phase 4. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Locked toolchain

- Flutter: `3.44.8` stable
- Bundled Dart: `3.12.2`
- Android Media3: `1.10.1`
- Android Java／Kotlin target: JVM 17
- Existing Drift, source-rule and WebView toolchains remain unchanged
- Target platforms: Android and Windows

Media3 `1.10.1` is the newest stable release verified compatible with the locked Android and Flutter toolchain when Phase 4 began. The Android implementation pins both `media3-exoplayer` and `media3-exoplayer-hls` to the same version.

## Implemented Phase 4 surface

- Hardened authoritative `PlaybackSession`, media tracks, expiry and refresh identity invariants
- Controlled WebView media-candidate to session resolution
- Cookie domain, secure, expiry and RFC-style path-boundary filtering
- Pure-Dart playback events and failure classification
- HTTP 401／403 and explicit expiry mapped to session refresh
- IPv4／IPv6 numeric-loopback-only HTTP proxy
- Per-session unguessable capability path and opaque resource registry
- Source allowlist reuse for the initial resource, redirects and all HLS child resources
- Public-address DNS preflight plus socket pinning to the validated address in the production upstream client
- Independent request-header, cookie, redirect, playlist, response and registered-resource budgets
- GET, HEAD and one-range forwarding
- HLS media-line and `URI=` attribute rewriting
- Response-header allowlist that does not forward Set-Cookie or redirect Location
- Lease cancellation and full service shutdown
- Application-owned playback coordinator for resolve → refresh → proxy → player lifecycle
- Typed Flutter MethodChannel／EventChannel Media3 boundary
- Native Android ExoPlayer open, pause, seek, close, state and error events
- Android INTERNET permission with cleartext disabled globally and permitted only for numeric loopback
- Strict session identity binding for native playback events
- Explicit unsupported Phase 4 backend for Windows
- Domain, resolver, proxy, HLS, platform and architecture tests

## Explicitly absent

No manifest sanitizer, ad detector, `AdRemovalPlan` generation, timeline rewrite, mpv, FFmpeg, downloader, Bangumi authentication, DRM bypass, paywall bypass or access-control circumvention is implemented in Phase 4. The pre-existing minimal `AdRemovalPlan` remains an identity placeholder and is not generated or modified by this phase.

## Validation policy

Current-head dependency resolution, generated-code consistency, formatting, static analysis, all tests, Android debug APK build and Windows debug build are the primary Gate. Hosted CI cannot prove real-device decoder, surface, lifecycle or network behavior, so those limits remain explicit; typed bridge compilation, deterministic platform-event tests, loopback proxy integration tests and independent read-only code review provide the authorized acceptance evidence.

The final CI evidence and Reviewer verdict are recorded on PR #10 and Issue #9 before merge. Phase 5 begins only after both are PASS.
