import '../domain/models/source_models.dart';
import '../domain/models/source_search_coordinator_models.dart';
import '../domain/models/source_search_normalization_models.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_search_normalizer.dart';
import 'source_live_http_package_runtime.dart';
import 'source_live_http_request_coordinator.dart';

/// One explicit live HTTP request paired with the source-search field mapping
/// that will consume its typed runtime records.
final class SourceLiveSearchPlan {
  SourceLiveSearchPlan({required this.requestPlan, required this.mapping});

  final SourceLiveHttpRequestPlan requestPlan;
  final SourceSearchFieldMapping mapping;

  String get identityKey => requestPlan.identityKey;
}

/// Composes bounded live source execution with the existing search normalizer.
///
/// Each call owns one immutable input snapshot and evaluates plans in caller
/// order. A later call supersedes an earlier pending call, and [close] makes
/// pending and future calls return a safe typed failure. This coordinator does
/// not cancel or close the lower transport, which remains the I/O lifecycle
/// authority.
final class SourceLiveSearchCoordinator {
  SourceLiveSearchCoordinator({
    required this.runtime,
    required this.normalizer,
  });

  static const maxPlans = 32;

  final SourceLiveHttpPackageRuntime runtime;
  final SourceSearchNormalizer normalizer;

  var _generation = 0;
  var _closed = false;

  Future<SourceSearchCoordinatorResult> search({
    required String query,
    required Iterable<SourceLiveSearchPlan> plans,
  }) async {
    if (_closed) {
      return _failedResult(
        query: _safeQuery(query),
        reasonCode: 'live_search_closed',
      );
    }
    final operation = ++_generation;
    final normalizedQuery = query.trim();
    if (!_validQuery(normalizedQuery)) {
      return _failedResult(query: '', reasonCode: 'invalid_query');
    }

    final planList = <SourceLiveSearchPlan>[];
    try {
      for (final plan in plans) {
        if (planList.length == maxPlans) {
          return _failedResult(
            query: normalizedQuery,
            reasonCode: 'too_many_source_plans',
          );
        }
        planList.add(plan);
      }
    } on Object {
      return _failedResult(
        query: normalizedQuery,
        reasonCode: 'invalid_source_plans',
      );
    }
    if (!_isCurrent(operation)) {
      return _staleResult(normalizedQuery);
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
      if (!_isCurrent(operation)) {
        return _staleResult(normalizedQuery);
      }

      final runtimeResult = await _executePlan(plan);
      if (!_isCurrent(operation)) {
        return _staleResult(normalizedQuery);
      }

      final normalized = _normalizePlan(plan, runtimeResult);
      if (!_isCurrent(operation)) {
        return _staleResult(normalizedQuery);
      }
      sourceResults.add(normalized);
      results.addAll(normalized.results);
    }

    if (!_isCurrent(operation)) {
      return _staleResult(normalizedQuery);
    }
    return _aggregate(
      query: normalizedQuery,
      sourceResults: sourceResults,
      results: results,
    );
  }

  /// Invalidates pending results. The lower HTTP transport remains owned by
  /// its caller and may finish its bounded operation independently.
  void close() {
    if (_closed) {
      return;
    }
    _closed = true;
    _generation++;
  }

  bool _isCurrent(int operation) => !_closed && operation == _generation;

  Future<SourceRuntimeResult> _executePlan(SourceLiveSearchPlan plan) async {
    try {
      return await runtime.execute(plan.requestPlan);
    } on Object {
      final package = plan.requestPlan.installedPackage.package;
      return SourceRuntimeResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: plan.requestPlan.programId,
        status: SourceRuntimeStatus.failed,
        records: const [],
        diagnostics: [
          SourceRuntimeDiagnostic(
            code: 'source_live_search_failed',
            message: 'The live source search failed.',
          ),
        ],
        consumedSteps: 0,
        selectorMatches: 0,
      );
    }
  }

  SourceSearchNormalizationResult _normalizePlan(
    SourceLiveSearchPlan plan,
    SourceRuntimeResult runtimeResult,
  ) {
    try {
      final normalized = normalizer.normalizeSearch(
        runtimeResult: runtimeResult,
        mapping: plan.mapping,
      );
      if (!_matchesPlanIdentity(normalized, plan)) {
        return _normalizationFailure(plan, 'normalization_identity_mismatch');
      }
      if (!_hasValidShape(normalized)) {
        return _normalizationFailure(plan, 'normalization_result_invalid');
      }
      return normalized;
    } on Object {
      return _normalizationFailure(plan, 'normalization_failed');
    }
  }

  bool _matchesPlanIdentity(
    SourceSearchNormalizationResult result,
    SourceLiveSearchPlan plan,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return result.packageId == package.packageId &&
        result.packageVersion.toString() == package.version.toString() &&
        result.programId == plan.requestPlan.programId;
  }

  bool _hasValidShape(SourceSearchNormalizationResult result) {
    if (result.status == SourceSearchNormalizationStatus.available) {
      return result.results.isNotEmpty;
    }
    return result.results.isEmpty;
  }

  SourceSearchNormalizationResult _normalizationFailure(
    SourceLiveSearchPlan plan,
    String code,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return SourceSearchNormalizationResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: SourceSearchNormalizationStatus.failed,
      results: const [],
      diagnostics: [
        SourceSearchNormalizationDiagnostic(
          code: code,
          message: switch (code) {
            'normalization_identity_mismatch' =>
              'The source search result identity was invalid.',
            _ => 'The source search result could not be normalized.',
          },
        ),
      ],
    );
  }

  SourceSearchCoordinatorResult _aggregate({
    required String query,
    required Iterable<SourceSearchNormalizationResult> sourceResults,
    required Iterable<SourceSearchResult> results,
  }) {
    final sourceResultList = sourceResults.toList(growable: false);
    final resultList = results.toList(growable: false);
    final hasResults = resultList.isNotEmpty;
    final hasFailed = sourceResultList.any(
      (source) => source.status == SourceSearchNormalizationStatus.failed,
    );
    final hasBlocked = sourceResultList.any(
      (source) => switch (source.status) {
        SourceSearchNormalizationStatus.disabled ||
        SourceSearchNormalizationStatus.consentRequired ||
        SourceSearchNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = sourceResultList.any(
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
      query: query,
      status: status,
      sourceResults: sourceResultList,
      results: resultList,
      reasonCode: reasonCode,
    );
  }

  SourceSearchCoordinatorResult _staleResult(String query) {
    return _failedResult(
      query: query,
      reasonCode: _closed ? 'live_search_closed' : 'stale_search',
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

  static String _safeQuery(String value) {
    final normalized = value.trim();
    return _validQuery(normalized) ? normalized : '';
  }

  static bool _validQuery(String value) {
    return value.isNotEmpty &&
        value.length <= 128 &&
        !value.codeUnits.any(
          (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
        );
  }
}
