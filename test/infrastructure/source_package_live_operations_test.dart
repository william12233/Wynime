import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/repositories/source_package_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/persistent_source_package_manager.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const decoder = SourcePackageDecoder();
  const encoder = SourcePackageEncoder();

  test(
    'schema-v2 decodes and round-trips all three live operation bindings',
    () {
      final package = decoder.decode(jsonEncode(validV2PackageMap()));

      expect(package.schemaVersion, 2);
      expect(package.liveOperations, hasLength(3));
      expect(
        package
            .liveOperationByKind(SourcePackageLiveOperationKind.search)!
            .programId,
        'search',
      );
      expect(
        package
            .liveOperationByKind(SourcePackageLiveOperationKind.episode)!
            .requestTemplate
            .placeholders,
        containsAll(<String>['sourceId', 'subjectId']),
      );
      expect(
        package
            .liveOperationByKind(SourcePackageLiveOperationKind.playableSource)!
            .mappingFieldNames,
        containsAll(<String>[
          'sourceKey',
          'label',
          'kind',
          'mediaUri',
          'pageUri',
        ]),
      );

      final encoded = encoder.encode(package);
      final decodedAgain = decoder.decode(encoded);
      expect(encoder.encode(decodedAgain), encoded);

      final signaturePayload = encoder.encodeSignaturePayload(package);
      expect(signaturePayload, contains('"liveOperations"'));
      final changedMap = validV2PackageMap();
      final changedOperations = changedMap['liveOperations']! as List<Object?>;
      (changedOperations.first as Map<String, Object?>)['uriTemplate'] =
          'https://example.com/search?page=2&q={query}';
      final changed = decoder.decode(jsonEncode(changedMap));
      expect(encoder.encodeSignaturePayload(changed), isNot(signaturePayload));
    },
  );

  test(
    'schema-v1 remains strict and does not silently accept live metadata',
    () {
      final v1 = validV1PackageMap();
      v1['liveOperations'] = <Object?>[];

      expect(
        () => decoder.decode(jsonEncode(v1)),
        throwsA(isA<SourcePackageFormatException>()),
      );
    },
  );

  test(
    'schema-v2 rejects unknown operation keys and malformed declarations',
    () {
      final unknownOperationKey = validV2PackageMap();
      final operations =
          unknownOperationKey['liveOperations']! as List<Object?>;
      (operations.first as Map<String, Object?>)['body'] = 'forbidden';
      expect(
        () => decoder.decode(jsonEncode(unknownOperationKey)),
        throwsA(isA<SourcePackageFormatException>()),
      );

      final unknownPlaceholder = validV2PackageMap();
      final search =
          (unknownPlaceholder['liveOperations']! as List<Object?>).first
              as Map<String, Object?>;
      search['uriTemplate'] = 'https://example.com/search?q={episodeId}';
      expect(
        () => decoder.decode(jsonEncode(unknownPlaceholder)),
        throwsA(
          isA<SourcePackageFormatException>().having(
            (error) => error.message,
            'message',
            contains('unsupported input placeholder'),
          ),
        ),
      );
    },
  );

  test('URI templates encode each input component and redact diagnostics', () {
    final template = SourcePackageUriTemplate(
      'https://example.com/search?q={query}&page=1',
    );
    final expanded = template.expand({'query': 'a&b?/秘密'});

    expect(expanded.queryParameters['q'], 'a&b?/秘密');
    expect(expanded.fragment, isEmpty);
    expect(template.toString(), isNot(contains('a&b')));
    expect(() => template.expand({'query': '  secret'}), throwsArgumentError);
    expect(
      () => SourcePackageUriTemplate(
        'https://example.com/search?${List<String>.filled(60, 'q={query}').join('&')}',
      ).expand({'query': List<String>.filled(256, '秘密').join()}),
      throwsArgumentError,
    );
  });

  test('URI templates reject unsafe authority, scheme, port and fragment', () {
    for (final value in <String>[
      'ftp://example.com/search',
      'https://{host}/search',
      'https://example.com:8443/search',
      'https://example.com/search#fragment',
      'https://localhost/search',
      'https://127.0.0.1/search',
    ]) {
      expect(
        () => SourcePackageUriTemplate(value),
        throwsArgumentError,
        reason: value,
      );
    }
  });

  test('manifest binds operation mappings to existing program fields', () {
    final package = _v2Package();
    expect(package.liveOperations, hasLength(3));

    final invalid = <SourcePackageLiveOperation>[
      SourcePackageLiveOperation(
        kind: SourcePackageLiveOperationKind.search,
        programId: 'search',
        uriTemplate: 'https://example.com/search?q={query}',
        mapping: SourceSearchFieldMapping(
          subjectIdField: 'missing',
          titleField: 'title',
        ),
      ),
    ];
    expect(
      () => SourcePackageManifest(
        schemaVersion: 2,
        packageId: 'example.anime',
        displayName: 'Example Anime',
        version: Version.parse('1.0.0'),
        wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
        securityPolicy: testSourcePolicy(),
        programs: [
          _program('search', ['subject', 'title']),
        ],
        liveOperations: invalid,
      ),
      throwsArgumentError,
    );
  });

  test('live operation metadata participates in re-consent comparison', () {
    final original = _v2Package();
    final same = _v2Package();
    final changedMap = validV2PackageMap();
    final changedSearch =
        (changedMap['liveOperations']! as List<Object?>).first
            as Map<String, Object?>;
    changedSearch['uriTemplate'] =
        'https://example.com/search?page=2&q={query}';
    changedMap['version'] = '1.1.0';
    final changed = const SourcePackageDecoder().decode(jsonEncode(changedMap));

    expect(original.requiresReconsentComparedTo(same), isFalse);
    expect(changed.requiresReconsentComparedTo(original), isTrue);

    final manager = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.0'),
    );
    expect(
      manager.validate(changed, previous: original).requiresReconsent,
      isTrue,
    );

    manager.install(original);
    final pending = manager.update(changed);
    expect(pending.status, SourcePackageStatus.disabled);
    expect(pending.requiresReconsent, isTrue);
    expect(
      () => manager.enable(
        packageId: changed.packageId,
        version: changed.version,
        userApproved: true,
        reconsentGranted: false,
      ),
      throwsA(
        isA<SourcePackageManagerException>().having(
          (error) => error.code,
          'code',
          'reconsent_required',
        ),
      ),
    );
  });

  test('persisted v2 package metadata survives manager restart', () async {
    final repository = _EncodedRepository();
    final first = PersistentSourcePackageManager(
      manager: DeclarativeSourcePackageManager(
        wynimeVersion: Version.parse('1.0.0'),
      ),
      repository: repository,
    );
    await first.load();
    await first.install(_v2Package());
    await first.enable(
      packageId: 'example.anime',
      version: Version.parse('1.0.0'),
      userApproved: true,
      reconsentGranted: false,
    );

    final second = PersistentSourcePackageManager(
      manager: DeclarativeSourcePackageManager(
        wynimeVersion: Version.parse('1.0.0'),
      ),
      repository: repository,
    );
    final restored = await second.load();

    expect(restored.single.status, SourcePackageStatus.enabled);
    expect(restored.single.package.schemaVersion, 2);
    expect(restored.single.package.liveOperations, hasLength(3));
  });
}

Map<String, Object?> validV1PackageMap() {
  return {
    'schemaVersion': 1,
    'packageId': 'example.anime',
    'displayName': 'Example Anime',
    'version': '1.0.0',
    'wynimeVersion': '^1.0.0',
    'security': _securityMap(),
    'programs': [
      _programMap('search', ['subjectId', 'title']),
    ],
  };
}

Map<String, Object?> validV2PackageMap() {
  return {
    'schemaVersion': 2,
    'packageId': 'example.anime',
    'displayName': 'Example Anime',
    'version': '1.0.0',
    'wynimeVersion': '^1.0.0',
    'security': _securityMap(),
    'programs': [
      _programMap('search', ['subjectId', 'searchTitle']),
      _programMap('episode', [
        'lineId',
        'subjectId',
        'episodeId',
        'episodeTitle',
      ]),
      _programMap('playable', [
        'sourceKey',
        'label',
        'kind',
        'mediaUri',
        'pageUri',
      ]),
    ],
    'liveOperations': [
      {
        'kind': 'search',
        'programId': 'search',
        'uriTemplate': 'https://example.com/search?q={query}',
        'mapping': {'subjectIdField': 'subjectId', 'titleField': 'searchTitle'},
      },
      {
        'kind': 'episode',
        'programId': 'episode',
        'uriTemplate':
            'https://example.com/anime/{sourceId}/{subjectId}/episodes',
        'mapping': {
          'lineIdField': 'lineId',
          'subjectIdField': 'subjectId',
          'episodeIdField': 'episodeId',
          'titleField': 'episodeTitle',
        },
      },
      {
        'kind': 'playableSource',
        'programId': 'playable',
        'uriTemplate':
            'https://example.com/anime/{subjectId}/episodes/{episodeId}/sources',
        'mapping': {
          'sourceKeyField': 'sourceKey',
          'labelField': 'label',
          'kindField': 'kind',
          'mediaUriField': 'mediaUri',
          'pageUriField': 'pageUri',
        },
      },
    ],
  };
}

Map<String, Object?> _securityMap() {
  return {
    'domains': [
      {
        'host': 'example.com',
        'includeSubdomains': true,
        'schemes': ['https'],
      },
    ],
    'permissions': ['network'],
    'budget': {
      'maxDocumentBytes': 65536,
      'maxRecords': 20,
      'maxSelectorMatches': 100,
      'maxEvaluationSteps': 1000,
      'maxRegexPatternChars': 128,
      'maxRegexInputChars': 1024,
      'maxRedirects': 3,
    },
  };
}

Map<String, Object?> _programMap(String id, List<String> fields) {
  return {
    'id': id,
    'documentKind': 'json',
    'root': {'type': 'jsonPath', 'expression': r'$'},
    'resultLimit': 20,
    'fields': fields
        .map(
          (name) => {
            'name': name,
            'selector': null,
            'value': 'raw',
            'attribute': null,
            'required': true,
            'regex': null,
          },
        )
        .toList(growable: false),
  };
}

SourcePackageManifest _v2Package() =>
    SourcePackageDecoder().decode(jsonEncode(validV2PackageMap()));

SourceRuleProgram _program(String id, List<String> fields) => SourceRuleProgram(
  programId: id,
  documentKind: SourceDocumentKind.json,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.jsonPath,
    expression: r'$',
  ),
  fields: fields
      .map(
        (name) => SourceFieldRule(
          name: name,
          valueKind: SourceValueKind.raw,
          required: true,
        ),
      )
      .toList(growable: false),
  resultLimit: 20,
);

final class _EncodedRepository implements SourcePackageRepository {
  final _encoder = const SourcePackageEncoder();
  final _decoder = const SourcePackageDecoder();
  List<InstalledSourcePackage> _stored = const [];

  @override
  Future<List<InstalledSourcePackage>> load() async {
    return _stored
        .map(
          (installed) => InstalledSourcePackage(
            package: _decoder.decode(_encoder.encode(installed.package)),
            status: installed.status,
            requiresConsent: installed.requiresConsent,
            requiresReconsent: installed.requiresReconsent,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<void> replaceAll(Iterable<InstalledSourcePackage> packages) async {
    _stored = packages.toList(growable: false);
  }
}
