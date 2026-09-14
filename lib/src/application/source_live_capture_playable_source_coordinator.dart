import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_identity.dart';
import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_live_capture_playable_source_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_playable_normalization_models.dart';
import '../domain/models/web_capture_models.dart';
import 'source_live_capture_snapshot_validator.dart';

/// Converts one accepted live capture into normalized playable candidates.
///
/// The package-aware capture surface remains responsible for admission and
/// WebView mounting. This coordinator rechecks that exact request/result pair,
/// validates the snapshot once more through the shared snapshot validator,
/// and requires explicit metadata for every candidate. It does not perform
/// source HTTP, execute package rules, select a route, resolve a session or
/// start a player.
final class SourceLiveCapturePlayableSourceCoordinator {
  SourceLiveCapturePlayableSourceCoordinator({required this.wynimeVersion});

  final Version wynimeVersion;
  final SourceLiveCaptureSnapshotValidator _snapshotValidator =
      const SourceLiveCaptureSnapshotValidator();

  SourceLiveCapturePlayableSourceResult normalize(
    SourceLiveCapturePlayableSourcePlan plan,
  ) {
    final installed = plan.installedPackage;
    final package = installed.package;

    if (installed.requiresConsent || installed.requiresReconsent) {
      return _blocked(
        plan,
        SourceLiveCapturePlayableSourceStatus.consentRequired,
        'consent_required',
      );
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return _blocked(
        plan,
        SourceLiveCapturePlayableSourceStatus.disabled,
        'package_disabled',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _blocked(
        plan,
        SourceLiveCapturePlayableSourceStatus.incompatible,
        'incompatible_wynime_version',
      );
    }
    try {
      package.programById(plan.programId);
    } on StateError {
      return _failed(plan, 'program_not_found');
    } on Object {
      return _failed(plan, 'package_preflight_failed');
    }

    if (!_validEpisodeIdentity(plan.episode)) {
      return _failed(plan, 'invalid_episode_identity');
    }
    if (plan.episode.sourceId != package.packageId) {
      return _failed(plan, 'episode_source_mismatch');
    }

    final request = plan.captureRequest;
    if (request.packageId != package.packageId ||
        request.packageVersion != package.version) {
      return _failed(plan, 'capture_package_mismatch');
    }
    if (request.programId != plan.programId) {
      return _failed(plan, 'capture_program_mismatch');
    }
    if (!request.webCaptureRequest.securityPolicy.semanticallyEquals(
      package.securityPolicy,
    )) {
      return _failed(plan, 'capture_policy_mismatch');
    }
    if (!package.securityPolicy.allowsUri(
      request.webCaptureRequest.initialUri,
    )) {
      return _failed(plan, 'initial_uri_not_allowed');
    }

    final capture = plan.captureResult;
    if (capture.packageId != request.packageId ||
        capture.packageVersion != request.packageVersion ||
        capture.programId != request.programId) {
      return _failed(plan, 'capture_result_mismatch');
    }
    if (capture.status != SourceLiveCaptureStatus.captured ||
        capture.snapshot == null) {
      return _failed(plan, capture.reasonCode ?? 'capture_not_available');
    }

    final validatedCapture = _snapshotValidator.validate(
      request,
      capture.snapshot!,
    );
    if (validatedCapture.status != SourceLiveCaptureStatus.captured ||
        validatedCapture.snapshot == null) {
      return _failed(
        plan,
        validatedCapture.reasonCode ?? 'capture_not_available',
      );
    }

    final snapshot = validatedCapture.snapshot!;
    final mappings = _mappingByIndex(plan.mappings, snapshot.candidates.length);
    if (mappings == null) {
      return _failed(plan, 'candidate_mapping_invalid');
    }
    if (snapshot.candidates.isEmpty) {
      return SourceLiveCapturePlayableSourceResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: plan.programId,
        status: SourceLiveCapturePlayableSourceStatus.notFound,
        sources: const [],
        diagnostics: const [],
        reasonCode: 'no_media_candidates',
      );
    }

    final sources = <SourceLiveCapturePlayableSource>[];
    final diagnostics = <SourcePlayableSourceNormalizationDiagnostic>[];
    final seenSourceKeys = <String>{};
    for (var index = 0; index < snapshot.candidates.length; index++) {
      final candidate = snapshot.candidates[index];
      final mapping = mappings[index]!;
      if (!_isSupportedCandidate(candidate.kind)) {
        diagnostics.add(
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'playable_kind_unsupported',
            message: 'The captured media candidate is unsupported.',
            recordIndex: index,
          ),
        );
        continue;
      }
      if (!seenSourceKeys.add(mapping.sourceKey)) {
        diagnostics.add(
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'duplicate_playable_source_key',
            message: 'A duplicate playable source was ignored.',
            recordIndex: index,
          ),
        );
        continue;
      }

      try {
        final source = SourcePlayableSource(
          episode: plan.episode,
          sourceKey: mapping.sourceKey,
          label: mapping.label,
          kind: candidate.kind,
          mediaUri: candidate.uri,
          pageUri: snapshot.finalUri,
        );
        sources.add(
          SourceLiveCapturePlayableSource(
            candidateIndex: index,
            source: source,
            candidate: candidate,
          ),
        );
      } on Object {
        diagnostics.add(
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'live_candidate_invalid',
            message: 'The captured playable candidate is invalid.',
            recordIndex: index,
          ),
        );
      }
    }

    if (sources.isEmpty) {
      return SourceLiveCapturePlayableSourceResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: plan.programId,
        status: SourceLiveCapturePlayableSourceStatus.failed,
        sources: const [],
        diagnostics: diagnostics,
        reasonCode: 'no_supported_candidates',
      );
    }

    return SourceLiveCapturePlayableSourceResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.programId,
      status: SourceLiveCapturePlayableSourceStatus.available,
      sources: sources,
      diagnostics: diagnostics,
      captureRequest: request,
      captureResult: validatedCapture,
    );
  }

  SourceLiveCapturePlayableSourceResult _blocked(
    SourceLiveCapturePlayableSourcePlan plan,
    SourceLiveCapturePlayableSourceStatus status,
    String reasonCode,
  ) => SourceLiveCapturePlayableSourceResult(
    packageId: plan.installedPackage.package.packageId,
    packageVersion: plan.installedPackage.package.version,
    programId: plan.programId,
    status: status,
    sources: const [],
    diagnostics: const [],
    reasonCode: reasonCode,
  );

  SourceLiveCapturePlayableSourceResult _failed(
    SourceLiveCapturePlayableSourcePlan plan,
    String reasonCode,
  ) => SourceLiveCapturePlayableSourceResult(
    packageId: plan.installedPackage.package.packageId,
    packageVersion: plan.installedPackage.package.version,
    programId: plan.programId,
    status: SourceLiveCapturePlayableSourceStatus.failed,
    sources: const [],
    diagnostics: const [],
    reasonCode: _safeReason(reasonCode),
  );

  static Map<int, SourceLiveCapturePlayableSourceMapping>? _mappingByIndex(
    Iterable<SourceLiveCapturePlayableSourceMapping> mappings,
    int candidateCount,
  ) {
    final mappingByIndex = <int, SourceLiveCapturePlayableSourceMapping>{};
    for (final mapping in mappings) {
      if (mapping.candidateIndex >= candidateCount ||
          mappingByIndex.containsKey(mapping.candidateIndex)) {
        return null;
      }
      mappingByIndex[mapping.candidateIndex] = mapping;
    }
    if (mappingByIndex.length != candidateCount) {
      return null;
    }
    for (var index = 0; index < candidateCount; index++) {
      if (!mappingByIndex.containsKey(index)) {
        return null;
      }
    }
    return mappingByIndex;
  }

  static bool _isSupportedCandidate(WebCandidateKind kind) {
    return switch (kind) {
      WebCandidateKind.hls ||
      WebCandidateKind.video ||
      WebCandidateKind.audio => true,
      WebCandidateKind.dash || WebCandidateKind.mediaSegment => false,
    };
  }

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

  static String _safeReason(String value) {
    final normalized = value.trim();
    return RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(normalized)
        ? normalized
        : 'live_capture_playable_failed';
  }
}
