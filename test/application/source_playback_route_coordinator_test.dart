import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/source_playback_route_selector.dart';

void main() {
  final coordinator = SourcePlaybackRouteCoordinator(
    selector: const DeterministicSourcePlaybackRouteSelector(),
  );

  test('selects the first source-local route in deterministic order', () {
    final result = coordinator.selectRoute(
      playableResult: _playableResult([
        _normalized('first.source', [
          _source('primary', 'First', packageId: 'first.source'),
        ]),
        _normalized('second.source', [
          _source('primary', 'Second', packageId: 'second.source'),
        ]),
      ]),
    );

    expect(result.status, SourcePlaybackRouteCoordinatorStatus.selected);
    expect(result.route!.packageId, 'first.source');
    expect(result.route!.source.label, 'First');
    expect(result.route!.episode.sourceId, 'first.source');
    expect(result.selectionResults.map((item) => item.status), [
      SourcePlaybackRouteSelectionStatus.selected,
      SourcePlaybackRouteSelectionStatus.selected,
    ]);
    expect(result.toString(), isNot(contains('first.source')));
    expect(result.toString(), isNot(contains('video')));
  });

  test('honors an exact package preference without implicit fallback', () {
    final playable = _playableResult([
      _normalized('first.source', [
        _source('primary', 'First', packageId: 'first.source'),
      ]),
      _normalized('second.source', [
        _source('primary', 'Second', packageId: 'second.source'),
        _source('backup', 'Backup', packageId: 'second.source'),
      ]),
    ]);
    final preference = SourcePlaybackRoutePreference(
      packageId: 'second.source',
      packageVersion: Version.parse('1.0.0'),
      programId: 'playback',
      sourceKey: 'backup',
    );

    final selected = coordinator.selectRoute(
      playableResult: playable,
      preference: preference,
    );
    expect(selected.status, SourcePlaybackRouteCoordinatorStatus.selected);
    expect(selected.route!.packageId, 'second.source');
    expect(selected.route!.source.sourceKey, 'backup');

    final missing = coordinator.selectRoute(
      playableResult: playable,
      preference: SourcePlaybackRoutePreference(
        packageId: 'second.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'playback',
        sourceKey: 'missing',
      ),
    );
    expect(
      missing.status,
      SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
    );
    expect(missing.route, isNull);
    expect(missing.reasonCode, 'preferred_source_not_found');
    expect(missing.selectionResults, hasLength(1));

    for (final mismatchedPreference in [
      SourcePlaybackRoutePreference(
        packageId: 'second.source',
        packageVersion: Version.parse('2.0.0'),
        programId: 'playback',
        sourceKey: 'backup',
      ),
      SourcePlaybackRoutePreference(
        packageId: 'second.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'other_program',
        sourceKey: 'backup',
      ),
      SourcePlaybackRoutePreference(
        packageId: 'other.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'playback',
        sourceKey: 'primary',
      ),
    ]) {
      final noCrossPackageFallback = coordinator.selectRoute(
        playableResult: playable,
        preference: mismatchedPreference,
      );
      expect(
        noCrossPackageFallback.status,
        SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
      );
      expect(noCrossPackageFallback.route, isNull);
    }
  });

  test('does not synthesize one episode identity across packages', () {
    final result = coordinator.selectRoute(
      playableResult: _playableResult([
        _normalized('first.source', [
          _source('primary', 'First', packageId: 'first.source'),
        ]),
        _normalized('second.source', [
          _source('primary', 'Second', packageId: 'second.source'),
        ]),
      ]),
    );

    expect(result.route!.episode.sourceId, result.route!.packageId);
    expect(result.route!.episode.sourceId, 'first.source');
    expect(result.route!.episode.lineId, 'line-1');
  });

  test('reports staged-consent and disabled source status without a route', () {
    final result = coordinator.selectRoute(
      playableResult: SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.noSources,
        sourceResults: [
          _normalized(
            'consent.source',
            const [],
            status: SourcePlayableSourceNormalizationStatus.consentRequired,
          ),
          _normalized(
            'disabled.source',
            const [],
            status: SourcePlayableSourceNormalizationStatus.disabled,
          ),
        ],
        sources: const [],
        reasonCode: 'no_enabled_sources',
      ),
    );

    expect(result.status, SourcePlaybackRouteCoordinatorStatus.consentRequired);
    expect(result.reasonCode, 'route_consent_required');
    expect(result.route, isNull);
    expect(result.selectionResults, hasLength(2));
  });

  test('selects a later source while preserving an earlier source failure', () {
    final result = coordinator.selectRoute(
      playableResult: _playableResult([
        _normalized(
          'broken.source',
          const [],
          status: SourcePlayableSourceNormalizationStatus.failed,
        ),
        _normalized('working.source', [
          _source('primary', 'Working', packageId: 'working.source'),
        ]),
      ], status: SourcePlayableSourceCoordinatorStatus.partial),
    );

    expect(result.status, SourcePlaybackRouteCoordinatorStatus.selected);
    expect(result.route!.packageId, 'working.source');
    expect(
      result.selectionResults.first.status,
      SourcePlaybackRouteSelectionStatus.failed,
    );
  });

  test('maps empty, failed and not-found aggregate states truthfully', () {
    final empty = coordinator.selectRoute(
      playableResult: SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.noSources,
        sourceResults: const [],
        sources: const [],
        reasonCode: 'no_enabled_sources',
      ),
    );
    expect(empty.status, SourcePlaybackRouteCoordinatorStatus.noSources);
    expect(empty.reasonCode, 'no_enabled_sources');

    final failed = coordinator.selectRoute(
      playableResult: SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.failed,
        sourceResults: const [],
        sources: const [],
        reasonCode: 'source_playable_failed',
      ),
    );
    expect(failed.status, SourcePlaybackRouteCoordinatorStatus.failed);
    expect(failed.reasonCode, 'source_playable_failed');

    final notFound = coordinator.selectRoute(
      playableResult: _playableResult([
        _normalized('empty.source', const []),
      ], status: SourcePlayableSourceCoordinatorStatus.notFound),
    );
    expect(notFound.status, SourcePlaybackRouteCoordinatorStatus.notFound);
    expect(notFound.reasonCode, 'source_route_not_found');
  });

  test('converts selector exceptions to a safe failed result', () {
    final throwing = SourcePlaybackRouteCoordinator(
      selector: const _ThrowingSelector(),
    );
    final result = throwing.selectRoute(
      playableResult: _playableResult([
        _normalized('throwing.source', [
          _source('primary', 'Primary', packageId: 'throwing.source'),
        ]),
      ]),
    );

    expect(result.status, SourcePlaybackRouteCoordinatorStatus.failed);
    expect(result.reasonCode, 'route_selection_failed');
    expect(result.toString(), isNot(contains('raw')));
  });

  test('accepts a safe hyphenated selector failure reason', () {
    final result =
        SourcePlaybackRouteCoordinator(
          selector: const _HyphenReasonSelector(),
        ).selectRoute(
          playableResult: _playableResult([
            _normalized('hyphen.source', [
              _source('primary', 'Primary', packageId: 'hyphen.source'),
            ]),
          ]),
        );

    expect(result.status, SourcePlaybackRouteCoordinatorStatus.failed);
    expect(result.reasonCode, 'route-selection-failed');
  });

  test('keeps route result immutable and preference bounded', () {
    final result = coordinator.selectRoute(
      playableResult: _playableResult([
        _normalized('immutable.source', [
          _source('primary', 'Primary', packageId: 'immutable.source'),
        ]),
      ]),
    );
    expect(
      () => result.selectionResults.add(result.selectionResults.single),
      throwsUnsupportedError,
    );
    expect(
      () => SourcePlaybackRoutePreference(
        packageId: 'immutable.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'playback',
        sourceKey: 'bad\u0080key',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackRoutePreference(
        packageId: 'Invalid Package',
        packageVersion: Version.parse('1.0.0'),
        programId: 'playback',
        sourceKey: 'primary',
      ),
      throwsArgumentError,
    );
  });
}

SourcePlayableSourceCoordinatorResult _playableResult(
  Iterable<SourcePlayableSourceNormalizationResult> sourceResults, {
  SourcePlayableSourceCoordinatorStatus status =
      SourcePlayableSourceCoordinatorStatus.available,
}) {
  final normalized = sourceResults.toList(growable: false);
  return SourcePlayableSourceCoordinatorResult(
    status: status,
    sourceResults: normalized,
    sources: [for (final result in normalized) ...result.results],
    reasonCode: status == SourcePlayableSourceCoordinatorStatus.available
        ? null
        : status == SourcePlayableSourceCoordinatorStatus.partial
        ? 'partial_source_results'
        : 'source_playable_not_found',
  );
}

SourcePlayableSourceNormalizationResult _normalized(
  String packageId,
  Iterable<SourcePlayableSource> sources, {
  SourcePlayableSourceNormalizationStatus status =
      SourcePlayableSourceNormalizationStatus.available,
}) {
  return SourcePlayableSourceNormalizationResult(
    packageId: packageId,
    packageVersion: Version.parse('1.0.0'),
    programId: 'playback',
    status: status,
    results: sources,
    diagnostics: const [],
  );
}

SourcePlayableSource _source(
  String key,
  String label, {
  required String packageId,
  WebCandidateKind kind = WebCandidateKind.video,
}) {
  return SourcePlayableSource(
    episode: SourceEpisodeIdentity(
      sourceId: packageId,
      lineId: 'line-1',
      subjectId: 'subject-1',
      episodeId: 'episode-1',
    ),
    sourceKey: key,
    label: label,
    kind: kind,
    mediaUri: Uri.parse('https://example.com/video/$key.mp4'),
    pageUri: Uri.parse('https://example.com/watch/episode-1'),
  );
}

final class _ThrowingSelector implements SourcePlaybackRouteSelector {
  const _ThrowingSelector();

  @override
  SourcePlaybackRouteSelectionResult selectRoute({
    required SourcePlayableSourceNormalizationResult normalized,
    required SourceEpisodeIdentity episode,
    String? preferredSourceKey,
  }) {
    throw StateError('raw route detail');
  }
}

final class _HyphenReasonSelector implements SourcePlaybackRouteSelector {
  const _HyphenReasonSelector();

  @override
  SourcePlaybackRouteSelectionResult selectRoute({
    required SourcePlayableSourceNormalizationResult normalized,
    required SourceEpisodeIdentity episode,
    String? preferredSourceKey,
  }) {
    return SourcePlaybackRouteSelectionResult(
      status: SourcePlaybackRouteSelectionStatus.failed,
      reasonCode: 'route-selection-failed',
    );
  }
}
