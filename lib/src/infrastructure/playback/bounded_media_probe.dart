import 'dart:async';
import 'dart:convert';

import '../../domain/models/hls_manifest.dart';
import '../../domain/models/playback_session.dart';
import '../../domain/models/source_security_policy.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/playback_proxy.dart';
import 'hls_manifest_parser.dart';
import 'proxy_upstream_client.dart';

/// Safe, bounded metadata for one upstream request. It intentionally retains
/// no URI path/query, header value, cookie or response body.
final class MediaProbeRequestShape {
  MediaProbeRequestShape({
    required this.method,
    required Uri requestUri,
    required this.redirectCount,
    required this.statusCode,
    required Iterable<String> headerNames,
    required Map<String, bool> headerPresence,
    required this.contentType,
    required this.bodyClassification,
    required this.rangeStrategy,
  }) : requestHost = requestUri.host,
       requestPort = _effectivePort(requestUri),
       headerNames = List<String>.unmodifiable(
         [...headerNames.map((name) => name.toLowerCase())]..sort(),
       ),
       headerPresence = Map<String, bool>.unmodifiable(headerPresence);

  final String method;
  final String requestHost;
  final int requestPort;
  final int redirectCount;
  final int statusCode;
  final List<String> headerNames;
  final Map<String, bool> headerPresence;
  final String? contentType;
  final String bodyClassification;
  final String rangeStrategy;

  Map<String, Object?> toRedactedDiagnostic() => {
    'method': method,
    'requestHost': requestHost,
    'requestPort': requestPort,
    'redirectCount': redirectCount,
    'statusCode': statusCode,
    'headerNames': headerNames,
    'headerPresence': headerPresence,
    'contentType': contentType,
    'bodyClassification': bodyClassification,
    'rangeStrategy': rangeStrategy,
  };
}

final class BoundedMediaProbeResult {
  BoundedMediaProbeResult({
    required this.passed,
    required this.reasonCode,
    required Iterable<MediaProbeRequestShape> requestShapes,
    this.finalStatusCode,
    this.finalContentType,
    this.finalBodyClassification,
  }) : requestShapes = List<MediaProbeRequestShape>.unmodifiable(requestShapes);

  final bool passed;
  final String reasonCode;
  final List<MediaProbeRequestShape> requestShapes;
  final int? finalStatusCode;
  final String? finalContentType;
  final String? finalBodyClassification;

  Map<String, Object?> toRedactedDiagnostic() => {
    'passed': passed,
    'reasonCode': reasonCode,
    'requestShapes': [
      for (final shape in requestShapes) shape.toRedactedDiagnostic(),
    ],
    'finalStatusCode': finalStatusCode,
    'finalContentType': finalContentType,
    'finalBodyClassification': finalBodyClassification,
  };
}

/// Reuses the probe's secret-safe body classification for the local loopback
/// verification path. It returns only a bounded class token.
String boundedMediaBodyClassification({
  required List<int> bytes,
  required int statusCode,
  required String? contentType,
  required Uri uri,
}) => _classifyBody(
  bytes: bytes,
  statusCode: statusCode,
  contentType: contentType,
  uri: uri,
);

/// Performs the exact bounded GET semantics that the playback proxy uses.
/// HEAD is deliberately not part of this probe: a provider can reject HEAD
/// while accepting a range GET, and HEAD therefore cannot prove playback.
final class BoundedMediaProbe {
  factory BoundedMediaProbe({
    required ProxyUpstreamClient upstreamClient,
    HlsManifestParser parser = const HlsManifestParser(),
    int maxProbeBytes = 128 * 1024,
  }) => BoundedMediaProbe._(upstreamClient, parser, maxProbeBytes);

  BoundedMediaProbe._(this._upstreamClient, this._parser, this.maxProbeBytes) {
    if (maxProbeBytes < 1024 || maxProbeBytes > 1024 * 1024) {
      throw ArgumentError.value(
        maxProbeBytes,
        'maxProbeBytes',
        'Must be between 1024 and 1048576.',
      );
    }
  }

  final ProxyUpstreamClient _upstreamClient;
  final HlsManifestParser _parser;
  final int maxProbeBytes;

  Future<BoundedMediaProbeResult> probe({
    required PlaybackSession session,
    required SourceSecurityPolicy securityPolicy,
    required PlaybackProxyBudget budget,
    required WebCandidateKind candidateKind,
    bool includeReferer = true,
    bool includeOrigin = true,
    bool includeUserAgent = true,
    bool includeCookies = true,
  }) async {
    if (!securityPolicy.allowsUri(session.mediaUri)) {
      return _failed('media_uri_not_allowed', const []);
    }

    final shapes = <MediaProbeRequestShape>[];
    final initial = await _fetch(
      uri: session.mediaUri,
      session: session,
      securityPolicy: securityPolicy,
      budget: budget,
      shapes: shapes,
      range: candidateKind == WebCandidateKind.hls
          ? null
          : _rangeFor(maxProbeBytes),
      rangeStrategy: candidateKind == WebCandidateKind.hls
          ? 'manifest_get'
          : 'bounded_range_get',
      includeReferer: includeReferer,
      includeOrigin: includeOrigin,
      includeUserAgent: includeUserAgent,
      includeCookies: includeCookies,
    );
    if (initial.failureCode != null) {
      return _failed(initial.failureCode!, shapes, initial);
    }
    final initialResponse = initial.response!;
    if (initialResponse.statusCode < 200 || initialResponse.statusCode > 299) {
      return _failed(
        _httpFailureCode(initialResponse.statusCode),
        shapes,
        initial,
      );
    }

    final initialClassification = _classifyBody(
      bytes: initial.body,
      statusCode: initialResponse.statusCode,
      contentType: initialResponse.contentType,
      uri: initial.finalUri,
    );
    if (candidateKind == WebCandidateKind.hls ||
        initialClassification == 'hls_manifest') {
      return _probeHls(
        initial: initial,
        session: session,
        securityPolicy: securityPolicy,
        budget: budget,
        shapes: shapes,
        includeReferer: includeReferer,
        includeOrigin: includeOrigin,
        includeUserAgent: includeUserAgent,
        includeCookies: includeCookies,
      );
    }

    final statusPass =
        initialResponse.statusCode == 206 || initialResponse.statusCode == 200;
    if (!statusPass || initialClassification != 'media') {
      return _failed(
        initialClassification == 'html_document'
            ? 'html_200_not_media'
            : 'media_body_not_playable',
        shapes,
        initial,
      );
    }
    return _passed('bounded_media_get', shapes, initial);
  }

  Future<BoundedMediaProbeResult> _probeHls({
    required _FetchResult initial,
    required PlaybackSession session,
    required SourceSecurityPolicy securityPolicy,
    required PlaybackProxyBudget budget,
    required List<MediaProbeRequestShape> shapes,
    required bool includeReferer,
    required bool includeOrigin,
    required bool includeUserAgent,
    required bool includeCookies,
  }) async {
    var playlistUri = initial.finalUri;
    var playlistBody = initial.body;
    HlsPlaylist playlist;
    try {
      playlist = _parser.parse(
        source: utf8.decode(playlistBody, allowMalformed: false),
        sourceUri: playlistUri,
      );
    } on Object {
      return _failed('hls_manifest_invalid', shapes, initial);
    }

    if (playlist is HlsMasterPlaylist) {
      final variant = _firstAllowedVariant(playlist.variants, securityPolicy);
      if (variant == null) {
        return _failed('hls_resource_not_allowed', shapes, initial);
      }
      playlistUri = variant.uri;
      final variantFetch = await _fetch(
        uri: playlistUri,
        session: session,
        securityPolicy: securityPolicy,
        budget: budget,
        shapes: shapes,
        range: null,
        rangeStrategy: 'manifest_get',
        includeReferer: includeReferer,
        includeOrigin: includeOrigin,
        includeUserAgent: includeUserAgent,
        includeCookies: includeCookies,
      );
      if (variantFetch.failureCode != null) {
        return _failed(variantFetch.failureCode!, shapes, variantFetch);
      }
      final response = variantFetch.response!;
      if (response.statusCode < 200 || response.statusCode > 299) {
        return _failed(
          _httpFailureCode(response.statusCode),
          shapes,
          variantFetch,
        );
      }
      playlistUri = variantFetch.finalUri;
      playlistBody = variantFetch.body;
      try {
        playlist = _parser.parse(
          source: utf8.decode(playlistBody, allowMalformed: false),
          sourceUri: playlistUri,
        );
      } on Object {
        return _failed('hls_variant_invalid', shapes, variantFetch);
      }
    }

    if (playlist is! HlsMediaPlaylist) {
      return _failed('hls_playlist_not_media', shapes, initial);
    }
    final segment = _firstAllowedSegment(playlist.segments, securityPolicy);
    if (segment == null) {
      return _failed('hls_resource_not_allowed', shapes, initial);
    }
    final segmentFetch = await _fetch(
      uri: segment.uri,
      session: session,
      securityPolicy: securityPolicy,
      budget: budget,
      shapes: shapes,
      range: _rangeFor(maxProbeBytes),
      rangeStrategy: 'segment_bounded_range_get',
      includeReferer: includeReferer,
      includeOrigin: includeOrigin,
      includeUserAgent: includeUserAgent,
      includeCookies: includeCookies,
    );
    if (segmentFetch.failureCode != null) {
      return _failed(segmentFetch.failureCode!, shapes, segmentFetch);
    }
    final response = segmentFetch.response!;
    final classification = _classifyBody(
      bytes: segmentFetch.body,
      statusCode: response.statusCode,
      contentType: response.contentType,
      uri: segmentFetch.finalUri,
    );
    if ((response.statusCode != 200 && response.statusCode != 206) ||
        classification != 'media') {
      return _failed('hls_segment_not_playable', shapes, segmentFetch);
    }
    return _passed('hls_manifest_and_segment_playable', shapes, segmentFetch);
  }

  Future<_FetchResult> _fetch({
    required Uri uri,
    required PlaybackSession session,
    required SourceSecurityPolicy securityPolicy,
    required PlaybackProxyBudget budget,
    required List<MediaProbeRequestShape> shapes,
    required String? range,
    required String rangeStrategy,
    required bool includeReferer,
    required bool includeOrigin,
    required bool includeUserAgent,
    required bool includeCookies,
  }) async {
    var currentUri = uri;
    var redirectCount = 0;
    final visited = <String>{currentUri.toString()};
    while (true) {
      if (!securityPolicy.allowsUri(currentUri)) {
        return _FetchResult.failure('redirect_uri_not_allowed', currentUri);
      }
      final headers = playbackUpstreamHeaders(
        session,
        requestUri: currentUri,
        range: range,
        includeReferer: includeReferer,
        includeOrigin: includeOrigin,
        includeUserAgent: includeUserAgent,
        includeCookies: includeCookies,
      );
      late ProxyUpstreamResponse response;
      try {
        response = await _upstreamClient
            .send(
              ProxyUpstreamRequest(
                uri: currentUri,
                method: 'GET',
                headers: headers,
                timeout: budget.upstreamTimeout,
              ),
            )
            .timeout(budget.upstreamTimeout);
      } on TimeoutException {
        return _FetchResult.failure('probe_timeout', currentUri);
      } on ProxyUpstreamSecurityException catch (error) {
        return _FetchResult.failure(error.code, currentUri);
      } on Object {
        return _FetchResult.failure('probe_transport_failed', currentUri);
      }

      if (_isRedirect(response.statusCode)) {
        shapes.add(
          _shape(
            requestUri: currentUri,
            statusCode: response.statusCode,
            headers: headers,
            contentType: response.contentType,
            bodyClassification: 'redirect',
            redirectCount: redirectCount,
            rangeStrategy: rangeStrategy,
          ),
        );
        try {
          await _discard(response.body, budget.upstreamTimeout);
        } on TimeoutException {
          return _FetchResult.failure('probe_timeout', currentUri);
        } on Object {
          return _FetchResult.failure('probe_transport_failed', currentUri);
        }
        if (redirectCount >= budget.maxRedirects) {
          return _FetchResult.failure('redirect_budget_exceeded', currentUri);
        }
        final location = response.firstLocation;
        if (location == null || location.trim().isEmpty) {
          return _FetchResult.failure('redirect_location_missing', currentUri);
        }
        Uri next;
        try {
          next = currentUri.resolve(location.trim());
        } on Object {
          return _FetchResult.failure('redirect_uri_invalid', currentUri);
        }
        if (next.userInfo.isNotEmpty ||
            next.fragment.isNotEmpty ||
            !securityPolicy.allowsUri(next) ||
            !visited.add(next.toString())) {
          return _FetchResult.failure('redirect_uri_not_allowed', next);
        }
        currentUri = next;
        redirectCount += 1;
        continue;
      }

      final List<int> bytes;
      try {
        bytes = await _readBounded(
          response.body,
          maxProbeBytes,
          budget.upstreamTimeout,
        );
      } on TimeoutException {
        return _FetchResult.failure('probe_timeout', currentUri);
      } on Object {
        return _FetchResult.failure('probe_transport_failed', currentUri);
      }
      final classification = _classifyBody(
        bytes: bytes,
        statusCode: response.statusCode,
        contentType: response.contentType,
        uri: currentUri,
      );
      shapes.add(
        _shape(
          requestUri: currentUri,
          statusCode: response.statusCode,
          headers: headers,
          contentType: response.contentType,
          bodyClassification: classification,
          redirectCount: redirectCount,
          rangeStrategy: rangeStrategy,
        ),
      );
      return _FetchResult(
        response: response,
        body: bytes,
        finalUri: currentUri,
        failureCode: null,
      );
    }
  }

  MediaProbeRequestShape _shape({
    required Uri requestUri,
    required int statusCode,
    required Map<String, String> headers,
    required String? contentType,
    required String bodyClassification,
    required int redirectCount,
    required String rangeStrategy,
  }) => MediaProbeRequestShape(
    method: 'GET',
    requestUri: requestUri,
    redirectCount: redirectCount,
    statusCode: statusCode,
    headerNames: headers.keys.toList(growable: false),
    headerPresence: {
      for (final name in const [
        'accept',
        'accept-language',
        'authorization',
        'cookie',
        'origin',
        'range',
        'referer',
        'user-agent',
      ])
        name: headers.containsKey(name),
    },
    contentType: _contentType(contentType),
    bodyClassification: bodyClassification,
    rangeStrategy: rangeStrategy,
  );

  BoundedMediaProbeResult _passed(
    String code,
    List<MediaProbeRequestShape> shapes,
    _FetchResult finalFetch,
  ) => BoundedMediaProbeResult(
    passed: true,
    reasonCode: code,
    requestShapes: shapes,
    finalStatusCode: finalFetch.response?.statusCode,
    finalContentType: _contentType(finalFetch.response?.contentType),
    finalBodyClassification: _classifyBody(
      bytes: finalFetch.body,
      statusCode: finalFetch.response?.statusCode ?? 0,
      contentType: finalFetch.response?.contentType,
      uri: finalFetch.finalUri,
    ),
  );

  BoundedMediaProbeResult _failed(
    String code,
    List<MediaProbeRequestShape> shapes, [
    _FetchResult? finalFetch,
  ]) => BoundedMediaProbeResult(
    passed: false,
    reasonCode: code,
    requestShapes: shapes,
    finalStatusCode: finalFetch?.response?.statusCode,
    finalContentType: _contentType(finalFetch?.response?.contentType),
    finalBodyClassification: finalFetch == null
        ? null
        : _classifyBody(
            bytes: finalFetch.body,
            statusCode: finalFetch.response?.statusCode ?? 0,
            contentType: finalFetch.response?.contentType,
            uri: finalFetch.finalUri,
          ),
  );
}

final class _FetchResult {
  _FetchResult({
    required this.response,
    required this.body,
    required this.finalUri,
    required this.failureCode,
  });

  _FetchResult.failure(this.failureCode, this.finalUri)
    : response = null,
      body = const [];

  final ProxyUpstreamResponse? response;
  final List<int> body;
  final Uri finalUri;
  final String? failureCode;
}

String _rangeFor(int maximumBytes) => 'bytes=0-${maximumBytes - 1}';

HlsVariantStream? _firstAllowedVariant(
  Iterable<HlsVariantStream> variants,
  SourceSecurityPolicy policy,
) {
  for (final variant in variants) {
    if (policy.allowsUri(variant.uri)) return variant;
  }
  return null;
}

HlsMediaSegment? _firstAllowedSegment(
  Iterable<HlsMediaSegment> segments,
  SourceSecurityPolicy policy,
) {
  for (final segment in segments) {
    if (policy.allowsUri(segment.uri)) return segment;
  }
  return null;
}

Future<List<int>> _readBounded(
  Stream<List<int>> body,
  int maximumBytes,
  Duration timeout,
) async {
  final bytes = <int>[];
  final iterator = StreamIterator<List<int>>(body);
  final stopwatch = Stopwatch()..start();
  try {
    while (bytes.length < maximumBytes) {
      final timeRemaining = timeout - stopwatch.elapsed;
      if (timeRemaining <= Duration.zero) {
        throw TimeoutException('Bounded media probe body timed out.');
      }
      final hasNext = await iterator.moveNext().timeout(timeRemaining);
      if (!hasNext) break;
      final chunk = iterator.current;
      final byteRemaining = maximumBytes - bytes.length;
      bytes.addAll(
        chunk.length <= byteRemaining ? chunk : chunk.take(byteRemaining),
      );
      if (bytes.length == maximumBytes) break;
    }
  } finally {
    final remaining = timeout - stopwatch.elapsed;
    try {
      await iterator.cancel().timeout(
        remaining <= Duration.zero ? Duration.zero : remaining,
      );
    } on Object {
      // The probe result remains bounded even when an upstream cancellation
      // future does not settle.
    }
  }
  return List<int>.unmodifiable(bytes);
}

Future<void> _discard(Stream<List<int>> body, Duration timeout) async {
  final subscription = body.listen((_) {});
  await subscription.cancel().timeout(timeout);
}

String _classifyBody({
  required List<int> bytes,
  required int statusCode,
  required String? contentType,
  required Uri uri,
}) {
  if (bytes.isEmpty) return 'empty';
  final type = _contentType(contentType) ?? '';
  final ascii = String.fromCharCodes(
    bytes
        .take(8192)
        .map((value) => value >= 0x20 && value <= 0x7e ? value : 0x20),
  ).trimLeft().toLowerCase();
  if (type.contains('html') ||
      ascii.startsWith('<!doctype html') ||
      ascii.startsWith('<html') ||
      ascii.contains('<html')) {
    return statusCode >= 400 ? 'html_access_denied_page' : 'html_document';
  }
  if (type.contains('json') || ascii.startsWith('{') || ascii.startsWith('[')) {
    return 'provider_json_error';
  }
  if (ascii.contains('captcha') ||
      ascii.contains('challenge') ||
      ascii.contains('cf-chl')) {
    return 'challenge_page';
  }
  if (statusCode >= 400) {
    return statusCode == 403 ? 'generic_cdn_forbidden' : 'http_error';
  }
  if (_looksLikeHls(uri, type)) return 'hls_manifest';
  if (_isMediaType(type) || _looksLikeMedia(uri)) return 'media';
  return 'unexpected_body';
}

String? _contentType(String? value) {
  if (value == null) return null;
  final normalized = value.split(';').first.trim().toLowerCase();
  if (normalized.isEmpty || normalized.length > 128) return null;
  return normalized;
}

bool _isMediaType(String value) =>
    value.startsWith('video/') ||
    value.startsWith('audio/') ||
    value == 'application/octet-stream' ||
    value == 'video/mp2t' ||
    value == 'application/mp4';

bool _looksLikeMedia(Uri uri) => RegExp(
  r'\.(?:mp4|m4a|m4s|ts|aac|mp3|webm|mkv|mov|cmfv|cmfa)$',
).hasMatch(uri.path.toLowerCase());

bool _looksLikeHls(Uri uri, String contentType) =>
    uri.path.toLowerCase().endsWith('.m3u8') ||
    contentType == 'application/vnd.apple.mpegurl' ||
    contentType == 'application/x-mpegurl' ||
    contentType == 'audio/mpegurl';

String _httpFailureCode(int status) =>
    status == 403 ? 'external_http_403' : 'http_status_$status';

bool _isRedirect(int status) =>
    status == 300 ||
    status == 301 ||
    status == 302 ||
    status == 303 ||
    status == 307 ||
    status == 308;

int _effectivePort(Uri uri) =>
    uri.hasPort ? uri.port : (uri.scheme.toLowerCase() == 'https' ? 443 : 80);
