import 'dart:collection';
import 'dart:convert';

import 'source_security_policy.dart';

/// The first live source transport boundary intentionally supports only
/// idempotent GET requests. POST and source-specific request construction are
/// separate capabilities and must not be implied by this contract.
enum SourceHttpMethod { get }

final class SourceHttpRequest {
  SourceHttpRequest({
    required this.uri,
    required this.securityPolicy,
    this.method = SourceHttpMethod.get,
    Map<String, String> headers = const {},
    List<int> bodyBytes = const [],
    this.timeout = const Duration(seconds: 30),
  }) : headers = _freezeHeaders(headers),
       bodyBytes = UnmodifiableListView(_copyBody(bodyBytes)) {
    if (uri.fragment.isNotEmpty) {
      throw ArgumentError.value(
        uri,
        'uri',
        'A live HTTP request must not contain a fragment.',
      );
    }
    if (!securityPolicy.allowsUri(uri)) {
      throw ArgumentError.value(
        uri,
        'uri',
        'URI is outside the source security policy.',
      );
    }
    if (method != SourceHttpMethod.get || this.bodyBytes.isNotEmpty) {
      throw ArgumentError(
        'The initial live HTTP boundary accepts GET requests without a body.',
      );
    }
    if (_headerBytes(this.headers) > maxHeaderBytes) {
      throw ArgumentError('Request headers exceed maxHeaderBytes.');
    }
    if (timeout < minTimeout || timeout > maxTimeout) {
      throw ArgumentError.value(
        timeout,
        'timeout',
        'Must be between $minTimeout and $maxTimeout.',
      );
    }
  }

  static const maxHeaderBytes = 64 * 1024;
  static const maxBodyBytes = 256 * 1024;
  static const minTimeout = Duration(seconds: 1);
  static const maxTimeout = Duration(seconds: 120);

  final Uri uri;
  final SourceSecurityPolicy securityPolicy;
  final SourceHttpMethod method;
  final UnmodifiableMapView<String, String> headers;
  final UnmodifiableListView<int> bodyBytes;
  final Duration timeout;

  Map<String, Object?> toRedactedDiagnostic() => {
    'method': method.name,
    'scheme': uri.scheme,
    'host': uri.host,
    'port': uri.hasPort ? uri.port : null,
    'pathSegmentCount': uri.pathSegments.length,
    'headerNames': headers.keys.toList(growable: false),
    'bodyBytes': bodyBytes.length,
    'timeoutMs': timeout.inMilliseconds,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static UnmodifiableMapView<String, String> _freezeHeaders(
    Map<String, String> values,
  ) {
    final result = <String, String>{};
    for (final entry in values.entries) {
      final name = entry.key.trim().toLowerCase();
      if (name.isEmpty ||
          name.length > 128 ||
          !RegExp(r"^[A-Za-z0-9!#$%&'*+.^_~-]+$").hasMatch(name)) {
        throw ArgumentError.value(
          entry.key,
          'headers',
          'Header names must be bounded HTTP tokens.',
        );
      }
      if (_forbiddenHeaderNames.contains(name)) {
        throw ArgumentError.value(
          entry.key,
          'headers',
          'Credential or hop-by-hop headers are not accepted.',
        );
      }
      final value = entry.value.trim();
      if (utf8.encode(value).length > 16 * 1024 ||
          _hasControlCharacters(value)) {
        throw ArgumentError.value(
          entry.value,
          'headers',
          'Header values must be bounded and free of control characters.',
        );
      }
      result[name] = value;
    }
    return UnmodifiableMapView(Map<String, String>.unmodifiable(result));
  }

  static List<int> _copyBody(List<int> values) {
    if (values.length > maxBodyBytes ||
        values.any((value) => value < 0 || value > 255)) {
      throw ArgumentError.value(
        values,
        'bodyBytes',
        'Request body must contain at most $maxBodyBytes byte values.',
      );
    }
    return List<int>.unmodifiable(values);
  }

  static bool _hasControlCharacters(String value) => value.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  );

  static int _headerBytes(Map<String, String> values) => values.entries.fold(
    0,
    (total, entry) =>
        total +
        utf8.encode(entry.key).length +
        utf8.encode(entry.value).length +
        4,
  );

  static const _forbiddenHeaderNames = {
    'authorization',
    'connection',
    'content-length',
    'cookie',
    'host',
    'keep-alive',
    'proxy-authenticate',
    'proxy-authorization',
    'set-cookie',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade',
  };
}

/// A successful, bounded text response kept only in memory for a later
/// declarative source evaluator. Response headers are deliberately not
/// retained by this model.
final class SourceHttpResponse {
  SourceHttpResponse({
    required this.statusCode,
    required this.finalUri,
    required Iterable<Uri> redirectChain,
    required String body,
    String? contentType,
  }) : redirectChain = UnmodifiableListView(
         List<Uri>.unmodifiable(redirectChain),
       ),
       body = body,
       contentType = _normalizeContentType(contentType) {
    if (statusCode < 200 || statusCode > 299) {
      throw ArgumentError.value(
        statusCode,
        'statusCode',
        'A successful response must have a 2xx status.',
      );
    }
    if (!_isSafeHttpUri(finalUri)) {
      throw ArgumentError.value(
        finalUri,
        'finalUri',
        'A response URI must be an HTTP(S) URI without a fragment, user info, or non-standard port.',
      );
    }
    if (this.redirectChain.length > 10 ||
        this.redirectChain.any((uri) => !_isSafeHttpUri(uri))) {
      throw ArgumentError.value(
        redirectChain,
        'redirectChain',
        'Redirects must be bounded safe HTTP URIs.',
      );
    }
    if (utf8.encode(this.body).length > 8 * 1024 * 1024) {
      throw ArgumentError.value(
        body,
        'body',
        'Response body exceeds the maximum source document size.',
      );
    }
  }

  final int statusCode;
  final Uri finalUri;
  final UnmodifiableListView<Uri> redirectChain;
  final String body;
  final String? contentType;

  Map<String, Object?> toRedactedDiagnostic() => {
    'statusCode': statusCode,
    'finalScheme': finalUri.scheme,
    'finalHost': finalUri.host,
    'redirectCount': redirectChain.length,
    'bodyBytes': utf8.encode(body).length,
    'hasContentType': contentType != null,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _hasControlCharacters(String value) => value.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  );

  static bool _isSafeHttpUri(Uri uri) {
    if (uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty ||
        !const {'http', 'https'}.contains(uri.scheme.toLowerCase())) {
      return false;
    }
    if (!uri.hasPort) {
      return true;
    }
    return switch (uri.scheme.toLowerCase()) {
      'http' => uri.port == 80,
      'https' => uri.port == 443,
      _ => false,
    };
  }

  static String? _normalizeContentType(String? value) {
    final normalized = value?.trim();
    if (normalized != null &&
        (normalized.isEmpty ||
            normalized.length > 256 ||
            _hasControlCharacters(normalized))) {
      throw ArgumentError.value(
        value,
        'contentType',
        'Content type must be bounded and free of control characters.',
      );
    }
    return normalized;
  }
}

/// Secret-safe metadata retained when a response cannot become a text
/// response. It deliberately stores no response bytes or header values.
final class SourceHttpResponseEvidence {
  SourceHttpResponseEvidence({
    required this.statusCode,
    required this.finalUri,
    required this.redirectCount,
    required this.bodyBytes,
    required this.bodyClassification,
    required this.bodyEncoding,
    this.contentType,
  }) {
    if (statusCode < 100 || statusCode > 599) {
      throw ArgumentError.value(
        statusCode,
        'statusCode',
        'HTTP status must be between 100 and 599.',
      );
    }
    if (!_isSafeHttpUri(finalUri)) {
      throw ArgumentError.value(
        finalUri,
        'finalUri',
        'An evidence URI must be a safe HTTP(S) URI.',
      );
    }
    if (redirectCount < 0 || redirectCount > 10) {
      throw ArgumentError.value(
        redirectCount,
        'redirectCount',
        'Redirect count is outside the bounded evidence range.',
      );
    }
    if (bodyBytes < 0 || bodyBytes > 8 * 1024 * 1024) {
      throw ArgumentError.value(
        bodyBytes,
        'bodyBytes',
        'Body byte count is outside the bounded evidence range.',
      );
    }
    _validateToken(bodyClassification, 'bodyClassification');
    _validateToken(bodyEncoding, 'bodyEncoding');
    _validateContentType(contentType);
  }

  final int statusCode;
  final Uri finalUri;
  final int redirectCount;
  final String? contentType;
  final int bodyBytes;
  final String bodyClassification;
  final String bodyEncoding;

  Map<String, Object?> toRedactedDiagnostic() => {
    'statusCode': statusCode,
    'finalScheme': finalUri.scheme,
    'finalHost': finalUri.host,
    'finalPort': finalUri.hasPort ? finalUri.port : null,
    'finalPath': _safePath(finalUri),
    'redirectCount': redirectCount,
    'contentType': contentType,
    'bodyBytes': bodyBytes,
    'bodyClassification': bodyClassification,
    'bodyEncoding': bodyEncoding,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static void _validateToken(String value, String name) {
    if (!RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value)) {
      throw ArgumentError.value(value, name, 'Must be a safe evidence token.');
    }
  }

  static void _validateContentType(String? value) {
    if (value == null) return;
    if (value.isEmpty || value.length > 256 || _hasControlCharacters(value)) {
      throw ArgumentError.value(
        value,
        'contentType',
        'Content type must be bounded and free of control characters.',
      );
    }
  }

  static bool _hasControlCharacters(String value) => value.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  );

  static bool _isSafeHttpUri(Uri uri) {
    if (uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty ||
        !const {'http', 'https'}.contains(uri.scheme.toLowerCase())) {
      return false;
    }
    return !uri.hasPort || (uri.port >= 1 && uri.port <= 65535);
  }

  static String _safePath(Uri uri) {
    final path = uri.path.isEmpty ? '/' : uri.path;
    return path.length <= 2048 ? path : '${path.substring(0, 2048)}...';
  }
}

enum SourceHttpTransportStatus {
  success,
  closed,
  policyRejected,
  redirectUriNotAllowed,
  redirectBudgetExceeded,
  responseTooLarge,
  httpError,
  timeout,
  networkError,
  invalidResponse,
  failed,
}

final class SourceHttpTransportResult {
  SourceHttpTransportResult({
    required this.status,
    this.response,
    this.reasonCode,
    this.httpStatus,
    this.responseEvidence,
  }) {
    final isSuccess = status == SourceHttpTransportStatus.success;
    if (isSuccess) {
      if (response == null ||
          reasonCode != null ||
          httpStatus != null ||
          responseEvidence != null) {
        throw ArgumentError(
          'A successful HTTP result must contain only one response.',
        );
      }
      return;
    }
    if (response != null || reasonCode == null || !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A failed HTTP result must contain one safe reason and no response.',
      );
    }
    if (httpStatus != null && (httpStatus! < 100 || httpStatus! > 599)) {
      throw ArgumentError.value(
        httpStatus,
        'httpStatus',
        'HTTP status must be between 100 and 599.',
      );
    }
    if (status != SourceHttpTransportStatus.httpError && httpStatus != null) {
      throw ArgumentError(
        'Only an HTTP error result may expose an HTTP status.',
      );
    }
  }

  final SourceHttpTransportStatus status;
  final SourceHttpResponse? response;
  final String? reasonCode;
  final int? httpStatus;
  final SourceHttpResponseEvidence? responseEvidence;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasResponse': response != null,
    'httpStatus': httpStatus,
    'responseBodyBytes': response == null
        ? null
        : utf8.encode(response!.body).length,
    'redirectCount': response?.redirectChain.length,
    'reasonCode': reasonCode,
    'responseEvidence': responseEvidence?.toRedactedDiagnostic(),
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}
