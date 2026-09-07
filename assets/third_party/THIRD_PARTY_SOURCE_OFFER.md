# Wynime v1.0.1 corresponding source and relink offer

This document accompanies every Wynime v1.0.1 distribution. It is the
corresponding-source and relink manifest for the LGPL-covered native media
components used by this release. The complete license text is shipped as
COPYING.LGPLv2.1 beside this document.

## Written offer

For three years from 2026-09-07 through 2029-09-07, william12233 offers each
recipient of Wynime v1.0.1 the complete corresponding machine-readable source
and relink materials identified below, at no charge other than the reasonable
cost of performing the distribution. The public HTTPS locations below provide
equivalent access from the same distribution project. If an upstream location
becomes unavailable, request the same materials through the Wynime repository
issue tracker:

https://github.com/william12233/Wynime/issues

The offer covers the FFmpeg/libmpv portions statically incorporated into the
Android libmpv.so files and the Windows libmpv-2.dll. It does not claim that
Wynime source code is under the LGPL; Wynime-owned source remains under the
root LICENSE.

## Immutable corresponding-source inputs

The following are the exact source, build-script and dependency inputs for the
release boundary. The release tag v1.0.1 contains the complete Wynime source,
pubspec.lock, platform build configuration, native provenance inventory and
release automation used to reproduce the application-side relink.

| Component | Corresponding source or relink material |
| --- | --- |
| FFmpeg | FFmpeg release n6.0 (tag object 3949db4d261748a9f34358a388ee255ad1a7f0c0): https://github.com/FFmpeg/FFmpeg/tree/n6.0 and https://github.com/FFmpeg/FFmpeg/archive/refs/tags/n6.0.tar.gz |
| Android libmpv build | media-kit libmpv-android-video-build release v1.1.7 (tag object fe8c3ac1a91c09aa6fb1deccbc833f1bafa54768): https://github.com/media-kit/libmpv-android-video-build/tree/v1.1.7 |
| Android dependency/build record | Pinned record and build scripts: https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/include/depinfo.sh |
| Android binary input | Exact default ABI JAR release assets and their package-declared MD5 values: https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7 |
| Windows libmpv build input | Exact x64 development archive release 2023-09-24: https://github.com/media-kit/libmpv-win32-video-build/releases/tag/2023-09-24 |
| Windows mpv source identity | mpv commit 652a1dd90711839acdccc08004056d25514ef2d8 recorded by the release probe: https://github.com/mpv-player/mpv/tree/652a1dd90711839acdccc08004056d25514ef2d8 |
| Windows FFmpeg source identity | FFmpeg n6.0, statically incorporated by the -Dgpl=false libmpv build: https://github.com/FFmpeg/FFmpeg/tree/n6.0 |
| Wynime application/relink inputs | Exact release source and automation at tag v1.0.1: https://github.com/william12233/Wynime/tree/v1.0.1 |

The Android and Windows build scripts, dependency records, selected
configuration, archive hashes and packaged native hashes are enumerated in
docs/THIRD_PARTY_PROVENANCE.md. The build uses the pinned upstream mechanisms
and does not substitute an alternate FFmpeg or libmpv version.

## Relink procedure

1. Obtain the Wynime source at tag v1.0.1, the FFmpeg n6.0 source, and the
   media-kit Android/Windows build inputs listed above.
2. Use the pinned media-kit build scripts and dependency record to rebuild the
   LGPL-covered libmpv/FFmpeg native library for the target ABI or x64
   platform. Preserve the recorded -Dgpl=false configuration and the
   corresponding source revisions.
3. Replace only the platform native library input in the platform build
   workspace, then run the checked-in Flutter/Gradle or CMake packaging
   workflow. The resulting application can be relinked with the modified
   compatible native library without changing the Wynime source license.

The release workflow fail-closes unless this offer, the full
COPYING.LGPLv2.1 text, the upstream source references and the package-content
checks are present. No signing key, password or private material is part of
the corresponding-source offer.
