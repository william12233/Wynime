import 'dart:collection';

import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

enum AdRemovalMode { off, safe, smart, aggressive }

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
}

final class MediaRange {
  MediaRange({required this.start, required this.end})
    : assert(!start.isNegative, 'start must not be negative.'),
      assert(end > start, 'end must be after start.');

  final Duration start;
  final Duration end;
}

final class TimelineMapEntry {
  TimelineMapEntry({
    required this.sourceStart,
    required this.sourceEnd,
    required this.outputStart,
  }) : assert(!sourceStart.isNegative, 'sourceStart must not be negative.'),
       assert(sourceEnd > sourceStart, 'sourceEnd must be after sourceStart.'),
       assert(!outputStart.isNegative, 'outputStart must not be negative.');

  final Duration sourceStart;
  final Duration sourceEnd;
  final Duration outputStart;
}

final class AdRemovalPlan {
  AdRemovalPlan({
    required this.key,
    required this.mode,
    Iterable<MediaRange> removedRanges = const [],
    Iterable<TimelineMapEntry> timeline = const [],
  }) : removedRanges = UnmodifiableListView(removedRanges),
       timeline = UnmodifiableListView(timeline);

  final AdRemovalPlanKey key;
  final AdRemovalMode mode;
  final UnmodifiableListView<MediaRange> removedRanges;
  final UnmodifiableListView<TimelineMapEntry> timeline;
}
