import '../models/source_package_manager_models.dart';

/// Durable storage for the complete installed source-package state.
///
/// Implementations must make [replaceAll] atomic: a failed replacement must
/// leave the previously committed snapshot readable after a restart.
abstract interface class SourcePackageRepository {
  Future<List<InstalledSourcePackage>> load();

  Future<void> replaceAll(Iterable<InstalledSourcePackage> packages);
}

final class SourcePackageRepositoryException implements Exception {
  const SourcePackageRepositoryException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourcePackageRepositoryException($code): $message';
}
