import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_capture_playback_route_coordinator.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

void main() {
  final coordinator = SourceLiveCapturePlaybackRouteCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
  );

  test(
    'selects the first live source and preserves exact capture authority',
    () {
      final fixture = _fixture();

      final result = coordinator.selectRoute(
        installedPackage: fixture.installedPackage,
        playableResult: fixture.playableResult,
      );
      expect(result.status, SourceLiveCapturePlaybackRouteStatus.selected);
      expect(result.reasonCode, isNull);
      expect(result.route!.packageId, 'demo.source');
      expect(result.route!.packageVersion, Version.parse('1.0.0'));
      expect(result.route!.programId, 'live');
      expect(identical(result.route!.source, fixture.primary), isTrue);
      expect(
        identical(result.route!.source.candidate, fixture.primary.candidate),
        isTrue,
      );
      expect(
        identical(result.route!.captureResult, fixture.captureResult),
        isTrue,
      );
      expect(
        identical(result.route!.captureRequest, fixture.captureRequest),
        isTrue,
      );
      expect(result.route!.captureResult.snapshot!.cookies, hasLength(1));
      expect(result.route!.source.candidate.headers['x-capture'], 'primary');
      expect(result.route!.source.candidate.sourceEventSequence, 10);
    },
  );

  test('honors an exact preference and never falls back across identity', () {
    final fixture = _fixture(includeBackup: true);
    final preference = SourcePlaybackRoutePreference(
      packageId: 'demo.source',
      packageVersion: Version.parse('1.0.0'),
      programId: 'live',
      sourceKey: 'backup',
    );

    final selected = coordinator.selectRoute(
      installedPackage: fixture.installedPackage,
      playableResult: fixture.playableResult,
      preference: preference,
    );
    expect(selected.status, SourceLiveCapturePlaybackRouteStatus.selected);
    expect(selected.route!.source.source.sourceKey, 'backup');
    expect(identical(selected.route!.source, fixture.backup), isTrue);

    for (final mismatchedPreference in [
      SourcePlaybackRoutePreference(
        packageId: 'demo.source',
        packageVersion: Version.parse('2.0.0'),
        programId: 'live',
        sourceKey: 'backup',
      ),
      SourcePlaybackRoutePreference(
        packageId: 'demo.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'other_program',
        sourceKey: 'backup',
      ),
      SourcePlaybackRoutePreference(
        packageId: 'other.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'live',
        sourceKey: 'primary',
      ),
      SourcePlaybackRoutePreference(
        packageId: 'demo.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'live',
        sourceKey: 'missing',
      ),
    ]) {
      final noFallback = coordinator.selectRoute(
        installedPackage: fixture.installedPackage,
        playableResult: fixture.playableResult,
        preference: mismatchedPreference,
      );
      expect(
        noFallback.status,
        SourceLiveCapturePlaybackRouteStatus.preferredSourceNotFound,
      );
      expect(noFallback.route, isNull);
      expect(noFallback.reasonCode, 'preferred_source_not_found');
    }
  });

  test('enforces consent before disabled and blocks both without a route', () {
    final fixture = _fixture();
    final consent = coordinator.selectRoute(
      installedPackage: fixture.installedPackage.copyWith(
        status: SourcePackageStatus.disabled,
        requiresConsent: true,
      ),
      playableResult: fixture.playableResult,
    );
    expect(
      consent.status,
      SourceLiveCapturePlaybackRouteStatus.consentRequired,
    );
    expect(consent.reasonCode, 'consent_required');
    expect(consent.route, isNull);

    final disabled = coordinator.selectRoute(
      installedPackage: fixture.installedPackage.copyWith(
        status: SourcePackageStatus.disabled,
      ),
      playableResult: fixture.playableResult,
    );
    expect(disabled.status, SourceLiveCapturePlaybackRouteStatus.disabled);
    expect(disabled.reasonCode, 'package_disabled');
    expect(disabled.route, isNull);

    final reconsent = coordinator.selectRoute(
      installedPackage: fixture.installedPackage.copyWith(
        requiresReconsent: true,
      ),
      playableResult: fixture.playableResult,
    );
    expect(
      reconsent.status,
      SourceLiveCapturePlaybackRouteStatus.consentRequired,
    );
  });

  test('blocks incompatible packages and unknown programs before handoff', () {
    final incompatibleFixture = _fixture(
      wynimeVersionConstraint: VersionConstraint.parse('>=2.0.0 <3.0.0'),
    );
    final incompatible = coordinator.selectRoute(
      installedPackage: incompatibleFixture.installedPackage,
      playableResult: incompatibleFixture.playableResult,
    );
    expect(
      incompatible.status,
      SourceLiveCapturePlaybackRouteStatus.incompatible,
    );
    expect(incompatible.reasonCode, 'incompatible_wynime_version');

    final unknownProgram = _fixture(programId: 'unknown_program');
    final notFound = coordinator.selectRoute(
      installedPackage: unknownProgram.installedPackage,
      playableResult: unknownProgram.playableResult,
    );
    expect(notFound.status, SourceLiveCapturePlaybackRouteStatus.failed);
    expect(notFound.reasonCode, 'program_not_found');
    expect(notFound.route, isNull);
  });

  test('rejects playable results from another package or version', () {
    final installed = _fixture();
    final otherPackage = _fixture(packageId: 'other.source');
    final packageMismatch = coordinator.selectRoute(
      installedPackage: installed.installedPackage,
      playableResult: otherPackage.playableResult,
    );
    expect(packageMismatch.status, SourceLiveCapturePlaybackRouteStatus.failed);
    expect(packageMismatch.reasonCode, 'live_route_package_mismatch');

    final otherVersion = _fixture(packageVersion: Version.parse('2.0.0'));
    final versionMismatch = coordinator.selectRoute(
      installedPackage: installed.installedPackage,
      playableResult: otherVersion.playableResult,
    );
    expect(versionMismatch.status, SourceLiveCapturePlaybackRouteStatus.failed);
    expect(versionMismatch.reasonCode, 'live_route_version_mismatch');
  });

  test(
    'rejects a capture whose identity does not match the playable result',
    () {
      final fixture = _fixture();
      final otherCapture = _fixture(packageId: 'other.source').captureResult;
      expect(
        () => SourceLiveCapturePlayableSourceResult(
          packageId: fixture.playableResult.packageId,
          packageVersion: fixture.playableResult.packageVersion,
          programId: fixture.playableResult.programId,
          status: SourceLiveCapturePlayableSourceStatus.available,
          sources: [fixture.primary],
          diagnostics: const [],
          captureRequest: fixture.captureRequest,
          captureResult: otherCapture,
        ),
        throwsArgumentError,
      );
    },
  );

  test('rejects a candidate whose headers are forged after capture', () {
    final fixture = _fixture();
    final forgedCandidate = WebMediaCandidate(
      kind: fixture.primary.candidate.kind,
      uri: fixture.primary.candidate.uri,
      headers: const {'x-capture': 'forged'},
      sourceEventSequence: fixture.primary.candidate.sourceEventSequence,
    );
    final forgedSource = SourceLiveCapturePlayableSource(
      candidateIndex: 0,
      candidate: forgedCandidate,
      source: fixture.primary.source,
    );
    final forged = _availableResult(fixture, sources: [forgedSource]);

    final result = coordinator.selectRoute(
      installedPackage: fixture.installedPackage,
      playableResult: forged,
    );
    expect(result.status, SourceLiveCapturePlaybackRouteStatus.failed);
    expect(result.reasonCode, 'live_route_provenance_invalid');
  });

  test('rejects a snapshot candidate without matching event provenance', () {
    final fixture = _fixture();
    final forgedSnapshot = WebCaptureSnapshot(
      events: const [],
      candidates: [fixture.primary.candidate],
      cookies: fixture.captureResult.snapshot!.cookies,
      stopReason: WebCaptureStopReason.completed,
      finalUri: fixture.captureResult.snapshot!.finalUri,
    );
    final forgedCapture = SourceLiveCaptureResult(
      packageId: fixture.captureResult.packageId,
      packageVersion: fixture.captureResult.packageVersion,
      programId: fixture.captureResult.programId,
      status: SourceLiveCaptureStatus.captured,
      snapshot: forgedSnapshot,
    );
    final forged = SourceLiveCapturePlayableSourceResult(
      packageId: fixture.playableResult.packageId,
      packageVersion: fixture.playableResult.packageVersion,
      programId: fixture.playableResult.programId,
      status: SourceLiveCapturePlayableSourceStatus.available,
      sources: [fixture.primary],
      diagnostics: const [],
      captureRequest: fixture.captureRequest,
      captureResult: forgedCapture,
    );

    final result = coordinator.selectRoute(
      installedPackage: fixture.installedPackage,
      playableResult: forged,
    );
    expect(result.status, SourceLiveCapturePlaybackRouteStatus.failed);
    expect(result.reasonCode, 'live_route_provenance_invalid');
    expect(result.route, isNull);
  });

  test('rejects candidate URI, headers and kind forged against its event', () {
    final fixture = _fixture();
    final original = fixture.primary.candidate;
    final episode = fixture.primary.source.episode;
    final cases = [
      (
        event: WebCaptureEvent(
          sequence: original.sourceEventSequence,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://media.example.com/video/other.mp4'),
          headers: original.headers,
        ),
        candidate: original,
        source: fixture.primary.source,
      ),
      (
        event: WebCaptureEvent(
          sequence: original.sourceEventSequence,
          kind: WebRequestKind.resource,
          uri: original.uri,
          headers: const {'x-capture': 'forged'},
        ),
        candidate: original,
        source: fixture.primary.source,
      ),
      (
        event: WebCaptureEvent(
          sequence: original.sourceEventSequence,
          kind: WebRequestKind.resource,
          uri: original.uri,
          headers: original.headers,
        ),
        candidate: WebMediaCandidate(
          kind: WebCandidateKind.hls,
          uri: original.uri,
          headers: original.headers,
          sourceEventSequence: original.sourceEventSequence,
        ),
        source: SourcePlayableSource(
          episode: episode,
          sourceKey: 'forged-kind',
          label: 'Forged kind',
          kind: WebCandidateKind.hls,
          mediaUri: original.uri,
          pageUri: fixture.captureResult.snapshot!.finalUri,
        ),
      ),
    ];

    for (final item in cases) {
      final capture = SourceLiveCaptureResult(
        packageId: fixture.captureResult.packageId,
        packageVersion: fixture.captureResult.packageVersion,
        programId: fixture.captureResult.programId,
        status: SourceLiveCaptureStatus.captured,
        snapshot: WebCaptureSnapshot(
          events: [item.event],
          candidates: [item.candidate],
          cookies: fixture.captureResult.snapshot!.cookies,
          stopReason: WebCaptureStopReason.completed,
          finalUri: fixture.captureResult.snapshot!.finalUri,
        ),
      );
      final playable = SourceLiveCapturePlayableSourceResult(
        packageId: fixture.playableResult.packageId,
        packageVersion: fixture.playableResult.packageVersion,
        programId: fixture.playableResult.programId,
        status: SourceLiveCapturePlayableSourceStatus.available,
        sources: [
          SourceLiveCapturePlayableSource(
            candidateIndex: 0,
            source: item.source,
            candidate: item.candidate,
          ),
        ],
        diagnostics: const [],
        captureRequest: fixture.captureRequest,
        captureResult: capture,
      );

      final result = coordinator.selectRoute(
        installedPackage: fixture.installedPackage,
        playableResult: playable,
      );
      expect(result.status, SourceLiveCapturePlaybackRouteStatus.failed);
      expect(result.reasonCode, 'live_route_provenance_invalid');
      expect(result.route, isNull);
    }
  });

  test('rejects reordered or duplicate candidates that hide an omission', () {
    final fixture = _fixture(includeBackup: true);
    final episode = fixture.primary.source.episode;
    final duplicateSource = SourcePlayableSource(
      episode: episode,
      sourceKey: 'duplicate-primary',
      label: 'Duplicate primary',
      kind: fixture.primary.source.kind,
      mediaUri: fixture.primary.source.mediaUri,
      pageUri: fixture.primary.source.pageUri,
    );
    final cases = [
      (
        candidates: [fixture.backup.candidate, fixture.primary.candidate],
        sources: [
          SourceLiveCapturePlayableSource(
            candidateIndex: 0,
            source: fixture.backup.source,
            candidate: fixture.backup.candidate,
          ),
          SourceLiveCapturePlayableSource(
            candidateIndex: 1,
            source: fixture.primary.source,
            candidate: fixture.primary.candidate,
          ),
        ],
      ),
      (
        candidates: [fixture.primary.candidate, fixture.primary.candidate],
        sources: [
          fixture.primary,
          SourceLiveCapturePlayableSource(
            candidateIndex: 1,
            source: duplicateSource,
            candidate: fixture.primary.candidate,
          ),
        ],
      ),
    ];

    for (final item in cases) {
      final capture = SourceLiveCaptureResult(
        packageId: fixture.captureResult.packageId,
        packageVersion: fixture.captureResult.packageVersion,
        programId: fixture.captureResult.programId,
        status: SourceLiveCaptureStatus.captured,
        snapshot: WebCaptureSnapshot(
          events: fixture.captureResult.snapshot!.events,
          candidates: item.candidates,
          cookies: fixture.captureResult.snapshot!.cookies,
          stopReason: WebCaptureStopReason.completed,
          finalUri: fixture.captureResult.snapshot!.finalUri,
        ),
      );
      final forged = SourceLiveCapturePlayableSourceResult(
        packageId: fixture.playableResult.packageId,
        packageVersion: fixture.playableResult.packageVersion,
        programId: fixture.playableResult.programId,
        status: SourceLiveCapturePlayableSourceStatus.available,
        sources: item.sources,
        diagnostics: const [],
        captureRequest: fixture.captureRequest,
        captureResult: capture,
      );

      final result = coordinator.selectRoute(
        installedPackage: fixture.installedPackage,
        playableResult: forged,
      );
      expect(result.status, SourceLiveCapturePlaybackRouteStatus.failed);
      expect(result.reasonCode, 'live_route_provenance_invalid');
      expect(result.route, isNull);
    }
  });

  test('rejects a candidate carrying a non-normalized fragment', () {
    final fixture = _fixture();
    final original = fixture.primary.candidate;
    final forgedUri = Uri.parse(
      'https://media.example.com/video/primary.mp4#forged',
    );
    final forgedCandidate = WebMediaCandidate(
      kind: original.kind,
      uri: forgedUri,
      headers: original.headers,
      sourceEventSequence: original.sourceEventSequence,
    );
    final forgedSource = SourcePlayableSource(
      episode: fixture.primary.source.episode,
      sourceKey: fixture.primary.source.sourceKey,
      label: fixture.primary.source.label,
      kind: original.kind,
      mediaUri: forgedUri,
      pageUri: fixture.primary.source.pageUri,
    );
    final capture = SourceLiveCaptureResult(
      packageId: fixture.captureResult.packageId,
      packageVersion: fixture.captureResult.packageVersion,
      programId: fixture.captureResult.programId,
      status: SourceLiveCaptureStatus.captured,
      snapshot: WebCaptureSnapshot(
        events: fixture.captureResult.snapshot!.events,
        candidates: [forgedCandidate],
        cookies: fixture.captureResult.snapshot!.cookies,
        stopReason: WebCaptureStopReason.completed,
        finalUri: fixture.captureResult.snapshot!.finalUri,
      ),
    );
    final forged = SourceLiveCapturePlayableSourceResult(
      packageId: fixture.playableResult.packageId,
      packageVersion: fixture.playableResult.packageVersion,
      programId: fixture.playableResult.programId,
      status: SourceLiveCapturePlayableSourceStatus.available,
      sources: [
        SourceLiveCapturePlayableSource(
          candidateIndex: 0,
          source: forgedSource,
          candidate: forgedCandidate,
        ),
      ],
      diagnostics: const [],
      captureRequest: fixture.captureRequest,
      captureResult: capture,
    );

    final result = coordinator.selectRoute(
      installedPackage: fixture.installedPackage,
      playableResult: forged,
    );
    expect(result.status, SourceLiveCapturePlaybackRouteStatus.failed);
    expect(result.reasonCode, 'live_route_provenance_invalid');
    expect(result.route, isNull);
  });

  test('rejects duplicate or misindexed candidate provenance', () {
    final fixture = _fixture(includeBackup: true);
    final duplicateIndex = _availableResult(
      fixture,
      sources: [
        fixture.primary,
        SourceLiveCapturePlayableSource(
          candidateIndex: 0,
          candidate: fixture.backup.candidate,
          source: fixture.backup.source,
        ),
      ],
    );
    final duplicateResult = coordinator.selectRoute(
      installedPackage: fixture.installedPackage,
      playableResult: duplicateIndex,
    );
    expect(duplicateResult.reasonCode, 'live_route_provenance_invalid');

    final wrongIndex = _availableResult(
      fixture,
      sources: [
        SourceLiveCapturePlayableSource(
          candidateIndex: 1,
          candidate: fixture.primary.candidate,
          source: fixture.primary.source,
        ),
      ],
    );
    final wrongIndexResult = coordinator.selectRoute(
      installedPackage: fixture.installedPackage,
      playableResult: wrongIndex,
    );
    expect(wrongIndexResult.reasonCode, 'live_route_provenance_invalid');
  });

  test(
    'rejects duplicate source keys, unsupported kinds and disallowed URIs',
    () {
      final duplicateFixture = _fixture(includeBackup: true);
      final duplicateKey = _availableResult(
        duplicateFixture,
        sources: [
          duplicateFixture.primary,
          SourceLiveCapturePlayableSource(
            candidateIndex: 1,
            candidate: duplicateFixture.backup.candidate,
            source: SourcePlayableSource(
              episode: duplicateFixture.backup.source.episode,
              sourceKey: duplicateFixture.primary.source.sourceKey,
              label: 'Backup label',
              kind: duplicateFixture.backup.source.kind,
              mediaUri: duplicateFixture.backup.source.mediaUri,
              pageUri: duplicateFixture.backup.source.pageUri,
            ),
          ),
        ],
      );
      final duplicateKeyResult = coordinator.selectRoute(
        installedPackage: duplicateFixture.installedPackage,
        playableResult: duplicateKey,
      );
      expect(duplicateKeyResult.reasonCode, 'live_route_source_key_invalid');

      final unsupported = _fixture(kind: WebCandidateKind.dash);
      final unsupportedResult = coordinator.selectRoute(
        installedPackage: unsupported.installedPackage,
        playableResult: unsupported.playableResult,
      );
      expect(unsupportedResult.reasonCode, 'live_route_provenance_invalid');

      final disallowed = _fixture(mediaHost: 'outside.example');
      final disallowedResult = coordinator.selectRoute(
        installedPackage: disallowed.installedPackage,
        playableResult: disallowed.playableResult,
      );
      expect(disallowedResult.reasonCode, 'live_route_provenance_invalid');
    },
  );

  test('maps unavailable playable states without inventing a route', () {
    final fixture = _fixture();
    final expectations = {
      SourceLiveCapturePlayableSourceStatus.notFound:
          SourceLiveCapturePlaybackRouteStatus.notFound,
      SourceLiveCapturePlayableSourceStatus.consentRequired:
          SourceLiveCapturePlaybackRouteStatus.consentRequired,
      SourceLiveCapturePlayableSourceStatus.disabled:
          SourceLiveCapturePlaybackRouteStatus.disabled,
      SourceLiveCapturePlayableSourceStatus.incompatible:
          SourceLiveCapturePlaybackRouteStatus.incompatible,
      SourceLiveCapturePlayableSourceStatus.failed:
          SourceLiveCapturePlaybackRouteStatus.failed,
    };

    for (final entry in expectations.entries) {
      final playable = SourceLiveCapturePlayableSourceResult(
        packageId: 'demo.source',
        packageVersion: Version.parse('1.0.0'),
        programId: 'live',
        status: entry.key,
        sources: const [],
        diagnostics: const [],
        reasonCode: entry.key == SourceLiveCapturePlayableSourceStatus.failed
            ? 'capture_failed'
            : 'capture_not_available',
      );
      final result = coordinator.selectRoute(
        installedPackage: fixture.installedPackage,
        playableResult: playable,
      );
      expect(result.status, entry.value);
      expect(result.route, isNull);
      expect(result.reasonCode, isNotNull);
    }
  });

  test(
    'requires completed accepted snapshots and keeps result diagnostics safe',
    () {
      final fixture = _fixture();
      expect(
        () => SourceLiveCaptureResult(
          packageId: 'demo.source',
          packageVersion: Version.parse('1.0.0'),
          programId: 'live',
          status: SourceLiveCaptureStatus.captured,
          snapshot: WebCaptureSnapshot(
            events: fixture.captureResult.snapshot!.events,
            candidates: fixture.captureResult.snapshot!.candidates,
            cookies: fixture.captureResult.snapshot!.cookies,
            stopReason: WebCaptureStopReason.candidateBudgetExceeded,
            finalUri: fixture.captureResult.snapshot!.finalUri,
          ),
        ),
        throwsArgumentError,
      );

      final result = coordinator.selectRoute(
        installedPackage: fixture.installedPackage,
        playableResult: fixture.playableResult,
      );
      expect(result.toString(), isNot(contains('captured-value')));
      expect(result.toString(), isNot(contains('media.example.com')));
      expect(result.toString(), contains('x-capture'));
      expect(
        () => result.route!.source.candidate.headers['x-capture'] = 'changed',
        throwsUnsupportedError,
      );
    },
  );
}

final class _Fixture {
  _Fixture({
    required this.installedPackage,
    required this.playableResult,
    required this.captureRequest,
    required this.captureResult,
    required this.primary,
    required this.backup,
  });

  final InstalledSourcePackage installedPackage;
  final SourceLiveCapturePlayableSourceResult playableResult;
  final SourceLiveCaptureRequest captureRequest;
  final SourceLiveCaptureResult captureResult;
  final SourceLiveCapturePlayableSource primary;
  final SourceLiveCapturePlayableSource backup;
}

_Fixture _fixture({
  String packageId = 'demo.source',
  Version? packageVersion,
  String programId = 'live',
  VersionConstraint? wynimeVersionConstraint,
  WebCandidateKind kind = WebCandidateKind.video,
  String mediaHost = 'media.example.com',
  bool includeBackup = false,
}) {
  final version = packageVersion ?? Version.parse('1.0.0');
  final pageUri = Uri.parse('https://page.example.com/watch/episode-1');
  final primaryUri = Uri.parse(
    'https://$mediaHost/video/primary.mp4',
  ).replace(fragment: '');
  final backupUri = Uri.parse(
    'https://$mediaHost/video/backup.mp4',
  ).replace(fragment: '');
  final primaryCandidate = WebMediaCandidate(
    kind: kind,
    uri: primaryUri,
    headers: const {'x-capture': 'primary'},
    sourceEventSequence: 10,
  );
  final backupCandidate = WebMediaCandidate(
    kind: kind,
    uri: backupUri,
    headers: const {'x-capture': 'backup'},
    sourceEventSequence: 20,
  );
  final events = [
    WebCaptureEvent(
      sequence: 10,
      kind: WebRequestKind.resource,
      uri: primaryUri,
      headers: const {'x-capture': 'primary'},
    ),
    if (includeBackup)
      WebCaptureEvent(
        sequence: 20,
        kind: WebRequestKind.resource,
        uri: backupUri,
        headers: const {'x-capture': 'backup'},
      ),
  ];
  final candidates = [primaryCandidate, if (includeBackup) backupCandidate];
  final snapshot = WebCaptureSnapshot(
    events: events,
    candidates: candidates,
    cookies: [
      WebCaptureCookie(
        name: 'session',
        value: 'captured-value',
        domain: 'example.com',
      ),
    ],
    stopReason: WebCaptureStopReason.completed,
    finalUri: pageUri,
  );
  final captureResult = SourceLiveCaptureResult(
    packageId: packageId,
    packageVersion: version,
    programId: programId,
    status: SourceLiveCaptureStatus.captured,
    snapshot: snapshot,
  );
  final episode = SourceEpisodeIdentity(
    sourceId: packageId,
    lineId: 'line-1',
    subjectId: 'subject-1',
    episodeId: 'episode-1',
  );
  final primarySource = SourcePlayableSource(
    episode: episode,
    sourceKey: 'primary',
    label: 'Primary',
    kind: kind,
    mediaUri: primaryUri,
    pageUri: pageUri,
  );
  final backupSource = SourcePlayableSource(
    episode: episode,
    sourceKey: 'backup',
    label: 'Backup',
    kind: kind,
    mediaUri: backupUri,
    pageUri: pageUri,
  );
  final primary = SourceLiveCapturePlayableSource(
    candidateIndex: 0,
    source: primarySource,
    candidate: primaryCandidate,
  );
  final backup = SourceLiveCapturePlayableSource(
    candidateIndex: 1,
    source: backupSource,
    candidate: backupCandidate,
  );
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: 'Demo Source',
    version: version,
    wynimeVersionConstraint:
        wynimeVersionConstraint ?? VersionConstraint.parse('>=1.0.0 <2.0.0'),
    securityPolicy: _securityPolicy(),
    programs: [_program()],
  );
  final captureRequest = SourceLiveCaptureRequest(
    packageId: packageId,
    packageVersion: version,
    programId: programId,
    webCaptureRequest: WebCaptureRequest(
      initialUri: pageUri,
      securityPolicy: package.securityPolicy,
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
  final installedPackage = InstalledSourcePackage(
    package: package,
    status: SourcePackageStatus.enabled,
    requiresConsent: false,
    requiresReconsent: false,
  );
  final playableResult = SourceLiveCapturePlayableSourceResult(
    packageId: packageId,
    packageVersion: version,
    programId: programId,
    status: SourceLiveCapturePlayableSourceStatus.available,
    sources: [primary, if (includeBackup) backup],
    diagnostics: const <SourcePlayableSourceNormalizationDiagnostic>[],
    captureRequest: captureRequest,
    captureResult: captureResult,
  );
  return _Fixture(
    installedPackage: installedPackage,
    playableResult: playableResult,
    captureRequest: captureRequest,
    captureResult: captureResult,
    primary: primary,
    backup: backup,
  );
}

SourceLiveCapturePlayableSourceResult _availableResult(
  _Fixture fixture, {
  required Iterable<SourceLiveCapturePlayableSource> sources,
}) {
  return SourceLiveCapturePlayableSourceResult(
    packageId: fixture.playableResult.packageId,
    packageVersion: fixture.playableResult.packageVersion,
    programId: fixture.playableResult.programId,
    status: SourceLiveCapturePlayableSourceStatus.available,
    sources: sources,
    diagnostics: const [],
    captureRequest: fixture.captureRequest,
    captureResult: fixture.captureResult,
  );
}

SourceSecurityPolicy _securityPolicy() => SourceSecurityPolicy(
  allowedDomains: [
    SourceDomainRule(host: 'example.com', includeSubdomains: true),
  ],
  permissions: const {
    SourcePermission.network,
    SourcePermission.cookies,
    SourcePermission.webView,
    SourcePermission.mediaRequestInspection,
    SourcePermission.desktopUserAgent,
  },
  budget: SourceResourceBudget(
    maxDocumentBytes: 1024,
    maxRecords: 10,
    maxSelectorMatches: 10,
    maxEvaluationSteps: 100,
    maxRegexPatternChars: 32,
    maxRegexInputChars: 128,
    maxRedirects: 2,
  ),
);

SourceRuleProgram _program() => SourceRuleProgram(
  programId: 'live',
  documentKind: SourceDocumentKind.html,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.css,
    expression: 'body',
  ),
  fields: [SourceFieldRule(name: 'value', valueKind: SourceValueKind.text)],
  resultLimit: 1,
);
