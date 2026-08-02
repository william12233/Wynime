import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/app_settings.dart';
import 'package:wynime/src/infrastructure/repositories/drift_settings_repository.dart';

import '../helpers/test_database.dart';

void main() {
  test(
    'settings default to telemetry disabled and persist typed values',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final now = DateTime.utc(2026, 8, 2);
      final repository = DriftSettingsRepository(database, clock: () => now);

      final defaults = await repository.load();
      expect(defaults.telemetryEnabled, isFalse);
      expect(defaults.theme, ThemePreference.system);

      final saved = AppSettings(
        theme: ThemePreference.dark,
        interfaceLanguage: InterfaceLanguagePreference.zhHant,
        telemetryEnabled: false,
        updatedAt: now.add(const Duration(minutes: 1)),
      );
      await repository.save(saved);

      final loaded = await repository.load();
      expect(loaded.theme, ThemePreference.dark);
      expect(loaded.interfaceLanguage, InterfaceLanguagePreference.zhHant);
      expect(loaded.telemetryEnabled, isFalse);
    },
  );
}
