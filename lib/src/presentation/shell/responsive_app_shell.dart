import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/app/app_destination.dart';
import 'package:wynime/src/design_system/tokens/breakpoints.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/presentation/pages/placeholder_page.dart';

class ResponsiveAppShell extends StatefulWidget {
  const ResponsiveAppShell({super.key});

  @override
  State<ResponsiveAppShell> createState() => _ResponsiveAppShellState();
}

class _ResponsiveAppShellState extends State<ResponsiveAppShell> {
  int _selectedIndex = 0;

  void _selectDestination(int index) {
    if (_selectedIndex == index) {
      return;
    }
    setState(() => _selectedIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final destinations = AppDestination.values;
    final selectedDestination = destinations[_selectedIndex];

    final page = PlaceholderPage(
      key: ValueKey(selectedDestination),
      icon: selectedDestination.selectedIcon,
      title: selectedDestination.label(localizations),
      description: selectedDestination.description(localizations),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final windowClass = WynimeBreakpoints.classify(constraints.maxWidth);

        return switch (windowClass) {
          WynimeWindowClass.compact => Scaffold(
            appBar: AppBar(
              title: Text(selectedDestination.label(localizations)),
            ),
            body: page,
            bottomNavigationBar: NavigationBar(
              selectedIndex: _selectedIndex,
              labelBehavior:
                  NavigationDestinationLabelBehavior.onlyShowSelected,
              onDestinationSelected: _selectDestination,
              destinations: [
                for (final destination in destinations)
                  NavigationDestination(
                    icon: Icon(destination.icon),
                    selectedIcon: Icon(destination.selectedIcon),
                    label: destination.label(localizations),
                  ),
              ],
            ),
          ),
          WynimeWindowClass.medium => _RailShell(
            selectedIndex: _selectedIndex,
            onDestinationSelected: _selectDestination,
            destinations: destinations,
            localizations: localizations,
            page: page,
            extended: false,
          ),
          WynimeWindowClass.expanded => _RailShell(
            selectedIndex: _selectedIndex,
            onDestinationSelected: _selectDestination,
            destinations: destinations,
            localizations: localizations,
            page: page,
            extended: true,
          ),
        };
      },
    );
  }
}

class _RailShell extends StatelessWidget {
  const _RailShell({
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
    required this.localizations,
    required this.page,
    required this.extended,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<AppDestination> destinations;
  final AppLocalizations localizations;
  final Widget page;
  final bool extended;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
          SizedBox(
            width: extended
                ? WynimeDimensions.expandedRailWidth
                : WynimeDimensions.mediumRailWidth,
            child: NavigationRail(
              extended: extended,
              minExtendedWidth: WynimeDimensions.expandedRailWidth,
              selectedIndex: selectedIndex,
              onDestinationSelected: onDestinationSelected,
              labelType: extended
                  ? NavigationRailLabelType.none
                  : NavigationRailLabelType.selected,
              leading: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Tooltip(
                  message: localizations.appTitle,
                  child: const Icon(Icons.play_circle_fill_rounded, size: 32),
                ),
              ),
              destinations: [
                for (final destination in destinations)
                  NavigationRailDestination(
                    icon: Icon(destination.icon),
                    selectedIcon: Icon(destination.selectedIcon),
                    label: Text(destination.label(localizations)),
                  ),
              ],
            ),
          ),
          const VerticalDivider(width: 1),
          Expanded(child: page),
        ],
      ),
    );
  }
}
