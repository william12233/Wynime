import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_capture_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

void main() {
  final coordinator = SourceLiveCapturePlaybackSessionRequestCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
  );

  test('forwards the exact live candidate, cookies, UA and policy once', () {
    final fixture = _fixture();

    final result = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
    );

    expect(result.status, SourceLiveCapturePlaybackSessionRequestStatus.ready);
    final request = result.request!;
    expect(identical(request.candidate, fixture.candidate), isTrue);
    expect(request.candidate.headers['x-capture'], 'captured');
    expect(request.candidate.sourceEventSequence, 10);
    expect(request.cookies, hasLength(1));
    expect(request.cookies.single.name, 'session');
    expect(request.cookies.single.value, 'secret-cookie-value');
    expect(request.userAgent, 'Mozilla/5.0 Wynime Test Browser');
    expect(request.pageUri, fixture.pageUri);
    expect(request.episode, fixture.episode);
    expect(
      identical(request.securityPolicy, fixture.package.securityPolicy),
      isTrue,
    );
    expect(identical(request.adRemovalPlan, fixture.adRemovalPlan), isTrue);
    expect(result.toString(), isNot(contains('secret-cookie-value')));
    expect(result.toString(), isNot(contains('media.example.com')));
  });

  test('does not build a resolver request for a non-selected route', () {
    final fixture = _fixture();
    final result = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: SourceLiveCapturePlaybackRouteResult(
        status: SourceLiveCapturePlaybackRouteStatus.notFound,
        reasonCode: 'live_not_found',
      ),
      adRemovalPlan: fixture.adRemovalPlan,
    );

    expect(
      result.status,
      SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected,
    );
    expect(result.routeStatus, SourceLiveCapturePlaybackRouteStatus.notFound);
    expect(result.reasonCode, 'live_not_found');
    expect(result.request, isNull);
  });

  test('maps consent, disabled and incompatible package gates first', () {
    final fixture = _fixture();

    final consent = coordinator.buildRequest(
      installedPackage: fixture.installedPackage.copyWith(
        status: SourcePackageStatus.disabled,
        requiresConsent: true,
      ),
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(
      consent.status,
      SourceLiveCapturePlaybackSessionRequestStatus.consentRequired,
    );
    expect(consent.reasonCode, 'consent_required');
    expect(consent.request, isNull);

    final disabled = coordinator.buildRequest(
      installedPackage: fixture.installedPackage.copyWith(
        status: SourcePackageStatus.disabled,
      ),
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(
      disabled.status,
      SourceLiveCapturePlaybackSessionRequestStatus.disabled,
    );
    expect(disabled.reasonCode, 'package_disabled');

    final incompatibleFixture = _fixture(
      wynimeVersionConstraint: VersionConstraint.parse('>=2.0.0 <3.0.0'),
    );
    final incompatible = coordinator.buildRequest(
      installedPackage: incompatibleFixture.installedPackage,
      routeResult: incompatibleFixture.routeResult,
      adRemovalPlan: incompatibleFixture.adRemovalPlan,
    );
    expect(
      incompatible.status,
      SourceLiveCapturePlaybackSessionRequestStatus.incompatible,
    );
    expect(incompatible.reasonCode, 'incompatible_wynime_version');
  });

  test('rejects package, program and capture-request identity mismatches', () {
    final fixture = _fixture();
    final otherPackage = _fixture(packageId: 'other.source');
    final packageMismatch = coordinator.buildRequest(
      installedPackage: otherPackage.installedPackage,
      routeResult: fixture.routeResult,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(
      packageMismatch.status,
      SourceLiveCapturePlaybackSessionRequestStatus.failed,
    );
    expect(packageMismatch.reasonCode, 'live_session_package_mismatch');

    final unknownProgramRequest = SourceLiveCaptureRequest(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: 'unknown',
      webCaptureRequest: fixture.captureRequest.webCaptureRequest,
    );
    final unknownProgramCapture = SourceLiveCaptureResult(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: 'unknown',
      status: SourceLiveCaptureStatus.captured,
      snapshot: fixture.captureResult.snapshot,
    );
    final unknownProgramRoute = SourceLiveCapturePlaybackRouteResult(
      status: SourceLiveCapturePlaybackRouteStatus.selected,
      route: SourceLiveCapturePlaybackRoute(
        packageId: fixture.package.packageId,
        packageVersion: fixture.package.version,
        programId: 'unknown',
        source: fixture.liveSource,
        captureRequest: unknownProgramRequest,
        captureResult: unknownProgramCapture,
      ),
    );
    final unknownProgram = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: unknownProgramRoute,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(unknownProgram.reasonCode, 'program_not_found');

    final mismatchedRequest = SourceLiveCaptureRequest(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: fixture.programId,
      webCaptureRequest: WebCaptureRequest(
        initialUri: fixture.pageUri,
        securityPolicy: _policy(maxDocumentBytes: 2048),
        budget: fixture.captureRequest.webCaptureRequest.budget,
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: true,
      ),
    );
    final mismatchedRoute = SourceLiveCapturePlaybackRouteResult(
      status: SourceLiveCapturePlaybackRouteStatus.selected,
      route: SourceLiveCapturePlaybackRoute(
        packageId: fixture.package.packageId,
        packageVersion: fixture.package.version,
        programId: fixture.programId,
        source: fixture.liveSource,
        captureRequest: mismatchedRequest,
        captureResult: fixture.captureResult,
      ),
    );
    final requestMismatch = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: mismatchedRoute,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(requestMismatch.reasonCode, 'live_session_capture_request_mismatch');
  });

  test('rejects forged snapshot provenance and candidate identity', () {
    final fixture = _fixture();
    final forgedSnapshot = WebCaptureSnapshot(
      events: const [],
      candidates: [fixture.candidate],
      cookies: fixture.captureResult.snapshot!.cookies,
      stopReason: WebCaptureStopReason.completed,
      finalUri: fixture.pageUri,
    );
    final forgedCapture = SourceLiveCaptureResult(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: fixture.programId,
      status: SourceLiveCaptureStatus.captured,
      snapshot: forgedSnapshot,
    );
    final forgedRoute = SourceLiveCapturePlaybackRouteResult(
      status: SourceLiveCapturePlaybackRouteStatus.selected,
      route: SourceLiveCapturePlaybackRoute(
        packageId: fixture.package.packageId,
        packageVersion: fixture.package.version,
        programId: fixture.programId,
        source: fixture.liveSource,
        captureRequest: fixture.captureRequest,
        captureResult: forgedCapture,
      ),
    );
    final forged = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: forgedRoute,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(forged.status, SourceLiveCapturePlaybackSessionRequestStatus.failed);
    expect(forged.reasonCode, 'candidate_event_sequence_invalid');
    expect(forged.request, isNull);

    final invalidIndexSource = SourceLiveCapturePlayableSource(
      candidateIndex: 1,
      source: fixture.source,
      candidate: fixture.candidate,
    );
    final invalidIndexRoute = SourceLiveCapturePlaybackRouteResult(
      status: SourceLiveCapturePlaybackRouteStatus.selected,
      route: SourceLiveCapturePlaybackRoute(
        packageId: fixture.package.packageId,
        packageVersion: fixture.package.version,
        programId: fixture.programId,
        source: invalidIndexSource,
        captureRequest: fixture.captureRequest,
        captureResult: fixture.captureResult,
      ),
    );
    final invalidIndex = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: invalidIndexRoute,
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(invalidIndex.reasonCode, 'live_session_candidate_index_invalid');
  });

  test(
    'rejects policy, page, media, supported-kind and ad-plan mismatches',
    () {
      final fixture = _fixture();
      final broaderPolicy = SourceSecurityPolicy(
        allowedDomains: [
          SourceDomainRule(host: 'example.com', includeSubdomains: true),
        ],
        permissions: fixture.package.securityPolicy.permissions,
        budget: SourceResourceBudget(
          maxDocumentBytes: 2048,
          maxRecords: 10,
          maxSelectorMatches: 10,
          maxEvaluationSteps: 100,
          maxRegexPatternChars: 32,
          maxRegexInputChars: 128,
          maxRedirects: 2,
        ),
      );
      final policyRequest = SourceLiveCaptureRequest(
        packageId: fixture.package.packageId,
        packageVersion: fixture.package.version,
        programId: fixture.programId,
        webCaptureRequest: WebCaptureRequest(
          initialUri: fixture.pageUri,
          securityPolicy: broaderPolicy,
          budget: fixture.captureRequest.webCaptureRequest.budget,
          userAgentPolicy:
              fixture.captureRequest.webCaptureRequest.userAgentPolicy,
          captureMediaRequests: true,
        ),
      );
      final policyRoute = _routeWithRequest(fixture, policyRequest);
      final policyResult = coordinator.buildRequest(
        installedPackage: fixture.installedPackage,
        routeResult: policyRoute,
        adRemovalPlan: fixture.adRemovalPlan,
      );
      expect(policyResult.reasonCode, 'live_session_capture_request_mismatch');

      final wrongPlan = AdRemovalPlan(
        key: AdRemovalPlanKey(
          episode: SourceEpisodeIdentity(
            sourceId: fixture.package.packageId,
            lineId: fixture.episode.lineId,
            subjectId: fixture.episode.subjectId,
            episodeId: 'episode-2',
          ),
          manifestFingerprint: ManifestFingerprint(
            algorithm: 'sha256',
            value: 'fixture',
          ),
        ),
      );
      final wrongPlanResult = coordinator.buildRequest(
        installedPackage: fixture.installedPackage,
        routeResult: fixture.routeResult,
        adRemovalPlan: wrongPlan,
      );
      expect(wrongPlanResult.reasonCode, 'ad_plan_episode_mismatch');

      final unsupportedCandidate = WebMediaCandidate(
        kind: WebCandidateKind.dash,
        uri: fixture.candidate.uri,
        headers: fixture.candidate.headers,
        sourceEventSequence: fixture.candidate.sourceEventSequence,
      );
      final unsupportedSource = SourceLiveCapturePlayableSource(
        candidateIndex: 0,
        source: SourcePlayableSource(
          episode: fixture.episode,
          sourceKey: 'dash',
          label: 'DASH',
          kind: WebCandidateKind.dash,
          mediaUri: fixture.candidate.uri,
          pageUri: fixture.pageUri,
        ),
        candidate: unsupportedCandidate,
      );
      final unsupportedRoute = SourceLiveCapturePlaybackRouteResult(
        status: SourceLiveCapturePlaybackRouteStatus.selected,
        route: SourceLiveCapturePlaybackRoute(
          packageId: fixture.package.packageId,
          packageVersion: fixture.package.version,
          programId: fixture.programId,
          source: unsupportedSource,
          captureRequest: fixture.captureRequest,
          captureResult: fixture.captureResult,
        ),
      );
      final unsupported = coordinator.buildRequest(
        installedPackage: fixture.installedPackage,
        routeResult: unsupportedRoute,
        adRemovalPlan: fixture.adRemovalPlan,
      );
      expect(unsupported.reasonCode, 'live_session_candidate_mismatch');

      final wrongPageSource = SourceLiveCapturePlayableSource(
        candidateIndex: 0,
        source: SourcePlayableSource(
          episode: fixture.episode,
          sourceKey: 'wrong-page',
          label: 'Wrong page',
          kind: fixture.candidate.kind,
          mediaUri: fixture.candidate.uri,
          pageUri: Uri.parse('https://page.example.com/watch/other'),
        ),
        candidate: fixture.candidate,
      );
      final wrongPageRoute = SourceLiveCapturePlaybackRouteResult(
        status: SourceLiveCapturePlaybackRouteStatus.selected,
        route: SourceLiveCapturePlaybackRoute(
          packageId: fixture.package.packageId,
          packageVersion: fixture.package.version,
          programId: fixture.programId,
          source: wrongPageSource,
          captureRequest: fixture.captureRequest,
          captureResult: fixture.captureResult,
        ),
      );
      final wrongPage = coordinator.buildRequest(
        installedPackage: fixture.installedPackage,
        routeResult: wrongPageRoute,
        adRemovalPlan: fixture.adRemovalPlan,
      );
      expect(wrongPage.reasonCode, 'live_session_candidate_mismatch');

      final wrongMediaUri = Uri.parse(
        'https://media.example.com/video/other.mp4',
      );
      final wrongMediaCandidate = WebMediaCandidate(
        kind: fixture.candidate.kind,
        uri: wrongMediaUri,
        headers: fixture.candidate.headers,
        sourceEventSequence: fixture.candidate.sourceEventSequence,
      );
      final wrongMediaSource = SourceLiveCapturePlayableSource(
        candidateIndex: 0,
        source: SourcePlayableSource(
          episode: fixture.episode,
          sourceKey: 'wrong-media',
          label: 'Wrong media',
          kind: wrongMediaCandidate.kind,
          mediaUri: wrongMediaCandidate.uri,
          pageUri: fixture.pageUri,
        ),
        candidate: wrongMediaCandidate,
      );
      final wrongMediaRoute = SourceLiveCapturePlaybackRouteResult(
        status: SourceLiveCapturePlaybackRouteStatus.selected,
        route: SourceLiveCapturePlaybackRoute(
          packageId: fixture.package.packageId,
          packageVersion: fixture.package.version,
          programId: fixture.programId,
          source: wrongMediaSource,
          captureRequest: fixture.captureRequest,
          captureResult: fixture.captureResult,
        ),
      );
      final wrongMedia = coordinator.buildRequest(
        installedPackage: fixture.installedPackage,
        routeResult: wrongMediaRoute,
        adRemovalPlan: fixture.adRemovalPlan,
      );
      expect(wrongMedia.reasonCode, 'live_session_candidate_mismatch');
    },
  );

  test('keeps failures typed and never creates a request', () {
    final fixture = _fixture();
    final failed = coordinator.buildRequest(
      installedPackage: fixture.installedPackage,
      routeResult: SourceLiveCapturePlaybackRouteResult(
        status: SourceLiveCapturePlaybackRouteStatus.failed,
        reasonCode: 'capture_failed',
      ),
      adRemovalPlan: fixture.adRemovalPlan,
    );
    expect(
      failed.status,
      SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected,
    );
    expect(failed.request, isNull);
    expect(failed.toString(), contains('capture_failed'));
    expect(failed.toString(), isNot(contains('secret-cookie-value')));
  });
}

SourceLiveCapturePlaybackRouteResult _routeWithRequest(
  _Fixture fixture,
  SourceLiveCaptureRequest request,
) => SourceLiveCapturePlaybackRouteResult(
  status: SourceLiveCapturePlaybackRouteStatus.selected,
  route: SourceLiveCapturePlaybackRoute(
    packageId: fixture.package.packageId,
    packageVersion: fixture.package.version,
    programId: fixture.programId,
    source: fixture.liveSource,
    captureRequest: request,
    captureResult: fixture.captureResult,
  ),
);

final class _Fixture {
  _Fixture({
    required this.package,
    required this.installedPackage,
    required this.programId,
    required this.episode,
    required this.pageUri,
    required this.captureRequest,
    required this.captureResult,
    required this.candidate,
    required this.source,
    required this.liveSource,
    required this.routeResult,
    required this.adRemovalPlan,
  });

  final SourcePackageManifest package;
  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceEpisodeIdentity episode;
  final Uri pageUri;
  final SourceLiveCaptureRequest captureRequest;
  final SourceLiveCaptureResult captureResult;
  final WebMediaCandidate candidate;
  final SourcePlayableSource source;
  final SourceLiveCapturePlayableSource liveSource;
  final SourceLiveCapturePlaybackRouteResult routeResult;
  final AdRemovalPlan adRemovalPlan;
}

_Fixture _fixture({
  String packageId = 'demo.source',
  VersionConstraint? wynimeVersionConstraint,
}) {
  final version = Version.parse('1.0.0');
  final programId = 'live';
  final pageUri = Uri.parse('https://page.example.com/watch/episode-1');
  final mediaUri = Uri.parse(
    'https://media.example.com/video/episode-1.mp4',
  ).replace(fragment: '');
  final episode = SourceEpisodeIdentity(
    sourceId: packageId,
    lineId: 'line-1',
    subjectId: 'subject-1',
    episodeId: 'episode-1',
  );
  final policy = _policy();
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: 'Demo Source',
    version: version,
    wynimeVersionConstraint:
        wynimeVersionConstraint ?? VersionConstraint.parse('>=1.0.0 <2.0.0'),
    securityPolicy: policy,
    programs: [_program(programId)],
  );
  final captureRequest = SourceLiveCaptureRequest(
    packageId: packageId,
    packageVersion: version,
    programId: programId,
    webCaptureRequest: WebCaptureRequest(
      initialUri: pageUri,
      securityPolicy: policy,
      budget: WebCaptureBudget(
        maxEvents: 100,
        maxCandidates: 10,
        maxHeaderBytes: 4096,
        maxCookieBytes: 4096,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.desktop,
        value: 'Mozilla/5.0 Wynime Test Browser',
      ),
      captureMediaRequests: true,
    ),
  );
  final candidate = WebMediaCandidate(
    kind: WebCandidateKind.video,
    uri: mediaUri,
    headers: const {
      'x-capture': 'captured',
      'referer': 'https://page.example.com/watch/episode-1',
    },
    sourceEventSequence: 10,
  );
  final captureResult = SourceLiveCaptureResult(
    packageId: packageId,
    packageVersion: version,
    programId: programId,
    status: SourceLiveCaptureStatus.captured,
    snapshot: WebCaptureSnapshot(
      events: [
        WebCaptureEvent(
          sequence: 10,
          kind: WebRequestKind.resource,
          uri: mediaUri,
          headers: candidate.headers,
        ),
      ],
      candidates: [candidate],
      cookies: [
        WebCaptureCookie(
          name: 'session',
          value: 'secret-cookie-value',
          domain: 'example.com',
        ),
      ],
      stopReason: WebCaptureStopReason.completed,
      finalUri: pageUri,
    ),
  );
  final source = SourcePlayableSource(
    episode: episode,
    sourceKey: 'primary',
    label: 'Primary',
    kind: candidate.kind,
    mediaUri: candidate.uri,
    pageUri: pageUri,
  );
  final liveSource = SourceLiveCapturePlayableSource(
    candidateIndex: 0,
    source: source,
    candidate: candidate,
  );
  final playableResult = SourceLiveCapturePlayableSourceResult(
    packageId: packageId,
    packageVersion: version,
    programId: programId,
    status: SourceLiveCapturePlayableSourceStatus.available,
    sources: [liveSource],
    diagnostics: const <SourcePlayableSourceNormalizationDiagnostic>[],
    captureRequest: captureRequest,
    captureResult: captureResult,
  );
  final installedPackage = InstalledSourcePackage(
    package: package,
    status: SourcePackageStatus.enabled,
    requiresConsent: false,
    requiresReconsent: false,
  );
  final routeResult =
      SourceLiveCapturePlaybackRouteCoordinator(
        wynimeVersion: Version.parse('1.0.0'),
      ).selectRoute(
        installedPackage: installedPackage,
        playableResult: playableResult,
      );
  final adRemovalPlan = AdRemovalPlan(
    key: AdRemovalPlanKey(
      episode: episode,
      manifestFingerprint: ManifestFingerprint(
        algorithm: 'sha256',
        value: 'fixture',
      ),
    ),
  );
  return _Fixture(
    package: package,
    installedPackage: installedPackage,
    programId: programId,
    episode: episode,
    pageUri: pageUri,
    captureRequest: captureRequest,
    captureResult: captureResult,
    candidate: candidate,
    source: source,
    liveSource: liveSource,
    routeResult: routeResult,
    adRemovalPlan: adRemovalPlan,
  );
}

SourceSecurityPolicy _policy({int maxDocumentBytes = 1024}) =>
    SourceSecurityPolicy(
      allowedDomains: [
        SourceDomainRule(host: 'example.com', includeSubdomains: true),
      ],
      permissions: const {
        SourcePermission.network,
        SourcePermission.cookies,
        SourcePermission.desktopUserAgent,
        SourcePermission.webView,
        SourcePermission.mediaRequestInspection,
      },
      budget: SourceResourceBudget(
        maxDocumentBytes: maxDocumentBytes,
        maxRecords: 10,
        maxSelectorMatches: 10,
        maxEvaluationSteps: 100,
        maxRegexPatternChars: 32,
        maxRegexInputChars: 128,
        maxRedirects: 2,
      ),
    );

SourceRuleProgram _program(String programId) => SourceRuleProgram(
  programId: programId,
  documentKind: SourceDocumentKind.html,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.css,
    expression: 'body',
  ),
  fields: [SourceFieldRule(name: 'value', valueKind: SourceValueKind.text)],
  resultLimit: 1,
);
