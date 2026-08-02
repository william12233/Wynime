import 'dart:collection';

import '../../domain/models/hls_manifest.dart';

final class HlsParseLimits {
  const HlsParseLimits({
    this.maxCharacters = 1024 * 1024,
    this.maxLines = 20000,
    this.maxSegments = 10000,
    this.maxVariants = 512,
    this.maxRenditions = 512,
    this.maxDateRanges = 512,
    this.maxAttributes = 64,
    this.maxLineLength = 16384,
    this.maxUriLength = 4096,
  }) : assert(maxCharacters > 0),
       assert(maxLines > 0),
       assert(maxSegments > 0),
       assert(maxVariants > 0),
       assert(maxRenditions > 0),
       assert(maxDateRanges > 0),
       assert(maxAttributes > 0),
       assert(maxLineLength > 0),
       assert(maxUriLength > 0);

  final int maxCharacters;
  final int maxLines;
  final int maxSegments;
  final int maxVariants;
  final int maxRenditions;
  final int maxDateRanges;
  final int maxAttributes;
  final int maxLineLength;
  final int maxUriLength;
}

final class HlsManifestParseException implements FormatException {
  const HlsManifestParseException(this.code, this.message, {this.line});

  final String code;
  @override
  final String message;
  final int? line;
  @override
  dynamic get source => null;
  @override
  int? get offset => line;

  @override
  String toString() =>
      'HlsManifestParseException($code${line == null ? '' : ', line $line'}): $message';
}

final class HlsManifestParser {
  const HlsManifestParser({this.limits = const HlsParseLimits()});

  final HlsParseLimits limits;

  HlsPlaylist parse({required String source, required Uri sourceUri}) {
    _validateSource(source, sourceUri);
    final canonicalSource = source
        .replaceAll('\r\n', '\n')
        .replaceAll('\r', '\n');
    final lines = canonicalSource.split('\n');
    if (lines.length > limits.maxLines) {
      throw const HlsManifestParseException(
        'line_count_exceeded',
        'Manifest exceeds the configured line budget.',
      );
    }

    final normalized = <_Line>[];
    for (var index = 0; index < lines.length; index += 1) {
      final raw = index == 0
          ? lines[index].replaceFirst('\uFEFF', '')
          : lines[index];
      if (raw.length > limits.maxLineLength) {
        throw HlsManifestParseException(
          'line_budget_exceeded',
          'A playlist line exceeds the configured limit.',
          line: index + 1,
        );
      }
      final value = raw.trim();
      if (value.isNotEmpty) {
        normalized.add(_Line(index + 1, value));
      }
    }
    if (normalized.isEmpty || normalized.first.value != '#EXTM3U') {
      throw const HlsManifestParseException(
        'missing_header',
        'Playlist must begin with #EXTM3U.',
        line: 1,
      );
    }

    final hasMasterTag = normalized.any(
      (line) =>
          line.value.startsWith('#EXT-X-STREAM-INF:') ||
          line.value.startsWith('#EXT-X-I-FRAME-STREAM-INF:') ||
          line.value.startsWith('#EXT-X-MEDIA:'),
    );
    final hasMediaTag = normalized.any(
      (line) =>
          line.value.startsWith('#EXTINF:') ||
          line.value.startsWith('#EXT-X-TARGETDURATION:') ||
          line.value.startsWith('#EXT-X-MEDIA-SEQUENCE:'),
    );
    if (hasMasterTag && hasMediaTag) {
      throw const HlsManifestParseException(
        'mixed_playlist_kinds',
        'Master and media playlist tags cannot be mixed.',
      );
    }
    return hasMasterTag
        ? _parseMaster(normalized, sourceUri)
        : _parseMedia(normalized, sourceUri);
  }

  void _validateSource(String source, Uri sourceUri) {
    if (source.length > limits.maxCharacters) {
      throw const HlsManifestParseException(
        'manifest_budget_exceeded',
        'Manifest exceeds the configured character budget.',
      );
    }
    if (source.contains('\u0000')) {
      throw const HlsManifestParseException(
        'nul_character',
        'Manifest contains a NUL character.',
      );
    }
    _validateHttpUri(sourceUri, line: null);
  }

  HlsMasterPlaylist _parseMaster(List<_Line> lines, Uri sourceUri) {
    int? version;
    var independentSegments = false;
    var sawIndependentSegments = false;
    final variants = <HlsVariantStream>[];
    final renditions = <HlsMediaRendition>[];
    Map<String, String>? pendingStreamAttributes;
    int? pendingStreamLine;

    for (final line in lines.skip(1)) {
      final value = line.value;
      if (pendingStreamAttributes != null) {
        if (value.startsWith('#')) {
          throw HlsManifestParseException(
            'missing_variant_uri',
            'EXT-X-STREAM-INF must be followed immediately by one URI.',
            line: pendingStreamLine,
          );
        }
        final uri = _resolveUri(sourceUri, value, line.number);
        variants.add(
          _variantFromAttributes(
            pendingStreamAttributes,
            uri,
            pendingStreamLine!,
          ),
        );
        _enforceVariantBudget(variants.length, line.number);
        pendingStreamAttributes = null;
        pendingStreamLine = null;
        continue;
      }
      if (!value.startsWith('#')) {
        throw HlsManifestParseException(
          'orphan_uri',
          'Master playlist URI is not associated with EXT-X-STREAM-INF.',
          line: line.number,
        );
      }
      if (!value.startsWith('#EXT')) {
        continue;
      }
      if (value == '#EXT-X-INDEPENDENT-SEGMENTS') {
        _rejectDuplicate(
          sawIndependentSegments,
          line,
          'duplicate_independent_segments',
        );
        sawIndependentSegments = true;
        independentSegments = true;
      } else if (value.startsWith('#EXT-X-VERSION:')) {
        _rejectDuplicate(version != null, line, 'duplicate_version');
        version = _parseNonNegativeInt(
          value.substring(15),
          line,
          'invalid_version',
        );
      } else if (value.startsWith('#EXT-X-STREAM-INF:')) {
        pendingStreamAttributes = _parseAttributeList(
          value.substring(18),
          line,
        );
        pendingStreamLine = line.number;
      } else if (value.startsWith('#EXT-X-I-FRAME-STREAM-INF:')) {
        final attributes = _parseAttributeList(value.substring(26), line);
        final uriValue = _requiredAttribute(attributes, 'URI', line);
        variants.add(
          _variantFromAttributes(
            attributes,
            _resolveUri(sourceUri, uriValue, line.number),
            line.number,
          ),
        );
        _enforceVariantBudget(variants.length, line.number);
      } else if (value.startsWith('#EXT-X-MEDIA:')) {
        renditions.add(
          _renditionFromAttributes(
            _parseAttributeList(value.substring(13), line),
            sourceUri,
            line,
          ),
        );
        if (renditions.length > limits.maxRenditions) {
          throw HlsManifestParseException(
            'rendition_budget_exceeded',
            'Master playlist contains too many renditions.',
            line: line.number,
          );
        }
      } else if (value.startsWith('#EXT-X-SESSION-DATA:') ||
          value.startsWith('#EXT-X-SESSION-KEY:') ||
          value.startsWith('#EXT-X-START:') ||
          value.startsWith('#EXT-X-DEFINE:')) {
        throw HlsManifestParseException(
          'unsupported_master_semantics',
          'This master playlist uses semantics not yet safe to transform.',
          line: line.number,
        );
      } else if (value.startsWith('#EXT-X-')) {
        throw HlsManifestParseException(
          'unsupported_tag',
          'Unsupported HLS tag.',
          line: line.number,
        );
      }
    }
    if (pendingStreamAttributes != null) {
      throw HlsManifestParseException(
        'missing_variant_uri',
        'EXT-X-STREAM-INF must be followed by one URI.',
        line: pendingStreamLine,
      );
    }
    return HlsMasterPlaylist(
      sourceUri: sourceUri,
      version: version,
      variants: variants,
      renditions: renditions,
      independentSegments: independentSegments,
    );
  }

  void _enforceVariantBudget(int count, int line) {
    if (count > limits.maxVariants) {
      throw HlsManifestParseException(
        'variant_budget_exceeded',
        'Master playlist contains too many variants.',
        line: line,
      );
    }
  }

  HlsVariantStream _variantFromAttributes(
    Map<String, String> attributes,
    Uri uri,
    int line,
  ) {
    final bandwidth = int.tryParse(
      _requiredAttribute(attributes, 'BANDWIDTH', _Line(line, '')),
    );
    if (bandwidth == null || bandwidth <= 0) {
      throw HlsManifestParseException(
        'invalid_bandwidth',
        'Variant BANDWIDTH must be a positive integer.',
        line: line,
      );
    }
    final averageRaw = attributes['AVERAGE-BANDWIDTH'];
    final averageBandwidth = averageRaw == null
        ? null
        : int.tryParse(averageRaw);
    if (averageRaw != null &&
        (averageBandwidth == null || averageBandwidth <= 0)) {
      throw HlsManifestParseException(
        'invalid_average_bandwidth',
        'Variant AVERAGE-BANDWIDTH must be a positive integer.',
        line: line,
      );
    }
    final frameRateRaw = attributes['FRAME-RATE'];
    final frameRate = frameRateRaw == null
        ? null
        : double.tryParse(frameRateRaw);
    if (frameRateRaw != null &&
        (frameRate == null || !frameRate.isFinite || frameRate <= 0)) {
      throw HlsManifestParseException(
        'invalid_frame_rate',
        'Variant FRAME-RATE must be finite and positive.',
        line: line,
      );
    }
    return HlsVariantStream(
      uri: uri,
      bandwidth: bandwidth,
      averageBandwidth: averageBandwidth,
      codecs: attributes['CODECS'],
      resolution: _parseResolution(attributes['RESOLUTION'], line),
      frameRate: frameRate,
      audioGroupId: attributes['AUDIO'],
      subtitleGroupId: attributes['SUBTITLES'],
    );
  }

  HlsMediaRendition _renditionFromAttributes(
    Map<String, String> attributes,
    Uri sourceUri,
    _Line line,
  ) {
    final type = _requiredAttribute(attributes, 'TYPE', line);
    if (!_renditionTypes.contains(type)) {
      throw HlsManifestParseException(
        'unsupported_rendition_type',
        'Rendition TYPE is unsupported.',
        line: line.number,
      );
    }
    final forced = _parseYesNo(attributes['FORCED'], line, defaultValue: false);
    if (forced && type != 'SUBTITLES') {
      throw HlsManifestParseException(
        'invalid_forced_rendition',
        'FORCED is valid only for subtitle renditions.',
        line: line.number,
      );
    }
    final uriValue = attributes['URI'];
    if (type == 'CLOSED-CAPTIONS' && uriValue != null) {
      throw HlsManifestParseException(
        'invalid_closed_captions_uri',
        'CLOSED-CAPTIONS renditions cannot include URI.',
        line: line.number,
      );
    }
    return HlsMediaRendition(
      type: type,
      groupId: _requiredAttribute(attributes, 'GROUP-ID', line),
      name: _requiredAttribute(attributes, 'NAME', line),
      defaultSelection: _parseYesNo(
        attributes['DEFAULT'],
        line,
        defaultValue: false,
      ),
      autoSelect: _parseYesNo(
        attributes['AUTOSELECT'],
        line,
        defaultValue: false,
      ),
      forced: forced,
      uri: uriValue == null
          ? null
          : _resolveUri(sourceUri, uriValue, line.number),
      language: attributes['LANGUAGE'],
      characteristics: attributes['CHARACTERISTICS'],
    );
  }

  HlsMediaPlaylist _parseMedia(List<_Line> lines, Uri sourceUri) {
    final dateRanges = _preparseDateRanges(lines);
    int? version;
    Duration? targetDuration;
    var mediaSequence = 0;
    var sawMediaSequence = false;
    var discontinuitySequence = 0;
    var sawDiscontinuitySequence = false;
    HlsPlaylistType? playlistType;
    var endList = false;
    var independentSegments = false;
    var sawIndependentSegments = false;
    var discontinuityGroup = 0;
    var pendingDiscontinuity = false;
    var explicitAdCue = false;
    var pendingGap = false;
    Duration? pendingDuration;
    var pendingTitle = '';
    HlsByteRange? pendingByteRange;
    HlsByteRange? previousByteRange;
    Uri? previousByteRangeUri;
    DateTime? pendingProgramDateTime;
    var hasPendingProgramDateTimeTag = false;
    HlsKey? currentKey;
    HlsInitializationMap? currentMap;
    final segments = <HlsMediaSegment>[];

    for (final line in lines.skip(1)) {
      final value = line.value;
      if (endList) {
        if (value.startsWith('#') && !value.startsWith('#EXT')) {
          continue;
        }
        throw HlsManifestParseException(
          'content_after_endlist',
          'No HLS tags or resources may follow EXT-X-ENDLIST.',
          line: line.number,
        );
      }
      if (!value.startsWith('#')) {
        if (pendingDuration == null) {
          throw HlsManifestParseException(
            'orphan_uri',
            'Media URI is not associated with EXTINF.',
            line: line.number,
          );
        }
        if (segments.length >= limits.maxSegments) {
          throw HlsManifestParseException(
            'segment_budget_exceeded',
            'Media playlist contains too many segments.',
            line: line.number,
          );
        }
        if (pendingDiscontinuity) {
          discontinuityGroup += 1;
        }
        final segmentUri = _resolveUri(sourceUri, value, line.number);
        var effectiveByteRange = pendingByteRange;
        if (effectiveByteRange != null && effectiveByteRange.offset == null) {
          if (previousByteRange == null ||
              previousByteRange.offset == null ||
              previousByteRangeUri != segmentUri) {
            throw HlsManifestParseException(
              'ambiguous_byte_range_offset',
              'An implicit BYTERANGE offset requires a preceding range for the same resource.',
              line: line.number,
            );
          }
          effectiveByteRange = HlsByteRange(
            length: effectiveByteRange.length,
            offset: previousByteRange.offset! + previousByteRange.length,
          );
        }
        final adDateRangeCue = _matchesAdDateRange(
          pendingProgramDateTime,
          dateRanges,
        );
        segments.add(
          HlsMediaSegment(
            mediaSequence: mediaSequence + segments.length,
            index: segments.length,
            uri: segmentUri,
            duration: pendingDuration,
            title: pendingTitle,
            discontinuityBefore: pendingDiscontinuity,
            discontinuityGroup: discontinuityGroup,
            explicitAdCue: explicitAdCue,
            adDateRangeCue: adDateRangeCue,
            gap: pendingGap,
            byteRange: effectiveByteRange,
            key: currentKey,
            initializationMap: currentMap,
            programDateTime: pendingProgramDateTime,
          ),
        );
        if (effectiveByteRange == null) {
          previousByteRange = null;
          previousByteRangeUri = null;
        } else {
          previousByteRange = effectiveByteRange;
          previousByteRangeUri = segmentUri;
        }
        if (pendingProgramDateTime != null) {
          pendingProgramDateTime = pendingProgramDateTime.add(pendingDuration);
        }
        pendingDuration = null;
        pendingTitle = '';
        hasPendingProgramDateTimeTag = false;
        pendingByteRange = null;
        pendingGap = false;
        pendingDiscontinuity = false;
        continue;
      }
      if (!value.startsWith('#EXT')) {
        continue;
      }
      if (value == '#EXT-X-INDEPENDENT-SEGMENTS') {
        _rejectDuplicate(
          sawIndependentSegments,
          line,
          'duplicate_independent_segments',
        );
        sawIndependentSegments = true;
        independentSegments = true;
      } else if (value == '#EXT-X-ENDLIST') {
        _rejectDuplicate(endList, line, 'duplicate_endlist');
        endList = true;
      } else if (value == '#EXT-X-DISCONTINUITY') {
        if (pendingDiscontinuity) {
          throw HlsManifestParseException(
            'duplicate_discontinuity',
            'Consecutive discontinuities are ambiguous.',
            line: line.number,
          );
        }
        pendingDiscontinuity = true;
      } else if (value == '#EXT-X-GAP') {
        if (pendingGap) {
          throw HlsManifestParseException(
            'duplicate_gap',
            'A segment cannot repeat EXT-X-GAP.',
            line: line.number,
          );
        }
        pendingGap = true;
      } else if (value == '#EXT-X-CUE-IN') {
        if (!explicitAdCue) {
          throw HlsManifestParseException(
            'cue_in_without_start',
            'CUE-IN appears without an active explicit ad cue.',
            line: line.number,
          );
        }
        explicitAdCue = false;
      } else if (value.startsWith('#EXT-X-CUE-OUT-CONT:')) {
        if (!explicitAdCue) {
          throw HlsManifestParseException(
            'cue_continuation_without_start',
            'CUE-OUT-CONT appears without a preceding CUE-OUT.',
            line: line.number,
          );
        }
      } else if (value.startsWith('#EXT-X-CUE-OUT') ||
          value.startsWith('#EXT-OATCLS-SCTE35:') ||
          value.startsWith('#EXT-X-ASSET:')) {
        if (explicitAdCue && value.startsWith('#EXT-X-CUE-OUT')) {
          throw HlsManifestParseException(
            'duplicate_cue_out',
            'A new CUE-OUT cannot begin before CUE-IN.',
            line: line.number,
          );
        }
        explicitAdCue = true;
      } else if (value.startsWith('#EXTINF:')) {
        if (pendingDuration != null) {
          throw HlsManifestParseException(
            'duplicate_extinf',
            'EXTINF must be followed by one URI.',
            line: line.number,
          );
        }
        final payload = value.substring(8);
        final comma = payload.indexOf(',');
        final durationRaw = comma < 0 ? payload : payload.substring(0, comma);
        final durationSeconds = double.tryParse(durationRaw);
        if (durationSeconds == null ||
            !durationSeconds.isFinite ||
            durationSeconds <= 0) {
          throw HlsManifestParseException(
            'invalid_extinf',
            'EXTINF duration must be finite and positive.',
            line: line.number,
          );
        }
        pendingDuration = Duration(
          microseconds: (durationSeconds * Duration.microsecondsPerSecond)
              .round(),
        );
        pendingTitle = comma < 0 ? '' : payload.substring(comma + 1);
      } else if (value.startsWith('#EXT-X-TARGETDURATION:')) {
        _rejectDuplicate(
          targetDuration != null,
          line,
          'duplicate_target_duration',
        );
        targetDuration = Duration(
          seconds: _parsePositiveInt(
            value.substring(22),
            line,
            'invalid_target_duration',
          ),
        );
      } else if (value.startsWith('#EXT-X-MEDIA-SEQUENCE:')) {
        _rejectDuplicate(sawMediaSequence, line, 'duplicate_media_sequence');
        if (segments.isNotEmpty || pendingDuration != null) {
          throw HlsManifestParseException(
            'late_media_sequence',
            'MEDIA-SEQUENCE must precede segments.',
            line: line.number,
          );
        }
        sawMediaSequence = true;
        mediaSequence = _parseNonNegativeInt(
          value.substring(22),
          line,
          'invalid_media_sequence',
        );
      } else if (value.startsWith('#EXT-X-DISCONTINUITY-SEQUENCE:')) {
        _rejectDuplicate(
          sawDiscontinuitySequence,
          line,
          'duplicate_discontinuity_sequence',
        );
        if (segments.isNotEmpty || pendingDuration != null) {
          throw HlsManifestParseException(
            'late_discontinuity_sequence',
            'DISCONTINUITY-SEQUENCE must precede segments.',
            line: line.number,
          );
        }
        sawDiscontinuitySequence = true;
        discontinuitySequence = _parseNonNegativeInt(
          value.substring(30),
          line,
          'invalid_discontinuity_sequence',
        );
      } else if (value.startsWith('#EXT-X-VERSION:')) {
        _rejectDuplicate(version != null, line, 'duplicate_version');
        version = _parseNonNegativeInt(
          value.substring(15),
          line,
          'invalid_version',
        );
      } else if (value.startsWith('#EXT-X-PLAYLIST-TYPE:')) {
        _rejectDuplicate(playlistType != null, line, 'duplicate_playlist_type');
        final raw = value.substring(21);
        playlistType = switch (raw) {
          'EVENT' => HlsPlaylistType.event,
          'VOD' => HlsPlaylistType.vod,
          _ => throw HlsManifestParseException(
            'invalid_playlist_type',
            'PLAYLIST-TYPE must be EVENT or VOD.',
            line: line.number,
          ),
        };
      } else if (value.startsWith('#EXT-X-BYTERANGE:')) {
        if (pendingByteRange != null) {
          throw HlsManifestParseException(
            'duplicate_byte_range',
            'A segment cannot repeat EXT-X-BYTERANGE.',
            line: line.number,
          );
        }
        pendingByteRange = _parseByteRange(value.substring(17), line);
      } else if (value.startsWith('#EXT-X-KEY:')) {
        currentKey = _parseKey(
          _parseAttributeList(value.substring(11), line),
          sourceUri,
          line,
        );
      } else if (value.startsWith('#EXT-X-MAP:')) {
        currentMap = _parseMap(
          _parseAttributeList(value.substring(11), line),
          sourceUri,
          line,
        );
      } else if (value.startsWith('#EXT-X-PROGRAM-DATE-TIME:')) {
        if (hasPendingProgramDateTimeTag) {
          throw HlsManifestParseException(
            'duplicate_program_date_time',
            'PROGRAM-DATE-TIME cannot be repeated before a segment.',
            line: line.number,
          );
        }
        final rawDateTime = value.substring(25);
        final parsed = _explicitTimeZone.hasMatch(rawDateTime)
            ? DateTime.tryParse(rawDateTime)
            : null;
        if (parsed == null) {
          throw HlsManifestParseException(
            'invalid_program_date_time',
            'PROGRAM-DATE-TIME must be ISO-8601.',
            line: line.number,
          );
        }
        pendingProgramDateTime = parsed.toUtc();
        hasPendingProgramDateTimeTag = true;
      } else if (value.startsWith('#EXT-X-DATERANGE:')) {
        // Parsed in a bounded pre-pass so ranges may safely precede or follow
        // their PROGRAM-DATE-TIME segments without changing the result.
        continue;
      } else if (value.startsWith('#EXT-X-PART:') ||
          value.startsWith('#EXT-X-PRELOAD-HINT:') ||
          value.startsWith('#EXT-X-SKIP:') ||
          value.startsWith('#EXT-X-RENDITION-REPORT:') ||
          value.startsWith('#EXT-X-DEFINE:') ||
          value.startsWith('#EXT-X-START:') ||
          value == '#EXT-X-I-FRAMES-ONLY') {
        throw HlsManifestParseException(
          'unsupported_media_semantics',
          'Playlist uses media semantics not yet safe to sanitize.',
          line: line.number,
        );
      } else if (value.startsWith('#EXT-X-')) {
        throw HlsManifestParseException(
          'unsupported_tag',
          'Unsupported HLS tag.',
          line: line.number,
        );
      }
    }

    if (pendingDuration != null) {
      throw const HlsManifestParseException(
        'missing_segment_uri',
        'The final EXTINF has no media URI.',
      );
    }
    if (explicitAdCue) {
      throw const HlsManifestParseException(
        'unterminated_ad_cue',
        'An explicit ad cue must end with EXT-X-CUE-IN.',
      );
    }
    if (pendingByteRange != null || pendingGap || pendingDiscontinuity) {
      throw const HlsManifestParseException(
        'dangling_segment_tag',
        'A segment-scoped tag has no following media segment.',
      );
    }
    if (targetDuration == null) {
      throw const HlsManifestParseException(
        'missing_target_duration',
        'Media playlist requires EXT-X-TARGETDURATION.',
      );
    }
    if (segments.isEmpty) {
      throw const HlsManifestParseException(
        'missing_segments',
        'Media playlist requires at least one segment.',
      );
    }
    final maximumSegmentSeconds = segments
        .map(
          (segment) =>
              (segment.duration.inMicroseconds / Duration.microsecondsPerSecond)
                  .ceil(),
        )
        .reduce((left, right) => left > right ? left : right);
    if (targetDuration.inSeconds < maximumSegmentSeconds) {
      throw const HlsManifestParseException(
        'target_duration_too_small',
        'TARGETDURATION must cover the longest rounded segment duration.',
      );
    }

    return HlsMediaPlaylist(
      sourceUri: sourceUri,
      version: version,
      targetDuration: targetDuration,
      mediaSequence: mediaSequence,
      discontinuitySequence: discontinuitySequence,
      playlistType: playlistType,
      endList: endList,
      independentSegments: independentSegments,
      segments: segments,
      dateRanges: dateRanges,
    );
  }

  List<HlsDateRange> _preparseDateRanges(List<_Line> lines) {
    final ranges = <HlsDateRange>[];
    final ids = <String>{};
    for (final line in lines.skip(1)) {
      if (!line.value.startsWith('#EXT-X-DATERANGE:')) {
        continue;
      }
      if (ranges.length >= limits.maxDateRanges) {
        throw HlsManifestParseException(
          'date_range_budget_exceeded',
          'Playlist contains too many DATERANGE records.',
          line: line.number,
        );
      }
      final attributes = _parseAttributeList(line.value.substring(17), line);
      if (attributes['END-ON-NEXT'] == 'YES') {
        throw HlsManifestParseException(
          'unsupported_date_range_semantics',
          'END-ON-NEXT DATERANGE semantics are not safe to sanitize yet.',
          line: line.number,
        );
      }
      late HlsDateRange range;
      try {
        range = HlsDateRange(attributes: attributes);
      } on ArgumentError {
        throw HlsManifestParseException(
          'invalid_date_range',
          'DATERANGE attributes are invalid.',
          line: line.number,
        );
      }
      if (!ids.add(range.id)) {
        throw HlsManifestParseException(
          'duplicate_date_range_id',
          'DATERANGE ID must be unique within one playlist.',
          line: line.number,
        );
      }
      ranges.add(range);
    }
    return List<HlsDateRange>.unmodifiable(ranges);
  }

  HlsKey _parseKey(Map<String, String> attributes, Uri sourceUri, _Line line) {
    final method = _requiredAttribute(attributes, 'METHOD', line).toUpperCase();
    if (method == 'NONE') {
      if (attributes.keys.any((key) => key != 'METHOD')) {
        throw HlsManifestParseException(
          'invalid_none_key',
          'METHOD=NONE cannot include other attributes.',
          line: line.number,
        );
      }
      return HlsKey(method: method, uri: null);
    }
    if (method != 'AES-128') {
      throw HlsManifestParseException(
        'unsupported_key_method',
        'Only identity AES-128 encryption is supported in Phase 5.',
        line: line.number,
      );
    }
    final keyFormat = attributes['KEYFORMAT'];
    if (keyFormat != null && keyFormat != 'identity') {
      throw HlsManifestParseException(
        'unsupported_key_format',
        'Non-identity key formats are outside the Phase 5 boundary.',
        line: line.number,
      );
    }
    final keyFormatVersions = attributes['KEYFORMATVERSIONS'];
    if (keyFormatVersions != null && keyFormatVersions != '1') {
      throw HlsManifestParseException(
        'unsupported_key_format_version',
        'Only identity key format version 1 is supported.',
        line: line.number,
      );
    }
    final iv = attributes['IV'];
    if (iv != null && !_aes128Iv.hasMatch(iv)) {
      throw HlsManifestParseException(
        'invalid_key_iv',
        'AES-128 IV must be a 128-bit hexadecimal value.',
        line: line.number,
      );
    }
    return HlsKey(
      method: method,
      uri: _resolveUri(
        sourceUri,
        _requiredAttribute(attributes, 'URI', line),
        line.number,
      ),
      iv: iv,
      keyFormat: keyFormat,
      keyFormatVersions: keyFormatVersions,
    );
  }

  HlsInitializationMap _parseMap(
    Map<String, String> attributes,
    Uri sourceUri,
    _Line line,
  ) {
    final byteRange = attributes['BYTERANGE'] == null
        ? null
        : _parseByteRange(attributes['BYTERANGE']!, line);
    if (byteRange != null && byteRange.offset == null) {
      throw HlsManifestParseException(
        'ambiguous_map_byte_range_offset',
        'EXT-X-MAP BYTERANGE requires an explicit offset.',
        line: line.number,
      );
    }
    return HlsInitializationMap(
      uri: _resolveUri(
        sourceUri,
        _requiredAttribute(attributes, 'URI', line),
        line.number,
      ),
      byteRange: byteRange,
    );
  }

  bool _matchesAdDateRange(
    DateTime? programDateTime,
    List<HlsDateRange> ranges,
  ) {
    if (programDateTime == null) {
      return false;
    }
    return ranges.any((range) {
      if (!range.hasExplicitAdSignal) {
        return false;
      }
      final end = range.effectiveEndDate;
      if (end == null) {
        return false;
      }
      return !programDateTime.isBefore(range.startDate) &&
          programDateTime.isBefore(end);
    });
  }

  Map<String, String> _parseAttributeList(String source, _Line line) {
    final result = <String, String>{};
    var index = 0;
    while (index < source.length) {
      if (result.length >= limits.maxAttributes) {
        throw HlsManifestParseException(
          'attribute_budget_exceeded',
          'Attribute list exceeds the configured budget.',
          line: line.number,
        );
      }
      final equals = source.indexOf('=', index);
      if (equals <= index) {
        throw HlsManifestParseException(
          'invalid_attribute_list',
          'Attribute list is malformed.',
          line: line.number,
        );
      }
      final name = source.substring(index, equals).trim().toUpperCase();
      if (!_attributeName.hasMatch(name) || result.containsKey(name)) {
        throw HlsManifestParseException(
          'invalid_attribute_name',
          'Attribute name is invalid or duplicated.',
          line: line.number,
        );
      }
      index = equals + 1;
      late String value;
      if (index < source.length && source.codeUnitAt(index) == 0x22) {
        index += 1;
        final buffer = StringBuffer();
        var closed = false;
        while (index < source.length) {
          final char = source[index];
          if (char == '"') {
            closed = true;
            index += 1;
            break;
          }
          if (char == '\r' || char == '\n') {
            throw HlsManifestParseException(
              'invalid_quoted_attribute',
              'Quoted attribute contains a line break.',
              line: line.number,
            );
          }
          buffer.write(char);
          index += 1;
        }
        if (!closed) {
          throw HlsManifestParseException(
            'unterminated_attribute',
            'Quoted attribute is not terminated.',
            line: line.number,
          );
        }
        value = buffer.toString();
      } else {
        final comma = source.indexOf(',', index);
        final end = comma < 0 ? source.length : comma;
        value = source.substring(index, end).trim();
        index = end;
      }
      if (value.isEmpty) {
        throw HlsManifestParseException(
          'empty_attribute',
          'Attribute value must not be empty.',
          line: line.number,
        );
      }
      result[name] = value;
      if (index == source.length) {
        break;
      }
      if (source[index] != ',') {
        throw HlsManifestParseException(
          'invalid_attribute_separator',
          'Attributes must be comma separated.',
          line: line.number,
        );
      }
      index += 1;
      while (index < source.length && source[index] == ' ') {
        index += 1;
      }
      if (index == source.length) {
        throw HlsManifestParseException(
          'trailing_attribute_separator',
          'Attribute list cannot end with a comma.',
          line: line.number,
        );
      }
    }
    return UnmodifiableMapView(result);
  }

  HlsByteRange _parseByteRange(String source, _Line line) {
    final separator = source.indexOf('@');
    final lengthRaw = separator < 0 ? source : source.substring(0, separator);
    final offsetRaw = separator < 0 ? null : source.substring(separator + 1);
    final length = int.tryParse(lengthRaw);
    final offset = offsetRaw == null ? null : int.tryParse(offsetRaw);
    if (length == null ||
        length <= 0 ||
        (offsetRaw != null && (offset == null || offset < 0))) {
      throw HlsManifestParseException(
        'invalid_byte_range',
        'BYTERANGE must use positive length and optional non-negative offset.',
        line: line.number,
      );
    }
    return HlsByteRange(length: length, offset: offset);
  }

  HlsResolution? _parseResolution(String? source, int line) {
    if (source == null) {
      return null;
    }
    final separator = source.toLowerCase().indexOf('x');
    final width = separator < 0
        ? null
        : int.tryParse(source.substring(0, separator));
    final height = separator < 0
        ? null
        : int.tryParse(source.substring(separator + 1));
    if (width == null || height == null || width <= 0 || height <= 0) {
      throw HlsManifestParseException(
        'invalid_resolution',
        'RESOLUTION must use WIDTHxHEIGHT.',
        line: line,
      );
    }
    return HlsResolution(width, height);
  }

  Uri _resolveUri(Uri base, String raw, int? line) {
    if (raw.isEmpty || raw.length > limits.maxUriLength) {
      throw HlsManifestParseException(
        'uri_budget_exceeded',
        'URI is empty or exceeds the configured budget.',
        line: line,
      );
    }
    final parsed = Uri.tryParse(raw);
    if (parsed == null) {
      throw HlsManifestParseException(
        'invalid_uri',
        'URI is malformed.',
        line: line,
      );
    }
    final resolved = base.resolveUri(parsed);
    _validateHttpUri(resolved, line: line);
    return resolved;
  }

  void _validateHttpUri(Uri uri, {required int? line}) {
    if ((uri.scheme != 'http' && uri.scheme != 'https') ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty) {
      throw HlsManifestParseException(
        'unsafe_uri',
        'HLS resources must use HTTP(S) without user info or fragments.',
        line: line,
      );
    }
  }

  String _requiredAttribute(
    Map<String, String> attributes,
    String name,
    _Line line,
  ) {
    final value = attributes[name];
    if (value == null || value.isEmpty) {
      throw HlsManifestParseException(
        'missing_attribute',
        'Required attribute is missing.',
        line: line.number,
      );
    }
    return value;
  }

  bool _parseYesNo(String? value, _Line line, {required bool defaultValue}) {
    if (value == null) {
      return defaultValue;
    }
    return switch (value) {
      'YES' => true,
      'NO' => false,
      _ => throw HlsManifestParseException(
        'invalid_yes_no',
        'Attribute must be YES or NO.',
        line: line.number,
      ),
    };
  }

  int _parsePositiveInt(String source, _Line line, String code) {
    final value = int.tryParse(source);
    if (value == null || value <= 0) {
      throw HlsManifestParseException(
        code,
        'Expected a positive integer.',
        line: line.number,
      );
    }
    return value;
  }

  int _parseNonNegativeInt(String source, _Line line, String code) {
    final value = int.tryParse(source);
    if (value == null || value < 0) {
      throw HlsManifestParseException(
        code,
        'Expected a non-negative integer.',
        line: line.number,
      );
    }
    return value;
  }

  void _rejectDuplicate(bool duplicate, _Line line, String code) {
    if (duplicate) {
      throw HlsManifestParseException(
        code,
        'Singleton playlist tag is duplicated.',
        line: line.number,
      );
    }
  }
}

final class _Line {
  const _Line(this.number, this.value);

  final int number;
  final String value;
}

const Set<String> _renditionTypes = {
  'AUDIO',
  'VIDEO',
  'SUBTITLES',
  'CLOSED-CAPTIONS',
};
final RegExp _attributeName = RegExp(r'^[A-Z0-9-]+$');
final RegExp _aes128Iv = RegExp(r'^0[xX][0-9A-Fa-f]{32}$');
final RegExp _explicitTimeZone = RegExp(r'(?:Z|[+-]\d{2}:\d{2})$');
