import 'package:pub_semver/pub_semver.dart';

import '../domain/models/ad_removal_plan.dart';
import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_live_capture_playback_route_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_live_capture_playback_session_request_models.dart';
import '../domain/models/web_capture_models.dart';
import '../domain/services/playback_session_resolver.dart';
import 'source_live_capture_snapshot_validator.dart';

/// Converts one selected live route into the existing resolver request.
///
/// This is a pure live-only handoff. It preserves the exact captured
/// candidate and request policy, transfers the one accepted cookie snapshot
/// once, and leaves session resolution, proxy, player and lifecycle ownership
/// with [PlaybackCoordinator].
final class SourceLiveCapturePlaybackSessionRequestCoordinator {
  SourceLiveCapturePlaybackSessionRequestCoordinator({
    required this.wynimeVersion,
  });

  final Version wynimeVersion;
  final SourceLiveCaptureSnapshotValidator _snapshotValidator =
      const SourceLiveCaptureSnapshotValidator();

  SourceLiveCapturePlaybackSessionRequestResult buildRequest({
    required InstalledSourcePackage installedPackage,
    required SourceLiveCapturePlaybackRouteResult routeResult,
    required AdRemovalPlan adRemovalPlan,
  }) {
    final package = installedPackage.package;
    if (installedPackage.requiresConsent ||
        installedPackage.requiresReconsent) {
      return _blocked(
        SourceLiveCapturePlaybackSessionRequestStatus.consentRequired,
        routeStatus: routeResult.status,
        reasonCode: 'consent_required',
      );
    }
    if (installedPackage.status != SourcePackageStatus.enabled) {
      return _blocked(
        SourceLiveCapturePlaybackSessionRequestStatus.disabled,
        routeStatus: routeResult.status,
        reasonCode: 'package_disabled',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _blocked(
        SourceLiveCapturePlaybackSessionRequestStatus.incompatible,
        routeStatus: routeResult.status,
        reasonCode: 'incompatible_wynime_version',
      );
    }
    if (routeResult.status != SourceLiveCapturePlaybackRouteStatus.selected) {
      return SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: routeResult.status,
        reasonCode: routeResult.reasonCode ?? _routeReason(routeResult.status),
      );
    }

    final route = routeResult.route!;
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

    final captureRequest = route.captureRequest;
    final webRequest = captureRequest.webCaptureRequest;
    if (captureRequest.packageId != route.packageId ||
        captureRequest.packageVersion != route.packageVersion ||
        captureRequest.programId != route.programId ||
        !webRequest.securityPolicy.semanticallyEquals(package.securityPolicy) ||
        !package.securityPolicy.allowsUri(webRequest.initialUri)) {
      return _failed('live_session_capture_request_mismatch');
    }

    final capture = route.captureResult;
    final snapshot = capture.snapshot;
    if (capture.status != SourceLiveCaptureStatus.captured ||
        snapshot == null ||
        snapshot.stopReason != WebCaptureStopReason.completed ||
        capture.packageId != route.packageId ||
        capture.packageVersion != route.packageVersion ||
        capture.programId != route.programId) {
      return _failed('live_session_capture_mismatch');
    }

    final validatedCapture = _snapshotValidator.validate(
      captureRequest,
      snapshot,
    );
    if (validatedCapture.status != SourceLiveCaptureStatus.captured ||
        validatedCapture.snapshot == null) {
      return _failed(
        validatedCapture.reasonCode ?? 'live_session_snapshot_invalid',
      );
    }

    final candidateIndex = route.source.candidateIndex;
    final validatedSnapshot = validatedCapture.snapshot!;
    if (candidateIndex < 0 ||
        candidateIndex >= validatedSnapshot.candidates.length) {
      return _failed('live_session_candidate_index_invalid');
    }
    final candidate = validatedSnapshot.candidates[candidateIndex];
    if (!_sameCandidate(candidate, route.source.candidate) ||
        route.source.source.episode.sourceId != package.packageId ||
        route.source.source.pageUri != validatedSnapshot.finalUri ||
        route.source.source.kind != candidate.kind ||
        route.source.source.mediaUri != candidate.uri ||
        !_isSupportedCandidate(candidate.kind) ||
        !webCaptureAllowsRuntimeUri(
          policy: package.securityPolicy,
          uri: candidate.uri,
          grant: candidate.runtimeOriginGrant,
          acquisitionId: webRequest.acquisitionId,
        ) ||
        !package.securityPolicy.allowsUri(route.source.source.pageUri)) {
      return _failed('live_session_candidate_mismatch');
    }
    if (adRemovalPlan.key.episode != route.episode) {
      return _failed('ad_plan_episode_mismatch');
    }

    try {
      final request = PlaybackSessionResolutionRequest(
        episode: route.episode,
        pageUri: route.source.source.pageUri,
        candidate: route.source.candidate,
        securityPolicy: package.securityPolicy,
        adRemovalPlan: adRemovalPlan,
        cookies: validatedSnapshot.cookies,
        userAgent: webRequest.userAgentPolicy.value,
        runtimeMediaOriginGrant: candidate.runtimeOriginGrant,
        acquisitionId: webRequest.acquisitionId,
      );
      return SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.ready,
        request: request,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
      );
    } on Object {
      return _failed('live_session_request_build_failed');
    }
  }

  SourceLiveCapturePlaybackSessionRequestResult _blocked(
    SourceLiveCapturePlaybackSessionRequestStatus status, {
    required SourceLiveCapturePlaybackRouteStatus routeStatus,
    required String reasonCode,
  }) => SourceLiveCapturePlaybackSessionRequestResult(
    status: status,
    routeStatus: routeStatus == SourceLiveCapturePlaybackRouteStatus.selected
        ? null
        : routeStatus,
    reasonCode: reasonCode,
  );

  SourceLiveCapturePlaybackSessionRequestResult _failed(String reasonCode) =>
      SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: reasonCode,
      );

  static String _routeReason(SourceLiveCapturePlaybackRouteStatus status) =>
      switch (status) {
        SourceLiveCapturePlaybackRouteStatus.selected => 'route_selected',
        SourceLiveCapturePlaybackRouteStatus.notFound => 'route_not_found',
        SourceLiveCapturePlaybackRouteStatus.consentRequired =>
          'consent_required',
        SourceLiveCapturePlaybackRouteStatus.disabled => 'package_disabled',
        SourceLiveCapturePlaybackRouteStatus.incompatible =>
          'incompatible_wynime_version',
        SourceLiveCapturePlaybackRouteStatus.preferredSourceNotFound =>
          'preferred_source_not_found',
        SourceLiveCapturePlaybackRouteStatus.failed => 'route_selection_failed',
      };

  static bool _isSupportedCandidate(WebCandidateKind kind) => switch (kind) {
    WebCandidateKind.hls ||
    WebCandidateKind.video ||
    WebCandidateKind.audio => true,
    WebCandidateKind.dash || WebCandidateKind.mediaSegment => false,
  };

  static bool _sameCandidate(WebMediaCandidate left, WebMediaCandidate right) {
    if (left.kind != right.kind ||
        left.uri != right.uri ||
        left.sourceEventSequence != right.sourceEventSequence ||
        left.headers.length != right.headers.length) {
      return false;
    }
    for (final entry in left.headers.entries) {
      if (right.headers[entry.key] != entry.value) {
        return false;
      }
    }
    final leftGrant = left.runtimeOriginGrant;
    final rightGrant = right.runtimeOriginGrant;
    return leftGrant == null && rightGrant == null ||
        leftGrant != null &&
            rightGrant != null &&
            leftGrant.acquisitionId == rightGrant.acquisitionId &&
            leftGrant.origin == rightGrant.origin &&
            leftGrant.sourceEventSequence == rightGrant.sourceEventSequence &&
            leftGrant.expiresAt == rightGrant.expiresAt;
  }
}
