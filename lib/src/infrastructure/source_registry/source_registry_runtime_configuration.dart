/// Optional build-time configuration for the read-only source registry.
///
/// No remote registry is selected when these values are absent. The app can
/// therefore run without a network-backed source catalog by default.
final class SourceRegistryRuntimeConfiguration {
  const SourceRegistryRuntimeConfiguration({
    required this.owner,
    required this.repository,
    required this.ref,
    required this.indexPath,
  });

  final String owner;
  final String repository;
  final String ref;
  final String indexPath;

  static SourceRegistryRuntimeConfiguration? fromEnvironment() {
    const owner = String.fromEnvironment('WYNIME_SOURCE_REGISTRY_OWNER');
    const repository = String.fromEnvironment(
      'WYNIME_SOURCE_REGISTRY_REPOSITORY',
    );
    const ref = String.fromEnvironment('WYNIME_SOURCE_REGISTRY_REF');
    const indexPath = String.fromEnvironment(
      'WYNIME_SOURCE_REGISTRY_INDEX_PATH',
      defaultValue: 'sources/index.json',
    );
    return fromValues(
      owner: owner,
      repository: repository,
      ref: ref,
      indexPath: indexPath,
    );
  }

  static SourceRegistryRuntimeConfiguration? fromValues({
    required String owner,
    required String repository,
    required String ref,
    String indexPath = 'sources/index.json',
  }) {
    if (!_validRepositoryPart(owner, maxLength: 39) ||
        !_validRepositoryPart(repository, maxLength: 100) ||
        !_validRef(ref) ||
        !_validIndexPath(indexPath)) {
      return null;
    }
    return SourceRegistryRuntimeConfiguration(
      owner: owner,
      repository: repository,
      ref: ref,
      indexPath: indexPath,
    );
  }
}

bool _validRepositoryPart(String value, {required int maxLength}) {
  return value.isNotEmpty &&
      value.length <= maxLength &&
      RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]*$').hasMatch(value);
}

bool _validRef(String value) {
  final segments = value.split('/');
  return value.isNotEmpty &&
      value.length <= 256 &&
      !value.startsWith('/') &&
      !value.endsWith('/') &&
      !value.contains('\\') &&
      RegExp(r'^[A-Za-z0-9][A-Za-z0-9._/-]*$').hasMatch(value) &&
      segments.every(
        (segment) => segment.isNotEmpty && segment != '.' && segment != '..',
      );
}

bool _validIndexPath(String value) {
  final segments = value.split('/');
  final validSegment = RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$');
  return value.isNotEmpty &&
      value.length <= 256 &&
      !value.startsWith('/') &&
      !value.endsWith('/') &&
      !value.contains('\\') &&
      segments.length <= 8 &&
      segments.every(validSegment.hasMatch) &&
      segments.last.endsWith('.json');
}
