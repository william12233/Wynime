import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/source_playback_session_request_builder.dart';

import 'playback/playback_coordinator.dart';
import 'source_playback_session_request_builder.dart';

enum SourcePlaybackOpenRequestBuildStatus {
  ready,
  sessionRequestRejected,
  invalidRefreshLeeway,
  invalidAutomaticRefreshes,
  invalidEpisodeDuration,
}

final class SourcePlaybackOpenRequestBuildResult {
  SourcePlaybackOpenRequestBuildResult({
    required this.status,
    this.request,
    this.reasonCode,
  }) {
    if (status == SourcePlaybackOpenRequestBuildStatus.ready &&
        request == null) {
      throw ArgumentError('A ready result must contain an open request.');
    }
    if (status != SourcePlaybackOpenRequestBuildStatus.ready &&
        request != null) {
      throw ArgumentError('A non-ready result cannot contain an open request.');
    }
    if (status == SourcePlaybackOpenRequestBuildStatus.ready &&
        reasonCode != null) {
      throw ArgumentError('A ready result cannot contain a failure code.');
    }
    if (status != SourcePlaybackOpenRequestBuildStatus.ready &&
        (reasonCode == null || !_isSafeToken(reasonCode!))) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'A non-ready result requires a bounded diagnostic token.',
      );
    }
  }

  final SourcePlaybackOpenRequestBuildStatus status;
  final PlaybackOpenRequest? request;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

abstract interface class SourcePlaybackOpenRequestBuilder {
  SourcePlaybackOpenRequestBuildResult buildOpenRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily,
    Duration refreshLeeway,
    int maxAutomaticRefreshes,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  });
}

final class DeterministicSourcePlaybackOpenRequestBuilder
    implements SourcePlaybackOpenRequestBuilder {
  const DeterministicSourcePlaybackOpenRequestBuilder({
    this._sessionRequestBuilder =
        const DeterministicSourcePlaybackSessionRequestBuilder(),
  });

  final SourcePlaybackSessionRequestBuilder _sessionRequestBuilder;

  @override
  SourcePlaybackOpenRequestBuildResult buildOpenRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) {
    if (refreshLeeway.isNegative) {
      return _failure(
        SourcePlaybackOpenRequestBuildStatus.invalidRefreshLeeway,
        'invalid_refresh_leeway',
      );
    }
    if (maxAutomaticRefreshes < 0 || maxAutomaticRefreshes > 3) {
      return _failure(
        SourcePlaybackOpenRequestBuildStatus.invalidAutomaticRefreshes,
        'invalid_automatic_refreshes',
      );
    }
    if (episodeDuration?.isNegative == true) {
      return _failure(
        SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
        'invalid_episode_duration',
      );
    }

    final sessionResult = _sessionRequestBuilder.buildRequest(
      route: route,
      package: package,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
    );
    if (sessionResult.status != SourcePlaybackSessionRequestBuildStatus.ready) {
      return _failure(
        SourcePlaybackOpenRequestBuildStatus.sessionRequestRejected,
        sessionResult.reasonCode ?? 'session_request_rejected',
      );
    }

    return SourcePlaybackOpenRequestBuildResult(
      status: SourcePlaybackOpenRequestBuildStatus.ready,
      request: PlaybackOpenRequest(
        resolution: sessionResult.request!,
        proxyBudget: proxyBudget,
        addressFamily: addressFamily,
        refreshLeeway: refreshLeeway,
        maxAutomaticRefreshes: maxAutomaticRefreshes,
        episodeDuration: episodeDuration,
        bangumiEpisode: bangumiEpisode,
      ),
    );
  }

  static SourcePlaybackOpenRequestBuildResult _failure(
    SourcePlaybackOpenRequestBuildStatus status,
    String reasonCode,
  ) {
    return SourcePlaybackOpenRequestBuildResult(
      status: status,
      reasonCode: reasonCode,
    );
  }
}

bool _isSafeToken(String value) =>
    value.isNotEmpty &&
    value.length <= 64 &&
    RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
