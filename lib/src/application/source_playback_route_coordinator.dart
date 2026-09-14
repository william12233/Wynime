import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/services/source_playback_route_selector.dart';

/// Composes source-local normalized candidates into one deterministic route.
///
/// Each available normalization result carries its own package-owned episode
/// identity. The coordinator passes that exact identity to the existing
/// selector; it never invents a cross-provider episode identity. It does not
/// create a session or invoke a resolver/player.
final class SourcePlaybackRouteCoordinator {
  const SourcePlaybackRouteCoordinator({required this.selector});

  final SourcePlaybackRouteSelector selector;

  SourcePlaybackRouteCoordinatorResult selectRoute({
    required SourcePlayableSourceCoordinatorResult playableResult,
    SourcePlaybackRoutePreference? preference,
  }) {
    if (playableResult.status == SourcePlayableSourceCoordinatorStatus.failed &&
        playableResult.sourceResults.isEmpty) {
      return _result(
        status: SourcePlaybackRouteCoordinatorStatus.failed,
        reasonCode: 'source_playable_failed',
      );
    }
    if (playableResult.sourceResults.isEmpty) {
      return _result(
        status:
            playableResult.status ==
                SourcePlayableSourceCoordinatorStatus.noSources
            ? SourcePlaybackRouteCoordinatorStatus.noSources
            : SourcePlaybackRouteCoordinatorStatus.notFound,
        reasonCode:
            playableResult.status ==
                SourcePlayableSourceCoordinatorStatus.noSources
            ? 'no_enabled_sources'
            : 'source_route_not_found',
      );
    }

    final selections = <SourcePlaybackRouteSelectionResult>[];
    SourcePlaybackRoute? selectedRoute;
    SourcePlaybackRouteSelectionResult? firstBlocked;
    SourcePlaybackRouteSelectionResult? firstFailure;
    var hasNotFound = false;
    var considered = 0;

    for (final normalized in playableResult.sourceResults) {
      if (preference != null && !_matchesPreference(normalized, preference)) {
        continue;
      }
      considered++;
      final selection = _selectOne(normalized, preference);
      selections.add(selection);
      if (selection.status == SourcePlaybackRouteSelectionStatus.selected) {
        selectedRoute ??= selection.route;
        continue;
      }
      if (selection.status ==
          SourcePlaybackRouteSelectionStatus.preferredSourceNotFound) {
        hasNotFound = true;
        continue;
      }
      if (selection.status == SourcePlaybackRouteSelectionStatus.notFound) {
        hasNotFound = true;
        continue;
      }
      if (selection.status == SourcePlaybackRouteSelectionStatus.failed) {
        firstFailure ??= selection;
        continue;
      }
      firstBlocked ??= selection;
    }

    if (selectedRoute != null) {
      return SourcePlaybackRouteCoordinatorResult(
        status: SourcePlaybackRouteCoordinatorStatus.selected,
        route: selectedRoute,
        selectionResults: selections,
      );
    }
    if (firstFailure != null) {
      return _result(
        status: SourcePlaybackRouteCoordinatorStatus.failed,
        selectionResults: selections,
        reasonCode: firstFailure.reasonCode ?? 'route_selection_failed',
      );
    }
    if (preference != null && considered == 0) {
      return _result(
        status: SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
        selectionResults: selections,
        reasonCode: 'preferred_source_not_found',
      );
    }
    if (firstBlocked != null && !hasNotFound) {
      return _result(
        status: _coordinatorStatus(firstBlocked.status),
        selectionResults: selections,
        reasonCode: _selectionReason(firstBlocked.status),
      );
    }
    if (preference != null) {
      return _result(
        status: SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
        selectionResults: selections,
        reasonCode: 'preferred_source_not_found',
      );
    }
    return _result(
      status: SourcePlaybackRouteCoordinatorStatus.notFound,
      selectionResults: selections,
      reasonCode: 'source_route_not_found',
    );
  }

  SourcePlaybackRouteSelectionResult _selectOne(
    SourcePlayableSourceNormalizationResult normalized,
    SourcePlaybackRoutePreference? preference,
  ) {
    if (normalized.status !=
        SourcePlayableSourceNormalizationStatus.available) {
      return SourcePlaybackRouteSelectionResult(
        status: _selectionStatus(normalized.status),
        reasonCode: _selectionReason(_selectionStatus(normalized.status)),
      );
    }
    if (normalized.results.isEmpty) {
      return SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.notFound,
      );
    }
    try {
      return selector.selectRoute(
        normalized: normalized,
        episode: normalized.results.first.episode,
        preferredSourceKey: preference?.sourceKey,
      );
    } on Object {
      return SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.failed,
        reasonCode: 'route_selection_failed',
      );
    }
  }

  static bool _matchesPreference(
    SourcePlayableSourceNormalizationResult normalized,
    SourcePlaybackRoutePreference preference,
  ) {
    return normalized.packageId == preference.packageId &&
        normalized.packageVersion == preference.packageVersion &&
        normalized.programId == preference.programId;
  }

  static SourcePlaybackRouteSelectionStatus _selectionStatus(
    SourcePlayableSourceNormalizationStatus status,
  ) {
    return switch (status) {
      SourcePlayableSourceNormalizationStatus.available =>
        SourcePlaybackRouteSelectionStatus.notFound,
      SourcePlayableSourceNormalizationStatus.notFound =>
        SourcePlaybackRouteSelectionStatus.notFound,
      SourcePlayableSourceNormalizationStatus.disabled =>
        SourcePlaybackRouteSelectionStatus.disabled,
      SourcePlayableSourceNormalizationStatus.consentRequired =>
        SourcePlaybackRouteSelectionStatus.consentRequired,
      SourcePlayableSourceNormalizationStatus.incompatible =>
        SourcePlaybackRouteSelectionStatus.incompatible,
      SourcePlayableSourceNormalizationStatus.failed =>
        SourcePlaybackRouteSelectionStatus.failed,
    };
  }

  static SourcePlaybackRouteCoordinatorStatus _coordinatorStatus(
    SourcePlaybackRouteSelectionStatus status,
  ) {
    return switch (status) {
      SourcePlaybackRouteSelectionStatus.selected =>
        SourcePlaybackRouteCoordinatorStatus.selected,
      SourcePlaybackRouteSelectionStatus.notFound =>
        SourcePlaybackRouteCoordinatorStatus.notFound,
      SourcePlaybackRouteSelectionStatus.disabled =>
        SourcePlaybackRouteCoordinatorStatus.disabled,
      SourcePlaybackRouteSelectionStatus.consentRequired =>
        SourcePlaybackRouteCoordinatorStatus.consentRequired,
      SourcePlaybackRouteSelectionStatus.incompatible =>
        SourcePlaybackRouteCoordinatorStatus.incompatible,
      SourcePlaybackRouteSelectionStatus.preferredSourceNotFound =>
        SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
      SourcePlaybackRouteSelectionStatus.failed =>
        SourcePlaybackRouteCoordinatorStatus.failed,
    };
  }

  static String _selectionReason(SourcePlaybackRouteSelectionStatus status) {
    return switch (status) {
      SourcePlaybackRouteSelectionStatus.selected => 'route_selected',
      SourcePlaybackRouteSelectionStatus.notFound => 'route_not_found',
      SourcePlaybackRouteSelectionStatus.disabled => 'route_disabled',
      SourcePlaybackRouteSelectionStatus.consentRequired =>
        'route_consent_required',
      SourcePlaybackRouteSelectionStatus.incompatible => 'route_incompatible',
      SourcePlaybackRouteSelectionStatus.preferredSourceNotFound =>
        'preferred_source_not_found',
      SourcePlaybackRouteSelectionStatus.failed => 'route_selection_failed',
    };
  }

  static SourcePlaybackRouteCoordinatorResult _result({
    required SourcePlaybackRouteCoordinatorStatus status,
    Iterable<SourcePlaybackRouteSelectionResult> selectionResults = const [],
    required String reasonCode,
  }) {
    return SourcePlaybackRouteCoordinatorResult(
      status: status,
      selectionResults: selectionResults,
      reasonCode: reasonCode,
    );
  }
}
