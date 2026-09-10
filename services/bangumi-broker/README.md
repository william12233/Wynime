# Bangumi broker deployment

The Worker hostname is deployment output, not a source-code default. Keep the
same hostname in all three configuration places below. The Worker deliberately
uses separate callback paths:

- `/oauth/callback` is the callback registered with Bangumi and is handled only
  as the provider callback.
- `/oauth/app-callback` is the final verified Android App Link return path. It
  must not be registered as Bangumi's provider callback.

1. `APP_LINK_HOST` on the Worker.
2. Bangumi's OAuth provider callback registration:
   `https://<worker-host>/oauth/callback`.
3. The Flutter build define:
   `--dart-define=WYNIME_BANGUMI_BROKER_ORIGIN=https://<worker-host>`.

The client intentionally has no fallback hostname. A build without this
define shows Bangumi as not enabled and does not open a browser or perform a
DNS request. This keeps an undeployed Worker from looking like a broken login
endpoint.

Deploy after authenticating Wrangler and capture the exact `workers.dev`
hostname printed by the deployment:

```text
npm install
npx wrangler whoami
npx wrangler deploy
```

These public Worker variables are checked into `wrangler.toml` so a normal
deployment cannot accidentally clear the App Link configuration:

```text
APP_LINK_HOST=<worker-host>
ANDROID_PACKAGE_NAME=io.github.william12233.wynime
ANDROID_CERT_SHA256=<production-release-certificate-fingerprint>
```

Store `BANGUMI_CLIENT_ID`, `BANGUMI_CLIENT_SECRET` and `TICKET_KEY_B64` with
`wrangler secret put`; never commit their values. The Android certificate
fingerprint must be the certificate that signs the distributed APK, otherwise
Android App Links will not be verified.

For a production Android or Windows artifact, set the CI repository variable
`WYNIME_BANGUMI_BROKER_ORIGIN` to the exact HTTPS origin. Leaving it empty is
safe and produces a deliberately unavailable Bangumi integration.
