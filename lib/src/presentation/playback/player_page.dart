import 'dart:async';

import 'package:flutter/material.dart';
import 'package:pub_semver/pub_semver.dart';

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
import '../../domain/models/source_models.dart';
import '../../domain/models/source_live_capture_models.dart';
import '../../domain/models/source_live_capture_package_models.dart';
import '../../domain/models/source_live_capture_playable_source_models.dart';
import '../../domain/models/source_security_policy.dart';
import '../../domain/models/web_capture_models.dart';
import '../../design_system/tokens/spacing.dart';
import 'package:wynime/l10n/app_localizations.dart';
import '../../platform/web_capture/inapp_webview_installed_source_live_capture_view.dart';
import '../../platform/web_capture/inapp_webview_source_live_playable_fallback.dart';
import 'source_line_selector.dart';
import '../../domain/services/web_source_browser.dart';
import '../../application/source_live_capture_playback_entry_point.dart';
import '../../application/source_live_operation_plan_factory.dart';
import '../../application/source_live_playable_source_coordinator.dart';

/// Opens one exact source episode through the existing playback pipeline.
/// This page never receives or resolves a raw media URL.
final class PlayerPage extends StatefulWidget {
  const PlayerPage({
    required this.sourceController,
    required this.episode,
    this.sourceEpisode,
    this.sourcePlayableFallback,
    super.key,
  });

  final SubjectSourcePlaybackController sourceController;
  final BangumiEpisode episode;
  final SourceEpisodeIdentity? sourceEpisode;
  final InAppWebViewSourceLivePlayableDocumentFallback? sourcePlayableFallback;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

final class _PlayerPageState extends State<PlayerPage> {
  StreamSubscription<PlaybackEvent>? _events;
  PlaybackState _state = PlaybackState.opening;
  String? _failureCode;
  bool _opening = true;
  SourceLiveCapturePackagePlan? _capturePlan;
  SourceLiveCapturePackageResult? _captureAdmission;
  SourceEpisodeIdentity? _activeSourceEpisode;
  Duration _lastPosition = Duration.zero;
  var _openGeneration = 0;
  var _captureCompleted = false;

  PlaybackCoordinator? get _coordinator =>
      widget.sourceController.playbackCoordinator;

  @override
  void initState() {
    super.initState();
    _events = _coordinator?.events.listen(_onPlaybackEvent);
    widget.sourcePlayableFallback?.addListener(_onPlayableFallbackChanged);
    unawaited(_open());
  }

  void _onPlayableFallbackChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _open({
    SourceEpisodeIdentity? requestedIdentity,
    Duration? resumePosition,
  }) async {
    final generation = ++_openGeneration;
    setState(() {
      _opening = true;
      _failureCode = null;
      _state = PlaybackState.opening;
      _capturePlan = null;
      _captureAdmission = null;
      _activeSourceEpisode = null;
      _captureCompleted = false;
    });
    try {
      final identity =
          requestedIdentity ??
          widget.sourceEpisode ??
          (await widget.sourceController.resolveEpisode(
            widget.episode,
          )).identity;
      if (!mounted || generation != _openGeneration) return;
      if (identity == null) {
        if (!mounted) return;
        setState(() {
          _opening = false;
          _failureCode = 'episode_selection_required';
        });
        return;
      }
      _activeSourceEpisode = identity;
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
      if (!mounted || generation != _openGeneration) return;
      final opened =
          result.status == SourceInstalledLivePlaybackPipelineStatus.opened ||
          result.status == SourceInstalledLivePlaybackPipelineStatus.partial;
      if (!opened &&
          _captureFallbackReason(result.reasonCode) &&
          result.targetResults.length == 1 &&
          result.targetResults.single.plan != null &&
          _prepareCaptureFallback(result.targetResults.single.planResult)) {
        return;
      }
      setState(() {
        _opening = false;
        _failureCode = opened
            ? null
            : result.reasonCode ?? 'playback_resolution_failed';
        _state = opened ? PlaybackState.ready : PlaybackState.failed;
      });
      if (opened &&
          resumePosition != null &&
          _isSafeResumePosition(resumePosition)) {
        try {
          await _coordinator?.seek(resumePosition);
        } on Object {
          // A resume seek is best effort; the new exact session remains the
          // authoritative playback identity when a backend rejects the seek.
        }
      }
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

  bool _prepareCaptureFallback(
    SourceLiveOperationPlanResult<SourceLivePlayableSourcePlan> planResult,
  ) {
    final support = widget.sourceController;
    final plan = planResult.plan;
    final browserPort = support.captureBrowserPort;
    final wynimeVersion = support.wynimeVersion;
    if (plan == null ||
        browserPort == null ||
        wynimeVersion == null ||
        support.capturePlaybackEntryPoint == null) {
      return false;
    }
    try {
      final webRequest = WebCaptureRequest(
        initialUri: plan.requestPlan.request.uri,
        securityPolicy: plan.requestPlan.request.securityPolicy,
        budget: _captureBudget(plan.requestPlan.request.securityPolicy),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: true,
        completionPolicy:
            WebCaptureCompletionPolicy.firstValidatedPlayableCandidateAfterLoad,
        postLoadTimeout: const Duration(seconds: 20),
        initialHeaders: plan.requestPlan.request.headers,
      );
      final capturePlan = SourceLiveCapturePackagePlan(
        installedPackage: plan.requestPlan.installedPackage,
        programId: planResult.programId,
        webCaptureRequest: webRequest,
      );
      setState(() {
        _capturePlan = capturePlan;
        _captureAdmission = null;
        _captureCompleted = false;
        _opening = false;
        _failureCode = null;
        _state = PlaybackState.opening;
      });
      return true;
    } on Object {
      return false;
    }
  }

  void _onCaptureAdmission(SourceLiveCapturePackageResult admission) {
    if (!mounted || _captureCompleted) return;
    _captureAdmission = admission;
    if (admission.status != SourceLiveCapturePackageStatus.ready) {
      setState(() {
        _capturePlan = null;
        _opening = false;
        _failureCode = admission.reasonCode ?? 'capture_admission_failed';
        _state = PlaybackState.failed;
      });
      return;
    }
    setState(() {});
  }

  Future<void> _onCaptureResult(
    SourceLiveCapturePackagePlan capturePlan,
    SourceLiveCaptureResult captureResult,
  ) async {
    if (!mounted || _captureCompleted) return;
    _captureCompleted = true;
    // The source page is acquisition-only. Remove its transient WebView
    // before the captured candidate enters the normal playback pipeline.
    setState(() {
      _capturePlan = null;
      _opening = true;
      _failureCode = null;
      _state = PlaybackState.opening;
    });
    final admission = _captureAdmission;
    final entryPoint = widget.sourceController.capturePlaybackEntryPoint;
    final identity = _activeSourceEpisode;
    if (admission == null ||
        admission.status != SourceLiveCapturePackageStatus.ready ||
        entryPoint == null ||
        identity == null ||
        captureResult.status != SourceLiveCaptureStatus.captured ||
        captureResult.snapshot == null) {
      _finishCaptureFailure(
        captureResult.reasonCode ?? 'capture_result_not_ready',
      );
      return;
    }

    try {
      final mappings = <SourceLiveCapturePlayableSourceMapping>[];
      final candidates = captureResult.snapshot!.candidates;
      for (var index = 0; index < candidates.length; index++) {
        final candidate = candidates[index];
        mappings.add(
          SourceLiveCapturePlayableSourceMapping(
            candidateIndex: index,
            sourceKey:
                '${capturePlan.installedPackage.package.packageId}-captured-$index',
            label:
                '${capturePlan.installedPackage.package.displayName} ${candidate.kind.name}',
          ),
        );
      }
      final result = await entryPoint.openCapturedLive(
        packagePlan: capturePlan,
        admission: admission,
        captureResult: captureResult,
        episode: identity,
        mappings: mappings,
        adRemovalPlan: _adRemovalPlan(identity),
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
          result.status == SourceLiveCapturePlaybackEntryStatus.opened;
      setState(() {
        _opening = false;
        _failureCode = opened
            ? null
            : result.planResult?.reasonCode ??
                  result.pipelineResult?.reasonCode ??
                  'capture_playback_failed';
        _state = opened ? PlaybackState.ready : PlaybackState.failed;
      });
    } on Object {
      _finishCaptureFailure('capture_playback_failed');
    }
  }

  void _finishCaptureFailure(String code) {
    if (!mounted) return;
    setState(() {
      _capturePlan = null;
      _opening = false;
      _failureCode = code;
      _state = PlaybackState.failed;
    });
  }

  void _onPlaybackEvent(PlaybackEvent event) {
    if (!mounted) return;
    _lastPosition = event.position;
    setState(() {
      _state = event.state;
      if (event.failure != null) {
        _failureCode = event.failure!.code;
      }
    });
  }

  List<SourceSubjectLine> get _availableLines =>
      widget.sourceController.state.subjectDetails?.lines ?? const [];

  String? _lineTitle(String? lineId) {
    if (lineId == null) return null;
    for (final line in _availableLines) {
      if (line.lineId == lineId) return line.title;
    }
    return lineId;
  }

  Future<void> _selectLine() async {
    if (_opening) return;
    final active = _activeSourceEpisode;
    final lines = _availableLines;
    if (active == null || !SourceLineSelector.shouldShow(lines)) return;
    final selectedLine = await showModalBottomSheet<SourceSubjectLine>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: SourceLineSelector(
          lines: lines,
          selectedLineId: active.lineId,
          onSelected: (line) => Navigator.of(sheetContext).pop(line),
        ),
      ),
    );
    if (!mounted ||
        selectedLine == null ||
        selectedLine.lineId == active.lineId) {
      return;
    }
    final resumePosition = _isSafeResumePosition(_lastPosition)
        ? _lastPosition
        : null;
    try {
      await _coordinator?.stop();
      if (!mounted) return;
      final resolution = await widget.sourceController.resolveEpisode(
        widget.episode,
        preferredLineId: selectedLine.lineId,
      );
      if (!mounted) return;
      SourceEpisodeIdentity? identity = resolution.identity;
      if (resolution.status ==
          SourceEpisodeResolutionStatus.selectionRequired) {
        final candidates = resolution.candidates
            .where(
              (candidate) => candidate.identity.lineId == selectedLine.lineId,
            )
            .toList(growable: false);
        final selected = await _selectEpisodeCandidate(candidates);
        if (!mounted || selected == null) return;
        final confirmed = await widget.sourceController.selectEpisode(
          widget.episode,
          selected,
        );
        identity = confirmed.identity;
      }
      if (identity == null || identity.lineId != selectedLine.lineId) {
        _finishCaptureFailure('line_episode_not_found');
        return;
      }
      await _open(requestedIdentity: identity, resumePosition: resumePosition);
    } on Object {
      _finishCaptureFailure('line_switch_failed');
    }
  }

  Future<SourceEpisode?> _selectEpisodeCandidate(
    List<SourceEpisode> candidates,
  ) async {
    if (candidates.isEmpty) return null;
    if (candidates.length == 1) return candidates.single;
    return showDialog<SourceEpisode>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('選擇來源集數'),
        content: SizedBox(
          width: 420,
          child: ListView(
            shrinkWrap: true,
            children: [
              for (final candidate in candidates)
                ListTile(
                  title: Text(candidate.title),
                  onTap: () => Navigator.of(dialogContext).pop(candidate),
                ),
            ],
          ),
        ),
      ),
    );
  }

  bool _isSafeResumePosition(Duration position) {
    if (position.isNegative) return false;
    final duration = widget.episode.duration;
    if (duration == null || duration < 0) return true;
    return position <= Duration(seconds: duration);
  }

  @override
  void dispose() {
    unawaited(_events?.cancel());
    unawaited(_coordinator?.stop());
    widget.sourcePlayableFallback?.removeListener(_onPlayableFallbackChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final capturePlan = _capturePlan;
    final captureVersion = widget.sourceController.wynimeVersion;
    final captureBrowserPort = widget.sourceController.captureBrowserPort;
    final title = widget.episode.nameCn.isNotEmpty
        ? widget.episode.nameCn
        : widget.episode.name;
    return Scaffold(
      appBar: AppBar(
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          if (_availableLines.length > 1)
            IconButton(
              key: const ValueKey('player-line-selector'),
              tooltip: '線路',
              onPressed: _opening ? null : () => unawaited(_selectLine()),
              icon: const Icon(Icons.alt_route_rounded),
            ),
        ],
      ),
      body: SafeArea(
        child: Stack(
          children: [
            Column(
              children: [
                Expanded(
                  child: ColoredBox(
                    color: Colors.black,
                    child: _opening
                        ? _ResolvingPanel(
                            sourceName: widget
                                .sourceController
                                .state
                                .package
                                ?.package
                                .displayName,
                            lineName: _lineTitle(_activeSourceEpisode?.lineId),
                          )
                        : _failureCode != null
                        ? PlaybackFailurePanel(
                            code: _failureCode!,
                            onRetry: () => unawaited(_open()),
                            onSwitchLine:
                                isPlaybackLine502FailureCode(_failureCode!) &&
                                    SourceLineSelector.shouldShow(
                                      _availableLines,
                                    )
                                ? () => unawaited(_selectLine())
                                : null,
                          )
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
            if (capturePlan != null &&
                captureVersion != null &&
                captureBrowserPort != null)
              Positioned(
                left: 0,
                top: 0,
                width: 1,
                height: 1,
                child: _BackgroundAcquisitionHost(
                  child: _CaptureHost(
                    plan: capturePlan,
                    wynimeVersion: captureVersion,
                    browserPort: captureBrowserPort,
                    onAdmission: _onCaptureAdmission,
                    onResult: (result) =>
                        unawaited(_onCaptureResult(capturePlan, result)),
                  ),
                ),
              ),
            if (widget.sourcePlayableFallback?.hasPendingCapture == true)
              Positioned(
                left: 0,
                top: 0,
                width: 1,
                height: 1,
                child: _BackgroundAcquisitionHost(
                  child: widget.sourcePlayableFallback!.buildView(context),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

final class _CaptureHost extends StatelessWidget {
  const _CaptureHost({
    required this.plan,
    required this.wynimeVersion,
    required this.browserPort,
    required this.onAdmission,
    required this.onResult,
  });

  final SourceLiveCapturePackagePlan plan;
  final Version wynimeVersion;
  final WebSourceBrowserPort browserPort;
  final ValueChanged<SourceLiveCapturePackageResult> onAdmission;
  final ValueChanged<SourceLiveCaptureResult> onResult;

  @override
  Widget build(BuildContext context) {
    return InAppWebViewInstalledSourceLiveCapture(
      wynimeVersion: wynimeVersion,
      plan: plan,
      browserPort: browserPort,
      onAdmission: onAdmission,
      onResult: onResult,
      loadingBuilder: (_) => const SizedBox.shrink(),
      unavailableBuilder: (_, status) => const SizedBox.shrink(),
    );
  }
}

final class _BackgroundAcquisitionHost extends StatelessWidget {
  const _BackgroundAcquisitionHost({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: IgnorePointer(
      child: ClipRect(child: SizedBox(width: 1, height: 1, child: child)),
    ),
  );
}

final class _ResolvingPanel extends StatelessWidget {
  const _ResolvingPanel({this.sourceName, this.lineName});

  final String? sourceName;
  final String? lineName;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(WynimeSpacing.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(color: Colors.white),
          const SizedBox(height: WynimeSpacing.md),
          const Text('正在解析播放來源…', style: TextStyle(color: Colors.white)),
          if (sourceName != null) ...[
            const SizedBox(height: WynimeSpacing.xs),
            Text(
              '來源：$sourceName',
              style: const TextStyle(color: Colors.white70),
            ),
          ],
          if (lineName != null) ...[
            const SizedBox(height: WynimeSpacing.xxs),
            Text('線路：$lineName', style: const TextStyle(color: Colors.white70)),
          ],
        ],
      ),
    ),
  );
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

bool isPlaybackLine502FailureCode(String code) =>
    const {'http_status_502', 'upstream_http_502'}.contains(code);

final class PlaybackFailurePanel extends StatelessWidget {
  const PlaybackFailurePanel({
    required this.code,
    required this.onRetry,
    this.onSwitchLine,
    super.key,
  });

  final String code;
  final VoidCallback onRetry;
  final VoidCallback? onSwitchLine;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final isLine502 = isPlaybackLine502FailureCode(code);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, color: Colors.white, size: 40),
            const SizedBox(height: WynimeSpacing.sm),
            Text(
              isLine502
                  ? localizations.playbackLine502Title
                  : localizations.playbackFailureTitle,
              style: const TextStyle(color: Colors.white),
            ),
            if (isLine502) ...[
              const SizedBox(height: WynimeSpacing.xs),
              Text(
                localizations.playbackLine502Description,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70),
              ),
            ],
            const SizedBox(height: WynimeSpacing.sm),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: WynimeSpacing.sm,
              runSpacing: WynimeSpacing.sm,
              children: [
                FilledButton.icon(
                  onPressed: onRetry,
                  icon: const Icon(Icons.refresh),
                  label: Text(localizations.subjectDetailRetryAction),
                ),
                if (onSwitchLine != null)
                  OutlinedButton.icon(
                    onPressed: onSwitchLine,
                    icon: const Icon(Icons.alt_route_rounded),
                    label: Text(localizations.playbackSwitchLineAction),
                  ),
              ],
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

bool _captureFallbackReason(String? reasonCode) => const {
  'no_usable_playback_sources',
  'live_playable_sources_not_found',
  'live_playable_failed',
}.contains(reasonCode);

WebCaptureBudget _captureBudget(SourceSecurityPolicy policy) {
  final resourceBudget = policy.budget;
  return WebCaptureBudget(
    maxEvents: _boundedCaptureValue(resourceBudget.maxRecords * 4, 1, 5000),
    maxCandidates: _boundedCaptureValue(resourceBudget.maxRecords, 1, 1000),
    maxHeaderBytes: _boundedCaptureValue(
      resourceBudget.maxDocumentBytes,
      0,
      256 * 1024,
    ),
    maxCookieBytes: _boundedCaptureValue(
      resourceBudget.maxDocumentBytes ~/ 4,
      0,
      256 * 1024,
    ),
  );
}

int _boundedCaptureValue(int value, int minimum, int maximum) {
  if (value < minimum) return minimum;
  if (value > maximum) return maximum;
  return value;
}

final class _PlayerPageFailure implements Exception {
  const _PlayerPageFailure(this.code);

  final String code;
}
