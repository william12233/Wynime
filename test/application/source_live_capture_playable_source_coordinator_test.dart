import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_coordinator.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'normalizes accepted candidates while preserving capture provenance',
    () {
      final plan = _plan();
      final snapshot = plan.captureResult.snapshot!;
      expect(
        snapshot.candidates.first.uri,
        snapshot.events.first.uri.replace(fragment: ''),
      );
      expect(snapshot.candidates.first.headers, snapshot.events.first.headers);

      final result = _coordinator().normalize(plan);

      expect(
        result.status,
        SourceLiveCapturePlayableSourceStatus.available,
        reason: result.toString(),
      );
      expect(identical(result.captureRequest, plan.captureRequest), isTrue);
      expect(result.sources.map((item) => item.source.sourceKey), [
        'primary',
        'backup',
      ]);
      expect(result.sources.map((item) => item.candidateIndex), [0, 1]);
      expect(result.sources.first.candidate.sourceEventSequence, 0);
      expect(
        result.sources.first.candidate.headers['referer'],
        'https://example.com/watch/episode-1',
      );
      expect(result.captureResult!.snapshot!.cookies.single.name, 'session');
      expect(result.captureResult!.snapshot!.cookies.single.value, 'secret');
    },
  );

  test(
    'does not retain a non-captured result or its reason as capture data',
    () {
      final plan = _plan(
        captureResult: SourceLiveCaptureResult(
          packageId: 'example.anime',
          packageVersion: Version.parse('1.0.0'),
          programId: 'playback',
          status: SourceLiveCaptureStatus.failed,
          reasonCode: 'capture_failed',
        ),
      );

      final result = _coordinator().normalize(plan);

      expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
      expect(result.reasonCode, 'capture_failed');
      expect(result.captureResult, isNull);
      expect(result.sources, isEmpty);
    },
  );

  test('rechecks package, version, program and policy provenance', () {
    final base = _plan();
    final mismatchedRequest = SourceLiveCaptureRequest(
      packageId: 'other.anime',
      packageVersion: Version.parse('1.0.0'),
      programId: 'playback',
      webCaptureRequest: base.captureRequest.webCaptureRequest,
    );
    final mismatched = _plan(captureRequest: mismatchedRequest);
    final policy = testSourcePolicy(
      domains: [
        SourceDomainRule(host: 'example.com', includeSubdomains: true),
        SourceDomainRule(host: 'other.example', includeSubdomains: true),
      ],
      permissions: {
        SourcePermission.network,
        SourcePermission.webView,
        SourcePermission.mediaRequestInspection,
        SourcePermission.cookies,
      },
    );
    final broaderRequest = SourceLiveCaptureRequest(
      packageId: 'example.anime',
      packageVersion: Version.parse('1.0.0'),
      programId: 'playback',
      webCaptureRequest: _request(policy: policy),
    );
    final broader = _plan(captureRequest: broaderRequest);

    expect(
      _coordinator().normalize(mismatched).reasonCode,
      'capture_package_mismatch',
    );
    expect(
      _coordinator().normalize(broader).reasonCode,
      'capture_policy_mismatch',
    );
  });

  test('package lifecycle and compatibility gates run before capture use', () {
    final consent = _coordinator().normalize(
      _plan(installedPackage: _installed(requiresConsent: true)),
    );
    final disabled = _coordinator().normalize(
      _plan(installedPackage: _installed(status: SourcePackageStatus.disabled)),
    );
    final incompatible = _coordinator().normalize(
      _plan(
        installedPackage: _installed(
          package: _package(constraint: VersionConstraint.parse('^2.0.0')),
        ),
      ),
    );

    expect(
      consent.status,
      SourceLiveCapturePlayableSourceStatus.consentRequired,
    );
    expect(consent.reasonCode, 'consent_required');
    expect(disabled.status, SourceLiveCapturePlayableSourceStatus.disabled);
    expect(disabled.reasonCode, 'package_disabled');
    expect(
      incompatible.status,
      SourceLiveCapturePlayableSourceStatus.incompatible,
    );
    expect(incompatible.reasonCode, 'incompatible_wynime_version');
  });

  test('rejects invalid episode and unknown program identities', () {
    final wrongEpisode = _coordinator().normalize(
      _plan(
        episode: SourceEpisodeIdentity(
          sourceId: 'other.anime',
          lineId: 'main',
          subjectId: 'subject-1',
          episodeId: 'episode-1',
        ),
      ),
    );
    final unknownProgram = _coordinator().normalize(
      _plan(programId: 'unknown'),
    );

    expect(wrongEpisode.reasonCode, 'episode_source_mismatch');
    expect(unknownProgram.reasonCode, 'program_not_found');
  });

  test('revalidates a forged completed snapshot before mapping candidates', () {
    final request = _captureRequest();
    final forged = SourceLiveCaptureResult(
      packageId: 'example.anime',
      packageVersion: Version.parse('1.0.0'),
      programId: 'playback',
      status: SourceLiveCaptureStatus.captured,
      snapshot: WebCaptureSnapshot(
        events: [
          WebCaptureEvent(
            sequence: 0,
            kind: WebRequestKind.resource,
            uri: Uri.parse('https://cdn.example.com/video.mp4'),
          ),
        ],
        candidates: [
          WebMediaCandidate(
            kind: WebCandidateKind.video,
            uri: Uri.parse('https://cdn.example.com/other.mp4'),
            headers: const {},
            sourceEventSequence: 0,
          ),
        ],
        cookies: const [],
        stopReason: WebCaptureStopReason.completed,
        finalUri: Uri.parse('https://example.com/watch/episode-1'),
      ),
    );

    final result = _coordinator().normalize(
      _plan(captureRequest: request, captureResult: forged),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'candidate_provenance_invalid');
    expect(result.captureResult, isNull);
  });

  test('rejects a candidate kind forged against the shared classifier', () {
    final request = _captureRequest();
    final snapshot = WebCaptureSnapshot(
      events: [
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://cdn.example.com/manifest.mpd'),
        ),
      ],
      candidates: [
        WebMediaCandidate(
          kind: WebCandidateKind.hls,
          uri: Uri.parse(
            'https://cdn.example.com/manifest.mpd',
          ).replace(fragment: ''),
          headers: const {},
          sourceEventSequence: 0,
        ),
      ],
      cookies: const [],
      stopReason: WebCaptureStopReason.completed,
      finalUri: Uri.parse('https://example.com/watch/episode-1'),
    );
    final result = _coordinator().normalize(
      _plan(
        captureRequest: request,
        captureResult: SourceLiveCaptureResult(
          packageId: request.packageId,
          packageVersion: request.packageVersion,
          programId: request.programId,
          status: SourceLiveCaptureStatus.captured,
          snapshot: snapshot,
        ),
        mappings: [_mapping(0, 'forged')],
      ),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'candidate_kind_invalid');
    expect(result.sources, isEmpty);
    expect(result.captureResult, isNull);

    final segmentUri = Uri.parse('https://cdn.example.com/video/segment.m4s');
    final segmentResult = _coordinator().normalize(
      _plan(
        captureRequest: request,
        captureResult: SourceLiveCaptureResult(
          packageId: request.packageId,
          packageVersion: request.packageVersion,
          programId: request.programId,
          status: SourceLiveCaptureStatus.captured,
          snapshot: WebCaptureSnapshot(
            events: [
              WebCaptureEvent(
                sequence: 0,
                kind: WebRequestKind.resource,
                uri: segmentUri,
              ),
            ],
            candidates: [
              WebMediaCandidate(
                kind: WebCandidateKind.video,
                uri: segmentUri.replace(fragment: ''),
                headers: const {},
                sourceEventSequence: 0,
              ),
            ],
            cookies: const [],
            stopReason: WebCaptureStopReason.completed,
            finalUri: Uri.parse('https://example.com/watch/episode-1'),
          ),
        ),
        mappings: [_mapping(0, 'forged-segment')],
      ),
    );

    expect(segmentResult.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(segmentResult.reasonCode, 'candidate_kind_invalid');
    expect(segmentResult.sources, isEmpty);
    expect(segmentResult.captureResult, isNull);

    final originalFragmentUri = Uri.parse(
      'https://cdn.example.com/video/master.m3u8#original',
    );
    final forgedFragmentUri = Uri.parse(
      'https://cdn.example.com/video/master.m3u8#forged',
    );
    final fragmentResult = _coordinator().normalize(
      _plan(
        captureRequest: request,
        captureResult: SourceLiveCaptureResult(
          packageId: request.packageId,
          packageVersion: request.packageVersion,
          programId: request.programId,
          status: SourceLiveCaptureStatus.captured,
          snapshot: WebCaptureSnapshot(
            events: [
              WebCaptureEvent(
                sequence: 0,
                kind: WebRequestKind.resource,
                uri: originalFragmentUri,
              ),
            ],
            candidates: [
              WebMediaCandidate(
                kind: WebCandidateKind.hls,
                uri: forgedFragmentUri,
                headers: const {},
                sourceEventSequence: 0,
              ),
            ],
            cookies: const [],
            stopReason: WebCaptureStopReason.completed,
            finalUri: Uri.parse('https://example.com/watch/episode-1'),
          ),
        ),
        mappings: [_mapping(0, 'forged-fragment')],
      ),
    );

    expect(fragmentResult.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(fragmentResult.reasonCode, 'candidate_provenance_invalid');
    expect(fragmentResult.sources, isEmpty);
    expect(fragmentResult.captureResult, isNull);
  });

  test('rejects a candidate that points at a later duplicate observation', () {
    final request = _captureRequest();
    final mediaUri = Uri.parse('https://cdn.example.com/video/repeated.m3u8');
    final firstHeaders = const {'X-Source': 'first'};
    final secondHeaders = const {'X-Source': 'second'};
    final result = _coordinator().normalize(
      _plan(
        captureRequest: request,
        captureResult: SourceLiveCaptureResult(
          packageId: request.packageId,
          packageVersion: request.packageVersion,
          programId: request.programId,
          status: SourceLiveCaptureStatus.captured,
          snapshot: WebCaptureSnapshot(
            events: [
              WebCaptureEvent(
                sequence: 0,
                kind: WebRequestKind.resource,
                uri: mediaUri,
                headers: firstHeaders,
              ),
              WebCaptureEvent(
                sequence: 1,
                kind: WebRequestKind.resource,
                uri: mediaUri,
                headers: secondHeaders,
              ),
            ],
            candidates: [
              WebMediaCandidate(
                kind: WebCandidateKind.hls,
                uri: mediaUri.replace(fragment: ''),
                headers: secondHeaders,
                sourceEventSequence: 1,
              ),
            ],
            cookies: const [],
            stopReason: WebCaptureStopReason.completed,
            finalUri: Uri.parse('https://example.com/watch/episode-1'),
          ),
        ),
        mappings: [_mapping(0, 'late-duplicate')],
      ),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'candidate_provenance_invalid');
    expect(result.sources, isEmpty);
    expect(result.captureResult, isNull);
  });

  test(
    'rejects a completed snapshot that omits an event-derived candidate',
    () {
      final plan = _plan();
      final snapshot = plan.captureResult.snapshot!;
      final result = _coordinator().normalize(
        _plan(
          captureRequest: plan.captureRequest,
          captureResult: _capturedFromSnapshot(
            snapshot,
            candidates: [snapshot.candidates.first],
          ),
          mappings: [_mapping(0, 'primary')],
        ),
      );

      expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
      expect(result.reasonCode, 'candidate_provenance_invalid');
      expect(result.sources, isEmpty);
      expect(result.captureResult, isNull);
    },
  );

  test('rejects a completed snapshot with a duplicated candidate', () {
    final plan = _plan();
    final snapshot = plan.captureResult.snapshot!;
    final result = _coordinator().normalize(
      _plan(
        captureRequest: plan.captureRequest,
        captureResult: _capturedFromSnapshot(
          snapshot,
          candidates: [snapshot.candidates.first, snapshot.candidates.first],
        ),
      ),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'candidate_provenance_invalid');
    expect(result.sources, isEmpty);
    expect(result.captureResult, isNull);
  });

  test('rejects a completed snapshot with reordered candidates', () {
    final plan = _plan();
    final snapshot = plan.captureResult.snapshot!;
    final result = _coordinator().normalize(
      _plan(
        captureRequest: plan.captureRequest,
        captureResult: _capturedFromSnapshot(
          snapshot,
          candidates: [snapshot.candidates[1], snapshot.candidates[0]],
        ),
      ),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'candidate_provenance_invalid');
    expect(result.sources, isEmpty);
    expect(result.captureResult, isNull);
  });

  test('rejects an event-derived candidate set that exceeds its budget', () {
    final request = _captureRequest(
      budget: WebCaptureBudget(
        maxEvents: 20,
        maxCandidates: 1,
        maxHeaderBytes: 64 * 1024,
        maxCookieBytes: 64 * 1024,
      ),
    );
    final base = _plan(captureRequest: request);
    final snapshot = base.captureResult.snapshot!;
    final result = _coordinator().normalize(
      _plan(
        captureRequest: request,
        captureResult: _capturedFromSnapshot(
          snapshot,
          candidates: [snapshot.candidates.first],
        ),
        mappings: [_mapping(0, 'primary')],
      ),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'capture_candidate_budget_exceeded');
    expect(result.sources, isEmpty);
    expect(result.captureResult, isNull);
  });

  test('requires a complete one-to-one candidate mapping', () {
    final duplicate = _coordinator().normalize(
      _plan(mappings: [_mapping(0, 'primary'), _mapping(0, 'backup')]),
    );
    final missing = _coordinator().normalize(
      _plan(mappings: [_mapping(0, 'primary')]),
    );

    expect(duplicate.reasonCode, 'candidate_mapping_invalid');
    expect(missing.reasonCode, 'candidate_mapping_invalid');
  });

  test('skips unsupported candidates but keeps supported candidates', () {
    final plan = _plan(kinds: [WebCandidateKind.dash, WebCandidateKind.hls]);

    final result = _coordinator().normalize(plan);

    expect(result.status, SourceLiveCapturePlayableSourceStatus.available);
    expect(result.sources.single.source.sourceKey, 'backup');
    expect(result.sources.single.candidateIndex, 1);
    expect(result.diagnostics.single.code, 'playable_kind_unsupported');
  });

  test('keeps the first duplicate source key deterministically', () {
    final result = _coordinator().normalize(
      _plan(mappings: [_mapping(0, 'same'), _mapping(1, 'same')]),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.available);
    expect(result.sources.single.candidateIndex, 0);
    expect(result.diagnostics.single.code, 'duplicate_playable_source_key');
  });

  test('reports a completed capture with no candidates as not found', () {
    final plan = _plan(
      captureResult: SourceLiveCaptureResult(
        packageId: 'example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'playback',
        status: SourceLiveCaptureStatus.captured,
        snapshot: WebCaptureSnapshot(
          events: const [],
          candidates: const [],
          cookies: const [],
          stopReason: WebCaptureStopReason.completed,
          finalUri: Uri.parse('https://example.com/watch/episode-1'),
        ),
      ),
      mappings: const [],
    );

    final result = _coordinator().normalize(plan);

    expect(result.status, SourceLiveCapturePlayableSourceStatus.notFound);
    expect(result.reasonCode, 'no_media_candidates');
    expect(result.captureResult, isNull);
  });

  test('fails when all captured candidates are unsupported', () {
    final result = _coordinator().normalize(
      _plan(kinds: [WebCandidateKind.dash, WebCandidateKind.mediaSegment]),
    );

    expect(result.status, SourceLiveCapturePlayableSourceStatus.failed);
    expect(result.reasonCode, 'no_supported_candidates');
    expect(result.sources, isEmpty);
    expect(result.diagnostics, hasLength(2));
  });

  test(
    'diagnostics are immutable and redact captured URL/header/cookie values',
    () {
      final result = _coordinator().normalize(_plan());

      expect(
        () => result.sources.add(result.sources.first),
        throwsUnsupportedError,
      );
      expect(result.toString(), isNot(contains('episode-1')));
      expect(result.toString(), isNot(contains('secret')));
      expect(result.toString(), isNot(contains('https://')));
    },
  );
}

SourceLiveCapturePlayableSourceCoordinator _coordinator() =>
    SourceLiveCapturePlayableSourceCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
    );

SourceLiveCapturePlayableSourcePlan _plan({
  InstalledSourcePackage? installedPackage,
  String programId = 'playback',
  SourceEpisodeIdentity? episode,
  SourceLiveCaptureRequest? captureRequest,
  SourceLiveCaptureResult? captureResult,
  Iterable<SourceLiveCapturePlayableSourceMapping>? mappings,
  List<WebCandidateKind>? kinds,
}) {
  final package = installedPackage?.package ?? _package();
  final request =
      captureRequest ?? _captureRequest(policy: package.securityPolicy);
  return SourceLiveCapturePlayableSourcePlan(
    installedPackage: installedPackage ?? _installed(package: package),
    programId: programId,
    episode: episode ?? _episode(),
    captureRequest: request,
    captureResult: captureResult ?? _captured(kinds: kinds),
    mappings: mappings ?? [_mapping(0, 'primary'), _mapping(1, 'backup')],
  );
}

SourceLiveCapturePlayableSourceMapping _mapping(int index, String key) =>
    SourceLiveCapturePlayableSourceMapping(
      candidateIndex: index,
      sourceKey: key,
      label: key == 'primary' ? 'Primary' : 'Backup',
    );

SourceLiveCaptureResult _capturedFromSnapshot(
  WebCaptureSnapshot original, {
  required Iterable<WebMediaCandidate> candidates,
}) => SourceLiveCaptureResult(
  packageId: 'example.anime',
  packageVersion: Version.parse('1.0.0'),
  programId: 'playback',
  status: SourceLiveCaptureStatus.captured,
  snapshot: WebCaptureSnapshot(
    events: original.events,
    candidates: candidates,
    cookies: original.cookies,
    stopReason: original.stopReason,
    finalUri: original.finalUri,
  ),
);

SourceEpisodeIdentity _episode({String sourceId = 'example.anime'}) =>
    SourceEpisodeIdentity(
      sourceId: sourceId,
      lineId: 'main',
      subjectId: 'subject-1',
      episodeId: 'episode-1',
    );

InstalledSourcePackage _installed({
  SourcePackageManifest? package,
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => InstalledSourcePackage(
  package: package ?? _package(),
  status: status,
  requiresConsent: requiresConsent,
  requiresReconsent: requiresReconsent,
);

SourcePackageManifest _package({
  VersionConstraint? constraint,
  SourceSecurityPolicy? policy,
}) => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
  securityPolicy: policy ?? _policy(),
  programs: [
    SourceRuleProgram(
      programId: 'playback',
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

SourceSecurityPolicy _policy() => testSourcePolicy(
  permissions: {
    SourcePermission.network,
    SourcePermission.webView,
    SourcePermission.mediaRequestInspection,
    SourcePermission.cookies,
  },
);

WebCaptureRequest _request({
  SourceSecurityPolicy? policy,
  WebCaptureBudget? budget,
}) => WebCaptureRequest(
  initialUri: Uri.parse('https://example.com/watch/episode-1'),
  securityPolicy: policy ?? _policy(),
  budget:
      budget ??
      WebCaptureBudget(
        maxEvents: 20,
        maxCandidates: 10,
        maxHeaderBytes: 64 * 1024,
        maxCookieBytes: 64 * 1024,
      ),
  userAgentPolicy: WebUserAgentPolicy(mode: WebUserAgentMode.platformDefault),
  captureMediaRequests: true,
);

SourceLiveCaptureRequest _captureRequest({
  SourceSecurityPolicy? policy,
  WebCaptureBudget? budget,
}) {
  final request = _request(policy: policy, budget: budget);
  return SourceLiveCaptureRequest(
    packageId: 'example.anime',
    packageVersion: Version.parse('1.0.0'),
    programId: 'playback',
    webCaptureRequest: request,
  );
}

SourceLiveCaptureResult _captured({List<WebCandidateKind>? kinds}) {
  return SourceLiveCaptureResult(
    packageId: 'example.anime',
    packageVersion: Version.parse('1.0.0'),
    programId: 'playback',
    status: SourceLiveCaptureStatus.captured,
    snapshot: _snapshot(kinds: kinds),
  );
}

WebCaptureSnapshot _snapshot({List<WebCandidateKind>? kinds}) {
  final page = Uri.parse('https://example.com/watch/episode-1');
  final candidateKinds =
      kinds ?? [WebCandidateKind.hls, WebCandidateKind.video];
  final media = [
    for (var index = 0; index < candidateKinds.length; index++)
      _mediaUriForKind(candidateKinds[index], index),
  ];
  final headers = const {'Referer': 'https://example.com/watch/episode-1'};
  final events = [
    for (var index = 0; index < media.length; index++)
      WebCaptureEvent(
        sequence: index,
        kind: WebRequestKind.resource,
        uri: media[index],
        headers: headers,
      ),
  ];
  return WebCaptureSnapshot(
    events: events,
    candidates: [
      for (var index = 0; index < media.length; index++)
        WebMediaCandidate(
          kind: candidateKinds[index],
          uri: media[index].replace(fragment: ''),
          headers: headers,
          sourceEventSequence: index,
        ),
    ],
    cookies: [
      WebCaptureCookie(name: 'session', value: 'secret', domain: 'example.com'),
    ],
    stopReason: WebCaptureStopReason.completed,
    finalUri: page,
  );
}

Uri _mediaUriForKind(WebCandidateKind kind, int index) {
  final name = index == 0 ? 'master' : 'backup';
  final suffix = switch (kind) {
    WebCandidateKind.hls => '.m3u8',
    WebCandidateKind.dash => '.mpd',
    WebCandidateKind.video => '.mp4',
    WebCandidateKind.audio => '.mp3',
    WebCandidateKind.mediaSegment => '.m4s',
  };
  return Uri.parse('https://cdn.example.com/video/$name$suffix');
}
