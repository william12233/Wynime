import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import '../../domain/models/source_package_manager_models.dart';
import '../../domain/models/source_package_manifest.dart';
import '../../domain/services/source_package_manager.dart';
import 'declarative_source_package_builder.dart';
import 'source_package_decoder.dart';

/// Deterministic lifecycle manager for already validated declarative packages.
///
/// This class deliberately has no filesystem, network, WebView or platform
/// dependencies. A future registry adapter may feed it decoded package
/// manifests, while persistence and remote registry trust remain separate
/// boundaries.
final class DeclarativeSourcePackageManager implements SourcePackageManager {
  DeclarativeSourcePackageManager({
    required Version wynimeVersion,
    SourcePackageDecoder decoder = const SourcePackageDecoder(),
    SourcePackageActivationService activation =
        const SourcePackageActivationService(),
  }) : this._(wynimeVersion, decoder, activation);

  DeclarativeSourcePackageManager._(
    this._wynimeVersion,
    this._decoder,
    this._activation,
  );

  final Version _wynimeVersion;
  final SourcePackageDecoder _decoder;
  final SourcePackageActivationService _activation;
  final Map<String, InstalledSourcePackage> _packages = {};

  @override
  List<InstalledSourcePackage> get installedPackages =>
      _sortedPackages(includeDisabled: true);

  @override
  List<InstalledSourcePackage> get enabledPackages =>
      _sortedPackages(includeDisabled: false);

  /// Restores a complete, already decoded durable snapshot atomically.
  ///
  /// Every record is revalidated against the current Wynime version and the
  /// package-state invariants before the current map is replaced. A malformed
  /// or incompatible record therefore cannot leave a partially restored
  /// manager.
  void restore(Iterable<InstalledSourcePackage> packages) {
    final restored = <String, InstalledSourcePackage>{};
    for (final installed in packages) {
      if (restored.containsKey(installed.package.packageId)) {
        throw const SourcePackageManagerException(
          'duplicate_persisted_package',
          'The persisted source package snapshot contains a duplicate ID.',
        );
      }
      _validateInstalledState(installed);
      _validateOrThrow(installed.package);
      restored[installed.package.packageId] = installed;
    }
    _packages
      ..clear()
      ..addAll(restored);
  }

  /// Decodes and installs one package without activating it.
  InstalledSourcePackage installEncoded(String source) {
    return install(_decoder.decode(source));
  }

  /// Decodes and updates one package without activating the replacement.
  InstalledSourcePackage updateEncoded(String source) {
    return update(_decoder.decode(source));
  }

  /// Installs a reviewed builder proposal and enables it atomically.
  ///
  /// The proposal gate is intentionally repeated with the manager's current
  /// package state. This prevents a proposal generated against an old policy
  /// from bypassing fresh re-consent after another update has been accepted.
  InstalledSourcePackage installApprovedProposal({
    required SourceBuilderProposal proposal,
    required String proposalId,
    required bool userApproved,
    required bool reconsentGranted,
  }) {
    final SourcePackageManifest package;
    try {
      package = _activation.activate(
        proposal: proposal,
        proposalId: proposalId,
        userApproved: userApproved,
        reconsentGranted: reconsentGranted,
      );
    } on SourcePackageActivationException catch (error) {
      throw SourcePackageManagerException(error.code, error.message);
    }
    final previous = _packages[package.packageId]?.package;
    _validateOrThrow(package, previous: previous);
    _checkInstallVersion(package, previous: previous);
    final validation = validate(package, previous: previous);
    if (validation.requiresReconsent && !reconsentGranted) {
      throw const SourcePackageManagerException(
        'reconsent_required',
        'The replacement source package security policy requires fresh consent.',
      );
    }

    final installed = InstalledSourcePackage(
      package: package,
      status: SourcePackageStatus.enabled,
      requiresConsent: false,
      requiresReconsent: false,
    );
    _packages[package.packageId] = installed;
    return installed;
  }

  @override
  SourcePackageValidationResult validate(
    SourcePackageManifest package, {
    SourcePackageManifest? previous,
  }) {
    if (!package.isCompatibleWith(_wynimeVersion)) {
      return const SourcePackageValidationResult(
        isValid: false,
        code: 'incompatible_wynime_version',
      );
    }
    if (previous != null && previous.packageId != package.packageId) {
      return const SourcePackageValidationResult(
        isValid: false,
        code: 'package_identity_mismatch',
      );
    }
    final requiresReconsent =
        previous != null &&
        (package.securityPolicy.requiresReconsentComparedTo(
              previous.securityPolicy,
            ) ||
            package.requiresReconsentComparedTo(previous));
    return SourcePackageValidationResult(
      isValid: true,
      code: 'valid',
      requiresReconsent: requiresReconsent,
    );
  }

  @override
  InstalledSourcePackage install(SourcePackageManifest package) {
    if (_packages.containsKey(package.packageId)) {
      throw const SourcePackageManagerException(
        'already_installed',
        'The source package is already installed; use update for a newer version.',
      );
    }
    _validateOrThrow(package);
    final installed = _newPendingPackage(
      package: package,
      requiresReconsent: false,
    );
    _packages[package.packageId] = installed;
    return installed;
  }

  @override
  InstalledSourcePackage update(SourcePackageManifest package) {
    final previous = _packages[package.packageId];
    if (previous == null) {
      throw const SourcePackageManagerException(
        'package_not_installed',
        'The source package must be installed before it can be updated.',
      );
    }
    _validateOrThrow(package, previous: previous.package);
    _checkInstallVersion(package, previous: previous.package);
    final validation = validate(package, previous: previous.package);
    final updated = _newPendingPackage(
      package: package,
      requiresReconsent: validation.requiresReconsent,
    );
    _packages[package.packageId] = updated;
    return updated;
  }

  @override
  InstalledSourcePackage enable({
    required String packageId,
    required Version version,
    required bool userApproved,
    required bool reconsentGranted,
  }) {
    final current = _requireVersion(packageId, version);
    if (current.status == SourcePackageStatus.enabled) {
      return current;
    }
    if (current.requiresConsent && !userApproved) {
      throw const SourcePackageManagerException(
        'user_approval_required',
        'Enabling a source package requires explicit user approval.',
      );
    }
    if (current.requiresReconsent && !reconsentGranted) {
      throw const SourcePackageManagerException(
        'reconsent_required',
        'The source package security policy requires fresh consent.',
      );
    }
    final enabled = current.copyWith(
      status: SourcePackageStatus.enabled,
      requiresConsent: false,
      requiresReconsent: false,
    );
    _packages[packageId] = enabled;
    return enabled;
  }

  @override
  InstalledSourcePackage disable({
    required String packageId,
    required Version version,
  }) {
    final current = _requireVersion(packageId, version);
    if (current.status == SourcePackageStatus.disabled) {
      return current;
    }
    final disabled = current.copyWith(status: SourcePackageStatus.disabled);
    _packages[packageId] = disabled;
    return disabled;
  }

  InstalledSourcePackage _newPendingPackage({
    required SourcePackageManifest package,
    required bool requiresReconsent,
  }) {
    return InstalledSourcePackage(
      package: package,
      status: SourcePackageStatus.disabled,
      requiresConsent: true,
      requiresReconsent: requiresReconsent,
    );
  }

  void _validateOrThrow(
    SourcePackageManifest package, {
    SourcePackageManifest? previous,
  }) {
    final result = validate(package, previous: previous);
    if (!result.isValid) {
      throw SourcePackageManagerException(
        result.code,
        'The source package failed manager validation.',
      );
    }
  }

  void _validateInstalledState(InstalledSourcePackage installed) {
    if ((installed.status == SourcePackageStatus.enabled &&
            (installed.requiresConsent || installed.requiresReconsent)) ||
        (!installed.requiresConsent && installed.requiresReconsent)) {
      throw const SourcePackageManagerException(
        'persisted_state_invalid',
        'The persisted source package state is inconsistent.',
      );
    }
  }

  void _checkInstallVersion(
    SourcePackageManifest package, {
    required SourcePackageManifest? previous,
  }) {
    if (previous == null) return;
    if (package.version <= previous.version) {
      throw const SourcePackageManagerException(
        'version_not_newer',
        'An update must have a strictly newer package version.',
      );
    }
  }

  InstalledSourcePackage _requireVersion(String packageId, Version version) {
    final current = _packages[packageId];
    if (current == null) {
      throw const SourcePackageManagerException(
        'package_not_installed',
        'The source package is not installed.',
      );
    }
    if (current.package.version != version) {
      throw const SourcePackageManagerException(
        'package_version_mismatch',
        'The requested source package version is no longer current.',
      );
    }
    return current;
  }

  List<InstalledSourcePackage> _sortedPackages({
    required bool includeDisabled,
  }) {
    final packages = _packages.values
        .where(
          (package) =>
              includeDisabled || package.status == SourcePackageStatus.enabled,
        )
        .toList(growable: false);
    final sorted = List<InstalledSourcePackage>.from(packages)
      ..sort((left, right) {
        final byId = left.package.packageId.compareTo(right.package.packageId);
        if (byId != 0) return byId;
        return left.package.version.compareTo(right.package.version);
      });
    return UnmodifiableListView(sorted);
  }
}
