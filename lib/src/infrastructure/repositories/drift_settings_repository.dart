import 'package:drift/drift.dart';

import '../../domain/models/app_settings.dart';
import '../../domain/repositories/settings_repository.dart';
import '../database/wynime_database.dart';

final class DriftSettingsRepository implements SettingsRepository {
  DriftSettingsRepository(this._database, {DateTime Function()? clock})
    : _clock = clock ?? DateTime.now;

  final WynimeDatabase _database;
  final DateTime Function() _clock;

  @override
  Future<AppSettings> load() async {
    final query = _database.select(_database.appSettingsRows)
      ..where((table) => table.singletonId.equals(0));
    final row = await query.getSingleOrNull();
    return row == null ? AppSettings.defaults(_clock()) : _map(row);
  }

  @override
  Stream<AppSettings> watch() {
    final query = _database.select(_database.appSettingsRows)
      ..where((table) => table.singletonId.equals(0));
    return query.watchSingleOrNull().map(
      (row) => row == null ? AppSettings.defaults(_clock()) : _map(row),
    );
  }

  @override
  Future<void> save(AppSettings settings) async {
    await _database
        .into(_database.appSettingsRows)
        .insertOnConflictUpdate(
          AppSettingsRowsCompanion(
            singletonId: const Value(0),
            theme: Value(settings.theme.name),
            interfaceLanguage: Value(settings.interfaceLanguage.name),
            telemetryEnabled: Value(settings.telemetryEnabled),
            updatedAt: Value(settings.updatedAt),
          ),
        );
  }

  AppSettings _map(SettingsRecord row) {
    return AppSettings(
      theme: ThemePreference.values.byName(row.theme),
      interfaceLanguage: InterfaceLanguagePreference.values.byName(
        row.interfaceLanguage,
      ),
      telemetryEnabled: row.telemetryEnabled,
      updatedAt: row.updatedAt,
    );
  }
}
