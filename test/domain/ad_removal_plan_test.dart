import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

void main() {
  test(
    'ad plan identity includes source, line, subject, episode, and manifest',
    () {
      final episode = SourceEpisodeIdentity(
        sourceId: 'source-a',
        lineId: 'line-1',
        subjectId: 'subject-9',
        episodeId: 'episode-3',
      );

      final first = AdRemovalPlanKey(
        episode: episode,
        manifestFingerprint: ManifestFingerprint(
          algorithm: 'sha256',
          value: 'manifest-a',
        ),
      );
      final second = AdRemovalPlanKey(
        episode: episode,
        manifestFingerprint: ManifestFingerprint(
          algorithm: 'sha256',
          value: 'manifest-b',
        ),
      );

      expect(first, isNot(second));
      expect(first.episode.sourceId, 'source-a');
      expect(first.episode.lineId, 'line-1');
      expect(first.episode.subjectId, 'subject-9');
      expect(first.episode.episodeId, 'episode-3');
    },
  );
}
