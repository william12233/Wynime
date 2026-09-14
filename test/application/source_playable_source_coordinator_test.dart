import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playable_source_coordinator.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/source_package_runtime.dart';
import 'package:wynime/src/domain/services/source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('aggregates explicit playable plans in deterministic source order', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({
            'key': '${plan.packageId}-primary',
            'label': 'Primary',
            'kind': 'hls',
            'media': 'https://cdn.example.com/${plan.packageId}/master.m3u8',
            'page': 'https://example.com/watch/${plan.packageId}',
            'sourceId': 'forged.example',
          }),
        ],
      ),
    );
    final result = _coordinator(runtime).listPlayableSources(
      plans: [_plan('first.source'), _plan('second.source')],
    );

    expect(result.status, SourcePlayableSourceCoordinatorStatus.available);
    expect(result.sources.map((source) => source.sourceKey), [
      'first.source-primary',
      'second.source-primary',
    ]);
    expect(result.sources.map((source) => source.episode.sourceId), [
      'first.source',
      'second.source',
    ]);
    expect(runtime.programs, ['playback', 'playback']);
    expect(result.toString(), isNot(contains('first.source')));
    expect(result.toString(), isNot(contains('master.m3u8')));
  });

  test('preflights reachable staged consent and blocked packages', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({
            'key': 'unexpected',
            'label': 'Unexpected',
            'kind': 'hls',
            'media': 'https://cdn.example.com/unexpected.m3u8',
            'page': 'https://example.com/unexpected',
          }),
        ],
      ),
    );
    final result = _coordinator(runtime).listPlayableSources(
      plans: [
        _plan('disabled.source', status: SourcePackageStatus.disabled),
        _plan(
          'consent.source',
          status: SourcePackageStatus.disabled,
          requiresConsent: true,
        ),
        _plan(
          'reconsent.source',
          status: SourcePackageStatus.disabled,
          requiresReconsent: true,
        ),
        _plan(
          'incompatible.source',
          version: Version.parse('2.0.0'),
          wynimeVersionConstraint: VersionConstraint.parse('^2.0.0'),
        ),
      ],
    );

    expect(result.status, SourcePlayableSourceCoordinatorStatus.noSources);
    expect(result.reasonCode, 'no_enabled_sources');
    expect(result.sourceResults.map((item) => item.status), [
      SourcePlayableSourceNormalizationStatus.disabled,
      SourcePlayableSourceNormalizationStatus.consentRequired,
      SourcePlayableSourceNormalizationStatus.consentRequired,
      SourcePlayableSourceNormalizationStatus.incompatible,
    ]);
    expect(runtime.plans, isEmpty);
  });

  test('keeps playable sources while reporting a partial runtime failure', () {
    final runtime = _RecordingRuntime((plan) {
      if (plan.packageId == 'broken.source') {
        throw StateError('token=private playable response');
      }
      return _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({
            'key': 'working-primary',
            'label': 'Working',
            'kind': 'video',
            'media': 'https://cdn.example.com/working.mp4',
            'page': 'https://example.com/working',
          }),
        ],
      );
    });
    final result = _coordinator(runtime).listPlayableSources(
      plans: [_plan('working.source'), _plan('broken.source')],
    );

    expect(result.status, SourcePlayableSourceCoordinatorStatus.partial);
    expect(result.sources.single.label, 'Working');
    expect(
      result.sourceResults.last.status,
      SourcePlayableSourceNormalizationStatus.failed,
    );
    expect(result.toString(), isNot(contains('private')));
    expect(result.toString(), isNot(contains('token')));
  });

  test('returns not found when eligible sources produce no candidates', () {
    final runtime = _RecordingRuntime((plan) => _runtimeResult(plan));
    final result = _coordinator(
      runtime,
    ).listPlayableSources(plans: [_plan('empty.source')]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.notFound);
    expect(result.reasonCode, 'source_playable_not_found');
    expect(result.sources, isEmpty);
  });

  test('maps normalizer failures to a safe typed result', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({
            'key': 'primary',
            'label': 'Primary',
            'kind': 'hls',
            'media': 'https://cdn.example.com/master.m3u8',
            'page': 'https://example.com/watch',
          }),
        ],
      ),
    );
    final result = SourcePlayableSourceCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
      runtime: runtime,
      normalizer: _ThrowingNormalizer(),
    ).listPlayableSources(plans: [_plan('throwing.source')]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_playable_failed');
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_failed',
    );
    expect(result.toString(), isNot(contains('raw')));
  });

  test('rejects duplicate and over-budget requests before execution', () {
    final runtime = _RecordingRuntime((plan) => _runtimeResult(plan));
    final coordinator = _coordinator(runtime);

    final duplicate = coordinator.listPlayableSources(
      plans: [_plan('same.source'), _plan('same.source')],
    );
    expect(duplicate.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(duplicate.reasonCode, 'duplicate_source_plan');

    final tooMany = coordinator.listPlayableSources(
      plans: [for (var index = 0; index < 33; index++) _plan('source$index')],
    );
    expect(tooMany.reasonCode, 'too_many_source_plans');
    expect(runtime.plans, isEmpty);
  });

  test('rejects a forged normalized candidate identity fail closed', () {
    final package = _package('identity.source');
    final plan = _plan('identity.source');
    final forged = SourcePlayableSource(
      episode: SourceEpisodeIdentity(
        sourceId: 'other.source',
        lineId: 'line-1',
        subjectId: 'subject-1',
        episodeId: 'episode-1',
      ),
      sourceKey: 'primary',
      label: 'Forged',
      kind: WebCandidateKind.hls,
      mediaUri: Uri.parse('https://cdn.example.com/forged.m3u8'),
      pageUri: Uri.parse('https://example.com/forged'),
    );
    final result = SourcePlayableSourceCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
      runtime: _RecordingRuntime((plan) => _runtimeResult(plan)),
      normalizer: _FixedNormalizer(
        SourcePlayableSourceNormalizationResult(
          packageId: package.packageId,
          packageVersion: package.version,
          programId: plan.programId,
          status: SourcePlayableSourceNormalizationStatus.available,
          results: [forged],
          diagnostics: const [],
        ),
      ),
    ).listPlayableSources(plans: [plan]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_playable_failed');
    expect(result.sources, isEmpty);
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_candidate_invalid',
    );
  });

  test('rejects a package-mismatched episode before runtime execution', () {
    final runtime = _RecordingRuntime((plan) => _runtimeResult(plan));
    final result = _coordinator(runtime).listPlayableSources(
      plans: [_plan('mismatch.source', episodeSourceId: 'other.source')],
    );

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_playable_failed');
    expect(result.sources, isEmpty);
    expect(runtime.plans, isEmpty);
  });

  test(
    'rejects whitespace-padded episode identity before runtime execution',
    () {
      // SourceEpisodeIdentity asserts reject an all-whitespace value in debug;
      // this padded value exercises the same trim boundary in every test mode.
      final runtime = _RecordingRuntime((plan) => _runtimeResult(plan));
      final result = _coordinator(runtime).listPlayableSources(
        plans: [_plan('invalid.source', episodeLineId: ' line-1 ')],
      );

      expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
      expect(result.reasonCode, 'source_playable_failed');
      expect(result.sources, isEmpty);
      expect(runtime.plans, isEmpty);
    },
  );

  test('rejects a normalizer candidate outside the package URI policy', () {
    final package = _package('policy.source');
    final plan = _plan('policy.source');
    final outside = SourcePlayableSource(
      episode: plan.episode,
      sourceKey: 'outside',
      label: 'Outside',
      kind: WebCandidateKind.hls,
      mediaUri: Uri.parse('https://outside.invalid/master.m3u8'),
      pageUri: Uri.parse('https://example.com/watch'),
    );
    final result = SourcePlayableSourceCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
      runtime: _RecordingRuntime((plan) => _runtimeResult(plan)),
      normalizer: _FixedNormalizer(
        SourcePlayableSourceNormalizationResult(
          packageId: package.packageId,
          packageVersion: package.version,
          programId: plan.programId,
          status: SourcePlayableSourceNormalizationStatus.available,
          results: [outside],
          diagnostics: const [],
        ),
      ),
    ).listPlayableSources(plans: [plan]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(result.sources, isEmpty);
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_candidate_invalid',
    );
    expect(result.toString(), isNot(contains('outside.invalid')));
  });

  test('keeps aggregate sources immutable and episode identity explicit', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({
            'key': 'primary',
            'label': 'Explicit',
            'kind': 'audio',
            'media': 'https://cdn.example.com/audio.mp3',
            'page': 'https://example.com/watch/episode-7',
          }),
        ],
      ),
    );
    final result = _coordinator(
      runtime,
    ).listPlayableSources(plans: [_plan('immutable.source')]);

    expect(
      () => result.sources.add(result.sources.single),
      throwsUnsupportedError,
    );
    expect(result.playableSources, same(result.sources));
    expect(result.sources.single.episode.lineId, 'line-1');
    expect(result.sources.single.episode.subjectId, 'subject-42');
    expect(result.sources.single.episode.episodeId, 'episode-7');
  });
}

SourcePlayableSourceCoordinator _coordinator(SourcePackageRuntime runtime) {
  return SourcePlayableSourceCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
    runtime: runtime,
    normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
  );
}

SourcePlayableSourcePlan _plan(
  String packageId, {
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
  String? episodeSourceId,
  String? episodeLineId,
  Version? version,
  VersionConstraint? wynimeVersionConstraint,
}) {
  return SourcePlayableSourcePlan(
    installedPackage: InstalledSourcePackage(
      package: _package(
        packageId,
        version: version,
        wynimeVersionConstraint: wynimeVersionConstraint,
      ),
      status: status,
      requiresConsent: requiresConsent,
      requiresReconsent: requiresReconsent,
    ),
    programId: 'playback',
    fixture: SourceFixture(
      initialUri: Uri.parse('https://example.com/playback'),
      body: '<div class="source"><span>value</span></div>',
    ),
    episode: SourceEpisodeIdentity(
      sourceId: episodeSourceId ?? packageId,
      lineId: episodeLineId ?? 'line-1',
      subjectId: 'subject-42',
      episodeId: 'episode-7',
    ),
    mapping: SourcePlayableSourceFieldMapping(
      sourceKeyField: 'key',
      labelField: 'label',
      kindField: 'kind',
      mediaUriField: 'media',
      pageUriField: 'page',
    ),
  );
}

SourcePackageManifest _package(
  String packageId, {
  Version? version,
  VersionConstraint? wynimeVersionConstraint,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: packageId,
    version: version ?? Version.parse('1.0.0'),
    wynimeVersionConstraint:
        wynimeVersionConstraint ?? VersionConstraint.parse('^1.0.0'),
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
          for (final name in ['key', 'label', 'kind', 'media', 'page'])
            SourceFieldRule(
              name: name,
              valueKind: SourceValueKind.raw,
              required: true,
            ),
        ],
        resultLimit: 20,
      ),
    ],
  );
}

SourceRuntimeResult _runtimeResult(
  _RuntimePlan plan, {
  Iterable<SourceRuntimeRecord> records = const [],
}) {
  return SourceRuntimeResult(
    packageId: plan.packageId,
    packageVersion: plan.packageVersion,
    programId: plan.programId,
    status: records.isEmpty
        ? SourceRuntimeStatus.notFound
        : SourceRuntimeStatus.available,
    records: records,
    diagnostics: const [],
    consumedSteps: 1,
    selectorMatches: records.length,
  );
}

final class _RuntimePlan {
  const _RuntimePlan(this.packageId, this.packageVersion, this.programId);

  final String packageId;
  final Version packageVersion;
  final String programId;
}

final class _RecordingRuntime implements SourcePackageRuntime {
  _RecordingRuntime(this.callback);

  final SourceRuntimeResult Function(_RuntimePlan plan) callback;
  final programs = <String>[];
  final plans = <String>[];

  @override
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  }) {
    programs.add(programId);
    final plan = _RuntimePlan(
      installedPackage.package.packageId,
      installedPackage.package.version,
      programId,
    );
    plans.add(plan.packageId);
    return callback(plan);
  }
}

final class _ThrowingNormalizer implements SourcePlayableSourceNormalizer {
  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) {
    throw StateError('raw normalization detail');
  }
}

final class _FixedNormalizer implements SourcePlayableSourceNormalizer {
  const _FixedNormalizer(this.result);

  final SourcePlayableSourceNormalizationResult result;

  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) => result;
}
