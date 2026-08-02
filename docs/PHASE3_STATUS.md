# Phase 3 Implementation Status

This file records the implementation baseline for Phase 3. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Locked toolchain

- Flutter: `3.44.8` stable
- Bundled Dart: `3.12.2`
- `flutter_inappwebview`: `6.2.0-beta.3`
- Android implementation: `flutter_inappwebview_android 1.2.0-beta.3`, selected because it contains the AGP 9 compatibility fix
- Windows implementation: endorsed `flutter_inappwebview_windows 0.7.0-beta.3` WebView2 plugin
- Target platforms: Android and Windows

The latest stable `flutter_inappwebview 6.1.5` was tested and rejected because its Android `1.1.3` implementation uses a ProGuard configuration removed by the project’s Flutter-generated AGP `9.0.1` toolchain. The prerelease line is therefore the newest version proven compatible with this project rather than an unverified preference for beta software.

## Implemented Phase 3 surface

- Pure-Dart capture request, runtime status, event, candidate, cookie and snapshot models
- Explicit `webView`, `desktopUserAgent`, `cookies` and `mediaRequestInspection` permission checks
- Event, candidate, header, cookie and redirect budgets
- UTF-8 byte accounting for headers and cookies
- Strictly increasing event sequence
- In-memory media-candidate classification and deduplication
- Secret-safe diagnostics that omit query strings, fragments, header values, cookie values and complete media URLs
- Android WebView and Windows WebView2 runtime bridge behind `WebSourceBrowserPort`
- Explicit WebView2 runtime unavailable and probe-failed states
- Scoped cookie import, export and clearing
- Navigation, iframe, resource, XHR and fetch observation
- Fail-closed navigation, resource, XHR and fetch policy enforcement
- File/content access, mixed content, automatic windows, browser downloads, HTTP authentication, invalid server trust, camera, microphone and geolocation blocked
- Deterministic Domain, Infrastructure, platform-mapper, settings and architecture tests

## Explicitly absent

No source-provided Dart, JavaScript, WASM or native adapter execution; no DRM, paywall, login or access-control bypass; no `PlaybackSession`; no local HLS proxy or sanitizer; no ad detection or timeline mapping; no Media3, mpv, playback backend, real download, FFmpeg or Bangumi authentication implementation.

## Validation policy

Current-head dependency resolution, generated-code consistency, formatting, static analysis, tests, Android build and Windows build are the primary Gate. A real Android device and a separately installed consumer Windows WebView2 environment are not available to the connected agent. For behavior that cannot be exercised in hosted CI, acceptance uses plugin API compilation, deterministic mapping and policy tests, explicit runtime-unavailable handling, and independent read-only code review as authorized by the project owner.

The final CI evidence and Reviewer verdict are recorded on PR #8 and Issue #7 before merge. Phase 4 begins only after both are PASS.
