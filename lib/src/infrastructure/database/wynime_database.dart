import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'database_write_gate.dart';

part 'wynime_database.g.dart';

Future<String> wynimeDatabaseFilePath() async {
  final directory = await getApplicationDocumentsDirectory();
  return path.join(directory.path, 'wynime.sqlite');
}

@DataClassName('SettingsRecord')
class AppSettingsRows extends Table {
  IntColumn get singletonId => integer().withDefault(const Constant(0))();
  TextColumn get theme => text()();
  TextColumn get interfaceLanguage => text()();
  BoolColumn get telemetryEnabled =>
      boolean().withDefault(const Constant(false))();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {singletonId};
}

@DataClassName('WatchHistoryRecord')
class WatchHistoryRows extends Table {
  TextColumn get progressId => text()();
  TextColumn get sourceId => text()();
  TextColumn get lineId => text()();
  TextColumn get subjectId => text()();
  TextColumn get episodeId => text()();
  IntColumn get positionMs => integer()();
  IntColumn get durationMs => integer()();
  BoolColumn get isCompleted => boolean().withDefault(const Constant(false))();
  TextColumn get playerBackendId => text().nullable()();
  TextColumn get timelineMapId => text().nullable()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {progressId};

  @override
  List<Set<Column<Object>>> get uniqueKeys => [
    {sourceId, lineId, subjectId, episodeId},
  ];
}

@DataClassName('ArtifactManifestRecord')
class ArtifactManifests extends Table {
  TextColumn get manifestId => text()();
  TextColumn get downloadId => text().unique()();
  DateTimeColumn get createdAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {manifestId};
}

@DataClassName('ArtifactRecord')
class ArtifactRows extends Table {
  TextColumn get artifactId => text()();
  TextColumn get manifestId => text().references(
    ArtifactManifests,
    #manifestId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get kind => text()();
  TextColumn get fileUri => text().unique()();

  @override
  Set<Column<Object>> get primaryKey => {manifestId, artifactId};
}

@DataClassName('DeleteJobRecord')
class DeleteJobRows extends Table {
  TextColumn get jobId => text()();
  TextColumn get artifactManifestId => text().references(
    ArtifactManifests,
    #manifestId,
    onDelete: KeyAction.restrict,
  )();
  TextColumn get status => text()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  TextColumn get failureCode => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {jobId};
}

@DataClassName('BangumiAccountRecord')
class BangumiAccounts extends Table {
  TextColumn get accountId => text()();
  TextColumn get username => text()();
  TextColumn get nickname => text().nullable()();
  TextColumn get avatarUrl => text().nullable()();
  BoolColumn get isActive => boolean().withDefault(const Constant(false))();
  DateTimeColumn get scheduleUpdatedAt => dateTime().nullable()();
  DateTimeColumn get lastSeenAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {accountId};
}

@DataClassName('BangumiScheduleRecord')
class BangumiSchedules extends Table {
  TextColumn get accountId => text().references(
    BangumiAccounts,
    #accountId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get entryId => text()();
  TextColumn get subjectId => text()();
  TextColumn get subjectName => text()();
  IntColumn get airWeekday => integer()();
  DateTimeColumn get airDate => dateTime().nullable()();
  RealColumn get episodeNumber => real().nullable()();
  TextColumn get imageUrl => text().nullable()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {accountId, entryId};
}

@DataClassName('BangumiSubjectRecord')
class BangumiSubjects extends Table {
  TextColumn get subjectId => text()();
  TextColumn get name => text()();
  TextColumn get nameCn => text()();
  TextColumn get summary => text()();
  TextColumn get imageUrl => text().nullable()();
  IntColumn get eps => integer().nullable()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {subjectId};
}

@DataClassName('BangumiCollectionRecord')
class BangumiCollections extends Table {
  TextColumn get accountId => text().references(
    BangumiAccounts,
    #accountId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get subjectId => text().references(
    BangumiSubjects,
    #subjectId,
    onDelete: KeyAction.cascade,
  )();
  IntColumn get status => integer().nullable()();
  TextColumn get remoteRevision => text().nullable()();
  DateTimeColumn get localUpdatedAt => dateTime()();
  DateTimeColumn get remoteUpdatedAt => dateTime().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {accountId, subjectId};
}

@DataClassName('BangumiEpisodeRecord')
class BangumiEpisodes extends Table {
  TextColumn get episodeId => text()();
  TextColumn get subjectId => text().references(
    BangumiSubjects,
    #subjectId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get name => text()();
  TextColumn get nameCn => text()();
  RealColumn get sort => real()();
  IntColumn get type => integer()();
  IntColumn get duration => integer().nullable()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {episodeId};
}

@DataClassName('BangumiEpisodeCollectionRecord')
class BangumiEpisodeCollections extends Table {
  TextColumn get accountId => text().references(
    BangumiAccounts,
    #accountId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get episodeId => text().references(
    BangumiEpisodes,
    #episodeId,
    onDelete: KeyAction.cascade,
  )();
  BoolColumn get watched => boolean().withDefault(const Constant(false))();
  TextColumn get remoteRevision => text().nullable()();
  DateTimeColumn get localUpdatedAt => dateTime()();
  DateTimeColumn get remoteUpdatedAt => dateTime().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {accountId, episodeId};
}

@DataClassName('BangumiMappingRecord')
class BangumiMappings extends Table {
  TextColumn get localSubjectKey => text()();
  TextColumn get bangumiSubjectId => text().references(
    BangumiSubjects,
    #subjectId,
    onDelete: KeyAction.restrict,
  )();
  DateTimeColumn get confirmedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {localSubjectKey};
}

@DataClassName('BangumiSyncOperationRecord')
class BangumiSyncOperations extends Table {
  TextColumn get operationId => text()();
  TextColumn get accountId => text().references(
    BangumiAccounts,
    #accountId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get subjectId => text().references(
    BangumiSubjects,
    #subjectId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get episodeId => text().nullable().references(
    BangumiEpisodes,
    #episodeId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get kind => text()();
  IntColumn get collectionStatus => integer().nullable()();
  BoolColumn get watched => boolean().nullable()();
  TextColumn get baseRemoteRevision => text().nullable()();
  TextColumn get state => text()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextAttemptAt => dateTime().nullable()();
  TextColumn get lastErrorCode => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {operationId};
}

@DataClassName('BangumiConflictSnapshotRecord')
class BangumiConflictSnapshots extends Table {
  TextColumn get operationId => text()();
  TextColumn get accountId => text().references(
    BangumiAccounts,
    #accountId,
    onDelete: KeyAction.cascade,
  )();
  TextColumn get subjectId => text().references(
    BangumiSubjects,
    #subjectId,
    onDelete: KeyAction.cascade,
  )();
  IntColumn get status => integer().nullable()();
  TextColumn get watchedEpisodeIds => text()();
  TextColumn get remoteRevision => text()();
  DateTimeColumn get capturedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {operationId};
}

@DriftDatabase(
  tables: [
    AppSettingsRows,
    WatchHistoryRows,
    ArtifactManifests,
    ArtifactRows,
    DeleteJobRows,
    BangumiAccounts,
    BangumiSchedules,
    BangumiSubjects,
    BangumiCollections,
    BangumiEpisodes,
    BangumiEpisodeCollections,
    BangumiMappings,
    BangumiSyncOperations,
    BangumiConflictSnapshots,
  ],
)
final class WynimeDatabase extends _$WynimeDatabase {
  WynimeDatabase(super.executor);

  WynimeDatabase.defaults()
    : super(
        driftDatabase(
          name: 'wynime',
          native: DriftNativeOptions(
            shareAcrossIsolates: true,
            databasePath: wynimeDatabaseFilePath,
          ),
        ),
      );

  @override
  int get schemaVersion => 3;

  final DatabaseWriteGate writeGate = DatabaseWriteGate();

  Future<T> runWrite<T>(Future<T> Function() operation) =>
      writeGate.run(operation);

  Future<void> quiesceWrites() => writeGate.quiesce();

  void resumeWrites() => writeGate.resume();

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) async {
      await migrator.createAll();
      await _createBangumiIndexes();
    },
    onUpgrade: (migrator, from, to) async {
      if (from < 2) {
        await migrator.createTable(bangumiAccounts);
        await migrator.createTable(bangumiSchedules);
        await migrator.createTable(bangumiSubjects);
        await migrator.createTable(bangumiCollections);
        await migrator.createTable(bangumiEpisodes);
        await migrator.createTable(bangumiEpisodeCollections);
        await migrator.createTable(bangumiMappings);
        await migrator.createTable(bangumiSyncOperations);
        await migrator.createTable(bangumiConflictSnapshots);
      }
      if (from < 3) {
        if (from >= 2) {
          await migrator.addColumn(
            bangumiAccounts,
            bangumiAccounts.scheduleUpdatedAt,
          );
          await migrator.createTable(bangumiSchedules);
        }
        await _createBangumiIndexes();
      }
    },
    beforeOpen: (details) async {
      await customStatement('PRAGMA foreign_keys = ON');
    },
  );

  Future<void> _createBangumiIndexes() async {
    await customStatement(
      'CREATE INDEX IF NOT EXISTS bangumi_sync_due_idx '
      'ON bangumi_sync_operations (account_id, state, next_attempt_at)',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS bangumi_collections_account_status_idx '
      'ON bangumi_collections (account_id, status)',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS bangumi_schedule_account_day_idx '
      'ON bangumi_schedules (account_id, air_weekday, air_date)',
    );
  }
}
