// The public constructor keeps public parameter names while implementation
// fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'dart:async';

import 'package:http/http.dart' as http;

import '../../domain/models/source_registry_models.dart';
import '../source_rules/source_package_decoder.dart';
import '../source_rules/source_registry_artifact_catalog.dart';
import '../source_rules/source_registry_index_decoder.dart';

const _githubRawHost = 'raw.githubusercontent.com';
const _defaultIndexPath = 'sources/index.json';
const _defaultTimeout = Duration(seconds: 15);
const _defaultMaxTotalPackageBytes = 8 * 1024 * 1024;

/// The bounded response returned by a source-registry transport.
final class SourceRegistryHttpResponse {
  SourceRegistryHttpResponse({
    required this.statusCode,
    required List<int> bodyBytes,
    Map<String, String> headers = const <String, String>{},
  }) : bodyBytes = List<int>.unmodifiable(bodyBytes),
       headers = Map<String, String>.unmodifiable(headers);

  final int statusCode;
  final List<int> bodyBytes;
  final Map<String, String> headers;
}

/// The only HTTP capability needed by the GitHub registry repository.
///
/// Keeping this port small lets deterministic tests supply exact response
/// bytes without granting the repository a general-purpose network client.
abstract interface class SourceRegistryHttpTransport {
  Future<SourceRegistryHttpResponse> get(
    Uri uri, {
    required int maxResponseBytes,
  });

  void close();
}

final class SourceRegistryTransportException implements Exception {
  const SourceRegistryTransportException(this.code);

  final String code;

  @override
  String toString() => 'SourceRegistryTransportException($code)';
}

/// Default HTTPS transport for the fixed GitHub raw-content host.
///
/// It never follows redirects, sends no credentials or cookies, and reads
/// each response through the caller's byte budget.
final class IoSourceRegistryHttpTransport
    implements SourceRegistryHttpTransport {
  IoSourceRegistryHttpTransport({
    http.Client? client,
    this.timeout = _defaultTimeout,
  }) : _client = client ?? http.Client(),
       _ownsClient = client == null {
    if (timeout <= Duration.zero) {
      throw ArgumentError.value(timeout, 'timeout', 'Must be positive.');
    }
  }

  final http.Client _client;
  final bool _ownsClient;
  final Duration timeout;
  bool _closed = false;

  @override
  Future<SourceRegistryHttpResponse> get(
    Uri uri, {
    required int maxResponseBytes,
  }) async {
    if (_closed) {
      throw const SourceRegistryTransportException('transport_closed');
    }
    if (maxResponseBytes <= 0) {
      throw const SourceRegistryTransportException('response_limit_invalid');
    }
    _validateGitHubRawUri(uri);

    try {
      final request = http.Request('GET', uri)
        ..followRedirects = false
        ..maxRedirects = 0
        ..headers['Accept'] = 'application/octet-stream'
        ..headers['User-Agent'] = 'Wynime-source-registry';
      final response = await _client.send(request).timeout(timeout);
      final contentLength = response.contentLength;
      if (contentLength != null && contentLength > maxResponseBytes) {
        throw const SourceRegistryTransportException('response_too_large');
      }
      final bytes = <int>[];
      await for (final chunk in response.stream.timeout(timeout)) {
        if (bytes.length + chunk.length > maxResponseBytes) {
          throw const SourceRegistryTransportException('response_too_large');
        }
        bytes.addAll(chunk);
      }
      return SourceRegistryHttpResponse(
        statusCode: response.statusCode,
        bodyBytes: bytes,
        headers: response.headers,
      );
    } on SourceRegistryTransportException {
      rethrow;
    } on TimeoutException {
      throw const SourceRegistryTransportException('request_timeout');
    } on http.ClientException {
      throw const SourceRegistryTransportException('request_failed');
    } on Object {
      throw const SourceRegistryTransportException('request_failed');
    }
  }

  @override
  void close() {
    if (_closed) return;
    _closed = true;
    if (_ownsClient) _client.close();
  }
}

/// Stable, secret-safe failure from the remote source-registry boundary.
final class SourceRegistryRepositoryException implements Exception {
  const SourceRegistryRepositoryException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourceRegistryRepositoryException($code): $message';
}

/// Read-only GitHub raw-content adapter for one immutable registry shape.
///
/// The adapter fetches the index first, decodes its exact package set, then
/// fetches each indexed package sequentially under bounded response and total
/// byte budgets. The existing artifact catalog loader remains the only place
/// that composes and verifies the snapshot. This class never installs,
/// persists, enables or activates a package and does not treat signatures as
/// runtime authority.
final class GitHubSourceRegistryRepository {
  GitHubSourceRegistryRepository({
    required String owner,
    required String repository,
    required String ref,
    String indexPath = _defaultIndexPath,
    SourceRegistryHttpTransport? transport,
    SourceRegistryIndexDecoder indexDecoder =
        const SourceRegistryIndexDecoder(),
    SourceRegistryArtifactCatalogLoader catalogLoader =
        const SourceRegistryArtifactCatalogLoader(),
    this.maxTotalPackageBytes = _defaultMaxTotalPackageBytes,
  }) : owner = _validateRepositoryPart(owner, 'owner', 39),
       repository = _validateRepositoryPart(repository, 'repository', 100),
       ref = _validateRef(ref),
       indexPath = _validateIndexPath(indexPath),
       _transport = transport ?? IoSourceRegistryHttpTransport(),
       _ownsTransport = transport == null,
       _indexDecoder = indexDecoder,
       _catalogLoader = catalogLoader {
    if (maxTotalPackageBytes <= 0 ||
        maxTotalPackageBytes >
            SourceRegistryIndex.maxEntries *
                SourcePackageDecoder.maxPackageBytes) {
      throw ArgumentError.value(
        maxTotalPackageBytes,
        'maxTotalPackageBytes',
        'Must be within the bounded registry package budget.',
      );
    }
  }

  final String owner;
  final String repository;
  final String ref;
  final String indexPath;
  final int maxTotalPackageBytes;
  final SourceRegistryHttpTransport _transport;
  final bool _ownsTransport;
  final SourceRegistryIndexDecoder _indexDecoder;
  final SourceRegistryArtifactCatalogLoader _catalogLoader;

  Future<SourceRegistryCatalog>? _inFlight;
  bool _closed = false;

  /// Loads one all-or-nothing catalog. Concurrent callers share one request.
  Future<SourceRegistryCatalog> loadCatalog() {
    if (_closed) {
      return Future<SourceRegistryCatalog>.error(
        const SourceRegistryRepositoryException(
          'repository_closed',
          'The source registry repository is closed.',
        ),
      );
    }
    final current = _inFlight;
    if (current != null) return current;

    final future = _loadCatalog();
    _inFlight = future;
    future.then<void>(
      (_) => _clearInFlight(future),
      onError: (_, _) => _clearInFlight(future),
    );
    return future;
  }

  /// Prevents new work and closes only a transport owned by this repository.
  void close() {
    if (_closed) return;
    _closed = true;
    if (_ownsTransport) _transport.close();
  }

  Future<SourceRegistryCatalog> _loadCatalog() async {
    final indexBytes = await _fetch(
      indexPath,
      maxResponseBytes: SourceRegistryIndex.maxIndexBytes,
      kind: 'index',
    );
    _ensureOpen();

    final SourceRegistryIndex index;
    try {
      index = _indexDecoder.decodeBytes(indexBytes);
    } on SourceRegistryIndexFormatException {
      throw const SourceRegistryRepositoryException(
        'index_format_invalid',
        'The remote source registry index failed schema validation.',
      );
    } on Object {
      throw const SourceRegistryRepositoryException(
        'index_format_invalid',
        'The remote source registry index failed schema validation.',
      );
    }

    final packageBytesByPath = <String, List<int>>{};
    var totalPackageBytes = 0;
    for (final entry in index.packages) {
      _ensureOpen();
      final relativePath = index.relativePathFor(entry);
      final packageBytes = await _fetch(
        relativePath,
        maxResponseBytes: SourcePackageDecoder.maxPackageBytes,
        kind: 'artifact',
      );
      totalPackageBytes += packageBytes.length;
      if (totalPackageBytes > maxTotalPackageBytes) {
        throw const SourceRegistryRepositoryException(
          'total_artifact_limit',
          'The source registry package set exceeds its total byte limit.',
        );
      }
      packageBytesByPath[relativePath] = packageBytes;
    }

    _ensureOpen();
    try {
      return _catalogLoader.load(
        indexBytes: indexBytes,
        packageBytesByPath: packageBytesByPath,
      );
    } on SourceRegistryCatalogException catch (error) {
      throw SourceRegistryRepositoryException(
        _safeCode(error.code, fallback: 'catalog_invalid'),
        'The remote source registry snapshot failed catalog validation.',
      );
    } on Object {
      throw const SourceRegistryRepositoryException(
        'catalog_invalid',
        'The remote source registry snapshot failed catalog validation.',
      );
    }
  }

  Future<List<int>> _fetch(
    String relativePath, {
    required int maxResponseBytes,
    required String kind,
  }) async {
    _ensureOpen();
    final SourceRegistryHttpResponse response;
    try {
      response = await _request(
        _uriFor(relativePath),
        maxResponseBytes: maxResponseBytes,
      );
    } on SourceRegistryRepositoryException catch (error) {
      if (error.code == 'response_too_large') {
        throw SourceRegistryRepositoryException(
          '${kind}_too_large',
          'The remote source registry $kind exceeds its byte limit.',
        );
      }
      rethrow;
    }
    if (response.bodyBytes.length > maxResponseBytes) {
      throw SourceRegistryRepositoryException(
        '${kind}_too_large',
        'The remote source registry $kind exceeds its byte limit.',
      );
    }
    if (response.statusCode != 200) {
      throw SourceRegistryRepositoryException(
        _httpCode(response.statusCode, kind),
        'The remote source registry $kind request was not successful.',
      );
    }
    return List<int>.unmodifiable(response.bodyBytes);
  }

  Future<SourceRegistryHttpResponse> _request(
    Uri uri, {
    required int maxResponseBytes,
  }) async {
    try {
      return await _transport.get(uri, maxResponseBytes: maxResponseBytes);
    } on SourceRegistryTransportException catch (error) {
      switch (error.code) {
        case 'transport_closed':
          throw const SourceRegistryRepositoryException(
            'repository_closed',
            'The source registry repository is closed.',
          );
        case 'response_too_large':
          throw const SourceRegistryRepositoryException(
            'response_too_large',
            'The remote source registry response exceeds its byte limit.',
          );
        case 'request_timeout':
          throw const SourceRegistryRepositoryException(
            'network_timeout',
            'The remote source registry request timed out.',
          );
        default:
          throw const SourceRegistryRepositoryException(
            'network_error',
            'The remote source registry request failed.',
          );
      }
    } on SourceRegistryRepositoryException {
      rethrow;
    } on Object {
      throw const SourceRegistryRepositoryException(
        'network_error',
        'The remote source registry request failed.',
      );
    }
  }

  Uri _uriFor(String relativePath) {
    final segments = <String>[
      owner,
      repository,
      ...ref.split('/'),
      ...relativePath.split('/'),
    ];
    return Uri.https(
      _githubRawHost,
      '/${segments.map(Uri.encodeComponent).join('/')}',
    );
  }

  void _ensureOpen() {
    if (_closed) {
      throw const SourceRegistryRepositoryException(
        'repository_closed',
        'The source registry repository is closed.',
      );
    }
  }

  void _clearInFlight(Future<SourceRegistryCatalog> future) {
    if (identical(_inFlight, future)) _inFlight = null;
  }

  static String _validateRepositoryPart(
    String value,
    String field,
    int maxLength,
  ) {
    if (value.isEmpty ||
        value.length > maxLength ||
        !RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]*$').hasMatch(value)) {
      throw ArgumentError.value(
        value,
        field,
        'Must be one bounded GitHub path segment.',
      );
    }
    return value;
  }

  static String _validateRef(String value) {
    final segments = value.split('/');
    if (value.isEmpty ||
        value.length > 256 ||
        value.startsWith('/') ||
        value.endsWith('/') ||
        value.contains('\\') ||
        !RegExp(r'^[A-Za-z0-9][A-Za-z0-9._/-]*$').hasMatch(value) ||
        segments.any(
          (segment) => segment.isEmpty || segment == '.' || segment == '..',
        )) {
      throw ArgumentError.value(
        value,
        'ref',
        'Must be a bounded GitHub ref without traversal segments.',
      );
    }
    return value;
  }

  static String _validateIndexPath(String value) {
    final segments = value.split('/');
    final validSegment = RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$');
    if (value.isEmpty ||
        value.length > 256 ||
        value.startsWith('/') ||
        value.endsWith('/') ||
        value.contains('\\') ||
        segments.length > 8 ||
        !segments.every(validSegment.hasMatch) ||
        !segments.last.endsWith('.json')) {
      throw ArgumentError.value(
        value,
        'indexPath',
        'Must be a bounded relative JSON path.',
      );
    }
    return value;
  }

  static String _httpCode(int statusCode, String kind) {
    return switch (statusCode) {
      404 => '${kind}_not_found',
      401 || 403 => '${kind}_forbidden',
      429 => 'rate_limited',
      >= 500 && <= 599 => 'remote_server_error',
      _ => '${kind}_http_error',
    };
  }
}

String _safeCode(String value, {required String fallback}) {
  return RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value) ? value : fallback;
}

void _validateGitHubRawUri(Uri uri) {
  if (uri.scheme != 'https' ||
      uri.host != _githubRawHost ||
      uri.userInfo.isNotEmpty ||
      (uri.hasPort && uri.port != 443) ||
      uri.hasQuery ||
      uri.hasFragment) {
    throw const SourceRegistryTransportException('uri_not_allowed');
  }
}
