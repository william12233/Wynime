import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_playback_route_models.dart';

enum SourcePlaybackRouteCoordinatorStatus {
  selected,
  notFound,
  noSources,
  disabled,
  consentRequired,
  incompatible,
  preferredSourceNotFound,
  failed,
}

/// An exact source-local route preference.
///
/// Package and program provenance are part of the preference so a repeated
/// source key in two installed packages cannot select the wrong source.
final class SourcePlaybackRoutePreference {
  SourcePlaybackRoutePreference({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required String sourceKey,
  }) : packageId = _packageId(packageId),
       programId = _programId(programId),
       sourceKey = _sourceKey(sourceKey);

  final String packageId;
  final Version packageVersion;
  final String programId;
  final String sourceKey;

  String get identityKey => '$packageId@$packageVersion/$programId/$sourceKey';
}

/// The bounded, immutable result of source-local route composition.
final class SourcePlaybackRouteCoordinatorResult {
  SourcePlaybackRouteCoordinatorResult({
    required this.status,
    this.route,
    required Iterable<SourcePlaybackRouteSelectionResult> selectionResults,
    this.reasonCode,
  }) : selectionResults = UnmodifiableListView(
         _boundedList(selectionResults, 'selectionResults', 32),
       ) {
    if (status == SourcePlaybackRouteCoordinatorStatus.selected &&
        route == null) {
      throw ArgumentError('A selected route result must contain a route.');
    }
    if (status != SourcePlaybackRouteCoordinatorStatus.selected &&
        route != null) {
      throw ArgumentError(
        'A non-selected route result cannot contain a route.',
      );
    }
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    if ((status == SourcePlaybackRouteCoordinatorStatus.failed ||
            status == SourcePlaybackRouteCoordinatorStatus.noSources) &&
        reasonCode == null) {
      throw ArgumentError(
        'A failed or no-sources route result requires a diagnostic code.',
      );
    }
  }

  final SourcePlaybackRouteCoordinatorStatus status;
  final SourcePlaybackRoute? route;
  final UnmodifiableListView<SourcePlaybackRouteSelectionResult>
  selectionResults;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRoute': route != null,
    'selectionCount': selectionResults.length,
    'selectedCount': selectionResults
        .where(
          (selection) =>
              selection.status == SourcePlaybackRouteSelectionStatus.selected,
        )
        .length,
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
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

String _packageId(String value) {
  final normalized = value.trim();
  if (normalized.length > 128 ||
      !RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'packageId', 'Invalid package identity.');
  }
  return normalized;
}

String _programId(String value) {
  final normalized = value.trim();
  if (normalized.length > 64 ||
      !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'programId', 'Invalid program identity.');
  }
  return normalized;
}

String _sourceKey(String value) {
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > 128 ||
      normalized.codeUnits.any(
        (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
      )) {
    throw ArgumentError.value(
      value,
      'sourceKey',
      'Must be a bounded source key without controls.',
    );
  }
  return normalized;
}
