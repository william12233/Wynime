import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';

final class PlaybackFailureClassifier {
  const PlaybackFailureClassifier();

  static const PlaybackErrorClassifier _classifier = PlaybackErrorClassifier();

  PlaybackFailure classifyMediaKitError(String rawMessage) =>
      _classifier.classifyRawMessage(rawMessage);
}
