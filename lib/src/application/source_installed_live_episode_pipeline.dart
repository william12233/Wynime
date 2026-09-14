import 'dart:collection';

import '../domain/models/source_episode_coordinator_models.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_package_manager_models.dart';
import 'source_live_episode_coordinator.dart';
import 'source_live_operation_plan_factory.dart';

/// One caller-owned exact source episode target.
///
/// The target keeps the installed package and the complete source episode
/// identity together. It is deliberately a value passed through the
/// application boundary; it does not select, merge, infer or normalize an
/// episode.
final class SourceInstalledLiveEpisodeTarget {
  const SourceInstalledLiveEpisodeTarget({
    required this.installedPackage,
    required this.episode,
  });

  final InstalledSourcePackage installedPackage;
  final SourceEpisodeIdentity episode;

  /// A collision-resistant internal identity for snapshot duplicate checks.
  ///
  /// Length-prefixing each component keeps the key deterministic even when a
  /// caller-owned identity component contains a separator character. The key
  /// never appears in diagnostics.
  String get identityKey {
    final package = installedPackage.package;
    return [
      package.packageId,
      package.version.toString(),
      episode.sourceId,
      episode.lineId,
      episode.subjectId,
      episode.episodeId,
    ].map(_lengthPrefix).join('|');
  }

  static String _lengthPrefix(String value) => '${value.length}:$value';
}

/// One exact target paired with the unchanged typed TASK-053 factory result.
///
/// Rejected factory results retain no request or URI. Ready results retain the
/// exact target, installed package, request, policy and mapping references
/// created by the existing factory; this wrapper never rebuilds a plan.
final class SourceInstalledLiveEpisodeTargetResult {
  SourceInstalledLiveEpisodeTargetResult({
    required this.target,
    required this.planResult,
  }) {
    final package = target.installedPackage.package;
    if (planResult.packageId != package.packageId ||
        planResult.packageVersion != package.version ||
        planResult.operation != SourcePackageLiveOperationKind.episode) {
      throw ArgumentError(
        'The episode factory result must match its target package and operation.',
      );
    }

    if (planResult.status == SourceLiveOperationPlanFactoryStatus.ready) {
      final plan = planResult.plan;
      if (plan == null ||
          !identical(
            plan.requestPlan.installedPackage,
            target.installedPackage,
          ) ||
          plan.requestPlan.programId != planResult.programId ||
          !identical(
            plan.requestPlan.request.securityPolicy,
            package.securityPolicy,
          )) {
        throw ArgumentError(
          'A ready episode plan must preserve exact target provenance.',
        );
      }
    }
  }

  final SourceInstalledLiveEpisodeTarget target;
  final SourceLiveOperationPlanResult<SourceLiveEpisodePlan> planResult;

  SourceLiveEpisodePlan? get plan => planResult.plan;

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

/// The outer status of the installed-source live-episode composition.
enum SourceInstalledLiveEpisodePipelineStatus {
  available,
  partial,
  notFound,
  noSources,
  noUsableSources,
  failed,
}

/// A bounded result for one installed-source live-episode fan-out.
///
/// [coordinatorResult] is the exact result from the existing live episode
/// coordinator. Package-level factory rejections remain visible in
/// [targetResults], while only exact ready plans are sent downstream. No
/// episode identity values, URI, response, header, cookie, token or raw
/// exception is retained by diagnostics.
final class SourceInstalledLiveEpisodePipelineResult {
  SourceInstalledLiveEpisodePipelineResult({
    required this.status,
    required Iterable<SourceInstalledLiveEpisodeTargetResult> targetResults,
    this.coordinatorResult,
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
    if (coordinatorResult == null) {
      if (status != SourceInstalledLiveEpisodePipelineStatus.noUsableSources &&
          status != SourceInstalledLiveEpisodePipelineStatus.failed) {
        throw ArgumentError(
          'A result without a coordinator result must be a typed rejection.',
        );
      }
      if (reasonCode == null) {
        throw ArgumentError('A typed rejection requires a reason code.');
      }
      if (status == SourceInstalledLiveEpisodePipelineStatus.noUsableSources &&
          readyCount != 0) {
        throw ArgumentError(
          'No usable sources cannot retain a ready target result.',
        );
      }
      return;
    }

    if (readyCount == 0) {
      throw ArgumentError(
        'A coordinator result requires at least one ready episode plan.',
      );
    }
    if (status == SourceInstalledLiveEpisodePipelineStatus.noUsableSources) {
      throw ArgumentError(
        'A coordinator result cannot report no usable sources.',
      );
    }

    final hasTargetRejections = readyCount != this.targetResults.length;
    final downstreamStatus = coordinatorResult!.status;
    final statusMatches = switch (status) {
      SourceInstalledLiveEpisodePipelineStatus.available =>
        downstreamStatus == SourceEpisodeCoordinatorStatus.available &&
            !hasTargetRejections,
      SourceInstalledLiveEpisodePipelineStatus.partial =>
        (downstreamStatus == SourceEpisodeCoordinatorStatus.available ||
                downstreamStatus == SourceEpisodeCoordinatorStatus.partial ||
                downstreamStatus == SourceEpisodeCoordinatorStatus.notFound) &&
            (hasTargetRejections ||
                downstreamStatus == SourceEpisodeCoordinatorStatus.partial),
      SourceInstalledLiveEpisodePipelineStatus.notFound =>
        downstreamStatus == SourceEpisodeCoordinatorStatus.notFound &&
            !hasTargetRejections,
      SourceInstalledLiveEpisodePipelineStatus.noSources =>
        downstreamStatus == SourceEpisodeCoordinatorStatus.noSources,
      SourceInstalledLiveEpisodePipelineStatus.failed =>
        downstreamStatus == SourceEpisodeCoordinatorStatus.failed,
      SourceInstalledLiveEpisodePipelineStatus.noUsableSources => false,
    };
    if (!statusMatches) {
      throw ArgumentError(
        'The installed-episode status must match its coordinator result.',
      );
    }
    if (status == SourceInstalledLiveEpisodePipelineStatus.partial &&
        reasonCode == null) {
      throw ArgumentError(
        'A partial installed-episode result requires a code.',
      );
    }
  }

  static const maxTargets = 32;

  final SourceInstalledLiveEpisodePipelineStatus status;
  final UnmodifiableListView<SourceInstalledLiveEpisodeTargetResult>
  targetResults;
  final SourceEpisodeCoordinatorResult? coordinatorResult;
  final String? reasonCode;

  Iterable<SourceInstalledLiveEpisodeTarget> get readyTargets => targetResults
      .where(
        (result) =>
            result.planResult.status ==
            SourceLiveOperationPlanFactoryStatus.ready,
      )
      .map((result) => result.target)
      .toList(growable: false);

  Iterable<SourceLiveEpisodePlan> get readyPlans => targetResults
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
    'downstreamStatus': coordinatorResult?.status.name,
    'downstreamReasonCode': coordinatorResult?.reasonCode,
    'reasonCode': reasonCode,
    'targets': targetResults
        .map((result) => result.toRedactedDiagnostic())
        .toList(growable: false),
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static List<SourceInstalledLiveEpisodeTargetResult> _boundedTargetResults(
    Iterable<SourceInstalledLiveEpisodeTargetResult> values,
  ) {
    final result = <SourceInstalledLiveEpisodeTargetResult>[];
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
    return List<SourceInstalledLiveEpisodeTargetResult>.unmodifiable(result);
  }

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Composes an exact installed-source episode-target snapshot with TASK-053's
/// package plan factory and the existing live-episode coordinator.
///
/// This class owns only synchronous target snapshotting and typed per-target
/// factory outcomes. The existing coordinator remains the sole authority for
/// generation, stale-response suppression, transport/runtime sequencing,
/// normalization and close invalidation.
final class SourceInstalledLiveEpisodePipeline {
  const SourceInstalledLiveEpisodePipeline({
    required this.planFactory,
    required this.episodeCoordinator,
  });

  static const maxTargets = 32;

  final SourceLiveOperationPlanFactory planFactory;
  final SourceLiveEpisodeCoordinator episodeCoordinator;

  /// Resolves the exact target iterable in caller order.
  ///
  /// The target iterable is completely snapshotted before any factory result
  /// can reach the asynchronous coordinator. A throwing, duplicate or
  /// over-bound snapshot therefore performs no factory, coordinator or source
  /// transport work.
  Future<SourceInstalledLiveEpisodePipelineResult> listEpisodes({
    required Iterable<SourceInstalledLiveEpisodeTarget> targets,
  }) async {
    final snapshot = _snapshot(targets);
    if (snapshot.reasonCode != null) {
      return SourceInstalledLiveEpisodePipelineResult(
        status: SourceInstalledLiveEpisodePipelineStatus.failed,
        targetResults: const [],
        reasonCode: snapshot.reasonCode,
      );
    }

    final targetResults = <SourceInstalledLiveEpisodeTargetResult>[];
    final readyPlans = <SourceLiveEpisodePlan>[];
    for (final target in snapshot.targets) {
      final planResult = _buildPlan(target);
      final targetResult = SourceInstalledLiveEpisodeTargetResult(
        target: target,
        planResult: planResult,
      );
      targetResults.add(targetResult);
      if (planResult.status == SourceLiveOperationPlanFactoryStatus.ready) {
        readyPlans.add(planResult.plan!);
      }
    }

    if (readyPlans.isEmpty) {
      return SourceInstalledLiveEpisodePipelineResult(
        status: SourceInstalledLiveEpisodePipelineStatus.noUsableSources,
        targetResults: targetResults,
        reasonCode: 'no_usable_episode_sources',
      );
    }

    final SourceEpisodeCoordinatorResult coordinatorResult;
    try {
      coordinatorResult = await episodeCoordinator.listEpisodes(
        plans: readyPlans,
      );
    } on Object {
      return SourceInstalledLiveEpisodePipelineResult(
        status: SourceInstalledLiveEpisodePipelineStatus.failed,
        targetResults: targetResults,
        reasonCode: 'live_episode_failed',
      );
    }

    final hasTargetRejections = targetResults.length != readyPlans.length;
    final status = _statusFor(
      coordinatorResult.status,
      hasTargetRejections: hasTargetRejections,
    );
    final reasonCode =
        status == SourceInstalledLiveEpisodePipelineStatus.partial &&
            hasTargetRejections &&
            coordinatorResult.status == SourceEpisodeCoordinatorStatus.available
        ? 'partial_target_preflight'
        : coordinatorResult.reasonCode;
    return SourceInstalledLiveEpisodePipelineResult(
      status: status,
      targetResults: targetResults,
      coordinatorResult: coordinatorResult,
      reasonCode: reasonCode,
    );
  }

  /// Delegates close and stale invalidation to the existing coordinator.
  void close() => episodeCoordinator.close();

  SourceLiveOperationPlanResult<SourceLiveEpisodePlan> _buildPlan(
    SourceInstalledLiveEpisodeTarget target,
  ) {
    try {
      return planFactory.buildEpisodePlan(
        installedPackage: target.installedPackage,
        episode: target.episode,
      );
    } on Object {
      final package = target.installedPackage.package;
      return SourceLiveOperationPlanResult(
        packageId: package.packageId,
        packageVersion: package.version,
        operation: SourcePackageLiveOperationKind.episode,
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

  static SourceInstalledLiveEpisodePipelineStatus _statusFor(
    SourceEpisodeCoordinatorStatus status, {
    required bool hasTargetRejections,
  }) {
    return switch (status) {
      SourceEpisodeCoordinatorStatus.available when hasTargetRejections =>
        SourceInstalledLiveEpisodePipelineStatus.partial,
      SourceEpisodeCoordinatorStatus.available =>
        SourceInstalledLiveEpisodePipelineStatus.available,
      SourceEpisodeCoordinatorStatus.partial =>
        SourceInstalledLiveEpisodePipelineStatus.partial,
      SourceEpisodeCoordinatorStatus.notFound when hasTargetRejections =>
        SourceInstalledLiveEpisodePipelineStatus.partial,
      SourceEpisodeCoordinatorStatus.notFound =>
        SourceInstalledLiveEpisodePipelineStatus.notFound,
      SourceEpisodeCoordinatorStatus.noSources =>
        SourceInstalledLiveEpisodePipelineStatus.noSources,
      SourceEpisodeCoordinatorStatus.failed =>
        SourceInstalledLiveEpisodePipelineStatus.failed,
    };
  }
}

final class _TargetSnapshot {
  const _TargetSnapshot.success(this.targets) : reasonCode = null;

  const _TargetSnapshot.failure(this.reasonCode) : targets = const [];

  final List<SourceInstalledLiveEpisodeTarget> targets;
  final String? reasonCode;
}
