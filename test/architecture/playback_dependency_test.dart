import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Media3 and Flutter channels remain isolated to the platform boundary', () {
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
  });

  test('Android pins the reviewed Media3 HLS MVP and validates loopback input', () {
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
    expect(activity, contains('uri.host == "127.0.0.1" || uri.host == "::1"'));
    expect(activity, contains('uri.userInfo == null'));
    expect(activity, contains('METHOD_CHANNEL'));
    expect(activity, contains('EVENT_CHANNEL'));
    expect(manifest, contains('android.permission.INTERNET'));
    expect(
      manifest,
      contains('android:networkSecurityConfig="@xml/network_security_config"'),
    );
    expect(manifest, contains('android:usesCleartextTraffic="false"'));
    expect(networkSecurity, contains('base-config cleartextTrafficPermitted="false"'));
    expect(networkSecurity, contains('>127.0.0.1</domain>'));
    expect(networkSecurity, contains('>::1</domain>'));
  });

  test('upstream sockets are pinned to validated DNS addresses', () {
    final upstream = File(
      'lib/src/infrastructure/playback/proxy_upstream_client.dart',
    ).readAsStringSync();

    expect(upstream, contains("..findProxy = (_) => 'DIRECT'"));
    expect(upstream, contains('..connectionFactory = _createPinnedConnection'));
    expect(upstream, contains('Socket.startConnect(address, port)'));
    expect(
      upstream,
      allOf(contains('SecureSocket.secure('), contains('host: uri.host')),
    );
  });

  test('Phase 4 contains no Phase 5 sanitizer or ad-detection implementation', () {
    final playbackFiles = Directory('lib/src')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    const forbiddenImplementations = [
      'AdDetector',
      'ManifestSanitizer',
      'TimelineRewriter',
      'FFmpeg',
      'libmpv',
    ];
    for (final file in playbackFiles) {
      final content = file.readAsStringSync();
      for (final token in forbiddenImplementations) {
        expect(
          content,
          isNot(contains(token)),
          reason: '${file.path} must not cross the Phase 4 boundary.',
        );
      }
    }
  });

  test('temporary source snapshot workflow is removed before review', () {
    expect(
      File('.github/workflows/phase4-source-snapshot.yml').existsSync(),
      isFalse,
    );
  });
}
