import '../domain/models/source_episode_coordinator_models.dart';
import '../domain/models/source_episode_normalization_models.dart';
import '../domain/models/source_models.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_episode_normalizer.dart';
import 'source_live_http_package_runtime.dart';
import 'source_live_http_request_coordinator.dart';

/// One explicit live HTTP request paired with the episode field mapping that
/// will consume its typed runtime records.
final class SourceLiveEpisodePlan {
  SourceLiveEpisodePlan({required this.requestPlan, required this.mapping});

  final SourceLiveHttpRequestPlan requestPlan;
  final SourceEpisodeFieldMapping mapping;

  String get identityKey => requestPlan.identityKey;
}

/// Composes bounded live source execution with the existing episode
/// normalizer.
///
/// Each call owns one immutable input snapshot and evaluates plans in caller
/// order. A later call supersedes an earlier pending call, and [close]
/// invalidates pending and future calls. The lower HTTP transport remains the
/// I/O lifecycle authority.
final class SourceLiveEpisodeCoordinator {
  SourceLiveEpisodeCoordinator({
    required this.runtime,
    required this.normalizer,
  });

  static const maxPlans = 32;

  final SourceLiveHttpPackageRuntime runtime;
  final SourceEpisodeNormalizer normalizer;

  var _generation = 0;
  var _closed = false;

  Future<SourceEpisodeCoordinatorResult> listEpisodes({
    required Iterable<SourceLiveEpisodePlan> plans,
  }) async {
    if (_closed) {
      return _failedResult(reasonCode: 'live_episode_closed');
    }
    final operation = ++_generation;
    final planList = <SourceLiveEpisodePlan>[];
    try {
      for (final plan in plans) {
        if (planList.length == maxPlans) {
          return _failedResult(reasonCode: 'too_many_source_plans');
        }
        planList.add(plan);
      }
    } on Object {
      return _failedResult(reasonCode: 'invalid_source_plans');
    }
    if (!_isCurrent(operation)) {
      return _staleResult();
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
      if (!_isCurrent(operation)) {
        return _staleResult();
      }

      final runtimeResult = await _executePlan(plan);
      if (!_isCurrent(operation)) {
        return _staleResult();
      }

      final normalized = _normalizePlan(plan, runtimeResult);
      if (!_isCurrent(operation)) {
        return _staleResult();
      }
      sourceResults.add(normalized);
      episodes.addAll(normalized.results);
    }

    if (!_isCurrent(operation)) {
      return _staleResult();
    }
    return _aggregate(sourceResults: sourceResults, episodes: episodes);
  }

  /// Invalidates pending results. The lower HTTP transport may finish its
  /// bounded operation independently.
  void close() {
    if (_closed) {
      return;
    }
    _closed = true;
    _generation++;
  }

  bool _isCurrent(int operation) => !_closed && operation == _generation;

  Future<SourceRuntimeResult> _executePlan(SourceLiveEpisodePlan plan) async {
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
            code: 'source_live_episode_failed',
            message: 'The live source episode listing failed.',
          ),
        ],
        consumedSteps: 0,
        selectorMatches: 0,
      );
    }
  }

  SourceEpisodeNormalizationResult _normalizePlan(
    SourceLiveEpisodePlan plan,
    SourceRuntimeResult runtimeResult,
  ) {
    try {
      final normalized = normalizer.normalizeEpisodes(
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
    SourceEpisodeNormalizationResult result,
    SourceLiveEpisodePlan plan,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return result.packageId == package.packageId &&
        result.packageVersion.toString() == package.version.toString() &&
        result.programId == plan.requestPlan.programId;
  }

  bool _hasValidShape(SourceEpisodeNormalizationResult result) {
    if (result.status == SourceEpisodeNormalizationStatus.available) {
      return result.results.isNotEmpty;
    }
    return result.results.isEmpty;
  }

  SourceEpisodeNormalizationResult _normalizationFailure(
    SourceLiveEpisodePlan plan,
    String code,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return SourceEpisodeNormalizationResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: SourceEpisodeNormalizationStatus.failed,
      results: const [],
      diagnostics: [
        SourceEpisodeNormalizationDiagnostic(
          code: code,
          message: switch (code) {
            'normalization_identity_mismatch' =>
              'The source episode result identity was invalid.',
            _ => 'The source episode result could not be normalized.',
          },
        ),
      ],
    );
  }

  SourceEpisodeCoordinatorResult _aggregate({
    required Iterable<SourceEpisodeNormalizationResult> sourceResults,
    required Iterable<SourceEpisode> episodes,
  }) {
    final sourceResultList = sourceResults.toList(growable: false);
    final episodeList = episodes.toList(growable: false);
    final hasEpisodes = episodeList.isNotEmpty;
    final hasFailed = sourceResultList.any(
      (source) => source.status == SourceEpisodeNormalizationStatus.failed,
    );
    final hasBlocked = sourceResultList.any(
      (source) => switch (source.status) {
        SourceEpisodeNormalizationStatus.disabled ||
        SourceEpisodeNormalizationStatus.consentRequired ||
        SourceEpisodeNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = sourceResultList.any(
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
      sourceResults: sourceResultList,
      episodes: episodeList,
      reasonCode: reasonCode,
    );
  }

  SourceEpisodeCoordinatorResult _staleResult() {
    return _failedResult(
      reasonCode: _closed ? 'live_episode_closed' : 'stale_episode',
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
