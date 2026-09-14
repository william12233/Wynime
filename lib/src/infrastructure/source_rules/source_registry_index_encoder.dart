import 'dart:convert';

import '../../domain/models/source_registry_models.dart';

/// Encodes the strict registry index schema in deterministic package order.
final class SourceRegistryIndexEncoder {
  const SourceRegistryIndexEncoder();

  String encode(SourceRegistryIndex index) {
    final encoded = jsonEncode(toMap(index));
    if (utf8.encode(encoded).length > SourceRegistryIndex.maxIndexBytes) {
      throw ArgumentError.value(
        index,
        'index',
        'The encoded source registry index exceeds its byte limit.',
      );
    }
    return encoded;
  }

  Map<String, Object?> toMap(SourceRegistryIndex index) {
    return {
      'schemaVersion': index.schemaVersion,
      'sourceRoot': index.sourceRoot,
      'revision': index.revision,
      'packages': index.packages
          .map(
            (package) => {
              'packageId': package.packageId,
              'version': package.version.toString(),
              'path': package.packagePath,
              'sha256': package.sha256,
            },
          )
          .toList(growable: false),
    };
  }
}
