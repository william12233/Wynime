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
      throw ArgumentError.value(method, 'method', 'Only GET and HEAD are valid.');
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
  Future<List<InternetAddress>> lookup(String host) async {
    final families = await Future.wait([
      _lookupFamily(host, InternetAddressType.IPv4),
      _lookupFamily(host, InternetAddressType.IPv6),
    ]);
    final unique = <String, InternetAddress>{};
    for (final addresses in families) {
      for (final address in addresses) {
        unique['${address.type.name}:${address.address}'] = address;
      }
    }
    return List<InternetAddress>.unmodifiable(unique.values);
  }
}

Future<List<InternetAddress>> _lookupFamily(
  String host,
  InternetAddressType type,
) async {
  try {
    return await InternetAddress.lookup(host, type: type);
  } on SocketException {
    return const [];
  }
}

abstract interface class ProxyUpstreamClient {
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request);

  Future<void> close();
}

final class DartIoProxyUpstreamClient implements ProxyUpstreamClient {
  factory DartIoProxyUpstreamClient({
    UpstreamAddressResolver addressResolver =
        const SystemUpstreamAddressResolver(),
    SecurityContext? securityContext,
  }) => DartIoProxyUpstreamClient._(
    HttpClient(context: securityContext),
    addressResolver,
    securityContext,
  );

  DartIoProxyUpstreamClient._(
    this._client,
    this._addressResolver,
    this._securityContext,
  ) {
    _client
      ..autoUncompress = false
      ..maxConnectionsPerHost = 8
      ..findProxy = (_) => 'DIRECT'
      ..connectionFactory = _createPinnedConnection;
  }

  final HttpClient _client;
  final UpstreamAddressResolver _addressResolver;
  final SecurityContext? _securityContext;
  bool _closed = false;

  @override
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request) async {
    if (_closed) {
      throw StateError('Proxy upstream client is closed.');
    }

    await _resolvePublicAddresses(request.uri.host).timeout(request.timeout);

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

  Future<ConnectionTask<Socket>> _createPinnedConnection(
    Uri uri,
    String? proxyHost,
    int? proxyPort,
  ) async {
    if (proxyHost != null || proxyPort != null) {
      throw const ProxyUpstreamSecurityException(
        'upstream_proxy_not_allowed',
        'Upstream proxy routing is disabled.',
      );
    }

    final addresses = await _resolvePublicAddresses(uri.host);
    final state = _ConnectionAttemptState();
    final socket = _connectValidatedAddresses(
      uri: uri,
      addresses: addresses,
      state: state,
      securityContext: _securityContext,
    );
    return ConnectionTask.fromSocket(socket, state.cancel);
  }

  Future<List<InternetAddress>> _resolvePublicAddresses(String host) async {
    final addresses = await _addressResolver.lookup(host);
    if (addresses.isEmpty || addresses.any(_isNonPublicAddress)) {
      throw const ProxyUpstreamSecurityException(
        'upstream_address_not_public',
        'Upstream DNS resolved to a non-public address.',
      );
    }
    return List<InternetAddress>.unmodifiable(addresses);
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

final class _ConnectionAttemptState {
  ConnectionTask<Socket>? _task;
  Socket? _socket;
  bool _cancelled = false;

  bool get isCancelled => _cancelled;

  void attachTask(ConnectionTask<Socket> task) {
    _task = task;
  }

  void attachSocket(Socket socket) {
    _socket = socket;
  }

  void reset() {
    _task = null;
    _socket = null;
  }

  void release() {
    reset();
  }

  void cancel() {
    if (_cancelled) {
      return;
    }
    _cancelled = true;
    _task?.cancel();
    _socket?.destroy();
  }
}

Future<Socket> _connectValidatedAddresses({
  required Uri uri,
  required List<InternetAddress> addresses,
  required _ConnectionAttemptState state,
  required SecurityContext? securityContext,
}) async {
  final port = uri.hasPort ? uri.port : _defaultPort(uri.scheme);
  Object? firstError;
  StackTrace? firstStackTrace;

  for (final address in addresses) {
    if (state.isCancelled) {
      throw const SocketException('Connection attempt cancelled.');
    }

    Socket? socket;
    try {
      final task = await Socket.startConnect(address, port);
      state.attachTask(task);
      socket = await task.socket;
      state.attachSocket(socket);
      if (uri.scheme == 'https') {
        socket = await SecureSocket.secure(
          socket,
          host: uri.host,
          context: securityContext,
        );
        state.attachSocket(socket);
      }
      state.release();
      return socket;
    } on Object catch (error, stackTrace) {
      socket?.destroy();
      state.reset();
      if (state.isCancelled) {
        throw const SocketException('Connection attempt cancelled.');
      }
      firstError ??= error;
      firstStackTrace ??= stackTrace;
    }
  }

  final error = firstError ??
      const SocketException('No validated upstream address was reachable.');
  Error.throwWithStackTrace(error, firstStackTrace ?? StackTrace.current);
}

int _defaultPort(String scheme) => scheme == 'https' ? 443 : 80;

T? _firstOrNull<T>(List<T>? values) =>
    values == null || values.isEmpty ? null : values.first;

bool _isNonPublicAddress(InternetAddress address) {
  if (address.isLoopback || address.isLinkLocal || address.isMulticast) {
    return true;
  }
  final bytes = address.rawAddress;
  if (address.type == InternetAddressType.IPv4) {
    return _isNonPublicIpv4(bytes);
  }

  if (bytes.every((value) => value == 0)) {
    return true;
  }
  if (_isIpv4Embedded(bytes) || _isWellKnownNat64(bytes)) {
    return _isNonPublicIpv4(bytes.sublist(12));
  }
  final isLocalUseNat64 =
      bytes[0] == 0x00 &&
      bytes[1] == 0x64 &&
      bytes[2] == 0xff &&
      bytes[3] == 0x9b &&
      bytes[4] == 0x00 &&
      bytes[5] == 0x01;
  final isDiscardOnly =
      bytes[0] == 0x01 &&
      bytes.skip(1).take(7).every((value) => value == 0);
  final isIetfSpecialPurpose =
      bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] <= 0x01;
  final isSixToFour = bytes[0] == 0x20 && bytes[1] == 0x02;
  final isDocumentation =
      bytes[0] == 0x3f && bytes[1] == 0xff && (bytes[2] & 0xf0) == 0;
  final isUniqueLocal = (bytes[0] & 0xfe) == 0xfc;
  final isSiteLocal = bytes[0] == 0xfe && (bytes[1] & 0xc0) == 0xc0;
  return isLocalUseNat64 ||
      isDiscardOnly ||
      isIetfSpecialPurpose ||
      isSixToFour ||
      isDocumentation ||
      isUniqueLocal ||
      isSiteLocal;
}

bool _isNonPublicIpv4(List<int> bytes) {
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
      (a == 192 && b == 31 && c == 196) ||
      (a == 192 && b == 52 && c == 193) ||
      (a == 192 && b == 88 && c == 99) ||
      (a == 192 && b == 168) ||
      (a == 192 && b == 175 && c == 48) ||
      (a == 198 && (b == 18 || b == 19)) ||
      (a == 198 && b == 51 && c == 100) ||
      (a == 203 && b == 0 && c == 113) ||
      a >= 224;
}

bool _isWellKnownNat64(List<int> bytes) =>
    bytes.length == 16 &&
    bytes[0] == 0x00 &&
    bytes[1] == 0x64 &&
    bytes[2] == 0xff &&
    bytes[3] == 0x9b &&
    bytes.skip(4).take(8).every((value) => value == 0);

bool _isIpv4Embedded(List<int> bytes) {
  if (bytes.length != 16 || bytes.take(10).any((value) => value != 0)) {
    return false;
  }
  final compatible = bytes[10] == 0 && bytes[11] == 0;
  final mapped = bytes[10] == 0xff && bytes[11] == 0xff;
  return compatible || mapped;
}
