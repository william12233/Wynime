import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/infrastructure/source_registry/source_registry_runtime_configuration.dart';

void main() {
  test('missing compile-time registry configuration stays disconnected', () {
    expect(SourceRegistryRuntimeConfiguration.fromEnvironment(), isNull);
  });

  test('accepts a bounded registry configuration for adapter wiring', () {
    final configuration = SourceRegistryRuntimeConfiguration.fromValues(
      owner: 'william',
      repository: 'wynime-registry',
      ref: 'release/v1',
      indexPath: 'sources/index.json',
    );

    expect(configuration, isNotNull);
    expect(configuration!.owner, 'william');
    expect(configuration.repository, 'wynime-registry');
    expect(configuration.ref, 'release/v1');
    expect(configuration.indexPath, 'sources/index.json');
  });

  test('release composition cannot select a local staged registry', () {
    expect(
      SourceRegistryRuntimeConfiguration.useStagedRegistry(
        isDebugMode: false,
        requested: true,
      ),
      isFalse,
    );
    expect(
      SourceRegistryRuntimeConfiguration.forApplication(
        isDebugMode: false,
        allowDevOverride: true,
      ),
      same(SourceRegistryRuntimeConfiguration.production),
    );
  });

  test(
    'staged registry selection requires both debug and explicit request',
    () {
      expect(
        SourceRegistryRuntimeConfiguration.useStagedRegistry(
          isDebugMode: true,
          requested: false,
        ),
        isFalse,
      );
      expect(
        SourceRegistryRuntimeConfiguration.useStagedRegistry(
          isDebugMode: true,
          requested: true,
        ),
        isTrue,
      );
    },
  );

  test('rejects unsafe registry path and ref values before construction', () {
    final invalidConfigurations = <SourceRegistryRuntimeConfiguration?>[
      SourceRegistryRuntimeConfiguration.fromValues(
        owner: '../owner',
        repository: 'wynime-registry',
        ref: 'main',
      ),
      SourceRegistryRuntimeConfiguration.fromValues(
        owner: 'william',
        repository: 'bad/repository',
        ref: 'main',
      ),
      SourceRegistryRuntimeConfiguration.fromValues(
        owner: 'william',
        repository: 'wynime-registry',
        ref: 'feature/../main',
      ),
      SourceRegistryRuntimeConfiguration.fromValues(
        owner: 'william',
        repository: 'wynime-registry',
        ref: 'main',
        indexPath: '../index.json',
      ),
    ];

    for (final configuration in invalidConfigurations) {
      expect(configuration, isNull);
    }
  });
}
