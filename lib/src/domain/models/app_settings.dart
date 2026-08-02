enum ThemePreference { system, light, dark }

enum InterfaceLanguagePreference { system, zhHant, zhHans, ja, en }

final class AppSettings {
  AppSettings({
    required this.theme,
    required this.interfaceLanguage,
    required this.telemetryEnabled,
    required this.updatedAt,
  });

  factory AppSettings.defaults(DateTime now) => AppSettings(
    theme: ThemePreference.system,
    interfaceLanguage: InterfaceLanguagePreference.system,
    telemetryEnabled: false,
    updatedAt: now,
  );

  final ThemePreference theme;
  final InterfaceLanguagePreference interfaceLanguage;
  final bool telemetryEnabled;
  final DateTime updatedAt;

  AppSettings copyWith({
    ThemePreference? theme,
    InterfaceLanguagePreference? interfaceLanguage,
    bool? telemetryEnabled,
    DateTime? updatedAt,
  }) {
    return AppSettings(
      theme: theme ?? this.theme,
      interfaceLanguage: interfaceLanguage ?? this.interfaceLanguage,
      telemetryEnabled: telemetryEnabled ?? this.telemetryEnabled,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}
