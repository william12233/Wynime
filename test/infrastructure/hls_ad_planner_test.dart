import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/hls_manifest.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/infrastructure/playback/hls_ad_planner.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_fingerprinter.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_parser.dart';

void main() {
  const parser = HlsManifestParser();
  const fingerprinter = HlsManifestFingerprinter();
  const planner = HlsAdPlanner();
  final episode = SourceEpisodeIdentity(
    sourceId: 'fixture',
    lineId: 'line-a',
    subjectId: 'subject-a',
    episodeId: 'episode-1',
  );

  HlsMediaPlaylist fixture(String name) =>
      parser.parse(
            source: File('test/fixtures/hls/$name').readAsStringSync(),
            sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
          )
          as HlsMediaPlaylist;

  test('safe mode removes only explicitly cued segments', () {
    final playlist = fixture('cue_ads.m3u8');
    final plan = planner.createPlan(
      episode: episode,
      playlist: playlist,
      fingerprint: fingerprinter.fingerprint(playlist),
      mode: AdRemovalMode.safe,
    );

    expect(plan.removedMediaSequences, {102, 103});
    expect(plan.removals.every((item) => item.confidence == 1), isTrue);
    expect(plan.timeline.originalDuration, const Duration(seconds: 60));
    expect(plan.timeline.sanitizedDuration, const Duration(seconds: 40));
    expect(
      plan.timeline.toSanitized(const Duration(seconds: 25)),
      const Duration(seconds: 20),
    );
    expect(
      plan.timeline.toOriginal(const Duration(seconds: 20)),
      const Duration(seconds: 40),
    );
  });

  test('a discontinuity alone never authorizes removal in any mode', () {
    final playlist = fixture('ambiguous_discontinuity.m3u8');
    for (final mode in [
      AdRemovalMode.safe,
      AdRemovalMode.smart,
      AdRemovalMode.aggressive,
    ]) {
      final plan = planner.createPlan(
        episode: episode,
        playlist: playlist,
        fingerprint: fingerprinter.fingerprint(playlist),
        mode: mode,
      );
      expect(plan.removals, isEmpty, reason: '$mode must fail closed.');
    }
  });

  test('smart mode requires multiple independent structural signals', () {
    final playlist =
        parser.parse(
              source: _smartFixture(),
              sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
            )
            as HlsMediaPlaylist;
    final plan = planner.createPlan(
      episode: episode,
      playlist: playlist,
      fingerprint: fingerprinter.fingerprint(playlist),
      mode: AdRemovalMode.smart,
    );

    expect(plan.removedMediaSequences, {9, 10});
    expect(
      plan.removals.first.evidence.map((item) => item.kind).toSet(),
      containsAll({
        AdEvidenceKind.alternateAuthority,
        AdEvidenceKind.suspiciousPath,
        AdEvidenceKind.shortDiscontinuityRun,
      }),
    );
  });

  test('live playlists fail closed before producing a plan', () {
    final playlist =
        parser.parse(
              source: '''
#EXTM3U
#EXT-X-TARGETDURATION:5
#EXTINF:5,
segment.ts
''',
              sourceUri: Uri.parse('https://media.example/live.m3u8'),
            )
            as HlsMediaPlaylist;
    expect(
      () => planner.createPlan(
        episode: episode,
        playlist: playlist,
        fingerprint: fingerprinter.fingerprint(playlist),
        mode: AdRemovalMode.safe,
      ),
      throwsA(
        isA<HlsAdPlanningException>().having(
          (error) => error.code,
          'code',
          'live_playlist_unsupported',
        ),
      ),
    );
  });

  test('safe mode records bounded DATERANGE evidence separately', () {
    final playlist = fixture('daterange_ads.m3u8');
    final plan = planner.createPlan(
      episode: episode,
      playlist: playlist,
      fingerprint: fingerprinter.fingerprint(playlist),
      mode: AdRemovalMode.safe,
    );

    expect(plan.removedMediaSequences, {2});
    expect(
      plan.removals.single.evidence.map((item) => item.kind),
      contains(AdEvidenceKind.adDateRange),
    );
    expect(
      plan.removals.single.evidence.map((item) => item.kind),
      isNot(contains(AdEvidenceKind.explicitCue)),
    );
  });

  test('explicit evidence cannot produce an empty sanitized playlist', () {
    final playlist =
        parser.parse(
              source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-CUE-OUT:12
#EXTINF:6,
ad-1.ts
#EXTINF:6,
ad-2.ts
#EXT-X-CUE-IN
#EXT-X-ENDLIST
''',
              sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
            )
            as HlsMediaPlaylist;

    expect(
      () => planner.createPlan(
        episode: episode,
        playlist: playlist,
        fingerprint: fingerprinter.fingerprint(playlist),
        mode: AdRemovalMode.safe,
      ),
      throwsA(
        isA<HlsAdPlanningException>().having(
          (error) => error.code,
          'code',
          'all_segments_selected',
        ),
      ),
    );
  });
}

String _smartFixture() {
  final output = StringBuffer('''
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:1
#EXT-X-PLAYLIST-TYPE:VOD
''');
  for (var sequence = 1; sequence <= 8; sequence += 1) {
    output
      ..writeln('#EXTINF:10,')
      ..writeln('https://media.example/content/$sequence.ts');
  }
  output.writeln('#EXT-X-DISCONTINUITY');
  for (var sequence = 9; sequence <= 10; sequence += 1) {
    output
      ..writeln('#EXTINF:10,')
      ..writeln('https://ads.example/advertising/$sequence.ts');
  }
  output.writeln('#EXT-X-DISCONTINUITY');
  for (var sequence = 11; sequence <= 16; sequence += 1) {
    output
      ..writeln('#EXTINF:10,')
      ..writeln('https://media.example/content/$sequence.ts');
  }
  output.writeln('#EXT-X-ENDLIST');
  return output.toString();
}
