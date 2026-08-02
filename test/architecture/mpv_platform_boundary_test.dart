import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Phase 6 keeps player ownership inside Wynime', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();

    for (final forbidden in [
      'media_kit',
      'video_player',
      'flutter_vlc_player',
      'fvp:',
      'mpv_dart',
    ]) {
      expect(pubspec, isNot(contains(forbidden)));
    }
  });

  test('Windows loads only allowlisted libmpv DLLs from the executable directory', () {
    final source = File(
      'windows/runner/mpv_windows_bridge.cpp',
    ).readAsStringSync();

    expect(source, contains('ExecutableDirectory()'));
    expect(source, contains('LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR'));
    expect(source, contains('LOAD_LIBRARY_SEARCH_SYSTEM32'));
    expect(source, contains('mpv-2.dll'));
    expect(source, contains('libmpv-2.dll'));
    expect(source, isNot(contains('LoadLibraryA(')));
    expect(source, isNot(contains('LoadLibraryW(')));
    expect(source, isNot(contains('input-ipc-server')));
    expect(source, contains('{"config", "no"}'));
    expect(source, contains('{"load-scripts", "no"}'));
    expect(source, contains('{"ytdl", "no"}'));
  });

  test('Android prototype reports capability without pretending JNI exists', () {
    final source = File(
      'android/app/src/main/kotlin/io/github/william12233/wynime/'
      'MpvPrototypeBridge.kt',
    ).readAsStringSync();

    expect(source, contains('System.loadLibrary("mpv")'));
    expect(source, contains('System.loadLibrary("wynime_mpv_bridge")'));
    expect(source, contains('jni_bridge_missing'));
    expect(source, contains('android_mpv_unavailable'));
    expect(source, isNot(contains('System.load(')));
  });

  test('mpv platform channels do not enter domain or infrastructure layers', () {
    for (final root in ['lib/src/domain', 'lib/src/infrastructure']) {
      for (final file in Directory(root)
          .listSync(recursive: true)
          .whereType<File>()
          .where((entry) => entry.path.endsWith('.dart'))) {
        final source = file.readAsStringSync();
        expect(source, isNot(contains('MethodChannel')));
        expect(source, isNot(contains('EventChannel')));
        expect(source, isNot(contains('MpvPlatformTransport')));
      }
    }
  });

  test('native and Dart mpv bridges both enforce numeric loopback input', () {
    final dartSource = File(
      'lib/src/platform/playback/mpv_player_backend.dart',
    ).readAsStringSync();
    final windowsSource = File(
      'windows/runner/mpv_windows_bridge.cpp',
    ).readAsStringSync();

    expect(dartSource, contains("uri.host == '127.0.0.1'"));
    expect(dartSource, contains("uri.host == '::1'"));
    expect(windowsSource, contains('http://127.0.0.1:'));
    expect(windowsSource, contains('http://[::1]:'));
    expect(windowsSource, contains('non_loopback_uri_rejected'));
  });
}
