import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

void main() {
  const selector = DeterministicSourcePlaybackRouteSelector();
  final episode = _episode();

  test('selects the first ordered source or an exact preferred source', () {
    final normalized = _normalized(
      sources: [_source('primary', 'Primary'), _source('backup', 'Backup')],
    );

    final first = selector.selectRoute(
      normalized: normalized,
      episode: episode,
    );
    expect(first.status, SourcePlaybackRouteSelectionStatus.selected);
    expect(first.route!.source.sourceKey, 'primary');
    expect(first.route!.packageId, 'example.anime');
    expect(first.route!.programId, 'playback');
    expect(first.route!.episode, episode);

    final preferred = selector.selectRoute(
      normalized: normalized,
      episode: episode,
      preferredSourceKey: ' backup ',
    );
    expect(preferred.status, SourcePlaybackRouteSelectionStatus.selected);
    expect(preferred.route!.source.sourceKey, 'backup');
  });

  test('does not silently fall back when a preferred source is absent', () {
    final result = selector.selectRoute(
      normalized: _normalized(sources: [_source('primary', 'Primary')]),
      episode: episode,
      preferredSourceKey: 'missing',
    );

    expect(
      result.status,
      SourcePlaybackRouteSelectionStatus.preferredSourceNotFound,
    );
    expect(result.route, isNull);
    expect(result.reasonCode, 'preferred_source_not_found');
  });

  test(
    'propagates unavailable and empty normalized states without a route',
    () {
      final statuses =
          <
            SourcePlayableSourceNormalizationStatus,
            SourcePlaybackRouteSelectionStatus
          >{
            SourcePlayableSourceNormalizationStatus.notFound:
                SourcePlaybackRouteSelectionStatus.notFound,
            SourcePlayableSourceNormalizationStatus.disabled:
                SourcePlaybackRouteSelectionStatus.disabled,
            SourcePlayableSourceNormalizationStatus.consentRequired:
                SourcePlaybackRouteSelectionStatus.consentRequired,
            SourcePlayableSourceNormalizationStatus.incompatible:
                SourcePlaybackRouteSelectionStatus.incompatible,
            SourcePlayableSourceNormalizationStatus.failed:
                SourcePlaybackRouteSelectionStatus.failed,
          };
      for (final entry in statuses.entries) {
        final result = selector.selectRoute(
          normalized: _normalized(status: entry.key),
          episode: episode,
        );
        expect(result.status, entry.value);
        expect(result.route, isNull);
      }

      final empty = selector.selectRoute(
        normalized: _normalized(),
        episode: episode,
      );
      expect(empty.status, SourcePlaybackRouteSelectionStatus.notFound);
    },
  );

  test(
    'rejects mixed episode identities and unsupported direct construction',
    () {
      final otherEpisode = SourceEpisodeIdentity(
        sourceId: 'example.anime',
        lineId: 'line-2',
        subjectId: 'subject-1',
        episodeId: 'episode-1',
      );
      final mixed = selector.selectRoute(
        normalized: _normalized(
          sources: [
            _source('primary', 'Primary'),
            _source('other', 'Other', episode: otherEpisode),
          ],
        ),
        episode: episode,
      );
      expect(mixed.status, SourcePlaybackRouteSelectionStatus.failed);
      expect(mixed.reasonCode, 'episode_identity_mismatch');

      final dash = selector.selectRoute(
        normalized: _normalized(
          sources: [_source('dash', 'DASH', kind: WebCandidateKind.dash)],
        ),
        episode: episode,
      );
      expect(dash.status, SourcePlaybackRouteSelectionStatus.failed);
      expect(dash.reasonCode, 'unsupported_candidate_kind');
    },
  );

  test('rejects invalid preferred keys and invalid normalized identity', () {
    final invalidKey = selector.selectRoute(
      normalized: _normalized(sources: [_source('primary', 'Primary')]),
      episode: episode,
      preferredSourceKey: 'bad\u0080key',
    );
    expect(invalidKey.status, SourcePlaybackRouteSelectionStatus.failed);
    expect(invalidKey.reasonCode, 'invalid_preferred_source_key');

    final invalidIdentity = selector.selectRoute(
      normalized: _normalized(
        packageId: 'Invalid Package',
        sources: [_source('primary', 'Primary')],
      ),
      episode: episode,
    );
    expect(invalidIdentity.status, SourcePlaybackRouteSelectionStatus.failed);
    expect(invalidIdentity.reasonCode, 'invalid_normalized_identity');
  });

  test(
    'route and result invariants reject inconsistent direct construction',
    () {
      expect(
        () => SourcePlaybackRoute(
          packageId: 'other.anime',
          packageVersion: Version.parse('1.0.0'),
          programId: 'playback',
          source: _source('primary', 'Primary'),
        ),
        throwsArgumentError,
      );
      expect(
        () => SourcePlaybackRoute(
          packageId: 'example.anime',
          packageVersion: Version.parse('1.0.0'),
          programId: 'playback',
          source: _source('dash', 'DASH', kind: WebCandidateKind.dash),
        ),
        throwsArgumentError,
      );
      final route = SourcePlaybackRoute(
        packageId: 'example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'playback',
        source: _source('primary', 'Primary'),
      );
      final diagnostic = route.toRedactedDiagnostic().toString();
      expect(diagnostic, contains('packageIdPresent: true'));
      expect(diagnostic, contains('programId: playback'));
      expect(diagnostic, isNot(contains('example.anime')));
      expect(diagnostic, isNot(contains('line-1')));
      expect(diagnostic, isNot(contains('subject-1')));
      expect(diagnostic, isNot(contains('episode-1')));
      expect(route.toString(), isNot(contains('example.anime')));
      expect(
        () => SourcePlaybackRouteSelectionResult(
          status: SourcePlaybackRouteSelectionStatus.selected,
        ),
        throwsArgumentError,
      );
      expect(
        () => SourcePlaybackRouteSelectionResult(
          status: SourcePlaybackRouteSelectionStatus.failed,
          route: SourcePlaybackRoute(
            packageId: 'example.anime',
            packageVersion: Version.parse('1.0.0'),
            programId: 'playback',
            source: SourcePlayableSource(
              episode: SourceEpisodeIdentity(
                sourceId: 'example.anime',
                lineId: 'line-1',
                subjectId: 'subject-1',
                episodeId: 'episode-1',
              ),
              sourceKey: 'primary',
              label: 'Primary',
              kind: WebCandidateKind.video,
              mediaUri: Uri.parse('https://example.com/video.mp4'),
              pageUri: Uri.parse('https://example.com/watch'),
            ),
          ),
        ),
        throwsArgumentError,
      );
    },
  );
}

SourcePlayableSourceNormalizationResult _normalized({
  String packageId = 'example.anime',
  String programId = 'playback',
  SourcePlayableSourceNormalizationStatus status =
      SourcePlayableSourceNormalizationStatus.available,
  Iterable<SourcePlayableSource> sources = const [],
}) {
  return SourcePlayableSourceNormalizationResult(
    packageId: packageId,
    packageVersion: Version.parse('1.0.0'),
    programId: programId,
    status: status,
    results: sources,
    diagnostics: const [],
  );
}

SourcePlayableSource _source(
  String key,
  String label, {
  WebCandidateKind kind = WebCandidateKind.video,
  SourceEpisodeIdentity? episode,
}) {
  return SourcePlayableSource(
    episode: episode ?? _episode(),
    sourceKey: key,
    label: label,
    kind: kind,
    mediaUri: Uri.parse('https://example.com/video/$key.mp4'),
    pageUri: Uri.parse('https://example.com/watch/episode-1'),
  );
}

SourceEpisodeIdentity _episode() => SourceEpisodeIdentity(
  sourceId: 'example.anime',
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: 'episode-1',
);
