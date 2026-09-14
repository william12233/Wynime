import 'dart:convert';

import 'package:pub_semver/pub_semver.dart';

import '../../domain/models/source_registry_models.dart';

/// Strictly decodes the offline source registry/index schema v1.
///
/// The decoder is deliberately data-only. It does not resolve a repository,
/// follow a branch, perform network I/O or activate any package.
final class SourceRegistryIndexDecoder {
  const SourceRegistryIndexDecoder();

  SourceRegistryIndex decode(String source) {
    return decodeBytes(utf8.encode(source));
  }

  /// Decodes the exact bytes supplied by a repository or transport adapter.
  ///
  /// The byte-oriented entry point is authoritative for remote data: it
  /// checks the actual byte length and rejects malformed UTF-8 instead of
  /// allowing a lossy replacement decode.
  SourceRegistryIndex decodeBytes(List<int> sourceBytes) {
    if (sourceBytes.length > SourceRegistryIndex.maxIndexBytes) {
      throw const SourceRegistryIndexFormatException(
        r'$',
        'The source registry index exceeds its byte limit.',
      );
    }

    final String source;
    try {
      source = utf8.decode(sourceBytes, allowMalformed: false);
    } on FormatException {
      throw const SourceRegistryIndexFormatException(
        r'$',
        'The source registry index is not valid UTF-8.',
      );
    }
    return _decodeJson(source);
  }

  SourceRegistryIndex _decodeJson(String source) {
    final Object? decoded;
    try {
      decoded = jsonDecode(source);
    } on FormatException {
      throw const SourceRegistryIndexFormatException(
        r'$',
        'The source registry index is not valid JSON.',
      );
    }
    final root = _asMap(decoded, r'$');
    _expectKeys(root, r'$', {
      'schemaVersion',
      'sourceRoot',
      'revision',
      'packages',
    });
    final packageValues = _asList(
      _required(root, 'packages', r'$'),
      r'$.packages',
    );
    if (packageValues.isEmpty ||
        packageValues.length > SourceRegistryIndex.maxEntries) {
      throw const SourceRegistryIndexFormatException(
        r'$.packages',
        'The source registry index contains an invalid package count.',
      );
    }

    final entries = <SourceRegistryEntry>[];
    final packageIds = <String>{};
    final packagePaths = <String>{};
    for (var index = 0; index < packageValues.length; index++) {
      final path = '\$.packages[$index]';
      final map = _asMap(packageValues[index], path);
      _expectKeys(map, path, {'packageId', 'version', 'path', 'sha256'});
      final packageId = _requiredString(map, 'packageId', path);
      final packagePath = _requiredString(map, 'path', path);
      if (!packageIds.add(packageId)) {
        throw const SourceRegistryIndexFormatException(
          r'$.packages',
          'Package IDs must be unique.',
        );
      }
      if (!packagePaths.add(packagePath)) {
        throw const SourceRegistryIndexFormatException(
          r'$.packages',
          'Package paths must be unique.',
        );
      }
      final versionText = _requiredString(map, 'version', path);
      final Version version;
      try {
        version = Version.parse(versionText);
      } on FormatException {
        throw const SourceRegistryIndexFormatException(
          r'$.packages[].version',
          'A package version is invalid.',
        );
      }
      try {
        entries.add(
          SourceRegistryEntry(
            packageId: packageId,
            version: version,
            packagePath: packagePath,
            sha256: _requiredString(map, 'sha256', path),
          ),
        );
      } on ArgumentError {
        throw const SourceRegistryIndexFormatException(
          r'$.packages[]',
          'A source registry package entry is invalid.',
        );
      }
    }

    try {
      return SourceRegistryIndex(
        schemaVersion: _requiredInt(root, 'schemaVersion', r'$'),
        sourceRoot: _requiredString(root, 'sourceRoot', r'$'),
        revision: _requiredString(root, 'revision', r'$'),
        packages: entries,
      );
    } on ArgumentError {
      throw const SourceRegistryIndexFormatException(
        r'$',
        'The source registry index failed schema validation.',
      );
    }
  }

  static Object? _required(Map<String, Object?> map, String key, String path) {
    if (!map.containsKey(key) || map[key] == null) {
      throw SourceRegistryIndexFormatException(
        '$path.$key',
        'Required value is missing.',
      );
    }
    return map[key];
  }

  static String _requiredString(
    Map<String, Object?> map,
    String key,
    String path,
  ) {
    final value = _required(map, key, path);
    if (value is! String) {
      throw SourceRegistryIndexFormatException(
        '$path.$key',
        'Expected a string.',
      );
    }
    return value;
  }

  static int _requiredInt(Map<String, Object?> map, String key, String path) {
    final value = _required(map, key, path);
    if (value is! int) {
      throw SourceRegistryIndexFormatException(
        '$path.$key',
        'Expected an integer.',
      );
    }
    return value;
  }

  static Map<String, Object?> _asMap(Object? value, String path) {
    if (value is! Map) {
      throw SourceRegistryIndexFormatException(path, 'Expected an object.');
    }
    final result = <String, Object?>{};
    for (final entry in value.entries) {
      if (entry.key is! String) {
        throw SourceRegistryIndexFormatException(
          path,
          'Object keys must be strings.',
        );
      }
      result[entry.key as String] = entry.value;
    }
    return result;
  }

  static List<Object?> _asList(Object? value, String path) {
    if (value is! List) {
      throw SourceRegistryIndexFormatException(path, 'Expected an array.');
    }
    return value.cast<Object?>();
  }

  static void _expectKeys(
    Map<String, Object?> map,
    String path,
    Set<String> allowed,
  ) {
    if (map.keys.any((key) => !allowed.contains(key))) {
      throw SourceRegistryIndexFormatException(
        path,
        'Unsupported keys are present.',
      );
    }
  }
}
