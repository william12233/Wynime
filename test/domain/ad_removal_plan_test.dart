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

  test('timeline maps removed gaps monotonically in both directions', () {
    final timeline = AdTimelineMap(
      originalDuration: const Duration(seconds: 18),
      sanitizedDuration: const Duration(seconds: 12),
      keptSpans: const [
        TimelineSpan(
          originalStart: Duration.zero,
          originalEnd: Duration(seconds: 6),
          sanitizedStart: Duration.zero,
          sanitizedEnd: Duration(seconds: 6),
        ),
        TimelineSpan(
          originalStart: Duration(seconds: 12),
          originalEnd: Duration(seconds: 18),
          sanitizedStart: Duration(seconds: 6),
          sanitizedEnd: Duration(seconds: 12),
        ),
      ],
    );

    expect(
      timeline.toSanitized(const Duration(seconds: 8)),
      const Duration(seconds: 6),
    );
    expect(
      timeline.toOriginal(const Duration(seconds: 6)),
      const Duration(seconds: 12),
    );
    expect(
      timeline.toSanitized(const Duration(seconds: 18)),
      const Duration(seconds: 12),
    );
    expect(
      timeline.toOriginal(const Duration(seconds: 12)),
      const Duration(seconds: 18),
    );
  });

  test(
    'plan rejects removal ranges that do not exactly complement kept spans',
    () {
      final episode = SourceEpisodeIdentity(
        sourceId: 'source-a',
        lineId: 'line-1',
        subjectId: 'subject-9',
        episodeId: 'episode-3',
      );
      final key = AdRemovalPlanKey(
        episode: episode,
        manifestFingerprint: ManifestFingerprint(
          algorithm: 'sha256',
          value: 'manifest-a',
        ),
      );

      expect(
        () => AdRemovalPlan(
          key: key,
          mode: AdRemovalMode.safe,
          removals: [
            AdRemovalDecision(
              mediaSequence: 11,
              segmentIndex: 1,
              originalStart: const Duration(seconds: 7),
              duration: const Duration(seconds: 5),
              confidence: 1,
              evidence: const [
                AdEvidence(
                  kind: AdEvidenceKind.explicitCue,
                  weight: 100,
                  code: 'fixture',
                ),
              ],
            ),
          ],
          timeline: AdTimelineMap(
            originalDuration: const Duration(seconds: 18),
            sanitizedDuration: const Duration(seconds: 13),
            keptSpans: const [
              TimelineSpan(
                originalStart: Duration.zero,
                originalEnd: Duration(seconds: 6),
                sanitizedStart: Duration.zero,
                sanitizedEnd: Duration(seconds: 6),
              ),
              TimelineSpan(
                originalStart: Duration(seconds: 11),
                originalEnd: Duration(seconds: 18),
                sanitizedStart: Duration(seconds: 6),
                sanitizedEnd: Duration(seconds: 13),
              ),
            ],
          ),
        ),
        throwsArgumentError,
      );
    },
  );
}
