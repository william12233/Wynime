import '../domain/models/source_identity.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_runtime_models.dart';
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

/// Typed results from one bounded browser-rendered subject document.
///
/// The document itself never crosses this boundary. Both programs are
/// evaluated by the same declarative runtime before the coordinator applies
/// the normalizer.
final class SourceLiveSubjectDocumentFallbackResult {
  const SourceLiveSubjectDocumentFallbackResult({
    required this.metadata,
    required this.episodes,
  });

  final SourceRuntimeResult metadata;
  final SourceRuntimeResult episodes;
}

/// Optional browser-backed fallback for a subject page whose static response
/// is a JavaScript shell and therefore has no typed metadata or episode rows.
abstract interface class SourceLiveSubjectDocumentFallback {
  Future<SourceLiveSubjectDocumentFallbackResult> capture(
    SourceLiveSubjectPlan plan,
  );
}

final class SourceLiveSubjectCoordinator {
  SourceLiveSubjectCoordinator({
    required this.runtime,
    required this.normalizer,
    this.documentFallback,
  });

  final SourceLiveHttpPackageRuntime runtime;
  final SourceSubjectDetailsNormalizer normalizer;
  final SourceLiveSubjectDocumentFallback? documentFallback;

  var _generation = 0;
  var _closed = false;

  Future<SourceSubjectDetailsResult> resolve({
    required SourceLiveSubjectPlan plan,
  }) async {
    if (_closed) return _failed(plan, 'live_subject_closed');
    final generation = ++_generation;
    final results = await _executePlan(plan);
    if (!_isCurrent(generation)) return _failed(plan, 'stale_subject');

    try {
      return normalizer.normalizeSubjectDetails(
        package: plan.requestPlan.installedPackage.package,
        metadataRuntimeResult: results.metadata,
        episodeRuntimeResult: results.episodes,
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

  Future<SourceLiveSubjectDocumentFallbackResult> _executePlan(
    SourceLiveSubjectPlan plan,
  ) async {
    try {
      final staticResults = await runtime.executePrograms(
        requestPlan: plan.requestPlan,
        programIds: [plan.requestPlan.programId, plan.mapping.episodeProgramId],
      );
      final metadata = staticResults[plan.requestPlan.programId];
      final episodes = staticResults[plan.mapping.episodeProgramId];
      if (metadata == null || episodes == null) {
        return _runtimeFailurePair(plan, 'subject_runtime_result_missing');
      }
      if (documentFallback != null &&
          (metadata.status == SourceRuntimeStatus.notFound ||
              episodes.status == SourceRuntimeStatus.notFound)) {
        return await documentFallback!.capture(plan);
      }
      return SourceLiveSubjectDocumentFallbackResult(
        metadata: metadata,
        episodes: episodes,
      );
    } on Object {
      return _runtimeFailurePair(plan, 'source_live_subject_failed');
    }
  }

  SourceLiveSubjectDocumentFallbackResult _runtimeFailurePair(
    SourceLiveSubjectPlan plan,
    String code,
  ) {
    return SourceLiveSubjectDocumentFallbackResult(
      metadata: _runtimeFailure(plan, plan.requestPlan.programId, code),
      episodes: _runtimeFailure(plan, plan.mapping.episodeProgramId, code),
    );
  }

  SourceRuntimeResult _runtimeFailure(
    SourceLiveSubjectPlan plan,
    String programId,
    String code,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: programId,
      status: SourceRuntimeStatus.failed,
      records: const [],
      diagnostics: [
        SourceRuntimeDiagnostic(
          code: code,
          message: 'The live source subject operation failed.',
        ),
      ],
      consumedSteps: 0,
      selectorMatches: 0,
    );
  }

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
