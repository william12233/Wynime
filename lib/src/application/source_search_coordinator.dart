import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/models/source_models.dart';
import '../domain/models/source_search_coordinator_models.dart';
import '../domain/models/source_search_normalization_models.dart';
import '../domain/services/source_package_runtime.dart';
import '../domain/services/source_search_normalizer.dart';

/// Composes the current fixture-only source runtime and search normalizer.
///
/// It does not own package lifecycle, persistence, HTTP, WebView or UI state.
/// Callers provide explicit plans, so package mappings cannot be guessed from
/// provider IDs. Plans are evaluated in input order and each source keeps its
/// own typed normalization state.
final class SourceSearchCoordinator {
  SourceSearchCoordinator({
    required this.wynimeVersion,
    required this.runtime,
    required this.normalizer,
  });

  static const maxPlans = 32;

  final Version wynimeVersion;
  final SourcePackageRuntime runtime;
  final SourceSearchNormalizer normalizer;

  SourceSearchCoordinatorResult search({
    required String query,
    required Iterable<SourceSearchPlan> plans,
  }) {
    final normalizedQuery = query.trim();
    if (!_validQuery(normalizedQuery)) {
      return _failedResult(query: '', reasonCode: 'invalid_query');
    }

    final planList = <SourceSearchPlan>[];
    for (final plan in plans) {
      if (planList.length == maxPlans) {
        return _failedResult(
          query: normalizedQuery,
          reasonCode: 'too_many_source_plans',
        );
      }
      planList.add(plan);
    }
    if (planList.isEmpty) {
      return SourceSearchCoordinatorResult(
        query: normalizedQuery,
        status: SourceSearchCoordinatorStatus.noSources,
        sourceResults: const [],
        results: const [],
        reasonCode: 'no_enabled_sources',
      );
    }

    final identities = <String>{};
    for (final plan in planList) {
      if (!identities.add(plan.identityKey)) {
        return _failedResult(
          query: normalizedQuery,
          reasonCode: 'duplicate_source_plan',
        );
      }
    }

    final sourceResults = <SourceSearchNormalizationResult>[];
    final results = <SourceSearchResult>[];
    for (final plan in planList) {
      final runtimeResult = _executePlan(plan);
      final normalized = _normalizePlan(plan, runtimeResult);
      sourceResults.add(normalized);
      results.addAll(normalized.results);
    }

    final hasResults = results.isNotEmpty;
    final hasFailed = sourceResults.any(
      (source) => source.status == SourceSearchNormalizationStatus.failed,
    );
    final hasBlocked = sourceResults.any(
      (source) => switch (source.status) {
        SourceSearchNormalizationStatus.disabled ||
        SourceSearchNormalizationStatus.consentRequired ||
        SourceSearchNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = sourceResults.any(
      (source) =>
          source.status == SourceSearchNormalizationStatus.available ||
          source.status == SourceSearchNormalizationStatus.notFound,
    );

    final status = switch ((hasResults, hasFailed, hasBlocked)) {
      (true, false, false) => SourceSearchCoordinatorStatus.available,
      (true, _, _) => SourceSearchCoordinatorStatus.partial,
      (false, true, _) => SourceSearchCoordinatorStatus.failed,
      (false, false, true) when !hasEligibleSource =>
        SourceSearchCoordinatorStatus.noSources,
      (false, false, _) => SourceSearchCoordinatorStatus.notFound,
    };
    final reasonCode = switch (status) {
      SourceSearchCoordinatorStatus.available => null,
      SourceSearchCoordinatorStatus.partial => 'partial_source_results',
      SourceSearchCoordinatorStatus.failed => 'source_search_failed',
      SourceSearchCoordinatorStatus.noSources => 'no_enabled_sources',
      SourceSearchCoordinatorStatus.notFound => 'source_search_not_found',
    };

    return SourceSearchCoordinatorResult(
      query: normalizedQuery,
      status: status,
      sourceResults: sourceResults,
      results: results,
      reasonCode: reasonCode,
    );
  }

  SourceRuntimeResult _executePlan(SourceSearchPlan plan) {
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

  SourceSearchNormalizationResult _normalizePlan(
    SourceSearchPlan plan,
    SourceRuntimeResult runtimeResult,
  ) {
    try {
      return normalizer.normalizeSearch(
        runtimeResult: runtimeResult,
        mapping: plan.mapping,
      );
    } on Object {
      return SourceSearchNormalizationResult(
        packageId: plan.installedPackage.package.packageId,
        packageVersion: plan.installedPackage.package.version,
        programId: plan.programId,
        status: SourceSearchNormalizationStatus.failed,
        results: const [],
        diagnostics: [
          SourceSearchNormalizationDiagnostic(
            code: 'normalization_failed',
            message: 'The source search result could not be normalized.',
          ),
        ],
      );
    }
  }

  SourceRuntimeResult _preflightResult(
    SourceSearchPlan plan,
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

  SourceSearchCoordinatorResult _failedResult({
    required String query,
    required String reasonCode,
  }) {
    return SourceSearchCoordinatorResult(
      query: query,
      status: SourceSearchCoordinatorStatus.failed,
      sourceResults: const [],
      results: const [],
      reasonCode: reasonCode,
    );
  }

  static bool _validQuery(String value) {
    return value.isNotEmpty &&
        value.length <= 128 &&
        !value.codeUnits.any(
          (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
        );
  }
}
