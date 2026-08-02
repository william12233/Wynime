import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_session.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test(
    'session freezes authority data and redacts values from diagnostics',
    () {
      final session = testPlaybackSession(
        headers: const {
          'Authorization': 'Bearer secret-token',
          'X-Trace': 'trace-value',
        },
        cookies: const {'sid': 'cookie-secret'},
        referer: Uri.parse('https://media.example/episode/1?ref=allowed'),
        origin: Uri.parse('https://media.example'),
        userAgent: 'Wynime Phase4 Test Agent',
      );

      expect(session.headers['authorization'], 'Bearer secret-token');
      expect(session.cookies['sid'], 'cookie-secret');
      expect(
        () => session.headers['authorization'] = 'changed',
        throwsUnsupportedError,
      );
      expect(session.toString(), isNot(contains('secret-token')));
      expect(session.toString(), isNot(contains('cookie-secret')));
      expect(session.toString(), contains('authorization'));
      expect(session.timelineMapIdentity, contains('phase4-placeholder'));
    },
  );

  test('player handoff accepts only explicit numeric loopback endpoints', () {
    final session = testPlaybackSession();
    final proxied = session.withPlaybackUri(
      Uri.parse('http://127.0.0.1:43123/v1/session/token/resource/id'),
    );

    expect(proxied.effectivePlaybackUri.host, '127.0.0.1');
    expect(
      () => session.withPlaybackUri(
        Uri.parse('https://media.example/video/master.m3u8'),
      ),
      throwsArgumentError,
    );
    expect(
      () => session.withPlaybackUri(
        Uri.parse('http://localhost:43123/v1/session/token/resource/id'),
      ),
      throwsArgumentError,
    );
  });

  test('session rejects hop-by-hop headers and duplicate track identities', () {
    expect(
      () => testPlaybackSession(headers: const {'Connection': 'keep-alive'}),
      throwsArgumentError,
    );

    final episode = testEpisode();
    expect(
      () => PlaybackSession(
        sessionId: 'session',
        episode: episode,
        mediaUri: Uri.parse('https://media.example/master.m3u8'),
        pageUri: Uri.parse('https://media.example/episode'),
        adRemovalPlan: testAdRemovalPlan(episode),
        subtitles: [MediaTrack(id: 'track', label: 'Subtitle')],
        audioTracks: [MediaTrack(id: 'track', label: 'Audio')],
      ),
      throwsArgumentError,
    );
  });

  test(
    'refresh preserves authoritative session and episode identities',
    () async {
      late PlaybackSession original;
      original = testPlaybackSession(
        sessionId: 'stable-session',
        refresh: () async => testPlaybackSession(
          sessionId: 'stable-session',
          episode: original.episode,
          mediaUri: Uri.parse('https://media.example/video/refreshed.m3u8'),
        ),
      );

      final refreshed = await original.refreshed();
      expect(refreshed.sessionId, original.sessionId);
      expect(refreshed.episode, original.episode);
      expect(refreshed.mediaUri.path, endsWith('refreshed.m3u8'));
    },
  );

  test('refresh rejects authority replacement', () async {
    final original = testPlaybackSession(
      refresh: () async => testPlaybackSession(sessionId: 'other-session'),
    );

    await expectLater(original.refreshed(), throwsStateError);
  });
}
