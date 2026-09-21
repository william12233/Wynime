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
      wynimeVersion: Version.parse('1.0.12'),
    );
    final pending = manager.install(package);
    installed = manager.enable(
      packageId: pending.package.packageId,
      version: pending.package.version,
      userApproved: true,
      reconsentGranted: false,
    );
    runtime = DeclarativeSourcePackageRuntime(
      wynimeVersion: Version.parse('1.0.12'),
    );
  });

  test(
    'xifan subject detail fixture produces subject and 13 episode links',
    () {
      final metadata = runtime.executeFixture(
        installedPackage: installed,
        programId: 'subject_metadata',
        fixture: _fixture(
          'test/fixtures/source_packages/xifan/detail_3541.html',
          'https://anime.xifanacg.com/bangumi/3541.html',
        ),
      );
      final episodes = runtime.executeFixture(
        installedPackage: installed,
        programId: 'episode_links',
        fixture: _fixture(
          'test/fixtures/source_packages/xifan/detail_3541.html',
          'https://anime.xifanacg.com/bangumi/3541.html',
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
            subject: SourceSubjectIdentity(
              sourceId: 'xifan',
              subjectId: '3541',
            ),
            mapping: operation.mapping as SourceSubjectDetailsFieldMapping,
          );

      expect(result.status, SourceSubjectDetailsStatus.available);
      expect(result.details!.title, contains('無職轉生'));
      expect(result.details!.lines, hasLength(1));
      expect(result.details!.lines.single.lineId, '1');
      expect(result.details!.lines.single.episodes, hasLength(13));
      expect(
        result.details!.lines.single.episodes.last.identity.episodeId,
        '13',
      );
    },
  );

  test(
    'xifan playable fixture extracts direct media URL from iframe query',
    () {
      final runtimeResult = runtime.executeFixture(
        installedPackage: installed,
        programId: 'playable_source',
        fixture: _fixture(
          'test/fixtures/source_packages/xifan/episode_3541_1_1.html',
          'https://anime.xifanacg.com/watch/3541/1/1.html',
        ),
      );
      final operation = package.liveOperationByKind(
        SourcePackageLiveOperationKind.playableSource,
      )!;
      final episode = SourceEpisodeIdentity(
        sourceId: 'xifan',
        lineId: '1',
        subjectId: '3541',
        episodeId: '1',
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
      expect(result.results.single.pageUri.host, 'player.moedot.net');
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
