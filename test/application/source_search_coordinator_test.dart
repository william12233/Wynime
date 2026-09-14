import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_search_coordinator.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_search_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/services/source_package_runtime.dart';
import 'package:wynime/src/domain/services/source_search_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('aggregates explicit package plans in deterministic order', () {
    final runtime = _RecordingRuntime(
      (plan) => SourceRuntimeResult(
        packageId: plan.packageId,
        packageVersion: plan.packageVersion,
        programId: plan.programId,
        status: SourceRuntimeStatus.available,
        records: [
          SourceRuntimeRecord({
            'subject': plan.packageId,
            'title': plan.packageId,
          }),
        ],
        diagnostics: const [],
        consumedSteps: 1,
        selectorMatches: 1,
      ),
    );
    final coordinator = _coordinator(runtime);

    final result = coordinator.search(
      query: 'anime',
      plans: [_plan('first.source'), _plan('second.source')],
    );

    expect(result.status, SourceSearchCoordinatorStatus.available);
    expect(result.results.map((item) => item.sourceId), [
      'first.source',
      'second.source',
    ]);
    expect(runtime.programs, ['search', 'search']);
    expect(result.toString(), isNot(contains('anime')));
  });

  test('preflights disabled, consent and incompatible packages', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({'subject': 'unexpected', 'title': 'unexpected'}),
        ],
      ),
    );
    final coordinator = _coordinator(runtime);

    final result = coordinator.search(
      query: 'anime',
      plans: [
        _plan('disabled.source', status: SourcePackageStatus.disabled),
        _plan(
          'consent.source',
          status: SourcePackageStatus.disabled,
          requiresConsent: true,
        ),
        _plan(
          'incompatible.source',
          version: Version.parse('2.0.0'),
          wynimeVersionConstraint: VersionConstraint.parse('^2.0.0'),
        ),
      ],
    );

    expect(result.status, SourceSearchCoordinatorStatus.noSources);
    expect(result.reasonCode, 'no_enabled_sources');
    expect(result.sourceResults.map((item) => item.status), [
      SourceSearchNormalizationStatus.disabled,
      SourceSearchNormalizationStatus.consentRequired,
      SourceSearchNormalizationStatus.incompatible,
    ]);
    expect(runtime.plans, isEmpty);
  });

  test('keeps results while reporting a partial source failure', () {
    final runtime = _RecordingRuntime((plan) {
      if (plan.packageId == 'broken.source') {
        throw StateError('token=private response');
      }
      return _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({'subject': '1', 'title': 'Working'}),
        ],
      );
    });
    final result = _coordinator(runtime).search(
      query: 'anime',
      plans: [_plan('working.source'), _plan('broken.source')],
    );

    expect(result.status, SourceSearchCoordinatorStatus.partial);
    expect(result.results.single.title, 'Working');
    expect(
      result.sourceResults.last.status,
      SourceSearchNormalizationStatus.failed,
    );
    expect(result.toString(), isNot(contains('private')));
    expect(result.toString(), isNot(contains('token')));
  });

  test('returns not found when eligible sources produce no rows', () {
    final runtime = _RecordingRuntime((plan) => _runtimeResult(plan));
    final result = _coordinator(
      runtime,
    ).search(query: 'missing', plans: [_plan('empty.source')]);

    expect(result.status, SourceSearchCoordinatorStatus.notFound);
    expect(result.reasonCode, 'source_search_not_found');
    expect(result.results, isEmpty);
  });

  test('maps normalizer failures to a safe typed result', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({'subject': '1', 'title': 'Title'}),
        ],
      ),
    );
    final normalizer = _ThrowingNormalizer();
    final result = SourceSearchCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
      runtime: runtime,
      normalizer: normalizer,
    ).search(query: 'anime', plans: [_plan('throwing.source')]);

    expect(result.status, SourceSearchCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_search_failed');
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_failed',
    );
    expect(result.toString(), isNot(contains('raw')));
  });

  test(
    'rejects invalid, duplicate and over-budget requests before execution',
    () {
      final runtime = _RecordingRuntime((plan) => _runtimeResult(plan));
      final coordinator = _coordinator(runtime);

      final invalid = coordinator.search(
        query: ' bad\nquery ',
        plans: [_plan('one.source')],
      );
      expect(invalid.status, SourceSearchCoordinatorStatus.failed);
      expect(invalid.reasonCode, 'invalid_query');

      final duplicate = coordinator.search(
        query: 'anime',
        plans: [_plan('same.source'), _plan('same.source')],
      );
      expect(duplicate.reasonCode, 'duplicate_source_plan');

      final tooMany = coordinator.search(
        query: 'anime',
        plans: [for (var index = 0; index < 33; index++) _plan('source$index')],
      );
      expect(tooMany.reasonCode, 'too_many_source_plans');
      expect(runtime.plans, isEmpty);
    },
  );

  test('keeps coordinator results immutable and does not infer mappings', () {
    final runtime = _RecordingRuntime(
      (plan) => _runtimeResult(
        plan,
        records: [
          SourceRuntimeRecord({'subject': '42', 'title': 'Explicit'}),
        ],
      ),
    );
    final result = _coordinator(
      runtime,
    ).search(query: 'anime', plans: [_plan('immutable.source')]);

    expect(
      () => result.results.add(result.results.single),
      throwsUnsupportedError,
    );
    expect(result.sourceResults.single.results.single.subjectId, '42');
    expect(result.sourceResults.single.results.single.title, 'Explicit');
  });
}

SourceSearchCoordinator _coordinator(SourcePackageRuntime runtime) {
  return SourceSearchCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
    runtime: runtime,
    normalizer: const DeclarativeSourceSearchNormalizer(),
  );
}

SourceSearchPlan _plan(
  String packageId, {
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  Version? version,
  VersionConstraint? wynimeVersionConstraint,
}) {
  return SourceSearchPlan(
    installedPackage: InstalledSourcePackage(
      package: _package(
        packageId,
        version: version,
        wynimeVersionConstraint: wynimeVersionConstraint,
      ),
      status: status,
      requiresConsent: requiresConsent,
      requiresReconsent: false,
    ),
    programId: 'search',
    fixture: SourceFixture(
      initialUri: Uri.parse('https://example.com/search'),
      body: '<div class="item"><span>value</span></div>',
    ),
    mapping: SourceSearchFieldMapping(
      subjectIdField: 'subject',
      titleField: 'title',
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
        programId: 'search',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: '.item',
        ),
        fields: [
          SourceFieldRule(
            name: 'subject',
            valueKind: SourceValueKind.raw,
            required: true,
          ),
          SourceFieldRule(
            name: 'title',
            valueKind: SourceValueKind.raw,
            required: true,
          ),
        ],
        resultLimit: 1,
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

final class _ThrowingNormalizer implements SourceSearchNormalizer {
  @override
  SourceSearchNormalizationResult normalizeSearch({
    required SourceRuntimeResult runtimeResult,
    required SourceSearchFieldMapping mapping,
  }) {
    throw StateError('raw normalization detail');
  }
}
