import 'dart:math' as math;

import '../../domain/models/ad_removal_plan.dart';
import '../../domain/models/hls_manifest.dart';
import '../../domain/models/manifest_fingerprint.dart';

final class HlsSanitizationException implements Exception {
  const HlsSanitizationException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'HlsSanitizationException($code): $message';
}

final class HlsManifestSanitizer {
  const HlsManifestSanitizer();

  String sanitize({
    required HlsMediaPlaylist playlist,
    required AdRemovalPlan plan,
    required ManifestFingerprint actualFingerprint,
  }) {
    if (!plan.isActive) {
      throw const HlsSanitizationException(
        'inactive_plan',
        'An inactive plan must not be sent through the sanitizer.',
      );
    }
    if (!playlist.endList || playlist.playlistType == HlsPlaylistType.event) {
      throw const HlsSanitizationException(
        'live_playlist_unsupported',
        'Phase 5 sanitization only accepts complete VOD playlists.',
      );
    }
    try {
      plan.verifyManifestFingerprint(actualFingerprint);
    } on StateError {
      throw const HlsSanitizationException(
        'fingerprint_mismatch',
        'AdRemovalPlan does not match the current manifest.',
      );
    }

    final starts = <Duration>[];
    var originalCursor = Duration.zero;
    for (final segment in playlist.segments) {
      starts.add(originalCursor);
      originalCursor += segment.duration;
    }
    for (final removal in plan.removals) {
      if (removal.segmentIndex >= playlist.segments.length) {
        throw const HlsSanitizationException(
          'unknown_segment',
          'AdRemovalPlan references a segment absent from this manifest.',
        );
      }
      final segment = playlist.segments[removal.segmentIndex];
      if (segment.mediaSequence != removal.mediaSequence ||
          segment.duration != removal.duration ||
          starts[removal.segmentIndex] != removal.originalStart) {
        throw const HlsSanitizationException(
          'removal_identity_mismatch',
          'AdRemovalPlan removal identity does not match the current manifest.',
        );
      }
    }
    final kept = playlist.segments
        .where(
          (segment) =>
              !plan.removedMediaSequences.contains(segment.mediaSequence),
        )
        .toList(growable: false);
    if (kept.isEmpty) {
      throw const HlsSanitizationException(
        'empty_playlist',
        'Sanitization would remove every media segment.',
      );
    }
    final keptDuration = kept.fold(
      Duration.zero,
      (total, segment) => total + segment.duration,
    );
    if (keptDuration != plan.timeline.sanitizedDuration ||
        playlist.totalDuration != plan.timeline.originalDuration ||
        !_timelineMatches(playlist, plan)) {
      throw const HlsSanitizationException(
        'timeline_mismatch',
        'AdRemovalPlan timeline does not match the current playlist.',
      );
    }

    final output = StringBuffer('#EXTM3U\n');
    if (playlist.version != null) {
      output.writeln('#EXT-X-VERSION:${playlist.version}');
    }
    if (playlist.independentSegments) {
      output.writeln('#EXT-X-INDEPENDENT-SEGMENTS');
    }
    final targetSeconds = kept
        .map(
          (segment) =>
              (segment.duration.inMicroseconds / Duration.microsecondsPerSecond)
                  .ceil(),
        )
        .fold<int>(1, math.max);
    output.writeln('#EXT-X-TARGETDURATION:$targetSeconds');
    output.writeln('#EXT-X-MEDIA-SEQUENCE:${kept.first.mediaSequence}');
    output.writeln(
      '#EXT-X-DISCONTINUITY-SEQUENCE:'
      '${playlist.discontinuitySequence + kept.first.discontinuityGroup}',
    );
    output.writeln('#EXT-X-PLAYLIST-TYPE:VOD');

    for (final range in playlist.dateRanges) {
      if (!range.hasExplicitAdSignal) {
        output.writeln(
          '#EXT-X-DATERANGE:${_renderAttributes(range.attributes)}',
        );
      }
    }

    HlsKey? emittedKey;
    HlsInitializationMap? emittedMap;
    HlsMediaSegment? previous;
    for (final segment in kept) {
      if (previous != null &&
          previous.discontinuityGroup != segment.discontinuityGroup) {
        output.writeln('#EXT-X-DISCONTINUITY');
      }
      if (segment.key != emittedKey) {
        if (segment.key != null) {
          output.writeln(_renderKey(segment.key!));
        } else if (emittedKey != null && !emittedKey.isNone) {
          output.writeln('#EXT-X-KEY:METHOD=NONE');
        }
        emittedKey = segment.key;
      }
      if (segment.initializationMap != emittedMap) {
        if (segment.initializationMap != null) {
          output.writeln(_renderMap(segment.initializationMap!));
        }
        emittedMap = segment.initializationMap;
      }
      if (segment.programDateTime != null) {
        output.writeln(
          '#EXT-X-PROGRAM-DATE-TIME:${segment.programDateTime!.toUtc().toIso8601String()}',
        );
      }
      if (segment.gap) {
        output.writeln('#EXT-X-GAP');
      }
      if (segment.byteRange != null) {
        output.writeln(
          '#EXT-X-BYTERANGE:${_renderByteRange(segment.byteRange!)}',
        );
      }
      output.writeln(
        '#EXTINF:${_renderDuration(segment.duration)},${segment.title}',
      );
      output.writeln(segment.uri);
      previous = segment;
    }
    output.writeln('#EXT-X-ENDLIST');
    return output.toString();
  }

  bool _timelineMatches(HlsMediaPlaylist playlist, AdRemovalPlan plan) {
    final removed = plan.removedMediaSequences;
    final expected = <TimelineSpan>[];
    var originalCursor = Duration.zero;
    var sanitizedCursor = Duration.zero;
    for (final segment in playlist.segments) {
      final originalEnd = originalCursor + segment.duration;
      if (!removed.contains(segment.mediaSequence)) {
        expected.add(
          TimelineSpan(
            originalStart: originalCursor,
            originalEnd: originalEnd,
            sanitizedStart: sanitizedCursor,
            sanitizedEnd: sanitizedCursor + segment.duration,
          ),
        );
        sanitizedCursor += segment.duration;
      }
      originalCursor = originalEnd;
    }
    if (expected.length != plan.timeline.keptSpans.length) {
      return false;
    }
    for (var index = 0; index < expected.length; index += 1) {
      final left = expected[index];
      final right = plan.timeline.keptSpans[index];
      if (left.originalStart != right.originalStart ||
          left.originalEnd != right.originalEnd ||
          left.sanitizedStart != right.sanitizedStart ||
          left.sanitizedEnd != right.sanitizedEnd) {
        return false;
      }
    }
    return true;
  }

  String _renderKey(HlsKey key) {
    final attributes = <String, String>{'METHOD': key.method};
    if (key.uri != null) {
      attributes['URI'] = key.uri.toString();
    }
    if (key.iv != null) {
      attributes['IV'] = key.iv!;
    }
    if (key.keyFormat != null) {
      attributes['KEYFORMAT'] = key.keyFormat!;
    }
    if (key.keyFormatVersions != null) {
      attributes['KEYFORMATVERSIONS'] = key.keyFormatVersions!;
    }
    return '#EXT-X-KEY:${_renderAttributes(attributes)}';
  }

  String _renderMap(HlsInitializationMap map) {
    final attributes = <String, String>{'URI': map.uri.toString()};
    if (map.byteRange != null) {
      attributes['BYTERANGE'] = _renderByteRange(map.byteRange!);
    }
    return '#EXT-X-MAP:${_renderAttributes(attributes)}';
  }

  String _renderAttributes(Map<String, String> attributes) => attributes.entries
      .map(
        (entry) => '${entry.key}=${_renderAttribute(entry.key, entry.value)}',
      )
      .join(',');

  String _renderAttribute(String name, String value) {
    if (_unquotedAttributeNames.contains(name) &&
        _safeUnquoted.hasMatch(value)) {
      return value;
    }
    return '"$value"';
  }

  String _renderByteRange(HlsByteRange range) => range.offset == null
      ? '${range.length}'
      : '${range.length}@${range.offset}';

  String _renderDuration(Duration duration) {
    final seconds = duration.inMicroseconds / Duration.microsecondsPerSecond;
    final raw = seconds.toStringAsFixed(6);
    return raw
        .replaceFirst(RegExp(r'0+$'), '')
        .replaceFirst(RegExp(r'\.$'), '');
  }
}

const Set<String> _unquotedAttributeNames = {
  'METHOD',
  'IV',
  'DURATION',
  'PLANNED-DURATION',
  'END-ON-NEXT',
};
final RegExp _safeUnquoted = RegExp(r'^[A-Za-z0-9.+\-/]+$');
