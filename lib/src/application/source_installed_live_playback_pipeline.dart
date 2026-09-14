import 'dart:collection';

import '../domain/models/ad_removal_plan.dart';
import '../domain/models/bangumi_episode_target.dart';
import '../domain/models/playback_session.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_playback_route_coordinator_models.dart';
import '../domain/services/playback_proxy.dart';
import 'source_installed_live_episode_pipeline.dart';
import 'source_live_operation_plan_factory.dart';
import 'source_live_playable_source_coordinator.dart';
import 'source_live_playback_pipeline.dart';

/// One exact installed-package/episode target paired with the unchanged
/// TASK-053 playable-source plan-factory result.
///
/// The wrapper retains the target so package preflight failures remain
/// inspectable, while ready results retain the exact plan object that may be
/// handed to TASK-052. It never rebuilds a playable plan or its references.
final class SourceInstalledLivePlaybackTargetResult {
  SourceInstalledLivePlaybackTargetResult({
    required this.target,
    required this.planResult,
  }) {
    final package = target.installedPackage.package;
    if (planResult.packageId != package.packageId ||
        planResult.packageVersion != package.version ||
        planResult.operation != SourcePackageLiveOperationKind.playableSource) {
      throw ArgumentError(
        'The playable-source factory result must match its target package and operation.',
      );
    }

    if (planResult.status == SourceLiveOperationPlanFactoryStatus.ready) {
      final plan = planResult.plan;
      if (plan == null ||
          !identical(
            plan.requestPlan.installedPackage,
            target.installedPackage,
          ) ||
          !identical(plan.episode, target.episode) ||
          plan.requestPlan.programId != planResult.programId ||
          !identical(
            plan.requestPlan.request.securityPolicy,
            package.securityPolicy,
          )) {
        throw ArgumentError(
          'A ready playable-source plan must preserve exact target provenance.',
        );
      }
    }
  }

  final SourceInstalledLiveEpisodeTarget target;
  final SourceLiveOperationPlanResult<SourceLivePlayableSourcePlan> planResult;

  SourceLivePlayableSourcePlan? get plan => planResult.plan;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageId': planResult.packageId,
    'packageVersion': planResult.packageVersion.toString(),
    'operation': planResult.operation.name,
    'programId': planResult.programId,
    'status': planResult.status.name,
    'hasPlan': planResult.plan != null,
    'reasonCode': planResult.reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

/// The bounded outcome of composing installed targets with TASK-052.
enum SourceInstalledLivePlaybackPipelineStatus {
  opened,
  partial,
  notOpened,
  noUsableSources,
  failed,
}

/// A bounded, immutable result for one installed-source live-playback call.
///
/// [playbackResult] is the exact result returned by the existing TASK-052
/// pipeline. A successful result exposes its exact [PlaybackSession] through
/// [session] without creating another session model. Target diagnostics never
/// retain episode identity, request URI, response body, headers, cookies,
/// tokens, proxy credentials or raw exceptions.
final class SourceInstalledLivePlaybackPipelineResult {
  SourceInstalledLivePlaybackPipelineResult({
    required this.status,
    required Iterable<SourceInstalledLivePlaybackTargetResult> targetResults,
    this.playbackResult,
    this.reasonCode,
  }) : targetResults = UnmodifiableListView(
         _boundedTargetResults(targetResults),
       ) {
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }

    final readyCount = readyTargetCount;
    if (playbackResult == null) {
      if (status != SourceInstalledLivePlaybackPipelineStatus.noUsableSources &&
          status != SourceInstalledLivePlaybackPipelineStatus.failed) {
        throw ArgumentError(
          'A result without a playback result must be a typed rejection.',
        );
      }
      if (reasonCode == null) {
        throw ArgumentError('A typed rejection requires a reason code.');
      }
      if (status == SourceInstalledLivePlaybackPipelineStatus.noUsableSources &&
          readyCount != 0) {
        throw ArgumentError(
          'No usable sources cannot retain a ready target result.',
        );
      }
      return;
    }

    if (readyCount == 0) {
      throw ArgumentError(
        'A playback result requires at least one ready playable-source plan.',
      );
    }

    final hasTargetRejections = readyCount != this.targetResults.length;
    final downstreamStatus = playbackResult!.status;
    final statusMatches = switch (status) {
      SourceInstalledLivePlaybackPipelineStatus.opened =>
        downstreamStatus == SourceLivePlaybackPipelineStatus.opened &&
            !hasTargetRejections &&
            reasonCode == null,
      SourceInstalledLivePlaybackPipelineStatus.partial =>
        downstreamStatus == SourceLivePlaybackPipelineStatus.opened &&
            hasTargetRejections &&
            reasonCode == 'partial_target_preflight',
      SourceInstalledLivePlaybackPipelineStatus.notOpened =>
        downstreamStatus == SourceLivePlaybackPipelineStatus.notOpened &&
            reasonCode == playbackResult!.reasonCode,
      SourceInstalledLivePlaybackPipelineStatus.noUsableSources => false,
      SourceInstalledLivePlaybackPipelineStatus.failed => false,
    };
    if (!statusMatches) {
      throw ArgumentError(
        'The installed-playback status must match the exact downstream result.',
      );
    }
  }

  static const maxTargets = 32;

  final SourceInstalledLivePlaybackPipelineStatus status;
  final UnmodifiableListView<SourceInstalledLivePlaybackTargetResult>
  targetResults;
  final SourceLivePlaybackPipelineResult? playbackResult;
  final String? reasonCode;

  /// The exact session returned by TASK-052, when it opened successfully.
  PlaybackSession? get session => playbackResult?.session;

  Iterable<SourceLivePlayableSourcePlan> get readyPlans => targetResults
      .where(
        (result) =>
            result.planResult.status ==
            SourceLiveOperationPlanFactoryStatus.ready,
      )
      .map((result) => result.plan!)
      .toList(growable: false);

  int get readyTargetCount => targetResults
      .where(
        (result) =>
            result.planResult.status ==
            SourceLiveOperationPlanFactoryStatus.ready,
      )
      .length;

  int get rejectedTargetCount => targetResults.length - readyTargetCount;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'targetCount': targetResults.length,
    'readyTargetCount': readyTargetCount,
    'rejectedTargetCount': rejectedTargetCount,
    'downstream': playbackResult?.toRedactedDiagnostic(),
    'reasonCode': reasonCode,
    'targets': targetResults
        .map((result) => result.toRedactedDiagnostic())
        .toList(growable: false),
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static List<SourceInstalledLivePlaybackTargetResult> _boundedTargetResults(
    Iterable<SourceInstalledLivePlaybackTargetResult> values,
  ) {
    final result = <SourceInstalledLivePlaybackTargetResult>[];
    final iterator = values.iterator;
    while (iterator.moveNext()) {
      if (result.length == maxTargets) {
        throw ArgumentError.value(
          values,
          'targetResults',
          'Must contain at most $maxTargets items.',
        );
      }
      result.add(iterator.current);
    }
    return List<SourceInstalledLivePlaybackTargetResult>.unmodifiable(result);
  }

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Composes exact installed source episode targets with the existing TASK-052
/// live playback pipeline.
///
/// Target snapshotting and factory admission are synchronous and owned here.
/// TASK-053 remains the package/security admission authority, while TASK-052
/// remains the sole playable-source, route, session-request, open, session,
/// proxy, player and asynchronous playback authority.
final class SourceInstalledLivePlaybackPipeline {
  const SourceInstalledLivePlaybackPipeline({
    required this.planFactory,
    required this.playbackPipeline,
    required this.closeDelegate,
  });

  static const maxTargets = 32;

  final SourceLiveOperationPlanFactory planFactory;
  final SourceLivePlaybackPipeline playbackPipeline;

  /// A caller-provided close delegate owned by the existing TASK-052/lower
  /// authorities. This wrapper never creates a lifecycle or close state.
  final Future<void> Function() closeDelegate;

  /// Opens playback for the exact target iterable in caller order.
  ///
  /// The iterable is fully snapshotted before any factory result can reach
  /// TASK-052. A throwing, duplicate or over-bound snapshot therefore performs
  /// no factory, source transport, resolver, proxy or player work.
  Future<SourceInstalledLivePlaybackPipelineResult> openLive({
    required Iterable<SourceInstalledLiveEpisodeTarget> targets,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    SourcePlaybackRoutePreference? preference,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) async {
    final snapshot = _snapshot(targets);
    if (snapshot.reasonCode != null) {
      return SourceInstalledLivePlaybackPipelineResult(
        status: SourceInstalledLivePlaybackPipelineStatus.failed,
        targetResults: const [],
        reasonCode: snapshot.reasonCode,
      );
    }

    final targetResults = <SourceInstalledLivePlaybackTargetResult>[];
    final readyPlans = <SourceLivePlayableSourcePlan>[];
    for (final target in snapshot.targets) {
      final planResult = _buildPlan(target);
      final targetResult = SourceInstalledLivePlaybackTargetResult(
        target: target,
        planResult: planResult,
      );
      targetResults.add(targetResult);
      if (planResult.status == SourceLiveOperationPlanFactoryStatus.ready) {
        readyPlans.add(planResult.plan!);
      }
    }

    if (readyPlans.isEmpty) {
      return SourceInstalledLivePlaybackPipelineResult(
        status: SourceInstalledLivePlaybackPipelineStatus.noUsableSources,
        targetResults: targetResults,
        reasonCode: 'no_usable_playback_sources',
      );
    }

    final SourceLivePlaybackPipelineResult playbackResult;
    try {
      playbackResult = await playbackPipeline.openLive(
        plans: readyPlans,
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: sourceEventSequence,
        proxyBudget: proxyBudget,
        preference: preference,
        addressFamily: addressFamily,
        refreshLeeway: refreshLeeway,
        maxAutomaticRefreshes: maxAutomaticRefreshes,
        episodeDuration: episodeDuration,
        bangumiEpisode: bangumiEpisode,
      );
    } on Object {
      return SourceInstalledLivePlaybackPipelineResult(
        status: SourceInstalledLivePlaybackPipelineStatus.failed,
        targetResults: targetResults,
        reasonCode: 'live_playback_failed',
      );
    }

    final hasTargetRejections = readyPlans.length != targetResults.length;
    final status =
        playbackResult.status == SourceLivePlaybackPipelineStatus.opened
        ? hasTargetRejections
              ? SourceInstalledLivePlaybackPipelineStatus.partial
              : SourceInstalledLivePlaybackPipelineStatus.opened
        : SourceInstalledLivePlaybackPipelineStatus.notOpened;
    final reasonCode =
        status == SourceInstalledLivePlaybackPipelineStatus.partial
        ? 'partial_target_preflight'
        : playbackResult.reasonCode;
    return SourceInstalledLivePlaybackPipelineResult(
      status: status,
      targetResults: targetResults,
      playbackResult: playbackResult,
      reasonCode: reasonCode,
    );
  }

  /// Delegates invalidation to the existing TASK-052/lower lifecycle owner.
  Future<void> close() => closeDelegate();

  SourceLiveOperationPlanResult<SourceLivePlayableSourcePlan> _buildPlan(
    SourceInstalledLiveEpisodeTarget target,
  ) {
    try {
      return planFactory.buildPlayableSourcePlan(
        installedPackage: target.installedPackage,
        episode: target.episode,
      );
    } on Object {
      final package = target.installedPackage.package;
      return SourceLiveOperationPlanResult(
        packageId: package.packageId,
        packageVersion: package.version,
        operation: SourcePackageLiveOperationKind.playableSource,
        programId: 'operation_unavailable',
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'package_preflight_failed',
      );
    }
  }

  _TargetSnapshot _snapshot(
    Iterable<SourceInstalledLiveEpisodeTarget> targets,
  ) {
    final snapshot = <SourceInstalledLiveEpisodeTarget>[];
    final identities = <String>{};
    try {
      for (final target in targets) {
        if (snapshot.length == maxTargets) {
          return const _TargetSnapshot.failure('too_many_episode_targets');
        }
        if (!identities.add(target.identityKey)) {
          return const _TargetSnapshot.failure('duplicate_episode_target');
        }
        snapshot.add(target);
      }
    } on Object {
      return const _TargetSnapshot.failure('invalid_episode_targets');
    }
    return _TargetSnapshot.success(snapshot);
  }
}

final class _TargetSnapshot {
  const _TargetSnapshot.success(this.targets) : reasonCode = null;

  const _TargetSnapshot.failure(this.reasonCode) : targets = const [];

  final List<SourceInstalledLiveEpisodeTarget> targets;
  final String? reasonCode;
}
