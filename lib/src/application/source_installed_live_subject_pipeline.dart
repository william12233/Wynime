import 'dart:collection';

import '../domain/models/source_identity.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_subject_details_models.dart';
import 'source_live_operation_plan_factory.dart';
import 'source_live_subject_coordinator.dart';

final class SourceInstalledLiveSubjectTarget {
  const SourceInstalledLiveSubjectTarget({
    required this.installedPackage,
    required this.subject,
  });

  final InstalledSourcePackage installedPackage;
  final SourceSubjectIdentity subject;

  String get identityKey {
    final package = installedPackage.package;
    return [
      package.packageId,
      package.version.toString(),
      subject.sourceId,
      subject.subjectId,
    ].map((value) => '${value.length}:$value').join('|');
  }
}

final class SourceInstalledLiveSubjectTargetResult {
  SourceInstalledLiveSubjectTargetResult({
    required this.target,
    required this.planResult,
    this.subjectResult,
  }) {
    final package = target.installedPackage.package;
    if (planResult.packageId != package.packageId ||
        planResult.packageVersion != package.version ||
        planResult.operation != SourcePackageLiveOperationKind.subjectDetails) {
      throw ArgumentError(
        'The subject factory result does not match its target.',
      );
    }
    if (planResult.status == SourceLiveOperationPlanFactoryStatus.ready &&
        (subjectResult == null ||
            planResult.plan == null ||
            !identical(
              planResult.plan!.requestPlan.installedPackage,
              target.installedPackage,
            ) ||
            planResult.plan!.requestPlan.programId != planResult.programId ||
            !identical(
              planResult.plan!.requestPlan.request.securityPolicy,
              package.securityPolicy,
            ) ||
            planResult.plan!.subject != target.subject)) {
      throw ArgumentError(
        'A ready subject plan must preserve target provenance.',
      );
    }
    if (planResult.status != SourceLiveOperationPlanFactoryStatus.ready &&
        subjectResult != null) {
      throw ArgumentError(
        'A rejected subject plan cannot have a subject result.',
      );
    }
    if (subjectResult != null &&
        (subjectResult!.packageId != package.packageId ||
            subjectResult!.packageVersion != package.version)) {
      throw ArgumentError(
        'The subject result does not match its target package.',
      );
    }
  }

  final SourceInstalledLiveSubjectTarget target;
  final SourceLiveOperationPlanResult<SourceLiveSubjectPlan> planResult;
  final SourceSubjectDetailsResult? subjectResult;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageId': planResult.packageId,
    'packageVersion': planResult.packageVersion.toString(),
    'operation': planResult.operation.name,
    'programId': planResult.programId,
    'status': planResult.status.name,
    'subjectStatus': subjectResult?.status.name,
    'reasonCode': planResult.reasonCode,
  };
}

enum SourceInstalledLiveSubjectPipelineStatus {
  available,
  partial,
  notFound,
  challengeRequired,
  noUsableSources,
  failed,
}

final class SourceInstalledLiveSubjectPipelineResult {
  SourceInstalledLiveSubjectPipelineResult({
    required this.status,
    required Iterable<SourceInstalledLiveSubjectTargetResult> targetResults,
    this.reasonCode,
  }) : targetResults = UnmodifiableListView(
         List<SourceInstalledLiveSubjectTargetResult>.unmodifiable(
           targetResults,
         ),
       ) {
    if (this.targetResults.length > 32) {
      throw ArgumentError('A subject pipeline may contain at most 32 targets.');
    }
    if (reasonCode != null &&
        !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(reasonCode!)) {
      throw ArgumentError('Invalid subject pipeline reason code.');
    }
    if ((status == SourceInstalledLiveSubjectPipelineStatus.noUsableSources ||
            status == SourceInstalledLiveSubjectPipelineStatus.partial) &&
        reasonCode == null) {
      throw ArgumentError(
        'A rejected or partial subject result requires a code.',
      );
    }
  }

  final SourceInstalledLiveSubjectPipelineStatus status;
  final UnmodifiableListView<SourceInstalledLiveSubjectTargetResult>
  targetResults;
  final String? reasonCode;

  Iterable<SourceSubjectDetailsResult> get subjectResults => targetResults
      .map((result) => result.subjectResult)
      .whereType<SourceSubjectDetailsResult>()
      .toList(growable: false);
}

final class SourceInstalledLiveSubjectPipeline {
  const SourceInstalledLiveSubjectPipeline({
    required this.planFactory,
    required this.subjectCoordinator,
  });

  static const maxTargets = 32;

  final SourceLiveOperationPlanFactory planFactory;
  final SourceLiveSubjectCoordinator subjectCoordinator;

  Future<SourceInstalledLiveSubjectPipelineResult> listSubjects({
    required Iterable<SourceInstalledLiveSubjectTarget> targets,
  }) async {
    final snapshot = <SourceInstalledLiveSubjectTarget>[];
    final identities = <String>{};
    try {
      for (final target in targets) {
        if (snapshot.length == maxTargets) {
          return _failed('too_many_subject_targets');
        }
        if (!identities.add(target.identityKey)) {
          return _failed('duplicate_subject_target');
        }
        snapshot.add(target);
      }
    } on Object {
      return _failed('invalid_subject_targets');
    }

    final results = <SourceInstalledLiveSubjectTargetResult>[];
    for (final target in snapshot) {
      final planResult = _buildPlan(target);
      if (planResult.status != SourceLiveOperationPlanFactoryStatus.ready) {
        results.add(
          SourceInstalledLiveSubjectTargetResult(
            target: target,
            planResult: planResult,
          ),
        );
        continue;
      }
      final subjectResult = await subjectCoordinator.resolve(
        plan: planResult.plan!,
      );
      results.add(
        SourceInstalledLiveSubjectTargetResult(
          target: target,
          planResult: planResult,
          subjectResult: subjectResult,
        ),
      );
    }

    final status = _aggregateStatus(results);
    return SourceInstalledLiveSubjectPipelineResult(
      status: status,
      targetResults: results,
      reasonCode: switch (status) {
        SourceInstalledLiveSubjectPipelineStatus.noUsableSources =>
          results.isEmpty ? 'no_enabled_sources' : 'no_usable_subject_sources',
        SourceInstalledLiveSubjectPipelineStatus.partial =>
          'partial_subject_preflight',
        _ => null,
      },
    );
  }

  void close() => subjectCoordinator.close();

  SourceLiveOperationPlanResult<SourceLiveSubjectPlan> _buildPlan(
    SourceInstalledLiveSubjectTarget target,
  ) {
    try {
      return planFactory.buildSubjectDetailsPlan(
        installedPackage: target.installedPackage,
        subject: target.subject,
      );
    } on Object {
      final package = target.installedPackage.package;
      return SourceLiveOperationPlanResult(
        packageId: package.packageId,
        packageVersion: package.version,
        operation: SourcePackageLiveOperationKind.subjectDetails,
        programId: 'operation_unavailable',
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'package_preflight_failed',
      );
    }
  }

  SourceInstalledLiveSubjectPipelineResult _failed(String code) {
    return SourceInstalledLiveSubjectPipelineResult(
      status: SourceInstalledLiveSubjectPipelineStatus.failed,
      targetResults: const [],
      reasonCode: code,
    );
  }

  static SourceInstalledLiveSubjectPipelineStatus _aggregateStatus(
    List<SourceInstalledLiveSubjectTargetResult> results,
  ) {
    if (results.isEmpty) {
      return SourceInstalledLiveSubjectPipelineStatus.noUsableSources;
    }
    final subjects = results
        .map((result) => result.subjectResult)
        .whereType<SourceSubjectDetailsResult>()
        .toList(growable: false);
    if (subjects.isEmpty) {
      return SourceInstalledLiveSubjectPipelineStatus.noUsableSources;
    }
    if (subjects.any(
      (result) => result.status == SourceSubjectDetailsStatus.available,
    )) {
      return subjects.length == results.length
          ? SourceInstalledLiveSubjectPipelineStatus.available
          : SourceInstalledLiveSubjectPipelineStatus.partial;
    }
    if (subjects.any(
      (result) => result.status == SourceSubjectDetailsStatus.challengeRequired,
    )) {
      return SourceInstalledLiveSubjectPipelineStatus.challengeRequired;
    }
    if (subjects.every(
          (result) => result.status == SourceSubjectDetailsStatus.notFound,
        ) &&
        subjects.isNotEmpty) {
      return subjects.length == results.length
          ? SourceInstalledLiveSubjectPipelineStatus.notFound
          : SourceInstalledLiveSubjectPipelineStatus.partial;
    }
    return SourceInstalledLiveSubjectPipelineStatus.failed;
  }
}
