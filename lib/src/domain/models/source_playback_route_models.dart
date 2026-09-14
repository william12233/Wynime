import 'package:pub_semver/pub_semver.dart';

import 'source_identity.dart';
import 'source_playable_normalization_models.dart';

enum SourcePlaybackRouteSelectionStatus {
  selected,
  notFound,
  disabled,
  consentRequired,
  incompatible,
  preferredSourceNotFound,
  failed,
}

final class SourcePlaybackRoute {
  SourcePlaybackRoute({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.source,
  }) : packageId = _packageId(packageId),
       programId = _programId(programId) {
    if (source.episode.sourceId != this.packageId) {
      throw ArgumentError(
        'A playback route source must belong to its package identity.',
      );
    }
    if (!_isSupportedKind(source)) {
      throw ArgumentError(
        'A playback route source must use a supported candidate kind.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourcePlayableSource source;

  SourceEpisodeIdentity get episode => source.episode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageIdPresent': packageId.isNotEmpty,
    'programId': programId,
    'kind': source.kind.name,
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

bool _isSupportedKind(SourcePlayableSource source) {
  return switch (source.kind.name) {
    'hls' || 'video' || 'audio' => true,
    _ => false,
  };
}

final class SourcePlaybackRouteSelectionResult {
  SourcePlaybackRouteSelectionResult({
    required this.status,
    this.route,
    this.reasonCode,
  }) {
    if (status == SourcePlaybackRouteSelectionStatus.selected &&
        route == null) {
      throw ArgumentError('A selected route result must contain a route.');
    }
    if (status != SourcePlaybackRouteSelectionStatus.selected &&
        route != null) {
      throw ArgumentError(
        'A non-selected route result cannot contain a route.',
      );
    }
    if (reasonCode != null &&
        (reasonCode!.isEmpty ||
            reasonCode!.length > 64 ||
            !_isSafeToken(reasonCode!))) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
  }

  final SourcePlaybackRouteSelectionStatus status;
  final SourcePlaybackRoute? route;
  final String? reasonCode;
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

bool _isSafeToken(String value) =>
    RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
