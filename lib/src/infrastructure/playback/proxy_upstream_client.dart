import 'dart:async';
import 'dart:collection';
import 'dart:io';

final class ProxyUpstreamRequest {
  ProxyUpstreamRequest({
    required this.uri,
    required String method,
    required Map<String, String> headers,
    required this.timeout,
  }) : method = method.toUpperCase(),
       headers = UnmodifiableMapView(
         Map<String, String>.unmodifiable(headers),
       ) {
    if (this.method != 'GET' && this.method != 'HEAD') {
      throw ArgumentError.value(
        method,
        'method',
        'Only GET and HEAD are valid.',
      );
    }
    if ((uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      throw ArgumentError.value(uri, 'uri', 'Must be a safe HTTP(S) URI.');
    }
    if (timeout <= Duration.zero) {
      throw ArgumentError.value(timeout, 'timeout', 'Must be positive.');
    }
  }

  final Uri uri;
  final String method;
  final UnmodifiableMapView<String, String> headers;
  final Duration timeout;
}

final class ProxyUpstreamResponse {
  ProxyUpstreamResponse({
    required this.statusCode,
    required Map<String, List<String>> headers,
    required this.body,
  }) : headers = UnmodifiableMapView(
         Map<String, List<String>>.unmodifiable({
           for (final entry in headers.entries)
             entry.key.toLowerCase(): List<String>.unmodifiable(entry.value),
         }),
       );

  final int statusCode;
  final UnmodifiableMapView<String, List<String>> headers;
  final Stream<List<int>> body;

  String? get firstLocation => _firstOrNull(headers['location']);

  int? get contentLength {
    final raw = _firstOrNull(headers['content-length']);
    return raw == null ? null : int.tryParse(raw);
  }

  String? get contentType => _firstOrNull(headers['content-type']);
}

abstract interface class UpstreamAddressResolver {
  Future<List<InternetAddress>> lookup(String host);
}

final class SystemUpstreamAddressResolver implements UpstreamAddressResolver {
  const SystemUpstreamAddressResolver();

  @override
  Future<List<InternetAddress>> lookup(String host) =>
      InternetAddress.lookup(host);
}

abstract interface class ProxyUpstreamClient {
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request);

  Future<void> close();
}

final class DartIoProxyUpstreamClient implements ProxyUpstreamClient {
  factory DartIoProxyUpstreamClient({
    HttpClient? client,
    UpstreamAddressResolver addressResolver =
        const SystemUpstreamAddressResolver(),
  }) => DartIoProxyUpstreamClient._(client ?? HttpClient(), addressResolver);

  DartIoProxyUpstreamClient._(this._client, this._addressResolver) {
    _client.autoUncompress = false;
    _client.maxConnectionsPerHost = 8;
  }

  final HttpClient _client;
  final UpstreamAddressResolver _addressResolver;
  bool _closed = false;

  @override
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request) async {
    if (_closed) {
      throw StateError('Proxy upstream client is closed.');
    }

    final addresses = await _addressResolver
        .lookup(request.uri.host)
        .timeout(request.timeout);
    if (addresses.isEmpty || addresses.any(_isNonPublicAddress)) {
      throw const ProxyUpstreamSecurityException(
        'upstream_address_not_public',
        'Upstream DNS resolved to a non-public address.',
      );
    }

    final outgoing = await _client
        .openUrl(request.method, request.uri)
        .timeout(request.timeout);
    outgoing
      ..followRedirects = false
      ..maxRedirects = 0
      ..persistentConnection = true;
    for (final entry in request.headers.entries) {
      outgoing.headers.set(entry.key, entry.value, preserveHeaderCase: false);
    }
    final incoming = await outgoing.close().timeout(request.timeout);
    final headers = <String, List<String>>{};
    incoming.headers.forEach((name, values) {
      headers[name.toLowerCase()] = List<String>.unmodifiable(values);
    });
    return ProxyUpstreamResponse(
      statusCode: incoming.statusCode,
      headers: headers,
      body: incoming,
    );
  }

  @override
  Future<void> close() async {
    if (_closed) {
      return;
    }
    _closed = true;
    _client.close(force: true);
  }
}

final class ProxyUpstreamSecurityException implements Exception {
  const ProxyUpstreamSecurityException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'ProxyUpstreamSecurityException($code): $message';
}

T? _firstOrNull<T>(List<T>? values) =>
    values == null || values.isEmpty ? null : values.first;

bool _isNonPublicAddress(InternetAddress address) {
  if (address.isLoopback || address.isLinkLocal || address.isMulticast) {
    return true;
  }
  final bytes = address.rawAddress;
  if (address.type == InternetAddressType.IPv4) {
    final a = bytes[0];
    final b = bytes[1];
    final c = bytes[2];
    return a == 0 ||
        a == 10 ||
        a == 127 ||
        (a == 100 && b >= 64 && b <= 127) ||
        (a == 169 && b == 254) ||
        (a == 172 && b >= 16 && b <= 31) ||
        (a == 192 && b == 0 && c == 0) ||
        (a == 192 && b == 0 && c == 2) ||
        (a == 192 && b == 168) ||
        (a == 198 && (b == 18 || b == 19)) ||
        (a == 198 && b == 51 && c == 100) ||
        (a == 203 && b == 0 && c == 113) ||
        a >= 224;
  }

  if (bytes.every((value) => value == 0)) {
    return true;
  }
  final isUniqueLocal = (bytes[0] & 0xfe) == 0xfc;
  final isDocumentation =
      bytes[0] == 0x20 &&
      bytes[1] == 0x01 &&
      bytes[2] == 0x0d &&
      bytes[3] == 0xb8;
  return isUniqueLocal || isDocumentation;
}
