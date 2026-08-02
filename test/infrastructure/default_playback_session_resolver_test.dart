import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/infrastructure/playback/default_playback_session_resolver.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test(
    'resolver creates one bounded authoritative session from a candidate',
    () async {
      final episode = testEpisode();
      final resolver = DefaultPlaybackSessionResolver(
        idGenerator: _FixedIdGenerator('fixed-session'),
      );
      final request = PlaybackSessionResolutionRequest(
        episode: episode,
        pageUri: Uri.parse('https://media.example/episode/1'),
        candidate: WebMediaCandidate(
          kind: WebCandidateKind.hls,
          uri: Uri.parse('https://cdn.media.example/video/master.m3u8'),
          headers: const {
            'Authorization': 'Bearer opaque',
            'Referer': 'https://media.example/custom-page',
            'Origin': 'https://media.example',
            'User-Agent': 'Captured Agent',
            'Cookie': 'must=not-forward-directly',
          },
          sourceEventSequence: 7,
        ),
        securityPolicy: testSourcePolicy(),
        adRemovalPlan: testAdRemovalPlan(episode),
        cookies: [
          WebCaptureCookie(
            name: 'sid',
            value: 'secret',
            domain: 'media.example',
            path: '/video',
          ),
          WebCaptureCookie(
            name: 'wrong_path',
            value: 'ignored',
            domain: 'media.example',
            path: '/vide',
          ),
          WebCaptureCookie(
            name: 'wrong_domain',
            value: 'ignored',
            domain: 'other.example',
          ),
          WebCaptureCookie(
            name: 'expired',
            value: 'ignored',
            domain: 'media.example',
            expiresAt: DateTime.utc(2000),
          ),
        ],
        expiresAt: DateTime.now().toUtc().add(const Duration(minutes: 15)),
      );

      final session = await resolver.resolve(request);

      expect(session.sessionId, 'fixed-session');
      expect(session.mediaUri.host, 'cdn.media.example');
      expect(session.headers['authorization'], 'Bearer opaque');
      expect(session.headers, isNot(contains('cookie')));
      expect(session.headers, isNot(contains('referer')));
      expect(session.headers, isNot(contains('origin')));
      expect(session.headers, isNot(contains('user-agent')));
      expect(session.cookies, {'sid': 'secret'});
      expect(session.referer.toString(), 'https://media.example/custom-page');
      expect(session.origin.toString(), 'https://media.example');
      expect(session.userAgent, 'Captured Agent');
      expect(session.episode, episode);
    },
  );

  test('explicit user agent overrides the captured user agent', () async {
    final episode = testEpisode();
    final resolver = DefaultPlaybackSessionResolver(
      idGenerator: _FixedIdGenerator('fixed-session'),
    );

    final session = await resolver.resolve(
      PlaybackSessionResolutionRequest(
        episode: episode,
        pageUri: Uri.parse('https://media.example/episode/1'),
        candidate: WebMediaCandidate(
          kind: WebCandidateKind.video,
          uri: Uri.parse('https://media.example/video/file.mp4'),
          headers: const {'user-agent': 'Captured Agent'},
          sourceEventSequence: 1,
        ),
        securityPolicy: testSourcePolicy(),
        adRemovalPlan: testAdRemovalPlan(episode),
        userAgent: 'Explicit Agent',
      ),
    );

    expect(session.userAgent, 'Explicit Agent');
  });

  test(
    'resolver fails closed for segments, DASH, and outside authorities',
    () async {
      final episode = testEpisode();
      final resolver = DefaultPlaybackSessionResolver(
        idGenerator: _FixedIdGenerator('fixed-session'),
      );

      Future<void> expectRejected(
        WebCandidateKind kind,
        Uri uri,
        String code,
      ) async {
        final future = resolver.resolve(
          PlaybackSessionResolutionRequest(
            episode: episode,
            pageUri: Uri.parse('https://media.example/episode/1'),
            candidate: WebMediaCandidate(
              kind: kind,
              uri: uri,
              headers: const {},
              sourceEventSequence: 1,
            ),
            securityPolicy: testSourcePolicy(),
            adRemovalPlan: testAdRemovalPlan(episode),
          ),
        );
        await expectLater(
          future,
          throwsA(
            isA<PlaybackSessionResolutionException>().having(
              (error) => error.code,
              'code',
              code,
            ),
          ),
        );
      }

      await expectRejected(
        WebCandidateKind.mediaSegment,
        Uri.parse('https://media.example/video/segment.ts'),
        'segment_candidate_rejected',
      );
      await expectRejected(
        WebCandidateKind.dash,
        Uri.parse('https://media.example/video/manifest.mpd'),
        'dash_deferred',
      );
      await expectRejected(
        WebCandidateKind.hls,
        Uri.parse('https://outside.example/master.m3u8'),
        'candidate_outside_allowlist',
      );
    },
  );
}

final class _FixedIdGenerator implements PlaybackSessionIdGenerator {
  _FixedIdGenerator(this.value);

  final String value;

  @override
  String nextId() => value;
}
