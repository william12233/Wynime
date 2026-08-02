import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

void main() {
  test(
    'playback session and ad plan must describe the same episode identity',
    () {
      final episode = SourceEpisodeIdentity(
        sourceId: 'source',
        lineId: 'line',
        subjectId: 'subject',
        episodeId: 'episode',
      );
      final plan = AdRemovalPlan(
        key: AdRemovalPlanKey(
          episode: episode,
          manifestFingerprint: ManifestFingerprint(
            algorithm: 'sha256',
            value: 'fingerprint',
          ),
        ),
        mode: AdRemovalMode.safe,
      );

      final session = PlaybackSession(
        sessionId: 'session',
        episode: episode,
        mediaUri: Uri.parse('https://invalid.example/media.m3u8'),
        pageUri: Uri.parse('https://invalid.example/episode'),
        adRemovalPlan: plan,
        headers: const {'User-Agent': 'redacted-for-test'},
      );

      expect(session.adRemovalPlan, same(plan));
      expect(session.headers['User-Agent'], 'redacted-for-test');
      expect(
        () => session.headers['Authorization'] = 'secret',
        throwsUnsupportedError,
      );
    },
  );
}
