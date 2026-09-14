import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playback_open_request_builder.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/source_playback_session_request_builder.dart';
import 'package:wynime/src/infrastructure/playback/default_playback_session_resolver.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const builder = DeterministicSourcePlaybackOpenRequestBuilder();
  final package = _package();
  final episode = _episode();
  final route = _route(episode);
  final plan = _adPlan(episode);
  final proxyBudget = _proxyBudget();

  test('composes the resolver request and coordinator options exactly', () {
    final capturingBuilder = _CapturingSessionRequestBuilder();
    final compositionBuilder = DeterministicSourcePlaybackOpenRequestBuilder(
      sessionRequestBuilder: capturingBuilder,
    );
    final bangumiEpisode = BangumiEpisodeTarget(
      subjectId: '100',
      episodeId: '200',
    );
    final result = compositionBuilder.buildOpenRequest(
      route: route,
      package: package,
      adRemovalPlan: plan,
      sourceEventSequence: 9,
      proxyBudget: proxyBudget,
      addressFamily: LoopbackAddressFamily.ipv6,
      refreshLeeway: const Duration(seconds: 45),
      maxAutomaticRefreshes: 2,
      episodeDuration: const Duration(minutes: 24),
      bangumiEpisode: bangumiEpisode,
    );

    expect(capturingBuilder.callCount, 1);
    expect(capturingBuilder.route, same(route));
    expect(capturingBuilder.package, same(package));
    expect(capturingBuilder.adRemovalPlan, same(plan));
    expect(capturingBuilder.sourceEventSequence, 9);
    expect(result.status, SourcePlaybackOpenRequestBuildStatus.ready);
    final request = result.request!;
    expect(request.proxyBudget, same(proxyBudget));
    expect(request.addressFamily, LoopbackAddressFamily.ipv6);
    expect(request.refreshLeeway, const Duration(seconds: 45));
    expect(request.maxAutomaticRefreshes, 2);
    expect(request.episodeDuration, const Duration(minutes: 24));
    expect(request.bangumiEpisode, same(bangumiEpisode));
    expect(request.resolution.episode, episode);
    expect(request.resolution.securityPolicy, same(package.securityPolicy));
    expect(request.resolution.adRemovalPlan, same(plan));
    expect(request.resolution.pageUri, route.source.pageUri);
    expect(request.resolution.candidate.uri, route.source.mediaUri);
    expect(request.resolution.candidate.sourceEventSequence, 9);
    expect(request.resolution.candidate.headers, isEmpty);
    expect(request.resolution.cookies, isEmpty);
  });

  test(
    'the composed resolution remains compatible with the existing resolver',
    () async {
      final result = builder.buildOpenRequest(
        route: route,
        package: package,
        adRemovalPlan: plan,
        sourceEventSequence: 9,
        proxyBudget: proxyBudget,
      );

      expect(result.status, SourcePlaybackOpenRequestBuildStatus.ready);
      final session = await DefaultPlaybackSessionResolver().resolve(
        result.request!.resolution,
      );

      expect(session.episode, same(episode));
      expect(session.mediaUri, route.source.mediaUri);
      expect(session.pageUri, route.source.pageUri);
      expect(session.headers, isEmpty);
      expect(session.cookies, isEmpty);
      expect(session.adRemovalPlan, same(plan));
    },
  );

  test('does not turn a rejected session request into an open request', () {
    final rejectedBuilder = DeterministicSourcePlaybackOpenRequestBuilder(
      sessionRequestBuilder: _RejectingSessionRequestBuilder(),
    );
    final result = rejectedBuilder.buildOpenRequest(
      route: route,
      package: package,
      adRemovalPlan: plan,
      sourceEventSequence: 0,
      proxyBudget: proxyBudget,
    );

    expect(
      result.status,
      SourcePlaybackOpenRequestBuildStatus.sessionRequestRejected,
    );
    expect(result.reasonCode, 'package_version_mismatch');
    expect(result.request, isNull);
  });

  test('rejects invalid coordinator options before delegation', () {
    final capturingBuilder = _CapturingSessionRequestBuilder();
    final optionBuilder = DeterministicSourcePlaybackOpenRequestBuilder(
      sessionRequestBuilder: capturingBuilder,
    );

    void expectInvalid(
      SourcePlaybackOpenRequestBuildStatus expectedStatus, {
      Duration refreshLeeway = const Duration(seconds: 30),
      int maxAutomaticRefreshes = 1,
      Duration? episodeDuration,
    }) {
      final result = optionBuilder.buildOpenRequest(
        route: route,
        package: package,
        adRemovalPlan: plan,
        sourceEventSequence: 0,
        proxyBudget: proxyBudget,
        refreshLeeway: refreshLeeway,
        maxAutomaticRefreshes: maxAutomaticRefreshes,
        episodeDuration: episodeDuration,
      );
      expect(result.status, expectedStatus);
      expect(result.request, isNull);
    }

    expectInvalid(
      SourcePlaybackOpenRequestBuildStatus.invalidRefreshLeeway,
      refreshLeeway: const Duration(seconds: -1),
    );
    expectInvalid(
      SourcePlaybackOpenRequestBuildStatus.invalidAutomaticRefreshes,
      maxAutomaticRefreshes: -1,
    );
    expectInvalid(
      SourcePlaybackOpenRequestBuildStatus.invalidAutomaticRefreshes,
      maxAutomaticRefreshes: 4,
    );
    expectInvalid(
      SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
      episodeDuration: const Duration(seconds: -1),
    );

    expect(capturingBuilder.callCount, 0);
  });

  test('result invariants and diagnostics stay bounded and redacted', () {
    expect(
      () => SourcePlaybackOpenRequestBuildResult(
        status: SourcePlaybackOpenRequestBuildStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestBuildResult(
        status: SourcePlaybackOpenRequestBuildStatus.sessionRequestRejected,
      ),
      throwsArgumentError,
    );
    final ready = builder.buildOpenRequest(
      route: route,
      package: package,
      adRemovalPlan: plan,
      sourceEventSequence: 0,
      proxyBudget: proxyBudget,
    );
    expect(
      () => SourcePlaybackOpenRequestBuildResult(
        status: SourcePlaybackOpenRequestBuildStatus.sessionRequestRejected,
        request: ready.request,
        reasonCode: 'package_version_mismatch',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestBuildResult(
        status: SourcePlaybackOpenRequestBuildStatus.sessionRequestRejected,
        reasonCode: 'Not Safe',
      ),
      throwsArgumentError,
    );
    final diagnostic = ready.toString();
    expect(diagnostic, contains('status: ready'));
    expect(diagnostic, contains('hasRequest: true'));
    expect(diagnostic, isNot(contains('example.anime')));
    expect(diagnostic, isNot(contains('master.m3u8')));
    expect(diagnostic, isNot(contains('episode-1')));
  });
}

final class _CapturingSessionRequestBuilder
    implements SourcePlaybackSessionRequestBuilder {
  final SourcePlaybackSessionRequestBuilder _delegate =
      const DeterministicSourcePlaybackSessionRequestBuilder();

  int callCount = 0;
  SourcePlaybackRoute? route;
  SourcePackageManifest? package;
  AdRemovalPlan? adRemovalPlan;
  int? sourceEventSequence;

  @override
  SourcePlaybackSessionRequestBuildResult buildRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    callCount++;
    this.route = route;
    this.package = package;
    this.adRemovalPlan = adRemovalPlan;
    this.sourceEventSequence = sourceEventSequence;
    return _delegate.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
    );
  }
}

final class _RejectingSessionRequestBuilder
    implements SourcePlaybackSessionRequestBuilder {
  @override
  SourcePlaybackSessionRequestBuildResult buildRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) => SourcePlaybackSessionRequestBuildResult(
    status: SourcePlaybackSessionRequestBuildStatus.versionMismatch,
    reasonCode: 'package_version_mismatch',
  );
}

SourcePackageManifest _package() => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
  securityPolicy: testSourcePolicy(
    domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
  ),
  programs: [_program()],
);

SourceRuleProgram _program() => SourceRuleProgram(
  programId: 'playback',
  documentKind: SourceDocumentKind.html,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.css,
    expression: 'article',
  ),
  fields: [SourceFieldRule(name: 'source', valueKind: SourceValueKind.text)],
  resultLimit: 1,
);

SourcePlaybackRoute _route(SourceEpisodeIdentity episode) =>
    SourcePlaybackRoute(
      packageId: 'example.anime',
      packageVersion: Version.parse('1.0.0'),
      programId: 'playback',
      source: SourcePlayableSource(
        episode: episode,
        sourceKey: 'primary',
        label: 'Primary',
        kind: WebCandidateKind.hls,
        mediaUri: Uri.parse('https://cdn.example.com/video/master.m3u8'),
        pageUri: Uri.parse('https://example.com/watch/episode-1'),
      ),
    );

SourceEpisodeIdentity _episode() => SourceEpisodeIdentity(
  sourceId: 'example.anime',
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: 'episode-1',
);

AdRemovalPlan _adPlan(SourceEpisodeIdentity episode) => AdRemovalPlan(
  key: AdRemovalPlanKey(
    episode: episode,
    manifestFingerprint: ManifestFingerprint(
      algorithm: 'sha256',
      value: 'fixture-fingerprint',
    ),
  ),
);

PlaybackProxyBudget _proxyBudget() => PlaybackProxyBudget(
  maxPlaylistBytes: 64 * 1024,
  maxResponseBytes: 1024 * 1024,
  maxRequestHeaderBytes: 64 * 1024,
  maxCookieBytes: 64 * 1024,
  maxRedirects: 3,
  maxRegisteredResources: 100,
  upstreamTimeout: const Duration(seconds: 5),
);
