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

  String _canonicalMedia(HlsMediaPlaylist playlist) {
    final output = StringBuffer()
      ..writeln('kind=media')
      ..writeln('version=${playlist.version ?? -1}')
      ..writeln('target=${playlist.targetDuration.inMicroseconds}')
      ..writeln('mediaSequence=${playlist.mediaSequence}')
      ..writeln('discontinuitySequence=${playlist.discontinuitySequence}')
      ..writeln('playlistType=${playlist.playlistType?.name ?? 'none'}')
      ..writeln('endList=${playlist.endList}')
      ..writeln('independent=${playlist.independentSegments}');
    for (final range in playlist.dateRanges) {
      final attributes = range.attributes.entries.toList()
        ..sort((left, right) => left.key.compareTo(right.key));
      output
        ..write('daterange|')
        ..writeln(
          attributes
              .map(
                (entry) =>
                    '${entry.key}=${_canonicalAttribute(entry.key, entry.value)}',
              )
              .join('|'),
        );
    }
    for (final segment in playlist.segments) {
      output
        ..write('segment|${segment.index}|${segment.mediaSequence}|')
        ..write(
          '${segment.duration.inMicroseconds}|${segment.discontinuityGroup}|',
        )
        ..write(
          '${segment.discontinuityBefore}|${segment.gap}|'
          '${segment.explicitAdCue}|${segment.adDateRangeCue}|',
        )
        ..write('${_canonicalUri(segment.uri)}|')
        ..write('${_canonicalByteRange(segment.byteRange)}|')
        ..write('${_canonicalKey(segment.key)}|')
        ..write('${_canonicalMap(segment.initializationMap)}|')
        ..writeln(segment.programDateTime?.toUtc().toIso8601String() ?? '');
    }
    return output.toString();
  }

  String _canonicalMaster(HlsMasterPlaylist playlist) {
    final output = StringBuffer()
      ..writeln('kind=master')
      ..writeln('version=${playlist.version ?? -1}')
      ..writeln('independent=${playlist.independentSegments}');
    for (final rendition in playlist.renditions) {
      output
        ..write(
          'rendition|${rendition.type}|${rendition.groupId}|${rendition.name}|',
        )
        ..write(
          '${rendition.defaultSelection}|${rendition.autoSelect}|${rendition.forced}|',
        )
        ..write(
          '${rendition.language ?? ''}|${rendition.characteristics ?? ''}|',
        )
        ..writeln(rendition.uri == null ? '' : _canonicalUri(rendition.uri!));
    }
    for (final variant in playlist.variants) {
      output
        ..write(
          'variant|${variant.bandwidth}|${variant.averageBandwidth ?? -1}|',
        )
        ..write('${variant.codecs ?? ''}|')
        ..write(
          '${variant.resolution?.width ?? -1}x${variant.resolution?.height ?? -1}|',
        )
        ..write('${variant.frameRate ?? -1}|${variant.audioGroupId ?? ''}|')
        ..write('${variant.subtitleGroupId ?? ''}|')
        ..writeln(_canonicalUri(variant.uri));
    }
    return output.toString();
  }

  String _canonicalAttribute(String name, String value) {
    final upper = name.toUpperCase();
    if (upper == 'SCTE35-OUT' || upper == 'SCTE35-CMD') {
      return '<signal:${value.length}>';
    }
    return value;
  }

  String _canonicalKey(HlsKey? key) {
    if (key == null) {
      return '';
    }
    return [
      key.method,
      key.uri == null ? '' : _canonicalUri(key.uri!),
      key.iv ?? '',
      key.keyFormat ?? '',
      key.keyFormatVersions ?? '',
    ].join(',');
  }

  String _canonicalMap(HlsInitializationMap? map) {
    if (map == null) {
      return '';
    }
    return '${_canonicalUri(map.uri)},${_canonicalByteRange(map.byteRange)}';
  }

  String _canonicalByteRange(HlsByteRange? range) =>
      range == null ? '' : '${range.length}@${range.offset ?? -1}';

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
