import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';

void main() {
  const boundary = PlaybackErrorBoundary();

  test('raw exception text becomes a bounded secret-safe operation error', () {
    final error = boundary.exceptionFrom(
      StateError(
        'GET https://secret.example/private/master.m3u8?token=private '
        'Cookie: session=secret-value Authorization: Bearer secret-token',
      ),
    );

    expect(error, isA<PlaybackOperationException>());
    expect(error.failure.code, 'manifest_invalid');
    expect(error.toString(), isNot(contains('secret.example')));
    expect(error.toString(), isNot(contains('private')));
    expect(error.toString(), isNot(contains('secret-token')));
  });

  test('raw status and decoder signals retain only stable classification', () {
    final authorization = boundary.exceptionFrom(
      StateError('403 https://secret.example/?token=private'),
    );
    expect(authorization.failure.kind, PlaybackFailureKind.authorization);
    expect(authorization.failure.shouldRefreshSession, isTrue);
    expect(authorization.toString(), isNot(contains('secret.example')));

    final decoder = boundary.exceptionFrom(
      StateError('decoder failed for https://secret.example/video.mp4'),
    );
    expect(decoder.failure.kind, PlaybackFailureKind.decoder);
    expect(decoder.toString(), isNot(contains('secret.example')));
  });

  test('existing typed failure is preserved without adding raw text', () {
    final failure = PlaybackFailure(
      code: 'renderer_failure',
      kind: PlaybackFailureKind.renderer,
      stage: PlaybackErrorStage.player,
      retryable: false,
      shouldRefreshSession: false,
    );

    final error = boundary.exceptionFrom(failure);

    expect(error.failure, same(failure));
    expect(
      error.toString(),
      'PlaybackOperationException('
      'code: renderer_failure, kind: renderer, stage: player)',
    );
  });
}
