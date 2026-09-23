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
  final values = playbackUpstreamHeaders(session, requestUri: session.mediaUri);
  return _headerBytes(values);
}

/// Builds one safe upstream request shape for the exact redirect hop.
///
/// The caller must invoke this for every hop. Captured Referer, Origin and
/// User-Agent are used only when they were actually observed and requested by
/// the caller; Origin is never synthesized from the page URI. Cookie scope is
/// evaluated against the current URI, while legacy unscoped cookies are only
/// retained on the original authority.
Map<String, String> playbackUpstreamHeaders(
  PlaybackSession session, {
  required Uri requestUri,
  String? range,
  bool includeReferer = true,
  bool includeOrigin = true,
  bool includeUserAgent = true,
  bool includeCookies = true,
}) {
  final headers = <String, String>{};
  final sameAuthority = _sameAuthority(session.mediaUri, requestUri);
  final sessionHeaders = <String, String>{...session.headers};
  for (final entry in sessionHeaders.entries) {
    final name = entry.key.toLowerCase();
    if (name == 'cookie' ||
        name == 'range' ||
        name == 'referer' ||
        name == 'origin' ||
        name == 'user-agent') {
      continue;
    }
    if (!sameAuthority && _isSensitiveRedirectHeader(name)) {
      continue;
    }
    headers[name] = entry.value;
  }
  headers['accept-encoding'] = 'identity';
  if (includeReferer && session.referer != null) {
    headers['referer'] = session.referer.toString();
  }
  if (includeOrigin && session.origin != null) {
    headers['origin'] = session.origin.toString();
  }
  if (includeUserAgent && session.userAgent != null) {
    headers['user-agent'] = session.userAgent!;
  }
  if (includeCookies) {
    final cookieHeader = _cookieHeaderForUri(session, requestUri);
    if (cookieHeader != null) {
      headers['cookie'] = cookieHeader;
    }
  }
  if (range != null) {
    if (!RegExp(r'^bytes=\d*\-\d*$').hasMatch(range.trim()) ||
        !range.trim().substring(6).contains('-')) {
      throw ArgumentError.value(
        range,
        'range',
        'Must be one bounded byte range.',
      );
    }
    headers['range'] = range.trim();
  }
  return headers;
}

bool _sameAuthority(Uri left, Uri right) =>
    left.scheme.toLowerCase() == right.scheme.toLowerCase() &&
    left.host.toLowerCase() == right.host.toLowerCase() &&
    _effectivePort(left) == _effectivePort(right);

int _effectivePort(Uri uri) =>
    uri.hasPort ? uri.port : (uri.scheme.toLowerCase() == 'https' ? 443 : 80);

bool _isSensitiveRedirectHeader(String name) =>
    name == 'authorization' ||
    name == 'proxy-authorization' ||
    name == 'x-api-key' ||
    name == 'x-auth-token' ||
    name == 'x-access-token' ||
    name == 'x-csrf-token';

String? _cookieHeaderForUri(PlaybackSession session, Uri requestUri) {
  final cookies = <String, String>{};
  final metadata = session.cookieMetadata;
  if (metadata.isNotEmpty) {
    final now = DateTime.now().toUtc();
    for (final cookie in metadata) {
      if (cookie.expiresAt != null && !cookie.expiresAt!.toUtc().isAfter(now)) {
        continue;
      }
      if (cookie.isSecure && requestUri.scheme != 'https') continue;
      if (!_cookieDomainMatches(requestUri.host, cookie.domain) ||
          !_cookiePathMatches(requestUri.path, cookie.path)) {
        continue;
      }
      cookies[cookie.name] = cookie.value;
    }
  } else if (_sameAuthority(session.mediaUri, requestUri)) {
    cookies.addAll(session.cookies);
  }
  if (cookies.isEmpty) return null;
  return cookies.entries
      .map((entry) => '${entry.key}=${entry.value}')
      .join('; ');
}

bool _cookieDomainMatches(String host, String domain) {
  final normalizedHost = host.toLowerCase();
  final normalizedDomain = domain.toLowerCase();
  return normalizedHost == normalizedDomain ||
      normalizedHost.endsWith('.$normalizedDomain');
}

bool _cookiePathMatches(String requestPath, String cookiePath) {
  final request = requestPath.isEmpty ? '/' : requestPath;
  if (request == cookiePath) return true;
  if (!request.startsWith(cookiePath)) return false;
  return cookiePath.endsWith('/') ||
      (request.length > cookiePath.length &&
          request.codeUnitAt(cookiePath.length) == 0x2f);
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
