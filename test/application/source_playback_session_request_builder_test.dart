import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/infrastructure/playback/default_playback_session_resolver.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const builder = DeterministicSourcePlaybackSessionRequestBuilder();
  final package = _package();
  final episode = _episode();
  final route = _route(episode);

  test('builds one resolver request from the selected route', () {
    final plan = _adPlan(episode);
    final result = builder.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: plan,
      sourceEventSequence: 17,
    );

    expect(result.status, SourcePlaybackSessionRequestBuildStatus.ready);
    final request = result.request!;
    expect(request.episode, episode);
    expect(request.pageUri, route.source.pageUri);
    expect(request.candidate.kind, WebCandidateKind.hls);
    expect(request.candidate.uri, route.source.mediaUri);
    expect(request.candidate.headers, isEmpty);
    expect(request.candidate.sourceEventSequence, 17);
    expect(request.cookies, isEmpty);
    expect(request.userAgent, isNull);
    expect(request.securityPolicy, same(package.securityPolicy));
    expect(request.adRemovalPlan, same(plan));
  });

  test(
    'the existing resolver accepts the built request as its authority',
    () async {
      final result = builder.buildRequest(
        route: route,
        package: package,
        adRemovalPlan: _adPlan(episode),
        sourceEventSequence: 17,
      );
      final session = await DefaultPlaybackSessionResolver(
        idGenerator: _FixedIdGenerator('session-from-route'),
      ).resolve(result.request!);

      expect(session.sessionId, 'session-from-route');
      expect(session.episode, episode);
      expect(session.mediaUri, route.source.mediaUri);
      expect(session.pageUri, route.source.pageUri);
      expect(session.headers, isEmpty);
      expect(session.cookies, isEmpty);
    },
  );

  test('requires exact package, version, program and ad-plan identity', () {
    final cases =
        <SourcePlaybackSessionRequestBuildStatus, SourcePlaybackRoute>{
          SourcePlaybackSessionRequestBuildStatus.packageMismatch: _route(
            episode,
          ),
          SourcePlaybackSessionRequestBuildStatus.versionMismatch: route,
          SourcePlaybackSessionRequestBuildStatus.programNotFound:
              SourcePlaybackRoute(
                packageId: package.packageId,
                packageVersion: package.version,
                programId: 'other',
                source: route.source,
              ),
        };

    final mismatchPackage = SourcePackageManifest(
      schemaVersion: 1,
      packageId: 'other.anime',
      displayName: 'Other Anime',
      version: package.version,
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      securityPolicy: package.securityPolicy,
      programs: [_program()],
    );
    final versionPackage = _package(version: Version.parse('2.0.0'));
    for (final entry in cases.entries) {
      final selectedPackage = switch (entry.key) {
        SourcePlaybackSessionRequestBuildStatus.packageMismatch =>
          mismatchPackage,
        SourcePlaybackSessionRequestBuildStatus.versionMismatch =>
          versionPackage,
        _ => package,
      };
      final result = builder.buildRequest(
        route: entry.value,
        package: selectedPackage,
        adRemovalPlan: _adPlan(episode),
        sourceEventSequence: 0,
      );
      expect(result.status, entry.key);
      expect(result.request, isNull);
      expect(result.reasonCode, isNotNull);
    }

    final version = builder.buildRequest(
      route: route,
      package: versionPackage,
      adRemovalPlan: _adPlan(episode),
      sourceEventSequence: 0,
    );
    expect(
      version.status,
      SourcePlaybackSessionRequestBuildStatus.versionMismatch,
    );

    final adPlan = builder.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: _adPlan(_episode(lineId: 'other-line')),
      sourceEventSequence: 0,
    );
    expect(
      adPlan.status,
      SourcePlaybackSessionRequestBuildStatus.adRemovalPlanMismatch,
    );
  });

  test('revalidates both route URIs against the package policy', () {
    final outsideMedia = _route(
      episode,
      mediaUri: Uri.parse('https://outside.example/video.mp4'),
    );
    final mediaResult = builder.buildRequest(
      route: outsideMedia,
      package: package,
      adRemovalPlan: _adPlan(episode),
      sourceEventSequence: 0,
    );
    expect(
      mediaResult.status,
      SourcePlaybackSessionRequestBuildStatus.mediaUriNotAllowed,
    );

    final outsidePage = _route(
      episode,
      pageUri: Uri.parse('https://outside.example/watch/episode-1'),
    );
    final pageResult = builder.buildRequest(
      route: outsidePage,
      package: package,
      adRemovalPlan: _adPlan(episode),
      sourceEventSequence: 0,
    );
    expect(
      pageResult.status,
      SourcePlaybackSessionRequestBuildStatus.pageUriNotAllowed,
    );
    expect(mediaResult.request, isNull);
    expect(pageResult.request, isNull);
  });

  test('rejects invalid source sequence before creating a request', () {
    final result = builder.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: _adPlan(episode),
      sourceEventSequence: -1,
    );

    expect(
      result.status,
      SourcePlaybackSessionRequestBuildStatus.invalidSourceEventSequence,
    );
    expect(result.reasonCode, 'invalid_source_event_sequence');
    expect(result.request, isNull);
  });

  test('result invariants and diagnostics remain bounded and redacted', () {
    expect(
      () => SourcePlaybackSessionRequestBuildResult(
        status: SourcePlaybackSessionRequestBuildStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackSessionRequestBuildResult(
        status: SourcePlaybackSessionRequestBuildStatus.packageMismatch,
      ),
      throwsArgumentError,
    );
    final ready = builder.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: _adPlan(episode),
      sourceEventSequence: 0,
    );
    expect(
      () => SourcePlaybackSessionRequestBuildResult(
        status: SourcePlaybackSessionRequestBuildStatus.packageMismatch,
        request: ready.request,
        reasonCode: 'package_identity_mismatch',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackSessionRequestBuildResult(
        status: SourcePlaybackSessionRequestBuildStatus.packageMismatch,
        reasonCode: 'Bad Code',
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

SourcePackageManifest _package({
  Version? version,
  SourceSecurityPolicy? policy,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: 'Example Anime',
    version: version ?? Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy:
        policy ??
        testSourcePolicy(
          domains: [
            SourceDomainRule(host: 'example.com', includeSubdomains: true),
          ],
        ),
    programs: [_program()],
  );
}

SourceRuleProgram _program({String programId = 'playback'}) =>
    SourceRuleProgram(
      programId: programId,
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: 'article',
      ),
      fields: [
        SourceFieldRule(name: 'source', valueKind: SourceValueKind.text),
      ],
      resultLimit: 1,
    );

SourcePlaybackRoute _route(
  SourceEpisodeIdentity episode, {
  Uri? mediaUri,
  Uri? pageUri,
}) => SourcePlaybackRoute(
  packageId: 'example.anime',
  packageVersion: Version.parse('1.0.0'),
  programId: 'playback',
  source: SourcePlayableSource(
    episode: episode,
    sourceKey: 'primary',
    label: 'Primary',
    kind: WebCandidateKind.hls,
    mediaUri:
        mediaUri ?? Uri.parse('https://cdn.example.com/video/master.m3u8'),
    pageUri: pageUri ?? Uri.parse('https://example.com/watch/episode-1'),
  ),
);

SourceEpisodeIdentity _episode({String lineId = 'line-1'}) =>
    SourceEpisodeIdentity(
      sourceId: 'example.anime',
      lineId: lineId,
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

final class _FixedIdGenerator implements PlaybackSessionIdGenerator {
  _FixedIdGenerator(this.value);

  final String value;

  @override
  String nextId() => value;
}
