# Wynime Release Contract

This document defines the version, artifact and publication contract. The
workflow in `.github/workflows/release.yml` is the only supported GitHub
Release publisher. Creating this file does not publish anything.

## Version and tag

- A release tag must be `vX.Y.Z`.
- `X.Y.Z` must exactly match the base version in `pubspec.yaml`.
- `pubspec.yaml` must also contain a positive, intentionally incremented
  Android build number after the `+` suffix.
- The matching `docs/release-notes-X.Y.Z.md` file must exist.
- The current candidate is `1.0.1+2`, corresponding to tag `v1.0.1` and
  Android `versionCode` `2`.

## GitHub Release assets

The release contains exactly these four uploaded files:

| Platform | Artifact | SHA-256 sidecar |
| --- | --- | --- |
| Android multi-ABI APK | `wynime-X.Y.Z.apk` | `wynime-X.Y.Z.apk.sha256` |
| Windows x64 portable | `wynime-X.Y.Z.zip` | `wynime-X.Y.Z.zip.sha256` |

The AAB may be built as an internal/Play Store handoff artifact, but it is not
uploaded to the GitHub Release by this contract.

## Windows portable contents

The ZIP must contain the complete contents of
`build/windows/x64/runner/Release`, including `wynime.exe`, and must add:

- `README.md`;
- `version.txt` containing exactly `X.Y.Z` with no extra text.

There is no updater executable in Wynime's current architecture, so the
workflow must not claim updater support or require an updater file.

## Local preparation

The checked-in PowerShell helper builds the Android APK and Windows bundle,
then writes versioned inspection assets and SHA-256 sidecars under the ignored
`build/release` directory:

```powershell
pwsh -NoProfile -File .github/scripts/build_release_assets.ps1 -AllowBlockedInspection
```

`-AllowBlockedInspection` is intentionally required while Phase 12 is blocked;
it produces local, unsigned inspection artifacts and cannot publish anything.
Omit that switch after the status is independently changed to
`RELEASE_READY`. The helper never creates a tag, pushes a branch or calls the
GitHub Release API. A clean Windows build also requires the NuGet command-line
tool because of the Windows WebView2 plugin; the checked-in GitHub workflow
installs it before resolving dependencies.

## Release gate and secrets

The workflow refuses to proceed unless `docs/PHASE12_STATUS.md` contains the
explicit status `RELEASE_READY`. The current status is
`AUDIT_COMPLETE_RELEASE_BLOCKED`.

The Android job requires these GitHub Actions secrets:

- `WYNIME_RELEASE_KEYSTORE_BASE64`;
- `WYNIME_RELEASE_STORE_PASSWORD`;
- `WYNIME_RELEASE_KEY_ALIAS`;
- `WYNIME_RELEASE_KEY_PASSWORD`.

The keystore is materialized only in the ephemeral CI workspace, passed to the
existing Gradle signing boundary, and removed after the Android job. No
signing secret belongs in the repository, issue tracker or release notes.

Before changing the status to `RELEASE_READY`, the project must independently
close the current Phase 12 requirements: source and deterministic checks,
publish signing, native provenance and linked licenses, reviewed FFmpeg and a
real remux fixture, observable Windows UI actions, and supported Android /
Windows hardware playback.

The native provenance inventory and its open notice/license checklist are
maintained in
[`docs/THIRD_PARTY_PROVENANCE.md`](THIRD_PARTY_PROVENANCE.md).

## Publication sequence

1. Update `pubspec.yaml`, `CHANGELOG.md` and the versioned release notes.
2. Close and record every Phase 12 gate; do not bypass `RELEASE_BLOCKED`.
3. Configure the four GitHub Actions signing secrets.
4. Create and push the matching `vX.Y.Z` tag.
5. Let `release.yml` build, sign, verify, hash and publish the exact four
   assets.

No local preparation step creates a tag, pushes a branch or calls the GitHub
Release API.

## GitHub secret setup

The AVACA screenshot is only a reference. Do not copy its keystore path,
certificate fingerprint or passwords into Wynime. Wynime uses the Android
application ID 'io.github.william12233.wynime' and should use a dedicated
Wynime release keystore (or an already-approved Wynime keystore).

Create a keystore outside the repository. keytool prompts for the store and
key passwords; keep the alias and passwords for the GitHub secret values:

~~~powershell
keytool -genkeypair -v -keystore C:/secure/wynime-release.jks -alias wynime-release -keyalg RSA -keysize 2048 -validity 10000
~~~

Convert that exact keystore to one-line Base64 without committing either file:

~~~powershell
$keystorePath = 'C:/secure/wynime-release.jks'
$base64Path = 'C:/secure/wynime-release.base64.txt'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath)) |
  Set-Content -NoNewline -Encoding ascii -LiteralPath $base64Path
~~~

Create these GitHub Actions repository secrets under
Settings > Secrets and variables > Actions:

1. WYNIME_RELEASE_KEYSTORE_BASE64: the complete contents of
   wynime-release.base64.txt;
2. WYNIME_RELEASE_STORE_PASSWORD: the keystore password;
3. WYNIME_RELEASE_KEY_ALIAS: normally wynime-release;
4. WYNIME_RELEASE_KEY_PASSWORD: the key password.

The workflow decodes the keystore only inside the ephemeral Android runner,
checks the file and alias before building, signs the APK, and removes the
temporary keystore in an always() cleanup step. Never commit the .jks,
Base64 file, or passwords.
