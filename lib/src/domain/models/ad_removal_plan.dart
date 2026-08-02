import 'dart:collection';

import 'manifest_fingerprint.dart';
import 'source_identity.dart';

final class AdRemovalPlanKey {
  const AdRemovalPlanKey({
    required this.episode,
    required this.manifestFingerprint,
  });

  final SourceEpisodeIdentity episode;
  final ManifestFingerprint manifestFingerprint;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AdRemovalPlanKey &&
          episode == other.episode &&
          manifestFingerprint == other.manifestFingerprint;

  @override
  int get hashCode => Object.hash(episode, manifestFingerprint);

  @override
  String toString() =>
      '${episode.sourceId}|${episode.lineId}|${episode.subjectId}|'
      '${episode.episodeId}|$manifestFingerprint';
}

enum AdRemovalMode { off, safe, smart, aggressive }

enum AdEvidenceKind {
  explicitCue,
  adDateRange,
  alternateAuthority,
  suspiciousPath,
  shortDiscontinuityRun,
  durationOutlier,
}

final class AdEvidence {
  const AdEvidence({
    required this.kind,
    required this.weight,
    required this.code,
  }) : assert(weight >= 0 && weight <= 100),
       assert(code != '');

  final AdEvidenceKind kind;
  final int weight;
  final String code;
}

final class AdRemovalDecision {
  AdRemovalDecision({
    required this.mediaSequence,
    required this.segmentIndex,
    required this.originalStart,
    required this.duration,
    required this.confidence,
    required List<AdEvidence> evidence,
  }) : evidence = UnmodifiableListView(evidence) {
    if (mediaSequence < 0 || segmentIndex < 0) {
      throw ArgumentError('Segment sequence and index must not be negative.');
    }
    if (originalStart < Duration.zero || duration <= Duration.zero) {
      throw ArgumentError(
        'Removal timeline values must be positive and ordered.',
      );
    }
    if (!confidence.isFinite || confidence < 0 || confidence > 1) {
      throw ArgumentError.value(
        confidence,
        'confidence',
        'Must be between zero and one.',
      );
    }
    if (this.evidence.isEmpty) {
      throw ArgumentError.value(
        evidence,
        'evidence',
        'A removal needs evidence.',
      );
    }
  }

  final int mediaSequence;
  final int segmentIndex;
  final Duration originalStart;
  final Duration duration;
  final double confidence;
  final UnmodifiableListView<AdEvidence> evidence;

  Duration get originalEnd => originalStart + duration;
}

final class TimelineSpan {
  const TimelineSpan({
    required this.originalStart,
    required this.originalEnd,
    required this.sanitizedStart,
    required this.sanitizedEnd,
  });

  final Duration originalStart;
  final Duration originalEnd;
  final Duration sanitizedStart;
  final Duration sanitizedEnd;

  Duration get duration => originalEnd - originalStart;
}

final class AdTimelineMap {
  AdTimelineMap({
    required this.originalDuration,
    required this.sanitizedDuration,
    required List<TimelineSpan> keptSpans,
  }) : keptSpans = UnmodifiableListView(keptSpans) {
    if (originalDuration < Duration.zero || sanitizedDuration < Duration.zero) {
      throw ArgumentError('Timeline durations must not be negative.');
    }
    var previousOriginalEnd = Duration.zero;
    var previousSanitizedEnd = Duration.zero;
    for (final span in this.keptSpans) {
      if (span.originalStart < previousOriginalEnd ||
          span.originalEnd <= span.originalStart ||
          span.sanitizedStart != previousSanitizedEnd ||
          span.sanitizedEnd <= span.sanitizedStart ||
          span.duration != span.sanitizedEnd - span.sanitizedStart) {
        throw ArgumentError.value(
          keptSpans,
          'keptSpans',
          'Timeline spans are not monotonic.',
        );
      }
      previousOriginalEnd = span.originalEnd;
      previousSanitizedEnd = span.sanitizedEnd;
    }
    if (previousOriginalEnd > originalDuration ||
        previousSanitizedEnd != sanitizedDuration) {
      throw ArgumentError.value(
        keptSpans,
        'keptSpans',
        'Timeline totals do not match spans.',
      );
    }
  }

  factory AdTimelineMap.identity(Duration duration) => AdTimelineMap(
    originalDuration: duration,
    sanitizedDuration: duration,
    keptSpans: duration == Duration.zero
        ? const []
        : [
            TimelineSpan(
              originalStart: Duration.zero,
              originalEnd: duration,
              sanitizedStart: Duration.zero,
              sanitizedEnd: duration,
            ),
          ],
  );

  final Duration originalDuration;
  final Duration sanitizedDuration;
  final UnmodifiableListView<TimelineSpan> keptSpans;

  Duration toSanitized(Duration original) {
    _validatePosition(original, originalDuration, 'original');
    if (original == originalDuration) {
      return sanitizedDuration;
    }
    var previousSanitizedEnd = Duration.zero;
    for (final span in keptSpans) {
      if (original < span.originalStart) {
        return previousSanitizedEnd;
      }
      if (original < span.originalEnd) {
        return span.sanitizedStart + (original - span.originalStart);
      }
      previousSanitizedEnd = span.sanitizedEnd;
    }
    return sanitizedDuration;
  }

  Duration toOriginal(Duration sanitized) {
    _validatePosition(sanitized, sanitizedDuration, 'sanitized');
    if (sanitized == sanitizedDuration) {
      return originalDuration;
    }
    for (final span in keptSpans) {
      if (sanitized < span.sanitizedEnd) {
        return span.originalStart + (sanitized - span.sanitizedStart);
      }
    }
    return originalDuration;
  }
}

void _validatePosition(Duration value, Duration limit, String name) {
  if (value < Duration.zero || value > limit) {
    throw RangeError.range(value.inMicroseconds, 0, limit.inMicroseconds, name);
  }
}

final class AdRemovalPlan {
  AdRemovalPlan({
    required this.key,
    this.mode = AdRemovalMode.off,
    List<AdRemovalDecision> removals = const [],
    AdTimelineMap? timeline,
  }) : removals = UnmodifiableListView(removals),
       timeline = timeline ?? AdTimelineMap.identity(Duration.zero) {
    final sequences = <int>{};
    var previousIndex = -1;
    var previousOriginalEnd = Duration.zero;
    var removedDuration = Duration.zero;
    for (final removal in this.removals) {
      if (!sequences.add(removal.mediaSequence) ||
          removal.segmentIndex <= previousIndex) {
        throw ArgumentError.value(
          removals,
          'removals',
          'Removals must be unique and ordered.',
        );
      }
      if (removal.originalStart < previousOriginalEnd ||
          removal.originalEnd > this.timeline.originalDuration) {
        throw ArgumentError.value(
          removals,
          'removals',
          'Removal ranges must be ordered, non-overlapping, and inside the timeline.',
        );
      }
      previousIndex = removal.segmentIndex;
      previousOriginalEnd = removal.originalEnd;
      removedDuration += removal.duration;
    }
    if (mode == AdRemovalMode.off && this.removals.isNotEmpty) {
      throw ArgumentError.value(
        removals,
        'removals',
        'Off mode cannot remove segments.',
      );
    }
    if (this.timeline.originalDuration - this.timeline.sanitizedDuration !=
        removedDuration) {
      throw ArgumentError.value(
        removals,
        'removals',
        'Removal duration must equal the timeline duration delta.',
      );
    }
    _validateOriginalCoverage(this.timeline, this.removals);
  }

  final AdRemovalPlanKey key;
  final AdRemovalMode mode;
  final UnmodifiableListView<AdRemovalDecision> removals;
  final AdTimelineMap timeline;

  bool get isActive => mode != AdRemovalMode.off;
  Set<int> get removedMediaSequences =>
      Set<int>.unmodifiable(removals.map((removal) => removal.mediaSequence));

  void verifyManifestFingerprint(ManifestFingerprint actual) {
    if (actual != key.manifestFingerprint) {
      throw StateError('AdRemovalPlan manifest fingerprint mismatch.');
    }
  }
}

void _validateOriginalCoverage(
  AdTimelineMap timeline,
  List<AdRemovalDecision> removals,
) {
  final intervals = <({Duration start, Duration end})>[
    for (final span in timeline.keptSpans)
      (start: span.originalStart, end: span.originalEnd),
    for (final removal in removals)
      (start: removal.originalStart, end: removal.originalEnd),
  ]..sort((left, right) => left.start.compareTo(right.start));
  var cursor = Duration.zero;
  for (final interval in intervals) {
    if (interval.start != cursor || interval.end <= interval.start) {
      throw ArgumentError.value(
        removals,
        'removals',
        'Kept and removed ranges must cover the original timeline exactly once.',
      );
    }
    cursor = interval.end;
  }
  if (cursor != timeline.originalDuration) {
    throw ArgumentError.value(
      removals,
      'removals',
      'Kept and removed ranges must cover the original timeline exactly once.',
    );
  }
}
