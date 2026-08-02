import 'package:flutter/material.dart';
import 'package:wynime/src/design_system/tokens/radii.dart';

abstract final class WynimeTheme {
  static const Color _seed = Color(0xFF5267A8);

  static ThemeData light() => _build(Brightness.light);

  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: _seed,
      brightness: brightness,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: colorScheme.surface,
      cardTheme: CardThemeData(
        elevation: 0,
        color: colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(WynimeRadii.large),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        elevation: 0,
        backgroundColor: colorScheme.surfaceContainer,
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: colorScheme.surfaceContainer,
        indicatorShape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(WynimeRadii.medium),
        ),
      ),
    );
  }
}
