import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as path;
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/database/wynime_database_recovery.dart';

void main() {
  test(
    'migrates a populated v1 database to v3 and preserves base rows',
    () async {
      final executor = NativeDatabase.memory(setup: _createVersionOneFixture);
      final database = WynimeDatabase(executor);
      addTearDown(database.close);

      final settings = await database.select(database.appSettingsRows).get();
      final history = await database.select(database.watchHistoryRows).get();
      final manifests = await database.select(database.artifactManifests).get();
      final artifacts = await database.select(database.artifactRows).get();
      final jobs = await database.select(database.deleteJobRows).get();
      final accounts = await database.select(database.bangumiAccounts).get();
      final schedules = await database.select(database.bangumiSchedules).get();
      final accountColumns = await database
          .customSelect('PRAGMA table_info(bangumi_accounts)')
          .get();
      final indexes = await database
          .customSelect(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name IN "
            "('bangumi_sync_due_idx', 'bangumi_collections_account_status_idx')",
          )
          .get();

      expect(settings.single.theme, 'dark');
      expect(history.single.progressId, 'progress-1');
      expect(manifests.single.manifestId, 'manifest-1');
      expect(artifacts.single.fileUri, 'file:///episode.mkv');
      expect(jobs.single.status, 'pending');
      expect(accounts, isEmpty);
      expect(schedules, isEmpty);
      expect(
        accountColumns.any(
          (row) => row.read<String>('name') == 'schedule_updated_at',
        ),
        isTrue,
      );
      expect(indexes, hasLength(2));
      expect(await _userVersion(database), 3);

      await database
          .into(database.bangumiAccounts)
          .insert(
            BangumiAccountsCompanion.insert(
              accountId: '7',
              username: 'alice',
              lastSeenAt: DateTime.utc(2026, 9, 8),
            ),
          );
      expect(
        (await database.select(database.bangumiAccounts).get()).single.username,
        'alice',
      );
    },
  );

  test(
    'creates a SQLite-consistent recovery point without copying WAL files',
    () async {
      final database = WynimeDatabase(
        NativeDatabase.memory(setup: _createVersionOneFixture),
      );
      final temporaryDirectory = await Directory.systemTemp.createTemp(
        'wynime-recovery-test-',
      );
      addTearDown(database.close);
      addTearDown(() => temporaryDirectory.delete(recursive: true));
      final databasePath = path.join(temporaryDirectory.path, 'live.sqlite');
      final recovery = DriftDatabaseRecoveryPort(
        database,
        temporaryDirectoryProvider: () async => temporaryDirectory,
        databasePathProvider: () async => databasePath,
      );

      final point = await recovery.createRecoveryPoint();

      expect(point.databasePath, databasePath);
      expect(await File(point.identifier).exists(), isTrue);
      final snapshot = WynimeDatabase(NativeDatabase(File(point.identifier)));
      addTearDown(snapshot.close);
      expect(
        (await snapshot.select(snapshot.appSettingsRows).get()).single.theme,
        'dark',
      );
      expect(await File('${point.identifier}-wal').exists(), isFalse);
      expect(await File('${point.identifier}-shm').exists(), isFalse);
    },
  );

  test(
    'quiesced recovery blocks writes until an aborted handoff resumes them',
    () async {
      final database = WynimeDatabase(
        NativeDatabase.memory(setup: _createVersionOneFixture),
      );
      final temporaryDirectory = await Directory.systemTemp.createTemp(
        'wynime-quiesced-recovery-test-',
      );
      addTearDown(database.close);
      addTearDown(() => temporaryDirectory.delete(recursive: true));
      final databasePath = path.join(temporaryDirectory.path, 'live.sqlite');
      final recovery = DriftDatabaseRecoveryPort(
        database,
        temporaryDirectoryProvider: () async => temporaryDirectory,
        databasePathProvider: () async => databasePath,
      );

      final point = await recovery.createRecoveryPointAndQuiesce();
      expect(database.writeGate.isQuiesced, isTrue);
      await expectLater(
        database.runWrite(
          () => database
              .into(database.bangumiAccounts)
              .insert(
                BangumiAccountsCompanion.insert(
                  accountId: 'blocked',
                  username: 'blocked',
                  lastSeenAt: DateTime.utc(2026, 9, 8),
                ),
              ),
        ),
        throwsA(isA<StateError>()),
      );

      await recovery.resumeAfterAbortedHandoff(point);
      await recovery.discardRecoveryPoint(point);
      expect(database.writeGate.isQuiesced, isFalse);
      await database.runWrite(
        () => database
            .into(database.bangumiAccounts)
            .insert(
              BangumiAccountsCompanion.insert(
                accountId: 'resumed',
                username: 'resumed',
                lastSeenAt: DateTime.utc(2026, 9, 8),
              ),
            ),
      );
      expect(
        (await database.select(database.bangumiAccounts).get())
            .single
            .accountId,
        'resumed',
      );
      expect(await File(point.identifier).exists(), isFalse);
    },
  );

  test('creates custom Bangumi indexes for a fresh v3 database', () async {
    final database = WynimeDatabase(NativeDatabase.memory());
    addTearDown(database.close);

    final indexes = await database
        .customSelect(
          "SELECT name FROM sqlite_master WHERE type = 'index' AND name IN "
          "('bangumi_sync_due_idx', 'bangumi_collections_account_status_idx')",
        )
        .get();

    expect(indexes, hasLength(2));
    expect(await _userVersion(database), 3);
  });
}

void _createVersionOneFixture(dynamic database) {
  database.execute('''
CREATE TABLE app_settings_rows (
  singleton_id INTEGER NOT NULL DEFAULT 0,
  theme TEXT NOT NULL,
  interface_language TEXT NOT NULL,
  telemetry_enabled INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (singleton_id)
)''');
  database.execute('''
CREATE TABLE watch_history_rows (
  progress_id TEXT NOT NULL,
  source_id TEXT NOT NULL,
  line_id TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  episode_id TEXT NOT NULL,
  position_ms INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL,
  is_completed INTEGER NOT NULL DEFAULT 0,
  player_backend_id TEXT,
  timeline_map_id TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (progress_id),
  UNIQUE (source_id, line_id, subject_id, episode_id)
)''');
  database.execute('''
CREATE TABLE artifact_manifests (
  manifest_id TEXT NOT NULL,
  download_id TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (manifest_id)
)''');
  database.execute('''
CREATE TABLE artifact_rows (
  artifact_id TEXT NOT NULL,
  manifest_id TEXT NOT NULL REFERENCES artifact_manifests(manifest_id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  file_uri TEXT NOT NULL UNIQUE,
  PRIMARY KEY (manifest_id, artifact_id)
)''');
  database.execute('''
CREATE TABLE delete_job_rows (
  job_id TEXT NOT NULL,
  artifact_manifest_id TEXT NOT NULL REFERENCES artifact_manifests(manifest_id) ON DELETE RESTRICT,
  status TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  failure_code TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (job_id)
)''');
  database.execute(
    '''INSERT INTO app_settings_rows VALUES (0, 'dark', 'zhHant', 0, 0)''',
  );
  database.execute(
    '''INSERT INTO watch_history_rows VALUES ('progress-1', 'source', 'line', 'subject', 'episode', 1200, 2400, 0, NULL, NULL, 0)''',
  );
  database.execute(
    '''INSERT INTO artifact_manifests VALUES ('manifest-1', 'download-1', 0)''',
  );
  database.execute(
    '''INSERT INTO artifact_rows VALUES ('artifact-1', 'manifest-1', 'video', 'file:///episode.mkv')''',
  );
  database.execute(
    '''INSERT INTO delete_job_rows VALUES ('job-1', 'manifest-1', 'pending', 0, NULL, 0, 0)''',
  );
  database.execute('PRAGMA user_version = 1');
}

Future<int> _userVersion(WynimeDatabase database) async {
  final row = await database.customSelect('PRAGMA user_version').getSingle();
  return row.read<int>('user_version');
}
