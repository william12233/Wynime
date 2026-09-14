import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_package_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'source_live_capture_playable_source_plan_coordinator.dart';
import 'source_live_capture_playback_pipeline.dart';

enum SourceLiveCapturePlaybackEntryStatus { opened, notOpened }

enum SourceLiveCapturePlaybackEntryFailureStage { plan, pipeline }

/// A bounded result for the complete package-capture-to-playback handoff.
///
/// An opened result retains only the session returned by the existing
/// playback lifecycle. A rejected result retains either the typed plan
/// rejection or the typed pipeline rejection, both of which are guaranteed
/// not to retain a ready plan, open request, capture snapshot or raw error.
final class SourceLiveCapturePlaybackEntryResult {
  SourceLiveCapturePlaybackEntryResult({
    required this.status,
    this.session,
    this.failureStage,
    this.planResult,
    this.pipelineResult,
  }) {
    final isOpened = status == SourceLiveCapturePlaybackEntryStatus.opened;
    final failureResultCount = [
      planResult,
      pipelineResult,
    ].where((result) => result != null).length;

    if (isOpened) {
      if (session == null || failureStage != null || failureResultCount != 0) {
        throw ArgumentError(
          'An opened entry result must contain only one playback session.',
        );
      }
      return;
    }

    if (session != null ||
        failureStage == null ||
        failureResultCount != 1 ||
        (failureStage == SourceLiveCapturePlaybackEntryFailureStage.plan &&
            (planResult == null ||
                planResult!.status !=
                    SourceLiveCapturePlayableSourcePlanStatus.notReady ||
                pipelineResult != null)) ||
        (failureStage == SourceLiveCapturePlaybackEntryFailureStage.pipeline &&
            (pipelineResult == null ||
                pipelineResult!.status !=
                    SourceLiveCapturePlaybackPipelineStatus.notOpened ||
                planResult != null))) {
      throw ArgumentError(
        'A rejected entry result must contain one typed non-ready stage.',
      );
    }
  }

  final SourceLiveCapturePlaybackEntryStatus status;
  final PlaybackSession? session;
  final SourceLiveCapturePlaybackEntryFailureStage? failureStage;
  final SourceLiveCapturePlayableSourcePlanResult? planResult;
  final SourceLiveCapturePlaybackPipelineResult? pipelineResult;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasSession': session != null,
    'failureStage': failureStage?.name,
    'planStatus': planResult?.status.name,
    'planFailureStage': planResult?.failureStage?.name,
    'pipelineStatus': pipelineResult?.status.name,
    'pipelineFailureStage': pipelineResult?.failureStage?.name,
    'reasonCode': planResult?.reasonCode ?? pipelineResult?.reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

/// Starts live playback from one package-aware capture outcome.
///
/// The entry point only composes two already-typed application boundaries:
/// [planCoordinator] pairs the exact package admission/capture identity with
/// an explicit playable plan, then [playbackPipeline] performs normalization,
/// route, session-request, open-request and prepared open in order. It owns no
/// WebView, source I/O, package execution, persistence, retry, session,
/// proxy, player, progress or generation state.
final class SourceLiveCapturePlaybackEntryPoint {
  const SourceLiveCapturePlaybackEntryPoint({
    required this.planCoordinator,
    required this.playbackPipeline,
  });

  final SourceLiveCapturePlayableSourcePlanCoordinator planCoordinator;
  final SourceLiveCapturePlaybackPipeline playbackPipeline;

  Future<SourceLiveCapturePlaybackEntryResult> openCapturedLive({
    required SourceLiveCapturePackagePlan packagePlan,
    required SourceLiveCapturePackageResult admission,
    required SourceLiveCaptureResult captureResult,
    required SourceEpisodeIdentity episode,
    required Iterable<SourceLiveCapturePlayableSourceMapping> mappings,
    required AdRemovalPlan adRemovalPlan,
    required PlaybackProxyBudget proxyBudget,
    String? preferredSourceKey,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) async {
    final planResult = planCoordinator.buildPlan(
      packagePlan: packagePlan,
      admission: admission,
      captureResult: captureResult,
      episode: episode,
      mappings: mappings,
    );
    if (planResult.status != SourceLiveCapturePlayableSourcePlanStatus.ready) {
      return SourceLiveCapturePlaybackEntryResult(
        status: SourceLiveCapturePlaybackEntryStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackEntryFailureStage.plan,
        planResult: planResult,
      );
    }

    final pipelineResult = await playbackPipeline.openLive(
      plan: planResult.plan!,
      adRemovalPlan: adRemovalPlan,
      proxyBudget: proxyBudget,
      preferredSourceKey: preferredSourceKey,
      addressFamily: addressFamily,
      refreshLeeway: refreshLeeway,
      maxAutomaticRefreshes: maxAutomaticRefreshes,
      episodeDuration: episodeDuration,
      bangumiEpisode: bangumiEpisode,
    );
    if (pipelineResult.status !=
        SourceLiveCapturePlaybackPipelineStatus.opened) {
      return SourceLiveCapturePlaybackEntryResult(
        status: SourceLiveCapturePlaybackEntryStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackEntryFailureStage.pipeline,
        pipelineResult: pipelineResult,
      );
    }

    return SourceLiveCapturePlaybackEntryResult(
      status: SourceLiveCapturePlaybackEntryStatus.opened,
      session: pipelineResult.session,
    );
  }
}
