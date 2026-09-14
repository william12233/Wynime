import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'playback/playback_coordinator.dart';
import 'source_playback_open_request_builder.dart';

enum SourcePlaybackCoordinatorOpenStatus { opened, requestRejected }

final class SourcePlaybackCoordinatorOpenResult {
  SourcePlaybackCoordinatorOpenResult({
    required this.status,
    this.session,
    this.requestStatus,
    this.reasonCode,
  }) {
    final isOpened = status == SourcePlaybackCoordinatorOpenStatus.opened;
    if (isOpened && session == null) {
      throw ArgumentError('An opened result must contain a session.');
    }
    if (!isOpened && session != null) {
      throw ArgumentError('A rejected result cannot contain a session.');
    }
    if (isOpened && (requestStatus != null || reasonCode != null)) {
      throw ArgumentError('An opened result cannot contain rejection data.');
    }
    if (!isOpened && requestStatus == null) {
      throw ArgumentError('A rejected result requires the request status.');
    }
    if (!isOpened &&
        requestStatus == SourcePlaybackOpenRequestBuildStatus.ready) {
      throw ArgumentError('A rejected result cannot carry a ready status.');
    }
    if (!isOpened && (reasonCode == null || !_isSafeToken(reasonCode!))) {
      throw ArgumentError(
        'A rejected result requires a bounded diagnostic token.',
      );
    }
  }

  final SourcePlaybackCoordinatorOpenStatus status;
  final PlaybackSession? session;
  final SourcePlaybackOpenRequestBuildStatus? requestStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasSession': session != null,
    'requestStatus': requestStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

abstract interface class SourcePlaybackCoordinatorOpener {
  Future<SourcePlaybackCoordinatorOpenResult> openFromSource({
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

final class PlaybackCoordinatorSourceOpener
    implements SourcePlaybackCoordinatorOpener {
  PlaybackCoordinatorSourceOpener({
    required this.coordinator,
    this.openRequestBuilder =
        const DeterministicSourcePlaybackOpenRequestBuilder(),
  });

  final PlaybackCoordinator coordinator;
  final SourcePlaybackOpenRequestBuilder openRequestBuilder;

  @override
  Future<SourcePlaybackCoordinatorOpenResult> openFromSource({
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
  }) async {
    final requestResult = openRequestBuilder.buildOpenRequest(
      route: route,
      package: package,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
      proxyBudget: proxyBudget,
      addressFamily: addressFamily,
      refreshLeeway: refreshLeeway,
      maxAutomaticRefreshes: maxAutomaticRefreshes,
      episodeDuration: episodeDuration,
      bangumiEpisode: bangumiEpisode,
    );
    if (requestResult.status != SourcePlaybackOpenRequestBuildStatus.ready) {
      return SourcePlaybackCoordinatorOpenResult(
        status: SourcePlaybackCoordinatorOpenStatus.requestRejected,
        requestStatus: requestResult.status,
        reasonCode: requestResult.reasonCode ?? 'source_open_request_rejected',
      );
    }

    // PlaybackCoordinator remains the only authority for session resolution,
    // proxy exposure, player lifecycle, generation checks and progress binding.
    final session = await coordinator.open(requestResult.request!);
    return SourcePlaybackCoordinatorOpenResult(
      status: SourcePlaybackCoordinatorOpenStatus.opened,
      session: session,
    );
  }
}

bool _isSafeToken(String value) =>
    value.isNotEmpty &&
    value.length <= 64 &&
    RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
