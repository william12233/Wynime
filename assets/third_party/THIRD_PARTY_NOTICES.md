# Wynime third-party notices

This notice is shipped with the Wynime v1.0.1 Android APK, Windows installer
and Windows portable ZIP. It is an engineering attribution and provenance
record for the release boundary. The Wynime-owned source license is the
bundled `LICENSE` file; third-party components retain their own upstream
terms.

## Wynime source

Wynime-owned source code is provided under the MIT License in `LICENSE`.
The Android APK includes that file as
`assets/flutter_assets/LICENSE`. Windows distributions include it at their
root. This notice does not relicense any dependency or native binary.

## Flutter packages

The following packages are resolved from `pubspec.lock`:

| Package | Version | License / source |
| --- | --- | --- |
| `media_kit` | 1.2.6 | MIT; <https://github.com/media-kit/media-kit/blob/main/LICENSE> |
| `media_kit_video` | 2.0.1 | MIT; <https://github.com/media-kit/media-kit/blob/main/LICENSE> |
| `media_kit_libs_android_video` | 1.3.8 | MIT wrapper; <https://github.com/media-kit/media-kit/blob/main/libs/android/media_kit_libs_android_video/LICENSE> |
| `media_kit_libs_windows_video` | 1.0.12, Git commit `e9abf3b9114fdb565b13a4c194d776c70e416e7d` | MIT wrapper; <https://github.com/media-kit/media-kit/tree/e9abf3b9114fdb565b13a4c194d776c70e416e7d/libs/windows/media_kit_libs_windows_video> |
| `flutter_inappwebview` | 6.2.0-beta.3 | Apache-2.0; <https://github.com/pichillilorenzo/flutter_inappwebview/blob/master/LICENSE> |
| `flutter_inappwebview_android` | 1.2.0-beta.3 | Apache-2.0; package LICENSE |
| `flutter_inappwebview_windows` | 0.7.0-beta.3 | Apache-2.0; package LICENSE |
| `sqlite3_flutter_libs` | 0.6.0+eol | MIT; <https://github.com/simolus3/sqlite3.dart/blob/main/LICENSE> |

## Android native media

The Android video package selects the `default` ABI JARs from the
media-kit libmpv Android build release `v1.1.7`. The package build script
verifies the upstream MD5 values before using the JARs. The upstream
dependency record identifies FFmpeg 6.0, mpv revision
`78d43740f52db817d98bcf24fb30a76ab6fa13ff`, and the default flavor's
non-GPL/nonfree dependency branch.

- Release: <https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7>
- Dependency record: <https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/include/depinfo.sh>
- Build license: <https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/LICENSE>
- FFmpeg licensing: <https://ffmpeg.org/legal.html>

The exact JAR and packaged `libmpv.so` hashes are recorded in
`docs/THIRD_PARTY_PROVENANCE.md` and regenerated in the final candidate
evidence.

## LGPL-covered native media and corresponding source

The `-Dgpl=false` native media builds use the LGPL path with FFmpeg
statically incorporated into libmpv. The applicable license differs by
platform and is not interchangeable:

- **Android LGPLv2.1:** the Android default libmpv build uses the complete
  `COPYING.LGPLv2.1` text at
  `assets/flutter_assets/assets/third_party/COPYING.LGPLv2.1`.
- **Windows LGPLv3:** the pinned Windows FFmpeg build enables
  `--enable-version3`, so the complete `COPYING.LGPLv3` text is required at
  the Windows distribution root and is also retained in the common Android
  asset manifest.

The canonical repository hashes are `246041b6ecf9bc32d718a62c57877c78b5eb397b6467e74ed7ae2626ab189c30`
for the shipped LGPLv2.1 text and
`da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768` for the
FFmpeg-commit LGPLv3 text. The corresponding source and relink offer is
shipped as `THIRD_PARTY_SOURCE_OFFER.md` in the Windows distribution and as
`assets/flutter_assets/assets/third_party/THIRD_PARTY_SOURCE_OFFER.md` in
the Android APK. It provides the immutable Android FFmpeg n6.0 and Windows
FFmpeg/media-kit source/build references, the exact dependency records, the
Windows native provenance lock, and a written offer valid from 2026-09-07
through 2029-09-07.

## Windows native media

The locked Windows CMake mechanism selects:

- `mpv-dev-x86_64-20241021-git-0f78584.7z` from the media-kit
  `20241021` release at build commit
  `8ddbe5472465950b87853789f7173f2eedc5586a`;
- `ANGLE.7z` from `flutter-windows-ANGLE-OpenGL-ES` `v1.0.1`;
- package-declared MD5 values, which are checked before extraction.

The Release `libmpv-2.dll` probe reports mpv
`v0.39.0-179-g0f78584518`, FFmpeg `N-117622-g8d940a07d` from exact commit
`8d940a07d19023a98689f353e4425a14688547e9`, `-Dgpl=false`,
`-Dlibmpv=true`, `-Dprefer_static=True` and `-Degl-angle=enabled`. The
upstream `packages/ffmpeg.cmake` blob
`ffbcbfc34882110acf2c271bdae994570bd62c39` requires
`--disable-gpl --disable-nonfree --enable-version3 --enable-static
--disable-shared`; the release gate rejects either forbidden enable flag.
FFmpeg is statically linked into libmpv; no separate FFmpeg DLL is
redistributed. Because `--enable-version3` selects the LGPLv3 licensing path,
the complete machine-readable record and the exact `COPYING.LGPLv3` text are
shipped together with `WINDOWS_LIBMPV_BUILD.lock.json`.

The exact Windows native hashes are:

| File | Size | SHA-256 |
| --- | ---: | --- |
| `mpv-dev-x86_64-20241021-git-0f78584.7z` | 11,601,537 bytes | `E23701DF0ADC1FE57C8EDE3FF313513B0B80519870058C2D35FF02754284A007` |
| `libmpv-2.dll` | 37,735,936 bytes | `56F9A69200863C2DCD2FB6367427FFEDC35550D26F947D6C36EAB37BD2A65FD5` |

The Windows distribution also contains:

| Component | License / source reference |
| --- | --- |
| `libmpv-2.dll` | media-kit Windows build; <https://github.com/mpv-player/mpv/blob/master/Copyright> |
| `libEGL.dll`, `libGLESv2.dll` | ANGLE; <https://chromium.googlesource.com/angle/angle/+/main/LICENSE> |
| `vk_swiftshader.dll` | SwiftShader; <https://github.com/google/swiftshader/blob/main/LICENSE.txt> |
| `vulkan-1.dll` | Vulkan Loader; <https://github.com/KhronosGroup/Vulkan-Loader/blob/main/LICENSE.txt> |
| `zlib.dll` | zlib; <https://zlib.net/zlib_license.html> |
| `sqlite3.dll` | SQLite public-domain notice; <https://www.sqlite.org/copyright.html> |
| `d3dcompiler_47.dll` | Microsoft Direct3D compiler redistribution terms |
| `WebView2Loader.dll` | WebView2; <https://github.com/MicrosoftEdge/WebView2/blob/main/LICENSE> |
| `flutter_windows.dll`, `dartjni.dll` | Flutter/Dart runtime; <https://github.com/flutter/flutter/blob/master/LICENSE> |
| media-kit and WebView plugin DLLs | package licenses referenced above |

The complete archive, runtime, PE dependency and packaged-binary hash
mapping is recorded in `docs/THIRD_PARTY_PROVENANCE.md`.

## Engineering disclosure

The exact source identity, selected versions/configuration, archive hashes,
packaged hashes and required attribution references were checked for this
release. No unrecorded FFmpeg, mpv, ANGLE or WebView2 package was substituted.
No independent legal opinion is claimed; this notice is not a project-wide
relicensing statement.

## Candidate-specific evidence

The final candidate SHA and artifact hashes are recorded outside the
repository in the operation-specific verification transcript. Release
automation verifies that this notice and `LICENSE` are present in the
corresponding package contents.
