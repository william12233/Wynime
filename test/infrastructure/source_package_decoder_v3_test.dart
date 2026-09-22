import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_package_capabilities.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';

void main() {
  const decoder = SourcePackageDecoder();
  const encoder = SourcePackageEncoder();

  Map<String, Object?> loadPackage() {
    return jsonDecode(File('sources/xifan.wynsrc.json').readAsStringSync())
        as Map<String, Object?>;
  }

  test(
    'xifan v3 package decodes with exact cache and capability authority',
    () {
      final package = decoder.decode(jsonEncode(loadPackage()));

      expect(package.schemaVersion, 3);
      expect(package.packageId, 'xifan');
      expect(package.cachePolicy.searchTtl, Duration.zero);
      expect(package.cachePolicy.subjectMetadataTtl, Duration.zero);
      expect(package.cachePolicy.episodeListTtl, Duration.zero);
      expect(package.cachePolicy.playbackResolutionTtl, Duration.zero);
      expect(
        package.capabilities[SourcePackageCapability.search],
        SourcePackageCapabilityState.supported,
      );
      expect(package.liveOperations, hasLength(3));
      expect(
        package.liveOperationByKind(SourcePackageLiveOperationKind.search),
        isNotNull,
      );
      expect(
        package.liveOperationByKind(
          SourcePackageLiveOperationKind.subjectDetails,
        ),
        isNotNull,
      );
      expect(
        package.liveOperationByKind(
          SourcePackageLiveOperationKind.playableSource,
        ),
        isNotNull,
      );
    },
  );

  test('v3 encoding round-trips exact wire keys and semantic cache values', () {
    final package = decoder.decode(jsonEncode(loadPackage()));
    final encoded = jsonDecode(encoder.encode(package)) as Map<String, Object?>;
    final cache = encoded['cache']! as Map<String, Object?>;

    expect(
      cache.keys,
      unorderedEquals(<String>[
        'searchTtlSeconds',
        'subjectMetadataTtlSeconds',
        'episodeListTtlSeconds',
        'playbackResolutionTtlSeconds',
      ]),
    );
    expect(cache['searchTtlSeconds'], 0);
    expect(cache.containsKey('searchSeconds'), isFalse);
    expect(cache.containsKey('searchTtl'), isFalse);

    final decodedAgain = decoder.decode(encoder.encode(package));
    expect(decodedAgain.cachePolicy, package.cachePolicy);
    expect(encoder.encode(decodedAgain), encoder.encode(package));
  });

  test(
    'strict v3 rejects old cache key names, missing keys and invalid TTLs',
    () {
      final base = loadPackage();
      final cache = base['cache']! as Map<String, Object?>;

      for (final invalid in [
        () => cache['searchSeconds'] = cache.remove('searchTtlSeconds'),
        () => cache.remove('searchTtlSeconds'),
        () => cache['searchTtlSeconds'] = -1,
        () => cache['searchTtlSeconds'] = 1.5,
        () => cache['searchTtlSeconds'] = '60',
      ]) {
        final candidate = loadPackage();
        final candidateCache = candidate['cache']! as Map<String, Object?>;
        invalid();
        candidateCache.addAll(cache);
        expect(
          () => decoder.decode(jsonEncode(candidate)),
          throwsA(isA<SourcePackageFormatException>()),
        );
      }
    },
  );

  test('schema v1 and v2 retain strict field dialects', () {
    for (final schemaVersion in [1, 2]) {
      final candidate = loadPackage()
        ..['schemaVersion'] = schemaVersion
        ..remove('cache')
        ..remove('capabilities');
      if (schemaVersion == 1) {
        candidate.remove('liveOperations');
      } else {
        candidate['liveOperations'] = (candidate['liveOperations']! as List)
            .where(
              (operation) =>
                  (operation as Map<String, Object?>)['kind'] ==
                  'playableSource',
            )
            .toList(growable: false);
      }

      expect(
        () => decoder.decode(jsonEncode(candidate)),
        throwsA(isA<SourcePackageFormatException>()),
        reason: 'schema $schemaVersion must reject literal fields',
      );
    }
  });

  test('cache policy binds canonical payload and re-consent', () {
    final original = decoder.decode(jsonEncode(loadPackage()));
    final changedMap = loadPackage();
    final cache = changedMap['cache']! as Map<String, Object?>;
    cache['playbackResolutionTtlSeconds'] = 60;
    final changed = decoder.decode(jsonEncode(changedMap));

    expect(
      encoder.encodeSignaturePayload(changed),
      isNot(encoder.encodeSignaturePayload(original)),
    );
    expect(changed.requiresReconsentComparedTo(original), isTrue);
    expect(
      changed.cachePolicy.playbackResolutionTtl,
      const Duration(seconds: 60),
    );
  });
}
