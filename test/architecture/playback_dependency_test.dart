import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'Media3 and Flutter channels remain isolated to the platform boundary',
    () {
      final dartFiles = Directory('lib/src')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart'));

      for (final file in dartFiles) {
        final path = file.path.replaceAll('\\', '/');
        final content = file.readAsStringSync();
        if (content.contains('package:flutter/services.dart')) {
          expect(
            path,
            startsWith('lib/src/platform/playback/'),
            reason: '$path imports Flutter channels outside Platform.',
          );
        }
        if (path.startsWith('lib/src/domain/')) {
          expect(content, isNot(contains('Media3')));
          expect(content, isNot(contains('MethodChannel')));
          expect(content, isNot(contains('HttpClient')));
        }
      }
    },
  );

  test(
    'Android pins the reviewed Media3 HLS MVP and validates loopback input',
    () {
      final gradle = File('android/app/build.gradle.kts').readAsStringSync();
      final activity = File(
        'android/app/src/main/kotlin/io/github/william12233/wynime/MainActivity.kt',
      ).readAsStringSync();
      final manifest = File(
        'android/app/src/main/AndroidManifest.xml',
      ).readAsStringSync();
      final networkSecurity = File(
        'android/app/src/main/res/xml/network_security_config.xml',
      ).readAsStringSync();

      expect(gradle, contains('val media3Version = "1.10.1"'));
      expect(gradle, contains('androidx.media3:media3-exoplayer:'));
      expect(gradle, contains('androidx.media3:media3-exoplayer-hls:'));
      expect(
        activity,
        contains('uri.host == "127.0.0.1" || uri.host == "::1"'),
      );
      expect(activity, contains('uri.userInfo == null'));
      expect(activity, contains('METHOD_CHANNEL'));
      expect(activity, contains('EVENT_CHANNEL'));
      expect(manifest, contains('android.permission.INTERNET'));
      expect(
        manifest,
        contains(
          'android:networkSecurityConfig="@xml/network_security_config"',
        ),
      );
      expect(manifest, contains('android:usesCleartextTraffic="false"'));
      expect(
        networkSecurity,
        contains('base-config cleartextTrafficPermitted="false"'),
      );
      expect(networkSecurity, contains('>127.0.0.1</domain>'));
      expect(networkSecurity, contains('>::1</domain>'));
    },
  );

  test('upstream sockets are pinned to validated DNS addresses', () {
    final upstream = File(
      'lib/src/infrastructure/playback/proxy_upstream_client.dart',
    ).readAsStringSync();

    expect(upstream, contains('..findProxy ='));
    expect(upstream, contains("=> 'DIRECT'"));
    expect(upstream, contains('..connectionFactory = _createPinnedConnection'));
    expect(upstream, contains('Socket.startConnect(address, port)'));
    expect(
      upstream,
      allOf(contains('SecureSocket.secure('), contains('host: uri.host')),
    );
  });

  test('Phase 5 HLS policy remains pure and isolated to Infrastructure', () {
    final hlsFiles = [
      File('lib/src/infrastructure/playback/hls_manifest_parser.dart'),
      File('lib/src/infrastructure/playback/hls_manifest_fingerprinter.dart'),
      File('lib/src/infrastructure/playback/hls_ad_planner.dart'),
      File('lib/src/infrastructure/playback/hls_manifest_sanitizer.dart'),
    ];

    const forbidden = [
      "import 'dart:io'",
      "import 'dart:ffi'",
      'package:flutter/',
      'MethodChannel',
      'HttpClient',
      'Socket',
      'Process.',
    ];
    for (final file in hlsFiles) {
      final content = file.readAsStringSync();
      for (final token in forbidden) {
        expect(
          content,
          isNot(contains(token)),
          reason: '${file.path} must remain deterministic policy code.',
        );
      }
    }
  });

  test('media-kit and libmpv stay inside the platform playback boundary', () {
    final dartFiles = Directory('lib/src')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    for (final file in dartFiles) {
      final path = file.path.replaceAll('\\', '/');
      final content = file.readAsStringSync();
      if (content.contains('package:media_kit/') ||
          content.contains('package:media_kit_video/')) {
        expect(
          path,
          startsWith('lib/src/platform/playback/'),
          reason: '$path imports media-kit outside Platform playback.',
        );
      }
      if (path.startsWith('lib/src/domain/') ||
          path.startsWith('lib/src/application/')) {
        expect(content, isNot(contains('package:media_kit/')));
        expect(content, isNot(contains('package:media_kit_video/')));
        expect(content, isNot(contains('VideoController')));
      }
      expect(content, isNot(contains('FFmpeg')));
      expect(content, isNot(contains('DownloadExecutor')));
      expect(content, isNot(contains('Aes128Downloader')));
    }
  });

  test('mpv receives only the loopback capability and empty headers', () {
    final backend = File(
      'lib/src/platform/playback/mpv_player_backend.dart',
    ).readAsStringSync();
    final facade = File(
      'lib/src/platform/playback/media_kit_facade.dart',
    ).readAsStringSync();
    final router = File(
      'lib/src/application/playback/playback_engine_router.dart',
    ).readAsStringSync();

    expect(backend, contains("uri.host != '127.0.0.1'"));
    expect(backend, contains("uri.host != '::1'"));
    expect(backend, contains('uri.userInfo.isNotEmpty'));
    expect(backend, contains('uri.hasQuery'));
    expect(backend, contains('uri.hasFragment'));
    expect(facade, contains('httpHeaders: const <String, String>{}'));
    expect(router, contains('_automaticFallbackCount == 0'));
    expect(router, contains('timeline_identity_mismatch'));
  });

  test('temporary Phase 6 workflows and payloads are absent before review', () {
    expect(File('.github/workflows/phase6-export.yml').existsSync(), isFalse);
    expect(File('.github/workflows/phase6-apply.yml').existsSync(), isFalse);
    expect(Directory('.phase6').existsSync(), isFalse);
  });
}
