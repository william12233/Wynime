import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/design_system/tokens/spacing.dart';

class PlaceholderPage extends StatelessWidget {
  const PlaceholderPage({
    required this.icon,
    required this.title,
    required this.description,
    super.key,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(WynimeSpacing.lg),
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: WynimeDimensions.contentMaxWidth,
            ),
            child: Semantics(
              container: true,
              header: true,
              label: title,
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(WynimeSpacing.xl),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(icon, size: 56, color: theme.colorScheme.primary),
                      const SizedBox(height: WynimeSpacing.lg),
                      Text(
                        title,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineMedium,
                      ),
                      const SizedBox(height: WynimeSpacing.sm),
                      Text(
                        description,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyLarge?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: WynimeSpacing.lg),
                      Chip(
                        avatar: const Icon(Icons.construction, size: 18),
                        label: Text(localizations.phaseZeroLabel),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
