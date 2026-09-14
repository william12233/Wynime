import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/source_playable_normalization_models.dart';
import '../domain/models/source_playable_source_coordinator_models.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_package_runtime.dart';
import '../domain/services/source_playable_source_normalizer.dart';

/// Composes the current fixture-only source runtime and playable-source
/// normalizer.
///
/// It does not own package lifecycle, persistence, HTTP, WebView, playback,
/// route selection or UI state. Plans are evaluated in caller order and each
/// source keeps its own typed normalization state.
final class SourcePlayableSourceCoordinator {
  SourcePlayableSourceCoordinator({
    required this.wynimeVersion,
    required this.runtime,
    required this.normalizer,
  });

  static const maxPlans = 32;

  final Version wynimeVersion;
  final SourcePackageRuntime runtime;
  final SourcePlayableSourceNormalizer normalizer;

  SourcePlayableSourceCoordinatorResult listPlayableSources({
    required Iterable<SourcePlayableSourcePlan> plans,
  }) {
    final planList = <SourcePlayableSourcePlan>[];
    for (final plan in plans) {
      if (planList.length == maxPlans) {
        return _failedResult(reasonCode: 'too_many_source_plans');
      }
      planList.add(plan);
    }
    if (planList.isEmpty) {
      return SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.noSources,
        sourceResults: const [],
        sources: const [],
        reasonCode: 'no_enabled_sources',
      );
    }

    final identities = <String>{};
    for (final plan in planList) {
      if (!identities.add(plan.identityKey)) {
        return _failedResult(reasonCode: 'duplicate_source_plan');
      }
    }

    final sourceResults = <SourcePlayableSourceNormalizationResult>[];
    final sources = <SourcePlayableSource>[];
    for (final plan in planList) {
      final runtimeResult = _executePlan(plan);
      final normalized = _normalizePlan(plan, runtimeResult);
      sourceResults.add(normalized);
      sources.addAll(normalized.results);
    }

    final hasSources = sources.isNotEmpty;
    final hasFailed = sourceResults.any(
      (source) =>
          source.status == SourcePlayableSourceNormalizationStatus.failed,
    );
    final hasBlocked = sourceResults.any(
      (source) => switch (source.status) {
        SourcePlayableSourceNormalizationStatus.disabled ||
        SourcePlayableSourceNormalizationStatus.consentRequired ||
        SourcePlayableSourceNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = sourceResults.any(
      (source) =>
          source.status == SourcePlayableSourceNormalizationStatus.available ||
          source.status == SourcePlayableSourceNormalizationStatus.notFound,
    );

    final status = switch ((hasSources, hasFailed, hasBlocked)) {
      (true, false, false) => SourcePlayableSourceCoordinatorStatus.available,
      (true, _, _) => SourcePlayableSourceCoordinatorStatus.partial,
      (false, true, _) => SourcePlayableSourceCoordinatorStatus.failed,
      (false, false, true) when !hasEligibleSource =>
        SourcePlayableSourceCoordinatorStatus.noSources,
      (false, false, _) => SourcePlayableSourceCoordinatorStatus.notFound,
    };
    final reasonCode = switch (status) {
      SourcePlayableSourceCoordinatorStatus.available => null,
      SourcePlayableSourceCoordinatorStatus.partial => 'partial_source_results',
      SourcePlayableSourceCoordinatorStatus.failed => 'source_playable_failed',
      SourcePlayableSourceCoordinatorStatus.noSources => 'no_enabled_sources',
      SourcePlayableSourceCoordinatorStatus.notFound =>
        'source_playable_not_found',
    };

    return SourcePlayableSourceCoordinatorResult(
      status: status,
      sourceResults: sourceResults,
      sources: sources,
      reasonCode: reasonCode,
    );
  }

  SourceRuntimeResult _executePlan(SourcePlayableSourcePlan plan) {
    final installed = plan.installedPackage;
    final package = installed.package;
    if (!_validEpisodeIdentity(plan.episode)) {
      return _preflightResult(
        plan,
        SourceRuntimeStatus.failed,
        'invalid_episode_identity',
        'The requested episode identity is invalid.',
      );
    }
    if (plan.episode.sourceId != package.packageId) {
      return _preflightResult(
        plan,
        SourceRuntimeStatus.failed,
        'episode_source_mismatch',
        'The requested episode does not belong to this source package.',
      );
    }
    if (installed.requiresConsent || installed.requiresReconsent) {
      return _preflightResult(
        plan,
        SourceRuntimeStatus.consentRequired,
        'consent_required',
        'The source package requires explicit consent.',
      );
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return _preflightResult(
        plan,
        SourceRuntimeStatus.disabled,
        'package_disabled',
        'The source package is not enabled.',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _preflightResult(
        plan,
        SourceRuntimeStatus.incompatible,
        'incompatible_wynime_version',
        'The source package is incompatible with this Wynime version.',
      );
    }
    try {
      return runtime.executeFixture(
        installedPackage: installed,
        programId: plan.programId,
        fixture: plan.fixture,
      );
    } on Object {
      return _preflightResult(
        plan,
        SourceRuntimeStatus.failed,
        'runtime_failed',
        'The source runtime reported a diagnostic.',
      );
    }
  }

  SourcePlayableSourceNormalizationResult _normalizePlan(
    SourcePlayableSourcePlan plan,
    SourceRuntimeResult runtimeResult,
  ) {
    SourcePlayableSourceNormalizationResult normalized;
    try {
      normalized = normalizer.normalizePlayableSources(
        package: plan.installedPackage.package,
        runtimeResult: runtimeResult,
        episode: plan.episode,
        mapping: plan.mapping,
      );
    } on Object {
      return _failedNormalization(
        plan,
        'The source playable result could not be normalized.',
      );
    }

    if (!_matchesPlan(plan, normalized)) {
      return _failedNormalization(
        plan,
        'The normalized source identity did not match its explicit plan.',
        code: 'normalization_identity_mismatch',
      );
    }
    final hasResults = normalized.results.isNotEmpty;
    if ((normalized.status ==
                SourcePlayableSourceNormalizationStatus.available &&
            !hasResults) ||
        (normalized.status !=
                SourcePlayableSourceNormalizationStatus.available &&
            hasResults)) {
      return _failedNormalization(
        plan,
        'The normalized playable result had an invalid status shape.',
        code: 'normalization_result_invalid',
      );
    }
    if (normalized.status ==
            SourcePlayableSourceNormalizationStatus.available &&
        normalized.results.any(
          (source) =>
              !_isSupportedCandidate(source) ||
              source.episode != plan.episode ||
              source.episode.sourceId !=
                  plan.installedPackage.package.packageId ||
              !plan.installedPackage.package.securityPolicy.allowsUri(
                source.mediaUri,
              ) ||
              !plan.installedPackage.package.securityPolicy.allowsUri(
                source.pageUri,
              ),
        )) {
      return _failedNormalization(
        plan,
        'The normalized playable candidates failed identity validation.',
        code: 'normalization_candidate_invalid',
      );
    }
    return normalized;
  }

  SourceRuntimeResult _preflightResult(
    SourcePlayableSourcePlan plan,
    SourceRuntimeStatus status,
    String code,
    String message,
  ) {
    final package = plan.installedPackage.package;
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.programId,
      status: status,
      records: const [],
      diagnostics: [SourceRuntimeDiagnostic(code: code, message: message)],
      consumedSteps: 0,
      selectorMatches: 0,
    );
  }

  SourcePlayableSourceNormalizationResult _failedNormalization(
    SourcePlayableSourcePlan plan,
    String message, {
    String code = 'normalization_failed',
  }) {
    return SourcePlayableSourceNormalizationResult(
      packageId: plan.installedPackage.package.packageId,
      packageVersion: plan.installedPackage.package.version,
      programId: plan.programId,
      status: SourcePlayableSourceNormalizationStatus.failed,
      results: const [],
      diagnostics: [
        SourcePlayableSourceNormalizationDiagnostic(
          code: code,
          message: message,
        ),
      ],
    );
  }

  static bool _matchesPlan(
    SourcePlayableSourcePlan plan,
    SourcePlayableSourceNormalizationResult normalized,
  ) {
    return normalized.packageId == plan.installedPackage.package.packageId &&
        normalized.packageVersion == plan.installedPackage.package.version &&
        normalized.programId == plan.programId;
  }

  static bool _isSupportedCandidate(SourcePlayableSource source) {
    return switch (source.kind.name) {
      'hls' || 'video' || 'audio' => true,
      _ => false,
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
          value.trim().isNotEmpty &&
          value.length <= 128 &&
          !value.codeUnits.any(
            (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
          ),
    );
  }

  SourcePlayableSourceCoordinatorResult _failedResult({
    required String reasonCode,
  }) {
    return SourcePlayableSourceCoordinatorResult(
      status: SourcePlayableSourceCoordinatorStatus.failed,
      sourceResults: const [],
      sources: const [],
      reasonCode: reasonCode,
    );
  }
}
