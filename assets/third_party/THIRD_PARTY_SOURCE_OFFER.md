# Wynime v1.0.1 corresponding source and relink offer

This document accompanies every Wynime v1.0.1 distribution. It is the
corresponding-source and relink manifest for the LGPL-covered native media
components used by this release. The Android and Windows default FFmpeg
builds both use `--enable-version3`; the applicable complete license text is
therefore shipped as `COPYING.LGPLv3` in the Android asset manifest and at the
root of the Windows distributions.

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
| Android libmpv build | media-kit libmpv-android-video-build release v1.1.7 (tag commit fe8c3ac1a91c09aa6fb1deccbc833f1bafa54768), mpv commit 78d43740f52db817d98bcf24fb30a76ab6fa13ff: https://github.com/media-kit/libmpv-android-video-build/tree/v1.1.7 |
| Android default FFmpeg build script | `buildscripts/flavors/default.sh`, Git blob `5968d5d2dc84dd4726540b846acbd26caa1984c3`, SHA-256 `d5b84c3398fc673c6210f6b0559a163d5c1e1c146b1dc52eab211c3ad0ba09ce`: https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/flavors/default.sh |
| Android dependency/build record | `buildscripts/include/depinfo.sh`, Git blob `481757452663bdac8162dea49e1699176411c5c7`, SHA-256 `3ac50b68e1669694f3e0b77d45a66bdae27a7bb23600389f6cfb686b924483b3`: https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/include/depinfo.sh |
| Android binary input | Exact default ABI JAR release assets and their package-declared MD5 values: https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7 |
| Android FFmpeg build policy | FFmpeg 6.0 source commit `ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2` (n6.0 tag object `3949db4d261748a9f34358a388ee255ad1a7f0c0`), with `--disable-gpl --disable-nonfree --enable-version3 --enable-static --disable-shared --enable-mbedtls`: https://github.com/FFmpeg/FFmpeg/tree/ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2 |
| Android LGPLv3 license | Complete shipped text `assets/flutter_assets/assets/third_party/COPYING.LGPLv3`, canonical repository SHA-256 `da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768`, source reference: https://raw.githubusercontent.com/FFmpeg/FFmpeg/ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2/COPYING.LGPLv3 |
| Windows Flutter package input | `media_kit_libs_windows_video` 1.0.12 from the media-kit repository at commit `e9abf3b9114fdb565b13a4c194d776c70e416e7d`, path `libs/windows/media_kit_libs_windows_video`: https://github.com/media-kit/media-kit/tree/e9abf3b9114fdb565b13a4c194d776c70e416e7d/libs/windows/media_kit_libs_windows_video |
| Windows libmpv build input | Exact x64 development archive `mpv-dev-x86_64-20241021-git-0f78584.7z`, release tag `20241021`, tag commit `8ddbe5472465950b87853789f7173f2eedc5586a`: https://github.com/media-kit/libmpv-win32-video-cmake/releases/tag/20241021 |
| Windows build workflow/source | Source commit `8ddbe5472465950b87853789f7173f2eedc5586a`; build workflow: https://github.com/media-kit/libmpv-win32-video-cmake/actions/runs/11444402983 |
| Windows mpv source identity | mpv commit `0f7858451817c5fd5ebdb74a807a7c997662c390`, recorded by the release probe: https://github.com/mpv-player/mpv/tree/0f7858451817c5fd5ebdb74a807a7c997662c390 |
| Windows FFmpeg source identity | FFmpeg commit `8d940a07d19023a98689f353e4425a14688547e9`, runtime version `N-117622-g8d940a07d`: https://github.com/FFmpeg/FFmpeg/tree/8d940a07d19023a98689f353e4425a14688547e9 |
| Windows FFmpeg build policy | Exact `packages/ffmpeg.cmake` at source commit `8ddbe5472465950b87853789f7173f2eedc5586a`, blob `ffbcbfc34882110acf2c271bdae994570bd62c39`, requires `--disable-gpl --disable-nonfree --enable-version3 --enable-static --disable-shared`: https://raw.githubusercontent.com/media-kit/libmpv-win32-video-cmake/8ddbe5472465950b87853789f7173f2eedc5586a/packages/ffmpeg.cmake |
| Windows LGPLv3 license | Exact `COPYING.LGPLv3` from FFmpeg commit `8d940a07d19023a98689f353e4425a14688547e9`, SHA-256 `da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768`: https://raw.githubusercontent.com/FFmpeg/FFmpeg/8d940a07d19023a98689f353e4425a14688547e9/COPYING.LGPLv3 |
| Windows native provenance lock | `assets/third_party/WINDOWS_LIBMPV_BUILD.lock.json`, including archive/DLL hashes, runtime identities, source blobs and custom patch identities |
| Wynime application/relink inputs | Exact release source and automation at tag v1.0.1: https://github.com/william12233/Wynime/tree/v1.0.1 |

The Android and Windows build scripts, dependency records, selected
configuration, archive hashes and packaged native hashes are enumerated in
docs/THIRD_PARTY_PROVENANCE.md and the machine-readable Windows lock. The
build uses the pinned upstream mechanisms and does not substitute an
unrecorded FFmpeg or libmpv version.

## Relink procedure

1. Obtain the Wynime source at tag v1.0.1, the Android FFmpeg n6.0 source,
   and the Windows FFmpeg commit `8d940a07d19023a98689f353e4425a14688547e9`
   plus the media-kit Android/Windows build inputs listed above.
2. Use the pinned media-kit build scripts, source commit and corresponding
   patch identities to rebuild the LGPL-covered libmpv/FFmpeg native library
   for the target ABI or x64 platform. Preserve the recorded `-Dgpl=false`
   configuration and the corresponding source revisions.
3. Replace only the platform native library input in the platform build
   workspace, then run the checked-in Flutter/Gradle or CMake packaging
   workflow. The resulting application can be relinked with the modified
   compatible native library without changing the Wynime source license.

The release workflow fail-closes unless this offer, the full applicable
Android and Windows LGPLv3 text, its locked hash, the upstream
source references and the package-content checks are present. No signing key,
password or private material is part of the corresponding-source offer.
