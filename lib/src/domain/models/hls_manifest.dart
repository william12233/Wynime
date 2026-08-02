import 'dart:collection';

sealed class HlsPlaylist {
  HlsPlaylist({required this.sourceUri, required this.version});

  final Uri sourceUri;
  final int? version;
}

final class HlsMasterPlaylist extends HlsPlaylist {
  HlsMasterPlaylist({
    required super.sourceUri,
    required super.version,
    required List<HlsVariantStream> variants,
    required List<HlsMediaRendition> renditions,
    required this.independentSegments,
  }) : variants = UnmodifiableListView(variants),
       renditions = UnmodifiableListView(renditions) {
    if (this.variants.isEmpty) {
      throw ArgumentError.value(
        variants,
        'variants',
        'A master playlist needs at least one variant.',
      );
    }
  }

  final UnmodifiableListView<HlsVariantStream> variants;
  final UnmodifiableListView<HlsMediaRendition> renditions;
  final bool independentSegments;
}

final class HlsVariantStream {
  HlsVariantStream({
    required this.uri,
    required this.bandwidth,
    this.averageBandwidth,
    this.codecs,
    this.resolution,
    this.frameRate,
    this.audioGroupId,
    this.subtitleGroupId,
  }) {
    if (bandwidth <= 0) {
      throw ArgumentError.value(bandwidth, 'bandwidth', 'Must be positive.');
    }
    if (averageBandwidth != null && averageBandwidth! <= 0) {
      throw ArgumentError.value(
        averageBandwidth,
        'averageBandwidth',
        'Must be positive.',
      );
    }
    if (frameRate != null && (!frameRate!.isFinite || frameRate! <= 0)) {
      throw ArgumentError.value(
        frameRate,
        'frameRate',
        'Must be finite and positive.',
      );
    }
  }

  final Uri uri;
  final int bandwidth;
  final int? averageBandwidth;
  final String? codecs;
  final HlsResolution? resolution;
  final double? frameRate;
  final String? audioGroupId;
  final String? subtitleGroupId;
}

final class HlsResolution {
  const HlsResolution(this.width, this.height)
    : assert(width > 0),
      assert(height > 0);

  final int width;
  final int height;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is HlsResolution && width == other.width && height == other.height;

  @override
  int get hashCode => Object.hash(width, height);
}

final class HlsMediaRendition {
  HlsMediaRendition({
    required this.type,
    required this.groupId,
    required this.name,
    required this.defaultSelection,
    required this.autoSelect,
    required this.forced,
    this.uri,
    this.language,
    this.characteristics,
  }) {
    if (type.isEmpty || groupId.isEmpty || name.isEmpty) {
      throw ArgumentError('Rendition type, group, and name must not be empty.');
    }
  }

  final String type;
  final String groupId;
  final String name;
  final bool defaultSelection;
  final bool autoSelect;
  final bool forced;
  final Uri? uri;
  final String? language;
  final String? characteristics;
}

enum HlsPlaylistType { event, vod }

final class HlsMediaPlaylist extends HlsPlaylist {
  HlsMediaPlaylist({
    required super.sourceUri,
    required super.version,
    required this.targetDuration,
    required this.mediaSequence,
    required this.discontinuitySequence,
    required this.playlistType,
    required this.endList,
    required this.independentSegments,
    required List<HlsMediaSegment> segments,
    required List<HlsDateRange> dateRanges,
  }) : segments = UnmodifiableListView(segments),
       dateRanges = UnmodifiableListView(dateRanges) {
    if (targetDuration <= Duration.zero) {
      throw ArgumentError.value(
        targetDuration,
        'targetDuration',
        'Must be positive.',
      );
    }
    if (mediaSequence < 0 || discontinuitySequence < 0) {
      throw ArgumentError('Playlist sequence numbers must not be negative.');
    }
    if (this.segments.isEmpty) {
      throw ArgumentError.value(
        segments,
        'segments',
        'A media playlist needs at least one segment.',
      );
    }
    var expectedSequence = mediaSequence;
    for (final segment in this.segments) {
      if (segment.mediaSequence != expectedSequence) {
        throw ArgumentError.value(
          segment.mediaSequence,
          'segments',
          'Segment media sequences must be contiguous from mediaSequence.',
        );
      }
      expectedSequence += 1;
    }
  }

  final Duration targetDuration;
  final int mediaSequence;
  final int discontinuitySequence;
  final HlsPlaylistType? playlistType;
  final bool endList;
  final bool independentSegments;
  final UnmodifiableListView<HlsMediaSegment> segments;
  final UnmodifiableListView<HlsDateRange> dateRanges;

  Duration get totalDuration => segments.fold(
    Duration.zero,
    (total, segment) => total + segment.duration,
  );
}

final class HlsMediaSegment {
  HlsMediaSegment({
    required this.mediaSequence,
    required this.index,
    required this.uri,
    required this.duration,
    required this.title,
    required this.discontinuityBefore,
    required this.discontinuityGroup,
    required this.explicitAdCue,
    required this.adDateRangeCue,
    required this.gap,
    this.byteRange,
    this.key,
    this.initializationMap,
    this.programDateTime,
  }) {
    if (mediaSequence < 0 || index < 0 || discontinuityGroup < 0) {
      throw ArgumentError(
        'Segment indexes, sequences, and discontinuity group must not be negative.',
      );
    }
    if (duration <= Duration.zero) {
      throw ArgumentError.value(duration, 'duration', 'Must be positive.');
    }
  }

  final int mediaSequence;
  final int index;
  final Uri uri;
  final Duration duration;
  final String title;
  final bool discontinuityBefore;
  final int discontinuityGroup;
  final bool explicitAdCue;
  final bool adDateRangeCue;
  final bool gap;
  final HlsByteRange? byteRange;
  final HlsKey? key;
  final HlsInitializationMap? initializationMap;
  final DateTime? programDateTime;
}

final class HlsByteRange {
  const HlsByteRange({required this.length, this.offset})
    : assert(length > 0),
      assert(offset == null || offset >= 0);

  final int length;
  final int? offset;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is HlsByteRange && length == other.length && offset == other.offset;

  @override
  int get hashCode => Object.hash(length, offset);
}

final class HlsKey {
  HlsKey({
    required this.method,
    required this.uri,
    this.iv,
    this.keyFormat,
    this.keyFormatVersions,
  }) {
    if (method.isEmpty) {
      throw ArgumentError.value(method, 'method', 'Must not be empty.');
    }
    if (method == 'NONE' && uri != null) {
      throw ArgumentError.value(uri, 'uri', 'METHOD=NONE cannot have a URI.');
    }
  }

  final String method;
  final Uri? uri;
  final String? iv;
  final String? keyFormat;
  final String? keyFormatVersions;

  bool get isNone => method == 'NONE';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is HlsKey &&
          method == other.method &&
          uri == other.uri &&
          iv == other.iv &&
          keyFormat == other.keyFormat &&
          keyFormatVersions == other.keyFormatVersions;

  @override
  int get hashCode =>
      Object.hash(method, uri, iv, keyFormat, keyFormatVersions);
}

final class HlsInitializationMap {
  const HlsInitializationMap({required this.uri, this.byteRange});

  final Uri uri;
  final HlsByteRange? byteRange;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is HlsInitializationMap &&
          uri == other.uri &&
          byteRange == other.byteRange;

  @override
  int get hashCode => Object.hash(uri, byteRange);
}

final class HlsDateRange {
  factory HlsDateRange({required Map<String, String> attributes}) {
    final frozen = Map<String, String>.unmodifiable(attributes);
    final id = frozen['ID'];
    if (id == null || id.trim().isEmpty) {
      throw ArgumentError.value(
        attributes,
        'attributes',
        'DATERANGE requires a non-empty ID.',
      );
    }
    final startRaw = frozen['START-DATE'];
    final startDate = startRaw == null ? null : DateTime.tryParse(startRaw);
    if (startDate == null) {
      throw ArgumentError.value(
        attributes,
        'attributes',
        'DATERANGE requires a valid START-DATE.',
      );
    }
    final endRaw = frozen['END-DATE'];
    final endDate = endRaw == null ? null : DateTime.tryParse(endRaw);
    if (endRaw != null && endDate == null) {
      throw ArgumentError.value(
        attributes,
        'attributes',
        'DATERANGE END-DATE must be valid ISO-8601.',
      );
    }
    if (endDate != null && endDate.isBefore(startDate)) {
      throw ArgumentError.value(
        attributes,
        'attributes',
        'DATERANGE END-DATE cannot precede START-DATE.',
      );
    }
    _validatePositiveSeconds(frozen, 'DURATION');
    _validatePositiveSeconds(frozen, 'PLANNED-DURATION');
    final endOnNext = frozen['END-ON-NEXT'];
    if (endOnNext != null && endOnNext != 'YES') {
      throw ArgumentError.value(
        attributes,
        'attributes',
        'DATERANGE END-ON-NEXT must be YES when present.',
      );
    }
    if (endOnNext == 'YES' &&
        (frozen['CLASS'] == null ||
            frozen.containsKey('END-DATE') ||
            frozen.containsKey('DURATION'))) {
      throw ArgumentError.value(
        attributes,
        'attributes',
        'END-ON-NEXT requires CLASS and forbids END-DATE or DURATION.',
      );
    }
    return HlsDateRange._(
      UnmodifiableMapView(frozen),
      startDate.toUtc(),
      endDate?.toUtc(),
    );
  }

  HlsDateRange._(this.attributes, this.startDate, this.endDate);

  final UnmodifiableMapView<String, String> attributes;
  final DateTime startDate;
  final DateTime? endDate;

  String get id => attributes['ID']!;
  String? get className => attributes['CLASS'];
  Duration? get duration {
    final raw = attributes['DURATION'];
    final seconds = raw == null ? null : double.tryParse(raw);
    return seconds == null
        ? null
        : Duration(
            microseconds: (seconds * Duration.microsecondsPerSecond).round(),
          );
  }

  DateTime? get effectiveEndDate =>
      endDate ?? (duration == null ? null : startDate.add(duration!));

  bool get hasExplicitAdSignal {
    final normalizedClass = className?.toLowerCase() ?? '';
    return _adDateRangeClass.hasMatch(normalizedClass) ||
        attributes.containsKey('SCTE35-OUT') ||
        attributes.containsKey('SCTE35-CMD');
  }
}

void _validatePositiveSeconds(Map<String, String> attributes, String name) {
  final raw = attributes[name];
  if (raw == null) {
    return;
  }
  final value = double.tryParse(raw);
  if (value == null || !value.isFinite || value <= 0) {
    throw ArgumentError.value(
      attributes,
      'attributes',
      'DATERANGE $name must be finite and positive.',
    );
  }
}

final RegExp _adDateRangeClass = RegExp(
  r'(^|[._:/-])(ad|ads|advert|advertising|commercial|interstitial|promo|sponsor)([._:/-]|$)',
  caseSensitive: false,
);
