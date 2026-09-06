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

  PlaybackFailure classifyRawMessage(
    String rawMessage, {
    PlaybackErrorStage stage = PlaybackErrorStage.player,
  }) {
    final normalized = rawMessage.toLowerCase();
    final status = _httpStatusFrom(normalized);
    final expired = _containsAny(normalized, const [
      'session expired',
      'token expired',
      'authentication expired',
    ]);
    final code = switch (true) {
      _ when expired => 'session_expired',
      _ when status == 401 || status == 403 => 'http_authorization',
      _ when status == 404 || status == 410 => 'media_not_found',
      _ when status != null && status >= 500 => 'upstream_unavailable',
      _ when _containsAny(normalized, const ['cancel', 'abort', 'closed']) =>
        'operation_cancelled',
      _
          when _containsAny(normalized, const [
            'manifest',
            'playlist',
            'm3u8',
            'demuxer failed to open',
          ]) =>
        'manifest_invalid',
      _
          when _containsAny(normalized, const [
            'network',
            'timeout',
            'timed out',
            'dns',
            'socket',
            'connection',
            'http error',
          ]) =>
        'network_failure',
      _
          when _containsAny(normalized, const [
            'unsupported',
            'not supported',
            'unrecognized file format',
            'failed to recognize file format',
            'codec not found',
          ]) =>
        'unsupported',
      _
          when _containsAny(normalized, const [
            'renderer',
            'rendering',
            'video output',
            'gpu',
            'surface',
            'vo failed',
          ]) =>
        'renderer_failure',
      _
          when _containsAny(normalized, const [
            'decoder',
            'decoding',
            'codec',
            'video format',
            'audio format',
          ]) =>
        'decoder_failure',
      _ => 'unknown_playback_failure',
    };
    return classify(
      PlaybackErrorSignal(
        code: code,
        stage: stage,
        httpStatus: status,
        sessionExpired: expired,
      ),
    );
  }

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

final class PlaybackOperationException extends StateError {
  PlaybackOperationException(this.failure) : super(failure.code);

  final PlaybackFailure failure;

  @override
  String toString() =>
      'PlaybackOperationException(code: ${failure.code}, '
      'kind: ${failure.kind.name}, stage: ${failure.stage.name})';
}

final class PlaybackErrorBoundary {
  const PlaybackErrorBoundary({
    this.classifier = const PlaybackErrorClassifier(),
  });

  final PlaybackErrorClassifier classifier;

  PlaybackFailure failureFrom(
    Object error, {
    PlaybackErrorStage stage = PlaybackErrorStage.player,
  }) {
    if (error case final PlaybackOperationException operation) {
      return operation.failure;
    }
    if (error case final PlaybackFailure failure) {
      return failure;
    }
    return classifier.classifyRawMessage(error.toString(), stage: stage);
  }

  PlaybackOperationException exceptionFrom(
    Object error, {
    PlaybackErrorStage stage = PlaybackErrorStage.player,
  }) {
    if (error case final PlaybackOperationException operation) {
      return operation;
    }
    return PlaybackOperationException(failureFrom(error, stage: stage));
  }
}

bool _containsAny(String value, Iterable<String> needles) =>
    needles.any(value.contains);

int? _httpStatusFrom(String value) {
  final match = RegExp(r'\b(401|403|404|410|5\d\d)\b').firstMatch(value);
  return match == null ? null : int.parse(match.group(1)!);
}
