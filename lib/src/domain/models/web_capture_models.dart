import 'dart:collection';
import 'dart:convert';

import 'source_security_policy.dart';

enum WebCapturePlatform { androidWebView, windowsWebView2 }

enum WebCaptureRuntimeState { available, unavailable, unsupported }

enum WebUserAgentMode { platformDefault, desktop }

enum WebRequestKind { navigation, iframe, resource, xmlHttpRequest, fetch }

enum WebCandidateKind { hls, dash, video, audio, mediaSegment }

enum WebCaptureStopReason {
  completed,
  eventBudgetExceeded,
  candidateBudgetExceeded,
  headerBudgetExceeded,
  redirectBudgetExceeded,
}

final class WebCaptureRuntimeStatus {
  WebCaptureRuntimeStatus({
    required this.state,
    this.platform,
    String? runtimeVersion,
    String? reasonCode,
  }) : runtimeVersion = _optionalText(runtimeVersion, 'runtimeVersion', 128),
       reasonCode = _optionalToken(reasonCode, 'reasonCode', 64) {
    if (state == WebCaptureRuntimeState.available && platform == null) {
      throw ArgumentError('Available runtime status requires a platform.');
    }
    if (state != WebCaptureRuntimeState.available && this.reasonCode == null) {
      throw ArgumentError('Unavailable runtime status requires a reasonCode.');
    }
  }

  final WebCaptureRuntimeState state;
  final WebCapturePlatform? platform;
  final String? runtimeVersion;
  final String? reasonCode;

  bool get isAvailable => state == WebCaptureRuntimeState.available;

  @override
  String toString() =>
      'WebCaptureRuntimeStatus(state: $state, platform: $platform, '
      'runtimeVersion: $runtimeVersion, reasonCode: $reasonCode)';
}

final class WebCaptureBudget {
  WebCaptureBudget({
    required this.maxEvents,
    required this.maxCandidates,
    required this.maxHeaderBytes,
    required this.maxCookieBytes,
  }) {
    _range(maxEvents, 'maxEvents', 1, 5000);
    _range(maxCandidates, 'maxCandidates', 1, 1000);
    _range(maxHeaderBytes, 'maxHeaderBytes', 0, 256 * 1024);
    _range(maxCookieBytes, 'maxCookieBytes', 0, 256 * 1024);
  }

  final int maxEvents;
  final int maxCandidates;
  final int maxHeaderBytes;
  final int maxCookieBytes;
}

final class WebUserAgentPolicy {
  WebUserAgentPolicy({required this.mode, String? value})
    : value = _optionalText(value, 'value', 512) {
    if (mode == WebUserAgentMode.desktop &&
        (this.value == null || this.value!.length < 16)) {
      throw ArgumentError(
        'Desktop user-agent mode requires an explicit value of at least 16 characters.',
      );
    }
    if (this.value != null && _containsControlCharacters(this.value!)) {
      throw ArgumentError.value(
        value,
        'value',
        'Must not contain control characters.',
      );
    }
  }

  final WebUserAgentMode mode;
  final String? value;
}

final class WebCaptureCookie {
  WebCaptureCookie({
    required String name,
    required String value,
    required String domain,
    String path = '/',
    this.isSecure = true,
    this.isHttpOnly = false,
    this.expiresAt,
  }) : name = _requiredToken(name, 'name', 256),
       value = _cookieValue(value),
       domain = _cookieDomain(domain),
       path = _cookiePath(path);

  final String name;
  final String value;
  final String domain;
  final String path;
  final bool isSecure;
  final bool isHttpOnly;
  final DateTime? expiresAt;

  int get encodedBytes =>
      _utf8Bytes(name) +
      _utf8Bytes(value) +
      _utf8Bytes(domain) +
      _utf8Bytes(path) +
      16;

  @override
  String toString() =>
      'WebCaptureCookie(name: $name, domain: $domain, path: $path, '
      'isSecure: $isSecure, isHttpOnly: $isHttpOnly, value: <redacted>)';
}

final class WebCaptureRequest {
  WebCaptureRequest({
    required this.initialUri,
    required this.securityPolicy,
    required this.budget,
    required this.userAgentPolicy,
    required this.captureMediaRequests,
    Map<String, String> initialHeaders = const {},
    Iterable<WebCaptureCookie> initialCookies = const [],
  }) : initialHeaders = _freezeHeaders(initialHeaders),
       initialCookies = UnmodifiableListView(
         List<WebCaptureCookie>.unmodifiable(initialCookies),
       ) {
    if (!securityPolicy.permissions.contains(SourcePermission.webView)) {
      throw ArgumentError('The webView permission is required.');
    }
    if (!securityPolicy.allowsUri(initialUri)) {
      throw ArgumentError.value(
        initialUri,
        'initialUri',
        'URI is outside the source allowlist.',
      );
    }
    if (userAgentPolicy.mode == WebUserAgentMode.desktop &&
        !securityPolicy.permissions.contains(
          SourcePermission.desktopUserAgent,
        )) {
      throw ArgumentError('The desktopUserAgent permission is required.');
    }
    if (captureMediaRequests &&
        !securityPolicy.permissions.contains(
          SourcePermission.mediaRequestInspection,
        )) {
      throw ArgumentError('The mediaRequestInspection permission is required.');
    }
    if (this.initialCookies.isNotEmpty &&
        !securityPolicy.permissions.contains(SourcePermission.cookies)) {
      throw ArgumentError('The cookies permission is required.');
    }
    if (_headerBytes(this.initialHeaders) > budget.maxHeaderBytes) {
      throw ArgumentError('Initial headers exceed maxHeaderBytes.');
    }
    if (_cookieBytes(this.initialCookies) > budget.maxCookieBytes) {
      throw ArgumentError('Initial cookies exceed maxCookieBytes.');
    }
    for (final cookie in this.initialCookies) {
      if (!_policyCoversCookieDomain(securityPolicy, cookie.domain)) {
        throw ArgumentError.value(
          cookie.domain,
          'initialCookies',
          'Cookie domain is outside the source allowlist.',
        );
      }
    }
  }

  final Uri initialUri;
  final SourceSecurityPolicy securityPolicy;
  final WebCaptureBudget budget;
  final WebUserAgentPolicy userAgentPolicy;
  final bool captureMediaRequests;
  final UnmodifiableMapView<String, String> initialHeaders;
  final UnmodifiableListView<WebCaptureCookie> initialCookies;

  @override
  String toString() =>
      'WebCaptureRequest(initialHost: ${initialUri.host}, '
      'headerNames: ${initialHeaders.keys.toList()}, '
      'cookieCount: ${initialCookies.length}, captureMediaRequests: '
      '$captureMediaRequests)';
}

final class WebCaptureEvent {
  WebCaptureEvent({
    required this.sequence,
    required this.kind,
    required Uri uri,
    String method = 'GET',
    Map<String, String> headers = const {},
    this.isMainFrame = false,
    this.isRedirect = false,
  }) : uri = _safeHttpUri(uri),
       method = _httpMethod(method),
       headers = _freezeHeaders(headers) {
    if (sequence < 0) {
      throw ArgumentError.value(sequence, 'sequence', 'Must not be negative.');
    }
  }

  final int sequence;
  final WebRequestKind kind;
  final Uri uri;
  final String method;
  final UnmodifiableMapView<String, String> headers;
  final bool isMainFrame;
  final bool isRedirect;

  Map<String, Object?> toRedactedDiagnostic() => {
    'sequence': sequence,
    'kind': kind.name,
    'scheme': uri.scheme,
    'host': uri.host,
    'port': uri.hasPort ? uri.port : null,
    'pathSegmentCount': uri.pathSegments.length,
    'method': method,
    'headerNames': headers.keys.toList(growable: false),
    'isMainFrame': isMainFrame,
    'isRedirect': isRedirect,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

final class WebMediaCandidate {
  WebMediaCandidate({
    required this.kind,
    required Uri uri,
    required Map<String, String> headers,
    required this.sourceEventSequence,
  }) : uri = _safeHttpUri(uri),
       headers = _freezeHeaders(headers) {
    if (sourceEventSequence < 0) {
      throw ArgumentError.value(
        sourceEventSequence,
        'sourceEventSequence',
        'Must not be negative.',
      );
    }
  }

  final WebCandidateKind kind;
  final Uri uri;
  final UnmodifiableMapView<String, String> headers;
  final int sourceEventSequence;

  Map<String, Object?> toRedactedDiagnostic() => {
    'kind': kind.name,
    'scheme': uri.scheme,
    'host': uri.host,
    'pathSegmentCount': uri.pathSegments.length,
    'headerNames': headers.keys.toList(growable: false),
    'sourceEventSequence': sourceEventSequence,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

final class WebCaptureSnapshot {
  WebCaptureSnapshot({
    required Iterable<WebCaptureEvent> events,
    required Iterable<WebMediaCandidate> candidates,
    required Iterable<WebCaptureCookie> cookies,
    required this.stopReason,
    required this.finalUri,
  }) : events = UnmodifiableListView(
         List<WebCaptureEvent>.unmodifiable(events),
       ),
       candidates = UnmodifiableListView(
         List<WebMediaCandidate>.unmodifiable(candidates),
       ),
       cookies = UnmodifiableListView(
         List<WebCaptureCookie>.unmodifiable(cookies),
       );

  final UnmodifiableListView<WebCaptureEvent> events;
  final UnmodifiableListView<WebMediaCandidate> candidates;
  final UnmodifiableListView<WebCaptureCookie> cookies;
  final WebCaptureStopReason stopReason;
  final Uri finalUri;

  @override
  String toString() =>
      'WebCaptureSnapshot(finalHost: ${finalUri.host}, '
      'events: ${events.length}, candidates: ${candidates.length}, '
      'cookies: ${cookies.length}, stopReason: $stopReason)';
}

final class WebCaptureSecurityException implements Exception {
  WebCaptureSecurityException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'WebCaptureSecurityException($code): $message';
}

UnmodifiableMapView<String, String> _freezeHeaders(Map<String, String> values) {
  final result = <String, String>{};
  for (final entry in values.entries) {
    final name = _requiredToken(entry.key, 'headerName', 128).toLowerCase();
    final value = entry.value.trim();
    if (_utf8Bytes(value) > 16 * 1024 || _containsControlCharacters(value)) {
      throw ArgumentError.value(
        entry.value,
        'headerValue',
        'Must be at most 16384 UTF-8 bytes without controls.',
      );
    }
    result[name] = value;
  }
  return UnmodifiableMapView(Map<String, String>.unmodifiable(result));
}

int webCaptureHeaderBytes(Map<String, String> values) => _headerBytes(values);

int webCaptureCookieBytes(Iterable<WebCaptureCookie> values) =>
    _cookieBytes(values);

bool webCapturePolicyCoversCookieDomain(
  SourceSecurityPolicy policy,
  String domain,
) => _policyCoversCookieDomain(policy, domain);

int _headerBytes(Map<String, String> values) => values.entries.fold(
  0,
  (total, entry) => total + _utf8Bytes(entry.key) + _utf8Bytes(entry.value) + 4,
);

int _cookieBytes(Iterable<WebCaptureCookie> values) =>
    values.fold(0, (total, cookie) => total + cookie.encodedBytes);

bool _policyCoversCookieDomain(SourceSecurityPolicy policy, String domain) {
  final normalized = domain.toLowerCase();
  return policy.allowedDomains.any(
    (rule) =>
        normalized == rule.host ||
        (rule.includeSubdomains && normalized.endsWith('.${rule.host}')),
  );
}

String _cookieDomain(String value) {
  var normalized = value.trim().toLowerCase();
  if (normalized.startsWith('.')) {
    normalized = normalized.substring(1);
  }
  SourceDomainRule(host: normalized);
  return normalized;
}

String _cookieValue(String value) {
  if (_utf8Bytes(value) > 4096 || _containsControlCharacters(value)) {
    throw ArgumentError.value(
      value,
      'value',
      'Must be at most 4096 UTF-8 bytes without controls.',
    );
  }
  return value;
}

String _cookiePath(String value) {
  final path = value.trim();
  if (!path.startsWith('/') ||
      _utf8Bytes(path) > 2048 ||
      _containsControlCharacters(path)) {
    throw ArgumentError.value(
      value,
      'path',
      'Must be a safe absolute path of at most 2048 UTF-8 bytes.',
    );
  }
  return path;
}

Uri _safeHttpUri(Uri value) {
  if (!value.hasScheme ||
      (value.scheme != 'https' && value.scheme != 'http') ||
      value.host.isEmpty ||
      value.userInfo.isNotEmpty) {
    throw ArgumentError.value(value, 'uri', 'Must be a safe HTTP(S) URI.');
  }
  return value;
}

String _httpMethod(String value) {
  final method = value.trim().toUpperCase();
  if (!RegExp(r'^[A-Z]{1,16}$').hasMatch(method)) {
    throw ArgumentError.value(value, 'method', 'Must be an HTTP method token.');
  }
  return method;
}

String _requiredToken(String value, String name, int maxLength) {
  final trimmed = value.trim();
  if (trimmed.isEmpty ||
      trimmed.length > maxLength ||
      !RegExp(r"^[!#$%&'*+.^_`|~0-9A-Za-z-]+$").hasMatch(trimmed)) {
    throw ArgumentError.value(value, name, 'Must be a valid token.');
  }
  return trimmed;
}

String? _optionalToken(String? value, String name, int maxLength) {
  if (value == null) {
    return null;
  }
  return _requiredToken(value, name, maxLength);
}

String? _optionalText(String? value, String name, int maxLength) {
  if (value == null) {
    return null;
  }
  final trimmed = value.trim();
  if (trimmed.isEmpty || trimmed.length > maxLength) {
    throw ArgumentError.value(value, name, 'Must be non-empty and bounded.');
  }
  return trimmed;
}

int _utf8Bytes(String value) => utf8.encode(value).length;

bool _containsControlCharacters(String value) =>
    value.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f);

void _range(int value, String name, int minimum, int maximum) {
  if (value < minimum || value > maximum) {
    throw ArgumentError.value(
      value,
      name,
      'Must be between $minimum and $maximum.',
    );
  }
}
