import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/hls_manifest.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/infrastructure/playback/hls_ad_planner.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_fingerprinter.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_parser.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_sanitizer.dart';

import '../helpers/playback_test_support.dart';

void main() {
  const parser = HlsManifestParser();
  const fingerprinter = HlsManifestFingerprinter();
  const planner = HlsAdPlanner();
  const sanitizer = HlsManifestSanitizer();
  final episode = SourceEpisodeIdentity(
    sourceId: 'fixture',
    lineId: 'line-a',
    subjectId: 'subject-a',
    episodeId: 'episode-1',
  );

  HlsMediaPlaylist playlistFixture() =>
      parser.parse(
            source: File('test/fixtures/hls/cue_ads.m3u8').readAsStringSync(),
            sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
          )
          as HlsMediaPlaylist;

  test(
    'removes only approved segments and preserves effective key/map context',
    () {
      final playlist = playlistFixture();
      final fingerprint = fingerprinter.fingerprint(playlist);
      final plan = planner.createPlan(
        episode: episode,
        playlist: playlist,
        fingerprint: fingerprint,
        mode: AdRemovalMode.safe,
      );
      final output = sanitizer.sanitize(
        playlist: playlist,
        plan: plan,
        actualFingerprint: fingerprint,
      );

      expect(output, isNot(contains('ads.example')));
      expect(output, isNot(contains('preroll-102')));
      expect(output, contains('content-101.m4s'));
      expect(output, contains('content-104.m4s'));
      expect(
        output,
        contains(
          '#EXT-X-MAP:URI="https://media.example/show/init.mp4?token=secret"',
        ),
      );
      expect(output, contains('IV=0x00000000000000000000000000000002'));
      expect('#EXT-X-DISCONTINUITY\n'.allMatches(output), hasLength(1));
      expect(output, contains('#EXT-X-TARGETDURATION:10'));
      expect(output, endsWith('#EXT-X-ENDLIST\n'));

      final reparsed =
          parser.parse(
                source: output,
                sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
              )
              as HlsMediaPlaylist;
      expect(reparsed.segments, hasLength(4));
      expect(reparsed.totalDuration, plan.timeline.sanitizedDuration);
    },
  );

  test('rejects plan reuse when the manifest fingerprint changes', () {
    final playlist = playlistFixture();
    final fingerprint = fingerprinter.fingerprint(playlist);
    final plan = planner.createPlan(
      episode: episode,
      playlist: playlist,
      fingerprint: fingerprint,
      mode: AdRemovalMode.safe,
    );

    expect(
      () => sanitizer.sanitize(
        playlist: playlist,
        plan: plan,
        actualFingerprint: ManifestFingerprint(
          algorithm: 'sha256',
          value: 'different',
        ),
      ),
      throwsA(
        isA<HlsSanitizationException>().having(
          (error) => error.code,
          'code',
          'fingerprint_mismatch',
        ),
      ),
    );
  });

  test('timeline mapping is monotonic across every segment boundary', () {
    final playlist = playlistFixture();
    final plan = planner.createPlan(
      episode: episode,
      playlist: playlist,
      fingerprint: fingerprinter.fingerprint(playlist),
      mode: AdRemovalMode.safe,
    );
    var previous = Duration.zero;
    for (var second = 0; second <= 60; second += 1) {
      final mapped = plan.timeline.toSanitized(Duration(seconds: second));
      expect(
        mapped >= previous,
        isTrue,
        reason: 'mapping regressed at $second seconds',
      );
      previous = mapped;
    }
  });
  test(
    'leading removals advance discontinuity sequence without a leading tag',
    () {
      const source = '''#EXTM3U
#EXT-X-VERSION:6
#EXT-X-TARGETDURATION:6
#EXT-X-MEDIA-SEQUENCE:20
#EXT-X-DISCONTINUITY-SEQUENCE:3
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-CUE-OUT:12
#EXTINF:6,
ad-1.ts
#EXTINF:6,
ad-2.ts
#EXT-X-CUE-IN
#EXT-X-DISCONTINUITY
#EXTINF:6,
content-1.ts
#EXTINF:6,
content-2.ts
#EXT-X-ENDLIST
''';
      final playlist =
          parser.parse(
                source: source,
                sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
              )
              as HlsMediaPlaylist;
      final fingerprint = fingerprinter.fingerprint(playlist);
      final plan = planner.createPlan(
        episode: testEpisode(),
        playlist: playlist,
        fingerprint: fingerprint,
        mode: AdRemovalMode.safe,
      );

      final sanitized = sanitizer.sanitize(
        playlist: playlist,
        plan: plan,
        actualFingerprint: fingerprint,
      );
      expect(sanitized, contains('#EXT-X-MEDIA-SEQUENCE:22'));
      expect(sanitized, contains('#EXT-X-DISCONTINUITY-SEQUENCE:4'));
      expect(
        sanitized.split('\n').where((line) => line == '#EXT-X-DISCONTINUITY'),
        isEmpty,
      );
    },
  );

  test(
    'rejects a valid-looking plan whose removal identity targets another segment',
    () {
      final playlist =
          parser.parse(
                source: File(
                  'test/fixtures/hls/cue_ads.m3u8',
                ).readAsStringSync(),
                sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
              )
              as HlsMediaPlaylist;
      final fingerprint = fingerprinter.fingerprint(playlist);
      final correct = planner.createPlan(
        episode: testEpisode(),
        playlist: playlist,
        fingerprint: fingerprint,
        mode: AdRemovalMode.safe,
      );
      final first = correct.removals.first;
      final forged = AdRemovalPlan(
        key: correct.key,
        mode: correct.mode,
        removals: [
          AdRemovalDecision(
            mediaSequence: playlist.segments.first.mediaSequence,
            segmentIndex: playlist.segments.first.index,
            originalStart: first.originalStart,
            duration: first.duration,
            confidence: first.confidence,
            evidence: first.evidence,
          ),
          ...correct.removals.skip(1),
        ],
        timeline: correct.timeline,
      );

      expect(
        () => sanitizer.sanitize(
          playlist: playlist,
          plan: forged,
          actualFingerprint: fingerprint,
        ),
        throwsA(
          isA<HlsSanitizationException>().having(
            (error) => error.code,
            'code',
            'removal_identity_mismatch',
          ),
        ),
      );
    },
  );

  test(
    'removes an explicitly bounded ad DATERANGE and its selected segment',
    () {
      final playlist =
          parser.parse(
                source: File(
                  'test/fixtures/hls/daterange_ads.m3u8',
                ).readAsStringSync(),
                sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
              )
              as HlsMediaPlaylist;
      final fingerprint = fingerprinter.fingerprint(playlist);
      final plan = planner.createPlan(
        episode: episode,
        playlist: playlist,
        fingerprint: fingerprint,
        mode: AdRemovalMode.safe,
      );

      final output = sanitizer.sanitize(
        playlist: playlist,
        plan: plan,
        actualFingerprint: fingerprint,
      );

      expect(output, isNot(contains('interstitial-2.ts')));
      expect(output, isNot(contains('#EXT-X-DATERANGE:ID="ad-1"')));
      expect(output, contains('content-1.ts'));
      expect(output, contains('content-3.ts'));
    },
  );
}
