import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_episode_coordinator_models.dart';
import '../domain/models/source_episode_normalization_models.dart';
import '../domain/models/source_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_episode_normalizer.dart';
import '../domain/services/source_package_runtime.dart';

/// Composes the current fixture-only source runtime and episode normalizer.
///
/// It does not own package lifecycle, persistence, HTTP, WebView, playback or
/// UI state. Callers provide explicit plans, so episode mappings cannot be
/// guessed from provider IDs. Plans are evaluated in input order and each
/// source keeps its own typed normalization state.
final class SourceEpisodeCoordinator {
  SourceEpisodeCoordinator({
    required this.wynimeVersion,
    required this.runtime,
    required this.normalizer,
  });

  static const maxPlans = 32;

  final Version wynimeVersion;
  final SourcePackageRuntime runtime;
  final SourceEpisodeNormalizer normalizer;

  SourceEpisodeCoordinatorResult listEpisodes({
    required Iterable<SourceEpisodePlan> plans,
  }) {
    final planList = <SourceEpisodePlan>[];
    for (final plan in plans) {
      if (planList.length == maxPlans) {
        return _failedResult(reasonCode: 'too_many_source_plans');
      }
      planList.add(plan);
    }
    if (planList.isEmpty) {
      return SourceEpisodeCoordinatorResult(
        status: SourceEpisodeCoordinatorStatus.noSources,
        sourceResults: const [],
        episodes: const [],
        reasonCode: 'no_enabled_sources',
      );
    }

    final identities = <String>{};
    for (final plan in planList) {
      if (!identities.add(plan.identityKey)) {
        return _failedResult(reasonCode: 'duplicate_source_plan');
      }
    }

    final sourceResults = <SourceEpisodeNormalizationResult>[];
    final episodes = <SourceEpisode>[];
    for (final plan in planList) {
      final runtimeResult = _executePlan(plan);
      final normalized = _normalizePlan(plan, runtimeResult);
      sourceResults.add(normalized);
      episodes.addAll(normalized.results);
    }

    final hasEpisodes = episodes.isNotEmpty;
    final hasFailed = sourceResults.any(
      (source) => source.status == SourceEpisodeNormalizationStatus.failed,
    );
    final hasBlocked = sourceResults.any(
      (source) => switch (source.status) {
        SourceEpisodeNormalizationStatus.disabled ||
        SourceEpisodeNormalizationStatus.consentRequired ||
        SourceEpisodeNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = sourceResults.any(
      (source) =>
          source.status == SourceEpisodeNormalizationStatus.available ||
          source.status == SourceEpisodeNormalizationStatus.notFound,
    );

    final status = switch ((hasEpisodes, hasFailed, hasBlocked)) {
      (true, false, false) => SourceEpisodeCoordinatorStatus.available,
      (true, _, _) => SourceEpisodeCoordinatorStatus.partial,
      (false, true, _) => SourceEpisodeCoordinatorStatus.failed,
      (false, false, true) when !hasEligibleSource =>
        SourceEpisodeCoordinatorStatus.noSources,
      (false, false, _) => SourceEpisodeCoordinatorStatus.notFound,
    };
    final reasonCode = switch (status) {
      SourceEpisodeCoordinatorStatus.available => null,
      SourceEpisodeCoordinatorStatus.partial => 'partial_source_results',
      SourceEpisodeCoordinatorStatus.failed => 'source_episode_failed',
      SourceEpisodeCoordinatorStatus.noSources => 'no_enabled_sources',
      SourceEpisodeCoordinatorStatus.notFound => 'source_episode_not_found',
    };

    return SourceEpisodeCoordinatorResult(
      status: status,
      sourceResults: sourceResults,
      episodes: episodes,
      reasonCode: reasonCode,
    );
  }

  SourceRuntimeResult _executePlan(SourceEpisodePlan plan) {
    final installed = plan.installedPackage;
    final package = installed.package;
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

  SourceEpisodeNormalizationResult _normalizePlan(
    SourceEpisodePlan plan,
    SourceRuntimeResult runtimeResult,
  ) {
    try {
      return normalizer.normalizeEpisodes(
        runtimeResult: runtimeResult,
        mapping: plan.mapping,
      );
    } on Object {
      return SourceEpisodeNormalizationResult(
        packageId: plan.installedPackage.package.packageId,
        packageVersion: plan.installedPackage.package.version,
        programId: plan.programId,
        status: SourceEpisodeNormalizationStatus.failed,
        results: const [],
        diagnostics: [
          SourceEpisodeNormalizationDiagnostic(
            code: 'normalization_failed',
            message: 'The source episode result could not be normalized.',
          ),
        ],
      );
    }
  }

  SourceRuntimeResult _preflightResult(
    SourceEpisodePlan plan,
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

  SourceEpisodeCoordinatorResult _failedResult({required String reasonCode}) {
    return SourceEpisodeCoordinatorResult(
      status: SourceEpisodeCoordinatorStatus.failed,
      sourceResults: const [],
      episodes: const [],
      reasonCode: reasonCode,
    );
  }
}
