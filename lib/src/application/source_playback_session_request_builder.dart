import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/source_playback_session_request_builder.dart';

final class DeterministicSourcePlaybackSessionRequestBuilder
    implements SourcePlaybackSessionRequestBuilder {
  const DeterministicSourcePlaybackSessionRequestBuilder();

  @override
  SourcePlaybackSessionRequestBuildResult buildRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    if (route.packageId != package.packageId) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.packageMismatch,
        'package_identity_mismatch',
      );
    }
    if (route.packageVersion != package.version) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.versionMismatch,
        'package_version_mismatch',
      );
    }
    if (!package.programs.any(
      (program) => program.programId == route.programId,
    )) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.programNotFound,
        'program_not_found',
      );
    }
    if (route.episode != adRemovalPlan.key.episode) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.adRemovalPlanMismatch,
        'ad_plan_episode_mismatch',
      );
    }
    if (!_isSupportedKind(route.source.kind)) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.unsupportedCandidate,
        'unsupported_candidate_kind',
      );
    }
    if (!package.securityPolicy.allowsUri(route.source.mediaUri)) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.mediaUriNotAllowed,
        'media_uri_not_allowed',
      );
    }
    if (!package.securityPolicy.allowsUri(route.source.pageUri)) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.pageUriNotAllowed,
        'page_uri_not_allowed',
      );
    }
    if (sourceEventSequence < 0) {
      return _failure(
        SourcePlaybackSessionRequestBuildStatus.invalidSourceEventSequence,
        'invalid_source_event_sequence',
      );
    }

    final request = PlaybackSessionResolutionRequest(
      episode: route.episode,
      pageUri: route.source.pageUri,
      candidate: WebMediaCandidate(
        kind: route.source.kind,
        uri: route.source.mediaUri,
        headers: const {},
        sourceEventSequence: sourceEventSequence,
      ),
      securityPolicy: package.securityPolicy,
      adRemovalPlan: adRemovalPlan,
    );
    return SourcePlaybackSessionRequestBuildResult(
      status: SourcePlaybackSessionRequestBuildStatus.ready,
      request: request,
    );
  }

  static SourcePlaybackSessionRequestBuildResult _failure(
    SourcePlaybackSessionRequestBuildStatus status,
    String reasonCode,
  ) {
    return SourcePlaybackSessionRequestBuildResult(
      status: status,
      reasonCode: reasonCode,
    );
  }

  static bool _isSupportedKind(WebCandidateKind kind) {
    return switch (kind.name) {
      'hls' || 'video' || 'audio' => true,
      _ => false,
    };
  }
}
