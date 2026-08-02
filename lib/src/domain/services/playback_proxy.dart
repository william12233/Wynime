import 'dart:convert';

import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';

enum LoopbackAddressFamily { ipv4, ipv6 }

final class PlaybackProxyBudget {
  PlaybackProxyBudget({
    required this.maxPlaylistBytes,
    required this.maxResponseBytes,
    required this.maxRequestHeaderBytes,
    required this.maxCookieBytes,
    required this.maxRedirects,
    required this.maxRegisteredResources,
    required this.upstreamTimeout,
  }) {
    _range(maxPlaylistBytes, 'maxPlaylistBytes', 1024, 8 * 1024 * 1024);
    _range(maxResponseBytes, 'maxResponseBytes', 1024, 2 * 1024 * 1024 * 1024);
    _range(maxRequestHeaderBytes, 'maxRequestHeaderBytes', 0, 256 * 1024);
    _range(maxCookieBytes, 'maxCookieBytes', 0, 256 * 1024);
    _range(maxRedirects, 'maxRedirects', 0, 10);
    _range(maxRegisteredResources, 'maxRegisteredResources', 1, 100000);
    if (upstreamTimeout < const Duration(seconds: 1) ||
        upstreamTimeout > const Duration(minutes: 5)) {
      throw ArgumentError.value(
        upstreamTimeout,
        'upstreamTimeout',
        'Must be between 1 second and 5 minutes.',
      );
    }
  }

  final int maxPlaylistBytes;
  final int maxResponseBytes;
  final int maxRequestHeaderBytes;
  final int maxCookieBytes;
  final int maxRedirects;
  final int maxRegisteredResources;
  final Duration upstreamTimeout;
}

final class PlaybackProxyRequest {
  PlaybackProxyRequest({
    required this.session,
    required this.securityPolicy,
    required this.budget,
    this.addressFamily = LoopbackAddressFamily.ipv4,
  }) {
    if (!securityPolicy.allowsUri(session.mediaUri)) {
      throw ArgumentError.value(
        session.mediaUri,
        'session',
        'The session media URI is outside the source allowlist.',
      );
    }
    if (_effectiveHeaderBytes(session) > budget.maxRequestHeaderBytes) {
      throw ArgumentError(
        'PlaybackSession headers exceed the proxy request-header budget.',
      );
    }
    if (_cookieBytes(session.cookies) > budget.maxCookieBytes) {
      throw ArgumentError(
        'PlaybackSession cookies exceed the proxy cookie budget.',
      );
    }
  }

  final PlaybackSession session;
  final SourceSecurityPolicy securityPolicy;
  final PlaybackProxyBudget budget;
  final LoopbackAddressFamily addressFamily;
}

final class PlaybackProxyLease {
  factory PlaybackProxyLease({
    required String sessionId,
    required Uri playbackUri,
    required Future<void> Function() close,
  }) => PlaybackProxyLease._(_requiredSessionId(sessionId), playbackUri, close);

  PlaybackProxyLease._(this.sessionId, this.playbackUri, this._close) {
    if (!_isNumericLoopback(playbackUri)) {
      throw ArgumentError.value(
        playbackUri,
        'playbackUri',
        'Proxy endpoint must use a numeric loopback host and explicit port.',
      );
    }
  }

  final String sessionId;
  final Uri playbackUri;
  final Future<void> Function() _close;
  bool _closed = false;

  bool get isClosed => _closed;

  Future<void> close() async {
    if (_closed) {
      return;
    }
    _closed = true;
    await _close();
  }
}

abstract interface class PlaybackProxyService {
  Future<PlaybackProxyLease> expose(PlaybackProxyRequest request);

  Future<void> close();
}

String _requiredSessionId(String value) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.length > 128) {
    throw ArgumentError.value(value, 'sessionId', 'Invalid session identity.');
  }
  return normalized;
}

bool _isNumericLoopback(Uri uri) =>
    uri.scheme == 'http' &&
    uri.userInfo.isEmpty &&
    uri.hasPort &&
    uri.port > 0 &&
    !uri.hasQuery &&
    !uri.hasFragment &&
    (uri.host == '127.0.0.1' || uri.host == '::1');

int _effectiveHeaderBytes(PlaybackSession session) {
  final values = <String, String>{...session.headers};
  if (session.referer != null) {
    values['referer'] = session.referer.toString();
  }
  if (session.origin != null) {
    values['origin'] = session.origin.toString();
  }
  if (session.userAgent != null) {
    values['user-agent'] = session.userAgent!;
  }
  if (session.cookies.isNotEmpty) {
    values['cookie'] = session.cookies.entries
        .map((entry) => '${entry.key}=${entry.value}')
        .join('; ');
  }
  values['accept-encoding'] = 'identity';
  return _headerBytes(values);
}

int _headerBytes(Map<String, String> values) => values.entries.fold(
  0,
  (total, entry) =>
      total +
      utf8.encode(entry.key).length +
      utf8.encode(entry.value).length +
      4,
);

int _cookieBytes(Map<String, String> values) => values.entries.fold(
  0,
  (total, entry) =>
      total +
      utf8.encode(entry.key).length +
      utf8.encode(entry.value).length +
      2,
);

void _range(int value, String name, int minimum, int maximum) {
  if (value < minimum || value > maximum) {
    throw ArgumentError.value(
      value,
      name,
      'Must be between $minimum and $maximum.',
    );
  }
}
