import 'dart:math' as math;

import '../../domain/models/ad_removal_plan.dart';
import '../../domain/models/hls_manifest.dart';
import '../../domain/models/manifest_fingerprint.dart';
import '../../domain/models/source_identity.dart';

final class HlsAdPlanningPolicy {
  const HlsAdPlanningPolicy({
    this.smartThreshold = 70,
    this.aggressiveThreshold = 50,
    this.maxShortRun = const Duration(seconds: 45),
    this.minimumContentDurationForHeuristics = const Duration(minutes: 2),
    this.maxHeuristicRemovalRatio = 0.35,
  }) : assert(smartThreshold >= 0 && smartThreshold <= 100),
       assert(aggressiveThreshold >= 0 && aggressiveThreshold <= 100),
       assert(aggressiveThreshold <= smartThreshold),
       assert(maxHeuristicRemovalRatio >= 0 && maxHeuristicRemovalRatio <= 1);

  final int smartThreshold;
  final int aggressiveThreshold;
  final Duration maxShortRun;
  final Duration minimumContentDurationForHeuristics;
  final double maxHeuristicRemovalRatio;
}

final class HlsAdPlanningException implements Exception {
  const HlsAdPlanningException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'HlsAdPlanningException($code): $message';
}

final class HlsAdPlanner {
  const HlsAdPlanner({this.policy = const HlsAdPlanningPolicy()});

  final HlsAdPlanningPolicy policy;

  AdRemovalPlan createPlan({
    required SourceEpisodeIdentity episode,
    required HlsMediaPlaylist playlist,
    required ManifestFingerprint fingerprint,
    required AdRemovalMode mode,
  }) {
    if (policy.maxShortRun <= Duration.zero ||
        policy.minimumContentDurationForHeuristics < Duration.zero) {
      throw const HlsAdPlanningException(
        'invalid_policy',
        'Ad planning durations must be non-negative and bounded.',
      );
    }
    final key = AdRemovalPlanKey(
      episode: episode,
      manifestFingerprint: fingerprint,
    );
    if (mode == AdRemovalMode.off) {
      return AdRemovalPlan(
        key: key,
        mode: mode,
        timeline: AdTimelineMap.identity(playlist.totalDuration),
      );
    }
    if (!playlist.endList || playlist.playlistType == HlsPlaylistType.event) {
      throw const HlsAdPlanningException(
        'live_playlist_unsupported',
        'Phase 5 sanitization only accepts complete VOD playlists.',
      );
    }

    final starts = _segmentStarts(playlist.segments);
    final groups = _groups(playlist.segments);
    final dominantAuthority = _dominantAuthority(playlist.segments);
    final medianDuration = _medianDuration(playlist.segments);
    final removals = <AdRemovalDecision>[];
    var heuristicRemovedMicros = 0;
    final maximumHeuristicMicros =
        (playlist.totalDuration.inMicroseconds *
                policy.maxHeuristicRemovalRatio)
            .floor();

    for (var groupIndex = 0; groupIndex < groups.length; groupIndex += 1) {
      final group = groups[groupIndex];
      final evidence = <AdEvidence>[];
      final explicitCueSegments = group
          .where((segment) => segment.explicitAdCue)
          .toList(growable: false);
      final dateRangeSegments = group
          .where((segment) => segment.adDateRangeCue)
          .toList(growable: false);
      final explicitlySelected = group
          .where((segment) => segment.explicitAdCue || segment.adDateRangeCue)
          .toList(growable: false);
      if (explicitCueSegments.isNotEmpty) {
        evidence.add(
          const AdEvidence(
            kind: AdEvidenceKind.explicitCue,
            weight: 100,
            code: 'explicit_hls_ad_marker',
          ),
        );
      }
      if (dateRangeSegments.isNotEmpty) {
        evidence.add(
          const AdEvidence(
            kind: AdEvidenceKind.adDateRange,
            weight: 100,
            code: 'bounded_ad_daterange',
          ),
        );
      }

      final groupDuration = group.fold(
        Duration.zero,
        (total, segment) => total + segment.duration,
      );
      final authority = _dominantAuthority(group);
      if (authority != dominantAuthority) {
        evidence.add(
          const AdEvidence(
            kind: AdEvidenceKind.alternateAuthority,
            weight: 25,
            code: 'authority_differs_from_dominant_content',
          ),
        );
      }
      if (_hasSuspiciousPath(group)) {
        evidence.add(
          const AdEvidence(
            kind: AdEvidenceKind.suspiciousPath,
            weight: 30,
            code: 'path_contains_advertising_signature',
          ),
        );
      }
      final isInteriorGroup = groupIndex > 0 && groupIndex < groups.length - 1;
      if (playlist.totalDuration >=
              policy.minimumContentDurationForHeuristics &&
          group.length <= 4 &&
          groupDuration <= policy.maxShortRun &&
          isInteriorGroup) {
        evidence.add(
          const AdEvidence(
            kind: AdEvidenceKind.shortDiscontinuityRun,
            weight: 25,
            code: 'short_discontinuity_bounded_run',
          ),
        );
      }
      final averageMicros = groupDuration.inMicroseconds / group.length;
      if (medianDuration > Duration.zero &&
          averageMicros < medianDuration.inMicroseconds * 0.55) {
        evidence.add(
          const AdEvidence(
            kind: AdEvidenceKind.durationOutlier,
            weight: 15,
            code: 'segment_duration_outlier',
          ),
        );
      }

      final score = math.min(
        100,
        evidence.fold<int>(0, (total, item) => total + item.weight),
      );
      final distinctKinds = evidence.map((item) => item.kind).toSet().length;
      final explicit = explicitlySelected.isNotEmpty;
      final heuristicThreshold = mode == AdRemovalMode.smart
          ? policy.smartThreshold
          : mode == AdRemovalMode.aggressive
          ? policy.aggressiveThreshold
          : 101;
      final heuristicCandidate =
          isInteriorGroup && score >= heuristicThreshold && distinctKinds >= 2;
      if (!explicit && !heuristicCandidate) {
        continue;
      }
      if (!explicit &&
          heuristicRemovedMicros + groupDuration.inMicroseconds >
              maximumHeuristicMicros) {
        continue;
      }

      final selected = explicit ? explicitlySelected : group;
      if (!explicit) {
        heuristicRemovedMicros += groupDuration.inMicroseconds;
      }
      for (final segment in selected) {
        removals.add(
          AdRemovalDecision(
            mediaSequence: segment.mediaSequence,
            segmentIndex: segment.index,
            originalStart: starts[segment.index],
            duration: segment.duration,
            confidence: score / 100,
            evidence: evidence,
          ),
        );
      }
    }

    removals.sort(
      (left, right) => left.segmentIndex.compareTo(right.segmentIndex),
    );
    if (removals.length == playlist.segments.length) {
      throw const HlsAdPlanningException(
        'all_segments_selected',
        'Ad evidence would remove the complete media playlist.',
      );
    }
    return AdRemovalPlan(
      key: key,
      mode: mode,
      removals: removals,
      timeline: _buildTimeline(playlist.segments, removals),
    );
  }

  List<Duration> _segmentStarts(List<HlsMediaSegment> segments) {
    final starts = <Duration>[];
    var cursor = Duration.zero;
    for (final segment in segments) {
      starts.add(cursor);
      cursor += segment.duration;
    }
    return starts;
  }

  List<List<HlsMediaSegment>> _groups(List<HlsMediaSegment> segments) {
    final groups = <List<HlsMediaSegment>>[];
    for (final segment in segments) {
      if (groups.isEmpty ||
          groups.last.last.discontinuityGroup != segment.discontinuityGroup) {
        groups.add(<HlsMediaSegment>[]);
      }
      groups.last.add(segment);
    }
    return groups;
  }

  String _dominantAuthority(List<HlsMediaSegment> segments) {
    final durations = <String, int>{};
    for (final segment in segments) {
      final authority = _authority(segment.uri);
      durations.update(
        authority,
        (value) => value + segment.duration.inMicroseconds,
        ifAbsent: () => segment.duration.inMicroseconds,
      );
    }
    final entries = durations.entries.toList()
      ..sort((left, right) {
        final byDuration = right.value.compareTo(left.value);
        return byDuration == 0 ? left.key.compareTo(right.key) : byDuration;
      });
    return entries.first.key;
  }

  String _authority(Uri uri) {
    final defaultPort =
        (uri.scheme == 'https' && (!uri.hasPort || uri.port == 443)) ||
        (uri.scheme == 'http' && (!uri.hasPort || uri.port == 80));
    return '${uri.scheme.toLowerCase()}://${uri.host.toLowerCase()}'
        '${defaultPort ? '' : ':${uri.port}'}';
  }

  bool _hasSuspiciousPath(List<HlsMediaSegment> group) => group.any(
    (segment) => _adPathPattern.hasMatch(segment.uri.path.toLowerCase()),
  );

  Duration _medianDuration(List<HlsMediaSegment> segments) {
    final micros =
        segments.map((segment) => segment.duration.inMicroseconds).toList()
          ..sort();
    final middle = micros.length ~/ 2;
    final value = micros.length.isOdd
        ? micros[middle]
        : ((micros[middle - 1] + micros[middle]) / 2).round();
    return Duration(microseconds: value);
  }

  AdTimelineMap _buildTimeline(
    List<HlsMediaSegment> segments,
    List<AdRemovalDecision> removals,
  ) {
    final removed = removals.map((removal) => removal.mediaSequence).toSet();
    final spans = <TimelineSpan>[];
    var originalCursor = Duration.zero;
    var sanitizedCursor = Duration.zero;
    for (final segment in segments) {
      final originalStart = originalCursor;
      final originalEnd = originalStart + segment.duration;
      if (!removed.contains(segment.mediaSequence)) {
        spans.add(
          TimelineSpan(
            originalStart: originalStart,
            originalEnd: originalEnd,
            sanitizedStart: sanitizedCursor,
            sanitizedEnd: sanitizedCursor + segment.duration,
          ),
        );
        sanitizedCursor += segment.duration;
      }
      originalCursor = originalEnd;
    }
    return AdTimelineMap(
      originalDuration: originalCursor,
      sanitizedDuration: sanitizedCursor,
      keptSpans: spans,
    );
  }
}

final RegExp _adPathPattern = RegExp(
  r'(^|[/_.-])(ad|ads|advert|advertising|commercial|midroll|preroll|promo|sponsor)([/_.-]|$)',
  caseSensitive: false,
);
