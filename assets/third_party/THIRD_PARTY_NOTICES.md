# Wynime third-party notices

This notice is shipped with the Wynime candidate packages. It records the
exact native media inputs observed for this candidate and points to the
authoritative upstream license texts. It is not a project-wide license and it
is not a substitute for independent legal review.

## Flutter packages

The following Dart and Flutter wrapper packages are resolved from
`pubspec.lock`:

| Package | Version | License / source |
| --- | --- | --- |
| `media_kit` | 1.2.6 | MIT; <https://github.com/media-kit/media-kit/blob/main/LICENSE> |
| `media_kit_video` | 2.0.1 | MIT; <https://github.com/media-kit/media-kit/blob/main/LICENSE> |
| `media_kit_libs_android_video` | 1.3.8 | MIT wrapper; <https://github.com/media-kit/media-kit/blob/main/libs/android/media_kit_libs_android_video/LICENSE> |
| `media_kit_libs_windows_video` | 1.0.11 | MIT wrapper; <https://github.com/media-kit/media-kit/blob/main/libs/windows/media_kit_libs_windows_video/LICENSE> |
| `flutter_inappwebview` | 6.2.0-beta.3 | Apache-2.0; <https://github.com/pichillilorenzo/flutter_inappwebview/blob/master/LICENSE> |
| `sqlite3_flutter_libs` | 0.6.0+eol | MIT; <https://github.com/simolus3/sqlite3.dart/blob/main/LICENSE> |

## Android native media input

The Windows and Android packages use the media-kit build repositories rather
than a locally selected or silently substituted FFmpeg version. Android uses
the `default` ABI JARs from the `v1.1.7` release:

- Source release: <https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7>
- Build dependency record: <https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/include/depinfo.sh>
- Upstream build license: <https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/LICENSE>
- The `default` build records FFmpeg `6.0`, mpv revision
  `78d43740f52db817d98bcf24fb30a76ab6fa13ff`, `--disable-gpl`,
  `--disable-nonfree`, and no GPL encoder flavor.
- The four JAR inputs are verified by the package's declared MD5 values and
  the candidate records their SHA-256 values in
  `docs/THIRD_PARTY_PROVENANCE.md`.

The `default` build license file applies to the default/full flavor. The
transitive source licenses remain governed by each upstream component and are
listed by the upstream dependency record; a downstream redistributor must
retain the applicable notices for the exact native binaries shipped.

## Windows native media input

The locked `media_kit_libs_windows_video` `1.0.11` CMake file selects:

- libmpv archive `mpv-dev-x86_64-20230924-git-652a1dd.7z` from the
  `2023-09-24` release;
- ANGLE archive `ANGLE.7z` from `flutter-windows-ANGLE-OpenGL-ES` `v1.0.1`;
- the exact declared MD5 values and local SHA-256 values recorded in
  `docs/THIRD_PARTY_PROVENANCE.md`.

The candidate's release `libmpv-2.dll` runtime reports:

```text
mpv-version=mpv v0.36.0-403-g652a1dd907
ffmpeg-version=n6.0
mpv-configuration=... -Dgpl=false ... -Dlibmpv=true ... -Degl-angle=enabled ...
```

The full probe output, archive hashes, PE dependency audit and the exact
source URLs are recorded in the provenance document. The runtime has FFmpeg
statically linked into libmpv; no separate FFmpeg DLL is redistributed by the
Windows bundle.

Important mpv licensing guidance:

- mpv's official `Copyright` file explains the GPLv2+/LGPLv2.1+ modes and
  warns that `-Dgpl=false` alone is not a license grant;
- linked FFmpeg and other native libraries retain their own terms;
- source and license links: <https://github.com/mpv-player/mpv/blob/master/Copyright>,
  <https://github.com/mpv-player/mpv/blob/master/LICENSE.LGPL>, and
  <https://github.com/mpv-player/mpv/blob/master/LICENSE.GPL>;
- FFmpeg licensing references: <https://ffmpeg.org/legal.html> and
  <https://github.com/FFmpeg/FFmpeg/tree/release/6.0>;
- ANGLE source and licensing references:
  <https://github.com/alexmercerind/flutter-windows-ANGLE-OpenGL-ES/tree/v1.0.1>
  and <https://chromium.googlesource.com/angle/angle/+/main/LICENSE>.

The archive listings do not contain a complete transitive notice bundle. The
release workflow therefore requires this notice file in both the Android APK
and Windows ZIP, while the provenance gate still requires the exact linked
library/license review to be independently closed before publication.

## Candidate-specific evidence

This file is intentionally version-independent so the package path remains
stable. The candidate SHA, package hashes, native runtime probe output and
verification date are recorded in `docs/THIRD_PARTY_PROVENANCE.md`; release
automation must verify that the packaged copy is present rather than relying
on the source checkout alone.
