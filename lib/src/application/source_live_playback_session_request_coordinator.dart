import 'package:pub_semver/pub_semver.dart';

import '../domain/models/ad_removal_plan.dart';
import '../domain/models/source_http_models.dart';
import '../domain/models/source_live_playback_route_models.dart';
import '../domain/models/source_live_playback_session_request_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_package_manifest.dart';
import '../domain/models/source_playback_session_request_models.dart';
import '../domain/models/web_capture_models.dart';
import '../domain/services/playback_session_resolver.dart';
import '../domain/services/source_playback_session_request_builder.dart';

/// Converts one selected live HTTP route into the existing resolver request.
///
/// The live HTTP route has no WebView event or cookie authority, so the
/// resulting request carries an empty candidate-header set, no cookies and no
/// user-agent. Session resolution, proxy, player and lifecycle ownership stay
/// with [PlaybackCoordinator].
final class SourceLivePlaybackSessionRequestCoordinator {
  const SourceLivePlaybackSessionRequestCoordinator({
    required this.wynimeVersion,
    required this.builder,
  });

  final Version wynimeVersion;
  final SourcePlaybackSessionRequestBuilder builder;

  SourceLivePlaybackSessionRequestResult buildRequest({
    required SourceLivePlaybackRouteResult routeResult,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    if (routeResult.status != SourceLivePlaybackRouteStatus.selected) {
      return SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: routeResult.status,
        reasonCode: routeResult.reasonCode ?? _routeReason(routeResult.status),
      );
    }

    final route = routeResult.route!;
    final installed = route.installedPackage;
    if (installed.requiresConsent || installed.requiresReconsent) {
      return _blocked(
        SourceLivePlaybackSessionRequestStatus.consentRequired,
        reasonCode: 'consent_required',
      );
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return _blocked(
        SourceLivePlaybackSessionRequestStatus.disabled,
        reasonCode: 'package_disabled',
      );
    }

    final package = installed.package;
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _blocked(
        SourceLivePlaybackSessionRequestStatus.incompatible,
        reasonCode: 'incompatible_wynime_version',
      );
    }

    try {
      package.programById(route.programId);
    } on StateError {
      return _failed('program_not_found');
    } on Object {
      return _failed('package_preflight_failed');
    }

    if (route.packageId != package.packageId) {
      return _failed('live_session_package_mismatch');
    }
    if (route.packageVersion != package.version) {
      return _failed('live_session_version_mismatch');
    }
    if (route.episode.sourceId != package.packageId) {
      return _failed('live_session_episode_mismatch');
    }

    final sourceRequest = route.request;
    if (sourceRequest.method != SourceHttpMethod.get ||
        sourceRequest.bodyBytes.isNotEmpty ||
        !package.securityPolicy.semanticallyEquals(
          sourceRequest.securityPolicy,
        ) ||
        !package.securityPolicy.allowsUri(sourceRequest.uri)) {
      return _failed('live_session_request_mismatch');
    }

    SourcePlaybackSessionRequestBuildResult built;
    try {
      built = builder.buildRequest(
        route: route.route,
        package: package,
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: sourceEventSequence,
      );
    } on Object {
      return _failed('live_session_request_build_failed');
    }

    if (built.status != SourcePlaybackSessionRequestBuildStatus.ready) {
      return SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.requestRejected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: built.status,
        reasonCode: built.reasonCode ?? 'session_request_rejected',
      );
    }

    final request = built.request;
    if (request == null ||
        !_matchesRequest(
          request,
          route: route,
          package: package,
          adRemovalPlan: adRemovalPlan,
          sourceEventSequence: sourceEventSequence,
        )) {
      return _failed('live_session_request_mismatch');
    }

    return SourceLivePlaybackSessionRequestResult(
      status: SourceLivePlaybackSessionRequestStatus.ready,
      request: request,
      routeStatus: SourceLivePlaybackRouteStatus.selected,
      requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
    );
  }

  SourceLivePlaybackSessionRequestResult _blocked(
    SourceLivePlaybackSessionRequestStatus status, {
    required String reasonCode,
  }) => SourceLivePlaybackSessionRequestResult(
    status: status,
    reasonCode: reasonCode,
  );

  SourceLivePlaybackSessionRequestResult _failed(String reasonCode) =>
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: _safeReason(reasonCode),
      );

  static String _routeReason(SourceLivePlaybackRouteStatus status) =>
      switch (status) {
        SourceLivePlaybackRouteStatus.selected => 'route_selected',
        SourceLivePlaybackRouteStatus.notFound => 'route_not_found',
        SourceLivePlaybackRouteStatus.noSources => 'no_enabled_sources',
        SourceLivePlaybackRouteStatus.disabled => 'route_disabled',
        SourceLivePlaybackRouteStatus.consentRequired =>
          'route_consent_required',
        SourceLivePlaybackRouteStatus.incompatible => 'route_incompatible',
        SourceLivePlaybackRouteStatus.preferredSourceNotFound =>
          'preferred_source_not_found',
        SourceLivePlaybackRouteStatus.failed => 'route_selection_failed',
      };

  static bool _matchesRequest(
    PlaybackSessionResolutionRequest request, {
    required SourceLivePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    final candidate = request.candidate;
    return request.episode == route.episode &&
        request.pageUri == route.source.pageUri &&
        candidate.kind == route.source.kind &&
        _isSupportedKind(candidate.kind) &&
        candidate.uri == route.source.mediaUri &&
        candidate.headers.isEmpty &&
        candidate.sourceEventSequence == sourceEventSequence &&
        request.securityPolicy.semanticallyEquals(package.securityPolicy) &&
        package.securityPolicy.allowsUri(request.pageUri) &&
        package.securityPolicy.allowsUri(candidate.uri) &&
        identical(request.adRemovalPlan, adRemovalPlan) &&
        request.adRemovalPlan.key.episode == route.episode &&
        request.cookies.isEmpty &&
        request.userAgent == null &&
        request.expiresAt == null &&
        request.refresh == null &&
        request.subtitles.isEmpty &&
        request.audioTracks.isEmpty;
  }

  static bool _isSupportedKind(WebCandidateKind kind) => switch (kind) {
    WebCandidateKind.hls ||
    WebCandidateKind.video ||
    WebCandidateKind.audio => true,
    WebCandidateKind.dash || WebCandidateKind.mediaSegment => false,
  };

  static String _safeReason(String value) =>
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value)
      ? value
      : 'live_session_request_failed';
}
