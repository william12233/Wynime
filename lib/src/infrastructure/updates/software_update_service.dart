// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import '../../domain/models/software_update_models.dart';
import 'app_version_provider.dart';

final class SoftwareUpdateService implements SoftwareUpdateServicePort {
  SoftwareUpdateService({
    http.Client? client,
    AppVersionProvider? versionProvider,
    SoftwareUpdatePlatform? platformOverride,
    String? architectureOverride,
    Future<Directory> Function()? temporaryDirectoryProvider,
    Future<DownloadedUpdate> Function(
      SoftwareUpdateResult result, {
      UpdateProgressCallback? onProgress,
    })?
    downloadOverride,
    this.timeout = const Duration(seconds: 30),
    this.maxRedirects = 4,
    this.maxDownloadBytes = 1024 * 1024 * 1024,
  }) : _client = client ?? http.Client(),
       _ownsClient = client == null,
       _versionProvider = versionProvider ?? PackageInfoAppVersionProvider(),
       _platformOverride = platformOverride,
       _architectureOverride = architectureOverride,
       _temporaryDirectoryProvider = temporaryDirectoryProvider,
       _downloadOverride = downloadOverride;

  static const repository = 'william12233/Wynime';
  static const _apiHost = 'api.github.com';
  static const _downloadHosts = <String>{
    'github.com',
    'objects.githubusercontent.com',
    'release-assets.githubusercontent.com',
  };
  static const _metadataLimit = 2 * 1024 * 1024;
  static const _maxArchiveEntries = 4096;
  static const _maxSingleEntryBytes = 512 * 1024 * 1024;
  static const _maxTotalUncompressedBytes = 2 * 1024 * 1024 * 1024;

  final http.Client _client;
  final bool _ownsClient;
  final AppVersionProvider _versionProvider;
  final SoftwareUpdatePlatform? _platformOverride;
  final String? _architectureOverride;
  final Future<Directory> Function()? _temporaryDirectoryProvider;
  final Future<DownloadedUpdate> Function(
    SoftwareUpdateResult result, {
    UpdateProgressCallback? onProgress,
  })?
  _downloadOverride;
  final Duration timeout;
  final int maxRedirects;
  final int maxDownloadBytes;

  @override
  Future<AppVersionInfo> currentVersion() => _versionProvider.load();

  @override
  Future<SoftwareUpdateResult> checkForUpdates() async {
    final current = await currentVersion();
    final platform = _platformOverride ?? current.platform;
    if (platform == SoftwareUpdatePlatform.unsupported) {
      return SoftwareUpdateResult(
        status: UpdateStatus.notSupported,
        current: current,
        error: const SoftwareUpdateException(
          UpdateFailureReason.notSupported,
          'platform_not_supported',
        ),
      );
    }

    final release = await _fetchLatestRelease();
    if (release.version.compareTo(current.semanticVersion) <= 0) {
      return SoftwareUpdateResult(
        status: UpdateStatus.upToDate,
        current: current,
        release: release,
      );
    }
    final asset = _selectAsset(
      release,
      platform,
      _architectureOverride ?? current.architecture,
    );
    return SoftwareUpdateResult(
      status: asset == null
          ? UpdateStatus.unavailable
          : UpdateStatus.updateAvailable,
      current: current,
      release: release,
      asset: asset,
      error: asset == null
          ? const SoftwareUpdateException(
              UpdateFailureReason.assetUnavailable,
              'compatible_asset_missing',
            )
          : null,
    );
  }

  @override
  Future<DownloadedUpdate> download(
    SoftwareUpdateResult result, {
    UpdateProgressCallback? onProgress,
  }) async {
    final override = _downloadOverride;
    if (override != null) return override(result, onProgress: onProgress);
    final release = result.release;
    final asset = result.asset;
    if (!result.hasUpdate || release == null || asset == null) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.assetUnavailable,
        'no_compatible_update',
      );
    }
    final uri = asset.downloadUrl;
    if (uri == null || asset.size <= 0 || asset.size > maxDownloadBytes) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.invalidMetadata,
        'asset_metadata_invalid',
      );
    }

    final temporaryDirectory = _temporaryDirectoryProvider == null
        ? await getTemporaryDirectory()
        : await _temporaryDirectoryProvider();
    final stagingDirectory = Directory(
      path.join(
        temporaryDirectory.path,
        'wynime-update-${DateTime.now().microsecondsSinceEpoch}',
      ),
    );
    await stagingDirectory.create(recursive: true);
    final outputFile = File(path.join(stagingDirectory.path, asset.name));

    try {
      final response = await _send(uri);
      if (response.statusCode != HttpStatus.ok) {
        await _drain(response.stream);
        throw const SoftwareUpdateException(
          UpdateFailureReason.download,
          'asset_http_error',
        );
      }
      final contentLength = response.contentLength;
      if (contentLength != null && contentLength != asset.size) {
        await _drain(response.stream);
        throw const SoftwareUpdateException(
          UpdateFailureReason.integrity,
          'declared_size_mismatch',
        );
      }

      final digestSink = _DigestSink();
      final digestInput = sha256.startChunkedConversion(digestSink);
      final output = outputFile.openWrite();
      var received = 0;
      try {
        await for (final chunk in response.stream.timeout(timeout)) {
          received += chunk.length;
          if (received > maxDownloadBytes || received > asset.size) {
            throw const SoftwareUpdateException(
              UpdateFailureReason.download,
              'download_limit_exceeded',
            );
          }
          digestInput.add(chunk);
          output.add(chunk);
          onProgress?.call(received, asset.size);
        }
      } finally {
        digestInput.close();
        await output.close();
      }
      if (received != asset.size) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.integrity,
          'truncated_download',
        );
      }
      if (digestSink.value.toString().toLowerCase() !=
          await _expectedHash(asset)) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.integrity,
          'sha256_mismatch',
        );
      }

      final extractedDirectory =
          result.current.platform == SoftwareUpdatePlatform.windows
          ? await _prepareWindowsArchive(
              outputFile,
              stagingDirectory,
              release.version,
            )
          : null;
      return DownloadedUpdate(
        filePath: outputFile.path,
        stagingDirectoryPath: stagingDirectory.path,
        release: release,
        asset: asset,
        extractedDirectoryPath: extractedDirectory?.path,
      );
    } catch (error) {
      if (await stagingDirectory.exists()) {
        await stagingDirectory.delete(recursive: true);
      }
      if (error is SoftwareUpdateException) rethrow;
      throw const SoftwareUpdateException(
        UpdateFailureReason.download,
        'download_failed',
      );
    }
  }

  @override
  void dispose() {
    if (_ownsClient) _client.close();
  }

  Future<Directory> _prepareWindowsArchive(
    File archiveFile,
    Directory stagingDirectory,
    SemanticVersion expectedVersion,
  ) async {
    try {
      final bytes = await archiveFile.readAsBytes();
      if (bytes.length > maxDownloadBytes) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.unsafeArchive,
          'archive_too_large',
        );
      }
      final decoder = ZipDecoder();
      final archive = decoder.decodeBytes(bytes, verify: true);
      final headers = decoder.directory.fileHeaders;
      if (headers.length > _maxArchiveEntries) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.unsafeArchive,
          'too_many_entries',
        );
      }
      var totalUncompressed = 0;
      for (final header in headers) {
        if (header.uncompressedSize > _maxSingleEntryBytes) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.unsafeArchive,
            'entry_too_large',
          );
        }
        totalUncompressed += header.uncompressedSize;
        if (totalUncompressed > _maxTotalUncompressedBytes) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.unsafeArchive,
            'expanded_size_limit',
          );
        }
        if (header.uncompressedSize > 0 &&
            (header.compressedSize <= 0 ||
                header.uncompressedSize > header.compressedSize * 200)) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.unsafeArchive,
            'compression_ratio_limit',
          );
        }
      }

      final extractRoot = Directory(
        path.join(stagingDirectory.path, 'portable'),
      );
      await extractRoot.create(recursive: true);
      final names = <String>{};
      for (final entry in archive.files) {
        final rawName = entry.name;
        final isDirectory = entry.isDirectory || rawName.endsWith('/');
        final name = _validateArchivePath(
          isDirectory && rawName.isNotEmpty
              ? rawName.substring(0, rawName.length - 1)
              : rawName,
        );
        final caseKey = name.toLowerCase();
        if (!names.add(caseKey)) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.unsafeArchive,
            'case_collision',
          );
        }
        if (isDirectory) continue;
        if (entry.isSymbolicLink) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.unsafeArchive,
            'symbolic_link',
          );
        }
        final payload = entry.readBytes();
        if (payload == null || payload.length != entry.size) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.unsafeArchive,
            'corrupt_entry',
          );
        }
        final target = File(
          path.joinAll(<String>[extractRoot.path, ...name.split('/')]),
        );
        await target.parent.create(recursive: true);
        await target.writeAsBytes(payload, flush: true);
      }

      final executable = File(path.join(extractRoot.path, 'wynime.exe'));
      final updater = File(path.join(extractRoot.path, 'wynime_update.exe'));
      final versionFile = File(path.join(extractRoot.path, 'version.txt'));
      if (!await executable.exists() ||
          !await updater.exists() ||
          !await versionFile.exists()) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.unsafeArchive,
          'required_file_missing',
        );
      }
      final actualVersion = (await versionFile.readAsString()).trim();
      if (actualVersion != expectedVersion.toString()) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.unsafeArchive,
          'version_file_mismatch',
        );
      }
      return extractRoot;
    } on SoftwareUpdateException {
      rethrow;
    } on Object {
      throw const SoftwareUpdateException(
        UpdateFailureReason.unsafeArchive,
        'archive_invalid',
      );
    }
  }

  String _validateArchivePath(String value) {
    if (value.isEmpty ||
        value.contains('\\') ||
        value.contains('\u0000') ||
        value.startsWith('/') ||
        value.startsWith('//') ||
        value.contains(':') ||
        RegExp(r'^[A-Za-z]:').hasMatch(value)) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.unsafeArchive,
        'unsafe_path',
      );
    }
    final segments = value.split('/');
    for (final segment in segments) {
      if (segment.isEmpty ||
          segment == '.' ||
          segment == '..' ||
          segment.endsWith('.') ||
          segment.endsWith(' ') ||
          segment.codeUnits.any((code) => code < 0x20 || code == 0x7f) ||
          RegExp(
            r'^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$',
            caseSensitive: false,
          ).hasMatch(segment)) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.unsafeArchive,
          'unsafe_path',
        );
      }
    }
    return value;
  }

  Future<SoftwareRelease> _fetchLatestRelease() async {
    final response = await _send(
      Uri.parse('https://$_apiHost/repos/$repository/releases/latest'),
    );
    if (response.statusCode != HttpStatus.ok) {
      await _drain(response.stream);
      throw const SoftwareUpdateException(
        UpdateFailureReason.network,
        'release_http_error',
      );
    }
    final bytes = await _readBounded(response.stream, _metadataLimit);
    try {
      final decoded = jsonDecode(utf8.decode(bytes));
      if (decoded is! Map) throw const FormatException('release_not_object');
      return SoftwareRelease.fromJson(Map<String, dynamic>.from(decoded));
    } on SoftwareUpdateException {
      rethrow;
    } on Object {
      throw const SoftwareUpdateException(
        UpdateFailureReason.invalidMetadata,
        'release_payload_invalid',
      );
    }
  }

  ReleaseAsset? _selectAsset(
    SoftwareRelease release,
    SoftwareUpdatePlatform platform,
    String architecture,
  ) {
    final expectedName = switch (platform) {
      SoftwareUpdatePlatform.android when architecture == 'arm64-v8a' =>
        'wynime-${release.version}-arm64-v8a.apk',
      SoftwareUpdatePlatform.windows when architecture == 'x64' =>
        'wynime-${release.version}.zip',
      _ => null,
    };
    if (expectedName == null) return null;
    final matches = release.assets
        .where((asset) => asset.name == expectedName)
        .toList(growable: false);
    if (matches.length != 1) return null;
    final checksumMatches = release.assets
        .where((asset) => asset.name == '$expectedName.sha256')
        .toList(growable: false);
    if (checksumMatches.length != 1) {
      return null;
    }
    return ReleaseAsset(
      name: matches.single.name,
      downloadUrl: matches.single.downloadUrl,
      size: matches.single.size,
      digest: matches.single.digest,
      checksumAsset: checksumMatches.length == 1
          ? checksumMatches.single
          : null,
    );
  }

  Future<String> _expectedHash(ReleaseAsset asset) async {
    final checksum = asset.checksumAsset;
    if (checksum?.downloadUrl == null) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.integrity,
        'checksum_missing',
      );
    }
    final response = await _send(checksum!.downloadUrl!);
    if (response.statusCode != HttpStatus.ok) {
      await _drain(response.stream);
      throw const SoftwareUpdateException(
        UpdateFailureReason.integrity,
        'checksum_http_error',
      );
    }
    final bytes = await _readBounded(response.stream, 4096);
    final match = RegExp(
      r'(?<![0-9a-f])([0-9a-f]{64})(?![0-9a-f])',
      caseSensitive: false,
    ).firstMatch(utf8.decode(bytes));
    if (match == null) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.integrity,
        'checksum_malformed',
      );
    }
    final sidecarHash = match.group(1)!.toLowerCase();
    final embeddedHash = asset.sha256;
    if (embeddedHash != null && embeddedHash != sidecarHash) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.integrity,
        'checksum_metadata_mismatch',
      );
    }
    return sidecarHash;
  }

  Future<http.StreamedResponse> _send(Uri uri) async {
    var current = uri;
    try {
      for (var redirect = 0; redirect <= maxRedirects; redirect++) {
        _validateUri(current);
        final request = http.Request('GET', current)
          ..followRedirects = false
          ..headers['Accept'] = 'application/vnd.github+json'
          ..headers['User-Agent'] = 'wynime-software-updater';
        final response = await _client.send(request).timeout(timeout);
        if (!_isRedirect(response.statusCode)) return response;
        await _drain(response.stream);
        final location = response.headers['location'];
        if (location == null || redirect == maxRedirects) {
          throw const SoftwareUpdateException(
            UpdateFailureReason.network,
            'redirect_invalid',
          );
        }
        current = current.resolve(location);
      }
    } on SoftwareUpdateException {
      rethrow;
    } on TimeoutException {
      throw const SoftwareUpdateException(
        UpdateFailureReason.network,
        'request_timeout',
      );
    } on Object {
      throw const SoftwareUpdateException(
        UpdateFailureReason.network,
        'request_failed',
      );
    }
    throw const SoftwareUpdateException(
      UpdateFailureReason.network,
      'redirect_limit',
    );
  }

  Future<Uint8List> _readBounded(Stream<List<int>> stream, int maxBytes) async {
    final builder = BytesBuilder(copy: false);
    var received = 0;
    await for (final chunk in stream.timeout(timeout)) {
      received += chunk.length;
      if (received > maxBytes) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.network,
          'response_size_limit',
        );
      }
      builder.add(chunk);
    }
    return builder.takeBytes();
  }

  Future<void> _drain(Stream<List<int>> stream) async {
    await stream.timeout(timeout).drain<void>();
  }

  void _validateUri(Uri uri) {
    final host = uri.host.toLowerCase();
    if (uri.scheme != 'https' ||
        uri.userInfo.isNotEmpty ||
        (uri.hasPort && uri.port != 443) ||
        (host != _apiHost && !_downloadHosts.contains(host))) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.network,
        'redirect_host_not_allowed',
      );
    }
  }

  static bool _isRedirect(int statusCode) =>
      statusCode == 301 ||
      statusCode == 302 ||
      statusCode == 303 ||
      statusCode == 307 ||
      statusCode == 308;
}

final class _DigestSink implements Sink<Digest> {
  Digest? _digest;

  Digest get value => _digest!;

  @override
  void add(Digest value) => _digest = value;

  @override
  void close() {}
}
