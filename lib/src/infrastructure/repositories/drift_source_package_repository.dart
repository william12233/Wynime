import 'package:drift/drift.dart';

import '../../domain/models/source_package_manager_models.dart';
import '../../domain/models/source_package_manifest.dart';
import '../../domain/repositories/source_package_repository.dart';
import '../database/wynime_database.dart';
import '../source_rules/source_package_decoder.dart';
import '../source_rules/source_package_encoder.dart';

/// Persists the manager's complete package snapshot in one SQLite transaction.
///
/// This repository stores package declarations and consent state only. It
/// does not fetch packages, verify publisher signatures or execute a package.
final class DriftSourcePackageRepository implements SourcePackageRepository {
  DriftSourcePackageRepository(
    this._database, {
    this._decoder = const SourcePackageDecoder(),
    this._encoder = const SourcePackageEncoder(),
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  final WynimeDatabase _database;
  final SourcePackageDecoder _decoder;
  final SourcePackageEncoder _encoder;
  final DateTime Function() _clock;

  @override
  Future<List<InstalledSourcePackage>> load() async {
    try {
      final query = _database.select(_database.sourcePackages)
        ..orderBy([(table) => OrderingTerm.asc(table.packageId)]);
      final rows = await query.get();
      return rows.map(_map).toList(growable: false);
    } on SourcePackageRepositoryException {
      rethrow;
    } on Object {
      throw const SourcePackageRepositoryException(
        'storage_read_failed',
        'The source package state could not be read.',
      );
    }
  }

  @override
  Future<void> replaceAll(Iterable<InstalledSourcePackage> packages) async {
    final snapshot = packages.toList(growable: false);
    _validateSnapshot(snapshot);
    final now = _clock().toUtc();
    final List<SourcePackagesCompanion> rows;
    try {
      rows = snapshot
          .map(
            (installed) => SourcePackagesCompanion.insert(
              packageId: installed.package.packageId,
              packageJson: _encoder.encode(installed.package),
              status: installed.status.name,
              requiresConsent: installed.requiresConsent,
              requiresReconsent: installed.requiresReconsent,
              updatedAt: now,
            ),
          )
          .toList(growable: false);
    } on SourcePackageRepositoryException {
      rethrow;
    } on Object {
      throw const SourcePackageRepositoryException(
        'state_invalid',
        'The source package snapshot could not be encoded.',
      );
    }

    try {
      await _database.runWrite(
        () => _database.transaction(() async {
          final existingRows = await _database
              .select(_database.sourcePackages)
              .get();
          final retainedIds = snapshot
              .map((installed) => installed.package.packageId)
              .toSet();
          final removedIds = existingRows
              .map((row) => row.packageId)
              .where((packageId) => !retainedIds.contains(packageId))
              .toSet();
          await _database.delete(_database.sourcePackages).go();
          if (rows.isNotEmpty) {
            await _database.batch((batch) {
              batch.insertAll(_database.sourcePackages, rows);
            });
          }
          for (final packageId in removedIds) {
            await (_database.delete(
              _database.sourceSubjectMappings,
            )..where((table) => table.packageId.equals(packageId))).go();
            await (_database.delete(
              _database.sourceEpisodeMappings,
            )..where((table) => table.packageId.equals(packageId))).go();
          }
        }),
      );
    } on SourcePackageRepositoryException {
      rethrow;
    } on Object {
      throw const SourcePackageRepositoryException(
        'storage_write_failed',
        'The source package state could not be committed.',
      );
    }
  }

  InstalledSourcePackage _map(SourcePackageRecord row) {
    final SourcePackageManifest package;
    final SourcePackageStatus status;
    try {
      package = _decoder.decode(row.packageJson);
      status = SourcePackageStatus.values.byName(row.status);
    } on Object {
      throw const SourcePackageRepositoryException(
        'persisted_state_invalid',
        'A persisted source package record is invalid.',
      );
    }
    if (package.packageId != row.packageId ||
        (status == SourcePackageStatus.enabled &&
            (row.requiresConsent || row.requiresReconsent)) ||
        (!row.requiresConsent && row.requiresReconsent)) {
      throw const SourcePackageRepositoryException(
        'persisted_state_invalid',
        'A persisted source package record is inconsistent.',
      );
    }
    return InstalledSourcePackage(
      package: package,
      status: status,
      requiresConsent: row.requiresConsent,
      requiresReconsent: row.requiresReconsent,
    );
  }

  void _validateSnapshot(List<InstalledSourcePackage> snapshot) {
    final ids = <String>{};
    for (final installed in snapshot) {
      if (!ids.add(installed.package.packageId) ||
          (installed.status == SourcePackageStatus.enabled &&
              (installed.requiresConsent || installed.requiresReconsent)) ||
          (!installed.requiresConsent && installed.requiresReconsent)) {
        throw const SourcePackageRepositoryException(
          'state_invalid',
          'The source package snapshot is inconsistent.',
        );
      }
    }
  }
}
