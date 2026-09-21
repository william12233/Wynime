import '../domain/models/source_identity.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_subject_details_models.dart';
import '../domain/services/source_subject_details_normalizer.dart';
import 'source_live_http_package_runtime.dart';
import 'source_live_http_request_coordinator.dart';

final class SourceLiveSubjectPlan {
  SourceLiveSubjectPlan({
    required this.requestPlan,
    required this.subject,
    required this.mapping,
  });

  final SourceLiveHttpRequestPlan requestPlan;
  final SourceSubjectIdentity subject;
  final SourceSubjectDetailsFieldMapping mapping;

  String get identityKey =>
      '${requestPlan.identityKey}/${subject.sourceId}/${subject.subjectId}';
}

final class SourceLiveSubjectCoordinator {
  SourceLiveSubjectCoordinator({
    required this.runtime,
    required this.normalizer,
  });

  final SourceLiveHttpPackageRuntime runtime;
  final SourceSubjectDetailsNormalizer normalizer;

  var _generation = 0;
  var _closed = false;

  Future<SourceSubjectDetailsResult> resolve({
    required SourceLiveSubjectPlan plan,
  }) async {
    if (_closed) return _failed(plan, 'live_subject_closed');
    final generation = ++_generation;
    final results = await runtime.executePrograms(
      requestPlan: plan.requestPlan,
      programIds: [plan.requestPlan.programId, plan.mapping.episodeProgramId],
    );
    if (!_isCurrent(generation)) return _failed(plan, 'stale_subject');

    final metadata = results[plan.requestPlan.programId];
    final episodes = results[plan.mapping.episodeProgramId];
    if (metadata == null || episodes == null) {
      return _failed(plan, 'subject_runtime_result_missing');
    }
    try {
      return normalizer.normalizeSubjectDetails(
        package: plan.requestPlan.installedPackage.package,
        metadataRuntimeResult: metadata,
        episodeRuntimeResult: episodes,
        subject: plan.subject,
        mapping: plan.mapping,
      );
    } on Object {
      return _failed(plan, 'subject_normalization_failed');
    }
  }

  void close() {
    if (_closed) return;
    _closed = true;
    _generation++;
  }

  bool _isCurrent(int generation) => !_closed && generation == _generation;

  SourceSubjectDetailsResult _failed(SourceLiveSubjectPlan plan, String code) {
    final package = plan.requestPlan.installedPackage.package;
    return SourceSubjectDetailsResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: SourceSubjectDetailsStatus.failed,
      diagnostics: [
        SourceSubjectDetailsDiagnostic(
          code: code,
          message: 'The source subject operation failed.',
        ),
      ],
    );
  }
}
