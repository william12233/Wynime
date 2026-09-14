import 'package:wynime/src/domain/services/playback_session_resolver.dart';

enum SourcePlaybackSessionRequestBuildStatus {
  ready,
  packageMismatch,
  versionMismatch,
  programNotFound,
  adRemovalPlanMismatch,
  mediaUriNotAllowed,
  pageUriNotAllowed,
  invalidSourceEventSequence,
  unsupportedCandidate,
}

final class SourcePlaybackSessionRequestBuildResult {
  SourcePlaybackSessionRequestBuildResult({
    required this.status,
    this.request,
    this.reasonCode,
  }) {
    if (status == SourcePlaybackSessionRequestBuildStatus.ready &&
        request == null) {
      throw ArgumentError('A ready result must contain a session request.');
    }
    if (status != SourcePlaybackSessionRequestBuildStatus.ready &&
        request != null) {
      throw ArgumentError(
        'A non-ready result cannot contain a session request.',
      );
    }
    if (status == SourcePlaybackSessionRequestBuildStatus.ready &&
        reasonCode != null) {
      throw ArgumentError('A ready result cannot contain a failure code.');
    }
    if (status != SourcePlaybackSessionRequestBuildStatus.ready &&
        (reasonCode == null || !_isSafeToken(reasonCode!))) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'A non-ready result requires a bounded diagnostic token.',
      );
    }
  }

  final SourcePlaybackSessionRequestBuildStatus status;
  final PlaybackSessionResolutionRequest? request;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

bool _isSafeToken(String value) =>
    value.isNotEmpty &&
    value.length <= 64 &&
    RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
