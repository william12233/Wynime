import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_episode_coordinator.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/domain/models/source_episode_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_episode_normalization_models.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/services/source_episode_normalizer.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_episode_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'composes ordered live packages into normalized episode results',
    () async {
      final first = _package('first.anime');
      final second = _package('second.anime');
      final transport = _QueueTransport([
        _success(
          _request(),
          '<article class="item">'
          '<span class="line">line-first</span>'
          '<span class="subject">subject-first</span>'
          '<span class="episode">1</span>'
          '<h2>First episode</h2></article>',
        ),
        _success(
          _request(),
          '<article class="item">'
          '<span class="line">line-second</span>'
          '<span class="subject">subject-second</span>'
          '<span class="episode">2</span>'
          '<h2>Second episode</h2></article>',
        ),
      ]);
      final subject = _subject(transport);

      final result = await subject.listEpisodes(
        plans: [_plan(first), _plan(second)],
      );

      expect(result.status, SourceEpisodeCoordinatorStatus.available);
      expect(result.episodes.map((value) => value.identity.sourceId), [
        'first.anime',
        'second.anime',
      ]);
      expect(result.episodes.map((value) => value.identity.episodeId), [
        '1',
        '2',
      ]);
      expect(result.episodes.map((value) => value.title), [
        'First episode',
        'Second episode',
      ]);
      expect(transport.requests, hasLength(2));
    },
  );

  test(
    'live package admission failure stays per-source and becomes partial',
    () async {
      final enabled = _package('enabled.anime');
      final disabled = _package('disabled.anime');
      final transport = _QueueTransport([
        _success(
          _request(),
          '<article class="item">'
          '<span class="line">line-enabled</span>'
          '<span class="subject">subject-enabled</span>'
          '<span class="episode">1</span>'
          '<h2>Enabled</h2></article>',
        ),
      ]);
      final result = await _subject(transport).listEpisodes(
        plans: [
          _plan(enabled),
          _plan(
            disabled,
            installedPackage: _installed(
              disabled,
              status: SourcePackageStatus.disabled,
            ),
          ),
        ],
      );

      expect(result.status, SourceEpisodeCoordinatorStatus.partial);
      expect(result.episodes.single.title, 'Enabled');
      expect(
        result.sourceResults[1].status,
        SourceEpisodeNormalizationStatus.disabled,
      );
      expect(transport.requests, hasLength(1));
    },
  );

  test('duplicate plans and plan limits short-circuit live I/O', () async {
    final package = _package('example.anime');
    final transport = _QueueTransport([_success(_request(), 'unused')]);
    final subject = _subject(transport);

    final duplicate = await subject.listEpisodes(
      plans: [_plan(package), _plan(package)],
    );
    expect(duplicate.reasonCode, 'duplicate_source_plan');
    expect(transport.requests, isEmpty);

    final tooMany = await subject.listEpisodes(
      plans: List<SourceLiveEpisodePlan>.generate(
        33,
        (index) => _plan(_package('example$index.anime')),
      ),
    );
    expect(tooMany.reasonCode, 'too_many_source_plans');
    expect(transport.requests, isEmpty);
  });

  test('normalizer identity mismatch fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLiveEpisodeCoordinator(
      runtime: runtime.runtime,
      normalizer: _ForgedNormalizer(),
    );

    final result = await subject.listEpisodes(plans: [_plan(package)]);

    expect(result.status, SourceEpisodeCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_episode_failed');
    expect(
      result.sourceResults.single.status,
      SourceEpisodeNormalizationStatus.failed,
    );
    expect(result.sourceResults.single.results, isEmpty);
  });

  test('invalid normalized result shape fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLiveEpisodeCoordinator(
      runtime: runtime.runtime,
      normalizer: _InvalidShapeNormalizer(),
    );

    final result = await subject.listEpisodes(plans: [_plan(package)]);

    expect(result.status, SourceEpisodeCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.status,
      SourceEpisodeNormalizationStatus.failed,
    );
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_result_invalid',
    );
    expect(result.episodes, isEmpty);
  });

  test('normalizer exceptions become safe typed failures', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLiveEpisodeCoordinator(
      runtime: runtime.runtime,
      normalizer: _ThrowingNormalizer(),
    );

    final result = await subject.listEpisodes(plans: [_plan(package)]);

    expect(result.status, SourceEpisodeCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_failed',
    );
    expect(result.toString(), isNot(contains('private')));
  });

  test(
    'a newer episode listing supersedes an older pending response',
    () async {
      final package = _package('example.anime');
      final transport = _DeferredTransport();
      final subject = _subject(transport);

      final first = subject.listEpisodes(plans: [_plan(package)]);
      await transport.firstStarted.future;
      final second = subject.listEpisodes(plans: [_plan(package)]);
      await transport.secondStarted.future;

      transport.completeFirst(
        _success(
          _request(),
          '<article class="item">'
          '<span class="line">line</span>'
          '<span class="subject">subject</span>'
          '<span class="episode">1</span>'
          '<h2>Old</h2></article>',
        ),
      );
      final stale = await first;
      expect(stale.status, SourceEpisodeCoordinatorStatus.failed);
      expect(stale.reasonCode, 'stale_episode');
      expect(stale.episodes, isEmpty);

      transport.completeSecond(
        _success(
          _request(),
          '<article class="item">'
          '<span class="line">line</span>'
          '<span class="subject">subject</span>'
          '<span class="episode">1</span>'
          '<h2>New</h2></article>',
        ),
      );
      final current = await second;
      expect(current.status, SourceEpisodeCoordinatorStatus.available);
      expect(current.episodes.single.title, 'New');
    },
  );

  test(
    'close invalidates pending and future listings without returning episodes',
    () async {
      final package = _package('example.anime');
      final transport = _DeferredTransport();
      final subject = _subject(transport);

      final pending = subject.listEpisodes(plans: [_plan(package)]);
      await transport.firstStarted.future;
      subject.close();
      transport.completeFirst(
        _success(
          _request(),
          '<article class="item">'
          '<span class="line">line</span>'
          '<span class="subject">subject</span>'
          '<span class="episode">1</span>'
          '<h2>Late</h2></article>',
        ),
      );

      final closed = await pending;
      expect(closed.status, SourceEpisodeCoordinatorStatus.failed);
      expect(closed.reasonCode, 'live_episode_closed');
      expect(closed.episodes, isEmpty);

      final afterClose = await subject.listEpisodes(plans: [_plan(package)]);
      expect(afterClose.reasonCode, 'live_episode_closed');
      expect(afterClose.episodes, isEmpty);
    },
  );
}

_RuntimeSubject _subject(SourceHttpTransport transport) {
  final runtime = SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: Version.parse('1.0.0'),
      ),
      transport: transport,
    ),
    fixtureRuntime: DeclarativeSourcePackageRuntime(
      wynimeVersion: Version.parse('1.0.0'),
    ),
  );
  return _RuntimeSubject(
    runtime: runtime,
    coordinator: SourceLiveEpisodeCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourceEpisodeNormalizer(),
    ),
  );
}

SourceLiveEpisodePlan _plan(
  SourcePackageManifest package, {
  InstalledSourcePackage? installedPackage,
}) => SourceLiveEpisodePlan(
  requestPlan: SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(package),
    programId: 'episodes',
    request: _request(),
  ),
  mapping: SourceEpisodeFieldMapping(
    lineIdField: 'lineId',
    subjectIdField: 'subjectId',
    episodeIdField: 'episodeId',
    titleField: 'title',
  ),
);

SourceHttpRequest _request() => SourceHttpRequest(
  uri: Uri.parse('https://example.com/episodes'),
  securityPolicy: testSourcePolicy(),
  headers: const {'accept': 'text/html'},
  timeout: const Duration(seconds: 2),
);

SourceHttpTransportResult _success(SourceHttpRequest request, String body) =>
    SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: request.uri,
        redirectChain: const [],
        body: body,
      ),
    );

SourcePackageManifest _package(String packageId) => SourcePackageManifest(
  schemaVersion: 1,
  packageId: packageId,
  displayName: packageId,
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
  securityPolicy: testSourcePolicy(),
  programs: [
    SourceRuleProgram(
      programId: 'episodes',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.item',
      ),
      fields: [
        SourceFieldRule(
          name: 'lineId',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.line',
          ),
        ),
        SourceFieldRule(
          name: 'subjectId',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.subject',
          ),
        ),
        SourceFieldRule(
          name: 'episodeId',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.episode',
          ),
        ),
        SourceFieldRule(
          name: 'title',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: 'h2',
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
}) => InstalledSourcePackage(
  package: package,
  status: status,
  requiresConsent: false,
  requiresReconsent: false,
);

final class _RuntimeSubject {
  const _RuntimeSubject({required this.runtime, required this.coordinator});

  final SourceLiveHttpPackageRuntime runtime;
  final SourceLiveEpisodeCoordinator coordinator;

  Future<SourceEpisodeCoordinatorResult> listEpisodes({
    required Iterable<SourceLiveEpisodePlan> plans,
  }) => coordinator.listEpisodes(plans: plans);

  void close() => coordinator.close();
}

final class _QueueTransport implements SourceHttpTransport {
  _QueueTransport(this.results);

  final List<SourceHttpTransportResult> results;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    return results.removeAt(0);
  }

  @override
  Future<void> close() async {}
}

final class _DeferredTransport implements SourceHttpTransport {
  final firstStarted = Completer<void>();
  final secondStarted = Completer<void>();
  final _first = Completer<SourceHttpTransportResult>();
  final _second = Completer<SourceHttpTransportResult>();
  var _calls = 0;

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) {
    _calls++;
    if (_calls == 1) {
      firstStarted.complete();
      return _first.future;
    }
    secondStarted.complete();
    return _second.future;
  }

  void completeFirst(SourceHttpTransportResult result) =>
      _first.complete(result);

  void completeSecond(SourceHttpTransportResult result) =>
      _second.complete(result);

  @override
  Future<void> close() async {}
}

final class _ForgedNormalizer implements SourceEpisodeNormalizer {
  @override
  SourceEpisodeNormalizationResult normalizeEpisodes({
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeFieldMapping mapping,
  }) => SourceEpisodeNormalizationResult(
    packageId: 'forged.package',
    packageVersion: runtimeResult.packageVersion,
    programId: runtimeResult.programId,
    status: SourceEpisodeNormalizationStatus.available,
    results: [
      SourceEpisode(
        identity: SourceEpisodeIdentity(
          sourceId: 'forged.package',
          lineId: 'forged-line',
          subjectId: 'forged-subject',
          episodeId: 'forged-episode',
        ),
        title: 'Forged',
      ),
    ],
    diagnostics: const [],
  );
}

final class _InvalidShapeNormalizer implements SourceEpisodeNormalizer {
  @override
  SourceEpisodeNormalizationResult normalizeEpisodes({
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeFieldMapping mapping,
  }) => SourceEpisodeNormalizationResult(
    packageId: runtimeResult.packageId,
    packageVersion: runtimeResult.packageVersion,
    programId: runtimeResult.programId,
    status: SourceEpisodeNormalizationStatus.available,
    results: const [],
    diagnostics: const [],
  );
}

final class _ThrowingNormalizer implements SourceEpisodeNormalizer {
  @override
  SourceEpisodeNormalizationResult normalizeEpisodes({
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeFieldMapping mapping,
  }) {
    throw StateError('private episode normalization detail');
  }
}
