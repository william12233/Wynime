import 'dart:collection';

import 'source_episode_normalization_models.dart';
import 'source_models.dart';
import 'source_package_manager_models.dart';
import 'source_rule_program.dart';

enum SourceEpisodeCoordinatorStatus {
  available,
  partial,
  notFound,
  noSources,
  failed,
}

/// One explicit fixture execution plan for an episode-listing program.
///
/// The caller supplies the package-owned program, fixture and field mapping.
/// The coordinator never derives episode identity fields from a package ID or
/// provider name.
final class SourceEpisodePlan {
  SourceEpisodePlan({
    required this.installedPackage,
    required String programId,
    required this.fixture,
    required this.mapping,
  }) : programId = _programId(programId);

  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceFixture fixture;
  final SourceEpisodeFieldMapping mapping;

  String get identityKey =>
      '${installedPackage.package.packageId}@${installedPackage.package.version}/$programId';

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
}

/// The bounded, immutable result of one multi-source episode orchestration.
final class SourceEpisodeCoordinatorResult {
  SourceEpisodeCoordinatorResult({
    required this.status,
    required Iterable<SourceEpisodeNormalizationResult> sourceResults,
    required Iterable<SourceEpisode> episodes,
    this.reasonCode,
  }) : sourceResults = UnmodifiableListView(
         _boundedList(sourceResults, 'sourceResults', 32),
       ),
       episodes = UnmodifiableListView(
         _boundedList(episodes, 'episodes', 32000),
       ) {
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    final hasEpisodes = this.episodes.isNotEmpty;
    if ((status == SourceEpisodeCoordinatorStatus.available ||
            status == SourceEpisodeCoordinatorStatus.partial) &&
        !hasEpisodes) {
      throw ArgumentError(
        'An available episode result must contain normalized episodes.',
      );
    }
    if ((status == SourceEpisodeCoordinatorStatus.notFound ||
            status == SourceEpisodeCoordinatorStatus.noSources ||
            status == SourceEpisodeCoordinatorStatus.failed) &&
        hasEpisodes) {
      throw ArgumentError(
        'A non-available episode result must not contain normalized episodes.',
      );
    }
    if (status == SourceEpisodeCoordinatorStatus.failed && reasonCode == null) {
      throw ArgumentError(
        'A failed episode listing requires a diagnostic code.',
      );
    }
    if (status == SourceEpisodeCoordinatorStatus.noSources &&
        reasonCode == null) {
      throw ArgumentError(
        'A no-sources episode listing requires a diagnostic code.',
      );
    }
  }

  final SourceEpisodeCoordinatorStatus status;
  final UnmodifiableListView<SourceEpisodeNormalizationResult> sourceResults;
  final UnmodifiableListView<SourceEpisode> episodes;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'sourceCount': sourceResults.length,
    'episodeCount': episodes.length,
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
