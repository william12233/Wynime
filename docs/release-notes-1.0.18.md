# Wynime 1.0.18

Wynime `1.0.18+19` is the next protected exact-SHA release after `v1.0.17`.

## Distribution assets

- Android arm64-v8a APK: `wynime-1.0.18-arm64-v8a.apk`.
- Windows x64 ZIP (portable): `wynime-1.0.18.zip`.
- Each asset is published with its `.sha256` sidecar.
- The release contains no universal Android APK, AAB or Windows installer.

## Dynamic production playback repair

- WebView media requests may establish short-lived, acquisition-bound exact
  HTTPS origin grants only after all resolved addresses pass the public-network
  policy. Grants are in-memory, expire with the acquisition and are not shared
  across episodes.
- Captured method, safe request headers, page identity and origin-scoped cookie
  metadata now travel through the single `PlaybackSession`, bounded probe and
  loopback proxy instead of depending on a fixed CDN hostname or port.
- Extensionless dynamic media candidates are classified and ranked from
  response content type, request destination, Accept and Range evidence while
  contradictory image, font, CSS and script traffic remains rejected.
- The static-to-rendered fallback now runs when normalization yields no usable
  candidates, and rendered fallback capture includes media requests.
- Media3's native rendered-first-frame signal is exposed to Dart and guarded
  by a bounded player timeout.
- Xifan source package 1.2.4 raises the rendered-document ceiling from 256 KiB
  to 1 MiB for the current public subject page and requires fresh consent.
- The Android App Link release gate now accepts both current and legacy
  `apksigner` signer-line formats without weakening the one-signer or exact
  certificate checks.

## Validation boundary

- `flutter test --no-pub`: 829 tests passed.
- `flutter analyze --fatal-infos --no-pub`: passed with no issues.
- Android arm64 and Windows x64 current-head builds passed before release
  preparation; protected exact-SHA CI, signing, provenance, App Link and asset
  publication gates run again for the immutable tag.
- Production `assetlinks.json` returned direct HTTP 200 JSON with `no-store`;
  its package and release certificate matched the signed candidate.
- Real Android E2E remains unverified; `HARDWARE_VALIDATION_PENDING` and
  `WINDOWS_CUA_VALIDATION_UNAVAILABLE` remain explicit. The repository owner
  will perform Bangumi authentication, dynamic acquisition, proxy, Media3
  first-frame, playback-beyond-ten-seconds, A→B→A and second-anime checks on a
  physical Android device using this release. No CAPTCHA, DRM or access-control
  bypass is included or claimed.
