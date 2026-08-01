# Phase 0 Implementation Status

This file records the implementation baseline for Phase 0. The authoritative
scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md`, and
`docs/DECISIONS.md`.

## Locked toolchain

- Flutter: `3.44.8` stable
- Bundled Dart: `3.12.2`
- Android package / namespace: `io.github.william12233.wynime`
- Java for CI Android builds: Temurin 17
- Target platforms: Android and Windows only

Flutter is pinned to an exact stable hotfix release. The generated Android and
Windows runner projects come from that exact SDK, so Gradle, Android Gradle
Plugin, Kotlin, CMake, and Windows runner templates stay mutually compatible
with Flutter instead of being selected independently.

## Implemented Phase 0 surface

- Android and Windows Flutter runners
- Compact, Medium, and Expanded app shells
- Home, Search, Library, Downloads, Sources, and Settings placeholders
- `gen_l10n` ARB resources for Traditional Chinese, Simplified Chinese,
  Japanese, and English, with generic Chinese resolving to Traditional Chinese
- Shared design tokens with no bundled font
- Pure Dart Domain contracts, including the authoritative
  `PlaybackSession`, `AdRemovalPlan`, `DownloadArtifactManifest`, and
  `DeleteJob`
- Unit, widget, architecture-boundary, and fixed-size Golden smoke tests
- CI gates for format, analyze, tests, Android build, and Windows build

## Explicitly absent

No live source parsing, WebView interception, media playback, downloads,
FFmpeg, mpv, Bangumi production authentication, or source package execution is
implemented in Phase 0.

## Validation status

- Flutter dependency resolution: **PASS**
- Localization generation: **PASS**
- Dart formatting: **PASS**
- Domain and application tests before Golden generation: **11 PASS**
- Full test suite used to generate and verify all four Golden baselines:
  **PASS**
- Final permanent CI run covering analyze, tests, Android build, and Windows
  build: **IN PROGRESS**

Phase 0 must not be marked complete until every permanent CI job passes on the
current branch head.
