import 'package:wynime/src/domain/models/playback_events.dart';

final class PlaybackFailureClassifier {
  const PlaybackFailureClassifier();

  PlaybackFailure classifyMediaKitError(String rawMessage) {
    final normalized = rawMessage.toLowerCase();
    if (_containsAny(normalized, const ['401', 'unauthorized'])) {
      return _failure(
        code: 'http_authorization',
        kind: PlaybackFailureKind.authorization,
        retryable: true,
        refresh: true,
        httpStatus: 401,
      );
    }
    if (_containsAny(normalized, const ['403', 'forbidden'])) {
      return _failure(
        code: 'http_authorization',
        kind: PlaybackFailureKind.authorization,
        retryable: true,
        refresh: true,
        httpStatus: 403,
      );
    }
    if (_containsAny(normalized, const [
      'manifest',
      'playlist',
      'm3u8',
      'demuxer failed to open',
    ])) {
      return _failure(
        code: 'manifest_invalid',
        kind: PlaybackFailureKind.manifest,
      );
    }
    if (_containsAny(normalized, const [
      'network',
      'timeout',
      'timed out',
      'dns',
      'socket',
      'connection',
      'http error',
    ])) {
      return _failure(
        code: 'network_failure',
        kind: PlaybackFailureKind.network,
        retryable: true,
      );
    }
    if (_containsAny(normalized, const [
      'unsupported',
      'not supported',
      'unrecognized file format',
      'failed to recognize file format',
      'codec not found',
    ])) {
      return _failure(
        code: 'unsupported',
        kind: PlaybackFailureKind.unsupported,
      );
    }
    if (_containsAny(normalized, const [
      'decoder',
      'decoding',
      'codec',
      'video format',
      'audio format',
    ])) {
      return _failure(
        code: 'decoder_failure',
        kind: PlaybackFailureKind.decoder,
      );
    }
    if (_containsAny(normalized, const [
      'renderer',
      'rendering',
      'video output',
      'gpu',
      'surface',
      'vo failed',
    ])) {
      return _failure(
        code: 'renderer_failure',
        kind: PlaybackFailureKind.renderer,
      );
    }
    return _failure(
      code: 'unknown_playback_failure',
      kind: PlaybackFailureKind.unknown,
    );
  }

  PlaybackFailure _failure({
    required String code,
    required PlaybackFailureKind kind,
    bool retryable = false,
    bool refresh = false,
    int? httpStatus,
  }) => PlaybackFailure(
    code: code,
    kind: kind,
    stage: PlaybackErrorStage.player,
    retryable: retryable,
    shouldRefreshSession: refresh,
    httpStatus: httpStatus,
  );
}

bool _containsAny(String value, Iterable<String> needles) =>
    needles.any(value.contains);
