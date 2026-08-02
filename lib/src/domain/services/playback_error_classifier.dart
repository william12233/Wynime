import 'package:wynime/src/domain/models/playback_events.dart';

final class PlaybackErrorSignal {
  PlaybackErrorSignal({
    required String code,
    required this.stage,
    this.httpStatus,
    this.sessionExpired = false,
  }) : code = code.trim().toLowerCase() {
    if (this.code.isEmpty || this.code.length > 96) {
      throw ArgumentError.value(code, 'code', 'Invalid error code.');
    }
  }

  final String code;
  final PlaybackErrorStage stage;
  final int? httpStatus;
  final bool sessionExpired;
}

final class PlaybackErrorClassifier {
  const PlaybackErrorClassifier();

  PlaybackFailure classify(PlaybackErrorSignal signal) {
    if (signal.sessionExpired) {
      return PlaybackFailure(
        code: 'session_expired',
        kind: PlaybackFailureKind.sessionExpired,
        stage: signal.stage,
        retryable: true,
        shouldRefreshSession: true,
        httpStatus: signal.httpStatus,
      );
    }

    final status = signal.httpStatus;
    if (status == 401 || status == 403) {
      return PlaybackFailure(
        code: 'http_authorization',
        kind: PlaybackFailureKind.authorization,
        stage: signal.stage,
        retryable: true,
        shouldRefreshSession: true,
        httpStatus: status,
      );
    }
    if (status != null && status >= 500) {
      return PlaybackFailure(
        code: 'upstream_unavailable',
        kind: PlaybackFailureKind.sourceUnavailable,
        stage: signal.stage,
        retryable: true,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }
    if (status == 404 || status == 410) {
      return PlaybackFailure(
        code: 'media_not_found',
        kind: PlaybackFailureKind.sourceUnavailable,
        stage: signal.stage,
        retryable: false,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }

    final code = signal.code;
    if (_containsAny(code, const ['cancel', 'abort', 'closed'])) {
      return PlaybackFailure(
        code: 'cancelled',
        kind: PlaybackFailureKind.cancelled,
        stage: signal.stage,
        retryable: false,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }
    if (_containsAny(code, const ['manifest', 'playlist', 'm3u8'])) {
      return PlaybackFailure(
        code: 'manifest_invalid',
        kind: PlaybackFailureKind.manifest,
        stage: signal.stage,
        retryable: false,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }
    if (_containsAny(code, const [
      'network',
      'timeout',
      'dns',
      'socket',
      'io',
    ])) {
      return PlaybackFailure(
        code: 'network_failure',
        kind: PlaybackFailureKind.network,
        stage: signal.stage,
        retryable: true,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }
    if (_containsAny(code, const ['decoder', 'codec'])) {
      return PlaybackFailure(
        code: 'decoder_failure',
        kind: PlaybackFailureKind.decoder,
        stage: signal.stage,
        retryable: false,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }
    if (_containsAny(code, const ['renderer', 'surface', 'audio_track'])) {
      return PlaybackFailure(
        code: 'renderer_failure',
        kind: PlaybackFailureKind.renderer,
        stage: signal.stage,
        retryable: false,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }
    if (_containsAny(code, const ['unsupported', 'unimplemented'])) {
      return PlaybackFailure(
        code: 'unsupported',
        kind: PlaybackFailureKind.unsupported,
        stage: signal.stage,
        retryable: false,
        shouldRefreshSession: false,
        httpStatus: status,
      );
    }

    return PlaybackFailure(
      code: 'unknown_playback_failure',
      kind: PlaybackFailureKind.unknown,
      stage: signal.stage,
      retryable: false,
      shouldRefreshSession: false,
      httpStatus: status,
    );
  }
}

bool _containsAny(String value, Iterable<String> needles) =>
    needles.any(value.contains);
