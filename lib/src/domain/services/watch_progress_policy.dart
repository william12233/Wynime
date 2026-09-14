import '../models/watch_progress.dart';

/// One authoritative policy for local progress, completion and resume.
final class WatchProgressPolicy {
  const WatchProgressPolicy({
    this.minimumMeaningfulProgress = const Duration(seconds: 5),
    this.completionNumerator = 9,
    this.completionDenominator = 10,
  }) : assert(completionNumerator > 0),
       assert(completionDenominator >= completionNumerator);

  /// A tiny checkpoint is retained only when it belongs to an existing row;
  /// a new row needs this much useful playback to enter Continue Watching.
  final Duration minimumMeaningfulProgress;

  /// Completion is reached at 90% by default. The ratio is integer based so
  /// the boundary is deterministic and does not depend on floating point
  /// rounding.
  final int completionNumerator;
  final int completionDenominator;

  bool isAtCompletionThreshold({
    required Duration position,
    required Duration duration,
  }) {
    final safeDuration = sanitizeDuration(duration);
    if (safeDuration == Duration.zero) return false;
    final safePosition = clampPosition(position, safeDuration);
    final threshold = _scaledCeiling(
      safeDuration,
      completionNumerator,
      completionDenominator,
    );
    return safePosition >= threshold;
  }

  bool isMeaningful({required Duration position, required Duration duration}) {
    final safeDuration = sanitizeDuration(duration);
    final safePosition = clampPosition(position, safeDuration);
    if (safePosition <= Duration.zero) return false;
    if (safeDuration == Duration.zero) {
      return safePosition >= minimumMeaningfulProgress;
    }

    // Very short episodes get a proportionally smaller floor, while ordinary
    // episodes still require a five-second checkpoint.
    final proportionalFloor = _scaledCeiling(safeDuration, 1, 20);
    final floor = proportionalFloor < minimumMeaningfulProgress
        ? proportionalFloor
        : minimumMeaningfulProgress;
    return safePosition >=
        (floor <= Duration.zero ? const Duration(microseconds: 1) : floor);
  }

  Duration sanitizeDuration(Duration duration) =>
      duration.isNegative ? Duration.zero : duration;

  Duration clampPosition(Duration position, Duration duration) {
    final safeDuration = sanitizeDuration(duration);
    if (position.isNegative) return Duration.zero;
    if (safeDuration != Duration.zero && position > safeDuration) {
      return safeDuration;
    }
    return position;
  }

  /// Returns zero for a new/tiny/completed item, otherwise a valid local
  /// position that the player may seek to before starting.
  Duration resumePosition(WatchProgress? progress) {
    if (progress == null) return Duration.zero;
    final duration = sanitizeDuration(progress.duration);
    final position = clampPosition(progress.position, duration);
    if (progress.isCompleted ||
        isAtCompletionThreshold(position: position, duration: duration) ||
        !isMeaningful(position: position, duration: duration)) {
      return Duration.zero;
    }
    return position;
  }

  /// Filters and orders local progress without treating remote watched state
  /// as local playback progress.
  List<WatchProgress> selectContinueWatching(Iterable<WatchProgress> values) {
    final result = values
        .where(
          (value) =>
              !value.isCompleted &&
              !isAtCompletionThreshold(
                position: value.position,
                duration: value.duration,
              ) &&
              isMeaningful(position: value.position, duration: value.duration),
        )
        .toList(growable: true);
    result.sort((left, right) {
      final byTime = right.updatedAt.compareTo(left.updatedAt);
      if (byTime != 0) return byTime;
      return right.progressId.compareTo(left.progressId);
    });
    return List.unmodifiable(result);
  }

  int? progressPercent(WatchProgress progress) {
    final duration = sanitizeDuration(progress.duration);
    if (duration == Duration.zero) return null;
    final position = clampPosition(progress.position, duration);
    return (position.inMicroseconds * 100 ~/ duration.inMicroseconds).clamp(
      0,
      100,
    );
  }

  Duration _scaledCeiling(Duration value, int numerator, int denominator) {
    final micros = value.inMicroseconds;
    final scaled = (micros * numerator + denominator - 1) ~/ denominator;
    return Duration(microseconds: scaled);
  }
}
