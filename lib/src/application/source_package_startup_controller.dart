import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_package_manifest.dart';
import '../domain/repositories/source_package_repository.dart';
import '../infrastructure/source_rules/persistent_source_package_manager.dart';

enum SourcePackageStartupStatus { idle, loading, ready, failed }

/// Loads the durable source-package snapshot once for the application shell
/// and owns explicit, persisted package lifecycle mutations.
///
/// It does not fetch registry artifacts, verify publisher signatures or
/// execute source programs. Installation/update only stages a disabled,
/// consent-pending manifest; enablement is a separate explicit operation.
final class SourcePackageStartupController extends ChangeNotifier {
  SourcePackageStartupController({required this.managerFactory});

  final Future<PersistentSourcePackageManager> Function() managerFactory;

  SourcePackageStartupStatus get status => _status;
  String? get errorCode => _errorCode;

  SourcePackageStartupStatus _status = SourcePackageStartupStatus.idle;
  String? _errorCode;
  PersistentSourcePackageManager? _manager;
  Future<void>? _initializeFuture;
  Future<void> _operationTail = Future<void>.value();
  Future<void>? _closeFuture;
  int _activeOperations = 0;
  bool _managerLoadInProgress = false;
  bool _disposed = false;

  /// True while an explicit package lifecycle operation is being persisted.
  ///
  /// Presentation uses this only to prevent duplicate taps. The persistent
  /// manager remains the lifecycle and serialization authority.
  bool get isMutating => _activeOperations > 0;

  List<InstalledSourcePackage> get installedPackages =>
      _manager?.installedPackages ?? const <InstalledSourcePackage>[];

  List<InstalledSourcePackage> get enabledPackages =>
      _manager?.enabledPackages ?? const <InstalledSourcePackage>[];

  Future<void> initialize() {
    final existing = _initializeFuture;
    if (existing != null) return existing;
    final future = _initialize();
    _initializeFuture = future;
    return future;
  }

  Future<void> _initialize() async {
    if (_disposed) return;
    _status = SourcePackageStartupStatus.loading;
    _errorCode = null;
    notifyListeners();
    try {
      final manager = await managerFactory();
      if (_disposed) return;
      _managerLoadInProgress = true;
      try {
        await manager.load();
      } finally {
        _managerLoadInProgress = false;
      }
      if (_disposed) return;
      _manager = manager;
      _status = SourcePackageStartupStatus.ready;
    } on SourcePackageRepositoryException catch (error) {
      if (_disposed) return;
      _status = SourcePackageStartupStatus.failed;
      _errorCode = _safeCode(error.code, fallback: 'load_failed');
    } on SourcePackageManagerException catch (error) {
      if (_disposed) return;
      _status = SourcePackageStartupStatus.failed;
      _errorCode = _safeCode(error.code, fallback: 'state_invalid');
    } on Object {
      if (_disposed) return;
      _status = SourcePackageStartupStatus.failed;
      _errorCode = 'load_failed';
    }
    if (!_disposed) notifyListeners();
  }

  /// Explicitly installs a registry/proposal manifest in consent-pending
  /// state, or stages a strictly newer version as an update.
  ///
  /// This method never enables or executes a package. The caller must make a
  /// separate explicit consent decision through [enable].
  Future<InstalledSourcePackage> installOrUpdate(
    SourcePackageManifest package,
  ) {
    return _runOperation((manager) {
      InstalledSourcePackage? current;
      for (final installed in manager.installedPackages) {
        if (installed.package.packageId == package.packageId) {
          current = installed;
          break;
        }
      }
      if (current == null) return manager.install(package);
      if (package.version <= current.package.version) {
        throw const SourcePackageManagerException(
          'version_not_newer',
          'The selected source package is not newer than the installed version.',
        );
      }
      return manager.update(package);
    });
  }

  /// Enables an exact version after the presentation layer has obtained
  /// explicit user approval and, when required, fresh re-consent.
  Future<InstalledSourcePackage> enable({
    required String packageId,
    required Version version,
    required bool userApproved,
    required bool reconsentGranted,
  }) {
    return _runOperation(
      (manager) => manager.enable(
        packageId: packageId,
        version: version,
        userApproved: userApproved,
        reconsentGranted: reconsentGranted,
      ),
    );
  }

  /// Disables an exact package version without deleting its manifest.
  Future<InstalledSourcePackage> disable({
    required String packageId,
    required Version version,
  }) {
    return _runOperation(
      (manager) => manager.disable(packageId: packageId, version: version),
    );
  }

  /// Removes an installed package and invalidates its durable source mappings
  /// through the repository's atomic package snapshot replacement.
  Future<InstalledSourcePackage> remove({required String packageId}) {
    return _runOperation((manager) => manager.remove(packageId: packageId));
  }

  Future<T> _runOperation<T>(
    FutureOr<T> Function(PersistentSourcePackageManager manager) operation,
  ) {
    final result = _operationTail.then<T>((_) async {
      if (_disposed) {
        throw const SourcePackageLifecycleException('controller_closed');
      }
      final manager = _manager;
      if (_status != SourcePackageStartupStatus.ready || manager == null) {
        throw const SourcePackageLifecycleException('not_initialized');
      }
      _activeOperations += 1;
      if (!_disposed) notifyListeners();
      try {
        return await operation(manager);
      } on SourcePackageManagerException catch (error) {
        throw SourcePackageLifecycleException(_safeOperationCode(error.code));
      } on SourcePackageRepositoryException catch (error) {
        throw SourcePackageLifecycleException(_safeOperationCode(error.code));
      } on SourcePackageLifecycleException {
        rethrow;
      } on Object {
        throw const SourcePackageLifecycleException('lifecycle_failed');
      } finally {
        _activeOperations -= 1;
        if (!_disposed) notifyListeners();
      }
    });
    _operationTail = result.then<void>((_) {}, onError: (_, _) {});
    return result;
  }

  /// Stops new lifecycle work and waits for startup or an already-running
  /// operation before the app closes its database.
  Future<void> close() {
    final existing = _closeFuture;
    if (existing != null) return existing;
    _disposed = true;
    final future = () async {
      // If the factory is still pending, disposal prevents it from starting a
      // database load, so waiting would only make app teardown unbounded. If
      // the manager is already loading durable state, wait for that I/O.
      if (_managerLoadInProgress) {
        final initialization = _initializeFuture;
        if (initialization != null) await initialization;
      }
      await _operationTail;
    }();
    _closeFuture = future;
    return future;
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

/// Stable, bounded failure exposed to the Sources presentation boundary.
final class SourcePackageLifecycleException implements Exception {
  const SourcePackageLifecycleException(this.code);

  final String code;

  @override
  String toString() => 'SourcePackageLifecycleException($code)';
}

String _safeCode(String value, {required String fallback}) {
  return RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value) ? value : fallback;
}

String _safeOperationCode(String value) {
  return RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value)
      ? value
      : 'lifecycle_failed';
}
