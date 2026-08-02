from __future__ import annotations

import re
from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    content = path.read_text()
    if new in content:
        return
    if content.count(old) != 1:
        raise SystemExit(f"replacement precondition failed for {path}: {old!r}")
    path.write_text(content.replace(old, new, 1))


def append_once(path: Path, marker: str, section: str) -> None:
    content = path.read_text()
    if marker in content:
        return
    path.write_text(content.rstrip() + "\n\n" + section.strip() + "\n")


switchable = Path("lib/src/application/playback/switchable_player_backend.dart")
replace_once(
    switchable,
    "    final capability = await primary.probe();\n",
    "    final capabilitySource = primary as PlayerBackendCapabilitySource;\n"
    "    final capability = await capabilitySource.probe();\n",
)
replace_once(
    switchable,
    "    final capability = await backend.probe();\n",
    "    final capabilitySource = backend as PlayerBackendCapabilitySource;\n"
    "    final capability = await capabilitySource.probe();\n",
)

mpv_backend = Path("lib/src/platform/playback/mpv_player_backend.dart")
replace_once(
    mpv_backend,
    "  Future<void> open(PlaybackSession session) async {\n"
    "    final uri = session.playbackUri;\n",
    "  Future<void> open(PlaybackSession session) async {\n"
    "    final capability = await probe();\n"
    "    if (!capability.isAvailable) {\n"
    "      throw UnsupportedError(\n"
    "        '$backendId is unavailable: ${capability.code}',\n"
    "      );\n"
    "    }\n"
    "    final uri = session.playbackUri;\n",
)
replace_once(
    mpv_backend,
    "    !uri.hasQuery &&\n"
    "    !uri.hasFragment &&\n"
    "    (uri.host == '127.0.0.1' || uri.host == '::1');\n",
    "    !uri.hasQuery &&\n"
    "    !uri.hasFragment &&\n"
    "    uri.path.isNotEmpty &&\n"
    "    uri.path != '/' &&\n"
    "    (uri.host == '127.0.0.1' || uri.host == '::1');\n",
)

switch_test = Path("test/application/switchable_player_backend_test.dart")
replace_once(
    switch_test,
    "    expect(fallback.opened, [same(session)]);\n",
    "    expect(fallback.opened, hasLength(1));\n"
    "    expect(fallback.opened.single, same(session));\n",
)

mpv_test = Path("test/platform/mpv_player_backend_test.dart")
replace_once(
    mpv_test,
    "    this.probePayload = const {\n"
    "      'backendId': 'windows-mpv',\n"
    "      'availability': 'unavailable',\n"
    "      'code': 'runtime_missing',\n"
    "    },\n",
    "    this.probePayload = const {\n"
    "      'backendId': 'windows-mpv',\n"
    "      'availability': 'available',\n"
    "      'code': 'libmpv_ready',\n"
    "      'clientApiVersion': 131077,\n"
    "      'runtimeVersion': 'client-api-2.5',\n"
    "    },\n",
)
if "fails closed before open when the runtime probe is unavailable" not in mpv_test.read_text():
    content = mpv_test.read_text()
    anchor = "  test('opens only a numeric loopback capability URI', () async {\n"
    block = """  test('fails closed before open when the runtime probe is unavailable', () async {
    final transport = _FakeMpvTransport(
      probePayload: const {
        'backendId': 'windows-mpv',
        'availability': 'unavailable',
        'code': 'runtime_missing',
      },
    );
    final backend = MpvPlayerBackend(
      backendId: 'windows-mpv',
      transport: transport,
    );

    await expectLater(
      backend.open(
        testPlaybackSession(
          playbackUri: Uri.parse('http://127.0.0.1:42000/master.m3u8'),
        ),
      ),
      throwsUnsupportedError,
    );
    expect(transport.calls, isEmpty);
  });

"""
    if content.count(anchor) != 1:
        raise SystemExit("mpv unavailable test anchor missing")
    mpv_test.write_text(content.replace(anchor, block + anchor, 1))

architecture = Path("test/architecture/playback_dependency_test.dart")
content = architecture.read_text()
old_start = "  test('Phase 5 stops at sanitized proxy output', () {\n"
next_test = "  test('playback application service depends only on domain ports', () {\n"
if old_start in content:
    start = content.index(old_start)
    end = content.index(next_test, start)
    content = content[:start] + end * 0 * "" + content[end:]
    architecture.write_text(content)

agents = Path("AGENTS.md")
content = agents.read_text()
if "## Current implementation scope (Phase 6)" not in content:
    content = re.sub(
        r"## Current implementation scope \(Phase 5\)",
        "## Preserved implementation scope through Phase 5",
        content,
        count=1,
    )
    agents.write_text(content)
append_once(
    agents,
    "## Current implementation scope (Phase 6)",
    """
## Current implementation scope (Phase 6)

- Keep `PlaybackSession`, sanitized loopback proxy output, and timeline mapping authoritative.
- Add Wynime-owned typed player capability, mpv transport, and bounded backend handoff contracts.
- Android remains `android-media3` first; `android-mpv` is decoder/renderer fallback only when its runtime probe is available.
- Windows uses `windows-mpv` only when an allowlisted executable-directory libmpv runtime exposes client API major 2.
- Player inputs remain numeric-loopback HTTP capability URLs. Upstream URLs, headers, cookies, and tokens never enter native player channels.
- Automatic engine fallback is limited to one decoder/renderer handoff on the same session, proxy lease, timeline-map identity, position, and pause intent.
- Authorization, expiry, manifest, sanitizer, network, and unknown failures must not trigger engine switching.
- Do not bundle libmpv binaries, Android ABI packages, mpv scripts, ytdl, IPC, external config, shaders, arbitrary file paths, or Phase 7 work.
""",
)

append_once(
    Path("docs/PROJECT_PLAN.md"),
    "## Phase 6 Gate — libmpv capability and engine handoff",
    """
## Phase 6 Gate — libmpv capability and engine handoff

Phase 6 is complete only when:

- typed capability probes distinguish available, unavailable, and incompatible runtimes;
- Android selects Media3 first and allows at most one decoder/renderer handoff to mpv;
- Windows dynamically loads only allowlisted executable-directory DLL names and requires client API major 2;
- both native bridges reject non-numeric-loopback player URLs;
- handoff preserves session identity, timeline-map identity, original position, and paused/playing intent;
- stale backend events and superseded handoffs cannot mutate active playback;
- mpv config, scripts, ytdl, IPC, external file discovery, and unreviewed binaries remain disabled or absent;
- fatal analyze, full tests, Android debug APK, and Windows debug build pass.
""",
)

append_once(
    Path("docs/ARCHITECTURE.md"),
    "## Phase 6 player capability and handoff architecture",
    """
## Phase 6 player capability and handoff architecture

`PlayerBackendRegistry` owns stable backend IDs and `PlayerBackendSelectionPolicy` chooses the host plan. Android constructs a `SwitchablePlayerBackend(android-media3, android-mpv)`; Windows selects `windows-mpv` directly. Platform classes stay below application/domain boundaries.

`MpvPlayerBackend` is the only Dart adapter for the Wynime mpv channels. It probes capability before open, validates strictly monotonic typed events, rejects stale session IDs, and accepts only numeric-loopback proxy URLs. Native code never receives source headers, cookies, tokens, or upstream URLs.

`SwitchablePlayerBackend` performs one bounded fallback only for decoder or renderer failure. It snapshots `sessionId`, `timelineMapIdentity`, original position, and play/pause intent; closes the superseded backend; opens the fallback with the same `PlaybackSession`; restores position and pause intent; and ignores stale events from the old backend.

The Windows runner dynamically resolves libmpv from an executable-directory allowlist and polls `mpv_wait_event(..., 0)` on the UI thread. Android provides a packaging/JNI capability prototype and reports unavailable until both native libraries are present. Production embedded rendering and Android ABI packaging are deferred.
""",
)

append_once(
    Path("docs/DECISIONS.md"),
    "## ADR-018 — Wynime owns the mpv transport boundary",
    """
## ADR-018 — Wynime owns the mpv transport boundary

**Decision:** Do not adopt a third-party Flutter player wrapper. Domain depends only on `PlayerBackend`; platform code owns MethodChannel/EventChannel payloads and native libmpv integration.

**Reason:** This keeps session authority, loopback-only input, error classification, cancellation, and stale-event policy auditable and independent from plugin release cadence.

## ADR-019 — libmpv is runtime-probed, never bundled in Phase 6

**Decision:** Windows loads only allowlisted DLL names from the executable directory and requires client API major 2. Android reports availability only when packaged `libmpv.so` and a Wynime JNI shim are both loadable.

**Reason:** CI and the repository remain free of unreviewed native binaries while capability and build boundaries can be validated now.

## ADR-020 — Automatic engine switching is decoder/renderer-only

**Decision:** Android may perform one automatic Media3-to-mpv handoff only for typed decoder or renderer failure. Authorization, expiry, manifest, sanitizer, network, and unknown errors are forwarded without switching.

**Reason:** Engine changes cannot repair source authorization or manifest failures and must not conceal refresh/retry decisions owned by higher layers.
""",
)
