import 'dart:async';
import 'dart:io';

import 'package:path/path.dart' as path;

import '../../domain/models/source_registry_models.dart';
import '../source_rules/source_package_decoder.dart';
import '../source_rules/source_registry_artifact_catalog.dart';
import '../source_rules/source_registry_index_decoder.dart';
import 'github_source_registry_repository.dart';

const _defaultIndexPath = 'sources/index.json';
const _defaultMaxTotalPackageBytes = 8 * 1024 * 1024;

/// Read-only registry adapter for a pre-release app-specific staging root.
///
/// This adapter deliberately shares the exact byte-oriented catalog loader
/// used by the GitHub adapter. It never creates directories, installs a
/// package, changes lifecycle state or executes source rules. The application
/// composition root is the only caller that may select it, and only for a
/// debug build explicitly compiled with the staged-registry flag.
final class StagedSourceRegistryRepository {
  StagedSourceRegistryRepository({
    required this.root,
    String indexPath = _defaultIndexPath,
    this._indexDecoder = const SourceRegistryIndexDecoder(),
    this._catalogLoader = const SourceRegistryArtifactCatalogLoader(),
    this.maxTotalPackageBytes = _defaultMaxTotalPackageBytes,
  }) : indexPath = _validateRelativeJsonPath(indexPath, 'indexPath') {
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

  final Directory root;
  final String indexPath;
  final int maxTotalPackageBytes;
  final SourceRegistryIndexDecoder _indexDecoder;
  final SourceRegistryArtifactCatalogLoader _catalogLoader;

  Future<SourceRegistryCatalog>? _inFlight;
  Future<String>? _canonicalRootPath;
  bool _closed = false;

  /// Loads one complete staged snapshot. Concurrent callers share one load.
  Future<SourceRegistryCatalog> loadCatalog() {
    if (_closed) {
      return Future<SourceRegistryCatalog>.error(
        const SourceRegistryRepositoryException(
          'repository_closed',
          'The staged source registry repository is closed.',
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

  /// Prevents new loads. No staged file is removed or modified.
  void close() {
    _closed = true;
  }

  Future<SourceRegistryCatalog> _loadCatalog() async {
    final indexBytes = await _read(
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
        'The staged source registry index failed schema validation.',
      );
    } on Object {
      throw const SourceRegistryRepositoryException(
        'index_format_invalid',
        'The staged source registry index failed schema validation.',
      );
    }

    final packageBytesByPath = <String, List<int>>{};
    var totalPackageBytes = 0;
    for (final entry in index.packages) {
      _ensureOpen();
      final relativePath = index.relativePathFor(entry);
      final packageBytes = await _read(
        relativePath,
        maxResponseBytes: SourcePackageDecoder.maxPackageBytes,
        kind: 'artifact',
      );
      totalPackageBytes += packageBytes.length;
      if (totalPackageBytes > maxTotalPackageBytes) {
        throw const SourceRegistryRepositoryException(
          'total_artifact_limit',
          'The staged source registry package set exceeds its total byte limit.',
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
        'The staged source registry snapshot failed catalog validation.',
      );
    } on Object {
      throw const SourceRegistryRepositoryException(
        'catalog_invalid',
        'The staged source registry snapshot failed catalog validation.',
      );
    }
  }

  Future<List<int>> _read(
    String relativePath, {
    required int maxResponseBytes,
    required String kind,
  }) async {
    _ensureOpen();
    final File file;
    try {
      file = await _fileWithinRoot(relativePath);
      final length = await file.length();
      if (length > maxResponseBytes) {
        throw SourceRegistryRepositoryException(
          '${kind}_too_large',
          'The staged source registry $kind exceeds its byte limit.',
        );
      }
      final bytes = await file.readAsBytes();
      if (bytes.length > maxResponseBytes) {
        throw SourceRegistryRepositoryException(
          '${kind}_too_large',
          'The staged source registry $kind exceeds its byte limit.',
        );
      }
      return List<int>.unmodifiable(bytes);
    } on SourceRegistryRepositoryException {
      rethrow;
    } on FileSystemException {
      throw SourceRegistryRepositoryException(
        '${kind}_not_found',
        'The staged source registry $kind is unavailable.',
      );
    } on Object {
      throw SourceRegistryRepositoryException(
        '${kind}_read_failed',
        'The staged source registry $kind could not be read.',
      );
    }
  }

  Future<File> _fileWithinRoot(String relativePath) async {
    final segments = _validateRelativePath(relativePath);
    final rootPath = await _canonicalRoot();
    final candidate = File(path.joinAll([rootPath, ...segments]));
    final candidateType = await FileSystemEntity.type(
      candidate.path,
      followLinks: false,
    );
    if (candidateType == FileSystemEntityType.link) {
      throw const SourceRegistryRepositoryException(
        'path_not_allowed',
        'The staged source registry path must not be a symbolic link.',
      );
    }
    final resolvedPath = await candidate.resolveSymbolicLinks();
    if (!_isContained(rootPath, resolvedPath)) {
      throw const SourceRegistryRepositoryException(
        'path_not_allowed',
        'The staged source registry path escapes its authorized root.',
      );
    }
    final resolvedType = await FileSystemEntity.type(
      resolvedPath,
      followLinks: false,
    );
    if (resolvedType != FileSystemEntityType.file) {
      throw const SourceRegistryRepositoryException(
        'artifact_not_regular_file',
        'The staged source registry artifact is not a regular file.',
      );
    }
    return File(resolvedPath);
  }

  Future<String> _canonicalRoot() {
    final existing = _canonicalRootPath;
    if (existing != null) return existing;
    final future = () async {
      final rootType = await FileSystemEntity.type(
        root.path,
        followLinks: false,
      );
      if (rootType != FileSystemEntityType.directory) {
        throw const SourceRegistryRepositoryException(
          'staged_root_unavailable',
          'The staged source registry root is unavailable.',
        );
      }
      final resolved = await root.resolveSymbolicLinks();
      final resolvedType = await FileSystemEntity.type(
        resolved,
        followLinks: false,
      );
      if (resolvedType != FileSystemEntityType.directory) {
        throw const SourceRegistryRepositoryException(
          'staged_root_unavailable',
          'The staged source registry root is unavailable.',
        );
      }
      return path.normalize(path.absolute(resolved));
    }();
    _canonicalRootPath = future;
    return future;
  }

  void _ensureOpen() {
    if (_closed) {
      throw const SourceRegistryRepositoryException(
        'repository_closed',
        'The staged source registry repository is closed.',
      );
    }
  }

  void _clearInFlight(Future<SourceRegistryCatalog> future) {
    if (identical(_inFlight, future)) _inFlight = null;
  }
}

List<String> _validateRelativePath(String value) {
  final segments = value.split('/');
  if (value.isEmpty ||
      value.startsWith('/') ||
      value.endsWith('/') ||
      value.contains('\\') ||
      value.contains(':') ||
      segments.any(
        (segment) => segment.isEmpty || segment == '.' || segment == '..',
      )) {
    throw const SourceRegistryRepositoryException(
      'path_not_allowed',
      'The staged source registry path is not a safe relative path.',
    );
  }
  return segments;
}

String _validateRelativeJsonPath(String value, String field) {
  final segments = _validateRelativePath(value);
  if (value.length > 256 || !segments.last.endsWith('.json')) {
    throw ArgumentError.value(
      value,
      field,
      'Must be a bounded relative JSON path.',
    );
  }
  return value;
}

bool _isContained(String rootPath, String candidatePath) {
  final root = path.normalize(path.absolute(rootPath));
  final candidate = path.normalize(path.absolute(candidatePath));
  final relative = path.relative(candidate, from: root);
  return relative != '..' &&
      !relative.startsWith('..${path.separator}') &&
      !path.isAbsolute(relative);
}

String _safeCode(String value, {required String fallback}) {
  return RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value) ? value : fallback;
}
