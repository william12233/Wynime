import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as path;
import 'package:wynime/src/application/bangumi_sync_service.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/database/wynime_database_recovery.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';

void main() {
  test(
    'migrates a populated v1 database to v5 and preserves base rows',
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
      final subjectColumns = await database
          .customSelect('PRAGMA table_info(bangumi_subjects)')
          .get();
      final collectionColumns = await database
          .customSelect('PRAGMA table_info(bangumi_collections)')
          .get();
      final detailTables = await database
          .customSelect(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN "
            "('bangumi_subject_characters', 'bangumi_subject_persons', "
            "'bangumi_subject_relations')",
          )
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
      expect(
        subjectColumns.any((row) => row.read<String>('name') == 'rating_json'),
        isTrue,
      );
      expect(
        collectionColumns.any((row) => row.read<String>('name') == 'ep_status'),
        isTrue,
      );
      expect(detailTables, hasLength(3));
      expect(indexes, hasLength(2));
      expect(await _userVersion(database), 5);

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

  test(
    'migrates legacy failed operations into retryable or blocked states',
    () async {
      final database = WynimeDatabase(
        NativeDatabase.memory(setup: _createVersionThreeFailedFixture),
      );
      addTearDown(database.close);

      final valid =
          await (database.select(database.bangumiSyncOperations)
                ..where((table) => table.operationId.equals('legacy-valid')))
              .getSingle();
      final invalid =
          await (database.select(database.bangumiSyncOperations)
                ..where((table) => table.operationId.equals('legacy-invalid')))
              .getSingle();

      expect(valid.state, 'retryWaiting');
      expect(valid.attempts, 0);
      expect(valid.nextAttemptAt, isNotNull);
      expect(valid.lastErrorCode, 'rate_limited');
      expect(invalid.state, 'blocked');
      expect(invalid.attempts, 0);
      expect(invalid.nextAttemptAt, isNull);
      expect(invalid.lastErrorCode, 'legacy_operation_invalid');

      final store = DriftBangumiLocalStore(
        database,
        clock: () => DateTime.utc(2026, 9, 10, 12),
      );
      await store.loadActiveAccount();
      expect(await store.pendingCount(), 1);
      expect(await store.blockedCount(), 1);
      expect(
        (await store.pendingOperations(forceRetry: true)).single.operationId,
        'legacy-valid',
      );

      final client = _MigratedOperationClient();
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _migrationSession,
        clock: () => DateTime.utc(2026, 9, 10, 12),
        delay: (_) async {},
      ).syncNow();
      expect(result.processed, 1);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 1);
    },
  );

  test('creates custom Bangumi indexes for a fresh v5 database', () async {
    final database = WynimeDatabase(NativeDatabase.memory());
    addTearDown(database.close);

    final indexes = await database
        .customSelect(
          "SELECT name FROM sqlite_master WHERE type = 'index' AND name IN "
          "('bangumi_sync_due_idx', 'bangumi_collections_account_status_idx')",
        )
        .get();

    expect(indexes, hasLength(2));
    expect(await _userVersion(database), 5);
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

void _createVersionThreeFailedFixture(dynamic database) {
  _createVersionOneFixture(database);
  database.execute('''
CREATE TABLE bangumi_accounts (
  account_id TEXT NOT NULL PRIMARY KEY,
  username TEXT NOT NULL,
  nickname TEXT,
  avatar_url TEXT,
  is_active INTEGER NOT NULL DEFAULT 0,
  schedule_updated_at INTEGER,
  last_seen_at INTEGER NOT NULL
)''');
  database.execute('''
CREATE TABLE bangumi_schedules (
  account_id TEXT NOT NULL REFERENCES bangumi_accounts(account_id) ON DELETE CASCADE,
  entry_id TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  subject_name TEXT NOT NULL,
  air_weekday INTEGER NOT NULL,
  air_date INTEGER,
  episode_number REAL,
  image_url TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (account_id, entry_id)
)''');
  database.execute('''
CREATE TABLE bangumi_subjects (
  subject_id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  name_cn TEXT NOT NULL,
  summary TEXT NOT NULL,
  image_url TEXT,
  eps INTEGER,
  updated_at INTEGER NOT NULL
)''');
  database.execute('''
CREATE TABLE bangumi_collections (
  account_id TEXT NOT NULL REFERENCES bangumi_accounts(account_id) ON DELETE CASCADE,
  subject_id TEXT NOT NULL REFERENCES bangumi_subjects(subject_id) ON DELETE CASCADE,
  status INTEGER,
  remote_revision TEXT,
  local_updated_at INTEGER NOT NULL,
  remote_updated_at INTEGER,
  PRIMARY KEY (account_id, subject_id)
)''');
  database.execute('''
CREATE TABLE bangumi_episodes (
  episode_id TEXT NOT NULL PRIMARY KEY,
  subject_id TEXT NOT NULL REFERENCES bangumi_subjects(subject_id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  name_cn TEXT NOT NULL,
  sort REAL NOT NULL,
  type INTEGER NOT NULL,
  duration INTEGER,
  updated_at INTEGER NOT NULL
)''');
  database.execute('''
CREATE TABLE bangumi_episode_collections (
  account_id TEXT NOT NULL REFERENCES bangumi_accounts(account_id) ON DELETE CASCADE,
  episode_id TEXT NOT NULL REFERENCES bangumi_episodes(episode_id) ON DELETE CASCADE,
  watched INTEGER NOT NULL DEFAULT 0,
  remote_revision TEXT,
  local_updated_at INTEGER NOT NULL,
  remote_updated_at INTEGER,
  PRIMARY KEY (account_id, episode_id)
)''');
  database.execute('''
CREATE TABLE bangumi_mappings (
  local_subject_key TEXT NOT NULL PRIMARY KEY,
  bangumi_subject_id TEXT NOT NULL REFERENCES bangumi_subjects(subject_id) ON DELETE RESTRICT,
  confirmed_at INTEGER NOT NULL
)''');
  database.execute('''
CREATE TABLE bangumi_sync_operations (
  operation_id TEXT NOT NULL PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES bangumi_accounts(account_id) ON DELETE CASCADE,
  subject_id TEXT NOT NULL REFERENCES bangumi_subjects(subject_id) ON DELETE CASCADE,
  episode_id TEXT REFERENCES bangumi_episodes(episode_id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  collection_status INTEGER,
  watched INTEGER,
  base_remote_revision TEXT,
  state TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at INTEGER,
  last_error_code TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
)''');
  database.execute('''
CREATE TABLE bangumi_conflict_snapshots (
  operation_id TEXT NOT NULL PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES bangumi_accounts(account_id) ON DELETE CASCADE,
  subject_id TEXT NOT NULL REFERENCES bangumi_subjects(subject_id) ON DELETE CASCADE,
  status INTEGER,
  watched_episode_ids TEXT NOT NULL,
  remote_revision TEXT NOT NULL,
  captured_at INTEGER NOT NULL
)''');
  database.execute(
    "INSERT INTO bangumi_accounts VALUES ('7', 'alice', NULL, NULL, 1, NULL, 0)",
  );
  database.execute(
    "INSERT INTO bangumi_subjects VALUES ('42', 'Title', '作品', '', NULL, 1, 0)",
  );
  database.execute(
    "INSERT INTO bangumi_episodes VALUES ('1001', '42', 'Episode 1', '第一集', 1, 0, NULL, 0)",
  );
  database.execute('''
INSERT INTO bangumi_sync_operations
  (operation_id, account_id, subject_id, episode_id, kind, collection_status,
   watched, base_remote_revision, state, attempts, next_attempt_at,
   last_error_code, created_at, updated_at)
VALUES ('legacy-valid', '7', '42', NULL, 'collectionStatus', 3, NULL, NULL,
        'failed', 4, NULL, 'rate_limited', 0, 0)
''');
  database.execute('''
INSERT INTO bangumi_sync_operations
  (operation_id, account_id, subject_id, episode_id, kind, collection_status,
   watched, base_remote_revision, state, attempts, next_attempt_at,
   last_error_code, created_at, updated_at)
VALUES ('legacy-invalid', '7', '42', NULL, 'collectionStatus', NULL, NULL,
        NULL, 'failed', 4, NULL, NULL, 0, 0)
''');
  database.execute(
    'CREATE INDEX bangumi_sync_due_idx ON bangumi_sync_operations '
    '(account_id, state, next_attempt_at)',
  );
  database.execute(
    'CREATE INDEX bangumi_collections_account_status_idx ON '
    'bangumi_collections (account_id, status)',
  );
  database.execute(
    'CREATE INDEX bangumi_schedule_account_day_idx ON bangumi_schedules '
    '(account_id, air_weekday, air_date)',
  );
  database.execute('PRAGMA user_version = 3');
}

final _migrationSession = BangumiAuthSession(
  accountId: '7',
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresAt: DateTime.utc(2030),
);

final class _MigratedOperationClient implements BangumiClient {
  var remote = _migrationRemote(BangumiCollectionStatus.wish);

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) async => remote;

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async {
    remote = _migrationRemote(status);
  }

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {}

  @override
  Future<BangumiUserIdentity> currentUser() => throw UnimplementedError();

  @override
  Future<BangumiCollectionPage> collections({int offset = 0, int limit = 30}) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiScheduleEntry>> calendar() => throw UnimplementedError();

  @override
  Future<BangumiSubject> subject(String id) => throw UnimplementedError();

  @override
  Future<BangumiEpisodePage> episodes(String subjectId) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiCharacter>> characters(String subjectId) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiPersonCredit>> persons(String subjectId) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiSubjectRelation>> relations(String subjectId) =>
      throw UnimplementedError();
}

BangumiRemoteState _migrationRemote(BangumiCollectionStatus status) {
  return BangumiRemoteState(
    accountId: '7',
    subjectId: '42',
    status: status,
    watchedEpisodeIds: const <String>{},
    remoteRevision: BangumiRemoteState.fingerprint(
      subjectId: '42',
      status: status,
      watchedEpisodeIds: const <String>{},
    ),
  );
}

Future<int> _userVersion(WynimeDatabase database) async {
  final row = await database.customSelect('PRAGMA user_version').getSingle();
  return row.read<int>('user_version');
}
