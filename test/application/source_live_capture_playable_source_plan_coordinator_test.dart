import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_plan_coordinator.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_package_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final fixture = _fixture();
  const coordinator = SourceLiveCapturePlayableSourcePlanCoordinator();

  test('builds one exact playable-source plan from an accepted capture', () {
    final result = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: fixture.admission,
      captureResult: fixture.capture,
      episode: fixture.episode,
      mappings: fixture.mappings,
    );

    expect(result.status, SourceLiveCapturePlayableSourcePlanStatus.ready);
    expect(result.failureStage, isNull);
    expect(result.plan, isNotNull);
    final plan = result.plan!;
    expect(plan.installedPackage, same(fixture.installedPackage));
    expect(plan.programId, 'live');
    expect(plan.episode, same(fixture.episode));
    expect(plan.captureRequest, same(fixture.admission.request));
    expect(plan.captureRequest, same(fixture.captureRequest));
    expect(plan.captureResult, same(fixture.capture));
    expect(plan.mappings, hasLength(2));
    expect(plan.mappings[0], same(fixture.mappings[0]));
    expect(plan.mappings[1], same(fixture.mappings[1]));
  });

  test('keeps package admission failures before capture data', () {
    final result = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: SourceLiveCapturePackageResult(
        packageId: fixture.package.packageId,
        packageVersion: fixture.package.version,
        programId: fixture.packagePlan.programId,
        status: SourceLiveCapturePackageStatus.consentRequired,
        reasonCode: 'consent_required',
      ),
      captureResult: fixture.capture,
      episode: fixture.episode,
      mappings: fixture.mappings,
    );

    expect(result.status, SourceLiveCapturePlayableSourcePlanStatus.notReady);
    expect(
      result.failureStage,
      SourceLiveCapturePlayableSourcePlanFailureStage.packageAdmission,
    );
    expect(
      result.packageStatus,
      SourceLiveCapturePackageStatus.consentRequired,
    );
    expect(result.captureStatus, isNull);
    expect(result.reasonCode, 'consent_required');
    expect(result.plan, isNull);
  });

  test('keeps superseded and failed captures as typed capture failures', () {
    for (final status in [
      SourceLiveCaptureStatus.superseded,
      SourceLiveCaptureStatus.failed,
      SourceLiveCaptureStatus.closed,
    ]) {
      final result = coordinator.buildPlan(
        packagePlan: fixture.packagePlan,
        admission: fixture.admission,
        captureResult: SourceLiveCaptureResult(
          packageId: fixture.package.packageId,
          packageVersion: fixture.package.version,
          programId: fixture.packagePlan.programId,
          status: status,
          reasonCode: switch (status) {
            SourceLiveCaptureStatus.superseded => 'capture_superseded',
            SourceLiveCaptureStatus.failed => 'capture_failed',
            SourceLiveCaptureStatus.closed => 'capture_closed',
            _ => 'capture_not_available',
          },
        ),
        episode: fixture.episode,
        mappings: fixture.mappings,
      );

      expect(
        result.failureStage,
        SourceLiveCapturePlayableSourcePlanFailureStage.capture,
      );
      expect(result.captureStatus, status);
      expect(result.reasonCode, isNotNull);
      expect(result.plan, isNull);
    }
  });

  test('rejects admission and capture identity substitution', () {
    final substitutedRequest = SourceLiveCaptureRequest(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: fixture.packagePlan.programId,
      webCaptureRequest: _webRequest(fixture.policy),
    );
    final substitutedAdmission = SourceLiveCapturePackageResult(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: fixture.packagePlan.programId,
      status: SourceLiveCapturePackageStatus.ready,
      request: substitutedRequest,
    );
    final admissionMismatch = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: substitutedAdmission,
      captureResult: fixture.capture,
      episode: fixture.episode,
      mappings: fixture.mappings,
    );
    expect(
      admissionMismatch.failureStage,
      SourceLiveCapturePlayableSourcePlanFailureStage.plan,
    );
    expect(admissionMismatch.reasonCode, 'admission_request_mismatch');

    final foreignCapture = SourceLiveCaptureResult(
      packageId: 'foreign.source',
      packageVersion: fixture.package.version,
      programId: fixture.packagePlan.programId,
      status: SourceLiveCaptureStatus.captured,
      snapshot: fixture.capture.snapshot,
    );
    final captureMismatch = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: fixture.admission,
      captureResult: foreignCapture,
      episode: fixture.episode,
      mappings: fixture.mappings,
    );
    expect(
      captureMismatch.failureStage,
      SourceLiveCapturePlayableSourcePlanFailureStage.plan,
    );
    expect(captureMismatch.reasonCode, 'capture_result_mismatch');
  });

  test('rejects a package state change and unsafe episode identity', () {
    final changedPackageResult = coordinator.buildPlan(
      packagePlan: _replacePackagePlan(
        fixture.packagePlan,
        fixture.installedPackage.copyWith(status: SourcePackageStatus.disabled),
      ),
      admission: fixture.admission,
      captureResult: fixture.capture,
      episode: fixture.episode,
      mappings: fixture.mappings,
    );
    expect(
      changedPackageResult.failureStage,
      SourceLiveCapturePlayableSourcePlanFailureStage.plan,
    );
    expect(changedPackageResult.reasonCode, 'package_state_changed');

    final episodeMismatch = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: fixture.admission,
      captureResult: fixture.capture,
      episode: SourceEpisodeIdentity(
        sourceId: 'other.source',
        lineId: fixture.episode.lineId,
        subjectId: fixture.episode.subjectId,
        episodeId: fixture.episode.episodeId,
      ),
      mappings: fixture.mappings,
    );
    expect(
      episodeMismatch.failureStage,
      SourceLiveCapturePlayableSourcePlanFailureStage.plan,
    );
    expect(episodeMismatch.reasonCode, 'episode_source_mismatch');

    final invalidEpisode = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: fixture.admission,
      captureResult: fixture.capture,
      episode: SourceEpisodeIdentity(
        sourceId: fixture.episode.sourceId,
        lineId: ' main',
        subjectId: fixture.episode.subjectId,
        episodeId: fixture.episode.episodeId,
      ),
      mappings: fixture.mappings,
    );
    expect(invalidEpisode.reasonCode, 'invalid_episode_identity');
  });

  test('requires one explicit mapping for every captured candidate', () {
    for (final mappings in [
      <SourceLiveCapturePlayableSourceMapping>[],
      [
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: 0,
          sourceKey: 'primary',
          label: 'Primary',
        ),
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: 0,
          sourceKey: 'backup',
          label: 'Backup',
        ),
      ],
      [
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: 0,
          sourceKey: 'primary',
          label: 'Primary',
        ),
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: 2,
          sourceKey: 'backup',
          label: 'Backup',
        ),
      ],
    ]) {
      final result = coordinator.buildPlan(
        packagePlan: fixture.packagePlan,
        admission: fixture.admission,
        captureResult: fixture.capture,
        episode: fixture.episode,
        mappings: mappings,
      );
      expect(
        result.failureStage,
        SourceLiveCapturePlayableSourcePlanFailureStage.plan,
      );
      expect(result.reasonCode, 'candidate_mapping_invalid');
      expect(result.plan, isNull);
    }
  });

  test('allows an empty mapping for an empty accepted capture', () {
    final emptyCapture = SourceLiveCaptureResult(
      packageId: fixture.package.packageId,
      packageVersion: fixture.package.version,
      programId: fixture.packagePlan.programId,
      status: SourceLiveCaptureStatus.captured,
      snapshot: WebCaptureSnapshot(
        events: fixture.capture.snapshot!.events,
        candidates: const [],
        cookies: fixture.capture.snapshot!.cookies,
        stopReason: WebCaptureStopReason.completed,
        finalUri: fixture.capture.snapshot!.finalUri,
      ),
    );
    final result = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: fixture.admission,
      captureResult: emptyCapture,
      episode: fixture.episode,
      mappings: const [],
    );

    expect(result.status, SourceLiveCapturePlayableSourcePlanStatus.ready);
    expect(result.plan!.mappings, isEmpty);
    expect(result.plan!.captureResult, same(emptyCapture));
  });

  test('keeps result invariants and diagnostics bounded', () {
    expect(
      () => SourceLiveCapturePlayableSourcePlanResult(
        status: SourceLiveCapturePlayableSourcePlanStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlayableSourcePlanResult(
        status: SourceLiveCapturePlayableSourcePlanStatus.notReady,
        failureStage: SourceLiveCapturePlayableSourcePlanFailureStage.capture,
        captureStatus: SourceLiveCaptureStatus.captured,
        reasonCode: 'captured',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlayableSourcePlanResult(
        status: SourceLiveCapturePlayableSourcePlanStatus.notReady,
        failureStage:
            SourceLiveCapturePlayableSourcePlanFailureStage.packageAdmission,
        packageStatus: SourceLiveCapturePackageStatus.ready,
        reasonCode: 'ready',
      ),
      throwsArgumentError,
    );

    final result = coordinator.buildPlan(
      packagePlan: fixture.packagePlan,
      admission: fixture.admission,
      captureResult: fixture.capture,
      episode: fixture.episode,
      mappings: [
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: 0,
          sourceKey: 'primary',
          label: 'Primary',
        ),
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: 1,
          sourceKey: 'backup',
          label: 'Backup',
        ),
      ],
    );
    final diagnostic = result.toString();
    expect(diagnostic, contains('hasPlan: true'));
    expect(diagnostic, isNot(contains('media.example.com')));
    expect(diagnostic, isNot(contains('captured-cookie')));
    expect(diagnostic, isNot(contains('x-capture-value')));
  });
}

_Fixture _fixture() {
  final policy = testSourcePolicy(
    permissions: {
      SourcePermission.network,
      SourcePermission.webView,
      SourcePermission.mediaRequestInspection,
      SourcePermission.cookies,
    },
  );
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: 'Example Anime',
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy: policy,
    programs: [
      SourceRuleProgram(
        programId: 'live',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: 'body',
        ),
        fields: [
          SourceFieldRule(name: 'candidate', valueKind: SourceValueKind.text),
        ],
        resultLimit: 20,
      ),
    ],
  );
  final installed = InstalledSourcePackage(
    package: package,
    status: SourcePackageStatus.enabled,
    requiresConsent: false,
    requiresReconsent: false,
  );
  final webRequest = _webRequest(policy);
  final packagePlan = SourceLiveCapturePackagePlan(
    installedPackage: installed,
    programId: 'live',
    webCaptureRequest: webRequest,
  );
  final captureRequest = SourceLiveCaptureRequest(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    webCaptureRequest: webRequest,
  );
  final admission = SourceLiveCapturePackageResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    status: SourceLiveCapturePackageStatus.ready,
    request: captureRequest,
  );
  final capture = SourceLiveCaptureResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    status: SourceLiveCaptureStatus.captured,
    snapshot: _snapshot(),
  );
  final episode = SourceEpisodeIdentity(
    sourceId: package.packageId,
    lineId: 'main',
    subjectId: 'subject-1',
    episodeId: 'episode-1',
  );
  final mappings = [
    SourceLiveCapturePlayableSourceMapping(
      candidateIndex: 0,
      sourceKey: 'primary',
      label: 'Primary',
    ),
    SourceLiveCapturePlayableSourceMapping(
      candidateIndex: 1,
      sourceKey: 'backup',
      label: 'Backup',
    ),
  ];
  return _Fixture(
    policy: policy,
    package: package,
    installedPackage: installed,
    packagePlan: packagePlan,
    captureRequest: captureRequest,
    admission: admission,
    capture: capture,
    episode: episode,
    mappings: mappings,
  );
}

WebCaptureRequest _webRequest(SourceSecurityPolicy policy) => WebCaptureRequest(
  initialUri: Uri.parse('https://example.com/watch/episode-1'),
  securityPolicy: policy,
  budget: WebCaptureBudget(
    maxEvents: 20,
    maxCandidates: 10,
    maxHeaderBytes: 64 * 1024,
    maxCookieBytes: 64 * 1024,
  ),
  userAgentPolicy: WebUserAgentPolicy(mode: WebUserAgentMode.platformDefault),
  captureMediaRequests: true,
);

WebCaptureSnapshot _snapshot() {
  final page = Uri.parse('https://example.com/watch/episode-1');
  final primary = Uri.parse('https://media.example.com/video/primary.m3u8');
  final backup = Uri.parse('https://media.example.com/video/backup.mp4');
  final headers = const {'x-capture': 'x-capture-value'};
  return WebCaptureSnapshot(
    events: [
      WebCaptureEvent(
        sequence: 0,
        kind: WebRequestKind.resource,
        uri: primary,
        headers: headers,
      ),
      WebCaptureEvent(
        sequence: 1,
        kind: WebRequestKind.resource,
        uri: backup,
        headers: headers,
      ),
    ],
    candidates: [
      WebMediaCandidate(
        kind: WebCandidateKind.hls,
        uri: primary.replace(fragment: ''),
        headers: headers,
        sourceEventSequence: 0,
      ),
      WebMediaCandidate(
        kind: WebCandidateKind.video,
        uri: backup.replace(fragment: ''),
        headers: headers,
        sourceEventSequence: 1,
      ),
    ],
    cookies: [
      WebCaptureCookie(
        name: 'session',
        value: 'captured-cookie',
        domain: 'example.com',
      ),
    ],
    stopReason: WebCaptureStopReason.completed,
    finalUri: page,
  );
}

SourceLiveCapturePackagePlan _replacePackagePlan(
  SourceLiveCapturePackagePlan packagePlan,
  InstalledSourcePackage installed,
) => SourceLiveCapturePackagePlan(
  installedPackage: installed,
  programId: packagePlan.programId,
  webCaptureRequest: packagePlan.webCaptureRequest,
);

final class _Fixture {
  _Fixture({
    required this.policy,
    required this.package,
    required this.installedPackage,
    required this.packagePlan,
    required this.captureRequest,
    required this.admission,
    required this.capture,
    required this.episode,
    required this.mappings,
  });

  final SourceSecurityPolicy policy;
  final SourcePackageManifest package;
  final InstalledSourcePackage installedPackage;
  final SourceLiveCapturePackagePlan packagePlan;
  final SourceLiveCaptureRequest captureRequest;
  final SourceLiveCapturePackageResult admission;
  final SourceLiveCaptureResult capture;
  final SourceEpisodeIdentity episode;
  final List<SourceLiveCapturePlayableSourceMapping> mappings;
}
