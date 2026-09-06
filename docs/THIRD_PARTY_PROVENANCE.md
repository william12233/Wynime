# Third-party native provenance

Status: `OPEN_RELEASE_BLOCKER`

This document records the native media artifacts observed in the local
dependency cache and in the candidate packages on 2026-09-03. It is an
engineering provenance inventory, not a legal approval or a declaration that
the release license review is complete. The release remains blocked until the
open items below are closed and independently reviewed.

## Resolved packages

The application currently resolves these relevant packages:

| Package | Version | Local package license observed |
| --- | --- | --- |
| `media_kit` | `1.2.6` | MIT |
| `media_kit_video` | `2.0.1` | MIT |
| `media_kit_libs_android_video` | `1.3.8` | MIT wrapper package |
| `media_kit_libs_windows_video` | `1.0.11` | MIT wrapper package |
| `flutter_inappwebview` | `6.2.0-beta.3` | Apache-2.0 |
| `sqlite3_flutter_libs` | `0.6.0+eol` | MIT |

The table describes the package metadata found locally. It does not by
itself establish the licenses of every native binary downloaded by those
packages.

## Android libmpv

`media_kit_libs_android_video` downloads ABI-specific JARs from the
`media-kit/libmpv-android-video-build` GitHub release `v1.1.7`. The package
build script declares these upstream MD5 values:

| ABI | Upstream MD5 | Local downloaded JAR SHA-256 |
| --- | --- | --- |
| `arm64-v8a` | `83df25b61193af8fa815e373143ac9af` | `4363DFA5D3D415B91C1F16F6FB90C3FE59A77DFD3F9B824D2B24B492D6B09DF9` |
| `armeabi-v7a` | `22e21526fefc0a2b8f17adbec9f57590` | `8EAD114FC5A43348D89DC0EB8F41823E549B15115C29F73EE26973F973620995` |
| `x86_64` | `6fa26bf0459b11f1c0b0dbc29e5b940d` | `90268CD15F0766E07FB8E427388C621161177C9EB343C544F327BD63232BB236` |
| `x86` | `0d742b756dc9d1fcd84ea271d8b68f32` | `94C13CB6188B774710E5E487AFFF6E500C4AF504DF74B2494D7B12CF9BE8A66A` |

The candidate APK contains these `libmpv.so` files:

| ABI | Size | Packaged SHA-256 |
| --- | ---: | --- |
| `arm64-v8a` | 12,369,680 bytes | `ADF83FDE58A9F6751CE6E83B9B187F651425D53EC0E20DA04DEB8DFB4AA775E1` |
| `armeabi-v7a` | 11,746,532 bytes | `8AA2B23D16D941A8D685B8EB995B1CB31677ED3E28C55C776954EB1618AB1A2E` |
| `x86_64` | 15,816,336 bytes | `F030E9A1A4D4664E89D7160A3D4528CD5875EE023FF6262E16E95AAC28FFAEC5` |

The upstream v1.1.7 dependency manifest identifies FFmpeg `6.0` and the mpv
revision `78d43740f52db817d98bcf24fb30a76ab6fa13ff`. The upstream license
file distinguishes the default/full flavor from the GPL encoder flavor. The
repository still needs to record the exact selected flavor and carry the
corresponding native notices into every distributable package.

## Windows libmpv and ANGLE

`media_kit_libs_windows_video` resolves the following archives through its
Windows CMake configuration:

| Component | Upstream artifact | Declared MD5 | Local archive SHA-256 |
| --- | --- | --- | --- |
| libmpv | `mpv-dev-x86_64-20230924-git-652a1dd.7z` | `a832ef24b3a6ff97cd2560b5b9d04cd8` | `DCE982222D7A23E4A1C6F0FB6CC39F6E899A6714624B95EA49CFF6558EE97572` |
| ANGLE | `ANGLE.7z` release `v1.0.1` | `e866f13e8d552348058afaafe869b1ed` | `CC5911BB15D596FD5A2B362613AD35B7093B427117269A7359054A65746A5F9A` |

The libmpv archive contains `libmpv-2.dll`, `libmpv.dll.a`, and headers. The
ANGLE archive contains the ANGLE/Vulkan runtime DLLs and headers. The local
archive listings did not contain a license or notice file. The current
upstream build scripts are not sufficient evidence for the historical
2023-09-24 binary, so the exact binary build provenance and license notice
set remain open.

## Package notice audit

The candidate Android APK contains Flutter's generated `NOTICES.Z` and
package notices, but the decompressed notice text did not contain a dedicated
`libmpv` or `FFmpeg` native notice section. The repository has no root
`LICENSE` or `NOTICE` file that can be used to infer a project-wide license.
Neither observation is resolved by the wrapper package licenses above.

## Required closure before publishing

- [ ] Identify the exact Android and Windows native binary flavor/build
  inputs, including the FFmpeg/mpv license combination actually shipped.
- [ ] Obtain and review the authoritative notices for every shipped native
  binary, including ANGLE and transitive codec/dependency components.
- [ ] Add the approved notice/license material to the APK and Windows bundle
  and verify it from the final package contents.
- [ ] Preserve archive and packaged-binary hashes in the final release
  evidence, with an independent review of the mapping.
- [ ] Obtain independent legal/license sign-off before a signed release is
  published.

This document does not change the `AUDIT_COMPLETE_RELEASE_BLOCKED` status in
`docs/PHASE12_STATUS.md`.

## Upstream references

- Android build release: https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7
- Android dependency manifest: https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/include/depinfo.sh
- Android license: https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/LICENSE
- Windows libmpv release: https://github.com/media-kit/libmpv-win32-video-build/releases/tag/2023-09-24
- ANGLE release: https://github.com/alexmercerind/flutter-windows-ANGLE-OpenGL-ES/releases/tag/v1.0.1
