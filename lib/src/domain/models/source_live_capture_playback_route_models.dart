import 'package:pub_semver/pub_semver.dart';

import 'source_identity.dart';
import 'source_live_capture_models.dart';
import 'source_live_capture_playable_source_models.dart';

enum SourceLiveCapturePlaybackRouteStatus {
  selected,
  notFound,
  consentRequired,
  disabled,
  incompatible,
  preferredSourceNotFound,
  failed,
}

/// A source-local route that retains the exact live capture candidate.
///
/// Unlike the fixture route contract, this value keeps the
/// [SourceLiveCapturePlayableSource] wrapper and the exact capture request so
/// a later session boundary can still access the captured candidate headers,
/// source-event sequence, cookie snapshot and request policy.
final class SourceLiveCapturePlaybackRoute {
  SourceLiveCapturePlaybackRoute({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.source,
    required this.captureRequest,
    required this.captureResult,
  }) : packageId = _packageId(packageId),
       programId = _programId(programId) {
    if (source.source.episode.sourceId != this.packageId) {
      throw ArgumentError(
        'A live playback route source must belong to its package identity.',
      );
    }
    if (captureResult.status != SourceLiveCaptureStatus.captured ||
        captureResult.snapshot == null ||
        captureResult.packageId != this.packageId ||
        captureResult.packageVersion != packageVersion ||
        captureResult.programId != this.programId) {
      throw ArgumentError(
        'A live playback route must retain its accepted capture identity.',
      );
    }
    if (captureRequest.packageId != this.packageId ||
        captureRequest.packageVersion != packageVersion ||
        captureRequest.programId != this.programId) {
      throw ArgumentError(
        'A live playback route must retain its matching capture request.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceLiveCapturePlayableSource source;
  final SourceLiveCaptureRequest captureRequest;
  final SourceLiveCaptureResult captureResult;

  SourceEpisodeIdentity get episode => source.source.episode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageIdPresent': packageId.isNotEmpty,
    'programId': programId,
    'candidateIndex': source.candidateIndex,
    'kind': source.candidate.kind.name,
    'sourceEventSequence': source.candidate.sourceEventSequence,
    'headerNames': source.candidate.headers.keys.toList(growable: false),
    'captureCookieCount': captureResult.snapshot!.cookies.length,
    'episode': {
      'sourceIdPresent': episode.sourceId.isNotEmpty,
      'lineIdLength': episode.lineId.length,
      'subjectIdLength': episode.subjectId.length,
      'episodeIdLength': episode.episodeId.length,
    },
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

/// The bounded result of selecting one live source-local route.
final class SourceLiveCapturePlaybackRouteResult {
  SourceLiveCapturePlaybackRouteResult({
    required this.status,
    this.route,
    this.reasonCode,
  }) {
    if (status == SourceLiveCapturePlaybackRouteStatus.selected &&
        route == null) {
      throw ArgumentError('A selected live route result must contain a route.');
    }
    if (status != SourceLiveCapturePlaybackRouteStatus.selected &&
        route != null) {
      throw ArgumentError(
        'A non-selected live route result cannot contain a route.',
      );
    }
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    if (status == SourceLiveCapturePlaybackRouteStatus.failed &&
        reasonCode == null) {
      throw ArgumentError('A failed live route result requires a code.');
    }
  }

  final SourceLiveCapturePlaybackRouteStatus status;
  final SourceLiveCapturePlaybackRoute? route;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRoute': route != null,
    'candidateIndex': route?.source.candidateIndex,
    'sourceEventSequence': route?.source.candidate.sourceEventSequence,
    'headerNames': route?.source.candidate.headers.keys.toList(growable: false),
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

String _packageId(String value) {
  final normalized = value.trim();
  if (normalized.length > 128 ||
      !RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'packageId', 'Invalid package identity.');
  }
  return normalized;
}

String _programId(String value) {
  final normalized = value.trim();
  if (normalized.length > 64 ||
      !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'programId', 'Invalid program identity.');
  }
  return normalized;
}

bool _safeToken(String value) =>
    value.isNotEmpty &&
    value.length <= 64 &&
    RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
