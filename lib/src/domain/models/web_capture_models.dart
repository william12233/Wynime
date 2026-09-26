import 'dart:collection';
import 'dart:convert';

import 'source_security_policy.dart';

enum WebCapturePlatform { androidWebView, windowsWebView2 }

enum WebCaptureRuntimeState { available, unavailable, unsupported }

enum WebUserAgentMode { platformDefault, desktop }

/// Controls when an interactive capture is considered complete.
///
/// [loadStop] preserves the original one-shot navigation behavior. The
/// candidate policy is for public players whose media request is created only
/// after the page has loaded and the user has interacted with the player.
enum WebCaptureCompletionPolicy {
  loadStop,
  firstPlayableCandidateAfterLoad,

  /// Waits for the bounded capture window and returns every validated
  /// playable candidate observed after load. Consumers can then rank and
  /// probe candidates without stopping at a wrapper or redirector.
  firstValidatedPlayableCandidateAfterLoad,
  documentAfterLoad,
}

enum WebRequestKind { navigation, iframe, resource, xmlHttpRequest, fetch }

enum WebCandidateKind { hls, dash, video, audio, mediaSegment }

/// One short-lived exact-origin authority derived from a browser request in a
/// single acquisition. It is never part of a source package or persisted.
final class RuntimeMediaOriginGrant {
  RuntimeMediaOriginGrant({
    required String acquisitionId,
    required Uri origin,
    required this.sourceEventSequence,
    required DateTime expiresAt,
  }) : acquisitionId = _requiredToken(acquisitionId, 'acquisitionId', 128),
       origin = _origin(origin),
       expiresAt = expiresAt.toUtc() {
    if (sourceEventSequence < 0) {
      throw ArgumentError.value(sourceEventSequence, 'sourceEventSequence');
    }
  }

  final String acquisitionId;
  final Uri origin;
  final int sourceEventSequence;
  final DateTime expiresAt;

  bool allows(Uri uri, {required String acquisitionId, DateTime? now}) {
    final current = (now ?? DateTime.now()).toUtc();
    return this.acquisitionId == acquisitionId &&
        expiresAt.isAfter(current) &&
        uri.userInfo.isEmpty &&
        uri.scheme == origin.scheme &&
        uri.host.toLowerCase() == origin.host.toLowerCase() &&
        _effectivePort(uri) == _effectivePort(origin);
  }

  bool coversCookieDomain(String domain, {required String acquisitionId}) =>
      this.acquisitionId == acquisitionId &&
      expiresAt.isAfter(DateTime.now().toUtc()) &&
      domain.toLowerCase() == origin.host.toLowerCase();
}

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
    this.captureDocument = false,
    this.completionPolicy = WebCaptureCompletionPolicy.loadStop,
    this.postLoadTimeout = const Duration(seconds: 15),
    Map<String, String> initialHeaders = const {},
    Iterable<WebCaptureCookie> initialCookies = const [],
    this.allowRuntimeMediaOrigins = false,
    String? acquisitionId,
  }) : initialHeaders = _freezeHeaders(initialHeaders),
       acquisitionId = _optionalToken(acquisitionId, 'acquisitionId', 128),
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
    if (allowRuntimeMediaOrigins &&
        (!captureMediaRequests || this.acquisitionId == null)) {
      throw ArgumentError(
        'Runtime media origins require capture and an acquisition identity.',
      );
    }
    if ((completionPolicy ==
                WebCaptureCompletionPolicy.firstPlayableCandidateAfterLoad ||
            completionPolicy ==
                WebCaptureCompletionPolicy
                    .firstValidatedPlayableCandidateAfterLoad) &&
        !captureMediaRequests) {
      throw ArgumentError(
        'Candidate completion requires media-request capture.',
      );
    }
    if (completionPolicy == WebCaptureCompletionPolicy.documentAfterLoad &&
        !captureDocument) {
      throw ArgumentError('Document completion requires document capture.');
    }
    if (postLoadTimeout < const Duration(seconds: 1) ||
        postLoadTimeout > const Duration(seconds: 60)) {
      throw ArgumentError.value(
        postLoadTimeout,
        'postLoadTimeout',
        'Must be between one and sixty seconds.',
      );
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
  final bool captureDocument;
  final WebCaptureCompletionPolicy completionPolicy;
  final Duration postLoadTimeout;
  final UnmodifiableMapView<String, String> initialHeaders;
  final UnmodifiableListView<WebCaptureCookie> initialCookies;
  final bool allowRuntimeMediaOrigins;
  final String? acquisitionId;

  @override
  String toString() =>
      'WebCaptureRequest(initialHost: ${initialUri.host}, '
      'headerNames: ${initialHeaders.keys.toList()}, '
      'cookieCount: ${initialCookies.length}, captureMediaRequests: '
      '$captureMediaRequests, captureDocument: $captureDocument, '
      'completionPolicy: ${completionPolicy.name}, '
      'postLoadTimeoutMs: ${postLoadTimeout.inMilliseconds})';
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
    this.runtimeOriginValidated = false,
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
  final bool runtimeOriginValidated;

  WebCaptureEvent withRuntimeOriginValidated() => WebCaptureEvent(
    sequence: sequence,
    kind: kind,
    uri: uri,
    method: method,
    headers: headers,
    isMainFrame: isMainFrame,
    isRedirect: isRedirect,
    runtimeOriginValidated: true,
  );

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
    Uri? pageUri,
    String requestMethod = 'GET',
    this.isRedirect = false,
    Iterable<Uri> redirectChain = const [],
    this.runtimeOriginGrant,
  }) : uri = _safeHttpUri(uri),
       pageUri = pageUri == null ? null : _safeHttpUri(pageUri, 'pageUri'),
       requestMethod = _httpMethod(requestMethod),
       headers = _freezeHeaders(headers),
       redirectChain = UnmodifiableListView(
         List<Uri>.unmodifiable(
           redirectChain.map((value) => _safeHttpUri(value, 'redirectUri')),
         ),
       ) {
    if (sourceEventSequence < 0) {
      throw ArgumentError.value(
        sourceEventSequence,
        'sourceEventSequence',
        'Must not be negative.',
      );
    }
    if (this.redirectChain.length > 10) {
      throw ArgumentError.value(
        this.redirectChain,
        'redirectChain',
        'Must contain at most ten bounded redirect hops.',
      );
    }
  }

  final WebCandidateKind kind;
  final Uri uri;
  final Uri? pageUri;
  final String requestMethod;
  final UnmodifiableMapView<String, String> headers;
  final int sourceEventSequence;
  final bool isRedirect;
  final UnmodifiableListView<Uri> redirectChain;
  final RuntimeMediaOriginGrant? runtimeOriginGrant;

  WebMediaCandidate withPageUri(Uri value) => WebMediaCandidate(
    kind: kind,
    uri: uri,
    pageUri: value,
    requestMethod: requestMethod,
    headers: headers,
    sourceEventSequence: sourceEventSequence,
    isRedirect: isRedirect,
    redirectChain: redirectChain,
    runtimeOriginGrant: runtimeOriginGrant,
  );

  Map<String, Object?> toRedactedDiagnostic() => {
    'kind': kind.name,
    'scheme': uri.scheme,
    'host': uri.host,
    'pathSegmentCount': uri.pathSegments.length,
    'pageScheme': pageUri?.scheme,
    'pageHost': pageUri?.host,
    'pagePort': pageUri?.hasPort == true ? pageUri!.port : null,
    'requestMethod': requestMethod,
    'headerNames': headers.keys.toList(growable: false),
    'sourceEventSequence': sourceEventSequence,
    'isRedirect': isRedirect,
    'redirectChain': [
      for (final redirect in redirectChain) _redactedWebUri(redirect),
    ],
    'hasRuntimeOriginGrant': runtimeOriginGrant != null,
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
    this.hasCompleteRequestMetadata = false,
    String? documentBody,
  }) : events = UnmodifiableListView(
         List<WebCaptureEvent>.unmodifiable(events),
       ),
       candidates = UnmodifiableListView(
         List<WebMediaCandidate>.unmodifiable(candidates),
       ),
       cookies = UnmodifiableListView(
         List<WebCaptureCookie>.unmodifiable(cookies),
       ),
       documentBody = _boundedDocumentBody(documentBody);

  final UnmodifiableListView<WebCaptureEvent> events;
  final UnmodifiableListView<WebMediaCandidate> candidates;
  final UnmodifiableListView<WebCaptureCookie> cookies;
  final WebCaptureStopReason stopReason;
  final Uri finalUri;
  final bool hasCompleteRequestMetadata;
  final String? documentBody;

  @override
  String toString() =>
      'WebCaptureSnapshot(finalHost: ${finalUri.host}, '
      'events: ${events.length}, candidates: ${candidates.length}, '
      'cookies: ${cookies.length}, hasDocument: ${documentBody != null}, '
      'hasCompleteRequestMetadata: $hasCompleteRequestMetadata, '
      'documentBytes: ${documentBody == null ? null : _utf8Bytes(documentBody!)}, '
      'stopReason: $stopReason)';
}

Map<String, Object?> _redactedWebUri(Uri uri) => {
  'scheme': uri.scheme,
  'host': uri.host,
  'port': uri.hasPort ? uri.port : null,
  'pathSegmentCount': uri.pathSegments.length,
};

String? _boundedDocumentBody(String? value) {
  if (value == null) return null;
  if (_utf8Bytes(value) > 8 * 1024 * 1024) {
    throw ArgumentError.value(
      value,
      'documentBody',
      'Captured document exceeds the bounded in-memory limit.',
    );
  }
  return value;
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

bool webCaptureAllowsRuntimeUri({
  required SourceSecurityPolicy policy,
  required Uri uri,
  RuntimeMediaOriginGrant? grant,
  String? acquisitionId,
  DateTime? now,
}) =>
    policy.allowsUri(uri) ||
    (grant != null &&
        acquisitionId != null &&
        grant.allows(uri, acquisitionId: acquisitionId, now: now));

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

Uri _safeHttpUri(Uri value, [String name = 'uri']) {
  if (!value.hasScheme ||
      (value.scheme != 'https' && value.scheme != 'http') ||
      value.host.isEmpty ||
      value.userInfo.isNotEmpty) {
    throw ArgumentError.value(value, name, 'Must be a safe HTTP(S) URI.');
  }
  return value;
}

Uri _origin(Uri value) {
  final safe = _safeHttpUri(value, 'origin');
  if (safe.scheme != 'https' ||
      (safe.path.isNotEmpty && safe.path != '/') ||
      safe.hasQuery ||
      safe.hasFragment) {
    throw ArgumentError.value(value, 'origin', 'Must be an HTTPS origin.');
  }
  return Uri(
    scheme: safe.scheme,
    host: safe.host,
    port: safe.hasPort ? safe.port : null,
  );
}

int _effectivePort(Uri uri) =>
    uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);

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
