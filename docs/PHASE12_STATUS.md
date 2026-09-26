# Phase 12 Implementation Status

This file records the machine-verifiable release boundary for performance,
security, licensing, packaging and publication. The machine-readable sources
of truth remain `AGENTS.md`, `docs/PROJECT_PLAN.md`,
`docs/ARCHITECTURE.md` and `docs/DECISIONS.md`.

## Status

`RELEASE_READY`

Operation `WYNIME-DYNAMIC-PLAYBACK-REAL-E2E-20260926` changes the release
boundary: a protected exact-SHA signed Android candidate may be built for
inspection and device validation. The real-device Bangumi callback, dynamic
source acquisition, proxy upstream, Media3 first-frame, playback beyond ten
seconds, A→B→A fresh acquisitions and a second anime remain unverified. The
repository owner explicitly authorized v1.0.18 publication on 2026-09-26 so
they can perform that hardware validation from the public signed artifact;
this release authorization does not convert any missing device evidence into
a pass. The historical v1.0.17, v1.0.16 and v1.0.15 evidence below remains a
record of those earlier release boundaries and is not evidence for this
operation.

## Post-1.0.16 continuation worktree

The current continuation runtime is
`1.0.18+19`; operation `WYNIME-DYNAMIC-PLAYBACK-REAL-E2E-20260926` updates the
xifan package revision to `1.2.4`, which requires
`^1.0.15` because the public 1.0.14 runtime does not contain the rendered
document capture bridge. Version 1.2.4 raises the bounded document budget to
1 MiB after the live public subject page exceeded the earlier 256 KiB ceiling;
the broader budget requires fresh package consent. Before publication, the
candidate must be tagged and submitted to the protected workflow at one
immutable `main` SHA. The historical release evidence below remains the public
release record and is not evidence for this operation.

### 2026-09-26 v1.0.18 release decision

- The repository owner explicitly authorizes direct `main` publication of
  v1.0.18 and will perform the outstanding physical Android validation from
  the published signed APK.
- The release includes acquisition-bound runtime media-origin grants,
  extensionless media-request classification, bounded public-address
  admission, captured request context propagation through the shared
  `PlaybackSession`, and native Media3 first-frame reporting.
- Xifan package 1.2.4 raises the bounded rendered-document ceiling to 1 MiB
  because the current public subject page exceeds 256 KiB; the permission and
  package hash change requires fresh consent.
- Current-head deterministic evidence includes 829 passing Flutter tests,
  analyzer/build success, Android and Windows compilation, live public source
  reachability, production App Link association, signed candidate metadata,
  alignment and signature checks, and two regression tests for current and
  legacy `apksigner` output.
- Real Android E2E remains unverified; `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit. No authenticated
  Bangumi callback, Media3 first frame, sustained hardware playback, A→B→A
  device sequence, CAPTCHA solving, DRM bypass or access-control-bypass claim
  is made.

### 2026-09-26 v1.0.17 release decision

- The current release request explicitly authorizes the v1.0.17 workflow.
- The candidate includes xifan package `1.2.3`, whose registry hash must match
  the exact package bytes. It declares only the exact HTTPS media origin
  `play.xfvod.pro:8088`; standard-port, adjacent-port and subdomain variants
  remain rejected.
- Each live episode request now obtains a fresh episode-specific media
  candidate. Deterministic tests cover request ordering, episode identity,
  candidate separation, package re-consent and exact-port policy.
- Current-head deterministic evidence is 820 passing Flutter tests,
  `flutter analyze --fatal-infos --no-pub`, Dart format with zero changes,
  `dart run build_runner build` with no generated diff, and
  `git diff --check` passing.
- The final candidate SHA must be frozen and revalidated by protected CI before
  publication. `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit, and no authenticated
  Bangumi, sustained hardware playback, CAPTCHA solving, DRM bypass or
  access-control-bypass claim is made.

### 2026-09-24 v1.0.16 release record

- The protected v1.0.16 workflow published the exact-SHA candidate with four
  public assets.
- Its Android emulator evidence, Windows UI limitation and hardware limitation
  remain historical evidence classes; they do not become hardware passes.

### 2026-09-24 v1.0.15 release decision (historical)

- The v1.0.15 request explicitly authorized the v1.0.15 workflow.
- The v1.0.15 protected CI and tag-triggered release workflow published the
  exact-SHA candidate with four public assets.
- Additional local UI and hardware verification was intentionally not rerun in
  that release turn. `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain truthful disclosures.

### 2026-09-23 live compatibility continuation

- The unreleased local xifan package is `1.2.1`; it adds only the exact HTTPS
  redirect authority `bjdownload.pan.wo.cn:30443` and is not admitted to the
  public 1.0.14 registry. `sources/index.json` records the matching package
  SHA-256.
- Static CLI search and subject parsing truthfully return typed `notFound` for
  the hydrated Next shell. The bounded rendered-document fallbacks complete
  subject metadata and exact episode correlation on Android; the playable
  document capture is empty and correctly falls back to public-control media
  request capture.
- Current API-36 Android x64 emulator evidence: APK build/install/launch pass;
  subject pass; episode mapping pass (`xfxf1/154427`); media-request capture
  pass with one candidate and zero captured cookies; the bounded `GET` plus
  `Range` replay reaches the declared `https://bjdownload.pan.wo.cn:30443`
  endpoint and observes HTTP 206 `video/mp4`. Initial Media3 handoff and
  `PLAYBACK_SESSION` pass on both phone and tablet harness runs. A later tablet
  attempt observed an upstream 502 after the initial handoff, so sustained
  duration playback is not claimed and no access-control or CAPTCHA bypass is
  attempted.
- Browser evidence remains browser-only: the public page exposes a top-level
  playable video and manual playback was observed there. It does not replace
  Android production-route evidence. An ordinary Windows build reproduces the
  VS/FileTracker `E_ACCESSDENIED` environment failure; a controlled elevated
  retry produces the Debug executable, but Windows UI remains unavailable to
  Computer Use.

### 2026-09-23 Animeko-style playback UX correction

`BLOCKED_PRE_RELEASE_REVIEW`

- Normal playback now renders only Wynime's resolving/error/native player
  surface. The source WebView is a transient, non-interactive 1x1 background
  acquisition host and is removed as soon as a typed capture result arrives;
  it is never the visible playback surface.
- Detail and Player pages use the typed `SourceSubjectLine` list as the line
  selector. A line switch closes the old session, resolves the exact
  `SourceEpisodeIdentity` for the selected `lineId`, and performs only a
  bounded best-effort position restore.
- The debug-only Android live harness was changed to the same hidden host so
  its screenshots cannot be mistaken for the production route. Phone and
  tablet screenshots show native controls without xifan page chrome.
- Deterministic acceptance: 805 Flutter tests, `flutter analyze
  --fatal-infos --no-pub`, `git diff --check`, Android x64 Debug build and
  Android API-36 acquisition/206/Media3 initial-handoff evidence pass.
- Release remains blocked because the public runtime registry observed by the
  normal app still serves xifan v1.1.0 rather than the dirty local v1.2.1
  package, so the normal Bangumi detail-to-production-playback route was not
  exercised end to end. Windows UI playback is also
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`. No commit, push, tag, protected
  approval or release was performed.

### 2026-09-23 production path admission validation

`BLOCKED_PRE_RELEASE_REVIEW`

- The debug-only `kDebugMode` plus compile-time opt-in staged registry now
  reads `sources/index.json` and the exact local artifact through the same
  strict decoder, catalog loader, byte budget, canonical-root check and
  SHA-256/package verifier used by the registry boundary. Release builds keep
  the fixed GitHub registry composition and cannot select this path.
- On the fixed API-36 phone and tablet AVDs, the normal Sources page staged
  xifan `1.2.1` from an installed `1.1.0`, displayed the broader policy,
  required fresh consent, enabled the package, and restored `Enabled` after an
  app restart. The normal source Search page returned three live xifan results
  for `naruto` on both devices. Evidence is under the operation's temporary
  `codex-ui-verification` directory.
- The phone reached the public Bangumi OAuth authorization page. The account
  holder did not grant external account access, so authenticated Bangumi
  Library/subject detail, episode 24, exact mapping persistence, production
  PlayerPage, live 502 line switch and real background acquisition remain
  unverified. The source Search page is not substituted for that route.
- The playback failure surface now maps `http_status_502` and
  `upstream_http_502` to a localized HTTP 502 message with `[重試]` and
  `[切換線路]` when more than one exact line is available; the latter closes
  and reopens through the existing controller/session path. Widget coverage
  is deterministic, not live HTTP proof.
- Current validation is 815 passing Flutter tests and a clean fatal-info
  analyzer. Two standard non-elevated Windows builds reproduce Visual Studio
  FileTracker `E_ACCESSDENIED`; two controlled elevated debug builds pass, but
  Windows live/UI evidence remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`. No commit, push, tag, protected
  approval or release was performed.

Approved Wynime distribution license: yes

The root `LICENSE` is the repository owner's MIT license for Wynime-owned
source code only. It does not change the terms of any third-party dependency,
native binary, codec or system runtime.

## Prior v1.0.3 machine release baseline

- Current-head `dart format --output=none --set-exit-if-changed lib test`:
  pass.
- `flutter analyze --fatal-infos`: pass with no analyzer issues.
- Full deterministic Flutter test suite and the repository's fixed-size
  golden tests: pass.
- Android debug and arm64-v8a Android release APK builds: pass. No universal
  APK or release AAB is published.
- Official Android signing uses the configured Wynime
  `WYNIME_RELEASE_*` keystore only; `apksigner verify`, APK alignment,
  version metadata and ABI checks are required and recorded by CI.
- Windows x64 Flutter Release build: pass on the declared toolchain.
- Engineering native provenance, archive identity, packaged-binary hashes,
  the applicable Android and Windows LGPLv3 text, the accompanying GPLv3
  Combined Work text,
  corresponding-source/relink offer and shipped notices:
  `CLOSED_RELEASE_PROVENANCE`.
- Android APK and Windows x64 portable ZIP each have a matching SHA-256
  sidecar.
- No signing secret, keystore or password is stored in the repository or
  release artifacts.
- The prior versioned release notes described the actual v1.0.3 boundary.
- The release workflow requires the tag, trigger SHA, `origin/main`,
  successful exact-SHA phase-0 CI and publication checkout to agree.

The protected candidate-signing workflow remains useful as exact-SHA signing
evidence before tagging. The final release workflow independently rebuilds and
verifies the signed APK and all public assets.

## 1.0.14 candidate status

- Version authority is `1.0.14+15`; the expected arm64 split APK versionCode is
  `2015`.
- This candidate preserves the v1.0.13 source-to-playback implementation and
  includes the generated detailed-plan snapshot committed on `main` before
  the release tag is frozen.
- Current-head full deterministic Flutter tests (772 tests), analyzer,
  formatting, targeted source tests and `git diff --check` must remain green;
  protected CI remains authoritative for signing and final release packaging.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`; native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## 1.0.13 candidate status

- Version authority is `1.0.13+14`; the expected arm64 split APK versionCode is
  `2014`.
- The source-to-playback path now has a fixed production registry index,
  persisted package provenance and Bangumi-to-source mappings, exact normalized
  subject matching, conservative episode correlation and detail-page playback
  through the existing shared session pipeline.
- The unsigned xifan 1.1.0 package declares supported public search, detail,
  episode and playback operations against its allowlisted Next and media
  hosts. No CAPTCHA handler or access-control bypass is included.
- Current-head full deterministic Flutter tests (772 tests), analyzer,
  formatting, targeted source tests and `git diff --check` pass on this
  candidate worktree. Android debug and Windows debug builds pass; protected
  CI remains authoritative for signing and final release packaging.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`; native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## 1.0.12 candidate status

- Version authority is `1.0.12+13`; the expected arm64 split APK versionCode is
  `2013`.
- Schema-v3 source packages now declare bounded cache policy, public
  capabilities and typed live bindings. The installed subject pipeline uses
  one admitted response for metadata and exact episode links, while playback
  remains connected to the existing shared session pipeline.
- The unsigned xifan package is allowlisted to its declared HTTPS hosts and
  network permission. Its live detail, episode and playback capabilities are
  supported; provider search is declared `challengeRequired`.
- Current-head full deterministic Flutter tests (763 tests), analyzer,
  formatting and `git diff --check` pass on this candidate worktree.
- Live browser verification reached the xifan detail and episode pages,
  observed 13 episode links and three source lines, loaded the player iframe
  with a 23:40 duration, and observed the search verification challenge
  without attempting to solve it. This is provider/browser evidence, not
  Android or Windows hardware playback evidence.
- Protected Android signing, App Link verification, Windows x64 Release
  packaging and exact-SHA CI remain required for the immutable candidate.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`; native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## 1.0.11 candidate status

- Version authority is `1.0.11+12`; the expected arm64 split APK versionCode is
  `2012`.
- The Bangumi mutation transport now explicitly sends JSON, emits only stable
  redacted HTTP 415 diagnostics, and makes recoverable blocked rows retryable.
  Queue coalescing and account/write gates prevent stale blocked or active
  duplicates from overwriting a newer local intent.
- Current-head full deterministic Flutter tests (750 tests), focused Bangumi
  tests (49 tests), analyzer, formatting and `git diff --check` pass on this
  candidate worktree.
- The fresh Android debug APK installed and launched on the fixed Android 16 /
  API 36 phone and tablet emulators. Protected Android signing, App Link
  verification, Windows x64 Release packaging and exact-SHA CI remain required
  for the immutable candidate.
- Independent browser-based GPT-5.6 Sol review returned `SOL_REVIEW_PASS` for
  operation `wynime-bangumi-sync-20260917-01a0`.
- Physical Android and Windows playback remain
  `HARDWARE_VALIDATION_PENDING`; native Windows action-level validation remains
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE`.

## 1.0.10 candidate status

- Version authority is `1.0.10+11`; the expected arm64 split APK versionCode is
  `2011`.
- Current-head `dart format --output=none --set-exit-if-changed lib test`,
  Flutter analyzer and the full deterministic test suite (261 tests) pass on
  this candidate worktree.
- Fixed phone and tablet emulator Library interactions pass, including the
  Library status tabs and On hold flow. A populated Bangumi account detail
  flow is not inferred from the unconfigured local debug build.
- Windows debug packaging and `flutter run -d windows --debug` pass; the
  runner bundle contains the required media-kit video plugin DLLs.
- Protected exact-SHA signing, final Android packaging and Windows ZIP
  packaging remain required for the immutable release candidate.

## 1.0.8 candidate status

- Version authority is `1.0.8+9`; the expected arm64 split APK versionCode is
  `2009`.
- The production Worker deployment `a5cccb1c-890c-4e59-bd5d-91a71c94b45c`
  publishes the exact Android package and certificate fingerprint and passes
  direct `/healthz`, App Link association and callback checks.
- The Android App Link repair validates only the published APK's exact single
  signer and makes the release workflow fail closed on association mismatch.
- Flutter analyzer and the full deterministic test suite (245 tests) pass on
  this candidate worktree; the Bangumi detail tests cover typed parsing,
  migration, cached detail round-trips, local-first state, partial failure and
  stale request protection.
- Protected CI signing, final Android packaging and Windows ZIP packaging
  remain required for the immutable release candidate.

## 1.0.5 published baseline

- Version authority is `1.0.5+6`; the expected arm64 split APK versionCode is
  `2006`.
- GitHub repository variable `WYNIME_BANGUMI_BROKER_ORIGIN` is configured with
  the production Worker origin.
- The Worker `ANDROID_CERT_SHA256` configuration is deployed and the public
  `assetlinks.json` now contains the exact production package and certificate
  fingerprint.
- Flutter analyzer, the full deterministic test suite (224 tests), broker
  typecheck and broker tests (12/12) pass on this candidate worktree.
- The local Android arm64-v8a Release APK passes versionCode `2006`,
  versionName `1.0.5` and `arm64-v8a` checks; it is unsigned and is not the
  protected CI signing result.
- Local Windows x64 Release compile/install passes and the Release process
  stays responsive. The Windows Debug launch still fails in third-party
  Debug-CRT linking, while the native Computer Use surface exposes `apps=[]`.
- The fixed Android phone and tablet AVD OAuth-entry evidence remains the
  previous 1.0.4 baseline; a fresh 1.0.5 physical callback exercise is still
  manual validation and is not substituted for physical playback evidence.

## External validation disclosure

The following items are non-blocking QA and are intentionally not upgraded to
release gates:

- Android physical hardware playback:
  `HARDWARE_VALIDATION_PENDING`.
- Windows physical hardware playback:
  `HARDWARE_VALIDATION_PENDING`.
- Windows native Computer Use action-level validation:
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` (`apps=[]`) in the audit environment.
- Additional manual exploratory UI testing: `PENDING`.

The fixed Android phone and tablet emulator action paths were exercised for
the earlier v1.0.1 shell and recorded as deterministic UI evidence. They do
not substitute for physical hardware playback or action-level validation of
the new Bangumi/update flows. The unavailable Windows Computer Use surface is
recorded as an environment limitation and does not claim compact, expanded,
live resize, mouse or keyboard actions passed.

## Runtime and UI evidence

Operation: `Wynime-Bangumi-1.0.5-20260910-0026-4f2c`.

- Current candidate Android arm64-v8a Release APK: metadata and ABI checks
  pass (`versionCode=2006`, `versionName=1.0.5`, `arm64-v8a`); local
  `apksigner verify` correctly reports unsigned. Protected CI signing remains
  required.
- Production Worker deployment `4911bd6c-f254-4590-9b08-5fe10cb0dfc1` passes
  `/healthz` with 200, routes the provider denial to
  `/oauth/app-callback`, returns the safe 200 fallback page, and publishes
  the exact Android package and release certificate in `assetlinks.json`.
- Current local Windows x64 Release binary: compile/install and an 8-second
  process liveness check pass. Native action-level UI could not be observed
  because the Computer Use session exposed only the in-app browser and
  `apps=[]`.
- Current fixed Android phone/tablet AVD launch: pass after the host restart;
  both AVDs booted and launched Wynime. Home and OAuth-entry screenshots are
  retained. User-reported account authorization and OS callback testing
  completed; physical playback remains pending.

- Android phone AVD `Pixel_API_36_Google_Play`: Android 16 / API 36,
  1080×2400, density 420. The updated Debug APK launched, the Home screen was
  captured, and the Bangumi sign-in action reached the Bangumi login page.
- Android tablet AVD `Pixel_Tablet_API_36_Google_Play`: Android 16 / API 36,
  2560×1600, density 320. The updated Debug APK launched, the expanded Home
  screen was captured, and the Bangumi sign-in action reached the Bangumi
  login page.
- Windows Release binary: build completed and the process launched
  responsively. Native action-level UI could not be observed because the
  fresh Computer Use session exposed only the in-app browser and
  `apps=[]`.

Evidence is retained outside the repository under the operation-specific
verification directory and is not used to claim physical hardware playback.

## Native engineering provenance

The locked dependency mechanism and candidate package contents were inspected
for every native component that enters the release distributions:

- `media_kit 1.2.6`, `media_kit_video 2.0.1`,
  `media_kit_libs_android_video 1.3.8` and
  `media_kit_libs_windows_video 1.0.12` from the pinned media-kit commit
  `e9abf3b9114fdb565b13a4c194d776c70e416e7d`;
- `flutter_inappwebview 6.2.0-beta.3`,
  `flutter_inappwebview_android 1.2.0-beta.3` and
  `flutter_inappwebview_windows 0.7.0-beta.3`;
- `sqlite3_flutter_libs 0.6.0+eol`;
- Android default ABI JARs from media-kit libmpv build release `v1.1.7`,
  with FFmpeg 6.0, mpv revision
  `78d43740f52db817d98bcf24fb30a76ab6fa13ff`, package-declared MD5 values
  and candidate SHA-256 values;
- Windows `mpv-dev-x86_64-20241021-git-0f78584.7z` from build source commit
  `8ddbe5472465950b87853789f7173f2eedc5586a` and `ANGLE.7z` v1.0.1, with
  package-declared MD5 values, local archive SHA-256 values, runtime
  version/configuration and packaged DLL hashes;
- Flutter/Dart runtime, WebView2 loader, SQLite, ANGLE/Vulkan/SwiftShader,
  zlib, media-kit plugin DLLs and all other native files in the Windows
  Release directory.

The selected Android default flavor disables GPL/nonfree FFmpeg options and
enables FFmpeg's version-3 licensing option according to the pinned upstream
`buildscripts/flavors/default.sh` record; it is covered by the complete
`COPYING.LGPLv3` text. LGPLv3 Section 4(b) also requires the accompanying
`COPYING.GPLv3` text; its exact upstream blob and SHA-256 are locked in the
native provenance record and both license files are packaged and checked. The
Windows release DLL runtime reports mpv
`v0.39.0-179-g0f78584518`, FFmpeg `N-117622-g8d940a07d` from exact commit
`8d940a07d19023a98689f353e4425a14688547e9`, `-Dgpl=false`, `-Dlibmpv=true`,
`-Dprefer_static=True` and static FFmpeg linkage. The exact Windows FFmpeg
policy is pinned to `packages/ffmpeg.cmake` blob
`ffbcbfc34882110acf2c271bdae994570bd62c39`, requiring
`--disable-gpl --disable-nonfree --enable-version3 --enable-static
--disable-shared` and rejecting both forbidden enable flags. The Windows
`--enable-version3` path is covered by the complete `COPYING.LGPLv3` text
and the accompanying `COPYING.GPLv3` text required by LGPLv3 Section 4(b).
The machine-readable lock and runtime verifier close this provenance chain;
no alternate FFmpeg or native package was substituted to satisfy the gate.

The exact upstream references, archive hashes, packaged hashes, runtime
probe, dependency versions and notice mapping are maintained in
`docs/THIRD_PARTY_PROVENANCE.md`. The same candidate notice material is
verified in the Android APK and Windows distributions by release automation.
The distributions carry `COPYING.LGPLv3` for both Android's pinned
`--enable-version3` native media and Windows' `--enable-version3` native
media, together with `THIRD_PARTY_SOURCE_OFFER.md`. The latter identifies the exact
corresponding source and relink procedure for the statically incorporated
FFmpeg/libmpv components.
No independent legal opinion was obtained; this is a disclosure, not a
machine release blocker under ADR-025.

## Package boundary

The Windows portable ZIP includes the complete Flutter Release directory plus
`README.md`, `LICENSE`, `THIRD_PARTY_NOTICES.md`, `COPYING.GPLv3`,
`COPYING.LGPLv3`,
`THIRD_PARTY_SOURCE_OFFER.md`,
`WINDOWS_LIBMPV_BUILD.lock.json`, `RELEASE_NOTES.md` and version-only
`version.txt`.

The historical `installer/windows/wynime.iss` file is retained as reference
material only. It is not invoked by the current release workflow. Windows
publication is ZIP-only and the ZIP includes the complete Flutter Release
bundle, the portable `wynime_update.exe` helper, version file, license texts,
corresponding-source offer, native provenance lock, notices and release notes.

Standalone FFmpeg execution, remuxing, MKV fallback, DRM/paywall bypass,
magnet/BT/seeding and source-provided executable adapters remain outside this
1.0.5 candidate boundary. Bangumi source integration and production Worker
configuration are included, while live account validation and platform runtime
evidence remain open until directly exercised.

## Security and privacy

- Playback uses the authoritative `PlaybackSession`, numeric loopback
  capability URI and exact current-session track identity.
- Platform errors are reduced to stable secret-safe diagnostic codes.
- Android cleartext traffic remains disabled except for bounded loopback
  hosts used by the local proxy.
- Telemetry is disabled by default.
- No signing secret, cookie, token or complete upstream media URL is persisted
  in release evidence or package metadata.

## Release decision

The machine-verifiable status is `RELEASE_READY`. External
validation disclosures remain visible and must be preserved in release notes;
they must not be rewritten as passes or used to imply hardware playback.
Publication still requires the exact annotated tag, exact-SHA CI, signed APK
verification, ZIP build, four-asset checksum verification and the protected
GitHub release environment.
