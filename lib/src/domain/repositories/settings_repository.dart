import '../models/app_settings.dart';

abstract interface class SettingsRepository {
  Future<AppSettings> load();

  Stream<AppSettings> watch();

  Future<void> save(AppSettings settings);
}
