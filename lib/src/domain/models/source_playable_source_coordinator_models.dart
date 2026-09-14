import 'dart:collection';

import 'source_identity.dart';
import 'source_package_manager_models.dart';
import 'source_playable_normalization_models.dart';
import 'source_rule_program.dart';

enum SourcePlayableSourceCoordinatorStatus {
  available,
  partial,
  notFound,
  noSources,
  failed,
}

/// One explicit fixture execution plan for a source's playable candidates.
///
/// The caller supplies the episode identity and field mapping. The
/// coordinator never derives either from a provider or package identifier.
final class SourcePlayableSourcePlan {
  SourcePlayableSourcePlan({
    required this.installedPackage,
    required String programId,
    required this.fixture,
    required this.episode,
    required this.mapping,
  }) : programId = _programId(programId);

  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceFixture fixture;
  final SourceEpisodeIdentity episode;
  final SourcePlayableSourceFieldMapping mapping;

  String get identityKey =>
      '${installedPackage.package.packageId}@${installedPackage.package.version}/'
      '$programId/${_identityPart(episode.sourceId)}/'
      '${_identityPart(episode.lineId)}/${_identityPart(episode.subjectId)}/'
      '${_identityPart(episode.episodeId)}';

  static String _programId(String value) {
    final normalized = value.trim();
    if (!RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'programId',
        'Must be a lower-case source program identifier.',
      );
    }
    return normalized;
  }

  static String _identityPart(String value) => '${value.length}:$value';
}

/// The bounded, immutable result of one multi-source playable-source
/// orchestration.
final class SourcePlayableSourceCoordinatorResult {
  SourcePlayableSourceCoordinatorResult({
    required this.status,
    required Iterable<SourcePlayableSourceNormalizationResult> sourceResults,
    required Iterable<SourcePlayableSource> sources,
    this.reasonCode,
  }) : sourceResults = UnmodifiableListView(
         _boundedList(sourceResults, 'sourceResults', 32),
       ),
       sources = UnmodifiableListView(_boundedList(sources, 'sources', 32000)) {
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    final hasSources = this.sources.isNotEmpty;
    if ((status == SourcePlayableSourceCoordinatorStatus.available ||
            status == SourcePlayableSourceCoordinatorStatus.partial) &&
        !hasSources) {
      throw ArgumentError(
        'An available playable-source result must contain normalized sources.',
      );
    }
    if ((status == SourcePlayableSourceCoordinatorStatus.notFound ||
            status == SourcePlayableSourceCoordinatorStatus.noSources ||
            status == SourcePlayableSourceCoordinatorStatus.failed) &&
        hasSources) {
      throw ArgumentError(
        'A non-available result must not contain normalized sources.',
      );
    }
    if (status == SourcePlayableSourceCoordinatorStatus.failed &&
        reasonCode == null) {
      throw ArgumentError('A failed playable-source result requires a code.');
    }
    if (status == SourcePlayableSourceCoordinatorStatus.noSources &&
        reasonCode == null) {
      throw ArgumentError(
        'A no-sources playable-source result requires a code.',
      );
    }
  }

  final SourcePlayableSourceCoordinatorStatus status;
  final UnmodifiableListView<SourcePlayableSourceNormalizationResult>
  sourceResults;
  final UnmodifiableListView<SourcePlayableSource> sources;
  final String? reasonCode;

  /// Alias for callers that use the domain term instead of the short result
  /// collection name.
  UnmodifiableListView<SourcePlayableSource> get playableSources => sources;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'sourceCount': sourceResults.length,
    'playableSourceCount': sources.length,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static List<T> _boundedList<T>(Iterable<T> values, String name, int maximum) {
    final result = <T>[];
    final iterator = values.iterator;
    while (iterator.moveNext()) {
      if (result.length == maximum) {
        throw ArgumentError.value(
          values,
          name,
          'Must contain at most $maximum items.',
        );
      }
      result.add(iterator.current);
    }
    return result;
  }

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value);
}
