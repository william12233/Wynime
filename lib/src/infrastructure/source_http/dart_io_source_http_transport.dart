import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../../domain/models/source_http_models.dart';
import '../../domain/services/source_http_transport.dart';
import '../playback/proxy_upstream_client.dart';

/// Bounded GET transport for source-package documents.
///
/// The existing [DartIoProxyUpstreamClient] is reused for its direct,
/// public-address-pinned connection path. This adapter adds source-specific
/// manual redirect, response-size and text-decoding rules without exposing
/// upstream response headers or body data on failed results.
final class DartIoSourceHttpTransport implements SourceHttpTransport {
  factory DartIoSourceHttpTransport({ProxyUpstreamClient? upstreamClient}) {
    final client = upstreamClient ?? DartIoProxyUpstreamClient();
    return DartIoSourceHttpTransport._(client, upstreamClient == null);
  }

  DartIoSourceHttpTransport._(this._upstreamClient, this._ownsClient);

  final ProxyUpstreamClient _upstreamClient;
  final bool _ownsClient;
  bool _closed = false;

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    if (_closed) {
      return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
    }

    var currentUri = request.uri;
    final redirects = <Uri>[];
    while (true) {
      if (_closed) {
        return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
      }
      if (currentUri.fragment.isNotEmpty ||
          !request.securityPolicy.allowsUri(currentUri)) {
        return _failure(
          redirects.isEmpty
              ? SourceHttpTransportStatus.policyRejected
              : SourceHttpTransportStatus.redirectUriNotAllowed,
          redirects.isEmpty
              ? 'request_uri_not_allowed'
              : 'redirect_uri_not_allowed',
        );
      }

      final incoming = await _sendUpstream(request, currentUri);
      if (_closed) {
        return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
      }
      if (incoming is SourceHttpTransportResult) {
        return incoming;
      }
      final response = incoming as ProxyUpstreamResponse;
      if (response.statusCode < 100 || response.statusCode > 599) {
        await _discardQuietly(
          response.body,
          request.securityPolicy.budget.maxDocumentBytes,
          request.timeout,
        );
        if (_closed) {
          return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
        }
        return _failure(
          SourceHttpTransportStatus.invalidResponse,
          'invalid_http_status',
        );
      }

      if (_isRedirect(response.statusCode)) {
        if (redirects.length >= request.securityPolicy.budget.maxRedirects) {
          await _discardQuietly(
            response.body,
            request.securityPolicy.budget.maxDocumentBytes,
            request.timeout,
          );
          if (_closed) {
            return _failure(
              SourceHttpTransportStatus.closed,
              'transport_closed',
            );
          }
          return _failure(
            SourceHttpTransportStatus.redirectBudgetExceeded,
            'redirect_budget_exceeded',
          );
        }
        final location = response.firstLocation;
        if (location == null || location.trim().isEmpty) {
          await _discardQuietly(
            response.body,
            request.securityPolicy.budget.maxDocumentBytes,
            request.timeout,
          );
          if (_closed) {
            return _failure(
              SourceHttpTransportStatus.closed,
              'transport_closed',
            );
          }
          return _failure(
            SourceHttpTransportStatus.invalidResponse,
            'redirect_location_missing',
          );
        }
        final nextUri = _resolveRedirect(currentUri, location);
        if (nextUri == null ||
            nextUri.fragment.isNotEmpty ||
            nextUri.userInfo.isNotEmpty ||
            !request.securityPolicy.allowsUri(nextUri)) {
          await _discardQuietly(
            response.body,
            request.securityPolicy.budget.maxDocumentBytes,
            request.timeout,
          );
          if (_closed) {
            return _failure(
              SourceHttpTransportStatus.closed,
              'transport_closed',
            );
          }
          return _failure(
            SourceHttpTransportStatus.redirectUriNotAllowed,
            'redirect_uri_not_allowed',
            responseEvidence: _redirectEvidence(
              response: response,
              finalUri: nextUri!,
              redirectCount: redirects.length + 1,
            ),
          );
        }
        final discardResult = await _discardBounded(
          response.body,
          request.securityPolicy.budget.maxDocumentBytes,
          request.timeout,
        );
        if (_closed) {
          return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
        }
        if (discardResult != null) {
          return discardResult;
        }
        redirects.add(nextUri);
        currentUri = nextUri;
        continue;
      }

      if (response.statusCode < 200 || response.statusCode > 299) {
        await _discardQuietly(
          response.body,
          request.securityPolicy.budget.maxDocumentBytes,
          request.timeout,
        );
        if (_closed) {
          return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
        }
        return SourceHttpTransportResult(
          status: SourceHttpTransportStatus.httpError,
          reasonCode: 'http_status_${response.statusCode}',
          httpStatus: response.statusCode,
          responseEvidence: _responseMetadataEvidence(
            response: response,
            finalUri: currentUri,
            redirects: redirects,
            bodyClassification: 'http_error',
          ),
        );
      }

      final maximumBytes = request.securityPolicy.budget.maxDocumentBytes;
      final contentLength = response.contentLength;
      if (contentLength != null &&
          (contentLength < 0 || contentLength > maximumBytes)) {
        await _discardQuietly(response.body, maximumBytes, request.timeout);
        if (_closed) {
          return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
        }
        return _failure(
          SourceHttpTransportStatus.responseTooLarge,
          'response_too_large',
        );
      }

      final bytes = await _readBounded(
        response.body,
        maximumBytes,
        request.timeout,
      );
      if (_closed) {
        return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
      }
      if (bytes is SourceHttpTransportResult) {
        return bytes;
      }
      final bodyBytes = bytes as List<int>;
      final body = _decodeUtf8(bodyBytes);
      if (body == null) {
        return _failure(
          SourceHttpTransportStatus.invalidResponse,
          'response_not_utf8',
          responseEvidence: _responseEvidence(
            response: response,
            finalUri: currentUri,
            redirects: redirects,
            bodyBytes: bodyBytes,
          ),
        );
      }
      try {
        return SourceHttpTransportResult(
          status: SourceHttpTransportStatus.success,
          response: SourceHttpResponse(
            statusCode: response.statusCode,
            finalUri: currentUri,
            redirectChain: redirects,
            body: body,
            contentType: response.contentType,
          ),
        );
      } on Object {
        return _failure(
          SourceHttpTransportStatus.invalidResponse,
          'response_invalid',
        );
      }
    }
  }

  Future<Object> _sendUpstream(SourceHttpRequest request, Uri uri) async {
    try {
      final headers = <String, String>{...request.headers};
      headers.putIfAbsent('accept-encoding', () => 'identity');
      return await _upstreamClient
          .send(
            ProxyUpstreamRequest(
              uri: uri,
              method: 'GET',
              headers: headers,
              timeout: request.timeout,
            ),
          )
          .timeout(request.timeout);
    } on TimeoutException {
      return _failure(SourceHttpTransportStatus.timeout, 'request_timeout');
    } on ProxyUpstreamSecurityException catch (error) {
      return _failure(
        SourceHttpTransportStatus.policyRejected,
        error.code == 'upstream_address_not_public'
            ? 'upstream_address_not_public'
            : 'upstream_security_rejected',
      );
    } on SocketException {
      return _failure(SourceHttpTransportStatus.networkError, 'network_error');
    } on HttpException {
      return _failure(SourceHttpTransportStatus.networkError, 'network_error');
    } on Object {
      if (_closed) {
        return _failure(SourceHttpTransportStatus.closed, 'transport_closed');
      }
      return _failure(SourceHttpTransportStatus.failed, 'source_http_failed');
    }
  }

  @override
  Future<void> close() async {
    if (_closed) {
      return;
    }
    _closed = true;
    if (_ownsClient) {
      await _upstreamClient.close();
    }
  }

  static SourceHttpTransportResult _failure(
    SourceHttpTransportStatus status,
    String reasonCode, {
    SourceHttpResponseEvidence? responseEvidence,
  }) => SourceHttpTransportResult(
    status: status,
    reasonCode: reasonCode,
    responseEvidence: responseEvidence,
  );

  static SourceHttpResponseEvidence _responseEvidence({
    required ProxyUpstreamResponse response,
    required Uri finalUri,
    required List<Uri> redirects,
    required List<int> bodyBytes,
  }) => SourceHttpResponseEvidence(
    statusCode: response.statusCode,
    finalUri: finalUri,
    redirectCount: redirects.length,
    contentType: response.contentType,
    bodyBytes: bodyBytes.length,
    bodyClassification: _classifyBody(bodyBytes, response.statusCode),
    bodyEncoding: _classifyEncoding(bodyBytes),
  );

  static SourceHttpResponseEvidence _responseMetadataEvidence({
    required ProxyUpstreamResponse response,
    required Uri finalUri,
    required List<Uri> redirects,
    required String bodyClassification,
  }) {
    final contentLength = response.contentLength;
    final bodyBytes =
        contentLength != null &&
            contentLength >= 0 &&
            contentLength <= 8 * 1024 * 1024
        ? contentLength
        : 0;
    return SourceHttpResponseEvidence(
      statusCode: response.statusCode,
      finalUri: finalUri,
      redirectCount: redirects.length,
      contentType: response.contentType,
      bodyBytes: bodyBytes,
      bodyClassification: bodyClassification,
      bodyEncoding: 'not_read',
    );
  }

  static SourceHttpResponseEvidence? _redirectEvidence({
    required ProxyUpstreamResponse response,
    required Uri finalUri,
    required int redirectCount,
  }) {
    final evidenceUri = _redactedEvidenceUri(finalUri);
    if (evidenceUri == null) {
      return null;
    }
    final contentLength = response.contentLength;
    final bodyBytes =
        contentLength != null &&
            contentLength >= 0 &&
            contentLength <= 8 * 1024 * 1024
        ? contentLength
        : 0;
    return SourceHttpResponseEvidence(
      statusCode: response.statusCode,
      finalUri: evidenceUri,
      redirectCount: redirectCount,
      bodyBytes: bodyBytes,
      bodyClassification: 'redirect',
      bodyEncoding: 'not_read',
      contentType: response.contentType,
    );
  }

  static Uri? _redactedEvidenceUri(Uri uri) {
    if (uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty ||
        !const {'http', 'https'}.contains(uri.scheme.toLowerCase())) {
      return null;
    }
    return Uri(
      scheme: uri.scheme.toLowerCase(),
      host: uri.host,
      port: uri.hasPort ? uri.port : null,
      path: uri.path.isEmpty ? '/' : uri.path,
    );
  }

  static bool _isRedirect(int statusCode) =>
      statusCode == 300 ||
      statusCode == 301 ||
      statusCode == 302 ||
      statusCode == 303 ||
      statusCode == 307 ||
      statusCode == 308;

  static Uri? _resolveRedirect(Uri currentUri, String location) {
    try {
      return currentUri.resolve(location.trim());
    } on Object {
      return null;
    }
  }

  static Future<Object> _readBounded(
    Stream<List<int>> body,
    int maximumBytes,
    Duration timeout,
  ) async {
    final bytes = <int>[];
    try {
      await for (final chunk in body.timeout(timeout)) {
        if (bytes.length > maximumBytes - chunk.length) {
          return _failure(
            SourceHttpTransportStatus.responseTooLarge,
            'response_too_large',
          );
        }
        bytes.addAll(chunk);
      }
      return bytes;
    } on TimeoutException {
      return _failure(SourceHttpTransportStatus.timeout, 'response_timeout');
    } on SocketException {
      return _failure(SourceHttpTransportStatus.networkError, 'network_error');
    } on Object {
      return _failure(
        SourceHttpTransportStatus.invalidResponse,
        'response_read_failed',
      );
    }
  }

  static Future<SourceHttpTransportResult?> _discardBounded(
    Stream<List<int>> body,
    int maximumBytes,
    Duration timeout,
  ) async {
    try {
      var bytes = 0;
      await for (final chunk in body.timeout(timeout)) {
        if (bytes > maximumBytes - chunk.length) {
          return _failure(
            SourceHttpTransportStatus.responseTooLarge,
            'response_too_large',
          );
        }
        bytes += chunk.length;
      }
      return null;
    } on TimeoutException {
      return _failure(SourceHttpTransportStatus.timeout, 'response_timeout');
    } on Object {
      return _failure(
        SourceHttpTransportStatus.networkError,
        'response_read_failed',
      );
    }
  }

  static Future<void> _discardQuietly(
    Stream<List<int>> body,
    int maximumBytes,
    Duration timeout,
  ) async {
    try {
      await _discardBounded(body, maximumBytes, timeout);
    } on Object {
      // The original status or policy outcome remains the truthful result.
    }
  }

  static String? _decodeUtf8(List<int> bytes) {
    try {
      return utf8.decode(bytes, allowMalformed: false);
    } on FormatException {
      return null;
    }
  }

  static String _classifyEncoding(List<int> bytes) {
    if (bytes.length >= 2 && bytes[0] == 0x1f && bytes[1] == 0x8b) {
      return 'gzip';
    }
    if (bytes.length >= 2 && bytes[0] == 0x78) {
      final second = bytes[1];
      if (second == 0x01 ||
          second == 0x5e ||
          second == 0x9c ||
          second == 0xda) {
        return 'deflate';
      }
    }
    return _decodeUtf8(bytes) == null ? 'non_utf8' : 'utf8';
  }

  static String _classifyBody(List<int> bytes, int statusCode) {
    if (bytes.isEmpty) return 'empty';
    final ascii = String.fromCharCodes(
      bytes
          .take(4096)
          .map((value) => value >= 0x20 && value <= 0x7e ? value : 0x20),
    ).trimLeft().toLowerCase();
    if (ascii.startsWith('{') || ascii.startsWith('[')) return 'json';
    if (ascii.contains('captcha') ||
        ascii.contains('challenge') ||
        ascii.contains('cf-chl')) {
      return 'challenge';
    }
    if (statusCode >= 400) return 'error_page';
    if (ascii.startsWith('<!doctype html') ||
        ascii.startsWith('<html') ||
        ascii.contains('<html')) {
      return 'html';
    }
    return 'unexpected';
  }
}
