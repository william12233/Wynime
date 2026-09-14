import '../domain/models/source_identity.dart';
import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_live_capture_package_models.dart';
import '../domain/models/source_live_capture_playable_source_models.dart';
import '../domain/models/source_package_manager_models.dart';

/// The typed handoff status for an accepted package capture to a playable
/// source plan.
enum SourceLiveCapturePlayableSourcePlanStatus { ready, notReady }

enum SourceLiveCapturePlayableSourcePlanFailureStage {
  packageAdmission,
  capture,
  plan,
}

/// A bounded result for pairing one package admission and one capture result
/// with explicit episode and candidate metadata.
///
/// A ready result retains the exact in-memory request/result pair inside the
/// plan for the next live normalization boundary. Rejected results retain no
/// capture data and expose only one typed stage and a safe reason token.
final class SourceLiveCapturePlayableSourcePlanResult {
  SourceLiveCapturePlayableSourcePlanResult({
    required this.status,
    this.plan,
    this.failureStage,
    this.packageStatus,
    this.captureStatus,
    this.reasonCode,
  }) {
    final isReady = status == SourceLiveCapturePlayableSourcePlanStatus.ready;
    if (isReady) {
      if (plan == null ||
          failureStage != null ||
          packageStatus != null ||
          captureStatus != null ||
          reasonCode != null) {
        throw ArgumentError(
          'A ready result must contain only one playable-source plan.',
        );
      }
      return;
    }

    if (plan != null ||
        failureStage == null ||
        reasonCode == null ||
        !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A non-ready result requires one typed failure and no plan.',
      );
    }

    final stageMatches = switch (failureStage!) {
      SourceLiveCapturePlayableSourcePlanFailureStage.packageAdmission =>
        packageStatus != null &&
            packageStatus != SourceLiveCapturePackageStatus.ready &&
            captureStatus == null,
      SourceLiveCapturePlayableSourcePlanFailureStage.capture =>
        captureStatus != null &&
            captureStatus != SourceLiveCaptureStatus.captured &&
            packageStatus == null,
      SourceLiveCapturePlayableSourcePlanFailureStage.plan =>
        packageStatus == null && captureStatus == null,
    };
    if (!stageMatches) {
      throw ArgumentError(
        'The plan failure stage must match its typed status.',
      );
    }
  }

  final SourceLiveCapturePlayableSourcePlanStatus status;
  final SourceLiveCapturePlayableSourcePlan? plan;
  final SourceLiveCapturePlayableSourcePlanFailureStage? failureStage;
  final SourceLiveCapturePackageStatus? packageStatus;
  final SourceLiveCaptureStatus? captureStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasPlan': plan != null,
    'failureStage': failureStage?.name,
    'packageStatus': packageStatus?.name,
    'captureStatus': captureStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Pairs the package-aware capture surface with the live playable-source
/// normalizer's existing plan contract.
///
/// This is a pure application handoff. It does not start capture, access a
/// WebView, execute a package, perform network I/O, normalize candidates,
/// select a route, resolve a session or start a player. Capture-generation
/// and late-result ownership remain with the package/WebView authorities.
final class SourceLiveCapturePlayableSourcePlanCoordinator {
  const SourceLiveCapturePlayableSourcePlanCoordinator();

  SourceLiveCapturePlayableSourcePlanResult buildPlan({
    required SourceLiveCapturePackagePlan packagePlan,
    required SourceLiveCapturePackageResult admission,
    required SourceLiveCaptureResult captureResult,
    required SourceEpisodeIdentity episode,
    required Iterable<SourceLiveCapturePlayableSourceMapping> mappings,
  }) {
    if (admission.status != SourceLiveCapturePackageStatus.ready) {
      return _packageFailure(
        admission.status,
        admission.reasonCode ?? 'package_admission_not_ready',
      );
    }
    if (admission.request == null) {
      return _planFailure('admission_request_missing');
    }

    final installed = packagePlan.installedPackage;
    final package = installed.package;
    final request = admission.request!;
    if (admission.packageId != package.packageId ||
        admission.packageVersion != package.version ||
        admission.programId != packagePlan.programId ||
        request.packageId != package.packageId ||
        request.packageVersion != package.version ||
        request.programId != packagePlan.programId ||
        !identical(request.webCaptureRequest, packagePlan.webCaptureRequest)) {
      return _planFailure('admission_request_mismatch');
    }
    if (installed.requiresConsent || installed.requiresReconsent) {
      return _planFailure('package_consent_changed');
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return _planFailure('package_state_changed');
    }
    try {
      package.programById(packagePlan.programId);
    } on Object {
      return _planFailure('program_not_found');
    }
    if (!request.webCaptureRequest.securityPolicy.semanticallyEquals(
          package.securityPolicy,
        ) ||
        !package.securityPolicy.allowsUri(
          request.webCaptureRequest.initialUri,
        )) {
      return _planFailure('capture_request_policy_mismatch');
    }

    if (captureResult.packageId != request.packageId ||
        captureResult.packageVersion != request.packageVersion ||
        captureResult.programId != request.programId) {
      return _planFailure('capture_result_mismatch');
    }
    if (captureResult.status != SourceLiveCaptureStatus.captured ||
        captureResult.snapshot == null) {
      return _captureFailure(
        captureResult.status,
        captureResult.reasonCode ?? 'capture_not_available',
      );
    }

    if (!_validEpisodeIdentity(episode)) {
      return _planFailure('invalid_episode_identity');
    }
    if (episode.sourceId != package.packageId) {
      return _planFailure('episode_source_mismatch');
    }

    final mappingList = _copyMappings(mappings);
    if (mappingList == null ||
        !_coversCandidates(
          mappingList,
          captureResult.snapshot!.candidates.length,
        )) {
      return _planFailure('candidate_mapping_invalid');
    }

    try {
      return SourceLiveCapturePlayableSourcePlanResult(
        status: SourceLiveCapturePlayableSourcePlanStatus.ready,
        plan: SourceLiveCapturePlayableSourcePlan(
          installedPackage: installed,
          programId: packagePlan.programId,
          episode: episode,
          captureRequest: request,
          captureResult: captureResult,
          mappings: mappingList,
        ),
      );
    } on Object {
      return _planFailure('playable_plan_build_failed');
    }
  }

  static SourceLiveCapturePlayableSourcePlanResult _packageFailure(
    SourceLiveCapturePackageStatus status,
    String reasonCode,
  ) => SourceLiveCapturePlayableSourcePlanResult(
    status: SourceLiveCapturePlayableSourcePlanStatus.notReady,
    failureStage:
        SourceLiveCapturePlayableSourcePlanFailureStage.packageAdmission,
    packageStatus: status,
    reasonCode: _safeReason(reasonCode, 'package_admission_failed'),
  );

  static SourceLiveCapturePlayableSourcePlanResult _captureFailure(
    SourceLiveCaptureStatus status,
    String reasonCode,
  ) => SourceLiveCapturePlayableSourcePlanResult(
    status: SourceLiveCapturePlayableSourcePlanStatus.notReady,
    failureStage: SourceLiveCapturePlayableSourcePlanFailureStage.capture,
    captureStatus: status,
    reasonCode: _safeReason(reasonCode, 'capture_failed'),
  );

  static SourceLiveCapturePlayableSourcePlanResult _planFailure(
    String reasonCode,
  ) => SourceLiveCapturePlayableSourcePlanResult(
    status: SourceLiveCapturePlayableSourcePlanStatus.notReady,
    failureStage: SourceLiveCapturePlayableSourcePlanFailureStage.plan,
    reasonCode: _safeReason(reasonCode, 'playable_plan_failed'),
  );

  static List<SourceLiveCapturePlayableSourceMapping>? _copyMappings(
    Iterable<SourceLiveCapturePlayableSourceMapping> mappings,
  ) {
    final result = <SourceLiveCapturePlayableSourceMapping>[];
    try {
      for (final mapping in mappings) {
        if (result.length == 1000) {
          return null;
        }
        result.add(mapping);
      }
    } on Object {
      return null;
    }
    return List<SourceLiveCapturePlayableSourceMapping>.unmodifiable(result);
  }

  static bool _coversCandidates(
    List<SourceLiveCapturePlayableSourceMapping> mappings,
    int candidateCount,
  ) {
    final indexes = <int>{};
    for (final mapping in mappings) {
      if (mapping.candidateIndex < 0 ||
          mapping.candidateIndex >= candidateCount ||
          !indexes.add(mapping.candidateIndex)) {
        return false;
      }
    }
    return indexes.length == candidateCount;
  }

  static bool _validEpisodeIdentity(SourceEpisodeIdentity episode) =>
      [
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

  static String _safeReason(String value, String fallback) {
    final normalized = value.trim();
    return RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)
        ? normalized
        : fallback;
  }
}
