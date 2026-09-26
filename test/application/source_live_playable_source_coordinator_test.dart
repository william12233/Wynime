import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/domain/services/source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'composes ordered live packages into normalized playable sources',
    () async {
      final first = _package('first.anime');
      final second = _package('second.anime');
      final transport = _QueueTransport([
        _success(
          _request(),
          '<article class="source">'
          '<span class="key">primary</span>'
          '<span class="label">1080p</span>'
          '<span class="kind">hls</span>'
          '<span class="media">https://example.com/media/first.m3u8</span>'
          '<span class="page">https://example.com/watch/first</span>'
          '</article>',
        ),
        _success(
          _request(),
          '<article class="source">'
          '<span class="key">secondary</span>'
          '<span class="label">Direct MP4</span>'
          '<span class="kind">video</span>'
          '<span class="media">https://example.com/media/second.mp4</span>'
          '<span class="page">https://example.com/watch/second</span>'
          '</article>',
        ),
      ]);
      final normalizer = _CountingNormalizer();
      final subject = _subject(transport, normalizer: normalizer);

      final result = await subject.listPlayableSources(
        plans: [_plan(first), _plan(second)],
      );

      expect(result.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(result.sources.map((value) => value.sourceKey), [
        'primary',
        'secondary',
      ]);
      expect(result.sources.map((value) => value.episode.sourceId), [
        'first.anime',
        'second.anime',
      ]);
      expect(normalizer.calls, 2);
      expect(transport.requests, hasLength(2));
    },
  );

  test(
    'uses the document fallback only after typed static not-found',
    () async {
      final package = _package('example.anime');
      final transport = _QueueTransport([
        _success(_request(), '<main></main>'),
      ]);
      final fallback = _FallbackRuntime(
        SourceRuntimeResult(
          packageId: package.packageId,
          packageVersion: package.version,
          programId: 'playback',
          status: SourceRuntimeStatus.available,
          records: [
            SourceRuntimeRecord({
              'key': 'rendered',
              'label': 'Rendered',
              'kind': 'video',
              'media': 'https://example.com/media/rendered.mp4',
              'page': 'https://example.com/watch/rendered',
            }),
          ],
          diagnostics: const [],
          consumedSteps: 1,
          selectorMatches: 1,
        ),
      );
      final subject = _subject(transport, fallback: fallback);

      final result = await subject.listPlayableSources(plans: [_plan(package)]);

      expect(result.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(result.sources.single.sourceKey, 'rendered');
      expect(fallback.calls, 1);
      expect(transport.requests, hasLength(1));
    },
  );

  test(
    'uses browser fallback when static result has no usable candidate',
    () async {
      final package = _package('example.anime');
      final transport = _QueueTransport([
        _success(
          _request(),
          '<article class="source">'
          '<span class="key">invalid</span>'
          '<span class="label">Invalid static</span>'
          '<span class="kind">video</span>'
          '<span class="media">https://outside.invalid/media.mp4</span>'
          '<span class="page">https://example.com/watch/static</span>'
          '</article>',
        ),
      ]);
      final fallback = _FallbackRuntime(
        SourceRuntimeResult(
          packageId: package.packageId,
          packageVersion: package.version,
          programId: 'playback',
          status: SourceRuntimeStatus.available,
          records: [
            SourceRuntimeRecord({
              'key': 'captured',
              'label': 'Captured',
              'kind': 'video',
              'media': 'https://example.com/media/captured.mp4',
              'page': 'https://example.com/watch/captured',
            }),
          ],
          diagnostics: const [],
          consumedSteps: 1,
          selectorMatches: 1,
        ),
      );
      final subject = _subject(transport, fallback: fallback);

      final result = await subject.listPlayableSources(plans: [_plan(package)]);

      expect(result.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(result.sources.single.sourceKey, 'captured');
      expect(fallback.calls, 1);
    },
  );

  test(
    'acquires a fresh episode-specific media candidate on every request',
    () async {
      final package = _package('example.anime');
      final transport = _DynamicEpisodeTransport();
      final subject = _subject(transport);

      final episodeA = _episode(package.packageId, episodeId: 'episode-a');
      final episodeB = _episode(package.packageId, episodeId: 'episode-b');

      final firstA = await subject.listPlayableSources(
        plans: [_plan(package, episode: episodeA)],
      );
      final firstB = await subject.listPlayableSources(
        plans: [_plan(package, episode: episodeB)],
      );
      final replayA = await subject.listPlayableSources(
        plans: [_plan(package, episode: episodeA)],
      );

      expect(firstA.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(firstB.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(replayA.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(transport.requests, hasLength(3));
      expect(transport.requests.map((request) => request.uri.path), [
        '/play/episode-a',
        '/play/episode-b',
        '/play/episode-a',
      ]);

      final firstAMedia = firstA.sources.single.mediaUri;
      final firstBMedia = firstB.sources.single.mediaUri;
      final replayAMedia = replayA.sources.single.mediaUri;
      expect(firstA.sources.single.episode, episodeA);
      expect(firstB.sources.single.episode, episodeB);
      expect(replayA.sources.single.episode, episodeA);
      expect(firstAMedia.path, '/media/episode-a/acquisition-1.mp4');
      expect(firstBMedia.path, '/media/episode-b/acquisition-2.mp4');
      expect(replayAMedia.path, '/media/episode-a/acquisition-3.mp4');
      expect(replayAMedia, isNot(equals(firstAMedia)));
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
          '<article class="source">'
          '<span class="key">enabled</span>'
          '<span class="label">Enabled</span>'
          '<span class="kind">video</span>'
          '<span class="media">https://example.com/media/enabled.mp4</span>'
          '<span class="page">https://example.com/watch/enabled</span>'
          '</article>',
        ),
      ]);

      final result = await _subject(transport).listPlayableSources(
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

      expect(result.status, SourcePlayableSourceCoordinatorStatus.partial);
      expect(result.sources.single.sourceKey, 'enabled');
      expect(
        result.sourceResults[1].status,
        SourcePlayableSourceNormalizationStatus.disabled,
      );
      expect(transport.requests, hasLength(1));
    },
  );

  test('duplicate plans and plan limits short-circuit live I/O', () async {
    final package = _package('example.anime');
    final transport = _QueueTransport([_success(_request(), 'unused')]);
    final subject = _subject(transport);

    final duplicate = await subject.listPlayableSources(
      plans: [_plan(package), _plan(package)],
    );
    expect(duplicate.reasonCode, 'duplicate_source_plan');
    expect(transport.requests, isEmpty);

    final tooMany = await subject.listPlayableSources(
      plans: List<SourceLivePlayableSourcePlan>.generate(
        33,
        (index) => _plan(_package('example$index.anime')),
      ),
    );
    expect(tooMany.reasonCode, 'too_many_source_plans');
    expect(transport.requests, isEmpty);
  });

  test('invalid episode identity fails before live I/O', () async {
    final package = _package('example.anime');
    final transport = _QueueTransport([_success(_request(), 'unused')]);
    final invalid = SourceEpisodeIdentity(
      sourceId: 'other.anime',
      lineId: 'line-1',
      subjectId: 'subject-1',
      episodeId: 'episode-1',
    );

    final result = await _subject(
      transport,
    ).listPlayableSources(plans: [_plan(package, episode: invalid)]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_playable_failed');
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'episode_source_mismatch',
    );
    expect(result.sources, isEmpty);
    expect(transport.requests, isEmpty);
  });

  test('normalizer identity mismatch fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLivePlayableSourceCoordinator(
      runtime: runtime.runtime,
      normalizer: _ForgedIdentityNormalizer(),
    );

    final result = await subject.listPlayableSources(plans: [_plan(package)]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.status,
      SourcePlayableSourceNormalizationStatus.failed,
    );
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_identity_mismatch',
    );
    expect(result.sources, isEmpty);
  });

  test('forged candidate identity or URI policy fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLivePlayableSourceCoordinator(
      runtime: runtime.runtime,
      normalizer: _ForgedCandidateNormalizer(),
    );

    final result = await subject.listPlayableSources(plans: [_plan(package)]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_candidate_invalid',
    );
    expect(result.sources, isEmpty);
  });

  test('invalid normalized shape fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLivePlayableSourceCoordinator(
      runtime: runtime.runtime,
      normalizer: _InvalidShapeNormalizer(),
    );

    final result = await subject.listPlayableSources(plans: [_plan(package)]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_result_invalid',
    );
    expect(result.sources, isEmpty);
  });

  test('normalizer exceptions become safe typed failures', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLivePlayableSourceCoordinator(
      runtime: runtime.runtime,
      normalizer: _ThrowingNormalizer(),
    );

    final result = await subject.listPlayableSources(plans: [_plan(package)]);

    expect(result.status, SourcePlayableSourceCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_failed',
    );
    expect(result.toString(), isNot(contains('private')));
  });

  test(
    'a newer playable listing supersedes an older pending response',
    () async {
      final package = _package('example.anime');
      final transport = _DeferredTransport();
      final subject = _subject(transport);

      final first = subject.listPlayableSources(plans: [_plan(package)]);
      await transport.firstStarted.future;
      final second = subject.listPlayableSources(plans: [_plan(package)]);
      await transport.secondStarted.future;

      transport.completeFirst(
        _success(
          _request(),
          '<article class="source">'
          '<span class="key">old</span>'
          '<span class="label">Old</span>'
          '<span class="kind">video</span>'
          '<span class="media">https://example.com/media/old.mp4</span>'
          '<span class="page">https://example.com/watch/old</span>'
          '</article>',
        ),
      );
      final stale = await first;
      expect(stale.status, SourcePlayableSourceCoordinatorStatus.failed);
      expect(stale.reasonCode, 'stale_playable');
      expect(stale.sources, isEmpty);

      transport.completeSecond(
        _success(
          _request(),
          '<article class="source">'
          '<span class="key">new</span>'
          '<span class="label">New</span>'
          '<span class="kind">video</span>'
          '<span class="media">https://example.com/media/new.mp4</span>'
          '<span class="page">https://example.com/watch/new</span>'
          '</article>',
        ),
      );
      final current = await second;
      expect(current.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(current.sources.single.sourceKey, 'new');
    },
  );

  test(
    'close invalidates pending and future listings without returning sources',
    () async {
      final package = _package('example.anime');
      final transport = _DeferredTransport();
      final subject = _subject(transport);

      final pending = subject.listPlayableSources(plans: [_plan(package)]);
      await transport.firstStarted.future;
      subject.close();
      transport.completeFirst(
        _success(
          _request(),
          '<article class="source">'
          '<span class="key">late</span>'
          '<span class="label">Late</span>'
          '<span class="kind">video</span>'
          '<span class="media">https://example.com/media/late.mp4</span>'
          '<span class="page">https://example.com/watch/late</span>'
          '</article>',
        ),
      );

      final closed = await pending;
      expect(closed.status, SourcePlayableSourceCoordinatorStatus.failed);
      expect(closed.reasonCode, 'live_playable_closed');
      expect(closed.sources, isEmpty);

      final afterClose = await subject.listPlayableSources(
        plans: [_plan(package)],
      );
      expect(afterClose.reasonCode, 'live_playable_closed');
      expect(afterClose.sources, isEmpty);
    },
  );
}

_RuntimeSubject _subject(
  SourceHttpTransport transport, {
  SourcePlayableSourceNormalizer? normalizer,
  SourceLivePlayableDocumentFallback? fallback,
}) {
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
    coordinator: SourceLivePlayableSourceCoordinator(
      runtime: runtime,
      normalizer:
          normalizer ?? const DeclarativeSourcePlayableSourceNormalizer(),
      documentFallback: fallback,
    ),
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
    request: request ?? _request(episodeId: episode?.episodeId),
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

SourceEpisodeIdentity _episode(
  String sourceId, {
  String episodeId = 'episode-1',
}) => SourceEpisodeIdentity(
  sourceId: sourceId,
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: episodeId,
);

SourceHttpRequest _request({String? episodeId}) => SourceHttpRequest(
  uri: Uri.parse(
    episodeId == null
        ? 'https://example.com/playback'
        : 'https://example.com/play/$episodeId',
  ),
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
        SourceFieldRule(
          name: 'label',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.label',
          ),
        ),
        SourceFieldRule(
          name: 'kind',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.kind',
          ),
        ),
        SourceFieldRule(
          name: 'media',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.media',
          ),
        ),
        SourceFieldRule(
          name: 'page',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.page',
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
  final SourceLivePlayableSourceCoordinator coordinator;

  Future<SourcePlayableSourceCoordinatorResult> listPlayableSources({
    required Iterable<SourceLivePlayableSourcePlan> plans,
  }) => coordinator.listPlayableSources(plans: plans);

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

final class _DynamicEpisodeTransport implements SourceHttpTransport {
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    final episodeId = request.uri.pathSegments.last;
    final acquisition = requests.length;
    return _success(
      request,
      '<article class="source">'
      '<span class="key">$episodeId-$acquisition</span>'
      '<span class="label">Dynamic</span>'
      '<span class="kind">video</span>'
      '<span class="media">https://example.com/media/$episodeId/acquisition-$acquisition.mp4</span>'
      '<span class="page">${request.uri}</span>'
      '</article>',
    );
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

final class _CountingNormalizer implements SourcePlayableSourceNormalizer {
  var calls = 0;
  final _delegate = const DeclarativeSourcePlayableSourceNormalizer();

  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) {
    calls++;
    return _delegate.normalizePlayableSources(
      package: package,
      runtimeResult: runtimeResult,
      episode: episode,
      mapping: mapping,
    );
  }
}

final class _ForgedIdentityNormalizer
    implements SourcePlayableSourceNormalizer {
  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) => SourcePlayableSourceNormalizationResult(
    packageId: 'forged.package',
    packageVersion: package.version,
    programId: runtimeResult.programId,
    status: SourcePlayableSourceNormalizationStatus.available,
    results: const [],
    diagnostics: const [],
  );
}

final class _ForgedCandidateNormalizer
    implements SourcePlayableSourceNormalizer {
  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) => SourcePlayableSourceNormalizationResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: runtimeResult.programId,
    status: SourcePlayableSourceNormalizationStatus.available,
    results: [
      SourcePlayableSource(
        episode: _episode('other.anime'),
        sourceKey: 'forged',
        label: 'Forged',
        kind: WebCandidateKind.video,
        mediaUri: Uri.parse('https://evil.example/media.mp4'),
        pageUri: Uri.parse('https://evil.example/watch'),
      ),
    ],
    diagnostics: const [],
  );
}

final class _InvalidShapeNormalizer implements SourcePlayableSourceNormalizer {
  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) => SourcePlayableSourceNormalizationResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: runtimeResult.programId,
    status: SourcePlayableSourceNormalizationStatus.available,
    results: const [],
    diagnostics: const [],
  );
}

final class _ThrowingNormalizer implements SourcePlayableSourceNormalizer {
  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) {
    throw StateError('private playable normalization detail');
  }
}

final class _FallbackRuntime implements SourceLivePlayableDocumentFallback {
  _FallbackRuntime(this.result);

  final SourceRuntimeResult result;
  var calls = 0;

  @override
  Future<SourceRuntimeResult> capture(SourceLivePlayableSourcePlan plan) async {
    calls++;
    return result;
  }
}
