import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/source_playback_session_request_builder.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('builds one resolver request with exact live route provenance', () {
    final fixture = _fixture();
    final recordingBuilder = _RecordingBuilder();
    final result = _coordinator(builder: recordingBuilder).buildRequest(
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
      sourceEventSequence: 17,
    );

    expect(result.status, SourceLivePlaybackSessionRequestStatus.ready);
    final request = result.request!;
    expect(recordingBuilder.calls, 1);
    expect(recordingBuilder.route, same(fixture.route.route));
    expect(recordingBuilder.package, same(fixture.package));
    expect(recordingBuilder.adRemovalPlan, same(fixture.adRemovalPlan));
    expect(recordingBuilder.sourceEventSequence, 17);
    expect(request.episode, fixture.episode);
    expect(request.pageUri, fixture.source.pageUri);
    expect(request.candidate.kind, fixture.source.kind);
    expect(request.candidate.uri, fixture.source.mediaUri);
    expect(request.candidate.headers, isEmpty);
    expect(request.candidate.sourceEventSequence, 17);
    expect(request.cookies, isEmpty);
    expect(request.userAgent, isNull);
    expect(request.expiresAt, isNull);
    expect(request.refresh, isNull);
    expect(request.subtitles, isEmpty);
    expect(request.audioTracks, isEmpty);
    expect(request.securityPolicy, same(fixture.package.securityPolicy));
    expect(request.adRemovalPlan, same(fixture.adRemovalPlan));
    expect(result.toString(), isNot(contains('cdn.example.com')));
    expect(result.toString(), isNot(contains('episode-1')));
  });

  test(
    'short-circuits every non-selected route without calling the builder',
    () {
      final fixture = _fixture();
      final cases = {
        SourceLivePlaybackRouteStatus.notFound: 'route_not_found',
        SourceLivePlaybackRouteStatus.noSources: 'no_enabled_sources',
        SourceLivePlaybackRouteStatus.disabled: 'package_disabled',
        SourceLivePlaybackRouteStatus.consentRequired: 'consent_required',
        SourceLivePlaybackRouteStatus.incompatible:
            'incompatible_wynime_version',
        SourceLivePlaybackRouteStatus.preferredSourceNotFound:
            'preferred_source_not_found',
        SourceLivePlaybackRouteStatus.failed: 'route_failed',
      };

      for (final entry in cases.entries) {
        final builder = _RecordingBuilder();
        final result = _coordinator(builder: builder).buildRequest(
          routeResult: SourceLivePlaybackRouteResult(
            status: entry.key,
            reasonCode: entry.value,
          ),
          adRemovalPlan: fixture.adRemovalPlan,
          sourceEventSequence: 0,
        );
        expect(
          result.status,
          SourceLivePlaybackSessionRequestStatus.routeNotSelected,
        );
        expect(result.routeStatus, entry.key);
        expect(result.reasonCode, entry.value);
        expect(result.request, isNull);
        expect(builder.calls, 0);
      }
    },
  );

  test('checks consent, disabled and compatibility before the builder', () {
    final base = _fixture();
    final cases = [
      (
        base.copyWith(
          installedPackage: base.installedPackage.copyWith(
            status: SourcePackageStatus.disabled,
            requiresConsent: true,
          ),
        ),
        SourceLivePlaybackSessionRequestStatus.consentRequired,
        'consent_required',
      ),
      (
        base.copyWith(
          installedPackage: base.installedPackage.copyWith(
            status: SourcePackageStatus.disabled,
          ),
        ),
        SourceLivePlaybackSessionRequestStatus.disabled,
        'package_disabled',
      ),
      (
        _fixture(wynimeVersionConstraint: '^2.0.0'),
        SourceLivePlaybackSessionRequestStatus.incompatible,
        'incompatible_wynime_version',
      ),
    ];

    for (final (fixture, status, reasonCode) in cases) {
      final builder = _RecordingBuilder();
      final result = _coordinator(builder: builder).buildRequest(
        routeResult: fixture.routeResult,
        adRemovalPlan: fixture.adRemovalPlan,
        sourceEventSequence: 0,
      );
      expect(result.status, status);
      expect(result.reasonCode, reasonCode);
      expect(result.routeStatus, isNull);
      expect(result.request, isNull);
      expect(builder.calls, 0);
    }
  });

  test('rechecks program identity and preserves route package invariants', () {
    final fixture = _fixture();
    final unknownProgramRoute = SourcePlaybackRoute(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: 'missing',
      source: fixture.source,
    );
    final unknownProgram = _coordinator().buildRequest(
      routeResult: _liveRouteResult(
        fixture,
        route: SourceLivePlaybackRoute(
          route: unknownProgramRoute,
          installedPackage: fixture.installedPackage,
          request: fixture.request,
        ),
      ),
      adRemovalPlan: fixture.adRemovalPlan,
      sourceEventSequence: 0,
    );
    expect(
      unknownProgram.status,
      SourceLivePlaybackSessionRequestStatus.failed,
    );
    expect(unknownProgram.reasonCode, 'program_not_found');

    final mismatchedPackageRoute = SourcePlaybackRoute(
      packageId: 'other.anime',
      packageVersion: fixture.package.version,
      programId: fixture.programId,
      source: SourcePlayableSource(
        episode: SourceEpisodeIdentity(
          sourceId: 'other.anime',
          lineId: fixture.episode.lineId,
          subjectId: fixture.episode.subjectId,
          episodeId: fixture.episode.episodeId,
        ),
        sourceKey: fixture.source.sourceKey,
        label: fixture.source.label,
        kind: fixture.source.kind,
        mediaUri: fixture.source.mediaUri,
        pageUri: fixture.source.pageUri,
      ),
    );
    expect(
      () => SourceLivePlaybackRoute(
        route: mismatchedPackageRoute,
        installedPackage: fixture.installedPackage,
        request: fixture.request,
      ),
      throwsArgumentError,
    );
  });

  test(
    'preserves typed builder rejection for ad plan, sequence and URI errors',
    () {
      final fixture = _fixture();

      final wrongAdPlan = _adPlan(
        SourceEpisodeIdentity(
          sourceId: fixture.episode.sourceId,
          lineId: fixture.episode.lineId,
          subjectId: fixture.episode.subjectId,
          episodeId: 'episode-2',
        ),
      );
      final adPlanResult = _coordinator().buildRequest(
        routeResult: fixture.routeResult,
        adRemovalPlan: wrongAdPlan,
        sourceEventSequence: 0,
      );
      expect(
        adPlanResult.status,
        SourceLivePlaybackSessionRequestStatus.requestRejected,
      );
      expect(
        adPlanResult.requestStatus,
        SourcePlaybackSessionRequestBuildStatus.adRemovalPlanMismatch,
      );
      expect(adPlanResult.reasonCode, 'ad_plan_episode_mismatch');
      expect(adPlanResult.request, isNull);

      final negativeSequence = _coordinator().buildRequest(
        routeResult: fixture.routeResult,
        adRemovalPlan: fixture.adRemovalPlan,
        sourceEventSequence: -1,
      );
      expect(
        negativeSequence.requestStatus,
        SourcePlaybackSessionRequestBuildStatus.invalidSourceEventSequence,
      );
      expect(negativeSequence.reasonCode, 'invalid_source_event_sequence');

      final unsafeSource = SourcePlayableSource(
        episode: fixture.episode,
        sourceKey: fixture.source.sourceKey,
        label: fixture.source.label,
        kind: fixture.source.kind,
        mediaUri: Uri.parse('https://unsafe.invalid/media.m3u8'),
        pageUri: fixture.source.pageUri,
      );
      final unsafeResult = _coordinator().buildRequest(
        routeResult: _liveRouteResult(
          fixture,
          route: SourceLivePlaybackRoute(
            route: SourcePlaybackRoute(
              packageId: fixture.package.packageId,
              packageVersion: fixture.package.version,
              programId: fixture.programId,
              source: unsafeSource,
            ),
            installedPackage: fixture.installedPackage,
            request: fixture.request,
          ),
        ),
        adRemovalPlan: fixture.adRemovalPlan,
        sourceEventSequence: 0,
      );
      expect(
        unsafeResult.requestStatus,
        SourcePlaybackSessionRequestBuildStatus.mediaUriNotAllowed,
      );
      expect(unsafeResult.reasonCode, 'media_uri_not_allowed');
    },
  );

  test('converts builder exceptions and rejects forged ready requests', () {
    final fixture = _fixture();
    final throwing = _coordinator(builder: const _ThrowingBuilder())
        .buildRequest(
          routeResult: fixture.routeResult,
          adRemovalPlan: fixture.adRemovalPlan,
          sourceEventSequence: 0,
        );
    expect(throwing.status, SourceLivePlaybackSessionRequestStatus.failed);
    expect(throwing.reasonCode, 'live_session_request_build_failed');
    expect(throwing.toString(), isNot(contains('raw')));

    final forged = _coordinator(builder: const _ForgingBuilder()).buildRequest(
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
      sourceEventSequence: 0,
    );
    expect(forged.status, SourceLivePlaybackSessionRequestStatus.failed);
    expect(forged.reasonCode, 'live_session_request_mismatch');
    expect(forged.request, isNull);
  });

  test('keeps result invariants and diagnostics bounded', () {
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: 'route_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: SourceLivePlaybackRouteStatus.notFound,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'route_not_found',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.requestRejected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: 'request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.failed,
        reasonCode: 'failed',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'failed',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: 'Bad Code',
      ),
      throwsArgumentError,
    );

    final fixture = _fixture();
    final result = _coordinator().buildRequest(
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
      sourceEventSequence: 0,
    );
    expect(result.toRedactedDiagnostic()['hasRequest'], isTrue);
    expect(result.toString(), isNot(contains('example.com')));
    expect(result.toString(), isNot(contains('episode-1')));
  });
}

SourceLivePlaybackSessionRequestCoordinator _coordinator({
  SourcePlaybackSessionRequestBuilder? builder,
}) => SourceLivePlaybackSessionRequestCoordinator(
  wynimeVersion: Version.parse('1.0.0'),
  builder: builder ?? const DeterministicSourcePlaybackSessionRequestBuilder(),
);

SourceLivePlaybackRouteResult _liveRouteResult(
  _Fixture fixture, {
  SourceLivePlaybackRoute? route,
}) => SourceLivePlaybackRouteResult(
  status: SourceLivePlaybackRouteStatus.selected,
  route: route ?? fixture.route,
);

final class _Fixture {
  _Fixture({
    required this.package,
    required this.installedPackage,
    required this.programId,
    required this.episode,
    required this.request,
    required this.source,
    required this.route,
    required this.routeResult,
    required this.adRemovalPlan,
  });

  final SourcePackageManifest package;
  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceEpisodeIdentity episode;
  final SourceHttpRequest request;
  final SourcePlayableSource source;
  final SourceLivePlaybackRoute route;
  final SourceLivePlaybackRouteResult routeResult;
  final AdRemovalPlan adRemovalPlan;

  _Fixture copyWith({InstalledSourcePackage? installedPackage}) => _fixture(
    package: package,
    installedPackage: installedPackage ?? this.installedPackage,
  );
}

_Fixture _fixture({
  SourcePackageManifest? package,
  InstalledSourcePackage? installedPackage,
  String? wynimeVersionConstraint,
}) {
  final selectedPackage =
      package ?? _package(wynimeVersionConstraint: wynimeVersionConstraint);
  final selectedInstalled = installedPackage ?? _installed(selectedPackage);
  final programId = 'playback';
  final episode = SourceEpisodeIdentity(
    sourceId: selectedPackage.packageId,
    lineId: 'line-1',
    subjectId: 'subject-1',
    episodeId: 'episode-1',
  );
  final policy = selectedPackage.securityPolicy;
  final request = SourceHttpRequest(
    uri: Uri.parse('https://example.com/playback'),
    securityPolicy: policy,
    headers: const {'accept': 'text/html'},
    timeout: const Duration(seconds: 2),
  );
  final source = SourcePlayableSource(
    episode: episode,
    sourceKey: 'primary',
    label: 'Primary',
    kind: WebCandidateKind.hls,
    mediaUri: Uri.parse('https://cdn.example.com/video/master.m3u8'),
    pageUri: Uri.parse('https://example.com/watch/episode-1'),
  );
  final route = SourceLivePlaybackRoute(
    route: SourcePlaybackRoute(
      packageId: selectedPackage.packageId,
      packageVersion: selectedPackage.version,
      programId: programId,
      source: source,
    ),
    installedPackage: selectedInstalled,
    request: request,
  );
  return _Fixture(
    package: selectedPackage,
    installedPackage: selectedInstalled,
    programId: programId,
    episode: episode,
    request: request,
    source: source,
    route: route,
    routeResult: SourceLivePlaybackRouteResult(
      status: SourceLivePlaybackRouteStatus.selected,
      route: route,
    ),
    adRemovalPlan: _adPlan(episode),
  );
}

SourcePackageManifest _package({String? wynimeVersionConstraint}) =>
    SourcePackageManifest(
      schemaVersion: 1,
      packageId: 'example.anime',
      displayName: 'Example Anime',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse(
        wynimeVersionConstraint ?? '^1.0.0',
      ),
      securityPolicy: testSourcePolicy(
        domains: [
          SourceDomainRule(host: 'example.com', includeSubdomains: true),
        ],
      ),
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
              name: 'source',
              valueKind: SourceValueKind.text,
              selector: SourceSelector(
                kind: SourceSelectorKind.css,
                expression: '.source',
              ),
            ),
          ],
          resultLimit: 1,
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

AdRemovalPlan _adPlan(SourceEpisodeIdentity episode) => AdRemovalPlan(
  key: AdRemovalPlanKey(
    episode: episode,
    manifestFingerprint: ManifestFingerprint(
      algorithm: 'sha256',
      value: 'fixture-fingerprint',
    ),
  ),
);

final class _RecordingBuilder implements SourcePlaybackSessionRequestBuilder {
  _RecordingBuilder({SourcePlaybackSessionRequestBuilder? delegate})
    : delegate =
          delegate ?? const DeterministicSourcePlaybackSessionRequestBuilder();

  final SourcePlaybackSessionRequestBuilder delegate;
  int calls = 0;
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
    calls++;
    this.route = route;
    this.package = package;
    this.adRemovalPlan = adRemovalPlan;
    this.sourceEventSequence = sourceEventSequence;
    return delegate.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
    );
  }
}

final class _ThrowingBuilder implements SourcePlaybackSessionRequestBuilder {
  const _ThrowingBuilder();

  @override
  SourcePlaybackSessionRequestBuildResult buildRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    throw StateError('raw request builder detail');
  }
}

final class _ForgingBuilder implements SourcePlaybackSessionRequestBuilder {
  const _ForgingBuilder();

  @override
  SourcePlaybackSessionRequestBuildResult buildRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    return SourcePlaybackSessionRequestBuildResult(
      status: SourcePlaybackSessionRequestBuildStatus.ready,
      request: PlaybackSessionResolutionRequest(
        episode: route.episode,
        pageUri: route.source.pageUri,
        candidate: WebMediaCandidate(
          kind: route.source.kind,
          uri: Uri.parse('https://example.com/forged.m3u8'),
          headers: const {},
          sourceEventSequence: sourceEventSequence,
        ),
        securityPolicy: package.securityPolicy,
        adRemovalPlan: adRemovalPlan,
      ),
    );
  }
}
