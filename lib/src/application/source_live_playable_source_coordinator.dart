import '../domain/models/source_identity.dart';
import '../domain/models/source_playable_normalization_models.dart';
import '../domain/models/source_playable_source_coordinator_models.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_playable_source_normalizer.dart';
import 'source_live_http_package_runtime.dart';
import 'source_live_http_request_coordinator.dart';

/// One explicit live HTTP request paired with the episode identity and
/// playable-source field mapping that will consume its typed runtime records.
final class SourceLivePlayableSourcePlan {
  SourceLivePlayableSourcePlan({
    required this.requestPlan,
    required this.episode,
    required this.mapping,
  });

  final SourceLiveHttpRequestPlan requestPlan;
  final SourceEpisodeIdentity episode;
  final SourcePlayableSourceFieldMapping mapping;

  String get identityKey =>
      '${requestPlan.identityKey}/${_identityPart(episode.sourceId)}/'
      '${_identityPart(episode.lineId)}/${_identityPart(episode.subjectId)}/'
      '${_identityPart(episode.episodeId)}';

  static String _identityPart(String value) => '${value.length}:$value';
}

/// Optional browser-backed fallback for a playable page whose static HTTP
/// response does not contain the hydrated media element.
///
/// The fallback receives the same admitted plan and returns only the typed
/// declarative runtime result. It cannot change request construction or
/// package policy.
abstract interface class SourceLivePlayableDocumentFallback {
  Future<SourceRuntimeResult> capture(SourceLivePlayableSourcePlan plan);
}

/// Composes bounded live source execution with the existing playable-source
/// normalizer.
///
/// Each call owns one immutable input snapshot and evaluates plans in caller
/// order. A later call supersedes an earlier pending call, and [close]
/// invalidates pending and future calls. The lower HTTP transport remains the
/// I/O lifecycle authority.
final class SourceLivePlayableSourceCoordinator {
  SourceLivePlayableSourceCoordinator({
    required this.runtime,
    required this.normalizer,
    this.documentFallback,
  });

  static const maxPlans = 32;

  final SourceLiveHttpPackageRuntime runtime;
  final SourcePlayableSourceNormalizer normalizer;
  final SourceLivePlayableDocumentFallback? documentFallback;

  var _generation = 0;
  var _closed = false;

  Future<SourcePlayableSourceCoordinatorResult> listPlayableSources({
    required Iterable<SourceLivePlayableSourcePlan> plans,
  }) async {
    if (_closed) {
      return _failedResult(reasonCode: 'live_playable_closed');
    }
    final operation = ++_generation;
    final planList = <SourceLivePlayableSourcePlan>[];
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
      if (!_isCurrent(operation)) {
        return _staleResult();
      }

      final preflight = _preflightPlan(plan);
      if (preflight != null) {
        sourceResults.add(preflight);
        continue;
      }

      var runtimeResult = await _executePlan(plan);
      if (!_isCurrent(operation)) {
        return _staleResult();
      }

      var normalized = _normalizePlan(plan, runtimeResult);
      if (normalized.results.isEmpty &&
          documentFallback != null &&
          (runtimeResult.status == SourceRuntimeStatus.available ||
              runtimeResult.status == SourceRuntimeStatus.notFound)) {
        runtimeResult = await documentFallback!.capture(plan);
        if (!_isCurrent(operation)) {
          return _staleResult();
        }
        normalized = _normalizePlan(plan, runtimeResult);
      }
      if (!_isCurrent(operation)) {
        return _staleResult();
      }
      sourceResults.add(normalized);
      sources.addAll(normalized.results);
    }

    if (!_isCurrent(operation)) {
      return _staleResult();
    }
    return _aggregate(sourceResults: sourceResults, sources: sources);
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

  SourcePlayableSourceNormalizationResult? _preflightPlan(
    SourceLivePlayableSourcePlan plan,
  ) {
    if (!_validEpisodeIdentity(plan.episode)) {
      return _normalizationFailure(plan, 'invalid_episode_identity');
    }
    if (plan.episode.sourceId !=
        plan.requestPlan.installedPackage.package.packageId) {
      return _normalizationFailure(plan, 'episode_source_mismatch');
    }
    return null;
  }

  Future<SourceRuntimeResult> _executePlan(
    SourceLivePlayableSourcePlan plan,
  ) async {
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
            code: 'source_live_playable_failed',
            message: 'The live source playable listing failed.',
          ),
        ],
        consumedSteps: 0,
        selectorMatches: 0,
      );
    }
  }

  SourcePlayableSourceNormalizationResult _normalizePlan(
    SourceLivePlayableSourcePlan plan,
    SourceRuntimeResult runtimeResult,
  ) {
    try {
      final normalized = normalizer.normalizePlayableSources(
        package: plan.requestPlan.installedPackage.package,
        runtimeResult: runtimeResult,
        episode: plan.episode,
        mapping: plan.mapping,
      );
      if (!_matchesPlanIdentity(normalized, plan)) {
        return _normalizationFailure(plan, 'normalization_identity_mismatch');
      }
      if (!_hasValidShape(normalized)) {
        return _normalizationFailure(plan, 'normalization_result_invalid');
      }
      if (normalized.status ==
              SourcePlayableSourceNormalizationStatus.available &&
          normalized.results.any(
            (source) => !_isValidCandidate(source, plan),
          )) {
        return _normalizationFailure(plan, 'normalization_candidate_invalid');
      }
      return normalized;
    } on Object {
      return _normalizationFailure(plan, 'normalization_failed');
    }
  }

  bool _matchesPlanIdentity(
    SourcePlayableSourceNormalizationResult result,
    SourceLivePlayableSourcePlan plan,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return result.packageId == package.packageId &&
        result.packageVersion.toString() == package.version.toString() &&
        result.programId == plan.requestPlan.programId;
  }

  bool _isValidCandidate(
    SourcePlayableSource source,
    SourceLivePlayableSourcePlan plan,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return source.episode == plan.episode &&
        source.episode.sourceId == package.packageId &&
        _isSupportedCandidate(source) &&
        package.securityPolicy.allowsUri(source.mediaUri) &&
        package.securityPolicy.allowsUri(source.pageUri);
  }

  bool _hasValidShape(SourcePlayableSourceNormalizationResult result) {
    if (result.status == SourcePlayableSourceNormalizationStatus.available) {
      return result.results.isNotEmpty;
    }
    return result.results.isEmpty;
  }

  SourcePlayableSourceNormalizationResult _normalizationFailure(
    SourceLivePlayableSourcePlan plan,
    String code,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    return SourcePlayableSourceNormalizationResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: SourcePlayableSourceNormalizationStatus.failed,
      results: const [],
      diagnostics: [
        SourcePlayableSourceNormalizationDiagnostic(
          code: code,
          message: switch (code) {
            'invalid_episode_identity' =>
              'The requested episode identity is invalid.',
            'episode_source_mismatch' =>
              'The requested episode does not belong to this source package.',
            'normalization_identity_mismatch' =>
              'The source playable result identity was invalid.',
            'normalization_candidate_invalid' =>
              'The normalized playable candidate was invalid.',
            'normalization_result_invalid' =>
              'The source playable result had an invalid shape.',
            _ => 'The source playable result could not be normalized.',
          },
        ),
      ],
    );
  }

  SourcePlayableSourceCoordinatorResult _aggregate({
    required Iterable<SourcePlayableSourceNormalizationResult> sourceResults,
    required Iterable<SourcePlayableSource> sources,
  }) {
    final sourceResultList = sourceResults.toList(growable: false);
    final sourceList = sources.toList(growable: false);
    final hasSources = sourceList.isNotEmpty;
    final hasFailed = sourceResultList.any(
      (source) =>
          source.status == SourcePlayableSourceNormalizationStatus.failed,
    );
    final hasBlocked = sourceResultList.any(
      (source) => switch (source.status) {
        SourcePlayableSourceNormalizationStatus.disabled ||
        SourcePlayableSourceNormalizationStatus.consentRequired ||
        SourcePlayableSourceNormalizationStatus.incompatible => true,
        _ => false,
      },
    );
    final hasEligibleSource = sourceResultList.any(
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
      sourceResults: sourceResultList,
      sources: sourceList,
      reasonCode: reasonCode,
    );
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

  SourcePlayableSourceCoordinatorResult _staleResult() {
    return _failedResult(
      reasonCode: _closed ? 'live_playable_closed' : 'stale_playable',
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
