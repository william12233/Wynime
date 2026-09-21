import 'dart:collection';

enum SourcePackageCapability { search, detail, episodes, playback }

enum SourcePackageCapabilityState { supported, unsupported, challengeRequired }

final class SourcePackageCapabilities {
  SourcePackageCapabilities({
    required Map<SourcePackageCapability, SourcePackageCapabilityState> values,
  }) : values = _freeze(values) {
    if (this.values.length != SourcePackageCapability.values.length ||
        !this.values.keys.toSet().containsAll(SourcePackageCapability.values)) {
      throw ArgumentError(
        'Every public source capability must have one declared state.',
      );
    }
  }

  final UnmodifiableMapView<
    SourcePackageCapability,
    SourcePackageCapabilityState
  >
  values;

  SourcePackageCapabilityState operator [](
    SourcePackageCapability capability,
  ) => values[capability]!;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourcePackageCapabilities &&
          SourcePackageCapability.values.every(
            (capability) => this[capability] == other[capability],
          );

  @override
  int get hashCode => Object.hashAll(
    SourcePackageCapability.values.map((capability) => this[capability]),
  );

  static UnmodifiableMapView<
    SourcePackageCapability,
    SourcePackageCapabilityState
  >
  _freeze(Map<SourcePackageCapability, SourcePackageCapabilityState> values) {
    return UnmodifiableMapView(
      Map<SourcePackageCapability, SourcePackageCapabilityState>.unmodifiable(
        values,
      ),
    );
  }
}

SourcePackageCapabilities unsupportedSourcePackageCapabilities() {
  return SourcePackageCapabilities(
    values: {
      for (final capability in SourcePackageCapability.values)
        capability: SourcePackageCapabilityState.unsupported,
    },
  );
}
