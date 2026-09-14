import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/application/source_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/source_playback_session_request_builder.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final package = _package();
  final episode = _episode();
  final route = _route(episode);
  final plan = _adPlan(episode);
  final selected = _routeResult(route);

  test('builds one typed resolver request from a selected route', () {
    final request = _readyRequest(route, package, plan);
    final builder = _RecordingBuilder(
      SourcePlaybackSessionRequestBuildResult(
        status: SourcePlaybackSessionRequestBuildStatus.ready,
        request: request,
      ),
    );
    final result = SourcePlaybackSessionRequestCoordinator(builder: builder)
        .buildRequest(
          routeResult: selected,
          package: package,
          adRemovalPlan: plan,
          sourceEventSequence: 17,
        );

    expect(result.status, SourcePlaybackSessionRequestCoordinatorStatus.ready);
    expect(result.request, same(request));
    expect(result.routeStatus, SourcePlaybackRouteCoordinatorStatus.selected);
    expect(result.requestStatus, SourcePlaybackSessionRequestBuildStatus.ready);
    expect(builder.route, same(route));
    expect(builder.package, same(package));
    expect(builder.adRemovalPlan, same(plan));
    expect(builder.sourceEventSequence, 17);
  });

  test('short-circuits every non-selected route state', () {
    final builder = _RecordingBuilder(_rejectedResult());
    final coordinator = SourcePlaybackSessionRequestCoordinator(
      builder: builder,
    );
    for (final status in [
      SourcePlaybackRouteCoordinatorStatus.notFound,
      SourcePlaybackRouteCoordinatorStatus.noSources,
      SourcePlaybackRouteCoordinatorStatus.disabled,
      SourcePlaybackRouteCoordinatorStatus.consentRequired,
      SourcePlaybackRouteCoordinatorStatus.incompatible,
      SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
      SourcePlaybackRouteCoordinatorStatus.failed,
    ]) {
      final result = coordinator.buildRequest(
        routeResult: SourcePlaybackRouteCoordinatorResult(
          status: status,
          selectionResults: const [],
          reasonCode: status == SourcePlaybackRouteCoordinatorStatus.failed
              ? 'route_selection_failed'
              : status == SourcePlaybackRouteCoordinatorStatus.noSources
              ? 'no_enabled_sources'
              : null,
        ),
        package: package,
        adRemovalPlan: plan,
        sourceEventSequence: 0,
      );
      expect(
        result.status,
        SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected,
      );
      expect(result.routeStatus, status);
      expect(result.request, isNull);
    }
    expect(builder.callCount, 0);
  });

  test('preserves a typed session-builder rejection', () {
    final builder = _RecordingBuilder(
      _rejectedResult(
        status: SourcePlaybackSessionRequestBuildStatus.packageMismatch,
        reasonCode: 'package_identity_mismatch',
      ),
    );
    final result = SourcePlaybackSessionRequestCoordinator(builder: builder)
        .buildRequest(
          routeResult: selected,
          package: package,
          adRemovalPlan: plan,
          sourceEventSequence: 0,
        );

    expect(
      result.status,
      SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
    );
    expect(result.routeStatus, SourcePlaybackRouteCoordinatorStatus.selected);
    expect(
      result.requestStatus,
      SourcePlaybackSessionRequestBuildStatus.packageMismatch,
    );
    expect(result.reasonCode, 'package_identity_mismatch');
    expect(result.request, isNull);
  });

  test(
    'keeps exact package, ad-plan and sequence validation in the builder',
    () {
      final coordinator = SourcePlaybackSessionRequestCoordinator(
        builder: const DeterministicSourcePlaybackSessionRequestBuilder(),
      );
      final invalidSequence = coordinator.buildRequest(
        routeResult: selected,
        package: package,
        adRemovalPlan: plan,
        sourceEventSequence: -1,
      );
      expect(
        invalidSequence.status,
        SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
      );
      expect(
        invalidSequence.requestStatus,
        SourcePlaybackSessionRequestBuildStatus.invalidSourceEventSequence,
      );

      final mismatch = coordinator.buildRequest(
        routeResult: selected,
        package: _package(packageId: 'other.anime'),
        adRemovalPlan: plan,
        sourceEventSequence: 0,
      );
      expect(
        mismatch.requestStatus,
        SourcePlaybackSessionRequestBuildStatus.packageMismatch,
      );
      expect(mismatch.request, isNull);
    },
  );

  test('converts builder exceptions into a safe failed result', () {
    final result =
        SourcePlaybackSessionRequestCoordinator(
          builder: const _ThrowingBuilder(),
        ).buildRequest(
          routeResult: selected,
          package: package,
          adRemovalPlan: plan,
          sourceEventSequence: 0,
        );

    expect(result.status, SourcePlaybackSessionRequestCoordinatorStatus.failed);
    expect(result.reasonCode, 'session_request_build_failed');
    expect(result.request, isNull);
    expect(result.toString(), isNot(contains('raw')));
  });

  test(
    'does not fabricate a session and keeps ready request diagnostics redacted',
    () {
      final request = _readyRequest(route, package, plan);
      final result = SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        request: request,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
      );

      expect(result.toString(), contains('status: ready'));
      expect(result.toString(), contains('hasRequest: true'));
      expect(result.toString(), isNot(contains('master.m3u8')));
      expect(result.toString(), isNot(contains('episode-1')));
      expect(result.request, isA<PlaybackSessionResolutionRequest>());
      expect(
        () => SourcePlaybackSessionRequestCoordinatorResult(
          status: SourcePlaybackSessionRequestCoordinatorStatus.ready,
          routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
          requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        ),
        throwsArgumentError,
      );
      expect(
        () => SourcePlaybackSessionRequestCoordinatorResult(
          status: SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
          routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
          requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
          reasonCode: 'session_request_rejected',
        ),
        throwsArgumentError,
      );
    },
  );
}

SourcePlaybackRouteCoordinatorResult _routeResult(SourcePlaybackRoute route) =>
    SourcePlaybackRouteCoordinatorResult(
      status: SourcePlaybackRouteCoordinatorStatus.selected,
      route: route,
      selectionResults: [
        SourcePlaybackRouteSelectionResult(
          status: SourcePlaybackRouteSelectionStatus.selected,
          route: route,
        ),
      ],
    );

PlaybackSessionResolutionRequest _readyRequest(
  SourcePlaybackRoute route,
  SourcePackageManifest package,
  AdRemovalPlan plan,
) => DeterministicSourcePlaybackSessionRequestBuilder()
    .buildRequest(
      route: route,
      package: package,
      adRemovalPlan: plan,
      sourceEventSequence: 0,
    )
    .request!;

SourcePlaybackSessionRequestBuildResult _rejectedResult({
  SourcePlaybackSessionRequestBuildStatus status =
      SourcePlaybackSessionRequestBuildStatus.mediaUriNotAllowed,
  String reasonCode = 'media_uri_not_allowed',
}) => SourcePlaybackSessionRequestBuildResult(
  status: status,
  reasonCode: reasonCode,
);

SourcePackageManifest _package({String packageId = 'example.anime'}) =>
    SourcePackageManifest(
      schemaVersion: 1,
      packageId: packageId,
      displayName: packageId,
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
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
            expression: 'article',
          ),
          fields: [
            SourceFieldRule(name: 'source', valueKind: SourceValueKind.text),
          ],
          resultLimit: 1,
        ),
      ],
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

final class _RecordingBuilder implements SourcePlaybackSessionRequestBuilder {
  _RecordingBuilder(this.result);

  final SourcePlaybackSessionRequestBuildResult result;
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
    return result;
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
    throw StateError('raw builder detail');
  }
}
