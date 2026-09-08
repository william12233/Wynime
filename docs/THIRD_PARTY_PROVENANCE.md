# Third-party native provenance

Status: `CLOSED_RELEASE_PROVENANCE`

Review date: 2026-09-07. This is an engineering provenance and packaging
record for the v1.0.1 release boundary. It does not claim that Wynime's MIT
license replaces any third-party terms. No independent legal opinion was
obtained; that fact is disclosed and is not a machine release blocker under
ADR-025.

## Engineering review conclusion

The locked dependency mechanism, upstream source records, exact downloaded
archives, packaged native files, runtime identity, build configuration and
shipped notice references were checked. The selected media-kit Android
default flavor and Windows libmpv build do not use the GPL encoder flavor or
nonfree FFmpeg option. Both the Android default and pinned Windows FFmpeg
builds enable `--enable-version3`, so their shipped FFmpeg components use the
applicable LGPLv3 text. The complete text is shipped with the distributions,
and `THIRD_PARTY_SOURCE_OFFER.md` supplies a concrete
three-year corresponding-source and relink path for the statically
incorporated FFmpeg/libmpv components. The root `LICENSE` applies only to
Wynime-owned source code; third-party components remain under their upstream
licenses.

The final release workflow repeats the package-content, version, checksum and
immutable-SHA checks. Candidate-specific artifact hashes are recorded in the
external verification transcript generated for the final commit and are never
copied from an earlier candidate.

## Resolved Dart and Flutter packages

The versions below are the direct or transitive native-relevant packages
resolved by `pubspec.lock`. The package archives each contain the stated
license file.

| Package | Version | License / source |
| --- | --- | --- |
| `media_kit` | `1.2.6` | MIT; <https://github.com/media-kit/media-kit/blob/main/LICENSE> |
| `media_kit_video` | `2.0.1` | MIT; <https://github.com/media-kit/media-kit/blob/main/LICENSE> |
| `media_kit_libs_android_video` | `1.3.8` | MIT wrapper; <https://github.com/media-kit/media-kit/blob/main/libs/android/media_kit_libs_android_video/LICENSE> |
| `media_kit_libs_windows_video` | `1.0.12` from Git commit `e9abf3b9114fdb565b13a4c194d776c70e416e7d` | MIT wrapper; <https://github.com/media-kit/media-kit/tree/e9abf3b9114fdb565b13a4c194d776c70e416e7d/libs/windows/media_kit_libs_windows_video/LICENSE> |
| `flutter_inappwebview` | `6.2.0-beta.3` | Apache-2.0; <https://github.com/pichillilorenzo/flutter_inappwebview/blob/master/LICENSE> |
| `flutter_inappwebview_android` | `1.2.0-beta.3` | Apache-2.0; package LICENSE |
| `flutter_inappwebview_windows` | `0.7.0-beta.3` | Apache-2.0; package LICENSE |
| `sqlite3_flutter_libs` | `0.6.0+eol` | MIT; <https://github.com/simolus3/sqlite3.dart/blob/main/LICENSE> |

The lockfile package SHA-256 values and package paths are verified by the
Flutter dependency resolution step. No dependency was silently substituted.

## Android libmpv and FFmpeg

`media_kit_libs_android_video` `1.3.8` selects the `default` ABI JARs
from the media-kit libmpv Android build release `v1.1.7`. Its checked-in
Gradle mechanism downloads these URLs and verifies the declared MD5 values:

| ABI | Upstream MD5 | Local JAR SHA-256 |
| --- | --- | --- |
| `arm64-v8a` | `83df25b61193af8fa815e373143ac9af` | `4363DFA5D3D415B91C1F16F6FB90C3FE59A77DFD3F9B824D2B24B492D6B09DF9` |
| `armeabi-v7a` | `22e21526fefc0a2b8f17adbec9f57590` | `8EAD114FC5A43348D89DC0EB8F41823E549B15115C29F73EE26973F973620995` |
| `x86_64` | `6fa26bf0459b11f1c0b0dbc29e5b940d` | `90268CD15F0766E07FB8E427388C621161177C9EB343C544F327BD63232BB236` |
| `x86` | `0d742b756dc9d1fcd84ea271d8b68f32` | `94C13CB6188B774710E5E487AFFF6E500C4AF504DF74B2494D7B12CF9BE8A66A` |

The upstream v1.1.7 dependency record identifies FFmpeg `6.0`, mpv
revision `78d43740f52db817d98bcf24fb30a76ab6fa13ff`, libass `0.17.1`,
harfbuzz `7.2.0`, fribidi `1.0.12`, freetype `2-13-0`, mbedTLS
`3.4.0`, dav1d `1.2.0`, libxml2 `2.10.3`, libogg `1.3.5`,
libvorbis `1.3.7` and libvpx `1.13`. The default dependency branch does not
enable `ENCODERS_GPL`. Its exact default FFmpeg script is
`buildscripts/flavors/default.sh` (Git blob
`5968d5d2dc84dd4726540b846acbd26caa1984c3`, SHA-256
`d5b84c3398fc673c6210f6b0559a163d5c1e1c146b1dc52eab211c3ad0ba09ce`) and
requires `--disable-gpl --disable-nonfree --enable-version3 --enable-static
--disable-shared --enable-mbedtls`. The dependency record blob is
`481757452663bdac8162dea49e1699176411c5c7` (SHA-256
`3ac50b68e1669694f3e0b77d45a66bdae27a7bb23600389f6cfb686b924483b3`). The
complete Android and Windows `COPYING.LGPLv3` text is included in the common
Android Flutter assets and the Windows distributions and verified by the
candidate signing and release workflows. Its canonical repository SHA-256 is
`da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768`.

The candidate APK package audit maps the shipped `libmpv.so` files to the
selected JAR outputs:

| ABI | Size | Packaged SHA-256 |
| --- | ---: | --- |
| `arm64-v8a` | 12,369,680 bytes | `ADF83FDE58A9F6751CE6E83B9B187F651425D53EC0E20DA04DEB8DFB4AA775E1` |
| `armeabi-v7a` | 11,746,532 bytes | `8AA2B23D16D941A8D685B8EB995B1CB31677ED3E28C55C776954EB1618AB1A2E` |
| `x86_64` | 15,816,336 bytes | `F030E9A1A4D4664E89D7160A3D4528CD5875EE023FF6262E16E95AAC28FFAEC5` |

Authoritative references:

- source release: <https://github.com/media-kit/libmpv-android-video-build/releases/tag/v1.1.7>;
- dependency record: <https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/buildscripts/include/depinfo.sh>;
- upstream build license: <https://raw.githubusercontent.com/media-kit/libmpv-android-video-build/v1.1.7/LICENSE>;
- FFmpeg 6.0 licensing references: <https://ffmpeg.org/legal.html>.

## Windows libmpv, FFmpeg and ANGLE

`media_kit_libs_windows_video` `1.0.12` is pinned to media-kit commit
`e9abf3b9114fdb565b13a4c194d776c70e416e7d` in `pubspec.lock`. Its CMake
source pins the Windows x64 input to the 2024-10-21 media-kit build release.
The complete machine-readable identity, source blobs, patch blobs, archive
hashes and runtime probe values are checked in at
`assets/third_party/WINDOWS_LIBMPV_BUILD.lock.json` and revalidated by
`.github/scripts/verify_windows_native_provenance.ps1`:

| Component | Upstream artifact | Declared MD5 | Local archive SHA-256 |
| --- | --- | --- | --- |
| libmpv | `mpv-dev-x86_64-20241021-git-0f78584.7z` | `6ecf18e85b093c3f7edb16f3ee6603f3` | `E23701DF0ADC1FE57C8EDE3FF313513B0B80519870058C2D35FF02754284A007` |
| ANGLE | `ANGLE.7z` release `v1.0.1` | `e866f13e8d552348058afaafe869b1ed` | `CC5911BB15D596FD5A2B362613AD35B7093B427117269A7359054A65746A5F9A` |

The locked URLs are:

~~~text
https://github.com/media-kit/libmpv-win32-video-cmake/releases/download/20241021/mpv-dev-x86_64-20241021-git-0f78584.7z
https://github.com/alexmercerind/flutter-windows-ANGLE-OpenGL-ES/releases/download/v1.0.1/ANGLE.7z
~~~

The exact Windows build source identity is:

- media-kit build source commit `8ddbe5472465950b87853789f7173f2eedc5586a`,
  tree `893a7d217a793a6ada3e80e42085dd7392c7afac`;
- mpv commit `0f7858451817c5fd5ebdb74a807a7c997662c390`;
- FFmpeg commit `8d940a07d19023a98689f353e4425a14688547e9`, version
  `N-117622-g8d940a07d`;
- `packages/ffmpeg.cmake` Git blob
  `ffbcbfc34882110acf2c271bdae994570bd62c39`, with
  `--disable-gpl --disable-nonfree --enable-version3 --enable-static
  --disable-shared` and no `--enable-gpl` or `--enable-nonfree`;
- the additional patch identities are recorded in the lock and are part of
  the corresponding-source offer.

The Release `libmpv-2.dll` runtime probe returned:

~~~text
mpv_initialize rc=0
mpv-version=mpv v0.39.0-179-g0f78584518
ffmpeg-version=N-117622-g8d940a07d
~~~

Its embedded and API-reported configuration includes `-Dgpl=false`,
`-Dlibmpv=true`, `-Dprefer_static=True` and `-Degl-angle=enabled`; the exact
configuration SHA-256 is
`f940a8f30f968817e21456395d630c54f8d49304a58a7cc82a259c9699820617`.
PE dependency inspection showed no separate FFmpeg DLL; FFmpeg is statically
linked into libmpv. Because the exact Windows build policy includes
`--enable-version3`, the complete `COPYING.LGPLv3` text, its locked SHA-256
and the corresponding-source/relink offer are copied into both Windows
distributions and verified by the release workflow. The same LGPLv3 text is
the applicable Android native-media license and is verified in the signed
Android artifacts.

The stable native files in the Windows Release tree and their verified
SHA-256 values are:

| File | SHA-256 |
| --- | --- |
| `d3dcompiler_47.dll` | `5653BC7B0E2701561464EF36602FF6171C96BFFE96E4C3597359CD7ADDCBA88A` |
| `dartjni.dll` | `58E2F6E4587760E364D6840B2762093504A462F6AEB4B6AF21229C82AB2C11EF` |
| `flutter_inappwebview_windows_plugin.dll` | `76DF343359F4C17683CB618D1944182BA3ED2F60DB0B6FAC75373BC26FD6B865` |
| `flutter_windows.dll` | `890B23404E770D3A01A978E6EE8DBEEF9006C85C39A9F5D75E3F91E5BCF3BB80` |
| `libEGL.dll` | `B2590BD0692F0381FC45C20BF1C7F7F713C9EA19C7EA6BAB62EFDD1FADC4EAAC` |
| `libGLESv2.dll` | `620BB6E38D7ED6C760A0CF4A8EB6A8F64B259B96FF286551CD32CEFC6C35CA39` |
| `libmpv-2.dll` | `56F9A69200863C2DCD2FB6367427FFEDC35550D26F947D6C36EAB37BD2A65FD5` |
| `media_kit_libs_windows_video_plugin.dll` | `DC03B8D9C1503B6D0AB336EAD55DABE61DB1A4B2170BB7A697B389348544DFAE` |
| `media_kit_video_plugin.dll` | `99F3F694A131992DD31F9A4F38B13CC7DEFD0E17D8402BB257ECD1312C9CBFEC` |
| `sqlite3.dll` | `858141A2826F53E8374CB07DE2638E0F1AC944F49B897DD558FEBA5597E86D1C` |
| `vk_swiftshader.dll` | `4F33EEA716491972CB1AD123A78ACEF485F852581130D3F3A98A1981009004F2` |
| `vulkan-1.dll` | `3BE9A95DD9019AA1ACA47ADE26F5C1C7C0047F3CF6F633D586C9EC0D3B459566` |
| `WebView2Loader.dll` | `8427B1FC58EC707813E5C0A51EB5D69397BB333250A7B891BE4D3B123F1E0F1C` |
| `wynime.exe` | `B69370B0C1906A7355BEFC9629C0D428B9D9B90D743AF064ED8E06CA1EDA908E` |
| `zlib.dll` | `82D5BF175CF882AC9AFC1558B416E674606D055966BC09529076B28A498FC0E4` |

Authoritative references:

- Windows Flutter package source: <https://github.com/media-kit/media-kit/tree/e9abf3b9114fdb565b13a4c194d776c70e416e7d/libs/windows/media_kit_libs_windows_video>;
- Windows libmpv build: <https://github.com/media-kit/libmpv-win32-video-cmake/releases/tag/20241021>;
- Windows libmpv build workflow: <https://github.com/media-kit/libmpv-win32-video-cmake/actions/runs/11444402983>;
- Windows FFmpeg source: <https://github.com/FFmpeg/FFmpeg/tree/8d940a07d19023a98689f353e4425a14688547e9>;
- mpv license guidance: <https://github.com/mpv-player/mpv/blob/master/Copyright>;
- FFmpeg source/license: <https://github.com/FFmpeg/FFmpeg/tree/release/6.0>;
- ANGLE license: <https://chromium.googlesource.com/angle/angle/+/main/LICENSE>;
- SwiftShader license: <https://github.com/google/swiftshader/blob/main/LICENSE.txt>;
- Vulkan Loader license: <https://github.com/KhronosGroup/Vulkan-Loader/blob/main/LICENSE.txt>;
- SQLite public-domain notice: <https://www.sqlite.org/copyright.html>;
- WebView2 loader source/license: <https://github.com/MicrosoftEdge/WebView2/blob/main/LICENSE>;
- Flutter/Dart source/license: <https://github.com/flutter/flutter/blob/master/LICENSE>.

## Shipped notice mapping

`assets/third_party/THIRD_PARTY_NOTICES.md` contains the package, native,
upstream and system-runtime references for the exact release boundary. The
same file is verified inside the Android APK and copied into both the Windows
portable ZIP and installer. The Windows distributions also include the root
`LICENSE`, `COPYING.LGPLv3`, `THIRD_PARTY_SOURCE_OFFER.md`, README and release
notes. Android includes the complete applicable license text and the source
offer under
`assets/flutter_assets/assets/third_party/`.

The source offer is part of the shipped compliance mechanism: it identifies
the exact FFmpeg n6.0 and media-kit build inputs, the pinned dependency
records, the release archive locations and the relink procedure. It remains
valid for three years from 2026-09-07 through 2029-09-07 and provides a
repository issue fallback if an upstream HTTPS location becomes unavailable.

The engineering checks cover:

- package license files and `pubspec.lock` identity;
- upstream archive URLs, declared MD5 values and local SHA-256 values;
- APK native ABI contents and Windows PE/native file hashes;
- libmpv runtime version, FFmpeg identity, build configuration and linkage;
- required notice presence in every shipped distribution;
- absence of secrets, keystore material and password files.

The notice document and this inventory are attribution/reference material,
not a project-wide relicensing statement. No legal opinion is claimed.

## Candidate-specific evidence

The final exact candidate SHA, signed APK/AAB hashes, Windows installer/ZIP
hashes, package metadata and CI URLs are recorded in the external
operation-specific verification transcript. The transcript is regenerated
after every candidate SHA change and is not a substitute for the workflow's
immutable checkout and checksum checks.
