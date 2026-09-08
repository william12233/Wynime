import 'dart:io';

import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import '../../domain/models/software_update_models.dart';
import 'wynime_database.dart';

/// Creates a SQLite-consistent snapshot with `VACUUM INTO` before the running
/// database is closed for a Windows portable update. It never copies an active
/// `.sqlite`, `-wal`, or `-shm` file as a raw filesystem bundle.
final class DriftDatabaseRecoveryPort implements DatabaseRecoveryPort {
  DriftDatabaseRecoveryPort(
    this._database, {
    Future<Directory> Function()? temporaryDirectoryProvider,
    Future<String> Function()? databasePathProvider,
  }) : _temporaryDirectoryProvider =
           temporaryDirectoryProvider ?? getTemporaryDirectory,
       _databasePathProvider = databasePathProvider ?? wynimeDatabaseFilePath;

  final WynimeDatabase _database;
  final Future<Directory> Function() _temporaryDirectoryProvider;
  final Future<String> Function() _databasePathProvider;

  @override
  Future<DatabaseRecoveryPoint> createRecoveryPoint() => _createRecoveryPoint();

  @override
  Future<DatabaseRecoveryPoint> createRecoveryPointAndQuiesce() async {
    await _database.quiesceWrites();
    try {
      return await _createRecoveryPoint();
    } on Object {
      _database.resumeWrites();
      rethrow;
    }
  }

  Future<DatabaseRecoveryPoint> _createRecoveryPoint() async {
    final temporaryDirectory = await _temporaryDirectoryProvider();
    final recoveryDirectory = Directory(
      path.join(temporaryDirectory.path, 'wynime-database-recovery'),
    );
    if (await recoveryDirectory.exists()) {
      await recoveryDirectory.delete(recursive: true);
    }
    await recoveryDirectory.create(recursive: true);
    final snapshot = File(path.join(recoveryDirectory.path, 'wynime.sqlite'));

    try {
      final databasePath = await _databasePathProvider();
      await _database.exclusively(() async {
        await _database.customStatement('PRAGMA wal_checkpoint(TRUNCATE)');
        await _database.customStatement('VACUUM INTO ?', <Object>[
          snapshot.path,
        ]);
      });
      if (!await snapshot.exists() || await snapshot.length() == 0) {
        throw const SoftwareUpdateException(
          UpdateFailureReason.databaseRecovery,
          'recovery_snapshot_missing',
        );
      }
      return DatabaseRecoveryPoint(
        identifier: snapshot.path,
        databasePath: databasePath,
      );
    } on SoftwareUpdateException {
      if (await recoveryDirectory.exists()) {
        await recoveryDirectory.delete(recursive: true);
      }
      rethrow;
    } on Object {
      if (await recoveryDirectory.exists()) {
        await recoveryDirectory.delete(recursive: true);
      }
      throw const SoftwareUpdateException(
        UpdateFailureReason.databaseRecovery,
        'recovery_snapshot_failed',
      );
    }
  }

  @override
  Future<void> resumeAfterAbortedHandoff(DatabaseRecoveryPoint point) async {
    _database.resumeWrites();
  }

  @override
  Future<void> discardRecoveryPoint(DatabaseRecoveryPoint point) async {
    final source = File(point.identifier);
    final recoveryDirectory = source.parent;
    if (await source.exists()) await source.delete();
    if (await recoveryDirectory.exists()) {
      final remaining = await recoveryDirectory.list().isEmpty;
      if (remaining) await recoveryDirectory.delete();
    }
  }

  @override
  Future<void> restoreRecoveryPoint(DatabaseRecoveryPoint point) async {
    final source = File(point.identifier);
    if (!await source.exists()) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.databaseRecovery,
        'recovery_snapshot_missing',
      );
    }
    await _database.close();
    final target = File(point.databasePath ?? await _databasePathProvider());
    await target.parent.create(recursive: true);
    await source.copy(target.path);
    for (final suffix in const <String>['-wal', '-shm']) {
      final sidecar = File('${target.path}$suffix');
      if (await sidecar.exists()) {
        await sidecar.delete();
      }
    }
  }
}
