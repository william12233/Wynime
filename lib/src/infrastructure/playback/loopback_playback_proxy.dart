import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:wynime/src/domain/models/hls_manifest.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_fingerprinter.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_parser.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_sanitizer.dart';
import 'package:wynime/src/infrastructure/playback/hls_playlist_rewriter.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';

final class LoopbackPlaybackProxyService implements PlaybackProxyService {
  factory LoopbackPlaybackProxyService({
    ProxyUpstreamClient? upstreamClient,
    Random? random,
    HlsPlaylistRewriter rewriter = const HlsPlaylistRewriter(),
    HlsManifestParser parser = const HlsManifestParser(),
    HlsManifestFingerprinter fingerprinter = const HlsManifestFingerprinter(),
    HlsManifestSanitizer sanitizer = const HlsManifestSanitizer(),
  }) => LoopbackPlaybackProxyService._(
    upstreamClient ?? DartIoProxyUpstreamClient(),
    random ?? Random.secure(),
    rewriter,
    parser,
    fingerprinter,
    sanitizer,
  );

  LoopbackPlaybackProxyService._(
    this._upstreamClient,
    this._random,
    this._rewriter,
    this._parser,
    this._fingerprinter,
    this._sanitizer,
  );

  final ProxyUpstreamClient _upstreamClient;
  final Random _random;
  final HlsPlaylistRewriter _rewriter;
  final HlsManifestParser _parser;
  final HlsManifestFingerprinter _fingerprinter;
  final HlsManifestSanitizer _sanitizer;
  final Map<String, _ProxySession> _sessions = {};
  HttpServer? _server;
  LoopbackAddressFamily? _addressFamily;
  bool _closed = false;

  @override
  Future<PlaybackProxyLease> expose(PlaybackProxyRequest request) async {
    if (_closed) {
      throw StateError('Playback proxy service is closed.');
    }
    if (request.session.isExpired) {
      throw PlaybackProxyException(
        'session_expired',
        'An expired PlaybackSession cannot be exposed.',
      );
    }

    final server = await _ensureServer(request.addressFamily);
    final token = _uniqueToken(_sessions.keys);
    final proxySession = _ProxySession(
      token: token,
      session: request.session,
      securityPolicy: request.securityPolicy,
      budget: request.budget,
      baseUri: _baseUri(server),
      random: _random,
    );
    _sessions[token] = proxySession;
    final playbackUri = proxySession.register(request.session.mediaUri);

    return PlaybackProxyLease(
      sessionId: request.session.sessionId,
      playbackUri: playbackUri,
      close: () async {
        final removed = _sessions.remove(token);
        removed?.close();
      },
    );
  }

  Future<HttpServer> _ensureServer(LoopbackAddressFamily family) async {
    final existing = _server;
    if (existing != null) {
      if (_addressFamily != family) {
        throw StateError(
          'A proxy service instance cannot mix IPv4 and IPv6 listeners.',
        );
      }
      return existing;
    }

    final address = switch (family) {
      LoopbackAddressFamily.ipv4 => InternetAddress.loopbackIPv4,
      LoopbackAddressFamily.ipv6 => InternetAddress.loopbackIPv6,
    };
    if (!address.isLoopback) {
      throw StateError('Refusing to bind a non-loopback address.');
    }
    final server = await HttpServer.bind(address, 0, shared: false);
    server.autoCompress = false;
    _server = server;
    _addressFamily = family;
    unawaited(_serve(server));
    return server;
  }

  Future<void> _serve(HttpServer server) async {
    try {
      await for (final request in server) {
        unawaited(_handleRequest(request));
      }
    } on Object {
      // Server shutdown ends iteration. No upstream URI or secret is logged.
    }
  }

  Future<void> _handleRequest(HttpRequest request) async {
    final response = request.response;
    response.bufferOutput = false;
    try {
      final remote = request.connectionInfo?.remoteAddress;
      if (remote == null || !remote.isLoopback) {
        await _writeError(response, HttpStatus.forbidden);
        return;
      }
      if (request.requestedUri.hasQuery || request.requestedUri.hasFragment) {
        await _writeError(response, HttpStatus.notFound);
        return;
      }
      if (request.method != 'GET' && request.method != 'HEAD') {
        response.headers.set(HttpHeaders.allowHeader, 'GET, HEAD');
        await _writeError(response, HttpStatus.methodNotAllowed);
        return;
      }

      final segments = request.requestedUri.pathSegments;
      if (segments.length != 5 ||
          segments[0] != 'v1' ||
          segments[1] != 'session' ||
          segments[3] != 'resource') {
        await _writeError(response, HttpStatus.notFound);
        return;
      }
      final proxySession = _sessions[segments[2]];
      final upstreamUri = proxySession?.resource(segments[4]);
      if (proxySession == null ||
          upstreamUri == null ||
          proxySession.isClosed) {
        await _writeError(response, HttpStatus.notFound);
        return;
      }
      if (proxySession.session.isExpired) {
        await _writeError(response, HttpStatus.gone);
        return;
      }

      await _proxyResource(
        request: request,
        proxySession: proxySession,
        initialUri: upstreamUri,
      );
    } on PlaybackProxyException {
      await _writeError(response, HttpStatus.badGateway);
    } on Object {
      await _writeError(response, HttpStatus.badGateway);
    }
  }

  Future<void> _proxyResource({
    required HttpRequest request,
    required _ProxySession proxySession,
    required Uri initialUri,
  }) async {
    proxySession.throwIfClosed();
    final range = request.headers.value(HttpHeaders.rangeHeader);
    if (range != null && !_validSingleRange(range)) {
      await _writeError(
        request.response,
        HttpStatus.requestedRangeNotSatisfiable,
      );
      return;
    }

    final outboundHeaders = _outboundHeaders(proxySession.session, range);
    if (_encodedHeaderBytes(outboundHeaders) >
        proxySession.budget.maxRequestHeaderBytes) {
      throw PlaybackProxyException(
        'request_header_budget_exceeded',
        'Forwarded request headers exceed the configured byte budget.',
      );
    }

    var currentUri = initialUri;
    var redirectCount = 0;
    final visited = <String>{currentUri.toString()};
    late ProxyUpstreamResponse upstream;
    while (true) {
      upstream = await proxySession.race(
        _upstreamClient.send(
          ProxyUpstreamRequest(
            uri: currentUri,
            method: request.method,
            headers: outboundHeaders,
            timeout: proxySession.budget.upstreamTimeout,
          ),
        ),
      );
      if (!_isRedirect(upstream.statusCode)) {
        break;
      }
      await _cancelBody(upstream.body);
      final location = upstream.firstLocation;
      if (location == null || location.trim().isEmpty) {
        throw PlaybackProxyException(
          'invalid_redirect',
          'Redirect response omitted Location.',
        );
      }
      if (redirectCount >= proxySession.budget.maxRedirects) {
        throw PlaybackProxyException(
          'redirect_budget_exceeded',
          'Upstream redirect budget exceeded.',
        );
      }
      final next = currentUri.resolve(location);
      if (!proxySession.securityPolicy.allowsUri(next)) {
        throw PlaybackProxyException(
          'redirect_outside_allowlist',
          'Redirect target is outside the source allowlist.',
        );
      }
      if (!visited.add(next.toString())) {
        throw PlaybackProxyException(
          'redirect_loop',
          'Upstream redirect loop detected.',
        );
      }
      currentUri = next;
      redirectCount += 1;
    }

    proxySession.throwIfClosed();
    final response = request.response;
    response.statusCode = upstream.statusCode;
    _copyResponseHeaders(upstream.headers, response.headers);

    if (request.method == 'HEAD') {
      await _cancelBody(upstream.body);
      await response.close();
      return;
    }

    final isPlaylist =
        upstream.statusCode == HttpStatus.ok &&
        range == null &&
        _looksLikeHlsPlaylist(currentUri, upstream.contentType);
    if (isPlaylist) {
      final bytes = await _readBounded(
        upstream.body,
        proxySession.budget.maxPlaylistBytes,
        proxySession,
      );
      late String playlist;
      try {
        playlist = utf8.decode(bytes, allowMalformed: false);
      } on FormatException {
        throw PlaybackProxyException(
          'invalid_playlist',
          'Playlist is not valid UTF-8.',
        );
      }
      var playerPlaylist = playlist;
      final adRemovalPlan = proxySession.session.adRemovalPlan;
      if (adRemovalPlan.isActive) {
        try {
          final parsed = _parser.parse(source: playlist, sourceUri: currentUri);
          final fingerprint = _fingerprinter.fingerprint(parsed);
          adRemovalPlan.verifyManifestFingerprint(fingerprint);
          if (parsed is HlsMasterPlaylist &&
              adRemovalPlan.removals.isNotEmpty) {
            throw PlaybackProxyException(
              'media_playlist_required',
              'A removal plan cannot be applied to a master playlist.',
            );
          }
          if (parsed is HlsMediaPlaylist && adRemovalPlan.removals.isNotEmpty) {
            playerPlaylist = _sanitizer.sanitize(
              playlist: parsed,
              plan: adRemovalPlan,
              actualFingerprint: fingerprint,
            );
          }
        } on HlsManifestParseException {
          throw PlaybackProxyException(
            'invalid_playlist',
            'Playlist parsing failed closed.',
          );
        } on HlsSanitizationException catch (error) {
          throw PlaybackProxyException(error.code, error.message);
        } on StateError {
          throw PlaybackProxyException(
            'fingerprint_mismatch',
            'AdRemovalPlan does not match the current manifest.',
          );
        }
      }

      late String rewritten;
      try {
        rewritten = _rewriter.rewrite(
          playlist: playerPlaylist,
          baseUri: currentUri,
          register: proxySession.register,
        );
      } on FormatException {
        throw PlaybackProxyException(
          'invalid_playlist',
          'Playlist parsing or URI rewriting failed.',
        );
      }
      final encoded = utf8.encode(rewritten);
      if (encoded.length > proxySession.budget.maxPlaylistBytes ||
          encoded.length > proxySession.budget.maxResponseBytes) {
        throw PlaybackProxyException(
          'playlist_budget_exceeded',
          'Rewritten playlist exceeds the configured byte budget.',
        );
      }
      response.headers
        ..removeAll(HttpHeaders.contentLengthHeader)
        ..set(
          HttpHeaders.contentTypeHeader,
          'application/vnd.apple.mpegurl; charset=utf-8',
        )
        ..set(HttpHeaders.contentLengthHeader, encoded.length)
        ..set(HttpHeaders.cacheControlHeader, 'no-store');
      response.add(encoded);
      await response.close();
      return;
    }

    final declaredLength = upstream.contentLength;
    if (declaredLength != null &&
        declaredLength > proxySession.budget.maxResponseBytes) {
      await _cancelBody(upstream.body);
      throw PlaybackProxyException(
        'response_budget_exceeded',
        'Upstream response exceeds the configured byte budget.',
      );
    }

    final iterator = StreamIterator<List<int>>(upstream.body);
    var bytesWritten = 0;
    try {
      while (await proxySession.race(iterator.moveNext())) {
        final chunk = iterator.current;
        bytesWritten += chunk.length;
        if (bytesWritten > proxySession.budget.maxResponseBytes) {
          throw PlaybackProxyException(
            'response_budget_exceeded',
            'Upstream response exceeds the configured byte budget.',
          );
        }
        response.add(chunk);
      }
    } finally {
      await iterator.cancel();
    }
    await response.close();
  }

  @override
  Future<void> close() async {
    if (_closed) {
      return;
    }
    _closed = true;
    for (final session in _sessions.values) {
      session.close();
    }
    _sessions.clear();
    final server = _server;
    _server = null;
    if (server != null) {
      await server.close(force: true);
    }
    await _upstreamClient.close();
  }

  String _uniqueToken(Iterable<String> existing) {
    var token = _randomToken(_random, 32);
    while (existing.contains(token)) {
      token = _randomToken(_random, 32);
    }
    return token;
  }
}

final class _ProxySession {
  _ProxySession({
    required this.token,
    required this.session,
    required this.securityPolicy,
    required this.budget,
    required this.baseUri,
    required this.random,
  });

  final String token;
  final PlaybackSession session;
  final SourceSecurityPolicy securityPolicy;
  final PlaybackProxyBudget budget;
  final Uri baseUri;
  final Random random;
  final Map<String, Uri> _resources = {};
  final Map<String, String> _resourceIdsByUri = {};
  final Completer<void> _closed = Completer<void>();

  bool get isClosed => _closed.isCompleted;

  Uri register(Uri upstreamUri) {
    throwIfClosed();
    if (!securityPolicy.allowsUri(upstreamUri)) {
      throw PlaybackProxyException(
        'resource_outside_allowlist',
        'HLS child resource is outside the source allowlist.',
      );
    }
    final key = upstreamUri.toString();
    final existing = _resourceIdsByUri[key];
    if (existing != null) {
      return _proxyUri(existing);
    }
    if (_resources.length >= budget.maxRegisteredResources) {
      throw PlaybackProxyException(
        'resource_budget_exceeded',
        'Registered HLS resource budget exceeded.',
      );
    }
    var resourceId = _randomToken(random, 18);
    while (_resources.containsKey(resourceId)) {
      resourceId = _randomToken(random, 18);
    }
    _resources[resourceId] = upstreamUri;
    _resourceIdsByUri[key] = resourceId;
    return _proxyUri(resourceId);
  }

  Uri? resource(String resourceId) => _resources[resourceId];

  Future<T> race<T>(Future<T> operation) => Future.any<T>([
    operation,
    _closed.future.then<T>((_) {
      throw PlaybackProxyException(
        'session_closed',
        'Playback proxy session was closed.',
      );
    }),
  ]);

  void throwIfClosed() {
    if (isClosed) {
      throw PlaybackProxyException(
        'session_closed',
        'Playback proxy session was closed.',
      );
    }
  }

  void close() {
    if (!isClosed) {
      _closed.complete();
    }
    _resources.clear();
    _resourceIdsByUri.clear();
  }

  Uri _proxyUri(String resourceId) => baseUri.replace(
    pathSegments: ['v1', 'session', token, 'resource', resourceId],
  );
}

final class PlaybackProxyException implements Exception {
  PlaybackProxyException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'PlaybackProxyException($code): $message';
}

Uri _baseUri(HttpServer server) =>
    Uri(scheme: 'http', host: server.address.address, port: server.port);

Map<String, String> _outboundHeaders(PlaybackSession session, String? range) {
  final headers = <String, String>{...session.headers};
  headers.remove(HttpHeaders.cookieHeader);
  headers.remove(HttpHeaders.rangeHeader);
  headers[HttpHeaders.acceptEncodingHeader] = 'identity';
  if (session.referer != null) {
    headers[HttpHeaders.refererHeader] = session.referer.toString();
  }
  if (session.origin != null) {
    headers['origin'] = session.origin.toString();
  }
  if (session.userAgent != null) {
    headers[HttpHeaders.userAgentHeader] = session.userAgent!;
  }
  if (session.cookies.isNotEmpty) {
    headers[HttpHeaders.cookieHeader] = session.cookies.entries
        .map((entry) => '${entry.key}=${entry.value}')
        .join('; ');
  }
  if (range != null) {
    headers[HttpHeaders.rangeHeader] = range;
  }
  return headers;
}

void _copyResponseHeaders(
  Map<String, List<String>> source,
  HttpHeaders destination,
) {
  const allowed = {
    'accept-ranges',
    'cache-control',
    'content-length',
    'content-range',
    'content-type',
    'etag',
    'last-modified',
  };
  for (final entry in source.entries) {
    if (!allowed.contains(entry.key)) {
      continue;
    }
    destination.removeAll(entry.key);
    for (final value in entry.value) {
      destination.add(entry.key, value);
    }
  }
}

Future<List<int>> _readBounded(
  Stream<List<int>> body,
  int maximum,
  _ProxySession session,
) async {
  final builder = BytesBuilder(copy: false);
  final iterator = StreamIterator<List<int>>(body);
  var total = 0;
  try {
    while (await session.race(iterator.moveNext())) {
      final chunk = iterator.current;
      total += chunk.length;
      if (total > maximum) {
        throw PlaybackProxyException(
          'response_budget_exceeded',
          'Upstream response exceeds the configured byte budget.',
        );
      }
      builder.add(chunk);
    }
  } finally {
    await iterator.cancel();
  }
  return builder.takeBytes();
}

Future<void> _cancelBody(Stream<List<int>> body) async {
  final subscription = body.listen((_) {});
  await subscription.cancel();
}

int _encodedHeaderBytes(Map<String, String> values) => values.entries.fold(
  0,
  (total, entry) =>
      total +
      utf8.encode(entry.key).length +
      utf8.encode(entry.value).length +
      4,
);

Future<void> _writeError(HttpResponse response, int status) async {
  try {
    response.statusCode = status;
    response.headers
      ..set(HttpHeaders.cacheControlHeader, 'no-store')
      ..set(HttpHeaders.contentLengthHeader, 0);
    await response.close();
  } on Object {
    // Client disconnects and already-committed responses are intentionally quiet.
  }
}

bool _looksLikeHlsPlaylist(Uri uri, String? contentType) {
  final type = contentType?.toLowerCase() ?? '';
  return uri.path.toLowerCase().endsWith('.m3u8') ||
      type.contains('mpegurl') ||
      type.contains('vnd.apple.mpegurl');
}

bool _validSingleRange(String value) {
  final match = RegExp(r'^bytes=(\d*)-(\d*)$').firstMatch(value.trim());
  if (match == null) {
    return false;
  }
  return match.group(1)!.isNotEmpty || match.group(2)!.isNotEmpty;
}

bool _isRedirect(int status) =>
    status == HttpStatus.movedPermanently ||
    status == HttpStatus.found ||
    status == HttpStatus.seeOther ||
    status == HttpStatus.temporaryRedirect ||
    status == HttpStatus.permanentRedirect;

String _randomToken(Random random, int byteCount) {
  final bytes = List<int>.generate(byteCount, (_) => random.nextInt(256));
  return base64Url.encode(bytes).replaceAll('=', '');
}
