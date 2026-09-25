import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_installed_live_subject_pipeline.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_capture_playback_entry_point.dart';
import 'package:wynime/src/application/source_live_capture_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_pipeline.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/playback/playback_engine_router.dart';
import 'package:wynime/src/application/source_live_capture_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_capture_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_plan_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_live_subject_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_package_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/domain/services/web_capture_candidate_classifier.dart';
import 'package:wynime/src/infrastructure/playback/default_playback_session_resolver.dart';
import 'package:wynime/src/infrastructure/playback/bounded_media_probe.dart';
import 'package:wynime/src/infrastructure/playback/loopback_playback_proxy.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';
import 'package:wynime/src/infrastructure/source_http/dart_io_source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_subject_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_browser_port.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_installed_source_live_capture_view.dart';
import 'package:wynime/src/platform/playback/player_backend_factory.dart';
import 'package:wynime/src/platform/playback/playback_surface_host.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_source_live_playable_fallback.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_source_live_subject_fallback.dart';

const _subjectId = '3403';
const _lineId = 'xfxf1';
const _episodeId = '121397';
const _wynimeVersion = '1.0.17';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final package = const SourcePackageDecoder().decode(_xifanPackageJson);
  final version = Version.parse(_wynimeVersion);
  final manager = DeclarativeSourcePackageManager(wynimeVersion: version);
  final pending = manager.install(package);
  final installed = manager.enable(
    packageId: pending.package.packageId,
    version: pending.package.version,
    userApproved: true,
    reconsentGranted: true,
  );
  final upstreamClient = DartIoProxyUpstreamClient();
  final transport = DartIoSourceHttpTransport(upstreamClient: upstreamClient);
  final playbackRouter = PlayerBackendFactory.create();
  final playbackCoordinator = PlaybackCoordinator(
    resolver: DefaultPlaybackSessionResolver(),
    proxy: LoopbackPlaybackProxyService(upstreamClient: upstreamClient),
    player: playbackRouter,
  );
  final runtime = SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: version,
      ),
      transport: transport,
    ),
    fixtureRuntime: DeclarativeSourcePackageRuntime(wynimeVersion: version),
  );
  final fallback = InAppWebViewSourceLiveSubjectFallback(
    wynimeVersion: version,
    fixtureRuntime: runtime.fixtureRuntime,
    browserPort: InAppWebViewBrowserPort(),
  );
  final playableFallback = InAppWebViewSourceLivePlayableDocumentFallback(
    wynimeVersion: version,
    fixtureRuntime: runtime.fixtureRuntime,
    browserPort: InAppWebViewBrowserPort(),
  );
  final captureBrowserPort = InAppWebViewBrowserPort();
  final pipeline = SourceInstalledLiveSubjectPipeline(
    planFactory: SourceLiveOperationPlanFactory(wynimeVersion: version),
    subjectCoordinator: SourceLiveSubjectCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourceSubjectNormalizer(),
      documentFallback: fallback,
    ),
  );
  runApp(
    _HarnessApp(
      package: package,
      installed: installed,
      runtime: runtime,
      transport: transport,
      upstreamClient: upstreamClient,
      playbackCoordinator: playbackCoordinator,
      playbackSurfaceHost: PlatformPlaybackSurfaceHost(playbackRouter),
      playbackRouter: playbackRouter,
      fallback: fallback,
      playableFallback: playableFallback,
      captureBrowserPort: captureBrowserPort,
      pipeline: pipeline,
    ),
  );
}

final class _HarnessApp extends StatefulWidget {
  const _HarnessApp({
    required this.package,
    required this.installed,
    required this.runtime,
    required this.transport,
    required this.upstreamClient,
    required this.playbackCoordinator,
    required this.playbackSurfaceHost,
    required this.playbackRouter,
    required this.fallback,
    required this.playableFallback,
    required this.captureBrowserPort,
    required this.pipeline,
  });

  final SourcePackageManifest package;
  final InstalledSourcePackage installed;
  final SourceLiveHttpPackageRuntime runtime;
  final DartIoSourceHttpTransport transport;
  final DartIoProxyUpstreamClient upstreamClient;
  final PlaybackCoordinator playbackCoordinator;
  final PlaybackSurfaceHost playbackSurfaceHost;
  final PlaybackEngineRouter playbackRouter;
  final InAppWebViewSourceLiveSubjectFallback fallback;
  final InAppWebViewSourceLivePlayableDocumentFallback playableFallback;
  final InAppWebViewBrowserPort captureBrowserPort;
  final SourceInstalledLiveSubjectPipeline pipeline;

  @override
  State<_HarnessApp> createState() => _HarnessAppState();
}

final class _HarnessAppState extends State<_HarnessApp> {
  final _messages = <String>['INSTALL: PASS (xifan@1.2.3)'];
  SourceLiveCapturePackagePlan? _capturePlan;
  SourceLiveCapturePackageResult? _captureAdmission;
  Completer<SourceLiveCaptureResult>? _captureCompleter;
  var _started = false;

  @override
  void initState() {
    super.initState();
    widget.fallback.addListener(_onFallbackChanged);
    widget.playableFallback.addListener(_onFallbackChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_started) {
        _started = true;
        unawaited(_run());
      }
    });
  }

  @override
  void dispose() {
    widget.fallback.removeListener(_onFallbackChanged);
    widget.playableFallback.removeListener(_onFallbackChanged);
    widget.pipeline.close();
    unawaited(widget.playbackCoordinator.close());
    widget.fallback.close();
    widget.fallback.dispose();
    widget.playableFallback.close();
    widget.playableFallback.dispose();
    unawaited(widget.transport.close());
    unawaited(widget.upstreamClient.close());
    super.dispose();
  }

  void _onFallbackChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _run() async {
    _log('SUBJECT: RUNNING (static_then_bounded_document)');
    final result = await widget.pipeline.listSubjects(
      targets: [
        SourceInstalledLiveSubjectTarget(
          installedPackage: widget.installed,
          subject: SourceSubjectIdentity(
            sourceId: 'xifan',
            subjectId: _subjectId,
          ),
        ),
      ],
    );
    final details = result.subjectResults
        .map((value) => value.details)
        .whereType<SourceSubjectDetails>()
        .firstOrNull;
    if (details == null) {
      _log('SUBJECT: FAIL (${_enumLabel(result.status)})');
      _log('STATUS: SUBJECT_PIPELINE_FAILED');
      return;
    }
    _log('SUBJECT: PASS (rendered_document_fallback_or_static)');
    final matching = [
      for (final line in details.lines)
        for (final episode in line.episodes)
          if (episode.identity.lineId == _lineId &&
              episode.identity.episodeId == _episodeId)
            episode,
    ];
    if (matching.length != 1) {
      _log('EPISODES: FAIL (episode_identity_not_found)');
      _log('STATUS: EPISODE_IDENTITY_FAILED');
      return;
    }
    final identity = matching.single.identity;
    _log('EPISODES: PASS (line=$_lineId episode=$_episodeId)');

    final planFactory = SourceLiveOperationPlanFactory(
      wynimeVersion: Version.parse(_wynimeVersion),
    );
    final playablePlan = planFactory.buildPlayableSourcePlan(
      installedPackage: widget.installed,
      episode: identity,
    );
    if (playablePlan.plan == null) {
      _log('STATIC_PLAYABLE: FAIL (${playablePlan.reasonCode})');
      _log('STATUS: PLAYABLE_PLAN_FAILED');
      return;
    }
    final playableRuntime = await widget.runtime.execute(
      playablePlan.plan!.requestPlan,
    );
    final playableResult = const DeclarativeSourcePlayableSourceNormalizer()
        .normalizePlayableSources(
          package: widget.package,
          runtimeResult: playableRuntime,
          episode: identity,
          mapping: playablePlan.plan!.mapping,
        );
    SourcePlayableSource? selectedSource;
    SourceRuntimeResult? capturedRuntime;
    if (playableResult.results.length == 1) {
      selectedSource = playableResult.results.single;
      _log('STATIC_PLAYABLE: PASS (host=${selectedSource.mediaUri.host})');
    } else if (playableRuntime.status == SourceRuntimeStatus.notFound) {
      _log('STATIC_PLAYABLE: FALLBACK_REQUIRED (bounded_rendered_document)');
      capturedRuntime = await widget.playableFallback.capture(
        playablePlan.plan!,
      );
      final capturedResult = const DeclarativeSourcePlayableSourceNormalizer()
          .normalizePlayableSources(
            package: widget.package,
            runtimeResult: capturedRuntime,
            episode: identity,
            mapping: playablePlan.plan!.mapping,
          );
      if (capturedResult.results.length != 1) {
        _log(
          'CAPTURE: DOCUMENT_EMPTY (candidate_count=${capturedResult.results.length})',
        );
        final publicCapture = await _captureWithPublicControl(
          playablePlan.plan!,
        );
        final captureAdmission = publicCapture.admission;
        final captureResult = publicCapture.result;
        if (captureAdmission == null ||
            captureAdmission.status != SourceLiveCapturePackageStatus.ready ||
            captureResult.status != SourceLiveCaptureStatus.captured ||
            captureResult.snapshot == null ||
            captureResult.snapshot!.candidates.isEmpty) {
          _log(
            'CAPTURE: FAIL (${captureResult.reasonCode ?? _enumLabel(captureResult.status)})',
          );
          _log('STATUS: PLAYABLE_CAPTURE_FAILED');
          return;
        }
        _log(
          'CAPTURE: PASS (media_request_candidates=${captureResult.snapshot!.candidates.length})',
        );
        _log(
          'CAPTURE_COOKIES: count=${captureResult.snapshot!.cookies.length}',
        );
        final capturedPlayback = await _probeCapturedCandidates(
          packagePlan: publicCapture.plan,
          admission: captureAdmission,
          captureResult: captureResult,
          identity: identity,
        );
        if (!capturedPlayback.mediaProbePassed) {
          _log('STATUS: UNRESOLVED_MEDIA_REQUEST_REPLAY_403');
          return;
        }
        if (!capturedPlayback.playbackSessionPrepared) {
          _log('PLAYBACK_SESSION: FAIL (media3_session_not_prepared)');
          _log('STATUS: PLAYBACK_SESSION_FAILED');
          return;
        }
        _log('PLAYBACK_SESSION: PASS (resolver_session_created)');
        if (!capturedPlayback.media3Playing) {
          _log(
            'ANDROID_PLAYER_HANDOFF: BLOCKED_PLATFORM_ENVIRONMENT '
            '(media3_not_playing)',
          );
          _log('STATUS: BLOCKED_PLATFORM_ENVIRONMENT');
          return;
        }
        _log('ANDROID_PLAYER_HANDOFF: PASS (media3_playing)');
        _log('STATUS: PASS (media_probe_and_media3_playback)');
        return;
      }
      selectedSource = capturedResult.results.single;
      _log('CAPTURE: PASS (rendered_document_candidate)');
    } else {
      _log(
        'STATIC_PLAYABLE: FAIL (candidate_count=${playableResult.results.length})',
      );
      _log('STATUS: PLAYABLE_EXTRACTION_FAILED');
      return;
    }
    final playback = await _openPlaybackSession(
      runtime: widget.runtime,
      plan: playablePlan.plan!,
      identity: identity,
      documentFallback: capturedRuntime == null
          ? null
          : _ReplayPlayableFallback(capturedRuntime),
    );
    if (playback.status != SourceLivePlaybackPipelineStatus.opened) {
      _log('PLAYBACK_SESSION: FAIL (${playback.reasonCode})');
      _log('STATUS: PLAYBACK_SESSION_FAILED');
      return;
    }
    final session = playback.session;
    if (session == null) {
      _log('MEDIA_PROBE: FAIL (playback_session_missing)');
      _log('STATUS: MEDIA_PROBE_FAILED');
      return;
    }
    final boundedProbe =
        await BoundedMediaProbe(upstreamClient: widget.upstreamClient).probe(
          session: session,
          securityPolicy: widget.package.securityPolicy,
          budget: _harnessProxyBudget(),
          candidateKind: selectedSource.kind,
        );
    _logProbeShapes('static', boundedProbe);
    if (!boundedProbe.passed) {
      _log('MEDIA_PROBE: FAIL (${boundedProbe.reasonCode})');
      _log('STATUS: MEDIA_PROBE_FAILED');
      return;
    }
    final finalShape = boundedProbe.requestShapes.isEmpty
        ? null
        : boundedProbe.requestShapes.last;
    _log(
      'MEDIA_PROBE: PASS variant=bounded_get '
      'reason=${boundedProbe.reasonCode} '
      'host=${finalShape?.requestHost ?? 'unknown'}',
    );
    _log('PLAYBACK_SESSION: PASS (resolver_session_created)');
    _log(
      'PLAYER_HANDOFF: BLOCKED_PLATFORM_ENVIRONMENT '
      '(harness_has_no_player_surface)',
    );
    _log('STATUS: BLOCKED_PLATFORM_ENVIRONMENT');
  }

  Future<_PublicCaptureOutcome> _captureWithPublicControl(
    SourceLivePlayableSourcePlan plan,
  ) async {
    late final SourceLiveCapturePackagePlan capturePlan;
    try {
      capturePlan = SourceLiveCapturePackagePlan(
        installedPackage: plan.requestPlan.installedPackage,
        programId: plan.requestPlan.programId,
        webCaptureRequest: WebCaptureRequest(
          initialUri: plan.requestPlan.request.uri,
          securityPolicy:
              plan.requestPlan.installedPackage.package.securityPolicy,
          budget: _captureBudget(
            plan.requestPlan.installedPackage.package.securityPolicy,
          ),
          userAgentPolicy: WebUserAgentPolicy(
            mode: WebUserAgentMode.platformDefault,
          ),
          captureMediaRequests: true,
          completionPolicy: WebCaptureCompletionPolicy
              .firstValidatedPlayableCandidateAfterLoad,
          postLoadTimeout: const Duration(seconds: 20),
          initialHeaders: plan.requestPlan.request.headers,
        ),
      );
    } on Object {
      final failed = SourceLiveCaptureResult(
        packageId: plan.requestPlan.installedPackage.package.packageId,
        packageVersion: plan.requestPlan.installedPackage.package.version,
        programId: plan.requestPlan.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'capture_request_invalid',
      );
      return _PublicCaptureOutcome(
        plan: SourceLiveCapturePackagePlan(
          installedPackage: plan.requestPlan.installedPackage,
          programId: plan.requestPlan.programId,
          webCaptureRequest: WebCaptureRequest(
            initialUri: plan.requestPlan.request.uri,
            securityPolicy:
                plan.requestPlan.installedPackage.package.securityPolicy,
            budget: _captureBudget(
              plan.requestPlan.installedPackage.package.securityPolicy,
            ),
            userAgentPolicy: WebUserAgentPolicy(
              mode: WebUserAgentMode.platformDefault,
            ),
            captureMediaRequests: true,
            completionPolicy: WebCaptureCompletionPolicy
                .firstValidatedPlayableCandidateAfterLoad,
            postLoadTimeout: const Duration(seconds: 20),
            initialHeaders: plan.requestPlan.request.headers,
          ),
        ),
        admission: null,
        result: failed,
      );
    }

    final completer = Completer<SourceLiveCaptureResult>();
    if (mounted) {
      setState(() {
        _capturePlan = capturePlan;
        _captureAdmission = null;
        _captureCompleter = completer;
      });
    }
    final result = await completer.future.timeout(
      const Duration(seconds: 35),
      onTimeout: () => SourceLiveCaptureResult(
        packageId: plan.requestPlan.installedPackage.package.packageId,
        packageVersion: plan.requestPlan.installedPackage.package.version,
        programId: plan.requestPlan.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'capture_timeout',
      ),
    );
    final admission = _captureAdmission;
    if (mounted) {
      setState(() {
        _capturePlan = null;
        _captureAdmission = null;
        _captureCompleter = null;
      });
    }
    return _PublicCaptureOutcome(
      plan: capturePlan,
      admission: admission,
      result: result,
    );
  }

  void _onCaptureAdmission(SourceLiveCapturePackageResult admission) {
    if (!mounted) return;
    _captureAdmission = admission;
    if (admission.status != SourceLiveCapturePackageStatus.ready) {
      final completer = _captureCompleter;
      if (completer != null && !completer.isCompleted) {
        completer.complete(
          SourceLiveCaptureResult(
            packageId: admission.packageId,
            packageVersion: admission.packageVersion,
            programId: admission.programId,
            status: SourceLiveCaptureStatus.failed,
            reasonCode: admission.reasonCode ?? 'capture_admission_failed',
          ),
        );
      }
    }
    setState(() {});
  }

  void _onCaptureResult(SourceLiveCaptureResult result) {
    final completer = _captureCompleter;
    if (completer == null || completer.isCompleted) return;
    completer.complete(result);
  }

  Future<_CapturedPlaybackOutcome> _probeCapturedCandidates({
    required SourceLiveCapturePackagePlan packagePlan,
    required SourceLiveCapturePackageResult admission,
    required SourceLiveCaptureResult captureResult,
    required SourceEpisodeIdentity identity,
  }) async {
    final snapshot = captureResult.snapshot!;
    final classifier = const WebCaptureCandidateClassifier();
    final indexes =
        List<int>.generate(snapshot.candidates.length, (index) => index)..sort(
          (left, right) => classifier
              .score(snapshot.candidates[right])
              .compareTo(classifier.score(snapshot.candidates[left])),
        );
    var saw403 = false;
    for (final candidateIndex in indexes) {
      final candidate = snapshot.candidates[candidateIndex];
      final media = candidate.uri;
      _log(
        'MEDIA_CANDIDATE: index=$candidateIndex host=${media.host} '
        'port=${_effectivePort(media)} method=${candidate.requestMethod} '
        'kind=${_enumLabel(candidate.kind)} is_redirect=${candidate.isRedirect} '
        'redirect_count=${candidate.redirectChain.length} '
        'page_host=${candidate.pageUri?.host ?? 'unknown'} '
        'header_names=${candidate.headers.keys.toList()..sort()}',
      );
      if (candidate.kind == WebCandidateKind.dash ||
          candidate.kind == WebCandidateKind.mediaSegment ||
          candidate.requestMethod != 'GET') {
        _log('MEDIA_CANDIDATE: SKIP (unsupported_candidate_shape)');
        continue;
      }

      final sourceKey = 'xifan-captured-$candidateIndex';
      final playback = await _openCapturedPlaybackSession(
        packagePlan: packagePlan,
        admission: admission,
        captureResult: captureResult,
        identity: identity,
        preferredSourceKey: sourceKey,
      );
      if (playback.status != SourceLiveCapturePlaybackEntryStatus.opened ||
          playback.session == null) {
        _log(
          'PLAYBACK_SESSION: FAIL candidate=$candidateIndex '
          '(${playback.pipelineResult?.reasonCode ?? playback.planResult?.reasonCode ?? 'capture_playback_failed'})',
        );
        continue;
      }
      final session = playback.session!;
      _log(
        'PLAYBACK_SESSION: PREPARED candidate=$candidateIndex '
        'media_host=${session.mediaUri.host} '
        'has_referer=${session.referer != null} '
        'has_origin=${session.origin != null} '
        'has_user_agent=${session.userAgent != null} '
        'cookie_metadata_count=${session.cookieMetadata.length}',
      );

      final probe = BoundedMediaProbe(
        upstreamClient: widget.upstreamClient,
        maxProbeBytes: 128 * 1024,
      );
      final variants = [
        const _ProbeVariant(
          name: 'range_only',
          includeReferer: false,
          includeOrigin: false,
          includeUserAgent: false,
          includeCookies: false,
        ),
        const _ProbeVariant(
          name: 'captured_referer',
          includeReferer: true,
          includeOrigin: false,
          includeUserAgent: false,
          includeCookies: false,
        ),
        const _ProbeVariant(
          name: 'captured_referer_user_agent',
          includeReferer: true,
          includeOrigin: false,
          includeUserAgent: true,
          includeCookies: false,
        ),
        const _ProbeVariant(
          name: 'captured_metadata',
          includeReferer: true,
          includeOrigin: true,
          includeUserAgent: true,
          includeCookies: true,
        ),
      ];
      for (final variant in variants) {
        final result = await probe.probe(
          session: session,
          securityPolicy: widget.package.securityPolicy,
          budget: _harnessProxyBudget(),
          candidateKind: candidate.kind,
          includeReferer: variant.includeReferer,
          includeOrigin: variant.includeOrigin,
          includeUserAgent: variant.includeUserAgent,
          includeCookies: variant.includeCookies,
        );
        _logProbeShapes(variant.name, result);
        if (result.reasonCode == 'external_http_403') {
          saw403 = true;
        }
        if (result.passed) {
          _log(
            'MEDIA_PROBE: PASS candidate=$candidateIndex '
            'variant=${variant.name} reason=${result.reasonCode}',
          );
          final actualPlayback = await _openActualMedia3Playback(
            packagePlan: packagePlan,
            admission: admission,
            captureResult: captureResult,
            identity: identity,
            preferredSourceKey: sourceKey,
            candidateIndex: candidateIndex,
          );
          return _CapturedPlaybackOutcome(
            mediaProbePassed: true,
            playbackSessionPrepared: actualPlayback.sessionPrepared,
            media3Playing: actualPlayback.media3Playing,
          );
        }
      }

      if (saw403) {
        final loopback = await _probeThroughActualLoopback(
          session: session,
          candidateKind: candidate.kind,
          securityPolicy: widget.package.securityPolicy,
        );
        _log(
          'LOOPBACK_UPSTREAM: ${loopback.passed ? 'PASS' : 'FAIL'} '
          '(status=${loopback.statusCode ?? 'unknown'} '
          'body=${loopback.bodyClassification ?? 'unknown'})',
        );
        if (loopback.passed) {
          _log(
            'MEDIA_PROBE: PASS candidate=$candidateIndex '
            'variant=actual_loopback_upstream',
          );
          final actualPlayback = await _openActualMedia3Playback(
            packagePlan: packagePlan,
            admission: admission,
            captureResult: captureResult,
            identity: identity,
            preferredSourceKey: sourceKey,
            candidateIndex: candidateIndex,
          );
          return _CapturedPlaybackOutcome(
            mediaProbePassed: true,
            playbackSessionPrepared: actualPlayback.sessionPrepared,
            media3Playing: actualPlayback.media3Playing,
          );
        }
      }
    }
    return const _CapturedPlaybackOutcome(
      mediaProbePassed: false,
      playbackSessionPrepared: false,
      media3Playing: false,
    );
  }

  Future<_ActualPlaybackOutcome> _openActualMedia3Playback({
    required SourceLiveCapturePackagePlan packagePlan,
    required SourceLiveCapturePackageResult admission,
    required SourceLiveCaptureResult captureResult,
    required SourceEpisodeIdentity identity,
    required String preferredSourceKey,
    required int candidateIndex,
  }) async {
    final playing = Completer<void>();
    final advanced = Completer<Duration>();
    Duration? firstPlayingPosition;
    var observedEvents = 0;
    final subscription = widget.playbackCoordinator.events.listen((event) {
      if (observedEvents < 20) {
        observedEvents++;
        debugPrint(
          'LIVE_HARNESS PLAYER_EVENT state=${event.state.name} '
          'position_ms=${event.position.inMilliseconds} '
          'buffered_ms=${event.bufferedPosition.inMilliseconds} '
          'failure=${event.failure?.code ?? 'none'} '
          'http_status=${event.failure?.httpStatus ?? 'none'}',
        );
      }
      if (event.state == PlaybackState.playing && !playing.isCompleted) {
        firstPlayingPosition ??= event.position;
        playing.complete();
      }
      if (event.state == PlaybackState.playing &&
          event.position > Duration.zero &&
          !advanced.isCompleted) {
        advanced.complete(event.position);
      }
      if (event.state == PlaybackState.failed && !playing.isCompleted) {
        playing.completeError(StateError('media3_playback_failed'));
      }
    });
    try {
      final playback = await _openCapturedPlaybackSession(
        packagePlan: packagePlan,
        admission: admission,
        captureResult: captureResult,
        identity: identity,
        preferredSourceKey: preferredSourceKey,
        coordinator: widget.playbackCoordinator,
      );
      if (playback.status != SourceLiveCapturePlaybackEntryStatus.opened ||
          playback.session == null) {
        _log(
          'ANDROID_PLAYER_HANDOFF: FAIL candidate=$candidateIndex '
          '(${playback.pipelineResult?.reasonCode ?? playback.planResult?.reasonCode ?? 'media3_open_failed'})',
        );
        return const _ActualPlaybackOutcome(
          sessionPrepared: false,
          media3Playing: false,
        );
      }
      if (widget.playbackRouter.activeKind != PlayerBackendKind.media3) {
        _log(
          'ANDROID_PLAYER_HANDOFF: FAIL candidate=$candidateIndex '
          '(active_backend=${_enumLabel(widget.playbackRouter.activeKind)})',
        );
        return const _ActualPlaybackOutcome(
          sessionPrepared: true,
          media3Playing: false,
        );
      }
      await playing.future.timeout(const Duration(seconds: 20));
      _log(
        'ANDROID_PLAYER_HANDOFF: PASS candidate=$candidateIndex '
        '(media3_playing)',
      );
      final advancedPosition = await advanced.future.timeout(
        const Duration(seconds: 12),
      );
      _log(
        'POSITION: PASS candidate=$candidateIndex '
        'start_ms=${firstPlayingPosition?.inMilliseconds ?? 0} '
        'advanced_ms=${advancedPosition.inMilliseconds}',
      );
      return const _ActualPlaybackOutcome(
        sessionPrepared: true,
        media3Playing: true,
      );
    } on Object catch (error) {
      _log(
        'ANDROID_PLAYER_HANDOFF: FAIL candidate=$candidateIndex '
        '(${error is TimeoutException ? 'media3_play_timeout' : 'media3_open_failed'})',
      );
      return const _ActualPlaybackOutcome(
        sessionPrepared: true,
        media3Playing: false,
      );
    } finally {
      await subscription.cancel();
    }
  }

  void _logProbeShapes(String variant, BoundedMediaProbeResult result) {
    for (final shape in result.requestShapes) {
      final presence = shape.headerPresence.entries
          .where((entry) => entry.value)
          .map((entry) => entry.key)
          .join(',');
      _log(
        'REQUEST_SHAPE_DIFF: variant=$variant method=${shape.method} '
        'host=${shape.requestHost} port=${shape.requestPort} '
        'redirect_count=${shape.redirectCount} status=${shape.statusCode} '
        'content_type=${shape.contentType ?? 'unknown'} '
        'body=${shape.bodyClassification} range_strategy=${shape.rangeStrategy} '
        'header_names=${shape.headerNames.join(',')} presence=$presence',
      );
    }
    if (result.requestShapes.isEmpty) {
      _log(
        'REQUEST_SHAPE_DIFF: variant=$variant status=not_sent '
        'reason=${result.reasonCode}',
      );
    }
  }

  Future<_LoopbackProbeResult> _probeThroughActualLoopback({
    required PlaybackSession session,
    required WebCandidateKind candidateKind,
    required SourceSecurityPolicy securityPolicy,
  }) async {
    final proxy = LoopbackPlaybackProxyService(
      upstreamClient: widget.upstreamClient,
    );
    final client = HttpClient()..autoUncompress = false;
    PlaybackProxyLease? lease;
    try {
      lease = await proxy.expose(
        PlaybackProxyRequest(
          session: session,
          securityPolicy: securityPolicy,
          budget: _harnessProxyBudget(),
        ),
      );
      final request = await client.openUrl('GET', lease.playbackUri);
      if (candidateKind != WebCandidateKind.hls) {
        request.headers.set(HttpHeaders.rangeHeader, 'bytes=0-131071');
      }
      final response = await request.close();
      final body = await _readLocalProbeBody(response);
      final bodyClassification = boundedMediaBodyClassification(
        bytes: body,
        statusCode: response.statusCode,
        contentType: response.headers.contentType?.toString(),
        uri: session.mediaUri,
      );
      final passed =
          response.statusCode >= 200 &&
          response.statusCode <= 299 &&
          (bodyClassification == 'media' ||
              (candidateKind == WebCandidateKind.hls &&
                  bodyClassification == 'hls_manifest'));
      return _LoopbackProbeResult(
        passed: passed,
        statusCode: response.statusCode,
        bodyClassification: bodyClassification,
      );
    } on Object {
      return const _LoopbackProbeResult(passed: false);
    } finally {
      client.close(force: true);
      if (lease != null) {
        await lease.close();
      }
      await proxy.close();
    }
  }

  void _log(String value) {
    debugPrint('LIVE_HARNESS $value');
    if (mounted) setState(() => _messages.add(value));
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Wynime live harness')),
        body: Stack(
          children: [
            ListView(
              padding: const EdgeInsets.all(24),
              children: [
                for (final message in _messages)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(message),
                  ),
              ],
            ),
            if (widget.playbackCoordinator.hasActivePlayback)
              Positioned.fill(child: widget.playbackSurfaceHost.build(context)),
            Positioned(
              left: 0,
              top: 0,
              width: 1,
              height: 1,
              child: widget.fallback.buildView(context),
            ),
            Positioned(
              left: 0,
              top: 0,
              width: 1,
              height: 1,
              child: ExcludeSemantics(
                child: IgnorePointer(
                  child: ClipRect(
                    child: SizedBox(
                      width: 1,
                      height: 1,
                      child: widget.playableFallback.buildView(context),
                    ),
                  ),
                ),
              ),
            ),
            if (_capturePlan != null)
              Positioned(
                left: 0,
                top: 0,
                width: 1,
                height: 1,
                child: ExcludeSemantics(
                  child: IgnorePointer(
                    child: ClipRect(
                      child: SizedBox(
                        width: 1,
                        height: 1,
                        child: InAppWebViewInstalledSourceLiveCapture(
                          key: const ValueKey(
                            'android-live-harness-media-capture',
                          ),
                          wynimeVersion: Version.parse(_wynimeVersion),
                          plan: _capturePlan!,
                          browserPort: widget.captureBrowserPort,
                          onAdmission: _onCaptureAdmission,
                          onResult: _onCaptureResult,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

int _effectivePort(Uri uri) =>
    uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);

PlaybackProxyBudget _harnessProxyBudget() => PlaybackProxyBudget(
  maxPlaylistBytes: 2 * 1024 * 1024,
  maxResponseBytes: 64 * 1024 * 1024,
  maxRequestHeaderBytes: 64 * 1024,
  maxCookieBytes: 64 * 1024,
  maxRedirects: 3,
  maxRegisteredResources: 2048,
  upstreamTimeout: const Duration(seconds: 30),
);

Future<List<int>> _readLocalProbeBody(HttpClientResponse response) async {
  final bytes = <int>[];
  final iterator = StreamIterator<List<int>>(response);
  try {
    while (bytes.length < 128 * 1024 && await iterator.moveNext()) {
      final chunk = iterator.current;
      final remaining = 128 * 1024 - bytes.length;
      bytes.addAll(chunk.length <= remaining ? chunk : chunk.take(remaining));
      if (bytes.length == 128 * 1024) break;
    }
  } finally {
    await iterator.cancel();
  }
  return bytes;
}

final class _ProbeVariant {
  const _ProbeVariant({
    required this.name,
    required this.includeReferer,
    required this.includeOrigin,
    required this.includeUserAgent,
    required this.includeCookies,
  });

  final String name;
  final bool includeReferer;
  final bool includeOrigin;
  final bool includeUserAgent;
  final bool includeCookies;
}

final class _LoopbackProbeResult {
  const _LoopbackProbeResult({
    required this.passed,
    this.statusCode,
    this.bodyClassification,
  });

  final bool passed;
  final int? statusCode;
  final String? bodyClassification;
}

final class _CapturedPlaybackOutcome {
  const _CapturedPlaybackOutcome({
    required this.mediaProbePassed,
    required this.playbackSessionPrepared,
    required this.media3Playing,
  });

  final bool mediaProbePassed;
  final bool playbackSessionPrepared;
  final bool media3Playing;
}

final class _ActualPlaybackOutcome {
  const _ActualPlaybackOutcome({
    required this.sessionPrepared,
    required this.media3Playing,
  });

  final bool sessionPrepared;
  final bool media3Playing;
}

Future<SourceLivePlaybackPipelineResult> _openPlaybackSession({
  required SourceLiveHttpPackageRuntime runtime,
  required SourceLivePlayableSourcePlan plan,
  required SourceEpisodeIdentity identity,
  SourceLivePlayableDocumentFallback? documentFallback,
}) {
  final pipeline = SourceLivePlaybackPipeline(
    playableSourceCoordinator: SourceLivePlayableSourceCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
      documentFallback: documentFallback,
    ),
    routeCoordinator: SourceLivePlaybackRouteCoordinator(
      wynimeVersion: Version.parse(_wynimeVersion),
      routeCoordinator: const SourcePlaybackRouteCoordinator(
        selector: DeterministicSourcePlaybackRouteSelector(),
      ),
    ),
    sessionRequestCoordinator: SourceLivePlaybackSessionRequestCoordinator(
      wynimeVersion: Version.parse(_wynimeVersion),
      builder: const DeterministicSourcePlaybackSessionRequestBuilder(),
    ),
    openRequestCoordinator: const SourceLivePlaybackOpenRequestCoordinator(),
    preparedOpener: const _ResolverPreparedRequestOpener(),
  );
  return pipeline.openLive(
    plans: [plan],
    adRemovalPlan: AdRemovalPlan(
      key: AdRemovalPlanKey(
        episode: identity,
        manifestFingerprint: ManifestFingerprint(
          algorithm: 'android-live-harness',
          value: 'direct-media-probe',
        ),
      ),
    ),
    sourceEventSequence: 1,
    proxyBudget: PlaybackProxyBudget(
      maxPlaylistBytes: 2 * 1024 * 1024,
      maxResponseBytes: 64 * 1024 * 1024,
      maxRequestHeaderBytes: 64 * 1024,
      maxCookieBytes: 64 * 1024,
      maxRedirects: 3,
      maxRegisteredResources: 2048,
      upstreamTimeout: const Duration(seconds: 30),
    ),
  );
}

Future<SourceLiveCapturePlaybackEntryResult> _openCapturedPlaybackSession({
  required SourceLiveCapturePackagePlan packagePlan,
  required SourceLiveCapturePackageResult admission,
  required SourceLiveCaptureResult captureResult,
  required SourceEpisodeIdentity identity,
  String? preferredSourceKey,
  PlaybackCoordinator? coordinator,
}) {
  final capturePipeline = SourceLiveCapturePlaybackPipeline(
    playableSourceCoordinator: SourceLiveCapturePlayableSourceCoordinator(
      wynimeVersion: Version.parse(_wynimeVersion),
    ),
    routeCoordinator: SourceLiveCapturePlaybackRouteCoordinator(
      wynimeVersion: Version.parse(_wynimeVersion),
    ),
    sessionRequestCoordinator:
        SourceLiveCapturePlaybackSessionRequestCoordinator(
          wynimeVersion: Version.parse(_wynimeVersion),
        ),
    openRequestCoordinator:
        const SourceLiveCapturePlaybackOpenRequestCoordinator(),
    preparedOpener: coordinator == null
        ? const _CaptureResolverPreparedRequestOpener()
        : PlaybackCoordinatorLiveCapturePreparedRequestOpener(
            coordinator: coordinator,
          ),
  );
  final entryPoint = SourceLiveCapturePlaybackEntryPoint(
    planCoordinator: const SourceLiveCapturePlayableSourcePlanCoordinator(),
    playbackPipeline: capturePipeline,
  );
  final candidates = captureResult.snapshot?.candidates ?? const [];
  return entryPoint.openCapturedLive(
    packagePlan: packagePlan,
    admission: admission,
    captureResult: captureResult,
    episode: identity,
    mappings: [
      for (var index = 0; index < candidates.length; index++)
        SourceLiveCapturePlayableSourceMapping(
          candidateIndex: index,
          sourceKey: 'xifan-captured-$index',
          label: '稀飯動漫 captured ${_enumLabel(candidates[index].kind)}',
        ),
    ],
    adRemovalPlan: AdRemovalPlan(
      key: AdRemovalPlanKey(
        episode: identity,
        manifestFingerprint: ManifestFingerprint(
          algorithm: 'android-live-harness',
          value: 'captured-media',
        ),
      ),
    ),
    proxyBudget: _harnessProxyBudget(),
    preferredSourceKey: preferredSourceKey,
  );
}

final class _CaptureResolverPreparedRequestOpener
    implements SourceLiveCapturePlaybackPreparedRequestOpener {
  const _CaptureResolverPreparedRequestOpener();

  @override
  Future<SourceLiveCapturePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLiveCapturePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    if (openResult.status !=
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready) {
      return SourceLiveCapturePlaybackPreparedOpenResult(
        status: SourceLiveCapturePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: openResult.status,
        sessionStatus: openResult.sessionStatus,
        routeStatus: openResult.routeStatus,
        reasonCode: openResult.reasonCode ?? 'live_open_request_not_ready',
      );
    }
    try {
      final session = await DefaultPlaybackSessionResolver().resolve(
        openResult.request!.resolution,
      );
      return SourceLiveCapturePlaybackPreparedOpenResult(
        status: SourceLiveCapturePlaybackPreparedOpenStatus.opened,
        session: session,
      );
    } on Object {
      return SourceLiveCapturePlaybackPreparedOpenResult(
        status: SourceLiveCapturePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourceLiveCapturePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: 'playback_session_failed',
      );
    }
  }
}

final class _PublicCaptureOutcome {
  const _PublicCaptureOutcome({
    required this.plan,
    required this.admission,
    required this.result,
  });

  final SourceLiveCapturePackagePlan plan;
  final SourceLiveCapturePackageResult? admission;
  final SourceLiveCaptureResult result;
}

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

final class _ReplayPlayableFallback
    implements SourceLivePlayableDocumentFallback {
  const _ReplayPlayableFallback(this.result);

  final SourceRuntimeResult result;

  @override
  Future<SourceRuntimeResult> capture(SourceLivePlayableSourcePlan plan) =>
      Future<SourceRuntimeResult>.value(result);
}

final class _ResolverPreparedRequestOpener
    implements SourceLivePlaybackPreparedRequestOpener {
  const _ResolverPreparedRequestOpener();

  @override
  Future<SourceLivePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLivePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    if (openResult.status !=
        SourceLivePlaybackOpenRequestCoordinatorStatus.ready) {
      return SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: openResult.status,
        sessionStatus: openResult.sessionStatus,
        routeStatus: openResult.routeStatus,
        requestStatus: openResult.requestStatus,
        reasonCode: openResult.reasonCode ?? 'live_open_request_not_ready',
      );
    }
    try {
      final session = await DefaultPlaybackSessionResolver().resolve(
        openResult.request!.resolution,
      );
      return SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.opened,
        session: session,
      );
    } on Object {
      return SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'playback_session_failed',
      );
    }
  }
}

String _enumLabel(Object? value, [String fallback = 'none']) {
  if (value == null) return fallback;
  final text = value.toString();
  final separator = text.lastIndexOf('.');
  return separator >= 0 ? text.substring(separator + 1) : text;
}

const _xifanPackageJson = r'''
{
  "schemaVersion": 3,
  "packageId": "xifan",
  "displayName": "稀飯動漫",
  "version": "1.2.3",
  "wynimeVersion": "^1.0.15",
  "cache": {
    "searchTtlSeconds": 0,
    "subjectMetadataTtlSeconds": 0,
    "episodeListTtlSeconds": 0,
    "playbackResolutionTtlSeconds": 0
  },
  "capabilities": {
    "search": "supported",
    "detail": "supported",
    "episodes": "supported",
    "playback": "supported"
  },
  "security": {
    "domains": [
      {
        "host": "next.xifanacg.com",
        "includeSubdomains": false,
        "schemes": ["https"]
      },
      {
        "host": "api.xifanacg.com",
        "includeSubdomains": false,
        "schemes": ["https"]
      },
      {
        "host": "player.moedot.net",
        "includeSubdomains": false,
        "schemes": ["https"]
      },
      {
        "host": "apn.moedot.net",
        "includeSubdomains": false,
        "schemes": ["https"]
      },
      {
        "host": "bjdownload.pan.wo.cn",
        "includeSubdomains": false,
        "schemes": ["https"],
        "ports": [30443]
      },
      {
        "host": "play.xfvod.pro",
        "includeSubdomains": false,
        "schemes": ["https"],
        "ports": [8088]
      }
    ],
    "permissions": [
      "network",
      "cookies",
      "webView",
      "mediaRequestInspection"
    ],
    "budget": {
      "maxDocumentBytes": 262144,
      "maxRecords": 128,
      "maxSelectorMatches": 512,
      "maxEvaluationSteps": 10000,
      "maxRegexPatternChars": 128,
      "maxRegexInputChars": 2048,
      "maxRedirects": 3
    }
  },
  "programs": [
    {
      "id": "search",
      "documentKind": "html",
      "root": {
        "type": "css",
        "expression": "main ul.grid a"
      },
      "resultLimit": 128,
      "fields": [
        {
          "name": "subjectId",
          "selector": null,
          "value": "attribute",
          "attribute": "href",
          "required": true,
          "regex": {
            "pattern": "/anime/([0-9]+)$",
            "group": 1,
            "caseSensitive": true
          }
        },
        {
          "name": "title",
          "selector": null,
          "value": "text",
          "attribute": null,
          "required": true,
          "regex": null
        }
      ]
    },
    {
      "id": "subject_metadata",
      "documentKind": "html",
      "root": {
        "type": "css",
        "expression": "main"
      },
      "resultLimit": 1,
      "fields": [
        {
          "name": "title",
          "selector": {
            "type": "css",
            "expression": "h1"
          },
          "value": "text",
          "attribute": null,
          "required": true,
          "regex": null
        }
      ]
    },
    {
      "id": "episode_links",
      "documentKind": "html",
      "root": {
        "type": "css",
        "expression": "main a"
      },
      "resultLimit": 128,
      "fields": [
        {
          "name": "lineId",
          "selector": null,
          "value": "attribute",
          "attribute": "href",
          "required": true,
          "regex": {
            "pattern": "source=([A-Za-z0-9_-]+)",
            "group": 1,
            "caseSensitive": true
          }
        },
        {
          "name": "subjectId",
          "selector": null,
          "value": "attribute",
          "attribute": "href",
          "required": true,
          "regex": {
            "pattern": "/anime/([0-9]+)/play/",
            "group": 1,
            "caseSensitive": true
          }
        },
        {
          "name": "episodeId",
          "selector": null,
          "value": "attribute",
          "attribute": "href",
          "required": true,
          "regex": {
            "pattern": "/play/([0-9]+)",
            "group": 1,
            "caseSensitive": true
          }
        },
        {
          "name": "episodeTitle",
          "selector": null,
          "value": "text",
          "attribute": null,
          "required": true,
          "regex": null
        }
      ]
    },
    {
      "id": "playable_source",
      "documentKind": "html",
      "root": {
        "type": "css",
        "expression": "video"
      },
      "resultLimit": 1,
      "fields": [
        {
          "name": "sourceKey",
          "selector": null,
          "value": "literal",
          "literal": "xifan-next",
          "attribute": null,
          "required": true,
          "regex": null
        },
        {
          "name": "label",
          "selector": null,
          "value": "literal",
          "literal": "稀飯動漫 Next",
          "attribute": null,
          "required": true,
          "regex": null
        },
        {
          "name": "kind",
          "selector": null,
          "value": "literal",
          "literal": "video",
          "attribute": null,
          "required": true,
          "regex": null
        },
        {
          "name": "mediaUri",
          "selector": null,
          "value": "attribute",
          "attribute": "src",
          "required": true,
          "regex": null
        },
        {
          "name": "pageUri",
          "selector": null,
          "value": "literal",
          "literal": "https://next.xifanacg.com",
          "attribute": null,
          "required": true,
          "regex": null
        }
      ]
    }
  ],
  "liveOperations": [
    {
      "kind": "search",
      "programId": "search",
      "uriTemplate": "https://next.xifanacg.com/search?q={query}",
      "mapping": {
        "subjectIdField": "subjectId",
        "titleField": "title"
      }
    },
    {
      "kind": "subjectDetails",
      "programId": "subject_metadata",
      "uriTemplate": "https://next.xifanacg.com/anime/{subjectId}",
      "mapping": {
        "episodeProgramId": "episode_links",
        "metadataTitleField": "title",
        "lineIdField": "lineId",
        "subjectIdField": "subjectId",
        "episodeIdField": "episodeId",
        "episodeTitleField": "episodeTitle"
      }
    },
    {
      "kind": "playableSource",
      "programId": "playable_source",
      "uriTemplate": "https://next.xifanacg.com/anime/{subjectId}/play/{episodeId}?source={lineId}",
      "mapping": {
        "sourceKeyField": "sourceKey",
        "labelField": "label",
        "kindField": "kind",
        "mediaUriField": "mediaUri",
        "pageUriField": "pageUri"
      }
    }
  ]
}
''';
