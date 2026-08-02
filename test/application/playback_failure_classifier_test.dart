import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/playback/playback_failure_classifier.dart';
import 'package:wynime/src/domain/models/playback_events.dart';

void main() {
  const classifier = PlaybackFailureClassifier();

  test('classifies only fallback-safe decoder and renderer failures', () {
    expect(
      classifier.classifyMediaKitError('video decoder failed').kind,
      PlaybackFailureKind.decoder,
    );
    expect(
      classifier.classifyMediaKitError('GPU video output failed').kind,
      PlaybackFailureKind.renderer,
    );
    expect(
      classifier.classifyMediaKitError('codec not found').kind,
      PlaybackFailureKind.unsupported,
    );
  });

  test('authorization, manifest, and network remain non-fallback kinds', () {
    final authorization = classifier.classifyMediaKitError(
      '403 https://secret.example/private.m3u8?token=do-not-leak',
    );
    expect(authorization.kind, PlaybackFailureKind.authorization);
    expect(authorization.httpStatus, 403);
    expect(authorization.shouldRefreshSession, isTrue);
    expect(authorization.toString(), isNot(contains('secret.example')));
    expect(authorization.toString(), isNot(contains('do-not-leak')));

    expect(
      classifier.classifyMediaKitError('playlist parse failed').kind,
      PlaybackFailureKind.manifest,
    );
    expect(
      classifier.classifyMediaKitError('socket timeout').kind,
      PlaybackFailureKind.network,
    );
  });

  test('unknown raw errors collapse to one stable diagnostic code', () {
    final failure = classifier.classifyMediaKitError(
      'password=hunter2 and cookie=session-secret',
    );
    expect(failure.code, 'unknown_playback_failure');
    expect(failure.kind, PlaybackFailureKind.unknown);
    expect(failure.toString(), isNot(contains('hunter2')));
    expect(failure.toString(), isNot(contains('session-secret')));
  });
}
