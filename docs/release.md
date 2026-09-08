# Wynime Release Contract

This document defines the version, artifact, machine-verifiable gate and
publication contract. The workflow in
`.github/workflows/release.yml` is the only supported GitHub Release
publisher.

## Version and immutable candidate

- A release tag is `vX.Y.Z`.
- `X.Y.Z` must exactly match the base version in `pubspec.yaml`, and the
  `+` suffix must be a positive Android build number.
- `docs/release-notes-X.Y.Z.md` must exist.
- The `v1.0.1` candidate is application version `1.0.1`, Android
  `versionCode` `2`.
- The tag target, push trigger SHA and current `origin/main` must be the same
  immutable commit.

## Hard release gates

The following gates are machine-verifiable and block publication:

1. exact candidate SHA equals `origin/main`;
2. a successful `phase-0-ci.yml` run exists for that exact SHA;
3. `dart format`, `flutter analyze --fatal-infos` and the required tests pass;
4. Android release APK build succeeds with the configured
   `WYNIME_RELEASE_*` keystore;
5. `apksigner verify`, APK alignment, version metadata and required ABIs pass;
6. the Windows x64 Flutter Release build succeeds;
7. the Android APK, Windows installer and portable ZIP each have a matching
   SHA-256 sidecar;
8. `THIRD_PARTY_NOTICES.md`, the complete applicable `COPYING.LGPLv2.1`
   text and `THIRD_PARTY_SOURCE_OFFER.md` are present in the Android and
   Windows distributions;
9. recorded native archive and packaged-binary hashes match the candidate;
10. the corresponding-source/relink mechanism is concrete, source URLs and
    pinned revisions are recorded, and release automation fails closed if the
    materials are absent;
11. no secret, keystore or password is present in source or artifacts;
12. versioned release notes exist;
13. the annotated tag points exactly to the release candidate;
14. GitHub Release assets are built from that same immutable SHA.

`docs/THIRD_PARTY_PROVENANCE.md` must declare
`CLOSED_RELEASE_PROVENANCE`, and `LICENSE` plus the exact
`Approved Wynime distribution license: yes` marker must be present.
This is an engineering provenance and packaging gate; an independent legal
opinion is not a machine gate for this release.

## Non-blocking external validation

These checks remain explicitly disclosed but do not block `RELEASE_READY`:

- `HARDWARE_VALIDATION_PENDING`: physical Android playback;
- `HARDWARE_VALIDATION_PENDING`: physical Windows playback;
- `WINDOWS_CUA_VALIDATION_UNAVAILABLE`: native Windows action-level
  Computer Use evidence when the audit surface exposes `apps=[]`;
- additional manual exploratory UI testing.

The project must never claim these checks passed when they were not run.
Deterministic tests, fixed phone/tablet emulator action evidence, current-head
builds and independent read-only review are recorded separately.

## Wynime license and third-party provenance

`LICENSE` is the repository owner's MIT license for Wynime-owned source code
only. It does not relicense or replace any third-party dependency, native
binary, codec or system runtime license.

The engineering provenance inventory must identify the resolved package
versions, upstream native artifacts, selected build flavor/configuration,
archive hashes, packaged-binary hashes, runtime identity, applicable license
references and shipped notices. The locked media-kit Android default flavor,
Windows libmpv archive, FFmpeg linkage, ANGLE inputs, WebView2 loader, SQLite,
Flutter/Dart runtime and all redistributed DLLs are covered by
`docs/THIRD_PARTY_PROVENANCE.md` and
`assets/third_party/THIRD_PARTY_NOTICES.md`.
The complete applicable LGPLv2.1 text is shipped as
`assets/third_party/COPYING.LGPLv2.1`, and the corresponding-source/relink
mechanism is shipped as `assets/third_party/THIRD_PARTY_SOURCE_OFFER.md`.

## GitHub Release assets

The release contains exactly these six files:

| Platform | Artifact | SHA-256 sidecar |
| --- | --- | --- |
| Android multi-ABI | `wynime-X.Y.Z.apk` | `wynime-X.Y.Z.apk.sha256` |
| Windows x64 installer | `wynime-X.Y.Z-windows-x64-setup.exe` | `wynime-X.Y.Z-windows-x64-setup.exe.sha256` |
| Windows x64 portable | `wynime-X.Y.Z.zip` | `wynime-X.Y.Z.zip.sha256` |

The AAB remains an internal/Play Store handoff artifact and is not uploaded
to the GitHub Release by this contract.

## Windows installer

`installer/windows/wynime.iss` is compiled with the `ISCC.exe` already
available on the `windows-2025` runner. The installer:

- installs the complete Flutter Windows Release bundle;
- uses a stable AppId and per-user default directory;
- creates a Start Menu shortcut;
- offers an unchecked optional desktop shortcut;
- includes `LICENSE`, `COPYING.LGPLv2.1`, `THIRD_PARTY_SOURCE_OFFER.md`,
  `THIRD_PARTY_NOTICES.md`, `WINDOWS_LIBMPV_BUILD.lock.json`, `README.md` and
  release notes;
- creates a complete uninstaller without deleting Wynime user data;
- uses app name `Wynime`, version `1.0.1`, publisher `william12233` and
  executable `wynime.exe`.

No Authenticode certificate is configured for this candidate. The installer
is therefore intentionally unsigned and may trigger a Windows SmartScreen
warning; no unsigned installer is presented as signed evidence.

The portable ZIP contains the complete
`build/windows/x64/runner/Release` directory plus `README.md`, `LICENSE`,
`THIRD_PARTY_NOTICES.md`, `COPYING.LGPLv2.1`,
`THIRD_PARTY_SOURCE_OFFER.md`, `WINDOWS_LIBMPV_BUILD.lock.json`,
`RELEASE_NOTES.md` and a version-only `version.txt`.

## Local preparation

The checked-in helper builds the local inspection APK and Windows bundle,
creates the portable ZIP, and builds the installer when `ISCC.exe` is
available:

~~~powershell
pwsh -NoProfile -File .github/scripts/build_release_assets.ps1
~~~

Use `-SkipInstaller` only for portable-only local diagnostics when Inno Setup
is unavailable. The helper never creates a tag, pushes a branch or calls the
GitHub Release API. Local APK output is unsigned unless the external signing
properties are configured; official publication signing is performed and
verified in GitHub Actions.

## Release notes and validation disclosure

`docs/release-notes-1.0.1.md` must list Android APK, Windows x64 installer
and Windows x64 portable ZIP. It must state that automated
tests/build/signing passed, emulator UI evidence passed where exercised,
physical Android/Windows validation is pending, and native Windows Computer
Use action-level validation is unavailable in the audit environment.
It must also disclose that the Windows installer is unsigned when no
certificate is configured.

## Publication sequence

1. Update the versioned release notes and release metadata.
2. Close the engineering provenance and hard release gates; retain external
   validation disclosures.
3. Freeze one final candidate SHA and obtain successful exact-SHA phase-0 CI.
4. Re-fetch `origin/main`, verify the candidate is its exact tip, and push
   normal non-force history.
5. Dispatch the protected candidate-signing workflow for that SHA and inspect
   the signed APK/AAB hashes.
6. Confirm the worktree is clean and create the annotated `vX.Y.Z` tag at
   that exact SHA.
7. Push the tag and let `release.yml` build, verify and publish the six
   immutable release assets.
8. Monitor the workflow and verify the GitHub Release URL, tag target,
   asset set, sidecars and successful conclusion.

The `github-release` job uses the protected `release` environment. GitHub
deployment approval remains a platform control and must not be bypassed.
An existing GitHub Release is never mutated.

## Android signing boundary

The Android job requires these GitHub Actions secrets:

- `WYNIME_RELEASE_KEYSTORE_BASE64`;
- `WYNIME_RELEASE_STORE_PASSWORD`;
- `WYNIME_RELEASE_KEY_ALIAS`;
- `WYNIME_RELEASE_KEY_PASSWORD`.

The keystore is materialized only in the ephemeral runner, passed through the
existing Gradle signing boundary, verified with `apksigner`, and removed in
an `always()` cleanup step. Secret values, `.jks` files and Base64 material
must never be committed.
