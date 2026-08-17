import 'dart:collection';
import 'dart:convert';

import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

typedef PlaybackSessionRefresher = Future<PlaybackSession> Function();

final class MediaTrack {
  MediaTrack({
    required String id,
    required String label,
    this.uri,
    String? languageCode,
    String? mimeType,
    this.isDefault = false,
  }) : id = _requiredText(id, 'id', 128),
       label = _requiredText(label, 'label', 256),
       languageCode = _optionalLanguageCode(languageCode),
       mimeType = _optionalMimeType(mimeType) {
    if (uri != null) {
      _safeRemoteUri(uri!, 'uri');
    }
  }

  final String id;
  final String label;
  final Uri? uri;
  final String? languageCode;
  final String? mimeType;
  final bool isDefault;
}

final class PlaybackSession {
  PlaybackSession({
    required String sessionId,
    required this.episode,
    required Uri mediaUri,
    required Uri pageUri,
    required this.adRemovalPlan,
    Uri? playbackUri,
    Map<String, String> headers = const {},
    Map<String, String> cookies = const {},
    Uri? referer,
    Uri? origin,
    String? userAgent,
    DateTime? expiresAt,
    Iterable<MediaTrack> subtitles = const [],
    Iterable<MediaTrack> audioTracks = const [],
    this.refresh,
  }) : sessionId = _requiredToken(sessionId, 'sessionId', 128),
       mediaUri = _safeRemoteUri(mediaUri, 'mediaUri'),
       pageUri = _safeRemoteUri(pageUri, 'pageUri'),
       playbackUri = playbackUri == null
           ? null
           : _safePlaybackUri(playbackUri, 'playbackUri'),
       headers = _freezeHeaders(headers),
       cookies = _freezeCookies(cookies),
       referer = referer == null ? null : _safeRemoteUri(referer, 'referer'),
       origin = origin == null ? null : _safeOrigin(origin),
       userAgent = _optionalHeaderValue(userAgent, 'userAgent', 1024),
       expiresAt = expiresAt?.toUtc(),
       subtitles = UnmodifiableListView(
         List<MediaTrack>.unmodifiable(subtitles),
       ),
       audioTracks = UnmodifiableListView(
         List<MediaTrack>.unmodifiable(audioTracks),
       ) {
    if (adRemovalPlan.key.episode != episode) {
      throw ArgumentError(
        'AdRemovalPlan must describe the same source, line, subject, and episode.',
      );
    }
    _validateTrackIds(this.subtitles, this.audioTracks);
  }

  final String sessionId;
  final SourceEpisodeIdentity episode;

  /// Authoritative upstream media URI captured from the selected source.
  final Uri mediaUri;

  /// Source page used to resolve this session.
  final Uri pageUri;

  /// Player handoff endpoint. HLS and direct resources are both exposed
  /// through the loopback proxy before reaching a native player.
  final Uri? playbackUri;

  final UnmodifiableMapView<String, String> headers;
  final UnmodifiableMapView<String, String> cookies;
  final Uri? referer;
  final Uri? origin;
  final String? userAgent;
  final DateTime? expiresAt;
  final UnmodifiableListView<MediaTrack> subtitles;
  final UnmodifiableListView<MediaTrack> audioTracks;
  final AdRemovalPlan adRemovalPlan;
  final PlaybackSessionRefresher? refresh;

  Uri get effectivePlaybackUri => playbackUri ?? mediaUri;

  String get timelineMapIdentity =>
      '${episode.sourceId}:${episode.lineId}:${episode.subjectId}:'
      '${episode.episodeId}:${adRemovalPlan.key.manifestFingerprint}';

  bool get isExpired => isExpiredAt(DateTime.now().toUtc());

  bool isExpiredAt(DateTime now) =>
      expiresAt != null && !expiresAt!.isAfter(now.toUtc());

  bool expiresWithin(Duration window, {DateTime? now}) {
    if (window.isNegative) {
      throw ArgumentError.value(window, 'window', 'Must not be negative.');
    }
    final expiry = expiresAt;
    if (expiry == null) {
      return false;
    }
    final current = (now ?? DateTime.now()).toUtc();
    return !expiry.isAfter(current.add(window));
  }

  PlaybackSession withPlaybackUri(Uri value) => PlaybackSession(
    sessionId: sessionId,
    episode: episode,
    mediaUri: mediaUri,
    pageUri: pageUri,
    playbackUri: value,
    headers: headers,
    cookies: cookies,
    referer: referer,
    origin: origin,
    userAgent: userAgent,
    expiresAt: expiresAt,
    subtitles: subtitles,
    audioTracks: audioTracks,
    adRemovalPlan: adRemovalPlan,
    refresh: refresh,
  );

  Future<PlaybackSession> refreshed() async {
    final refresher = refresh;
    if (refresher == null) {
      throw StateError('PlaybackSession does not provide a refresh callback.');
    }
    final next = await refresher();
    if (next.sessionId != sessionId || next.episode != episode) {
      throw StateError(
        'A refreshed PlaybackSession must preserve session and episode identity.',
      );
    }
    return next;
  }

  Map<String, Object?> toRedactedDiagnostic() => {
    'sessionId': sessionId,
    'episode': {
      'sourceId': episode.sourceId,
      'lineId': episode.lineId,
      'subjectId': episode.subjectId,
      'episodeId': episode.episodeId,
    },
    'mediaScheme': mediaUri.scheme,
    'mediaHost': mediaUri.host,
    'mediaPathSegmentCount': mediaUri.pathSegments.length,
    'pageHost': pageUri.host,
    'headerNames': headers.keys.toList(growable: false),
    'cookieNames': cookies.keys.toList(growable: false),
    'hasPlaybackUri': playbackUri != null,
    'expiresAt': expiresAt?.toIso8601String(),
    'subtitleCount': subtitles.length,
    'audioTrackCount': audioTracks.length,
    'timelineMapIdentity': timelineMapIdentity,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

void _validateTrackIds(
  Iterable<MediaTrack> subtitles,
  Iterable<MediaTrack> audioTracks,
) {
  final ids = <String>{};
  for (final track in [...subtitles, ...audioTracks]) {
    if (!ids.add(track.id)) {
      throw ArgumentError.value(
        track.id,
        'tracks',
        'Track identifiers must be unique within a PlaybackSession.',
      );
    }
  }
}

UnmodifiableMapView<String, String> _freezeHeaders(Map<String, String> values) {
  final result = <String, String>{};
  const forbidden = {
    'connection',
    'content-length',
    'host',
    'proxy-authorization',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade',
  };
  var encodedBytes = 0;
  for (final entry in values.entries) {
    final name = _requiredToken(entry.key, 'headerName', 128).toLowerCase();
    if (forbidden.contains(name)) {
      throw ArgumentError.value(entry.key, 'headerName', 'Hop-by-hop header.');
    }
    final value = _requiredHeaderValue(entry.value, 'headerValue', 16 * 1024);
    encodedBytes += utf8.encode(name).length + utf8.encode(value).length + 4;
    if (encodedBytes > 256 * 1024) {
      throw ArgumentError('PlaybackSession headers exceed 256 KiB.');
    }
    result[name] = value;
  }
  return UnmodifiableMapView(Map<String, String>.unmodifiable(result));
}

UnmodifiableMapView<String, String> _freezeCookies(Map<String, String> values) {
  final result = <String, String>{};
  var encodedBytes = 0;
  for (final entry in values.entries) {
    final name = _requiredToken(entry.key, 'cookieName', 256);
    final value = _cookieValue(entry.value);
    encodedBytes += utf8.encode(name).length + utf8.encode(value).length + 2;
    if (encodedBytes > 256 * 1024) {
      throw ArgumentError('PlaybackSession cookies exceed 256 KiB.');
    }
    result[name] = value;
  }
  return UnmodifiableMapView(Map<String, String>.unmodifiable(result));
}

Uri _safeRemoteUri(Uri value, String name) {
  final scheme = value.scheme.toLowerCase();
  if ((scheme != 'https' && scheme != 'http') ||
      value.host.isEmpty ||
      value.userInfo.isNotEmpty) {
    throw ArgumentError.value(
      value,
      name,
      'Must be an HTTP(S) URI without user-info.',
    );
  }
  return value;
}

Uri _safePlaybackUri(Uri value, String name) {
  _safeRemoteUri(value, name);
  if (value.scheme != 'http' ||
      (value.host != '127.0.0.1' && value.host != '::1') ||
      !value.hasPort ||
      value.port <= 0 ||
      value.hasQuery ||
      value.hasFragment) {
    throw ArgumentError.value(
      value,
      name,
      'Must be a numeric HTTP loopback URI with an explicit port.',
    );
  }
  return value;
}

Uri _safeOrigin(Uri value) {
  _safeRemoteUri(value, 'origin');
  if ((value.path.isNotEmpty && value.path != '/') ||
      value.hasQuery ||
      value.hasFragment) {
    throw ArgumentError.value(
      value,
      'origin',
      'Origin must contain only scheme and authority.',
    );
  }
  return value;
}

String _requiredText(String value, String name, int maxLength) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.length > maxLength) {
    throw ArgumentError.value(
      value,
      name,
      'Must contain between 1 and $maxLength characters.',
    );
  }
  if (_containsControlCharacters(normalized)) {
    throw ArgumentError.value(value, name, 'Must not contain controls.');
  }
  return normalized;
}

String _requiredToken(String value, String name, int maxLength) {
  final normalized = _requiredText(value, name, maxLength);
  if (!RegExp(r"^[!#$%&'*+.^_`|~0-9A-Za-z-]+$").hasMatch(normalized)) {
    throw ArgumentError.value(value, name, 'Must be a valid token.');
  }
  return normalized;
}

String? _optionalToken(String? value, String name, int maxLength) {
  if (value == null) {
    return null;
  }
  return _requiredToken(value, name, maxLength);
}

String? _optionalMimeType(String? value) {
  if (value == null) {
    return null;
  }
  final normalized = value.trim().toLowerCase();
  if (normalized.isEmpty ||
      normalized.length > 128 ||
      !RegExp(
        r"^[!#$%&'*+.^_`|~0-9a-z-]+/[!#$%&'*+.^_`|~0-9a-z-]+$",
      ).hasMatch(normalized)) {
    throw ArgumentError.value(
      value,
      'mimeType',
      'Must be a valid type/subtype MIME value.',
    );
  }
  return normalized;
}

String? _optionalHeaderValue(String? value, String name, int maxLength) {
  if (value == null) {
    return null;
  }
  return _requiredHeaderValue(value, name, maxLength);
}

String _requiredHeaderValue(String value, String name, int maxLength) {
  final normalized = value.trim();
  if (normalized.isEmpty || utf8.encode(normalized).length > maxLength) {
    throw ArgumentError.value(
      value,
      name,
      'Must be non-empty and at most $maxLength UTF-8 bytes.',
    );
  }
  if (_containsControlCharacters(normalized)) {
    throw ArgumentError.value(value, name, 'Must not contain controls.');
  }
  return normalized;
}

String _cookieValue(String value) {
  if (utf8.encode(value).length > 16 * 1024 ||
      _containsControlCharacters(value)) {
    throw ArgumentError.value(
      value,
      'cookieValue',
      'Must be at most 16384 UTF-8 bytes without controls.',
    );
  }
  return value;
}

String? _optionalLanguageCode(String? value) {
  if (value == null) {
    return null;
  }
  final normalized = value.trim();
  if (!RegExp(r'^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'languageCode', 'Invalid language tag.');
  }
  return normalized;
}

bool _containsControlCharacters(String value) =>
    value.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f);
