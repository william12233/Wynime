import 'package:pub_semver/pub_semver.dart';

import '../../domain/models/source_package_manager_models.dart';
import '../../domain/models/source_package_manifest.dart';
import '../../domain/repositories/source_package_repository.dart';
import 'declarative_source_package_builder.dart';
import 'declarative_source_package_manager.dart';

/// Adds durable restart recovery to the deterministic package manager.
///
/// The wrapped manager remains the only package lifecycle authority. This
/// facade serializes mutations, commits the complete snapshot atomically and
/// restores the prior in-memory snapshot if storage rejects a write.
final class PersistentSourcePackageManager {
  PersistentSourcePackageManager({
    required this._manager,
    required this._repository,
  });

  final DeclarativeSourcePackageManager _manager;
  final SourcePackageRepository _repository;
  Future<void> _tail = Future<void>.value();
  bool _loaded = false;

  bool get isLoaded => _loaded;

  List<InstalledSourcePackage> get installedPackages =>
      _manager.installedPackages;

  List<InstalledSourcePackage> get enabledPackages => _manager.enabledPackages;

  Future<List<InstalledSourcePackage>> load() {
    return _enqueue(() async {
      final restored = await _repository.load();
      _manager.restore(restored);
      _loaded = true;
      return _manager.installedPackages;
    });
  }

  Future<InstalledSourcePackage> install(SourcePackageManifest package) {
    return _mutate(() => _manager.install(package));
  }

  Future<InstalledSourcePackage> installEncoded(String source) {
    return _mutate(() => _manager.installEncoded(source));
  }

  Future<InstalledSourcePackage> update(SourcePackageManifest package) {
    return _mutate(() => _manager.update(package));
  }

  Future<InstalledSourcePackage> updateEncoded(String source) {
    return _mutate(() => _manager.updateEncoded(source));
  }

  Future<InstalledSourcePackage> enable({
    required String packageId,
    required Version version,
    required bool userApproved,
    required bool reconsentGranted,
  }) {
    return _mutate(
      () => _manager.enable(
        packageId: packageId,
        version: version,
        userApproved: userApproved,
        reconsentGranted: reconsentGranted,
      ),
    );
  }

  Future<InstalledSourcePackage> disable({
    required String packageId,
    required Version version,
  }) {
    return _mutate(
      () => _manager.disable(packageId: packageId, version: version),
    );
  }

  Future<InstalledSourcePackage> remove({required String packageId}) {
    return _mutate(() => _manager.remove(packageId: packageId));
  }

  Future<InstalledSourcePackage> installApprovedProposal({
    required SourceBuilderProposal proposal,
    required String proposalId,
    required bool userApproved,
    required bool reconsentGranted,
  }) {
    return _mutate(
      () => _manager.installApprovedProposal(
        proposal: proposal,
        proposalId: proposalId,
        userApproved: userApproved,
        reconsentGranted: reconsentGranted,
      ),
    );
  }

  Future<T> _mutate<T>(T Function() operation) {
    return _enqueue(() async {
      if (!_loaded) {
        throw const SourcePackageRepositoryException(
          'not_initialized',
          'The source package manager must load persisted state first.',
        );
      }
      final before = _manager.installedPackages;
      final T result;
      try {
        result = operation();
      } on Object {
        _manager.restore(before);
        rethrow;
      }
      try {
        await _repository.replaceAll(_manager.installedPackages);
      } on Object {
        _manager.restore(before);
        rethrow;
      }
      return result;
    });
  }

  Future<T> _enqueue<T>(Future<T> Function() operation) {
    final result = _tail.then<T>((_) => operation());
    _tail = result.then<void>((_) {}, onError: (_, _) {});
    return result;
  }
}
