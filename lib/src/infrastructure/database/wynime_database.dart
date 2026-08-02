import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'wynime_database.g.dart';

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
  BoolColumn get isCompleted =>
      boolean().withDefault(const Constant(false))();
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
  Set<Column<Object>> get primaryKey => {artifactId};
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

@DriftDatabase(
  tables: [
    AppSettingsRows,
    WatchHistoryRows,
    ArtifactManifests,
    ArtifactRows,
    DeleteJobRows,
  ],
)
final class WynimeDatabase extends _$WynimeDatabase {
  WynimeDatabase(super.executor);

  WynimeDatabase.defaults()
    : super(
        driftDatabase(
          name: 'wynime',
          native: const DriftNativeOptions(shareAcrossIsolates: true),
        ),
      );

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) => migrator.createAll(),
    beforeOpen: (details) async {
      await customStatement('PRAGMA foreign_keys = ON');
    },
  );
}
