# Phase 6 Implementation Status

This file records the current Phase 6 evidence boundary. Authoritative scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`PROTOTYPE_NOT_HARDWARE_VALIDATED`

The Application engine router, Media3 bridge, media-kit/libmpv wrapper, handoff state preservation, generation fencing, track authority and stable error boundary are implemented. This is not a release or hardware playback pass.

## Implemented surface

- Android preference Media3 → libmpv → WebView and Windows preference libmpv → WebView;
- availability probes with fail-closed unavailable states;
- numeric-loopback capability-only player URIs;
- one shared `PlaybackSession`, proxy lease, `AdRemovalPlan` and timeline identity across handoff;
- preservation of original-timeline position, play state, volume, rate and exact current-session tracks;
- stale generation and stale operation event suppression;
- one bounded fallback only for decoder, renderer or unsupported failures;
- no fallback for authorization, expiry, network or manifest failures;
- platform/Application error normalization to stable diagnostic codes without raw native messages, URLs, cookies, tokens or stack traces;
- current-session and non-external track validation at the shared router boundary.

## Current verification evidence

- `dart format --output=none --set-exit-if-changed .`: passed during the current continuation;
- `flutter analyze --fatal-infos`: passed on the current worktree;
- targeted router, coordinator, Media3, mpv, dependency-boundary and error-redaction tests: passed;
- `flutter build apk --debug`: passed after disabling cross-volume Kotlin incremental caching in `android/gradle.properties`;
- direct `android\gradlew.bat --no-daemon --console=plain assembleDebug --stacktrace`: passed;
- `flutter build windows --debug`: passed.

## Remaining boundary

No supported Android phone/tablet or Windows hardware playback run has been completed in this continuation, so real decoder, renderer, lifecycle and surface behaviour remains unverified. The exact bundled libmpv/FFmpeg artifact provenance, hashes, build flags and license closure remain a Phase 12 release gate. The Phase 11 Golden baseline was intentionally updated and independently rerun successfully; the current cross-platform UI boundary is documented in `docs/PHASE11_STATUS.md`.

Phase 6 does not claim real downloads, FFmpeg remuxing, Bangumi or release readiness.
