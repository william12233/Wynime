import 'dart:collection';

import 'source_package_manager_models.dart';
import 'source_rule_program.dart';
import 'source_models.dart';
import 'source_search_normalization_models.dart';

enum SourceSearchCoordinatorStatus {
  available,
  partial,
  notFound,
  noSources,
  failed,
}

/// One explicit fixture execution plan for a source-package search program.
///
/// The plan deliberately carries the field mapping and fixture instead of
/// inferring either from a package ID or provider-specific code. A future
/// transport boundary can replace the fixture without changing the
/// coordinator's aggregation contract.
final class SourceSearchPlan {
  SourceSearchPlan({
    required this.installedPackage,
    required String programId,
    required this.fixture,
    required this.mapping,
  }) : programId = _programId(programId);

  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceFixture fixture;
  final SourceSearchFieldMapping mapping;

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

/// The bounded, immutable result of one multi-source search orchestration.
final class SourceSearchCoordinatorResult {
  SourceSearchCoordinatorResult({
    required String query,
    required this.status,
    required Iterable<SourceSearchNormalizationResult> sourceResults,
    required Iterable<SourceSearchResult> results,
    this.reasonCode,
  }) : query = _query(query),
       sourceResults = UnmodifiableListView(
         _boundedList(sourceResults, 'sourceResults', 32),
       ),
       results = UnmodifiableListView(_boundedList(results, 'results', 32000)) {
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    final hasResults = this.results.isNotEmpty;
    if ((status == SourceSearchCoordinatorStatus.available ||
            status == SourceSearchCoordinatorStatus.partial) &&
        !hasResults) {
      throw ArgumentError(
        'A result with available sources must contain normalized results.',
      );
    }
    if ((status == SourceSearchCoordinatorStatus.notFound ||
            status == SourceSearchCoordinatorStatus.noSources ||
            status == SourceSearchCoordinatorStatus.failed) &&
        hasResults) {
      throw ArgumentError(
        'A non-available result must not contain normalized results.',
      );
    }
    if (status == SourceSearchCoordinatorStatus.failed && reasonCode == null) {
      throw ArgumentError('A failed search requires a diagnostic code.');
    }
    if (status == SourceSearchCoordinatorStatus.noSources &&
        reasonCode == null) {
      throw ArgumentError('A no-sources result requires a diagnostic code.');
    }
  }

  final String query;
  final SourceSearchCoordinatorStatus status;
  final UnmodifiableListView<SourceSearchNormalizationResult> sourceResults;
  final UnmodifiableListView<SourceSearchResult> results;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'queryLength': query.length,
    'sourceCount': sourceResults.length,
    'resultCount': results.length,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static String _query(String value) {
    final normalized = value.trim();
    if (normalized.length > 128 || _hasControlCharacter(normalized)) {
      throw ArgumentError.value(
        value,
        'query',
        'Must contain at most 128 characters without control characters.',
      );
    }
    return normalized;
  }

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

  static bool _hasControlCharacter(String value) {
    return value.codeUnits.any(
      (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
    );
  }

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value);
}
