# Phase 6 Status — libmpv capability and engine handoff

## Scope

Phase 6 adds a Wynime-owned player capability boundary without bundling or publishing libmpv binaries.

- Android remains Media3-first and can hand off only decoder or renderer failures to an Android mpv backend when its runtime probe is available.
- Windows selects the Windows mpv backend directly and fails closed when no compatible runtime is installed beside the executable.
- Both player bridges accept only Phase 4/5 numeric-loopback capability URLs and never receive upstream URLs, cookies, tokens, or source headers.

## Implemented contracts

- `PlayerBackendCapability` and typed availability codes.
- `PlaybackHandoffSnapshot` preserving session identity, timeline-map identity, original position, and playing/paused intent.
- `PlayerBackendRegistry` and deterministic host-platform selection policy.
- `SwitchablePlayerBackend` with a single bounded decoder/renderer fallback.
- `MpvPlayerBackend` with strict MethodChannel/EventChannel payload validation and stale-session rejection.

## Windows prototype

- Dynamically loads only `mpv-2.dll`, `libmpv-2.dll`, `mpv-1.dll`, or `libmpv-1.dll` from the executable directory.
- Resolves the libmpv client API at runtime; no import library or bundled binary is required for CI.
- Requires client API major version 2.
- Explicitly disables external config, scripts, ytdl, default key bindings, OSC, automatic subtitle lookup, and automatic external-audio lookup.
- Uses a UI-thread `WM_TIMER` poll of `mpv_wait_event(..., 0)` so Flutter EventSink is not called from an arbitrary native thread.
- Supports probe, open, pause, seek, close, position/buffer events, and typed failure states.

The prototype may use libmpv's own native window when a compatible runtime is placed beside the executable. A production embedded Flutter texture or child-surface renderer remains outside Phase 6.

## Android prototype

- Probes packaged `libmpv.so` with `System.loadLibrary("mpv")`.
- Requires a separate Wynime-owned `libwynime_mpv_bridge.so` JNI shim before reporting available.
- This phase does not bundle either native library; normal builds therefore report `runtime_missing` or `jni_bridge_missing` and continue with Media3.
- Control calls fail closed while the JNI bridge is unavailable.

## Automatic fallback policy

Automatic switching is allowed only when the active backend reports `decoder` or `renderer` failure.

The following failures are forwarded without an engine switch:

- authorization or expiry,
- manifest or sanitizer failure,
- network or HTTP failure,
- unknown native failure.

A handoff reuses the same `PlaybackSession` and loopback proxy lease. It opens the fallback, seeks to the original timeline position, restores pause intent, ignores stale events from the superseded backend, and performs at most one automatic fallback.

## Excluded

- Bundled libmpv binaries or Android ABI packaging.
- Production Android JNI implementation.
- Embedded Windows texture rendering.
- mpv scripts, IPC, external config, ytdl, arbitrary local paths, shaders, or subtitle downloading.
- DRM or access-control bypass.
