import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_live_playback_route_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_playable_normalization_models.dart';
import '../domain/models/source_playable_source_coordinator_models.dart';
import '../domain/models/source_playback_route_coordinator_models.dart';
import '../domain/models/source_playback_route_models.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/web_capture_models.dart';
import 'source_live_playable_source_coordinator.dart';
import 'source_playback_route_coordinator.dart';

/// Selects one source-local route from an accepted live playable-source
/// aggregate.
///
/// This is the live HTTP counterpart to the fixture route coordinator. It
/// revalidates the exact plan/result alignment and delegates source choice to
/// the existing deterministic route coordinator. It performs no source I/O,
/// session resolution, player work, persistence or asynchronous lifecycle
/// management.
final class SourceLivePlaybackRouteCoordinator {
  const SourceLivePlaybackRouteCoordinator({
    required this.wynimeVersion,
    required this.routeCoordinator,
  });

  final Version wynimeVersion;
  final SourcePlaybackRouteCoordinator routeCoordinator;

  static const maxPlans = 32;

  SourceLivePlaybackRouteResult selectRoute({
    required Iterable<SourceLivePlayableSourcePlan> plans,
    required SourcePlayableSourceCoordinatorResult playableResult,
    SourcePlaybackRoutePreference? preference,
  }) {
    final planList = <SourceLivePlayableSourcePlan>[];
    try {
      for (final plan in plans) {
        if (planList.length == maxPlans) {
          return _failed('too_many_source_plans');
        }
        planList.add(plan);
      }
    } on Object {
      return _failed('invalid_source_plans');
    }

    final identities = <String>{};
    try {
      for (final plan in planList) {
        if (!identities.add(plan.identityKey)) {
          return _failed('duplicate_source_plan');
        }
      }
    } on Object {
      return _failed('invalid_source_plans');
    }

    if (playableResult.status == SourcePlayableSourceCoordinatorStatus.failed &&
        playableResult.sourceResults.isEmpty &&
        playableResult.sources.isEmpty) {
      return _failed(playableResult.reasonCode ?? 'live_playable_failed');
    }
    if (planList.isEmpty) {
      if (playableResult.sourceResults.isNotEmpty ||
          playableResult.sources.isNotEmpty) {
        return _failed('live_route_result_mismatch');
      }
      return _mapSelection(
        routeCoordinator.selectRoute(
          playableResult: playableResult,
          preference: preference,
        ),
      );
    }

    if (playableResult.sourceResults.length != planList.length) {
      return _failed('live_route_result_mismatch');
    }

    final validationError = _validateAggregate(planList, playableResult);
    if (validationError != null) {
      return _failed(validationError);
    }

    SourcePlaybackRouteCoordinatorResult selection;
    try {
      selection = routeCoordinator.selectRoute(
        playableResult: playableResult,
        preference: preference,
      );
    } on Object {
      return _failed('live_route_selection_failed');
    }

    if (selection.status != SourcePlaybackRouteCoordinatorStatus.selected) {
      return _mapSelection(selection);
    }

    final selectedRoute = selection.route!;
    final matchingPlans = planList
        .where((plan) => _matchesSelectedPlan(plan, selectedRoute))
        .toList(growable: false);
    if (matchingPlans.length != 1) {
      return _failed('live_route_plan_mismatch');
    }

    try {
      return SourceLivePlaybackRouteResult(
        status: SourceLivePlaybackRouteStatus.selected,
        route: SourceLivePlaybackRoute(
          route: selectedRoute,
          installedPackage: matchingPlans.single.requestPlan.installedPackage,
          request: matchingPlans.single.requestPlan.request,
        ),
        selectionResults: selection.selectionResults,
      );
    } on Object {
      return _failed('live_route_build_failed');
    }
  }

  String? _validateAggregate(
    List<SourceLivePlayableSourcePlan> plans,
    SourcePlayableSourceCoordinatorResult playableResult,
  ) {
    final expectedSources = <SourcePlayableSource>[];
    for (var index = 0; index < plans.length; index++) {
      final error = _validatePlanResult(
        plans[index],
        playableResult.sourceResults[index],
      );
      if (error != null) {
        return error;
      }
      expectedSources.addAll(playableResult.sourceResults[index].results);
    }
    if (!_sameSourceList(expectedSources, playableResult.sources)) {
      return 'live_route_result_mismatch';
    }
    if (_aggregateStatus(playableResult.sourceResults, expectedSources) !=
        playableResult.status) {
      return 'live_route_aggregate_invalid';
    }
    return null;
  }

  String? _validatePlanResult(
    SourceLivePlayableSourcePlan plan,
    SourcePlayableSourceNormalizationResult normalized,
  ) {
    final installed = plan.requestPlan.installedPackage;
    final package = installed.package;
    if (!_validEpisodeIdentity(plan.episode)) {
      return 'invalid_episode_identity';
    }
    if (plan.episode.sourceId != package.packageId) {
      return 'episode_source_mismatch';
    }
    try {
      package.programById(plan.requestPlan.programId);
    } on StateError {
      return 'program_not_found';
    } on Object {
      return 'package_preflight_failed';
    }
    if (!package.securityPolicy.semanticallyEquals(
          plan.requestPlan.request.securityPolicy,
        ) ||
        !package.securityPolicy.allowsUri(plan.requestPlan.request.uri)) {
      return 'live_route_request_mismatch';
    }
    if (normalized.packageId != package.packageId ||
        normalized.packageVersion != package.version ||
        normalized.programId != plan.requestPlan.programId) {
      return 'live_route_identity_mismatch';
    }

    final expectedBlockedStatus = _blockedStatus(installed, wynimeVersion);
    if (expectedBlockedStatus != null) {
      return normalized.status == expectedBlockedStatus &&
              normalized.results.isEmpty
          ? null
          : 'live_route_result_mismatch';
    }
    if (normalized.status !=
        SourcePlayableSourceNormalizationStatus.available) {
      return normalized.results.isEmpty &&
              (normalized.status ==
                      SourcePlayableSourceNormalizationStatus.notFound ||
                  normalized.status ==
                      SourcePlayableSourceNormalizationStatus.failed)
          ? null
          : 'normalization_result_invalid';
    }
    if (normalized.results.isEmpty) {
      return 'normalization_result_invalid';
    }

    final sourceKeys = <String>{};
    for (final source in normalized.results) {
      if (source.episode != plan.episode ||
          source.episode.sourceId != package.packageId) {
        return 'candidate_identity_mismatch';
      }
      if (!_isSupportedCandidate(source.kind)) {
        return 'unsupported_candidate_kind';
      }
      if (!package.securityPolicy.allowsUri(source.mediaUri) ||
          !package.securityPolicy.allowsUri(source.pageUri)) {
        return 'candidate_uri_not_allowed';
      }
      if (!sourceKeys.add(source.sourceKey)) {
        return 'duplicate_source_key';
      }
    }
    return null;
  }

  bool _matchesSelectedPlan(
    SourceLivePlayableSourcePlan plan,
    SourcePlaybackRoute route,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return package.packageId == route.packageId &&
        package.version == route.packageVersion &&
        plan.requestPlan.programId == route.programId &&
        plan.episode == route.episode;
  }

  SourceLivePlaybackRouteResult _mapSelection(
    SourcePlaybackRouteCoordinatorResult selection,
  ) {
    final status = switch (selection.status) {
      SourcePlaybackRouteCoordinatorStatus.selected =>
        SourceLivePlaybackRouteStatus.selected,
      SourcePlaybackRouteCoordinatorStatus.notFound =>
        SourceLivePlaybackRouteStatus.notFound,
      SourcePlaybackRouteCoordinatorStatus.noSources =>
        SourceLivePlaybackRouteStatus.noSources,
      SourcePlaybackRouteCoordinatorStatus.disabled =>
        SourceLivePlaybackRouteStatus.disabled,
      SourcePlaybackRouteCoordinatorStatus.consentRequired =>
        SourceLivePlaybackRouteStatus.consentRequired,
      SourcePlaybackRouteCoordinatorStatus.incompatible =>
        SourceLivePlaybackRouteStatus.incompatible,
      SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound =>
        SourceLivePlaybackRouteStatus.preferredSourceNotFound,
      SourcePlaybackRouteCoordinatorStatus.failed =>
        SourceLivePlaybackRouteStatus.failed,
    };
    return SourceLivePlaybackRouteResult(
      status: status,
      selectionResults: selection.selectionResults,
      reasonCode: selection.reasonCode ?? _reasonFor(status),
    );
  }

  static SourcePlayableSourceNormalizationStatus? _blockedStatus(
    InstalledSourcePackage installed,
    Version wynimeVersion,
  ) {
    if (installed.requiresConsent || installed.requiresReconsent) {
      return SourcePlayableSourceNormalizationStatus.consentRequired;
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return SourcePlayableSourceNormalizationStatus.disabled;
    }
    if (!installed.package.isCompatibleWith(wynimeVersion)) {
      return SourcePlayableSourceNormalizationStatus.incompatible;
    }
    return null;
  }

  SourcePlayableSourceCoordinatorStatus _aggregateStatus(
    Iterable<SourcePlayableSourceNormalizationResult> sourceResults,
    Iterable<SourcePlayableSource> sources,
  ) {
    final resultList = sourceResults.toList(growable: false);
    final sourceList = sources.toList(growable: false);
    final hasSources = sourceList.isNotEmpty;
    final hasFailed = resultList.any(
      (source) =>
          source.status == SourcePlayableSourceNormalizationStatus.failed,
    );
    final hasBlocked = resultList.any(
      (source) => switch (source.status) {
        SourcePlayableSourceNormalizationStatus.disabled ||
        SourcePlayableSourceNormalizationStatus.consentRequired ||
        SourcePlayableSourceNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = resultList.any(
      (source) =>
          source.status == SourcePlayableSourceNormalizationStatus.available ||
          source.status == SourcePlayableSourceNormalizationStatus.notFound,
    );

    return switch ((hasSources, hasFailed, hasBlocked)) {
      (true, false, false) => SourcePlayableSourceCoordinatorStatus.available,
      (true, _, _) => SourcePlayableSourceCoordinatorStatus.partial,
      (false, true, _) => SourcePlayableSourceCoordinatorStatus.failed,
      (false, false, true) when !hasEligibleSource =>
        SourcePlayableSourceCoordinatorStatus.noSources,
      (false, false, _) => SourcePlayableSourceCoordinatorStatus.notFound,
    };
  }

  static bool _sameSourceList(
    Iterable<SourcePlayableSource> left,
    Iterable<SourcePlayableSource> right,
  ) {
    final leftList = left.toList(growable: false);
    final rightList = right.toList(growable: false);
    if (leftList.length != rightList.length) {
      return false;
    }
    for (var index = 0; index < leftList.length; index++) {
      final a = leftList[index];
      final b = rightList[index];
      if (a.episode != b.episode ||
          a.sourceKey != b.sourceKey ||
          a.label != b.label ||
          a.kind != b.kind ||
          a.mediaUri != b.mediaUri ||
          a.pageUri != b.pageUri) {
        return false;
      }
    }
    return true;
  }

  static bool _isSupportedCandidate(WebCandidateKind kind) => switch (kind) {
    WebCandidateKind.hls ||
    WebCandidateKind.video ||
    WebCandidateKind.audio => true,
    WebCandidateKind.dash || WebCandidateKind.mediaSegment => false,
  };

  static bool _validEpisodeIdentity(SourceEpisodeIdentity episode) {
    return [
      episode.sourceId,
      episode.lineId,
      episode.subjectId,
      episode.episodeId,
    ].every(
      (value) =>
          value == value.trim() &&
          value.isNotEmpty &&
          value.length <= 128 &&
          !value.codeUnits.any(
            (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
          ),
    );
  }

  static String _reasonFor(SourceLivePlaybackRouteStatus status) =>
      switch (status) {
        SourceLivePlaybackRouteStatus.selected => 'route_selected',
        SourceLivePlaybackRouteStatus.notFound => 'live_route_not_found',
        SourceLivePlaybackRouteStatus.noSources => 'no_enabled_sources',
        SourceLivePlaybackRouteStatus.disabled => 'package_disabled',
        SourceLivePlaybackRouteStatus.consentRequired => 'consent_required',
        SourceLivePlaybackRouteStatus.incompatible =>
          'incompatible_wynime_version',
        SourceLivePlaybackRouteStatus.preferredSourceNotFound =>
          'preferred_source_not_found',
        SourceLivePlaybackRouteStatus.failed => 'live_route_failed',
      };

  SourceLivePlaybackRouteResult _failed(String reasonCode) =>
      SourceLivePlaybackRouteResult(
        status: SourceLivePlaybackRouteStatus.failed,
        reasonCode: _safeReason(reasonCode),
      );

  static String _safeReason(String value) =>
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value)
      ? value
      : 'live_route_failed';
}
