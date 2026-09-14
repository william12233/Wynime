import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/source_playback_route_selector.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final coordinator = _coordinator();

  test(
    'selects a live route and preserves exact package/request provenance',
    () {
      final package = _package('example.anime');
      final plan = _plan(package);
      final source = _source(plan.episode, packageId: package.packageId);
      final result = coordinator.selectRoute(
        plans: [plan],
        playableResult: _playable([
          _normalized(plan, [source]),
        ]),
      );

      expect(result.status, SourceLivePlaybackRouteStatus.selected);
      expect(result.route!.packageId, package.packageId);
      expect(result.route!.programId, 'playback');
      expect(result.route!.source, same(source));
      expect(result.route!.episode, plan.episode);
      expect(
        result.route!.installedPackage,
        same(plan.requestPlan.installedPackage),
      );
      expect(result.route!.request, same(plan.requestPlan.request));
      expect(result.selectionResults, hasLength(1));
      expect(result.toString(), isNot(contains('https://example.com/media')));
    },
  );

  test('keeps source order and selects the first valid live package route', () {
    final first = _package('first.anime');
    final second = _package('second.anime');
    final firstPlan = _plan(first);
    final secondPlan = _plan(second);
    final firstSource = _source(
      firstPlan.episode,
      packageId: first.packageId,
      key: 'primary',
    );
    final secondSource = _source(
      secondPlan.episode,
      packageId: second.packageId,
      key: 'backup',
    );

    final result = coordinator.selectRoute(
      plans: [firstPlan, secondPlan],
      playableResult: _playable([
        _normalized(firstPlan, [firstSource]),
        _normalized(secondPlan, [secondSource]),
      ]),
    );

    expect(result.status, SourceLivePlaybackRouteStatus.selected);
    expect(result.route!.packageId, first.packageId);
    expect(result.route!.source.sourceKey, 'primary');
    expect(result.selectionResults.map((selection) => selection.status), [
      SourcePlaybackRouteSelectionStatus.selected,
      SourcePlaybackRouteSelectionStatus.selected,
    ]);
  });

  test(
    'honors exact live source preference without cross-package fallback',
    () {
      final first = _package('first.anime');
      final second = _package('second.anime');
      final firstPlan = _plan(first);
      final secondPlan = _plan(second);
      final firstSource = _source(
        firstPlan.episode,
        packageId: first.packageId,
      );
      final secondPrimary = _source(
        secondPlan.episode,
        packageId: second.packageId,
        key: 'primary',
      );
      final secondBackup = _source(
        secondPlan.episode,
        packageId: second.packageId,
        key: 'backup',
      );
      final playableResult = _playable([
        _normalized(firstPlan, [firstSource]),
        _normalized(secondPlan, [secondPrimary, secondBackup]),
      ]);

      final selected = coordinator.selectRoute(
        plans: [firstPlan, secondPlan],
        playableResult: playableResult,
        preference: SourcePlaybackRoutePreference(
          packageId: second.packageId,
          packageVersion: second.version,
          programId: 'playback',
          sourceKey: 'backup',
        ),
      );
      expect(selected.status, SourceLivePlaybackRouteStatus.selected);
      expect(selected.route!.packageId, second.packageId);
      expect(selected.route!.source.sourceKey, 'backup');

      final missing = coordinator.selectRoute(
        plans: [firstPlan, secondPlan],
        playableResult: playableResult,
        preference: SourcePlaybackRoutePreference(
          packageId: second.packageId,
          packageVersion: second.version,
          programId: 'playback',
          sourceKey: 'missing',
        ),
      );
      expect(
        missing.status,
        SourceLivePlaybackRouteStatus.preferredSourceNotFound,
      );
      expect(missing.route, isNull);

      final wrongPackage = coordinator.selectRoute(
        plans: [firstPlan, secondPlan],
        playableResult: playableResult,
        preference: SourcePlaybackRoutePreference(
          packageId: 'other.anime',
          packageVersion: Version.parse('1.0.0'),
          programId: 'playback',
          sourceKey: 'backup',
        ),
      );
      expect(
        wrongPackage.status,
        SourceLivePlaybackRouteStatus.preferredSourceNotFound,
      );
      expect(wrongPackage.route, isNull);
    },
  );

  test('maps live no-source and blocked states truthfully', () {
    final cases = [
      (
        _installed(_package('consent.anime'), requiresConsent: true),
        SourcePlayableSourceNormalizationStatus.consentRequired,
        SourceLivePlaybackRouteStatus.consentRequired,
      ),
      (
        _installed(
          _package('disabled.anime'),
          status: SourcePackageStatus.disabled,
        ),
        SourcePlayableSourceNormalizationStatus.disabled,
        SourceLivePlaybackRouteStatus.disabled,
      ),
      (
        _installed(
          _package('incompatible.anime', wynimeVersionConstraint: '^2.0.0'),
        ),
        SourcePlayableSourceNormalizationStatus.incompatible,
        SourceLivePlaybackRouteStatus.incompatible,
      ),
    ];

    for (final (installed, normalizationStatus, routeStatus) in cases) {
      final plan = _plan(installed.package, installedPackage: installed);
      final result = coordinator.selectRoute(
        plans: [plan],
        playableResult: _playable([
          _normalized(plan, const [], status: normalizationStatus),
        ], status: SourcePlayableSourceCoordinatorStatus.noSources),
      );
      expect(result.status, routeStatus);
      expect(result.route, isNull);
      expect(result.reasonCode, isNotNull);
    }

    final noPlans = coordinator.selectRoute(
      plans: const [],
      playableResult: SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.noSources,
        sourceResults: const [],
        sources: const [],
        reasonCode: 'no_enabled_sources',
      ),
    );
    expect(noPlans.status, SourceLivePlaybackRouteStatus.noSources);
    expect(noPlans.reasonCode, 'no_enabled_sources');
  });

  test('rejects stale blocked status for a currently enabled package', () {
    final package = _package('enabled.anime');
    final plan = _plan(package);
    for (final status in [
      SourcePlayableSourceNormalizationStatus.disabled,
      SourcePlayableSourceNormalizationStatus.consentRequired,
      SourcePlayableSourceNormalizationStatus.incompatible,
    ]) {
      final result = coordinator.selectRoute(
        plans: [plan],
        playableResult: _playable([
          _normalized(plan, const [], status: status),
        ], status: SourcePlayableSourceCoordinatorStatus.noSources),
      );
      expect(result.status, SourceLivePlaybackRouteStatus.failed);
      expect(result.reasonCode, 'normalization_result_invalid');
      expect(result.route, isNull);
    }
  });

  test('rejects mismatched plan/result order before route selection', () {
    final first = _package('first.anime');
    final second = _package('second.anime');
    final firstPlan = _plan(first);
    final secondPlan = _plan(second);
    final firstSource = _source(firstPlan.episode, packageId: first.packageId);
    final secondSource = _source(
      secondPlan.episode,
      packageId: second.packageId,
    );

    final result = coordinator.selectRoute(
      plans: [firstPlan, secondPlan],
      playableResult: _playable([
        _normalized(secondPlan, [secondSource]),
        _normalized(firstPlan, [firstSource]),
      ]),
    );

    expect(result.status, SourceLivePlaybackRouteStatus.failed);
    expect(result.reasonCode, 'live_route_identity_mismatch');
    expect(result.route, isNull);
  });

  test(
    'rejects forged identity, invalid episode ownership and request policy',
    () {
      final package = _package('example.anime');
      final plan = _plan(package);
      final forgedIdentity = SourcePlayableSourceNormalizationResult(
        packageId: 'forged.anime',
        packageVersion: package.version,
        programId: 'playback',
        status: SourcePlayableSourceNormalizationStatus.failed,
        results: const [],
        diagnostics: const [],
      );
      final identityResult = coordinator.selectRoute(
        plans: [plan],
        playableResult: _playable([
          forgedIdentity,
        ], status: SourcePlayableSourceCoordinatorStatus.failed),
      );
      expect(identityResult.reasonCode, 'live_route_identity_mismatch');

      final invalidEpisodePlan = _plan(
        package,
        episode: _episode('other.anime'),
      );
      final invalidEpisodeResult = coordinator.selectRoute(
        plans: [invalidEpisodePlan],
        playableResult: _playable([
          _normalized(
            invalidEpisodePlan,
            const [],
            status: SourcePlayableSourceNormalizationStatus.failed,
          ),
        ], status: SourcePlayableSourceCoordinatorStatus.failed),
      );
      expect(invalidEpisodeResult.reasonCode, 'episode_source_mismatch');

      final mismatchedRequestPlan = _plan(
        package,
        request: _request(
          testSourcePolicy(budget: testSourceBudget(maxRecords: 21)),
        ),
      );
      final requestResult = coordinator.selectRoute(
        plans: [mismatchedRequestPlan],
        playableResult: _playable([
          _normalized(
            mismatchedRequestPlan,
            const [],
            status: SourcePlayableSourceNormalizationStatus.failed,
          ),
        ], status: SourcePlayableSourceCoordinatorStatus.failed),
      );
      expect(requestResult.reasonCode, 'live_route_request_mismatch');

      final missingProgramPlan = SourceLivePlayableSourcePlan(
        requestPlan: SourceLiveHttpRequestPlan(
          installedPackage: _installed(package),
          programId: 'missing',
          request: _request(package.securityPolicy),
        ),
        episode: _episode(package.packageId),
        mapping: mismatchedRequestPlan.mapping,
      );
      final missingProgramResult = coordinator.selectRoute(
        plans: [missingProgramPlan],
        playableResult: _playable([
          _normalized(
            missingProgramPlan,
            const [],
            status: SourcePlayableSourceNormalizationStatus.failed,
          ),
        ], status: SourcePlayableSourceCoordinatorStatus.failed),
      );
      expect(missingProgramResult.reasonCode, 'program_not_found');
    },
  );

  test('rejects forged candidate episode, kind, URI and duplicate key', () {
    final package = _package('example.anime');
    final plan = _plan(package);
    final cases = [
      (
        _source(_episode('other.anime'), packageId: package.packageId),
        'candidate_identity_mismatch',
      ),
      (
        _source(
          plan.episode,
          packageId: package.packageId,
          kind: WebCandidateKind.dash,
        ),
        'unsupported_candidate_kind',
      ),
      (
        _source(
          plan.episode,
          packageId: package.packageId,
          mediaUri: Uri.parse('https://unsafe.invalid/media.mp4'),
        ),
        'candidate_uri_not_allowed',
      ),
    ];

    for (final (source, reasonCode) in cases) {
      final result = coordinator.selectRoute(
        plans: [plan],
        playableResult: _playable([
          _normalized(plan, [source]),
        ]),
      );
      expect(result.status, SourceLivePlaybackRouteStatus.failed);
      expect(result.reasonCode, reasonCode);
    }

    final duplicate = _source(
      plan.episode,
      packageId: package.packageId,
      key: 'primary',
      label: 'Duplicate',
    );
    final duplicateResult = coordinator.selectRoute(
      plans: [plan],
      playableResult: _playable([
        _normalized(plan, [
          _source(plan.episode, packageId: package.packageId),
          duplicate,
        ]),
      ]),
    );
    expect(duplicateResult.reasonCode, 'duplicate_source_key');
  });

  test('rejects aggregate source leakage and status mismatch', () {
    final package = _package('example.anime');
    final plan = _plan(package);
    final source = _source(plan.episode, packageId: package.packageId);
    final normalized = _normalized(plan, [source]);
    final leaked = _source(
      plan.episode,
      packageId: package.packageId,
      key: 'leaked',
    );
    final leakedResult = coordinator.selectRoute(
      plans: [plan],
      playableResult: SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.available,
        sourceResults: [normalized],
        sources: [leaked],
      ),
    );
    expect(leakedResult.reasonCode, 'live_route_result_mismatch');

    final failedPlan = _plan(_package('failed.anime'));
    final failedNormalized = _normalized(
      failedPlan,
      const [],
      status: SourcePlayableSourceNormalizationStatus.failed,
    );
    final statusMismatch = coordinator.selectRoute(
      plans: [plan, failedPlan],
      playableResult: SourcePlayableSourceCoordinatorResult(
        status: SourcePlayableSourceCoordinatorStatus.available,
        sourceResults: [normalized, failedNormalized],
        sources: [source],
      ),
    );
    expect(statusMismatch.reasonCode, 'live_route_aggregate_invalid');
  });

  test('rejects duplicate and over-bound plans before selection', () {
    final package = _package('example.anime');
    final plan = _plan(package);
    final emptyFailed = SourcePlayableSourceCoordinatorResult(
      status: SourcePlayableSourceCoordinatorStatus.failed,
      sourceResults: const [],
      sources: const [],
      reasonCode: 'source_playable_failed',
    );

    final duplicate = coordinator.selectRoute(
      plans: [plan, plan],
      playableResult: emptyFailed,
    );
    expect(duplicate.status, SourceLivePlaybackRouteStatus.failed);
    expect(duplicate.reasonCode, 'duplicate_source_plan');

    final tooMany = coordinator.selectRoute(
      plans: List<SourceLivePlayableSourcePlan>.generate(
        33,
        (index) => _plan(_package('example$index.anime')),
      ),
      playableResult: emptyFailed,
    );
    expect(tooMany.status, SourceLivePlaybackRouteStatus.failed);
    expect(tooMany.reasonCode, 'too_many_source_plans');
  });

  test('converts selector exceptions to a safe typed live failure', () {
    final throwing = SourceLivePlaybackRouteCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
      routeCoordinator: SourcePlaybackRouteCoordinator(
        selector: const _ThrowingSelector(),
      ),
    );
    final package = _package('example.anime');
    final plan = _plan(package);
    final source = _source(plan.episode, packageId: package.packageId);
    final result = throwing.selectRoute(
      plans: [plan],
      playableResult: _playable([
        _normalized(plan, [source]),
      ]),
    );

    expect(result.status, SourceLivePlaybackRouteStatus.failed);
    expect(result.reasonCode, 'route_selection_failed');
    expect(result.toString(), isNot(contains('raw')));
  });

  test('keeps live route result selection details immutable', () {
    final package = _package('example.anime');
    final plan = _plan(package);
    final source = _source(plan.episode, packageId: package.packageId);
    final result = coordinator.selectRoute(
      plans: [plan],
      playableResult: _playable([
        _normalized(plan, [source]),
      ]),
    );

    expect(
      () => result.selectionResults.add(result.selectionResults.single),
      throwsUnsupportedError,
    );
    expect(result.toRedactedDiagnostic()['hasRoute'], isTrue);
  });
}

SourceLivePlaybackRouteCoordinator _coordinator() =>
    SourceLivePlaybackRouteCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
      routeCoordinator: SourcePlaybackRouteCoordinator(
        selector: const DeterministicSourcePlaybackRouteSelector(),
      ),
    );

SourcePlayableSourceCoordinatorResult _playable(
  Iterable<SourcePlayableSourceNormalizationResult> sourceResults, {
  SourcePlayableSourceCoordinatorStatus? status,
}) {
  final normalized = sourceResults.toList(growable: false);
  final sources = [for (final result in normalized) ...result.results];
  final aggregateStatus =
      status ??
      (sources.isEmpty
          ? SourcePlayableSourceCoordinatorStatus.notFound
          : SourcePlayableSourceCoordinatorStatus.available);
  return SourcePlayableSourceCoordinatorResult(
    status: aggregateStatus,
    sourceResults: normalized,
    sources: sources,
    reasonCode:
        aggregateStatus == SourcePlayableSourceCoordinatorStatus.available
        ? null
        : aggregateStatus == SourcePlayableSourceCoordinatorStatus.partial
        ? 'partial_source_results'
        : aggregateStatus == SourcePlayableSourceCoordinatorStatus.noSources
        ? 'no_enabled_sources'
        : aggregateStatus == SourcePlayableSourceCoordinatorStatus.failed
        ? 'source_playable_failed'
        : 'source_playable_not_found',
  );
}

SourcePlayableSourceNormalizationResult _normalized(
  SourceLivePlayableSourcePlan plan,
  Iterable<SourcePlayableSource> sources, {
  SourcePlayableSourceNormalizationStatus status =
      SourcePlayableSourceNormalizationStatus.available,
}) {
  final package = plan.requestPlan.installedPackage.package;
  return SourcePlayableSourceNormalizationResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: plan.requestPlan.programId,
    status: status,
    results: sources,
    diagnostics: const [],
  );
}

SourceLivePlayableSourcePlan _plan(
  SourcePackageManifest package, {
  InstalledSourcePackage? installedPackage,
  SourceEpisodeIdentity? episode,
  SourceHttpRequest? request,
}) => SourceLivePlayableSourcePlan(
  requestPlan: SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(package),
    programId: 'playback',
    request: request ?? _request(package.securityPolicy),
  ),
  episode: episode ?? _episode(package.packageId),
  mapping: SourcePlayableSourceFieldMapping(
    sourceKeyField: 'key',
    labelField: 'label',
    kindField: 'kind',
    mediaUriField: 'media',
    pageUriField: 'page',
  ),
);

SourceEpisodeIdentity _episode(String sourceId) => SourceEpisodeIdentity(
  sourceId: sourceId,
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: 'episode-1',
);

SourcePlayableSource _source(
  SourceEpisodeIdentity episode, {
  required String packageId,
  String key = 'primary',
  String label = 'Primary',
  WebCandidateKind kind = WebCandidateKind.video,
  Uri? mediaUri,
  Uri? pageUri,
}) => SourcePlayableSource(
  episode: episode,
  sourceKey: key,
  label: label,
  kind: kind,
  mediaUri: mediaUri ?? Uri.parse('https://example.com/media/$key.mp4'),
  pageUri: pageUri ?? Uri.parse('https://example.com/watch/episode-1'),
);

SourceHttpRequest _request(SourceSecurityPolicy policy) => SourceHttpRequest(
  uri: Uri.parse('https://example.com/playback'),
  securityPolicy: policy,
  headers: const {'accept': 'text/html'},
  timeout: const Duration(seconds: 2),
);

SourcePackageManifest _package(
  String packageId, {
  String wynimeVersionConstraint = '^1.0.0',
}) => SourcePackageManifest(
  schemaVersion: 1,
  packageId: packageId,
  displayName: packageId,
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse(wynimeVersionConstraint),
  securityPolicy: testSourcePolicy(),
  programs: [
    SourceRuleProgram(
      programId: 'playback',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.source',
      ),
      fields: [
        SourceFieldRule(
          name: 'key',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.key',
          ),
        ),
      ],
      resultLimit: 10,
    ),
  ],
);

InstalledSourcePackage _installed(
  SourcePackageManifest package, {
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => InstalledSourcePackage(
  package: package,
  status: status,
  requiresConsent: requiresConsent,
  requiresReconsent: requiresReconsent,
);

final class _ThrowingSelector implements SourcePlaybackRouteSelector {
  const _ThrowingSelector();

  @override
  SourcePlaybackRouteSelectionResult selectRoute({
    required SourcePlayableSourceNormalizationResult normalized,
    required SourceEpisodeIdentity episode,
    String? preferredSourceKey,
  }) {
    throw StateError('raw route detail');
  }
}
