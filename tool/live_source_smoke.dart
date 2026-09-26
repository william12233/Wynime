// ignore_for_file: avoid_print

import 'dart:io';
import 'dart:convert';

import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/infrastructure/playback/default_playback_session_resolver.dart';
import 'package:wynime/src/infrastructure/playback/bounded_media_probe.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';
import 'package:wynime/src/infrastructure/source_http/dart_io_source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_subject_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';

/// Bounded manual live smoke runner. It is intentionally outside normal CI:
/// it makes public HTTPS requests, retains no response body, and never prints
/// a full media URI, header value, cookie or diagnostic response text.
Future<void> main(List<String> arguments) async {
  final options = _parseArguments(arguments);
  final packagePath = options['package'] ?? 'sources/xifan.wynsrc.json';
  final query = options['query'];
  final subjectId = options['subject-id'];
  final lineId = options['line-id'];
  final episodeId = options['episode-id'];
  if ([
    query,
    subjectId,
    lineId,
    episodeId,
  ].any((value) => value == null || value.trim().isEmpty)) {
    stderr.writeln(
      'Usage: dart run tool/live_source_smoke.dart '
      '--query=<public title> --subject-id=<id> --line-id=<id> '
      '--episode-id=<id> [--package=sources/xifan.wynsrc.json] '
      '[--wynime-version=1.0.17]',
    );
    exitCode = 64;
    return;
  }

  final wynimeVersion = Version.parse(options['wynime-version'] ?? '1.0.17');
  final timestamp = DateTime.now().toUtc().toIso8601String();
  final decoder = const SourcePackageDecoder();
  final upstreamClient = DartIoProxyUpstreamClient();
  final transport = DartIoSourceHttpTransport(upstreamClient: upstreamClient);
  final mediaProbe = BoundedMediaProbe(upstreamClient: upstreamClient);
  final proxyBudget = _liveProxyBudget();
  try {
    final package = decoder.decode(File(packagePath).readAsStringSync());
    final manager = DeclarativeSourcePackageManager(
      wynimeVersion: wynimeVersion,
    );
    final pending = manager.install(package);
    final installed = manager.enable(
      packageId: package.packageId,
      version: package.version,
      userApproved: true,
      reconsentGranted: true,
    );
    final runtime = SourceLiveHttpPackageRuntime(
      httpExecutor: SourceLiveHttpRequestExecutor(
        requestCoordinator: SourceLiveHttpRequestCoordinator(
          wynimeVersion: wynimeVersion,
        ),
        transport: transport,
      ),
      fixtureRuntime: DeclarativeSourcePackageRuntime(
        wynimeVersion: wynimeVersion,
      ),
    );
    final planFactory = SourceLiveOperationPlanFactory(
      wynimeVersion: wynimeVersion,
    );

    print('SOURCE: ${package.packageId} (${package.displayName})');
    print('PACKAGE VERSION: ${package.version}');
    print('TIMESTAMP: $timestamp');
    print(
      'INSTALL: PASS (${pending.package.packageId}@${pending.package.version})',
    );

    var searchFallbackRequired = false;

    final searchPlan = planFactory.buildSearchPlan(
      installedPackage: installed,
      query: query!,
    );
    if (searchPlan.plan == null) {
      _stageFailure('SEARCH', searchPlan.reasonCode ?? 'search_plan_failed');
      exitCode = 1;
      return;
    }
    final searchRequestPlan = searchPlan.plan!.requestPlan;
    final searchExecution = await runtime.httpExecutor.execute(
      searchRequestPlan,
    );
    if (searchExecution.status != SourceLiveHttpExecutionStatus.completed) {
      _printHttpEvidence(
        stage: 'SEARCH',
        request: searchRequestPlan.request,
        execution: searchExecution,
      );
      _stageFailure(
        'SEARCH',
        _executionCode(searchExecution, 'search_http_failed'),
      );
      exitCode = 1;
      return;
    }
    final searchResponse = searchExecution.response!;
    final searchRuntime = runtime.fixtureRuntime.executeFixture(
      installedPackage: installed,
      programId: searchRequestPlan.programId,
      fixture: SourceFixture(
        initialUri: searchRequestPlan.request.uri,
        redirectChain: searchResponse.redirectChain,
        body: searchResponse.body,
      ),
    );
    final searchResult = const DeclarativeSourceSearchNormalizer()
        .normalizeSearch(
          runtimeResult: searchRuntime,
          mapping: searchPlan.plan!.mapping,
        );
    _printHttpEvidence(
      stage: 'SEARCH',
      request: searchRequestPlan.request,
      execution: searchExecution,
      runtimeResult: searchRuntime,
      normalizerResult: searchResult,
    );
    final matchingSearchResults = searchResult.results
        .where((item) => item.subjectId == subjectId)
        .toList(growable: false);
    if (matchingSearchResults.length != 1) {
      if (searchRuntime.status == SourceRuntimeStatus.notFound &&
          searchResult.status == SourceSearchNormalizationStatus.notFound) {
        searchFallbackRequired = true;
        print('SEARCH: FALLBACK_REQUIRED (static_not_found)');
        print(
          'SEARCH FALLBACK: bounded_rendered_document_required '
          '(explicit_subject_id_continuation=true)',
        );
      } else {
        _stageFailure(
          'SEARCH',
          _runtimeCode(searchRuntime, 'subject_not_found'),
        );
        exitCode = 1;
        return;
      }
    } else {
      _stagePass(
        'SEARCH',
        'available_result_count=${searchResult.results.length}',
      );
    }

    final sourceSubject = SourceSubjectIdentity(
      sourceId: package.packageId,
      subjectId: subjectId!,
    );
    final subjectPlan = planFactory.buildSubjectDetailsPlan(
      installedPackage: installed,
      subject: sourceSubject,
    );
    if (subjectPlan.plan == null) {
      _stageFailure('SUBJECT', subjectPlan.reasonCode ?? 'subject_plan_failed');
      exitCode = 1;
      return;
    }
    final subjectMapping = subjectPlan.plan!.mapping;
    final subjectRequestPlan = subjectPlan.plan!.requestPlan;
    final subjectExecution = await runtime.httpExecutor.execute(
      subjectRequestPlan,
    );
    SourceEpisodeIdentity identity;
    if (subjectExecution.status != SourceLiveHttpExecutionStatus.completed) {
      _printHttpEvidence(
        stage: 'SUBJECT',
        request: subjectRequestPlan.request,
        execution: subjectExecution,
      );
      final code = _executionCode(subjectExecution, 'subject_http_failed');
      if (code != 'response_too_large') {
        _stageFailure('SUBJECT', code);
        exitCode = 1;
        return;
      }
      print(
        'SUBJECT: BROWSER_CAPTURE_REQUIRED '
        '(static_response_budget_exceeded explicit_episode_continuation=true)',
      );
      identity = SourceEpisodeIdentity(
        sourceId: package.packageId,
        subjectId: subjectId,
        lineId: lineId!,
        episodeId: episodeId!,
      );
    } else {
      final subjectResponse = subjectExecution.response!;
      final subjectFixture = SourceFixture(
        initialUri: subjectRequestPlan.request.uri,
        redirectChain: subjectResponse.redirectChain,
        body: subjectResponse.body,
      );
      final metadataRuntime = runtime.fixtureRuntime.executeFixture(
        installedPackage: installed,
        programId: subjectRequestPlan.programId,
        fixture: subjectFixture,
      );
      final episodeRuntime = runtime.fixtureRuntime.executeFixture(
        installedPackage: installed,
        programId: subjectMapping.episodeProgramId,
        fixture: subjectFixture,
      );
      _printHttpEvidence(
        stage: 'SUBJECT',
        request: subjectRequestPlan.request,
        execution: subjectExecution,
        runtimeResult: metadataRuntime,
      );
      print(
        'SUBJECT EPISODES: status=${episodeRuntime.status.name} '
        'decoder=declarative_fixture '
        'css_root_matches=${episodeRuntime.selectorMatches} '
        'record_count=${episodeRuntime.records.length} '
        'stable_code=${_runtimeCode(episodeRuntime, 'not_run')}',
      );
      final details = const DeclarativeSourceSubjectNormalizer()
          .normalizeSubjectDetails(
            package: package,
            metadataRuntimeResult: metadataRuntime,
            episodeRuntimeResult: episodeRuntime,
            subject: sourceSubject,
            mapping: subjectMapping,
          )
          .details;
      if (details == null) {
        print(
          'SUBJECT: BROWSER_CAPTURE_REQUIRED '
          '(static_subject_unavailable explicit_episode_continuation=true)',
        );
        identity = SourceEpisodeIdentity(
          sourceId: package.packageId,
          subjectId: subjectId,
          lineId: lineId!,
          episodeId: episodeId!,
        );
      } else {
        _stagePass('SUBJECT', 'available');
        final matchingEpisodes = [
          for (final line in details.lines)
            for (final episode in line.episodes)
              if (episode.identity.lineId == lineId &&
                  episode.identity.episodeId == episodeId)
                episode,
        ];
        if (matchingEpisodes.length != 1) {
          _stageFailure('EPISODES', 'episode_identity_not_found');
          exitCode = 1;
          return;
        }
        identity = matchingEpisodes.single.identity;
        _stagePass('EPISODES', 'available');
      }
    }

    final playablePlan = planFactory.buildPlayableSourcePlan(
      installedPackage: installed,
      episode: identity,
    );
    if (playablePlan.plan == null) {
      _stageFailure(
        'STATIC_PLAYABLE',
        playablePlan.reasonCode ?? 'playable_plan_failed',
      );
      exitCode = 1;
      return;
    }
    final playableRuntime = await runtime.execute(
      playablePlan.plan!.requestPlan,
    );
    final playableResult = const DeclarativeSourcePlayableSourceNormalizer()
        .normalizePlayableSources(
          package: package,
          runtimeResult: playableRuntime,
          episode: identity,
          mapping: playablePlan.plan!.mapping,
        );
    if (playableResult.results.isEmpty) {
      _stageFailure(
        'STATIC_PLAYABLE',
        _runtimeCode(playableRuntime, 'playable_extraction_empty'),
      );
      print('BROWSER_CAPTURE_REQUIRED');
      print('STATUS: BROWSER_CAPTURE_REQUIRED');
      exitCode = 2;
      return;
    }
    if (playableResult.results.length != 1) {
      _stageFailure('STATIC_PLAYABLE', 'multiple_playable_candidates');
      exitCode = 1;
      return;
    }
    _stagePass(
      'STATIC_PLAYABLE',
      'available_candidates_${playableResult.results.length}',
    );

    final playbackResult = await _openPlaybackSession(
      runtime: runtime,
      plan: playablePlan.plan!,
      identity: identity,
      wynimeVersion: wynimeVersion,
      proxyBudget: proxyBudget,
    );
    if (playbackResult.status != SourceLivePlaybackPipelineStatus.opened) {
      _stageFailure(
        'PLAYBACK_SESSION',
        playbackResult.reasonCode ?? 'playback_session_failed',
      );
      exitCode = 1;
      return;
    }
    final session = playbackResult.session;
    if (session == null) {
      _stageFailure('MEDIA_PROBE', 'playback_session_missing');
      exitCode = 1;
      return;
    }
    final boundedProbe = await mediaProbe.probe(
      session: session,
      securityPolicy: package.securityPolicy,
      budget: proxyBudget,
      candidateKind: playableResult.results.single.kind,
    );
    if (!boundedProbe.passed) {
      _stageFailure('MEDIA_PROBE', boundedProbe.reasonCode);
      exitCode = 1;
      return;
    }
    final finalShape = boundedProbe.requestShapes.isEmpty
        ? null
        : boundedProbe.requestShapes.last;
    _stagePass(
      'MEDIA_PROBE',
      '${boundedProbe.reasonCode} '
          'final_host=${finalShape?.requestHost ?? 'unknown'} '
          'final_status=${boundedProbe.finalStatusCode ?? 'unknown'}',
    );
    _stagePass('PLAYBACK_SESSION', 'resolver_session_created');
    print(
      'PLAYER_HANDOFF: BLOCKED_PLATFORM_ENVIRONMENT '
      '(cli_has_no_player_surface)',
    );
    if (searchFallbackRequired) {
      print('STATUS: PARTIAL_SEARCH_FALLBACK_REQUIRED');
      exitCode = 2;
    } else {
      print('STATUS: RESOLVED_NOT_RENDERED');
    }
  } on Object catch (error) {
    _stageFailure('RUNNER', _safeErrorCode(error));
    exitCode = 1;
  } finally {
    await transport.close();
    await upstreamClient.close();
  }
}

Future<SourceLivePlaybackPipelineResult> _openPlaybackSession({
  required SourceLiveHttpPackageRuntime runtime,
  required SourceLivePlayableSourcePlan plan,
  required SourceEpisodeIdentity identity,
  required Version wynimeVersion,
  required PlaybackProxyBudget proxyBudget,
}) {
  final pipeline = SourceLivePlaybackPipeline(
    playableSourceCoordinator: SourceLivePlayableSourceCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
    ),
    routeCoordinator: SourceLivePlaybackRouteCoordinator(
      wynimeVersion: wynimeVersion,
      routeCoordinator: const SourcePlaybackRouteCoordinator(
        selector: DeterministicSourcePlaybackRouteSelector(),
      ),
    ),
    sessionRequestCoordinator: SourceLivePlaybackSessionRequestCoordinator(
      wynimeVersion: wynimeVersion,
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
          algorithm: 'live-smoke',
          value: 'direct-media-probe',
        ),
      ),
    ),
    sourceEventSequence: 1,
    proxyBudget: proxyBudget,
  );
}

PlaybackProxyBudget _liveProxyBudget() => PlaybackProxyBudget(
  maxPlaylistBytes: 2 * 1024 * 1024,
  maxResponseBytes: 64 * 1024 * 1024,
  maxRequestHeaderBytes: 64 * 1024,
  maxCookieBytes: 64 * 1024,
  maxRedirects: 3,
  maxRegisteredResources: 2048,
  upstreamTimeout: const Duration(seconds: 30),
);

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

Map<String, String> _parseArguments(List<String> arguments) {
  final values = <String, String>{};
  for (final argument in arguments) {
    if (!argument.startsWith('--')) continue;
    final separator = argument.indexOf('=');
    if (separator <= 2) continue;
    final key = argument.substring(2, separator).trim();
    final value = argument.substring(separator + 1).trim();
    if (key.isNotEmpty && value.isNotEmpty) values[key] = value;
  }
  return values;
}

void _stagePass(String stage, String value) => print('$stage: PASS ($value)');

void _stageFailure(String stage, String code) =>
    print('$stage: FAIL (${_safeToken(code)})');

String _runtimeCode(SourceRuntimeResult? result, String fallback) {
  for (final diagnostic in result?.diagnostics ?? const []) {
    final code = diagnostic.code.trim();
    if (_isSafeToken(code)) return code;
  }
  return fallback;
}

void _printHttpEvidence({
  required String stage,
  required SourceHttpRequest request,
  required SourceLiveHttpExecutionResult execution,
  SourceRuntimeResult? runtimeResult,
  SourceSearchNormalizationResult? normalizerResult,
}) {
  final response = execution.response;
  final transport = execution.transportResult;
  final responseEvidence = transport?.responseEvidence;
  final responseBody = response?.body;
  final finalUri =
      response?.finalUri ?? responseEvidence?.finalUri ?? request.uri;
  final status =
      response?.statusCode ??
      responseEvidence?.statusCode ??
      transport?.httpStatus;
  final contentType = response?.contentType ?? responseEvidence?.contentType;
  final bodyBytes = responseBody == null
      ? responseEvidence?.bodyBytes
      : _utf8Bytes(responseBody);
  final bodyClassification = responseBody == null
      ? responseEvidence?.bodyClassification ?? 'unavailable'
      : _classifyBody(responseBody, status);
  final bodyEncoding =
      responseEvidence?.bodyEncoding ??
      (responseBody == null ? 'unknown' : 'utf8');
  print(
    '$stage REQUEST: scheme=${request.uri.scheme} host=${request.uri.host} '
    'path=${_safePath(request.uri)} '
    'header_names=${request.headers.keys.join(',')}',
  );
  print(
    '$stage HTTP: status=${status ?? 'not_received'} '
    'final_scheme=${finalUri.scheme} final_host=${finalUri.host} '
    'final_path=${_safePath(finalUri)} '
    'redirect_count=${response?.redirectChain.length ?? responseEvidence?.redirectCount ?? 'unknown'} '
    'content_type=${_safeContentType(contentType)} '
    'body_bytes=${bodyBytes ?? 'unknown'} '
    'body_classification=$bodyClassification '
    'body_encoding=$bodyEncoding',
  );
  print(
    '$stage TRANSPORT: stage=${execution.failureStage?.name ?? 'completed'} '
    'status=${transport?.status.name ?? 'success'} '
    'reason=${transport?.reasonCode ?? 'none'}',
  );
  print(
    '$stage RUNTIME: status=${runtimeResult?.status.name ?? 'not_run'} '
    'decoder=${runtimeResult == null ? 'not_run' : 'declarative_fixture'} '
    'css_root_matches=${runtimeResult?.selectorMatches ?? 'not_run'} '
    'record_count=${runtimeResult?.records.length ?? 'not_run'} '
    'required_fields=${_requiredFieldEvidence(runtimeResult)} '
    'stable_code=${_runtimeCode(runtimeResult, 'not_run')}',
  );
  print(
    '$stage NORMALIZER: status=${normalizerResult?.status.name ?? 'not_run'} '
    'result_count=${normalizerResult?.results.length ?? 'not_run'} '
    'diagnostics=${normalizerResult?.diagnostics.length ?? 'not_run'}',
  );
}

String _executionCode(
  SourceLiveHttpExecutionResult execution,
  String fallback,
) {
  final code =
      execution.admissionResult?.reasonCode ??
      execution.transportResult?.reasonCode;
  return code == null ? fallback : _safeToken(code);
}

String _requiredFieldEvidence(SourceRuntimeResult? result) {
  if (result == null) return 'not_run';
  if (result.status != SourceRuntimeStatus.available) return 'not_available';
  return result.records.isEmpty ? 'no_records' : 'extracted_or_recorded';
}

String _safePath(Uri uri) {
  final path = uri.path.isEmpty ? '/' : uri.path;
  return path.length <= 256 ? path : '${path.substring(0, 256)}...';
}

String _safeContentType(String? value) {
  final normalized = value?.trim().toLowerCase();
  if (normalized == null || normalized.isEmpty) return 'none';
  final mediaType = normalized.split(';').first.trim();
  return RegExp(r'^[a-z0-9.+-]+/[a-z0-9.+-]+$').hasMatch(mediaType)
      ? mediaType
      : 'unrecognized';
}

int _utf8Bytes(String value) => utf8.encode(value).length;

String _classifyBody(String? body, int? status) {
  if (body == null || body.isEmpty) return 'empty_or_unavailable';
  final normalized = body.trimLeft().toLowerCase();
  if (normalized.startsWith('{') || normalized.startsWith('[')) {
    return 'json';
  }
  if (normalized.contains('captcha') ||
      normalized.contains('challenge') ||
      normalized.contains('cf-chl')) {
    return 'challenge';
  }
  if (status != null && status >= 400) return 'error_page';
  if (normalized.startsWith('<!doctype html') ||
      normalized.startsWith('<html') ||
      normalized.contains('<html')) {
    return 'html';
  }
  return 'unexpected';
}

String _safeErrorCode(Object error) {
  final text = error.toString();
  final match = RegExp(r'[a-z][a-z0-9_]{1,63}').firstMatch(text);
  return match == null ? 'runner_failed' : match.group(0)!;
}

String _safeToken(String value) =>
    _isSafeToken(value) ? value : 'live_smoke_failed';

bool _isSafeToken(String value) =>
    RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value);
