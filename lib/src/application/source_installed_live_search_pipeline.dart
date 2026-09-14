import 'dart:collection';

import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_search_coordinator_models.dart';
import 'source_live_operation_plan_factory.dart';
import 'source_live_search_coordinator.dart';

/// The outer status of the installed-source live-search composition.
///
/// [coordinatorResult] retains the exact status returned by the existing live
/// search coordinator. [partial] may additionally describe package-level
/// factory rejections that were kept outside that coordinator's ready-plan
/// snapshot.
enum SourceInstalledLiveSearchPipelineStatus {
  available,
  partial,
  notFound,
  noSources,
  noUsableSources,
  failed,
}

/// A bounded result for one installed-source search fan-out.
///
/// Every package remains represented by the existing TASK-053 factory result,
/// so rejected packages retain typed preflight state while ready results retain
/// the exact [SourceLiveSearchPlan] reference passed to the existing live
/// search coordinator. No query, URI, response, cookie, header or raw error is
/// retained by this wrapper's diagnostics.
final class SourceInstalledLiveSearchPipelineResult {
  SourceInstalledLiveSearchPipelineResult({
    required this.status,
    required Iterable<SourceLiveOperationPlanResult<SourceLiveSearchPlan>>
    packageResults,
    this.coordinatorResult,
    this.reasonCode,
  }) : packageResults = UnmodifiableListView(
         _boundedPackageResults(packageResults),
       ) {
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }

    final readyCount = this.packageResults
        .where(
          (result) =>
              result.status == SourceLiveOperationPlanFactoryStatus.ready,
        )
        .length;
    if (coordinatorResult == null) {
      if (status != SourceInstalledLiveSearchPipelineStatus.noUsableSources &&
          status != SourceInstalledLiveSearchPipelineStatus.failed) {
        throw ArgumentError(
          'A result without a coordinator result must be a typed rejection.',
        );
      }
      if (reasonCode == null) {
        throw ArgumentError('A typed rejection requires a reason code.');
      }
      if (status == SourceInstalledLiveSearchPipelineStatus.noUsableSources &&
          readyCount != 0) {
        throw ArgumentError(
          'No usable sources cannot retain a ready package result.',
        );
      }
      return;
    }

    if (readyCount == 0) {
      throw ArgumentError(
        'A coordinator result requires at least one ready package plan.',
      );
    }
    if (status == SourceInstalledLiveSearchPipelineStatus.noUsableSources) {
      throw ArgumentError(
        'A coordinator result cannot report no usable sources.',
      );
    }

    final hasPackageRejections = readyCount != this.packageResults.length;
    final downstreamStatus = coordinatorResult!.status;
    final statusMatches = switch (status) {
      SourceInstalledLiveSearchPipelineStatus.available =>
        downstreamStatus == SourceSearchCoordinatorStatus.available &&
            !hasPackageRejections,
      SourceInstalledLiveSearchPipelineStatus.partial =>
        (downstreamStatus == SourceSearchCoordinatorStatus.available ||
                downstreamStatus == SourceSearchCoordinatorStatus.partial ||
                downstreamStatus == SourceSearchCoordinatorStatus.notFound) &&
            (hasPackageRejections ||
                downstreamStatus == SourceSearchCoordinatorStatus.partial),
      SourceInstalledLiveSearchPipelineStatus.notFound =>
        downstreamStatus == SourceSearchCoordinatorStatus.notFound &&
            !hasPackageRejections,
      SourceInstalledLiveSearchPipelineStatus.noSources =>
        downstreamStatus == SourceSearchCoordinatorStatus.noSources,
      SourceInstalledLiveSearchPipelineStatus.failed =>
        downstreamStatus == SourceSearchCoordinatorStatus.failed,
      SourceInstalledLiveSearchPipelineStatus.noUsableSources => false,
    };
    if (!statusMatches) {
      throw ArgumentError(
        'The installed-search status must match its coordinator result.',
      );
    }
    if (status == SourceInstalledLiveSearchPipelineStatus.partial &&
        reasonCode == null) {
      throw ArgumentError('A partial installed-search result requires a code.');
    }
  }

  static const maxPackageResults = 32;

  final SourceInstalledLiveSearchPipelineStatus status;
  final UnmodifiableListView<
    SourceLiveOperationPlanResult<SourceLiveSearchPlan>
  >
  packageResults;
  final SourceSearchCoordinatorResult? coordinatorResult;
  final String? reasonCode;

  Iterable<SourceLiveSearchPlan> get readyPlans => packageResults
      .where(
        (result) => result.status == SourceLiveOperationPlanFactoryStatus.ready,
      )
      .map((result) => result.plan!)
      .toList(growable: false);

  int get readyPackageCount => packageResults
      .where(
        (result) => result.status == SourceLiveOperationPlanFactoryStatus.ready,
      )
      .length;

  int get rejectedPackageCount => packageResults.length - readyPackageCount;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'packageCount': packageResults.length,
    'readyPackageCount': readyPackageCount,
    'rejectedPackageCount': rejectedPackageCount,
    'downstreamStatus': coordinatorResult?.status.name,
    'downstreamReasonCode': coordinatorResult?.reasonCode,
    'reasonCode': reasonCode,
    'packages': packageResults
        .map((result) => result.toRedactedDiagnostic())
        .toList(growable: false),
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static List<SourceLiveOperationPlanResult<SourceLiveSearchPlan>>
  _boundedPackageResults(
    Iterable<SourceLiveOperationPlanResult<SourceLiveSearchPlan>> values,
  ) {
    final result = <SourceLiveOperationPlanResult<SourceLiveSearchPlan>>[];
    final iterator = values.iterator;
    while (iterator.moveNext()) {
      if (result.length == maxPackageResults) {
        throw ArgumentError.value(
          values,
          'packageResults',
          'Must contain at most $maxPackageResults items.',
        );
      }
      result.add(iterator.current);
    }
    return List<
      SourceLiveOperationPlanResult<SourceLiveSearchPlan>
    >.unmodifiable(result);
  }

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Composes the current installed source snapshot with TASK-053's package
/// plan factory and the existing live-search coordinator.
///
/// This class owns only the synchronous package snapshot and the typed
/// per-package factory outcomes. The live-search coordinator remains the sole
/// owner of search generation, stale-response suppression, transport/runtime
/// sequencing and close invalidation.
final class SourceInstalledLiveSearchPipeline {
  const SourceInstalledLiveSearchPipeline({
    required this.planFactory,
    required this.searchCoordinator,
  });

  static const maxPackages = 32;

  final SourceLiveOperationPlanFactory planFactory;
  final SourceLiveSearchCoordinator searchCoordinator;

  /// Searches the exact installed-package iterable in its caller order.
  ///
  /// The iterable is fully snapshotted before any factory plan can be handed
  /// to the asynchronous coordinator. A throwing, duplicate or over-bound
  /// snapshot returns before source transport is reached.
  Future<SourceInstalledLiveSearchPipelineResult> search({
    required Iterable<InstalledSourcePackage> installedPackages,
    required String query,
  }) async {
    final snapshot = _snapshot(installedPackages);
    if (snapshot.reasonCode != null) {
      return SourceInstalledLiveSearchPipelineResult(
        status: SourceInstalledLiveSearchPipelineStatus.failed,
        packageResults: const [],
        reasonCode: snapshot.reasonCode,
      );
    }

    final packageResults =
        <SourceLiveOperationPlanResult<SourceLiveSearchPlan>>[];
    final readyPlans = <SourceLiveSearchPlan>[];
    for (final installedPackage in snapshot.packages) {
      final result = _buildPlan(installedPackage, query);
      packageResults.add(result);
      if (result.status == SourceLiveOperationPlanFactoryStatus.ready) {
        readyPlans.add(result.plan!);
      }
    }

    if (readyPlans.isEmpty) {
      return SourceInstalledLiveSearchPipelineResult(
        status: SourceInstalledLiveSearchPipelineStatus.noUsableSources,
        packageResults: packageResults,
        reasonCode: 'no_usable_search_sources',
      );
    }

    final SourceSearchCoordinatorResult coordinatorResult;
    try {
      coordinatorResult = await searchCoordinator.search(
        query: query,
        plans: readyPlans,
      );
    } on Object {
      return SourceInstalledLiveSearchPipelineResult(
        status: SourceInstalledLiveSearchPipelineStatus.failed,
        packageResults: packageResults,
        reasonCode: 'live_search_failed',
      );
    }

    final hasPackageRejections = packageResults.length != readyPlans.length;
    final status = _statusFor(
      coordinatorResult.status,
      hasPackageRejections: hasPackageRejections,
    );
    final reasonCode =
        status == SourceInstalledLiveSearchPipelineStatus.partial &&
            coordinatorResult.status ==
                SourceSearchCoordinatorStatus.available &&
            hasPackageRejections
        ? 'partial_package_preflight'
        : coordinatorResult.reasonCode;
    return SourceInstalledLiveSearchPipelineResult(
      status: status,
      packageResults: packageResults,
      coordinatorResult: coordinatorResult,
      reasonCode: reasonCode,
    );
  }

  /// Delegates close and stale invalidation to the existing coordinator.
  void close() => searchCoordinator.close();

  SourceLiveOperationPlanResult<SourceLiveSearchPlan> _buildPlan(
    InstalledSourcePackage installedPackage,
    String query,
  ) {
    try {
      return planFactory.buildSearchPlan(
        installedPackage: installedPackage,
        query: query,
      );
    } on Object {
      final package = installedPackage.package;
      return SourceLiveOperationPlanResult(
        packageId: package.packageId,
        packageVersion: package.version,
        operation: SourcePackageLiveOperationKind.search,
        programId: 'operation_unavailable',
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'package_preflight_failed',
      );
    }
  }

  _InstalledPackageSnapshot _snapshot(
    Iterable<InstalledSourcePackage> installedPackages,
  ) {
    final packages = <InstalledSourcePackage>[];
    final identities = <String>{};
    try {
      for (final installedPackage in installedPackages) {
        if (packages.length == maxPackages) {
          return const _InstalledPackageSnapshot.failure(
            'too_many_installed_packages',
          );
        }
        final package = installedPackage.package;
        final identity = '${package.packageId}@${package.version}';
        if (!identities.add(identity)) {
          return const _InstalledPackageSnapshot.failure(
            'duplicate_installed_package',
          );
        }
        packages.add(installedPackage);
      }
    } on Object {
      return const _InstalledPackageSnapshot.failure(
        'invalid_installed_packages',
      );
    }
    return _InstalledPackageSnapshot.success(packages);
  }

  static SourceInstalledLiveSearchPipelineStatus _statusFor(
    SourceSearchCoordinatorStatus status, {
    required bool hasPackageRejections,
  }) {
    return switch (status) {
      SourceSearchCoordinatorStatus.available when hasPackageRejections =>
        SourceInstalledLiveSearchPipelineStatus.partial,
      SourceSearchCoordinatorStatus.available =>
        SourceInstalledLiveSearchPipelineStatus.available,
      SourceSearchCoordinatorStatus.partial =>
        SourceInstalledLiveSearchPipelineStatus.partial,
      SourceSearchCoordinatorStatus.notFound when hasPackageRejections =>
        SourceInstalledLiveSearchPipelineStatus.partial,
      SourceSearchCoordinatorStatus.notFound =>
        SourceInstalledLiveSearchPipelineStatus.notFound,
      SourceSearchCoordinatorStatus.noSources =>
        SourceInstalledLiveSearchPipelineStatus.noSources,
      SourceSearchCoordinatorStatus.failed =>
        SourceInstalledLiveSearchPipelineStatus.failed,
    };
  }
}

final class _InstalledPackageSnapshot {
  const _InstalledPackageSnapshot.success(this.packages) : reasonCode = null;

  const _InstalledPackageSnapshot.failure(this.reasonCode)
    : packages = const [];

  final List<InstalledSourcePackage> packages;
  final String? reasonCode;
}
