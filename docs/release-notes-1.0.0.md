# Wynime 1.0.0

Wynime 1.0.0 is the first cross-platform product candidate for Android and
Windows. It provides the application shell, typed source and playback
boundaries, local artifact safety contracts and deterministic validation
coverage described in the project plan.

## Highlights

- Responsive Home, Search, Library, Downloads, Sources and Settings pages.
- Traditional Chinese, Simplified Chinese, Japanese and English localization.
- Truthful offline, unavailable and review-required states for features that
  do not have an enabled source or live account.
- Shared `PlaybackSession` routing between Android Media3, libmpv/media-kit
  and the bounded WebView fallback boundary.
- Root-confined download and artifact operations with manifest-authorized
  deletion and secret-safe diagnostics.
- Typed Bangumi synchronization and declarative source-package proposals,
  both validated through deterministic fixtures.

## Validation boundary

The candidate's source, analyzer, deterministic tests, Goldens, Android build
and Windows build gates are recorded in `docs/PHASE12_STATUS.md`. This file is
release notes for the candidate; it is not evidence that the release gate has
passed.

## Known release blockers

- A dedicated external Android release keystore and the four GitHub Actions
  secret names are configured, but an official tagged CI signing run has not
  yet been produced.
- The complete project, libmpv, FFmpeg, ANGLE and linked-library license /
  provenance record is not closed.
- A reviewed FFmpeg runtime and real remux fixture are not available on the
  current host.
- Windows action-level UI evidence and supported Android/Windows hardware
  playback remain unverified.
- The in-app browser/Sol review runtime must be available before the required
  independent review is recorded.

## GitHub Release assets

When all gates are explicitly closed, the release workflow publishes exactly:

- `wynime-1.0.0.apk` and `wynime-1.0.0.apk.sha256`;
- `wynime-1.0.0.zip` and `wynime-1.0.0.zip.sha256`.

The APK is the multi-ABI package produced by the current Android build. The
Windows ZIP is a portable Flutter bundle and contains `wynime.exe`, all
required runtime files, `README.md` and `version.txt`.
