import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../../domain/models/hls_manifest.dart';
import '../../domain/models/manifest_fingerprint.dart';

final class HlsManifestFingerprinter {
  const HlsManifestFingerprinter();

  ManifestFingerprint fingerprint(HlsPlaylist playlist) {
    final canonical = switch (playlist) {
      HlsMediaPlaylist media => _canonicalMedia(media),
      HlsMasterPlaylist master => _canonicalMaster(master),
    };
    return ManifestFingerprint(
      algorithm: 'sha256',
      value: sha256.convert(utf8.encode(canonical)).toString(),
    );
  }

  String _canonicalMedia(HlsMediaPlaylist playlist) => jsonEncode({
    'kind': 'media',
    'source': _canonicalUri(playlist.sourceUri),
    'version': playlist.version,
    'targetMicros': playlist.targetDuration.inMicroseconds,
    'mediaSequence': playlist.mediaSequence,
    'discontinuitySequence': playlist.discontinuitySequence,
    'playlistType': playlist.playlistType?.name,
    'endList': playlist.endList,
    'independentSegments': playlist.independentSegments,
    'dateRanges': [
      for (final range in playlist.dateRanges)
        {
          for (final entry
              in (range.attributes.entries.toList()
                ..sort((left, right) => left.key.compareTo(right.key))))
            entry.key: _canonicalAttribute(entry.key, entry.value),
        },
    ],
    'segments': [
      for (final segment in playlist.segments)
        {
          'index': segment.index,
          'mediaSequence': segment.mediaSequence,
          'durationMicros': segment.duration.inMicroseconds,
          'title': segment.title,
          'discontinuityGroup': segment.discontinuityGroup,
          'discontinuityBefore': segment.discontinuityBefore,
          'gap': segment.gap,
          'explicitAdCue': segment.explicitAdCue,
          'adDateRangeCue': segment.adDateRangeCue,
          'uri': _canonicalUri(segment.uri),
          'byteRange': _canonicalByteRange(segment.byteRange),
          'key': _canonicalKey(segment.key),
          'initializationMap': _canonicalMap(segment.initializationMap),
          'programDateTime': segment.programDateTime?.toUtc().toIso8601String(),
        },
    ],
  });

  String _canonicalMaster(HlsMasterPlaylist playlist) => jsonEncode({
    'kind': 'master',
    'source': _canonicalUri(playlist.sourceUri),
    'version': playlist.version,
    'independentSegments': playlist.independentSegments,
    'renditions': [
      for (final rendition in playlist.renditions)
        {
          'type': rendition.type,
          'groupId': rendition.groupId,
          'name': rendition.name,
          'defaultSelection': rendition.defaultSelection,
          'autoSelect': rendition.autoSelect,
          'forced': rendition.forced,
          'language': rendition.language,
          'characteristics': rendition.characteristics,
          'uri': rendition.uri == null ? null : _canonicalUri(rendition.uri!),
        },
    ],
    'variants': [
      for (final variant in playlist.variants)
        {
          'bandwidth': variant.bandwidth,
          'averageBandwidth': variant.averageBandwidth,
          'codecs': variant.codecs,
          'resolution': variant.resolution == null
              ? null
              : {
                  'width': variant.resolution!.width,
                  'height': variant.resolution!.height,
                },
          'frameRate': variant.frameRate,
          'audioGroupId': variant.audioGroupId,
          'subtitleGroupId': variant.subtitleGroupId,
          'uri': _canonicalUri(variant.uri),
        },
    ],
  });

  String _canonicalAttribute(String name, String value) {
    final upper = name.toUpperCase();
    if (upper == 'SCTE35-OUT' || upper == 'SCTE35-CMD') {
      return '<signal:${value.length}>';
    }
    return value;
  }

  Map<String, Object?>? _canonicalKey(HlsKey? key) => key == null
      ? null
      : {
          'method': key.method,
          'uri': key.uri == null ? null : _canonicalUri(key.uri!),
          'iv': key.iv,
          'keyFormat': key.keyFormat,
          'keyFormatVersions': key.keyFormatVersions,
        };

  Map<String, Object?>? _canonicalMap(HlsInitializationMap? map) => map == null
      ? null
      : {
          'uri': _canonicalUri(map.uri),
          'byteRange': _canonicalByteRange(map.byteRange),
        };

  Map<String, int?>? _canonicalByteRange(HlsByteRange? range) =>
      range == null ? null : {'length': range.length, 'offset': range.offset};

  String _canonicalUri(Uri uri) {
    final query = <String>[];
    final entries = uri.queryParametersAll.entries.toList()
      ..sort((left, right) => left.key.compareTo(right.key));
    for (final entry in entries) {
      final name = entry.key.toLowerCase();
      final values = entry.value.toList()..sort();
      if (_volatileQueryNames.contains(name) || _looksVolatile(name)) {
        query.add(
          '${Uri.encodeQueryComponent(name)}=<volatile:${values.length}>',
        );
      } else {
        for (final value in values) {
          query.add(
            '${Uri.encodeQueryComponent(name)}=${Uri.encodeQueryComponent(value)}',
          );
        }
      }
    }
    final normalizedPort =
        (uri.scheme == 'https' && uri.port == 443) ||
            (uri.scheme == 'http' && uri.port == 80)
        ? null
        : uri.hasPort
        ? uri.port
        : null;
    final base = Uri(
      scheme: uri.scheme.toLowerCase(),
      host: uri.host.toLowerCase(),
      port: normalizedPort,
      pathSegments: uri.pathSegments.isEmpty ? const [''] : uri.pathSegments,
    ).toString();
    return query.isEmpty ? base : '$base?${query.join('&')}';
  }

  bool _looksVolatile(String name) =>
      name.contains('token') ||
      name.contains('signature') ||
      name.endsWith('sig') ||
      name.contains('credential') ||
      name.contains('expires') ||
      name == 'exp';
}

const Set<String> _volatileQueryNames = {
  'auth',
  'authorization',
  'hdnea',
  'hmac',
  'jwt',
  'key',
  'policy',
  'session',
  'sessionid',
  'x-amz-algorithm',
  'x-amz-credential',
  'x-amz-date',
  'x-amz-expires',
  'x-amz-security-token',
  'x-amz-signature',
  'x-goog-credential',
  'x-goog-date',
  'x-goog-expires',
  'x-goog-signature',
};
