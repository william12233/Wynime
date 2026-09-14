import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/source_playback_session_request_builder.dart';

/// Composes a selected route into the existing resolver request contract.
///
/// Route selection remains a separate boundary. This coordinator owns no
/// resolver, session, proxy, player, persistence, I/O or asynchronous state.
final class SourcePlaybackSessionRequestCoordinator {
  const SourcePlaybackSessionRequestCoordinator({required this.builder});

  final SourcePlaybackSessionRequestBuilder builder;

  SourcePlaybackSessionRequestCoordinatorResult buildRequest({
    required SourcePlaybackRouteCoordinatorResult routeResult,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  }) {
    if (routeResult.status != SourcePlaybackRouteCoordinatorStatus.selected) {
      return SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected,
        routeStatus: routeResult.status,
        reasonCode: _routeReason(routeResult.status),
      );
    }

    try {
      final result = builder.buildRequest(
        route: routeResult.route!,
        package: package,
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: sourceEventSequence,
      );
      if (result.status == SourcePlaybackSessionRequestBuildStatus.ready) {
        return SourcePlaybackSessionRequestCoordinatorResult(
          status: SourcePlaybackSessionRequestCoordinatorStatus.ready,
          request: result.request!,
          routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
          requestStatus: result.status,
        );
      }
      return SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
        requestStatus: result.status,
        reasonCode: result.reasonCode ?? 'session_request_rejected',
      );
    } on Object {
      return SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.failed,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
        reasonCode: 'session_request_build_failed',
      );
    }
  }

  static String _routeReason(SourcePlaybackRouteCoordinatorStatus status) {
    return switch (status) {
      SourcePlaybackRouteCoordinatorStatus.selected => 'route_selected',
      SourcePlaybackRouteCoordinatorStatus.notFound => 'route_not_found',
      SourcePlaybackRouteCoordinatorStatus.noSources => 'no_enabled_sources',
      SourcePlaybackRouteCoordinatorStatus.disabled => 'route_disabled',
      SourcePlaybackRouteCoordinatorStatus.consentRequired =>
        'route_consent_required',
      SourcePlaybackRouteCoordinatorStatus.incompatible => 'route_incompatible',
      SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound =>
        'preferred_source_not_found',
      SourcePlaybackRouteCoordinatorStatus.failed => 'route_selection_failed',
    };
  }
}
