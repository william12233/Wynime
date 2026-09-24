import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_installed_live_episode_pipeline.dart';
import 'package:wynime/src/application/source_installed_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_installed_live_search_pipeline.dart';
import 'package:wynime/src/application/source_installed_live_subject_pipeline.dart';
import 'package:wynime/src/application/source_episode_correlator.dart';
import 'package:wynime/src/application/source_episode_ordinal.dart';
import 'package:wynime/src/application/source_live_episode_coordinator.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_live_search_coordinator.dart';
import 'package:wynime/src/application/source_live_subject_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/application/subject_source_playback_controller.dart';
import 'package:wynime/src/application/source_subject_matcher.dart';
import 'package:wynime/src/domain/models/bangumi_episode_type.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/episode_mapping.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_provenance.dart';
import 'package:wynime/src/domain/models/source_playback_mapping.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_package_repository.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_playback_mapping_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_episode_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_revision_calculator.dart';
import 'package:wynime/src/infrastructure/source_rules/source_title_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_subject_normalizer.dart';
import 'package:wynime/src/application/source_package_startup_controller.dart';
import 'package:wynime/src/infrastructure/source_rules/persistent_source_package_manager.dart';

import '../helpers/test_database.dart';

void main() {
  test('episode type and ordinal contracts are conservative', () {
    expect(BangumiEpisodeTypeCode.isMainStory(0), isTrue);
    for (final type in [1, 2, 3, 4, 5, 6, -1, 99]) {
      expect(BangumiEpisodeTypeCode.isMainStory(type), isFalse);
    }
    expect(SourceEpisodeOrdinal.parse('EP 01')?.canonical, '1');
    expect(SourceEpisodeOrdinal.parse('第１２.５０話')?.canonical, '12.5');
    expect(SourceEpisodeOrdinal.parse('SP 1'), isNull);
    expect(SourceEpisodeOrdinal.parse('OVA'), isNull);
  });

  test('source labels retain raw text, number, and episode kind', () {
    final label = SourceEpisodeLabel.parse('第 ０１ 集');
    expect(label.rawLabel, '第 ０１ 集');
    expect(label.number, 1);
    expect(label.kind, SourceEpisodeKind.main);

    final special = SourceEpisodeLabel.parse('SP 1');
    expect(special.rawLabel, 'SP 1');
    expect(special.number, 1);
    expect(special.kind, SourceEpisodeKind.special);
    expect(SourceEpisodeOrdinal.parse('第 01 集')?.canonical, '1');
    expect(SourceEpisodeOrdinal.parse('SP 1'), isNull);
  });

  test('title normalization does not perform fuzzy or translated matching', () {
    const normalizer = SourceTitleNormalizer();
    expect(normalizer.normalize('  ＡＢＣ：第１話  '), 'abc 第1話');
    expect(normalizer.normalize('ABC 1'), 'abc 1');
    expect(normalizer.normalize('abc season 2'), isNot('abc season 1'));
  });

  test('subject matching accepts one exact normalized identity only', () {
    final result = const SourceSubjectMatcher().match(
      subject: _subject(name: 'Example', nameCn: '範例'),
      results: [
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-100',
          title: '  範例  ',
        ),
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-100',
          title: 'Example',
        ),
      ],
    );
    expect(result.status, SourceSubjectMatchStatus.matched);
    expect(result.identity?.identityKey, 'xifan/source-100');
  });

  test('ambiguous subject matches remain explicit', () {
    final result = const SourceSubjectMatcher().match(
      subject: _subject(name: 'Example', nameCn: '範例'),
      results: [
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-100',
          title: '範例',
        ),
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-101',
          title: 'Example',
        ),
      ],
    );
    expect(result.status, SourceSubjectMatchStatus.selectionRequired);
    expect(result.identity, isNull);
    expect(result.candidates, hasLength(2));
  });

  test(
    'subject matching reports no result instead of selecting the first row',
    () {
      final result = const SourceSubjectMatcher().match(
        subject: _subject(name: 'Example', nameCn: '範例'),
        results: [
          SourceSearchResult(
            sourceId: 'xifan',
            subjectId: 'source-100',
            title: 'Another title',
          ),
        ],
      );
      expect(result.status, SourceSubjectMatchStatus.notFound);
      expect(result.candidates, isEmpty);
    },
  );

  test('main-story correlation accepts one exact ordinal only', () {
    final sourceSubject = SourceSubjectIdentity(
      sourceId: 'xifan',
      subjectId: 'source-100',
    );
    final details = SourceSubjectDetails(
      identity: sourceSubject,
      title: 'Example',
      lines: [
        SourceSubjectLine(
          identity: sourceSubject,
          lineId: 'line-a',
          title: 'A',
          episodes: [
            SourceEpisode(
              identity: SourceEpisodeIdentity(
                sourceId: 'xifan',
                lineId: 'line-a',
                subjectId: 'source-100',
                episodeId: 'ep-1',
              ),
              title: 'Episode 1',
            ),
          ],
        ),
      ],
    );
    final result = const SourceEpisodeCorrelator().correlate(
      episode: const BangumiEpisode(
        id: 'bgm-1',
        subjectId: '100',
        name: 'Episode 1',
        nameCn: '第一集',
        sort: 1,
        type: 0,
      ),
      details: details,
    );
    expect(result.status, SourceEpisodeCorrelationStatus.automatic);
    expect(result.selected, details.lines.single.episodes.single.identity);
  });

  test(
    'main-story correlation requires explicit selection when source ordinals do not align',
    () {
      final sourceSubject = SourceSubjectIdentity(
        sourceId: 'xifan',
        subjectId: 'source-100',
      );
      final sourceEpisode = SourceEpisode(
        identity: SourceEpisodeIdentity(
          sourceId: 'xifan',
          lineId: 'line-a',
          subjectId: 'source-100',
          episodeId: 'ep-12',
        ),
        title: '12',
      );
      final details = SourceSubjectDetails(
        identity: sourceSubject,
        title: 'Example',
        lines: [
          SourceSubjectLine(
            identity: sourceSubject,
            lineId: 'line-a',
            title: 'A',
            episodes: [sourceEpisode],
          ),
        ],
      );

      final result = const SourceEpisodeCorrelator().correlate(
        episode: const BangumiEpisode(
          id: 'bgm-24',
          subjectId: '100',
          name: 'Episode 24',
          nameCn: '第二十四集',
          sort: 24,
          type: 0,
        ),
        details: details,
      );

      expect(result.status, SourceEpisodeCorrelationStatus.selectionRequired);
      expect(result.reason, 'episode_manual_selection_required');
      expect(result.selected, isNull);
      expect(result.candidates, [sourceEpisode]);
    },
  );

  test(
    'manual Bangumi episode mapping survives controller reconstruction',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final version = Version.parse('1.0.16');
      final package = const SourcePackageDecoder().decode(
        File('sources/xifan.wynsrc.json').readAsStringSync(),
      );
      final sourcePackages = SourcePackageStartupController(
        managerFactory: () async => PersistentSourcePackageManager(
          manager: DeclarativeSourcePackageManager(wynimeVersion: version),
          repository: DriftSourcePackageRepository(database),
        ),
      );
      await sourcePackages.initialize();
      final pending = await sourcePackages.installOrUpdate(package);
      final installed = await sourcePackages.enable(
        packageId: pending.package.packageId,
        version: pending.package.version,
        userApproved: true,
        reconsentGranted: false,
      );
      expect(installed.status, SourcePackageStatus.enabled);

      final mappingRepository = DriftSourcePlaybackMappingRepository(database);
      final body = File('test/fixtures/source_packages/xifan/detail_3541.html')
          .readAsStringSync()
          .replaceAll(
            '<li><a href="/anime/633/play/9453?source=xfy2">第 13 集</a></li>',
            '',
          );
      final transport = _StaticSourceHttpTransport(body);
      final runtime = SourceLiveHttpPackageRuntime(
        httpExecutor: SourceLiveHttpRequestExecutor(
          requestCoordinator: SourceLiveHttpRequestCoordinator(
            wynimeVersion: version,
          ),
          transport: transport,
        ),
        fixtureRuntime: DeclarativeSourcePackageRuntime(wynimeVersion: version),
      );
      final planFactory = SourceLiveOperationPlanFactory(
        wynimeVersion: version,
      );
      final searchPipeline = SourceInstalledLiveSearchPipeline(
        planFactory: planFactory,
        searchCoordinator: SourceLiveSearchCoordinator(
          runtime: runtime,
          normalizer: const DeclarativeSourceSearchNormalizer(),
        ),
      );
      final subjectPipeline = SourceInstalledLiveSubjectPipeline(
        planFactory: planFactory,
        subjectCoordinator: SourceLiveSubjectCoordinator(
          runtime: runtime,
          normalizer: const DeclarativeSourceSubjectNormalizer(),
        ),
      );
      final episodePipeline = SourceInstalledLiveEpisodePipeline(
        planFactory: planFactory,
        episodeCoordinator: SourceLiveEpisodeCoordinator(
          runtime: runtime,
          normalizer: const DeclarativeSourceEpisodeNormalizer(),
        ),
      );
      final playbackPipeline = SourceInstalledLivePlaybackPipeline(
        planFactory: planFactory,
        playbackPipeline: SourceLivePlaybackPipeline(
          playableSourceCoordinator: SourceLivePlayableSourceCoordinator(
            runtime: runtime,
            normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
          ),
          routeCoordinator: SourceLivePlaybackRouteCoordinator(
            wynimeVersion: version,
            routeCoordinator: const SourcePlaybackRouteCoordinator(
              selector: DeterministicSourcePlaybackRouteSelector(),
            ),
          ),
          sessionRequestCoordinator:
              SourceLivePlaybackSessionRequestCoordinator(
                wynimeVersion: version,
                builder:
                    const DeterministicSourcePlaybackSessionRequestBuilder(),
              ),
          openRequestCoordinator:
              const SourceLivePlaybackOpenRequestCoordinator(),
          preparedOpener: const _NoOpenPreparedRequestOpener(),
        ),
        closeDelegate: () async {},
      );
      addTearDown(() {
        searchPipeline.close();
        subjectPipeline.close();
        episodePipeline.close();
        sourcePackages.dispose();
      });
      addTearDown(sourcePackages.close);

      final subject = const BangumiSubject(
        id: '638151',
        name: 'Example season',
        nameCn: 'Example season',
        summary: '',
        eps: 24,
      );
      const episode = BangumiEpisode(
        id: '1705031',
        subjectId: '638151',
        name: 'Episode 24',
        nameCn: '第 24 集',
        sort: 24,
        type: 0,
      );
      final first = SubjectSourcePlaybackController(
        subject: subject,
        sourcePackages: sourcePackages,
        searchPipeline: searchPipeline,
        subjectPipeline: subjectPipeline,
        episodePipeline: episodePipeline,
        playbackPipeline: playbackPipeline,
        mappingRepository: mappingRepository,
      );

      await first.selectSubject(
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: '633',
          title: 'Example season',
        ),
      );
      expect(first.state.phase, SubjectSourcePlaybackPhase.ready);
      final pendingResolution = await first.resolveEpisode(episode);
      expect(
        pendingResolution.status,
        SourceEpisodeResolutionStatus.selectionRequired,
      );
      expect(pendingResolution.reason, 'episode_manual_selection_required');
      expect(pendingResolution.candidates, hasLength(12));
      final selected = pendingResolution.candidates.singleWhere(
        (candidate) => candidate.identity.episodeId == '9452',
      );
      final selectedResolution = await first.selectEpisode(episode, selected);
      expect(selectedResolution.status, SourceEpisodeResolutionStatus.ready);
      expect(selectedResolution.identity, selected.identity);
      expect(first.state.selectedEpisode, selected.identity);
      first.dispose();

      final reopened = SubjectSourcePlaybackController(
        subject: subject,
        sourcePackages: sourcePackages,
        searchPipeline: searchPipeline,
        subjectPipeline: subjectPipeline,
        episodePipeline: episodePipeline,
        playbackPipeline: playbackPipeline,
        mappingRepository: mappingRepository,
      );
      addTearDown(reopened.dispose);
      await reopened.initialize();
      expect(reopened.state.phase, SubjectSourcePlaybackPhase.ready);
      final reopenedResolution = await reopened.resolveEpisode(episode);
      expect(reopenedResolution.status, SourceEpisodeResolutionStatus.ready);
      expect(reopenedResolution.identity, selected.identity);
      expect(reopened.state.selectedEpisode, selected.identity);

      final persisted = await mappingRepository.findValidEpisodeMapping(
        bangumiSubjectId: subject.id,
        bangumiEpisodeId: episode.id,
        packageId: 'xifan',
        expectedSourceSubject: SourceSubjectIdentity(
          sourceId: 'xifan',
          subjectId: '633',
        ),
        expectedProvenance: SourcePackageRevisionCalculator().calculate(
          package,
        ),
      );
      expect(persisted?.sourceEpisode, selected.identity);
      expect(persisted?.mappingKind, EpisodeMappingKind.userConfirmed);
      expect(persisted?.mapping?.numberingMode, EpisodeNumberingMode.manual);
      expect(persisted?.mapping?.sourceEpisode.rawLabel, selected.rawLabel);
    },
  );

  test('mapping provenance mismatch prunes stale identity rows', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final repository = DriftSourcePlaybackMappingRepository(database);
    final provenance = SourcePackageProvenance(
      packageId: 'xifan',
      version: Version.parse('1.0.0'),
      revisionSha256: 'a' * 64,
    );
    final sourceSubject = SourceSubjectIdentity(
      sourceId: 'xifan',
      subjectId: 'source-100',
    );
    await repository.upsertSubjectMapping(
      SourceSubjectMapping(
        bangumiSubjectId: '100',
        packageId: 'xifan',
        sourceSubject: sourceSubject,
        provenance: provenance,
        mappingKind: SubjectMappingKind.automaticExactTitle,
        confirmedAt: DateTime.utc(2026, 9, 22),
      ),
    );
    await repository.upsertEpisodeMapping(
      SourceEpisodeMapping(
        bangumiSubjectId: '100',
        bangumiEpisodeId: 'bgm-1',
        packageId: 'xifan',
        sourceEpisode: SourceEpisodeIdentity(
          sourceId: 'xifan',
          lineId: 'line-a',
          subjectId: 'source-100',
          episodeId: 'ep-1',
        ),
        provenance: provenance,
        mappingKind: EpisodeMappingKind.automaticExactNumber,
        confirmedAt: DateTime.utc(2026, 9, 22),
      ),
    );

    final stale = await repository.findValidSubjectMapping(
      bangumiSubjectId: '100',
      packageId: 'xifan',
      expectedProvenance: SourcePackageProvenance(
        packageId: 'xifan',
        version: Version.parse('1.0.0'),
        revisionSha256: 'b' * 64,
      ),
    );
    expect(stale, isNull);
    expect(
      await (database.select(database.sourceSubjectMappings)).get(),
      isEmpty,
    );
    expect(
      await (database.select(database.sourceEpisodeMappings)).get(),
      isEmpty,
    );
  });

  test('episode correlation infers cumulative numbering with evidence', () {
    final sourceSubject = SourceSubjectIdentity(
      sourceId: 'xifan',
      subjectId: 'source-638151',
    );
    final sourceEpisodes = [
      for (var index = 1; index <= 3; index++)
        SourceEpisode(
          identity: SourceEpisodeIdentity(
            sourceId: 'xifan',
            lineId: 'line-a',
            subjectId: 'source-638151',
            episodeId: 'ep-$index',
          ),
          title: '第 ${index.toString().padLeft(2, '0')} 集',
        ),
    ];
    final details = SourceSubjectDetails(
      identity: sourceSubject,
      title: 'Example season',
      lines: [
        SourceSubjectLine(
          identity: sourceSubject,
          lineId: 'line-a',
          title: 'A',
          episodes: sourceEpisodes,
        ),
      ],
    );
    final bangumiEpisodes = [
      for (var index = 13; index <= 15; index++)
        BangumiEpisode(
          id: 'bgm-$index',
          subjectId: '638151',
          name: 'Episode $index',
          nameCn: '第 $index 集',
          sort: index.toDouble(),
          type: 0,
        ),
    ];

    final result = const SourceEpisodeCorrelator().correlate(
      episode: bangumiEpisodes[1],
      details: details,
      bangumiEpisodes: bangumiEpisodes,
    );

    expect(result.status, SourceEpisodeCorrelationStatus.automatic);
    expect(result.mapping?.sourceEpisode.identity, sourceEpisodes[1].identity);
    expect(
      result.mapping?.numberingMode,
      EpisodeNumberingMode.cumulativeAbsolute,
    );
    expect(result.mapping?.offset, 12);
    expect(
      result.mapping?.evidence.map((evidence) => evidence.code),
      contains('ordered_episode_index_alignment'),
    );

    final firstSeasonEpisode = const SourceEpisodeCorrelator().correlate(
      episode: bangumiEpisodes.first,
      details: details,
      bangumiEpisodes: bangumiEpisodes,
    );
    expect(
      firstSeasonEpisode.mapping?.sourceEpisode.identity,
      sourceEpisodes.first.identity,
    );
  });

  test(
    'removing a package snapshot clears its mapping rows atomically',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final package = const SourcePackageDecoder().decode(
        File('sources/xifan.wynsrc.json').readAsStringSync(),
      );
      final installed = InstalledSourcePackage(
        package: package,
        status: SourcePackageStatus.enabled,
        requiresConsent: false,
        requiresReconsent: false,
      );
      await DriftSourcePackageRepository(database).replaceAll([installed]);
      final mappingRepository = DriftSourcePlaybackMappingRepository(database);
      final provenance = const SourcePackageRevisionCalculator().calculate(
        package,
      );
      await mappingRepository.upsertSubjectMapping(
        SourceSubjectMapping(
          bangumiSubjectId: '100',
          packageId: 'xifan',
          sourceSubject: SourceSubjectIdentity(
            sourceId: 'xifan',
            subjectId: 'source-100',
          ),
          provenance: provenance,
          mappingKind: SubjectMappingKind.userConfirmed,
          confirmedAt: DateTime.utc(2026, 9, 22),
        ),
      );
      await DriftSourcePackageRepository(database).replaceAll(const []);
      expect(await database.select(database.sourcePackages).get(), isEmpty);
      expect(
        await database.select(database.sourceSubjectMappings).get(),
        isEmpty,
      );
    },
  );
}

BangumiSubject _subject({required String name, required String nameCn}) =>
    BangumiSubject(
      id: 'bgm-100',
      name: name,
      nameCn: nameCn,
      summary: '',
      eps: 1,
    );

final class _StaticSourceHttpTransport implements SourceHttpTransport {
  _StaticSourceHttpTransport(this.body);

  final String body;

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    return SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: request.uri,
        redirectChain: const [],
        body: body,
      ),
    );
  }

  @override
  Future<void> close() async {}
}

final class _NoOpenPreparedRequestOpener
    implements SourceLivePlaybackPreparedRequestOpener {
  const _NoOpenPreparedRequestOpener();

  @override
  Future<SourceLivePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLivePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    if (openResult.status ==
        SourceLivePlaybackOpenRequestCoordinatorStatus.ready) {
      return SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'test_open_disabled',
      );
    }
    return SourceLivePlaybackPreparedOpenResult(
      status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
      openRequestStatus: openResult.status,
      sessionStatus: openResult.sessionStatus,
      routeStatus: openResult.routeStatus,
      requestStatus: openResult.requestStatus,
      reasonCode: openResult.reasonCode ?? 'test_open_disabled',
    );
  }
}
