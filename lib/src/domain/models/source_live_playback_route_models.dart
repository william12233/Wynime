import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_http_models.dart';
import 'source_identity.dart';
import 'source_package_manager_models.dart';
import 'source_playable_normalization_models.dart';
import 'source_playback_route_models.dart';

enum SourceLivePlaybackRouteStatus {
  selected,
  notFound,
  noSources,
  disabled,
  consentRequired,
  incompatible,
  preferredSourceNotFound,
  failed,
}

/// A live HTTP route that retains the exact package admission and GET request
/// used to produce its normalized source candidate.
///
/// The request is retained as bounded in-memory provenance for the next live
/// handoff. It contains no cookies or authorization headers, and this model
/// does not retain the live response body.
final class SourceLivePlaybackRoute {
  SourceLivePlaybackRoute({
    required this.route,
    required this.installedPackage,
    required this.request,
  }) {
    final package = installedPackage.package;
    if (route.packageId != package.packageId ||
        route.packageVersion != package.version ||
        route.source.episode.sourceId != package.packageId ||
        !package.securityPolicy.semanticallyEquals(request.securityPolicy) ||
        !package.securityPolicy.allowsUri(request.uri)) {
      throw ArgumentError(
        'A live playback route must retain matching package and request policy.',
      );
    }
  }

  final SourcePlaybackRoute route;
  final InstalledSourcePackage installedPackage;
  final SourceHttpRequest request;

  String get packageId => route.packageId;
  Version get packageVersion => route.packageVersion;
  String get programId => route.programId;
  SourcePlayableSource get source => route.source;
  SourceEpisodeIdentity get episode => route.episode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageIdPresent': packageId.isNotEmpty,
    'packageVersion': packageVersion.toString(),
    'programId': programId,
    'kind': source.kind.name,
    'packageStatus': installedPackage.status.name,
    'requiresConsent':
        installedPackage.requiresConsent || installedPackage.requiresReconsent,
    'request': request.toRedactedDiagnostic(),
    'episode': {
      'sourceIdPresent': episode.sourceId.isNotEmpty,
      'lineIdLength': episode.lineId.length,
      'subjectIdLength': episode.subjectId.length,
      'episodeIdLength': episode.episodeId.length,
    },
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

/// The bounded result of selecting one live HTTP source-local route.
final class SourceLivePlaybackRouteResult {
  SourceLivePlaybackRouteResult({
    required this.status,
    this.route,
    Iterable<SourcePlaybackRouteSelectionResult> selectionResults = const [],
    this.reasonCode,
  }) : selectionResults = UnmodifiableListView(
         _boundedList(selectionResults, 'selectionResults', 32),
       ) {
    final isSelected = status == SourceLivePlaybackRouteStatus.selected;
    if (isSelected && route == null) {
      throw ArgumentError('A selected live route result must contain a route.');
    }
    if (!isSelected && route != null) {
      throw ArgumentError(
        'A non-selected live route result cannot contain a route.',
      );
    }
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    if (!isSelected && reasonCode == null) {
      throw ArgumentError(
        'A non-selected live route result requires a diagnostic code.',
      );
    }
  }

  final SourceLivePlaybackRouteStatus status;
  final SourceLivePlaybackRoute? route;
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
