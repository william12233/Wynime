import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';

void main() {
  const classifier = PlaybackErrorClassifier();

  test('401 and 403 are session refresh failures, not decoder failures', () {
    for (final status in [401, 403]) {
      final failure = classifier.classify(
        PlaybackErrorSignal(
          code: 'error_code_decoding_failed',
          stage: PlaybackErrorStage.player,
          httpStatus: status,
        ),
      );
      expect(failure.kind, PlaybackFailureKind.authorization);
      expect(failure.shouldRefreshSession, isTrue);
      expect(failure.retryable, isTrue);
    }
  });

  test('explicit expiry is authoritative even without an HTTP status', () {
    final failure = classifier.classify(
      PlaybackErrorSignal(
        code: 'expired',
        stage: PlaybackErrorStage.proxy,
        sessionExpired: true,
      ),
    );

    expect(failure.kind, PlaybackFailureKind.sessionExpired);
    expect(failure.shouldRefreshSession, isTrue);
  });

  test(
    'network, manifest, decoder, renderer, and cancellation remain distinct',
    () {
      final cases = <String, PlaybackFailureKind>{
        'socket_timeout': PlaybackFailureKind.network,
        'invalid_m3u8_playlist': PlaybackFailureKind.manifest,
        'decoder_init_failed': PlaybackFailureKind.decoder,
        'renderer_surface_lost': PlaybackFailureKind.renderer,
        'operation_cancelled': PlaybackFailureKind.cancelled,
      };

      for (final entry in cases.entries) {
        final failure = classifier.classify(
          PlaybackErrorSignal(
            code: entry.key,
            stage: PlaybackErrorStage.player,
          ),
        );
        expect(failure.kind, entry.value, reason: entry.key);
      }
    },
  );
}
