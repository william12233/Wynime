import 'dart:async';

import 'package:flutter/material.dart';

import '../../application/playback/playback_coordinator.dart';
import '../../application/source_installed_live_episode_pipeline.dart';
import '../../application/source_installed_live_playback_pipeline.dart';
import '../../application/source_playback_controller_factory.dart';
import '../../application/subject_source_playback_controller.dart';
import '../../domain/models/ad_removal_plan.dart';
import '../../domain/models/bangumi_episode_target.dart';
import '../../domain/models/bangumi_models.dart';
import '../../domain/models/manifest_fingerprint.dart';
import '../../domain/models/playback_events.dart';
import '../../domain/models/source_identity.dart';
import '../../design_system/tokens/spacing.dart';

/// Opens one exact source episode through the existing playback pipeline.
/// This page never receives or resolves a raw media URL.
final class PlayerPage extends StatefulWidget {
  const PlayerPage({
    required this.sourceController,
    required this.episode,
    this.sourceEpisode,
    super.key,
  });

  final SubjectSourcePlaybackController sourceController;
  final BangumiEpisode episode;
  final SourceEpisodeIdentity? sourceEpisode;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

final class _PlayerPageState extends State<PlayerPage> {
  StreamSubscription<PlaybackEvent>? _events;
  PlaybackState _state = PlaybackState.opening;
  String? _failureCode;
  bool _opening = true;

  PlaybackCoordinator? get _coordinator =>
      widget.sourceController.playbackCoordinator;

  @override
  void initState() {
    super.initState();
    _events = _coordinator?.events.listen(_onPlaybackEvent);
    unawaited(_open());
  }

  Future<void> _open() async {
    setState(() {
      _opening = true;
      _failureCode = null;
      _state = PlaybackState.opening;
    });
    try {
      final identity =
          widget.sourceEpisode ??
          (await widget.sourceController.resolveEpisode(
            widget.episode,
          )).identity;
      if (identity == null) {
        if (!mounted) return;
        setState(() {
          _opening = false;
          _failureCode = 'episode_selection_required';
        });
        return;
      }
      final package = widget.sourceController.state.package;
      if (package == null) {
        throw const _PlayerPageFailure('source_package_not_ready');
      }
      final result = await widget.sourceController.playbackPipeline.openLive(
        targets: [
          SourceInstalledLiveEpisodeTarget(
            installedPackage: package,
            episode: identity,
          ),
        ],
        adRemovalPlan: _adRemovalPlan(identity),
        sourceEventSequence: 0,
        proxyBudget: SourcePlaybackControllerFactory.defaultProxyBudget(),
        bangumiEpisode: BangumiEpisodeTarget(
          subjectId: widget.episode.subjectId,
          episodeId: widget.episode.id,
        ),
        episodeDuration: widget.episode.duration == null
            ? null
            : Duration(seconds: widget.episode.duration!),
      );
      if (!mounted) return;
      final opened =
          result.status == SourceInstalledLivePlaybackPipelineStatus.opened ||
          result.status == SourceInstalledLivePlaybackPipelineStatus.partial;
      setState(() {
        _opening = false;
        _failureCode = opened
            ? null
            : result.reasonCode ?? 'playback_resolution_failed';
        _state = opened ? PlaybackState.ready : PlaybackState.failed;
      });
    } on _PlayerPageFailure catch (error) {
      if (!mounted) return;
      setState(() {
        _opening = false;
        _failureCode = error.code;
        _state = PlaybackState.failed;
      });
    } on Object {
      if (!mounted) return;
      setState(() {
        _opening = false;
        _failureCode = 'playback_resolution_failed';
        _state = PlaybackState.failed;
      });
    }
  }

  void _onPlaybackEvent(PlaybackEvent event) {
    if (!mounted) return;
    setState(() {
      _state = event.state;
      if (event.failure != null) {
        _failureCode = event.failure!.code;
      }
    });
  }

  @override
  void dispose() {
    unawaited(_events?.cancel());
    unawaited(_coordinator?.stop());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.episode.nameCn.isNotEmpty
        ? widget.episode.nameCn
        : widget.episode.name;
    return Scaffold(
      appBar: AppBar(
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: ColoredBox(
                color: Colors.black,
                child: _opening
                    ? const Center(child: CircularProgressIndicator())
                    : _failureCode != null
                    ? _FailurePanel(code: _failureCode!, onRetry: _open)
                    : widget.sourceController.surfaceHost?.build(context) ??
                          const Center(
                            child: Text(
                              '播放畫面目前無法使用',
                              style: TextStyle(color: Colors.white),
                            ),
                          ),
              ),
            ),
            _PlaybackControls(state: _state, coordinator: _coordinator),
          ],
        ),
      ),
    );
  }
}

final class _PlaybackControls extends StatelessWidget {
  const _PlaybackControls({required this.state, required this.coordinator});

  final PlaybackState state;
  final PlaybackCoordinator? coordinator;

  @override
  Widget build(BuildContext context) {
    final active = coordinator?.hasActivePlayback == true;
    final playing =
        state == PlaybackState.playing || state == PlaybackState.buffering;
    return Padding(
      padding: const EdgeInsets.all(WynimeSpacing.sm),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            tooltip: playing ? '暫停' : '播放',
            onPressed: !active
                ? null
                : () => unawaited(
                    playing ? coordinator!.pause() : coordinator!.play(),
                  ),
            icon: Icon(playing ? Icons.pause : Icons.play_arrow),
          ),
          IconButton(
            tooltip: '停止',
            onPressed: !active ? null : () => unawaited(coordinator!.stop()),
            icon: const Icon(Icons.stop),
          ),
        ],
      ),
    );
  }
}

final class _FailurePanel extends StatelessWidget {
  const _FailurePanel({required this.code, required this.onRetry});

  final String code;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, color: Colors.white, size: 40),
            const SizedBox(height: WynimeSpacing.sm),
            const Text('播放來源無法使用', style: TextStyle(color: Colors.white)),
            const SizedBox(height: WynimeSpacing.sm),
            FilledButton.icon(
              onPressed: () => unawaited(onRetry()),
              icon: const Icon(Icons.refresh),
              label: const Text('重試'),
            ),
          ],
        ),
      ),
    );
  }
}

AdRemovalPlan _adRemovalPlan(SourceEpisodeIdentity identity) => AdRemovalPlan(
  key: AdRemovalPlanKey(
    episode: identity,
    manifestFingerprint: ManifestFingerprint(
      algorithm: 'none',
      value: 'unclassified',
    ),
  ),
);

final class _PlayerPageFailure implements Exception {
  const _PlayerPageFailure(this.code);

  final String code;
}
