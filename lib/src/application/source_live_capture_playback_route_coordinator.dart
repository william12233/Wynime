import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_live_capture_playable_source_models.dart';
import '../domain/models/source_live_capture_playback_route_models.dart';
import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_package_manifest.dart';
import '../domain/models/source_playback_route_coordinator_models.dart';
import '../domain/models/web_capture_models.dart';
import '../domain/services/web_capture_candidate_classifier.dart';

/// Selects one route from an already accepted live playable-source result.
///
/// This is the live-only route boundary. It preserves the exact captured
/// candidate instead of converting it through the fixture route contract,
/// performs no source I/O, and does not resolve a session or start a player.
final class SourceLiveCapturePlaybackRouteCoordinator {
  const SourceLiveCapturePlaybackRouteCoordinator({
    required this.wynimeVersion,
  });

  final Version wynimeVersion;

  static const _candidateClassifier = WebCaptureCandidateClassifier();

  SourceLiveCapturePlaybackRouteResult selectRoute({
    required InstalledSourcePackage installedPackage,
    required SourceLiveCapturePlayableSourceResult playableResult,
    SourcePlaybackRoutePreference? preference,
  }) {
    final package = installedPackage.package;
    if (installedPackage.requiresConsent ||
        installedPackage.requiresReconsent) {
      return _blocked(
        status: SourceLiveCapturePlaybackRouteStatus.consentRequired,
        reasonCode: 'consent_required',
      );
    }
    if (installedPackage.status != SourcePackageStatus.enabled) {
      return _blocked(
        status: SourceLiveCapturePlaybackRouteStatus.disabled,
        reasonCode: 'package_disabled',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _blocked(
        status: SourceLiveCapturePlaybackRouteStatus.incompatible,
        reasonCode: 'incompatible_wynime_version',
      );
    }
    try {
      package.programById(playableResult.programId);
    } on StateError {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'program_not_found',
      );
    } on Object {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'package_preflight_failed',
      );
    }

    if (playableResult.packageId != package.packageId) {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'live_route_package_mismatch',
      );
    }
    if (playableResult.packageVersion != package.version) {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'live_route_version_mismatch',
      );
    }

    if (playableResult.status !=
        SourceLiveCapturePlayableSourceStatus.available) {
      return _fromUnavailable(playableResult);
    }

    final capture = playableResult.captureResult;
    final snapshot = capture?.snapshot;
    if (capture == null ||
        snapshot == null ||
        capture.status != SourceLiveCaptureStatus.captured ||
        capture.packageId != playableResult.packageId ||
        capture.packageVersion != playableResult.packageVersion ||
        capture.programId != playableResult.programId) {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'live_route_capture_mismatch',
      );
    }
    if (snapshot.stopReason != WebCaptureStopReason.completed) {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'live_route_snapshot_invalid',
      );
    }

    final sourceError = _validateSources(
      package: package,
      playableResult: playableResult,
      snapshot: snapshot,
      webRequest: playableResult.captureRequest!.webCaptureRequest,
    );
    if (sourceError != null) {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: sourceError,
      );
    }
    if (playableResult.sources.isEmpty) {
      return _failed(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        reasonCode: 'live_route_sources_empty',
      );
    }

    if (preference != null && !_matchesPreference(playableResult, preference)) {
      return _preferredNotFound();
    }
    SourceLiveCapturePlayableSource? selected;
    if (preference == null) {
      selected = playableResult.sources.first;
    } else {
      for (final source in playableResult.sources) {
        if (source.source.sourceKey == preference.sourceKey) {
          selected = source;
          break;
        }
      }
    }
    if (selected == null) {
      return _preferredNotFound();
    }

    return SourceLiveCapturePlaybackRouteResult(
      status: SourceLiveCapturePlaybackRouteStatus.selected,
      route: SourceLiveCapturePlaybackRoute(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: playableResult.programId,
        source: selected,
        captureRequest: playableResult.captureRequest!,
        captureResult: capture,
      ),
    );
  }

  static String? _validateSources({
    required SourcePackageManifest package,
    required SourceLiveCapturePlayableSourceResult playableResult,
    required WebCaptureSnapshot snapshot,
    required WebCaptureRequest webRequest,
  }) {
    final eventsBySequence = <int, WebCaptureEvent>{};
    var previousEventSequence = -1;
    for (final event in snapshot.events) {
      if (event.sequence <= previousEventSequence ||
          eventsBySequence.containsKey(event.sequence) ||
          (!package.securityPolicy.allowsUri(event.uri) &&
              !(webRequest.allowRuntimeMediaOrigins &&
                  event.runtimeOriginValidated))) {
        return 'live_route_provenance_invalid';
      }
      previousEventSequence = event.sequence;
      eventsBySequence[event.sequence] = event;
    }

    final redirectChainBySequence = <int, List<Uri>>{};
    final redirectChain = <Uri>[];
    for (final event in snapshot.events) {
      if (event.isRedirect &&
          redirectChain.length < package.securityPolicy.budget.maxRedirects) {
        redirectChain.add(event.uri);
      }
      redirectChainBySequence[event.sequence] = List<Uri>.unmodifiable(
        redirectChain,
      );
    }

    final expectedCandidatesByKey = <String, WebMediaCandidate>{};
    for (final event in snapshot.events) {
      final kind = _candidateClassifier.classify(event);
      if (kind == null) {
        continue;
      }
      final normalizedUri = _candidateClassifier.normalizeCandidateUri(
        event.uri,
      );
      expectedCandidatesByKey.putIfAbsent(
        _candidateKey(kind, normalizedUri),
        () {
          final captured = snapshot.candidates.where(
            (candidate) =>
                candidate.sourceEventSequence == event.sequence &&
                candidate.uri == normalizedUri,
          );
          return WebMediaCandidate(
            kind: kind,
            uri: normalizedUri,
            headers: event.headers,
            sourceEventSequence: event.sequence,
            pageUri: snapshot.hasCompleteRequestMetadata
                ? snapshot.finalUri
                : null,
            requestMethod: event.method,
            isRedirect: event.isRedirect,
            redirectChain: snapshot.hasCompleteRequestMetadata
                ? (redirectChainBySequence[event.sequence] ?? const [])
                : const [],
            runtimeOriginGrant: captured.isEmpty
                ? null
                : captured.first.runtimeOriginGrant,
          );
        },
      );
    }
    final expectedCandidates = expectedCandidatesByKey.values.toList(
      growable: true,
    )..sort(_candidateClassifier.compareCandidates);
    if (snapshot.candidates.length != expectedCandidates.length) {
      return 'live_route_provenance_invalid';
    }

    for (
      var candidateIndex = 0;
      candidateIndex < snapshot.candidates.length;
      candidateIndex++
    ) {
      final candidate = snapshot.candidates[candidateIndex];
      if (!webCaptureAllowsRuntimeUri(
        policy: package.securityPolicy,
        uri: candidate.uri,
        grant: candidate.runtimeOriginGrant,
        acquisitionId: webRequest.acquisitionId,
      )) {
        return 'live_route_provenance_invalid';
      }
      final sourceEvent = eventsBySequence[candidate.sourceEventSequence];
      if (sourceEvent == null ||
          !_candidateMatchesEvent(candidate, sourceEvent)) {
        return 'live_route_provenance_invalid';
      }
      if (!_sameCandidate(candidate, expectedCandidates[candidateIndex])) {
        return 'live_route_provenance_invalid';
      }
      if (snapshot.hasCompleteRequestMetadata &&
          !_sameCandidateRequestMetadata(
            candidate,
            sourceEvent,
            snapshot.finalUri,
            redirectChainBySequence[sourceEvent.sequence] ?? const [],
          )) {
        return 'live_route_provenance_invalid';
      }
    }

    final candidateIndexes = <int>{};
    final sourceKeys = <String>{};
    for (final source in playableResult.sources) {
      final candidateIndex = source.candidateIndex;
      if (!candidateIndexes.add(candidateIndex) ||
          candidateIndex >= snapshot.candidates.length) {
        return 'live_route_provenance_invalid';
      }
      final snapshotCandidate = snapshot.candidates[candidateIndex];
      if (!_sameCandidate(source.candidate, snapshotCandidate) ||
          source.source.episode.sourceId != package.packageId ||
          source.source.pageUri != snapshot.finalUri ||
          !webCaptureAllowsRuntimeUri(
            policy: package.securityPolicy,
            uri: source.source.mediaUri,
            grant: source.candidate.runtimeOriginGrant,
            acquisitionId: webRequest.acquisitionId,
          ) ||
          !package.securityPolicy.allowsUri(source.source.pageUri) ||
          !_isSupportedCandidate(source.candidate.kind)) {
        return 'live_route_provenance_invalid';
      }
      if (!sourceKeys.add(source.source.sourceKey)) {
        return 'live_route_source_key_invalid';
      }
    }
    return null;
  }

  static bool _matchesPreference(
    SourceLiveCapturePlayableSourceResult result,
    SourcePlaybackRoutePreference preference,
  ) {
    return result.packageId == preference.packageId &&
        result.packageVersion == preference.packageVersion &&
        result.programId == preference.programId;
  }

  static bool _isSupportedCandidate(WebCandidateKind kind) {
    return switch (kind) {
      WebCandidateKind.hls ||
      WebCandidateKind.video ||
      WebCandidateKind.audio => true,
      WebCandidateKind.dash || WebCandidateKind.mediaSegment => false,
    };
  }

  static bool _sameCandidate(WebMediaCandidate left, WebMediaCandidate right) =>
      left.kind == right.kind &&
      left.uri.toString() == right.uri.toString() &&
      left.sourceEventSequence == right.sourceEventSequence &&
      _sameHeaders(left.headers, right.headers) &&
      _sameGrant(left.runtimeOriginGrant, right.runtimeOriginGrant);

  static bool _sameCandidateRequestMetadata(
    WebMediaCandidate candidate,
    WebCaptureEvent event,
    Uri pageUri,
    List<Uri> redirectChain,
  ) =>
      candidate.pageUri == pageUri &&
      candidate.requestMethod == event.method &&
      candidate.isRedirect == event.isRedirect &&
      _sameUris(candidate.redirectChain, redirectChain);

  static bool _sameUris(Iterable<Uri> left, Iterable<Uri> right) {
    final leftList = left.toList(growable: false);
    final rightList = right.toList(growable: false);
    if (leftList.length != rightList.length) return false;
    for (var index = 0; index < leftList.length; index++) {
      if (leftList[index] != rightList[index]) return false;
    }
    return true;
  }

  static bool _sameHeaders(
    Map<String, String> left,
    Map<String, String> right,
  ) {
    if (left.length != right.length) {
      return false;
    }
    for (final entry in left.entries) {
      if (right[entry.key] != entry.value) {
        return false;
      }
    }
    return true;
  }

  static bool _candidateMatchesEvent(
    WebMediaCandidate candidate,
    WebCaptureEvent event,
  ) {
    final classifiedKind = _candidateClassifier.classify(event);
    return classifiedKind == candidate.kind &&
        candidate.uri.toString() ==
            _candidateClassifier.normalizeCandidateUri(event.uri).toString() &&
        _sameHeaders(candidate.headers, event.headers) &&
        candidate.sourceEventSequence == event.sequence;
  }

  static bool _sameGrant(
    RuntimeMediaOriginGrant? left,
    RuntimeMediaOriginGrant? right,
  ) =>
      left == null && right == null ||
      left != null &&
          right != null &&
          left.acquisitionId == right.acquisitionId &&
          left.origin == right.origin &&
          left.sourceEventSequence == right.sourceEventSequence &&
          left.expiresAt == right.expiresAt;

  static String _candidateKey(WebCandidateKind kind, Uri uri) =>
      '${kind.name}:${_candidateClassifier.normalizeCandidateUri(uri)}';

  static SourceLiveCapturePlaybackRouteResult _fromUnavailable(
    SourceLiveCapturePlayableSourceResult result,
  ) {
    final status = switch (result.status) {
      SourceLiveCapturePlayableSourceStatus.notFound =>
        SourceLiveCapturePlaybackRouteStatus.notFound,
      SourceLiveCapturePlayableSourceStatus.consentRequired =>
        SourceLiveCapturePlaybackRouteStatus.consentRequired,
      SourceLiveCapturePlayableSourceStatus.disabled =>
        SourceLiveCapturePlaybackRouteStatus.disabled,
      SourceLiveCapturePlayableSourceStatus.incompatible =>
        SourceLiveCapturePlaybackRouteStatus.incompatible,
      SourceLiveCapturePlayableSourceStatus.failed =>
        SourceLiveCapturePlaybackRouteStatus.failed,
      SourceLiveCapturePlayableSourceStatus.available =>
        SourceLiveCapturePlaybackRouteStatus.failed,
    };
    final reasonCode =
        result.reasonCode ??
        switch (status) {
          SourceLiveCapturePlaybackRouteStatus.notFound =>
            'live_playable_not_found',
          SourceLiveCapturePlaybackRouteStatus.consentRequired =>
            'consent_required',
          SourceLiveCapturePlaybackRouteStatus.disabled => 'package_disabled',
          SourceLiveCapturePlaybackRouteStatus.incompatible =>
            'incompatible_wynime_version',
          SourceLiveCapturePlaybackRouteStatus.failed => 'live_playable_failed',
          SourceLiveCapturePlaybackRouteStatus.selected => 'live_route_failed',
          SourceLiveCapturePlaybackRouteStatus.preferredSourceNotFound =>
            'preferred_source_not_found',
        };
    return SourceLiveCapturePlaybackRouteResult(
      status: status,
      reasonCode: reasonCode,
    );
  }

  static SourceLiveCapturePlaybackRouteResult _preferredNotFound() =>
      SourceLiveCapturePlaybackRouteResult(
        status: SourceLiveCapturePlaybackRouteStatus.preferredSourceNotFound,
        reasonCode: 'preferred_source_not_found',
      );

  static SourceLiveCapturePlaybackRouteResult _blocked({
    required SourceLiveCapturePlaybackRouteStatus status,
    required String reasonCode,
  }) => SourceLiveCapturePlaybackRouteResult(
    status: status,
    reasonCode: reasonCode,
  );

  static SourceLiveCapturePlaybackRouteResult _failed({
    required String packageId,
    required Version packageVersion,
    required String programId,
    required String reasonCode,
  }) => SourceLiveCapturePlaybackRouteResult(
    status: SourceLiveCapturePlaybackRouteStatus.failed,
    reasonCode: reasonCode,
  );
}
