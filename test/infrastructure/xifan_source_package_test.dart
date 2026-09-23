import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_subject_details_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_subject_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';

void main() {
  late SourcePackageManifest package;
  late InstalledSourcePackage installed;
  late DeclarativeSourcePackageRuntime runtime;

  setUp(() {
    package = const SourcePackageDecoder().decode(
      File('sources/xifan.wynsrc.json').readAsStringSync(),
    );
    final manager = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.15'),
    );
    final pending = manager.install(package);
    installed = manager.enable(
      packageId: pending.package.packageId,
      version: pending.package.version,
      userApproved: true,
      reconsentGranted: false,
    );
    runtime = DeclarativeSourcePackageRuntime(
      wynimeVersion: Version.parse('1.0.15'),
    );
  });

  test('xifan search fixture produces an exact source subject result', () {
    final runtimeResult = runtime.executeFixture(
      installedPackage: installed,
      programId: 'search',
      fixture: _fixture(
        'test/fixtures/source_packages/xifan/search_633.html',
        'https://next.xifanacg.com/search?q=%E7%84%A1%E8%81%B7%E8%BD%89%E7%94%9F',
      ),
    );
    final operation = package.liveOperationByKind(
      SourcePackageLiveOperationKind.search,
    )!;

    expect(runtimeResult.records, hasLength(1));
    expect(runtimeResult.records.single.values['subjectId'], '633');
    expect(runtimeResult.records.single.values['title'], contains('無職轉生'));
    final expanded = operation.requestTemplate.expand({'query': '無職轉生'});
    expect(expanded.host, 'next.xifanacg.com');
    expect(expanded.path, '/search');
    expect(expanded.queryParameters['q'], '無職轉生');
  });

  test('xifan live document bridge requires the unreleased next runtime', () {
    final publicValidation = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.14'),
    ).validate(package);
    final nextValidation = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.15'),
    ).validate(package);

    expect(publicValidation.isValid, isFalse);
    expect(publicValidation.code, 'incompatible_wynime_version');
    expect(nextValidation.isValid, isTrue);
    expect(nextValidation.code, 'valid');
  });

  test('xifan 1.1.0 to 1.2.1 update requires fresh re-consent', () {
    final currentJson =
        jsonDecode(File('sources/xifan.wynsrc.json').readAsStringSync())
            as Map<String, dynamic>;
    final legacyJson =
        jsonDecode(jsonEncode(currentJson)) as Map<String, dynamic>
          ..['version'] = '1.1.0';
    final security = Map<String, dynamic>.from(
      legacyJson['security'] as Map<dynamic, dynamic>,
    );
    security['domains'] = (security['domains'] as List<dynamic>)
        .map((raw) {
          final domain = Map<String, dynamic>.from(
            raw as Map<dynamic, dynamic>,
          );
          if (domain['host'] == 'bjdownload.pan.wo.cn') {
            domain.remove('ports');
          }
          return domain;
        })
        .toList(growable: false);
    legacyJson['security'] = security;

    final decoder = const SourcePackageDecoder();
    final legacyPackage = decoder.decode(jsonEncode(legacyJson));
    final manager = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.15'),
    );
    final pending = manager.install(legacyPackage);
    final enabled = manager.enable(
      packageId: pending.package.packageId,
      version: pending.package.version,
      userApproved: true,
      reconsentGranted: false,
    );
    expect(enabled.status, SourcePackageStatus.enabled);

    final updated = manager.update(package);
    expect(updated.package.version, Version.parse('1.2.1'));
    expect(updated.status, SourcePackageStatus.disabled);
    expect(updated.requiresConsent, isTrue);
    expect(updated.requiresReconsent, isTrue);
    expect(
      () => manager.enable(
        packageId: updated.package.packageId,
        version: updated.package.version,
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

    final reconsented = manager.enable(
      packageId: updated.package.packageId,
      version: updated.package.version,
      userApproved: true,
      reconsentGranted: true,
    );
    expect(reconsented.status, SourcePackageStatus.enabled);
    expect(reconsented.requiresConsent, isFalse);
    expect(reconsented.requiresReconsent, isFalse);
  });

  test(
    'xifan subject detail fixture produces subject and 13 episode links',
    () {
      final metadata = runtime.executeFixture(
        installedPackage: installed,
        programId: 'subject_metadata',
        fixture: _fixture(
          'test/fixtures/source_packages/xifan/detail_3541.html',
          'https://next.xifanacg.com/anime/633',
        ),
      );
      final episodes = runtime.executeFixture(
        installedPackage: installed,
        programId: 'episode_links',
        fixture: _fixture(
          'test/fixtures/source_packages/xifan/detail_3541.html',
          'https://next.xifanacg.com/anime/633',
        ),
      );
      final operation = package.liveOperationByKind(
        SourcePackageLiveOperationKind.subjectDetails,
      )!;

      final result = const DeclarativeSourceSubjectNormalizer()
          .normalizeSubjectDetails(
            package: package,
            metadataRuntimeResult: metadata,
            episodeRuntimeResult: episodes,
            subject: SourceSubjectIdentity(sourceId: 'xifan', subjectId: '633'),
            mapping: operation.mapping as SourceSubjectDetailsFieldMapping,
          );

      expect(result.status, SourceSubjectDetailsStatus.available);
      expect(result.details!.title, contains('無職轉生'));
      expect(result.details!.lines, hasLength(1));
      expect(result.details!.lines.single.lineId, 'xfy2');
      expect(result.details!.lines.single.episodes, hasLength(13));
      expect(
        result.details!.lines.single.episodes.last.identity.episodeId,
        '9453',
      );
    },
  );

  test(
    'xifan playable fixture extracts direct media URL from video element',
    () {
      final runtimeResult = runtime.executeFixture(
        installedPackage: installed,
        programId: 'playable_source',
        fixture: _fixture(
          'test/fixtures/source_packages/xifan/episode_3541_1_1.html',
          'https://next.xifanacg.com/anime/633/play/9441?source=xfy2',
        ),
      );
      final operation = package.liveOperationByKind(
        SourcePackageLiveOperationKind.playableSource,
      )!;
      final episode = SourceEpisodeIdentity(
        sourceId: 'xifan',
        lineId: 'xfy2',
        subjectId: '633',
        episodeId: '9441',
      );

      final result = const DeclarativeSourcePlayableSourceNormalizer()
          .normalizePlayableSources(
            package: package,
            runtimeResult: runtimeResult,
            episode: episode,
            mapping: operation.mapping as SourcePlayableSourceFieldMapping,
          );

      expect(result.status.name, 'available');
      expect(result.results, hasLength(1));
      expect(result.results.single.mediaUri.host, 'apn.moedot.net');
      expect(result.results.single.pageUri.host, 'next.xifanacg.com');
      expect(result.results.single.kind.name, 'video');
    },
  );

  test('challenge diagnostics stay typed and do not expose response text', () {
    final result = const DeclarativeSourceSubjectNormalizer()
        .normalizeSubjectDetails(
          package: package,
          metadataRuntimeResult: SourceRuntimeResult(
            packageId: package.packageId,
            packageVersion: package.version,
            programId: 'subject_metadata',
            status: SourceRuntimeStatus.failed,
            records: const [],
            diagnostics: [
              SourceRuntimeDiagnostic(
                code: 'challenge_required',
                message: 'The source requires an interactive challenge.',
              ),
            ],
            consumedSteps: 0,
            selectorMatches: 0,
          ),
          episodeRuntimeResult: SourceRuntimeResult(
            packageId: package.packageId,
            packageVersion: package.version,
            programId: 'episode_links',
            status: SourceRuntimeStatus.failed,
            records: const [],
            diagnostics: const [],
            consumedSteps: 0,
            selectorMatches: 0,
          ),
          subject: SourceSubjectIdentity(sourceId: 'xifan', subjectId: '3541'),
          mapping:
              package
                      .liveOperationByKind(
                        SourcePackageLiveOperationKind.subjectDetails,
                      )!
                      .mapping
                  as SourceSubjectDetailsFieldMapping,
        );

    expect(result.status, SourceSubjectDetailsStatus.challengeRequired);
    expect(result.details, isNull);
    expect(result.diagnostics.single.code, 'challenge_required');
    expect(result.diagnostics.single.message, isNot(contains('驗證')));
  });
}

SourceFixture _fixture(String path, String uri) => SourceFixture(
  initialUri: Uri.parse(uri),
  body: File(path).readAsStringSync(),
);
